package com.asamm.osmTools.generatorDb.db;

import com.asamm.osmTools.generatorDb.address.Boundary;
import com.asamm.osmTools.generatorDb.address.City;
import com.asamm.osmTools.generatorDb.address.Street;
import com.asamm.osmTools.utils.Logger;
import com.vividsolutions.jts.geom.*;
import com.vividsolutions.jts.io.ParseException;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

import static com.asamm.locus.features.dbPoi.DbPoiConst.*;

public class DatabaseAddress extends ADatabaseHandler {

    private static final String TAG = DatabaseAddress.class.getSimpleName();

    private static final int tilesNum = 512;

    /** increment for street. Streets have own ids there is NO relation with OSM id*/
    private long streetIdSequence = 1;
    /** Precompiled statement for inserting cities into db*/
    private PreparedStatement psInsertCity;
    private PreparedStatement psInsertCityTile;
    /** statement for insert new street into database */
    private PreparedStatement psInsertStreet;
    /** Statement select street from database */
    private PreparedStatement psSelectStreet;
    /** Statement to update street geometry */
    private PreparedStatement psUpdateStreet;

    private PreparedStatement psSelectNereastCities;

    ByteArrayInputStream bais;

    public DatabaseAddress(File file) throws Exception {
		super(file, true);

        initialize();

        // create prepared statemennts
        psInsertCity = createPreparedStatement(
                "INSERT INTO "+ TN_CITIES +" ("+COL_ID+", "+COL_TYPE+", "+COL_NAME+", "+COL_CENTER_GEOM+", "+COL_GEOM+
                 ") VALUES (?, ?, ?, GeomFromWKB(?, 4326), GeomFromWKB(?, 4326))");

        psInsertCityTile = createPreparedStatement("INSERT INTO cityTiles ("+COL_ID+", xtile, ytile) " +
                "VALUES (?, ?, ? )");

        psInsertStreet = createPreparedStatement(
                "INSERT INTO "+TN_STREETS+" ("+COL_ID+", "+COL_CITY_ID+", "+COL_CITY_PART_ID+", "+COL_NAME+", "+COL_GEOM+
                ") VALUES (?, ?, ?, ?, GeomFromWKB(?, 4326))");

        psSelectStreet = createPreparedStatement(
                "SELECT "+COL_ID+", "+COL_CITY_ID+", "+COL_CITY_PART_ID+", "+COL_NAME+", AsBinary("+COL_GEOM+") " +
                "FROM "+TN_STREETS +
                " WHERE "+COL_CITY_ID+"=? AND "+COL_CITY_PART_ID+"=? and "+COL_NAME+"=?");

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
	}

	@Override
	protected void setTables(Connection conn) throws SQLException {

        // TABLE FOR (CITIES) PLACES

        String sql = "CREATE TABLE "+TN_CITIES+" (";
		sql += COL_ID+" BIGINT NOT NULL PRIMARY KEY,";
		sql += COL_TYPE+" TEXT NOT NULL,";
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
        sql += COL_CITY_ID+" BIGINT NOT NULL,";
        sql += COL_CITY_PART_ID+" BIGINT,";
        sql += COL_NAME+" TEXT NOT NULL,";
        sql += "FOREIGN KEY("+COL_CITY_ID+") REFERENCES cities(id)";
        sql += "FOREIGN KEY("+COL_CITY_PART_ID+") REFERENCES cities(id)";
        sql +=        ")";
        executeStatement(sql);
        // creating a POINT Geometry column
        sql = "SELECT AddGeometryColumn('"+TN_STREETS+"', ";
        sql += "'"+COL_GEOM+"', 4326, 'MULTILINESTRING', 'XY')";
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

        sql = "CREATE INDEX idx_streets_city_id ON " + TN_STREETS +
                " (" + COL_CITY_ID+  ")";
        executeStatement(sql);

        sql = "CREATE INDEX idx_streets_name ON " + TN_STREETS +
                " (" + COL_NAME+  ")";
        executeStatement(sql);

        sql = "SELECT CreateSpatialIndex('" + TN_STREETS + "', 'geom')";
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
            psInsertCity.setString(2, City.CityType.valueToString(city.getType()));
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
            psInsertStreet.setLong(2, street.getCityId());
            psInsertStreet.setLong(3, street.getCityPartId());
            psInsertStreet.setString(4, street.getName());
            //psInsertStreet.setBinaryStream(5, new ByteArrayInputStream());
            psInsertStreet.setBytes(5, wkbWriter.write(street.getGeometry()));

            psInsertStreet.execute();
            return id;

        } catch (SQLException e) {
            Logger.e(TAG, "insertWayStreetToCache(), problem with query", e);
            e.printStackTrace();
            return 0;
        }
    }


    /**************************************************/
    /*                  SELECT PART                   */
    /**************************************************/


    /**
     * Load street from database
     * @param street Define street name to search
     * @return
     */
    public List<Street> selectStreet(Street street) {
        List<Street> streets = new ArrayList<>();

        try{
            psSelectStreet.clearParameters();
            psSelectStreet.setLong(1, street.getCityId());
            psSelectStreet.setLong(2, street.getCityPartId());
            psSelectStreet.setString(3, street.getName());

            ResultSet rs = psSelectStreet.executeQuery();

            while (rs.next()){

                Street streetLoaded = new Street();
                streetLoaded.setId(rs.getLong(1));
                streetLoaded.setCityId(rs.getLong(2));
                streetLoaded.setCityPartId(rs.getLong(3));
                streetLoaded.setName(rs.getString(4));
//                byte[] data = rs.getBytes(4);
//                MultiLineString mls = (MultiLineString) wkbReader.read(data);
                byte[] data = rs.getBytes(5);
                if (data == null){
                    Logger.i(TAG, "Street is empty " + street.toString());
                }
                else {
                    MultiLineString mls = (MultiLineString) wkbReader.read(rs.getBytes(5));
                    streetLoaded.setGeometry(mls);
                }

                streets.add(streetLoaded);
            }

            return streets;

        } catch (SQLException e) {
            Logger.e(TAG, "selectStreet(), problem with query", e);
            e.printStackTrace();
        } catch (ParseException e) {
            Logger.e(TAG, "selectStreet(), problem with geometry parsing", e);
            e.printStackTrace();
        }

        return streets;
    }


    public long updateStreet(Street street) {

        try{
            psUpdateStreet.setBytes(1, wkbWriter.write(street.getGeometry()));
            psUpdateStreet.setLong(2, street.getId());
            psUpdateStreet.execute();

            return street.getId();
        } catch (SQLException e) {
            Logger.e(TAG, "updateStreet(), problem with query", e);
            e.printStackTrace();
        }
        return 0;
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
                City.CityType type = City.CityType.createFromPlaceValue(rs.getString(1));
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



}
