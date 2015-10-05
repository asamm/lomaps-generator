package com.asamm.osmTools.generatorDb.db;

import com.asamm.osmTools.generatorDb.address.Boundary;
import com.asamm.osmTools.generatorDb.address.City;
import com.asamm.osmTools.generatorDb.address.Street;
import com.asamm.osmTools.utils.Logger;
import com.vividsolutions.jts.geom.*;
import com.vividsolutions.jts.io.ParseException;
import com.vividsolutions.jts.io.WKBReader;
import com.vividsolutions.jts.io.WKBWriter;
import com.vividsolutions.jts.simplify.DouglasPeuckerSimplifier;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.asamm.locus.features.dbPoi.DbPoiConst.*;
import static com.asamm.locus.features.dbPoi.DbPoiConst.TN_STREET_IN_CITIES;

public class DatabaseAddress extends ADatabaseHandler {

    private static final String TAG = DatabaseAddress.class.getSimpleName();


    /** increment for street. Streets have own ids there is NO relation with OSM id*/
    private long streetIdSequence = 1;

    /** Precompiled statement for inserting cities into db*/
    private PreparedStatement psInsertCity;
    private PreparedStatement psInsertCityTile;
    /** statement for insert new street into database */
    private PreparedStatement psInsertStreet;
    /** For insert possible cities where is street in*/
    private PreparedStatement psInserStreetCities;
    /** Statement select street from database */
    private PreparedStatement psSelectStreet;
    /** Statement to update street geometry */
    private PreparedStatement psUpdateStreet;

    private PreparedStatement psSelectNereastCities;

    private PreparedStatement psSimplifyStretGeom;

    private PreparedStatement psSimplifyCityGeom;

    private PreparedStatement psDeleteStreet;

    private PreparedStatement psDeleteStreetCities;

    ByteArrayInputStream bais;

    static boolean deleteOldDb = false;
    public DatabaseAddress(File file) throws Exception {

        super(file, deleteOldDb);

        if (!deleteOldDb){
            cleanTables();
        }

        setTables();

        // create prepared statemennts
        psInsertCity = createPreparedStatement(
                "INSERT INTO "+ TN_CITIES +" ("+COL_ID+", "+COL_TYPE+", "+COL_NAME+", "+COL_CENTER_GEOM+", "+COL_GEOM+
                 ") VALUES (?, ?, ?, GeomFromWKB(?, 4326), GeomFromWKB(?, 4326))");

        psInsertCityTile = createPreparedStatement("INSERT INTO cityTiles ("+COL_ID+", xtile, ytile) " +
                "VALUES (?, ?, ? )");

//        psInsertStreet = createPreparedStatement(
//                "INSERT INTO "+TN_STREETS+" ("+COL_ID+", "+COL_CITY_ID+", "+COL_CITY_PART_ID+", "+COL_NAME+", "+COL_GEOM+
//                ") VALUES (?, ?, ?, ?, GeomFromWKB(?, 4326))");

        psInsertStreet = createPreparedStatement(
                "INSERT INTO "+TN_STREETS+" ("+COL_ID+", "+COL_NAME+", "+COL_GEOM+
                        ") VALUES (?, ?, GeomFromWKB(?, 4326))");

        psInserStreetCities = createPreparedStatement("INSERT INTO " + TN_STREET_IN_CITIES + " ( " + COL_STREET_ID + ", "
                    + COL_CITY_ID + " ) VALUES (?, ?)");

        psSelectStreet = createPreparedStatement(
                "SELECT " + COL_ID + ", "  + COL_NAME + ", AsBinary(" + COL_GEOM + ")" +
                        " FROM " + TN_STREETS +
                        " WHERE " + COL_ID + "=? ");

        psUpdateStreet = createPreparedStatement("UPDATE "+TN_STREETS +
                " SET "+COL_GEOM+" = GeomFromWKB(?, 4326) WHERE "+COL_ID+" = ?");

        psSelectNereastCities = createPreparedStatement(
                "SELECT "+COL_TYPE+", Cities."+COL_ID+", "+COL_NAME+",  AsBinary("+COL_CENTER_GEOM+"), "+
                        " ((X(center)-?)*(X(center)-?)) + ((Y(center)-?)*(Y(center)-?)) as distance" +
                        " FROM " + TN_CITIES +
                        " JOIN cityTiles ON Cities.id = cityTiles.id" +
                        " WHERE xtile = ? AND ytile = ?" +
                        " ORDER By distance" +
                        " LIMIT ?");

        psSimplifyStretGeom = createPreparedStatement(
                "UPDATE "+ TN_STREETS + " Set "+COL_GEOM+" = " +
                        "GeomFromWKB(?, 4326) where "+COL_ID+" = ?" );

        psSimplifyCityGeom = createPreparedStatement(
                "UPDATE "+ TN_CITIES + " Set "+COL_GEOM+" = " +
                        "GeomFromWKB(?, 4326) where "+COL_ID+" = ?" );

        psDeleteStreet = createPreparedStatement("DELETE FROM " + TN_STREETS + " WHERE "+COL_ID+" = ? ");

        psDeleteStreetCities = createPreparedStatement("DELETE FROM " + TN_STREET_IN_CITIES + " WHERE "+COL_STREET_ID+" = ? ");

	}

    /**
     * Drop tables and indexes for addresses
     */
    @Override
    protected void cleanTables() {
        String sql = "";
        try {

            // remove indexes

            sql = "DROP INDEX IF EXISTS idx_cities_name";
            executeStatement(sql);

            sql = "DROP INDEX IF EXISTS idx_streets_name";
            executeStatement(sql);

            sql = "DROP INDEX IF EXISTS  idx_cities_tiles" ;
            executeStatement(sql);

            sql =  "SELECT DisableSpatialIndex ('" + TN_STREETS + "', '"+COL_GEOM+"')";
            executeStatement(sql);
            sql = "DROP TABLE IF EXISTS  idx_"+ TN_STREETS +"_"+COL_GEOM;
            executeStatement(sql);

            sql = "SELECT DisableSpatialIndex('" + TN_CITIES + "', '"+COL_CENTER_GEOM+"')";
            executeStatement(sql);
            sql = "DROP TABLE IF EXISTS  idx_"+ TN_CITIES +"_"+COL_CENTER_GEOM;
            executeStatement(sql);

            sql = "SELECT DisableSpatialIndex('" + TN_CITIES + "', '"+COL_GEOM+"')";
            executeStatement(sql);
            sql = "DROP TABLE IF EXISTS  idx_"+ TN_CITIES +"_"+COL_GEOM;
            executeStatement(sql);


            sql ="DROP TABLE IF EXISTS  "+TN_CITIES ;
            executeStatement(sql);

            sql = "DROP TABLE IF EXISTS  "+TN_STREETS;
            executeStatement(sql);

            sql = "DROP TABLE IF EXISTS  "+ TN_STREET_IN_CITIES;
            executeStatement(sql);

            sql = "DROP TABLE IF EXISTS  cityTiles";
            executeStatement(sql);


        } catch (SQLException e) {
            Logger.e(TAG, "cleanTables(), problem with query: " + sql, e);
            e.printStackTrace();
        }
    }

    @Override
	protected void setTables() throws SQLException {

        // TABLE FOR (CITIES) PLACES

        String sql = "CREATE TABLE "+TN_CITIES+" (";
		sql += COL_ID+" BIGINT NOT NULL PRIMARY KEY,";
		sql += COL_TYPE+" INT NOT NULL,";
		sql += COL_NAME+" TEXT NOT NULL)";
		executeStatement(sql);
		// creating a POINT Geometry column
		sql = "SELECT AddGeometryColumn('"+TN_CITIES+"', ";
		sql += "'"+COL_CENTER_GEOM+"', 4326, 'POINT', 'XY')";
		executeStatement(sql);
        // creating a Boundary Geometry column
        sql = "SELECT AddGeometryColumn('"+TN_CITIES+"', ";
        sql += "'"+COL_GEOM+"', 4326, 'MULTIPOLYGON', 'XY')";
        executeStatement(sql);

        // TABLE FOR STREETS

        sql = "CREATE TABLE "+TN_STREETS+" (";
        sql += COL_ID+" BIGINT NOT NULL PRIMARY KEY,";
        //sql += COL_CITY_ID+" BIGINT NOT NULL,";
        //sql += COL_CITY_PART_ID+" BIGINT,";
        sql += COL_NAME+" TEXT NOT NULL";
        //sql += "FOREIGN KEY("+COL_CITY_ID+") REFERENCES cities(id)";
        //sql += "FOREIGN KEY("+COL_CITY_PART_ID+") REFERENCES cities(id)";
        sql +=        ")";
        executeStatement(sql);

        // creating a POINT Geometry column
        sql = "SELECT AddGeometryColumn('"+TN_STREETS+"', ";
        sql += "'"+COL_GEOM+"', 4326, 'MULTILINESTRING', 'XY')";
        executeStatement(sql);

        //
        sql = "CREATE TABLE "+ TN_STREET_IN_CITIES +" (";
        sql += COL_STREET_ID+" BIGINT NOT NULL,";
        sql += COL_CITY_ID+" BIGINT NOT NULL";
        sql +=        ")";
        executeStatement(sql);

        // JOIN TABLE CITY TILES
        sql = "CREATE TABLE cityTiles (";
        sql += COL_ID+" BIGINT, ";
        sql += "xtile INT,";
        sql += "ytile INT )";

        executeStatement(sql);
	}

    @Override
    public void destroy () throws SQLException {

        commit(false);

        String sql = "CREATE INDEX idx_cities_name ON " + TN_CITIES +
                " (" + COL_NAME+ ")";
        executeStatement(sql);

        sql = "CREATE INDEX idx_streets_name ON " + TN_STREETS +
                " (" + COL_NAME+  ")";
        executeStatement(sql);

        sql = "SELECT CreateSpatialIndex('" + TN_STREETS + "', '"+COL_GEOM+"')";
        executeStatement(sql);


        super.destroy();
    }

    public void createCityBoundaryIndex () {
        try {
            commit(false);
            String sql = "SELECT CreateSpatialIndex('" + TN_CITIES + "', '"+COL_GEOM+"')";

            executeStatement(sql);
        } catch (SQLException e) {
            Logger.e(TAG, "createCityBoundaryIndex(), problem with query", e);
            e.printStackTrace();
        }
    }


    public void crateCityCenterIndex () {
        try {
            commit(false);
            String sql = "SELECT CreateSpatialIndex('" + TN_CITIES + "', '"+COL_CENTER_GEOM+"')";

            executeStatement(sql);
        } catch (SQLException e) {
            Logger.e(TAG, "createCityCenterIndex(), problem with query", e);
            e.printStackTrace();
        }
    }

    public void createCityTilesIndex () {
        try {
            commit(false);
            String sql = "CREATE INDEX idx_cities_tiles ON cityTiles" +
                    " (xtile, ytile)";

            executeStatement(sql);
        } catch (SQLException e) {
            Logger.e(TAG, "crateCityTilesIndex(), problem with query", e);
            e.printStackTrace();
        }
    }
//	@Override
//	protected void insertObject(AOsmObject obj, Statement stmt) {
//		// check data
//		if (!(obj instanceof OsmPoi)) {
//			return;
//		}
//		
//		OsmPoi poi = (OsmPoi) obj;
//		StringBuilder sb = new StringBuilder();
//		try {
//			// generate query
//			sb.append("INSERT INTO points (type, name, desc, geom) VALUES (");
//			sb.append("'").append(getEscapedText(poi.getType())).append("',");
//			sb.append("'").append(getEscapedText(poi.getName())).append("',");
//			sb.append("'").append(getEscapedText(poi.getDescription())).append("',");
//			sb.append("GeomFromText('POINT(").append(poi.getLon()).append(" ").
//			append(poi.getLat()).append(")', 4326))");
//				
//			// execute query
//			stmt.executeUpdate(sb.toString());
//		} catch (SQLException e) {
//			log.error("insertPoi(), problem with query:" + sb.toString(), e);
//		}
//	}


    /**************************************************/
    /*                  INSERT PART                   */
    /**************************************************/

    public void insertCity(City city, Boundary boundary) {

        try {
            //Logger.i(TAG, "Insert city:  " + city.toString());
            //TODO try to define geometry as binary
            psInsertCity.clearParameters();

            psInsertCity.setLong(1, city.getId());
            psInsertCity.setInt(2, city.getType().getTypeCode());
            psInsertCity.setString(3, city.getName());
            //psInsertCity.setBinaryStream(4, new ByteArrayInputStream(wkbWriter.write(city.getCenter())));
            psInsertCity.setBytes(4, wkbWriter.write(city.getCenter()));

            if (boundary != null){
                psInsertCity.setBytes(5, wkbWriter.write(boundary.getGeom()));
            }
            psInsertCity.execute();

            // define which tile covers this city
            insertCityTile(city, boundary);

        } catch (SQLException e) {
            Logger.e(TAG, "insertCity(), problem with query", e);
            e.printStackTrace();
        }
    }

    private void insertCityTile (City city, Boundary boundary){

        int[] minMaxTiles;
        if (boundary == null){
            minMaxTiles = getMinMaxTile(city.getCenter());
        }
        else {
            minMaxTiles = getMinMaxTile(boundary.getGeom());
        }

        try {
            for (int x=minMaxTiles[0]; x <= minMaxTiles[2]; x++) {
                for (int y=minMaxTiles[1]; y <= minMaxTiles[3]; y++) {
                    psInsertCityTile.setLong(1, city.getId());
                    psInsertCityTile.setInt(2, x);
                    psInsertCityTile.setInt(3, y);

                    psInsertCityTile.addBatch();
                }
            }
            psInsertCityTile.executeBatch();
        } catch (SQLException e) {
            Logger.e(TAG, "insertCityTile(), problem with query", e);
            e.printStackTrace();
        }
    }
    /**
     * return min max x and y tiles
     * @param geom
     * @return xmin, ymin, xmax, ymax
     */
    private int[] getMinMaxTile (Geometry geom) {

        Envelope envelope = geom.getEnvelopeInternal();

        int xTileMin = (int)Math.floor((envelope.getMinX() + 180) / 360 * 512 );
        int xTileMax = (int)Math.floor((envelope.getMaxX() + 180) / 360 * 512 );
        int yTileMin = (int)Math.floor((envelope.getMinY() + 90) / 180 * 256 );
        int yTileMax = (int)Math.floor((envelope.getMaxY() + 90) / 180 * 256 );

        return new int[] {xTileMin, yTileMin,xTileMax,yTileMax};
    }

    private int[] getTile (Point point){
        int xTile = (int)Math.floor((point.getX() + 180) / 360 * 512 );
        int yTile = (int)Math.floor((point.getY() + 90) / 180 * 256 );

        return new int[] {xTile, yTile};
    }


    /**
     * Insert street into Address database
     * @param street street to insert
     * @return id of inserted street. This is not OSM id
     */
    public long insertStreet (Street street){
        try {
            long id = streetIdSequence++;

            psInsertStreet.clearParameters();

            psInsertStreet.setLong(1, id);
            //psInsertStreet.setLong(2, street.getCityId());
           // psInsertStreet.setLong(3, street.getCityPartId());
            psInsertStreet.setString(2, street.getName());
            //psInsertStreet.setBinaryStream(5, new ByteArrayInputStream());

            MultiLineString mls = street.getGeometry();
            if (!mls.isValid()){
                Logger.w(TAG, "insertStreet: not valid geom " + street.toString() );
            }
            if (mls.isEmpty()){
                Logger.w(TAG, "insertStreet: empty geom " + street.toString() );
            }

            wkbWriter = new WKBWriter();
            psInsertStreet.setBytes(3, wkbWriter.write(street.getGeometry()));

            psInsertStreet.execute();

            List<Long> cityIds = street.getCityIds();
            for (Long cityId : cityIds) {

                psInserStreetCities.setLong(1,id);
                psInserStreetCities.setLong(2,cityId);

                psInserStreetCities.addBatch();
            }
            psInserStreetCities.executeBatch();
            return id;

        } catch (SQLException e) {
            Logger.e(TAG, "insertStreet(), problem with query", e);
            e.printStackTrace();
            return 0;
        }
    }


    /**************************************************/
    /*                  SELECT PART                   */
    /**************************************************/


    /**
     * Load street from database, The cityIds are not loaded
     * @param streetId id of street
     * @return street or null if street with such id is not in DB
     */
    public Street selectStreet(long streetId) {

        Street streetLoaded = null;
        try{
            psSelectStreet.setLong(1, streetId);
            ResultSet rs = psSelectStreet.executeQuery();

            while (rs.next()){

                streetLoaded = new Street();
                streetLoaded.setId(rs.getLong(1));
                streetLoaded.setName(rs.getString(2));

                byte[] data = rs.getBytes(3);
                if (data == null){
                    Logger.i(TAG, "Geom is empty for street: " + streetId);
                }
                else {
                    MultiLineString mls = (MultiLineString) wkbReader.read(data);
                    streetLoaded.setGeometry(mls);
                }
            }

            return streetLoaded;

        } catch (SQLException e) {
            Logger.e(TAG, "selectStreet(), problem with query", e);
            e.printStackTrace();
        } catch (ParseException e) {
            Logger.e(TAG, "selectStreet(), problem with geometry parsing", e);
            e.printStackTrace();
        }

        return streetLoaded;
    }


    public long updateStreet(Street street) {

        long streetId = street.getId();
        try{
            psUpdateStreet.setBytes(1, wkbWriter.write(street.getGeometry()));
            psUpdateStreet.setLong(2, street.getId());
            psUpdateStreet.execute();

            // insert only not existed cityIds for this street
            List<Long> cityIds = street.getCityIds();
            for (Long cityId : cityIds) {

                psInserStreetCities.setLong(1,streetId);
                psInserStreetCities.setLong(2,cityId);

                psInserStreetCities.addBatch();
            }
            psInserStreetCities.executeBatch();

            return streetId;
        } catch (SQLException e) {
            Logger.e(TAG, "updateStreet(), problem with query", e);
            e.printStackTrace();
        }
        return 0;
    }

    /**
     * Loads the same street (with the same name) that can are in cities with specified list of ids
     * @param street define name of street and possible cityIds where street can be in
     * @return
     */
    public List<Street> selectStreetInCities(Street street) {

        // more then one street can be loaded
        Map<Long, Street> loadedStreetMap  = new HashMap<>();
        String sql = "";
        try {
            List<Long> cityIds = street.getCityIds();

            if (cityIds.size() == 0){
                Logger.w(TAG, "selectStreetInCities:  street has no cityId, street " + street.toString() );
            }

            // prepare list of city ids
            String isInIds = "";
            for(int i=0, size = cityIds.size(); i < size; i++){
                if (i == 0){
                    isInIds += "(" + String.valueOf(cityIds.get(i)) ;
                }
                else{
                    isInIds += ", " + String.valueOf(cityIds.get(i));
                }
            }
            isInIds += ")";

            String name = escapeSqlString (street.getName());

            sql = "SELECT " + COL_STREET_ID+ ", " + COL_CITY_ID+ ", "+ COL_NAME + ", AsBinary(" + COL_GEOM + ")";
            sql += " FROM " + TN_STREETS + " JOIN " + TN_STREET_IN_CITIES;
            sql += " ON " + COL_ID + " = " + COL_STREET_ID;
            sql += " WHERE " + COL_NAME + " like '" + name +"'";
            sql += " AND " + COL_CITY_ID + " IN " + isInIds;

            if (name.equals("Radwanderweg Glinde - Trittau")){
                Logger.i(TAG, "select in cities: " + sql );
            }


            ResultSet rs = getStmt().executeQuery(sql);

            while (rs.next()){

                long streetId = rs.getLong(1);

                // check if exist street in map from previous result
                Street streetLoaded = loadedStreetMap.get(streetId);


                if (streetLoaded == null){
                    // load completly whole street
                    streetLoaded = new Street();

                    streetLoaded.setId(rs.getLong(1));
                    streetLoaded.addCityId (rs.getLong(2));
                    streetLoaded.setName(rs.getString(3));


                    byte[] data = rs.getBytes(4);
                    if (data == null){
                        Logger.i(TAG, "Street is empty " + street.toString());
                    }
                    else {
                        wkbReader = new WKBReader();
                        MultiLineString mls = (MultiLineString) wkbReader.read(data);
                        streetLoaded.setGeometry(mls);
                    }

                    loadedStreetMap.put(streetId, streetLoaded);
                }
                else {
                    // street with id was loaded in previous result now only update the list of cityIds
                    long cityId = rs.getLong(2);
                    streetLoaded.addCityId(cityId);
                    loadedStreetMap.put(streetId, streetLoaded);
                }
            }

        } catch (SQLException e) {
            Logger.e(TAG, "selectStreetInCities(), query: " + sql);
            Logger.e(TAG, "selectStreetInCities(), problem with query", e);
            e.printStackTrace();
        }
        catch (ParseException e) {
            Logger.e(TAG, "selectStreetInCities(), query: " + sql);
            Logger.e(TAG, "selectStreetInCities(), problem with parsing wkb data", e);
            e.printStackTrace();
        }
        return new ArrayList<>(loadedStreetMap.values());
    }


    public List<City> loadNearestCities(Point streetCentroid, int limit) {

        List<City> cities = new ArrayList<>();

        try {
            double x = streetCentroid.getX();
            double y = streetCentroid.getY();
            int[] tiles = getTile(streetCentroid);

            psSelectNereastCities.clearParameters();

            psSelectNereastCities.setDouble(1,x);
            psSelectNereastCities.setDouble(2,x);

            psSelectNereastCities.setDouble(3,y);
            psSelectNereastCities.setDouble(4,y);

            psSelectNereastCities.setInt(5,tiles[0]);
            psSelectNereastCities.setInt(6,tiles[1]);

            psSelectNereastCities.setInt(7,limit);

            ResultSet rs = psSelectNereastCities.executeQuery();

            while (rs.next()){
                City.CityType type = City.CityType.createFromTypeCodeValue(rs.getInt(1));
                if (type == null){
                    continue;
                }
                City city = new City(type);
                city.setId(rs.getLong(2));
                city.setName(rs.getString(3));
                Point point = (Point) wkbReader.read(rs.getBytes(4));
                city.setCenter(point);
                cities.add(city);
            }

        } catch (Exception e) {
            Logger.e(TAG, "loadNearestCities(), problem with query", e);
            e.printStackTrace();
        }
        return cities;
    }



    /**************************************************/
    /*                 SIMPLIFY
    /**************************************************/

    public void simplifyStreetGeoms (){

        GeometryFactory geometryFactory = new GeometryFactory();
        for (int streetId=1; streetId < streetIdSequence; streetId++){

            try {
                Street street = selectStreet(streetId);

                if (street == null || street.getGeometry().getCoordinates().length <= 4){
                    continue;
                }

                Geometry geometry = DouglasPeuckerSimplifier.simplify(street.getGeometry(), 0.00005);

                MultiLineString mls;
                if (geometry instanceof LineString){
                    mls = geometryFactory.createMultiLineString(new LineString[]{(LineString) geometry});
                }
                else {
                    mls = (MultiLineString) geometry;
                }

                psSimplifyStretGeom.clearParameters();
                psSimplifyStretGeom.setLong(2, streetId);
                psSimplifyStretGeom.setBytes(1, wkbWriter.write(mls));
                psSimplifyStretGeom.execute();
//
//                String sql  = "UPDATE "+ TN_STREETS + " Set "+COL_GEOM+" = " +
//                " (SELECT SimplifyPreserveTopology("+COL_GEOM+", 0.01) from "+ TN_STREETS +
//                " where "+COL_ID+" = "+streetId+ ") where "+COL_ID+" = "+streetId;
//
//                executeStatement(sql);

//                String sql =
//                        "UPDATE Streets Set geom = " +
//                                "(select SimplifyPreserveTopology(geom, 0.0005) from Streets where id = "+streetId+") " +
//                                "where id = "+streetId;
//                executeStatement(sql);

            }
            catch (SQLException e) {
               Logger.i(TAG, "Exception when simplify street: " + streetId);
               continue;
            }
        }
    }

    public void simplifyCityGeom(City city, Boundary boundary) {

        GeometryFactory geometryFactory = new GeometryFactory();
        try {

            Geometry geometry = DouglasPeuckerSimplifier.simplify(boundary.getGeom(), 0.001);

            MultiPolygon mp;
            if (geometry instanceof Polygon) {
                mp = geometryFactory.createMultiPolygon(new Polygon[]{(Polygon) geometry});
            } else {
                mp = (MultiPolygon) geometry;
            }
            psSimplifyCityGeom.clearParameters();
            psSimplifyCityGeom.setLong(2, city.getId());
            psSimplifyCityGeom.setBytes(1, wkbWriter.write(mp));
            psSimplifyCityGeom.execute();


        } catch (SQLException e) {
            Logger.i(TAG, "Exception when simplify city: " + city.getId());
        }
    }

    public void deleteStreet (long streetId){
        try {

            psDeleteStreet.clearParameters();
            psDeleteStreet.setLong(1, streetId);
            psDeleteStreet.execute();

            psDeleteStreetCities.clearParameters();
            psDeleteStreetCities.setLong(1, streetId);
            psDeleteStreetCities.execute();

        } catch (SQLException e) {
            Logger.i(TAG, "deleteStreet(): Exception when delete street with id: " + streetId);
        }
    }


    /**************************************************/
    /*                OTHER TOOLS
    /**************************************************/

    /**
     * Escape special characters for SQL query
     * @param name String to escape
     * @return escaped string
     */
    private String escapeSqlString(String name) {
        name = name.replace("'", "''");
        return name;
    }
}
