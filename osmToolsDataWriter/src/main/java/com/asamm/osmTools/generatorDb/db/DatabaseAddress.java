package com.asamm.osmTools.generatorDb.db;

import com.asamm.osmTools.generatorDb.address.*;
import com.asamm.osmTools.generatorDb.utils.Utils;
import com.asamm.osmTools.utils.Logger;
import com.vividsolutions.jts.geom.*;
import com.vividsolutions.jts.geom.prep.PreparedGeometry;
import com.vividsolutions.jts.geom.prep.PreparedGeometryFactory;
import com.vividsolutions.jts.index.quadtree.Quadtree;
import com.vividsolutions.jts.index.strtree.STRtree;
import com.vividsolutions.jts.io.ParseException;
import com.vividsolutions.jts.io.WKBReader;
import com.vividsolutions.jts.simplify.DouglasPeuckerSimplifier;
import gnu.trove.iterator.TLongIterator;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.hash.THashMap;
import gnu.trove.set.hash.TLongHashSet;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.util.*;

import static com.asamm.locus.features.dbAddressPoi.DbAddressPoiConst.*;
import static com.asamm.locus.features.dbAddressPoi.DbAddressPoiConst.COL_ID;
import static com.asamm.locus.features.dbAddressPoi.DbAddressPoiConst.TN_STREET_IN_CITIES;

public class DatabaseAddress extends ADatabaseHandler {

    private static final String TAG = DatabaseAddress.class.getSimpleName();

    public static final String DEFAULT_LANG_CODE = "def";

    /** increment for street. Streets have own ids there is NO relation with OSM id*/
    private int streetIdSequence = 0;

    private long housesIdSequence = 0;

    public long housesPreparedAsBlobForStreets = 0;

    /** Counter for used postCodes */
    private int postCodesIdSequence = 0;

    /** Map store the reference between postCode and num of row in table of postCodes */
    private THashMap<String, Integer> postCodesMap;

    /** Id of streets which geometry is from path or tracks */
    private TIntArrayList pathStreetIds;

    /** Precompiled statement for inserting cities into db*/
    private PreparedStatement psInsertCity;

    /** Insert international names of city*/
    private PreparedStatement psInsertCityNames;

    //private PreparedStatement psInsertCityTile;
    /** statement for insert new street into database */
    private PreparedStatement psInsertStreet;
    /** For insert possible cities where is street in*/
    private PreparedStatement psInsertStreetCities;

    /** Insert house object into houses table*/
    private PreparedStatement psInsertHouse;
    /** Insert house that is not used in db into table that collect all removed houses*/
    private PreparedStatement psInsertRemovedHouse;
    /** Insert new postCode into table of postCodes*/
    private PreparedStatement psInsertPostCode;

    /** Statement select street from database */
    private PreparedStatement psSelectStreet;
    /** When looking for street specific name in city with name*/
    private PreparedStatement psSelectStreetByNames;

    private PreparedStatement psSelectHousesInStreet;
    /** Statement to update street geometry */
    private PreparedStatement psUpdateStreet;

    private PreparedStatement psSimplifyStretGeom;

    private PreparedStatement psSimplifyCityGeom;

    private PreparedStatement psDeleteStreet;

    private PreparedStatement psDeleteStreetCities;

    ByteArrayInputStream bais;


    private static boolean deleteOldDb = false;

    /** Only for testing when table houses contains all house values and table is not deleted*/
    private static boolean hasHousesTableWithGeom = false;

    /** JTS in memory index of geometries of joined streets*/
    private STRtree streetGeomIndex;

    /** Custom geom index only for dummy streets created from cities or places*/
    private Quadtree dummyStreetGeomIndex;


    public DatabaseAddress(File file) throws Exception {

        super(file, deleteOldDb);

        if (!deleteOldDb){
            cleanTables();
        }

        setTables();

        initPreparedStatements();

        streetGeomIndex = new STRtree();
        dummyStreetGeomIndex = new Quadtree();

        postCodesMap = new THashMap<>();
        pathStreetIds = new TIntArrayList();
	}

    private void initPreparedStatements() throws SQLException {
        // create prepared statemennts
        psInsertCity = createPreparedStatement(
                "INSERT INTO "+ TN_CITIES +" ("+COL_ID+", "+COL_TYPE+", "+COL_PLACE_NAME+", "+COL_CENTER_GEOM+", "+COL_GEOM+
                        ") VALUES (?, ?, ?, GeomFromWKB(?, 4326), GeomFromWKB(?, 4326))");

        psInsertCityNames = createPreparedStatement( "INSERT INTO "+ TN_CITIES_NAMES +
                " ("+COL_CITY_ID+", "+COL_LANG_CODE+", "+COL_NAME+", "+COL_NAME_NORM+
                " ) VALUES (?, ?, ?, ?)");

        psInsertStreet = createPreparedStatement(
                "INSERT INTO "+TN_STREETS+" ("+COL_ID+", "+COL_NAME+", " + COL_NAME_NORM + ", " +COL_GEOM+
                        ") VALUES (?, ?, ?, GeomFromWKB(?, 4326))");

        psInsertStreetCities = createPreparedStatement("INSERT INTO " + TN_STREET_IN_CITIES + " ( " + COL_STREET_ID + ", "
                + COL_CITY_ID + " ) VALUES (?, ?)");

        psInsertHouse = createPreparedStatement(
                "INSERT INTO "+ TN_HOUSES +" ("+COL_ID+", "+COL_STREET_ID+", " + COL_STREET_NAME + ", "  +COL_NUMBER+", " +
                        COL_NAME+", "+  COL_POST_CODE+", "+COL_DATA + ", " + COL_CENTER_GEOM +
                        ") VALUES (?, ?, ?, ?, ?, ?,  ?, GeomFromWKB(?, 4326))");

        psInsertRemovedHouse =  createPreparedStatement(
                "INSERT INTO "+ TN_HOUSES_REMOVED +" ("+COL_STREET_NAME+", "+COL_PLACE_NAME+",  "+COL_NUMBER+", " +COL_NAME+", "+COL_POST_CODE+", "+COL_CENTER_GEOM+
                        ") VALUES (?, ?, ?, ?, ?,  GeomFromWKB(?, 4326))");

        psInsertPostCode = createPreparedStatement(
                "INSERT INTO "+ TN_POSTCODES +" ("+COL_ID+", "+COL_POST_CODE+") VALUES (?, ?)");

        psSelectStreet = createPreparedStatement(
                "SELECT " + COL_ID + ", "  + COL_NAME + ", "+ COL_NAME_NORM + ", AsBinary(" + COL_GEOM + ")" +
                        " FROM " + TN_STREETS +
                        " WHERE " + COL_ID + "=? ");

        psSelectStreetByNames = createPreparedStatement(
                "SELECT " + TN_STREETS+"."+COL_ID + ", "  + TN_STREETS+ "."+COL_NAME + ", "+
                TN_STREETS+ "."+COL_NAME_NORM + ", AsBinary(" + TN_STREETS+ "."+COL_GEOM + ")" +
                        " FROM " + TN_STREETS +
                        " JOIN " + TN_STREET_IN_CITIES + " ON " + TN_STREET_IN_CITIES+ "." + COL_STREET_ID + " = " + TN_STREETS+ "." + COL_ID +
                        " JOIN " + TN_CITIES_NAMES + " ON " + TN_STREET_IN_CITIES+ "."+ COL_CITY_ID + " = " + TN_CITIES_NAMES + "." + COL_CITY_ID +

                        " WHERE (" + TN_CITIES_NAMES+ "." + COL_NAME_NORM + " = ?) "
                        + " AND  (" +TN_STREETS+ "." + COL_NAME_NORM + " = ?) ");

        psSelectHousesInStreet = createPreparedStatement(
                "SELECT " + COL_DATA + " FROM " + TN_HOUSES + " WHERE " + COL_STREET_ID + "=? ");

        psUpdateStreet = createPreparedStatement("UPDATE "+TN_STREETS +
                " SET "+COL_DATA+" = ? WHERE "+COL_ID+" = ?");

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

            sql = "DROP INDEX IF EXISTS " + IDX_CITIES_NAMES_NAMENORM;
            executeStatement(sql);

            sql = "DROP INDEX IF EXISTS " + IDX_CITIES_NAMES_CITYID;
            executeStatement(sql);

            sql = "DROP INDEX IF EXISTS " + IDX_STREETS_NAMENORM;
            executeStatement(sql);

            sql = "DROP INDEX IF EXISTS " + IDX_STREETS_IN_CITIES_CITYID;
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

            sql ="DROP TABLE IF EXISTS  "+TN_CITIES_NAMES ;
            executeStatement(sql);

            sql = "DROP TABLE IF EXISTS  "+TN_STREETS;
            executeStatement(sql);

            sql = "DROP TABLE IF EXISTS  "+ TN_STREET_IN_CITIES;
            executeStatement(sql);

            sql = "DROP TABLE IF EXISTS  "+ TN_HOUSES;
            executeStatement(sql);

            sql = "DROP VIEW IF EXISTS  "+ VIEW_HOUSES;
            executeStatement(sql);

            sql = "DROP TABLE IF EXISTS  "+ TN_HOUSES_REMOVED;
            executeStatement(sql);

            sql = "DROP TABLE IF EXISTS  "+ TN_POSTCODES;
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
		sql += COL_TYPE+" INT NOT NULL, ";
        sql += COL_PLACE_NAME+" TEXT ) ";
		executeStatement(sql);

		// creating a Center Geometry column fro Cities
		sql = "SELECT AddGeometryColumn('"+TN_CITIES+"', ";
		sql += "'"+COL_CENTER_GEOM+"', 4326, 'POINT', 'XY')";
		executeStatement(sql);

        // creating a Boundary Geometry column
        sql = "SELECT AddGeometryColumn('"+TN_CITIES+"', ";
        sql += "'"+COL_GEOM+"', 4326, 'MULTIPOLYGON', 'XY')";
        executeStatement(sql);

        // TABLE OF INTERNATIONAL CITY NAMES
        sql = "CREATE TABLE "+TN_CITIES_NAMES+" (";
        sql += COL_CITY_ID+" BIGINT NOT NULL,";
        sql += COL_LANG_CODE+" TEXT NOT NULL, ";
        sql += COL_NAME+" TEXT, ";
        sql += COL_NAME_NORM+" TEXT NOT NULL)";
        executeStatement(sql);

        // TABLE FOR STREETS

        sql = "CREATE TABLE "+TN_STREETS+" (";
        sql += COL_ID+" INT NOT NULL PRIMARY KEY,";
        sql += COL_NAME+" ,";
        sql += COL_NAME_NORM+" TEXT NOT NULL, ";
        sql += COL_DATA+" BLOB";
        sql +=        ")";
        executeStatement(sql);

        // creating a POINT Geometry column
        sql = "SELECT AddGeometryColumn('"+TN_STREETS+"', ";
        sql += "'"+COL_GEOM+"', 4326, 'MULTILINESTRING', 'XY')";
        executeStatement(sql);

        //
        sql = "CREATE TABLE "+ TN_STREET_IN_CITIES +" (";
        sql += COL_STREET_ID+" INT NOT NULL,";
        sql += COL_CITY_ID+" BIGINT NOT NULL";
        sql +=        ")";
        executeStatement(sql);

        // TABLE OF HOUSES

        sql = "CREATE TABLE "+TN_HOUSES+" (";
        sql += COL_ID+" BIGINT NOT NULL, ";
        sql += COL_STREET_ID + " INT NOT NULL, ";
        sql += COL_STREET_NAME + " TEXT, ";
        sql += COL_NUMBER+" TEXT NOT NULL, ";
        sql += COL_NAME+ " TEXT,  ";
        sql += COL_POST_CODE+" TEXT, "  ;
        sql += COL_DATA+" BLOB )" ;
        executeStatement(sql);

        sql = "SELECT AddGeometryColumn('"+TN_HOUSES+"', ";
        sql += "'"+COL_CENTER_GEOM+"', 4326, 'POINT', 'XY')";
        executeStatement(sql);


        sql = "CREATE TABLE "+TN_HOUSES_REMOVED+" (";
        sql += COL_STREET_NAME + " TEXT, ";
        sql += COL_PLACE_NAME + " TEXT, ";
        sql += COL_NUMBER+" TEXT NOT NULL, ";
        sql += COL_NAME+ " TEXT,  ";
        sql += COL_POST_CODE+" TEXT ) ";
        executeStatement(sql);

        // creating a Center Geometry column of centers of removed houses
        sql = "SELECT AddGeometryColumn('"+TN_HOUSES_REMOVED+"', ";
        sql += "'"+COL_CENTER_GEOM+"', 4326, 'POINT', 'XY')";
        executeStatement(sql);

        // TABLE OF POST CODES
        sql = "CREATE TABLE "+TN_POSTCODES+" (";
        sql += COL_ID + " INTEGER PRIMARY KEY, ";
        sql += COL_POST_CODE + " TEXT) ";
        executeStatement(sql);
	}

    @Override
    public void destroy () throws SQLException {

        commit(false);
        String sql = "";

        sql = "SELECT CreateSpatialIndex('" + TN_STREETS + "', '"+COL_GEOM+"')";
        executeStatement(sql);

        // compress street geom
        sql = "UPDATE " + TN_STREETS +" SET "+ COL_GEOM+ " = CompressGeometry("+COL_GEOM+")";
        executeStatement(sql);

        if ( !hasHousesTableWithGeom){
            sql = "DROP TABLE IF EXISTS  "+ TN_HOUSES;
            executeStatement(sql);
        }

        super.destroy();
    }

    /**************************************************/
    /*                  DB INDEXES PART                   */
    /**************************************************/

    public void buildCityBoundaryIndex() {
        try {
            commit(false);
            String sql = "SELECT CreateSpatialIndex('" + TN_CITIES + "', '"+COL_GEOM+"')";

            executeStatement(sql);
        } catch (SQLException e) {
            Logger.e(TAG, "buildCityBoundaryIndex(), problem with query", e);
            e.printStackTrace();
        }
    }

    /**
     * Build spatial index for city center point and index for city names
     */
    public void buildCityIndexes() {
        try {
            commit(false);
            String sql = "SELECT CreateSpatialIndex('" + TN_CITIES + "', '"+COL_CENTER_GEOM+"')";
            executeStatement(sql);

            sql = "CREATE INDEX "+IDX_CITIES_NAMES_NAMENORM+" ON " + TN_CITIES_NAMES +
                    " (" + COL_NAME_NORM+ ")";
            executeStatement(sql);

            sql = "CREATE INDEX " + IDX_CITIES_NAMES_CITYID + " ON " + TN_CITIES_NAMES +
                    " (" + COL_CITY_ID+ ")";
            executeStatement(sql);

        } catch (SQLException e) {
            Logger.e(TAG, "buildCityIndexes(), problem with query", e);
            e.printStackTrace();
        }
    }

    public void buildHouseIndexes () {
        try {
            commit(false);
            if (hasHousesTableWithGeom){
                String sql = "SELECT CreateSpatialIndex('" + TN_HOUSES + "', '"+COL_CENTER_GEOM+"')";
                executeStatement(sql);
            }

            // index for street id
            String sql = "CREATE INDEX " + IDX_HOUSES_STREETID + " ON " + TN_HOUSES +
                    " (" + COL_STREET_ID+ ")";
            executeStatement(sql);

//            else {
//                // Workaround how to access integer lat lon
//                String sql = "CREATE VIEW '" + VIEW_HOUSES + "' AS ";
//                sql += "SELECT ROWID AS ROWID, " + COL_ID + " AS " + COL_ID + " , " + COL_STREET_ID + " AS " + COL_STREET_ID + " , ";
//                sql += COL_NUMBER + " AS " + COL_NUMBER + " , " + COL_NAME + " AS " + COL_NAME + " , ";
//                sql += COL_POST_CODE + " AS " + COL_POST_CODE + " , ";
//                sql += COL_LON + " AS " + COL_LON + " , " + COL_LAT + " AS " + COL_LAT + " , ";
//                sql += " MakePoint ("+ COL_LON + " / 1.0e6,  " + COL_LAT + "/ 1.0e6, 4326) AS " + COL_CENTER_GEOM;
//                sql += " FROM " + TN_HOUSES;
//
//                executeStatement(sql);
//

//                // CREATE PSEUDO SPATIAL INDEX - see https://www.sqlite.org/rtree.html
//                sql = "CREATE VIRTUAL TABLE " + IDX_HOUSES_LAT_LON + " USING Rtree (";
//                sql += COL_ID + ", lonmin, lonmax, latmin, latmax)";
//                executeStatement(sql);
//
//                // FILL THE INDEX
//
//                sql = "INSERT INTO " + IDX_HOUSES_LAT_LON + " (";
//                sql += COL_ID + ", lonmin, lonmax, latmin, latmax)";
//                sql += " SELECT " + COL_ID + ", " + COL_LON + "/1.0e6, " + COL_LON + " / 1.0e6, ";
//                sql += COL_LAT + "/1.0e6, " + COL_LAT + " / 1.0e6 FROM " + TN_HOUSES;
//                executeStatement(sql);
//            }

        } catch (SQLException e) {
            Logger.e(TAG, "buildHouseIndexes(), problem with query", e);
            e.printStackTrace();
        }
    }

    /**
     * Build index for normalized street name and for Street_in_cities table
     */
    public void buildStreetNameIndex() {
        try {
            String sql = "CREATE INDEX "+IDX_STREETS_NAMENORM+" ON " + TN_STREETS +
                    " (" + COL_NAME_NORM+  ")";
            executeStatement(sql);

            sql = "CREATE INDEX "+IDX_STREETS_IN_CITIES_CITYID+" ON " + TN_STREET_IN_CITIES +
                    " (" + COL_CITY_ID+  ")";
            executeStatement(sql);

        } catch (SQLException e) {
            Logger.e(TAG, "buildStreetNameIndex(), problem with query", e);
            e.printStackTrace();
        }
    }

    /**************************************************/
    /*                  INSERT PART                   */
    /**************************************************/

    /**
     * Insert city object into address database
     * @param city city to insert
     * @param boundary boundary for city (can be null if no boundary is created for city)
     */
    public void insertCity(City city, Boundary boundary) {

        try {
            //Logger.i(TAG, "Insert city:  " + city.toString());
            psInsertCity.clearParameters();

            psInsertCity.setLong(1, city.getOsmId());
            psInsertCity.setInt(2, city.getType().getTypeCode());

            City parentCity = city.getParentCity();
            if (parentCity != null){
                psInsertCity.setString(3, parentCity.getName());
            }

            psInsertCity.setBytes(4, wkbWriter.write(city.getCenter()));
            if (boundary != null){
                psInsertCity.setBytes(5, wkbWriter.write(boundary.getGeom()));
            }
            psInsertCity.execute();

            // INSERT CITY NAMES

            // add default name into list of lang mutation
            city.addNameInternational(DEFAULT_LANG_CODE, city.getName());
            Set<Map.Entry<String, String>> entrySet = city.getNamesInternational().entrySet();
            for (Map.Entry<String, String> entry : entrySet){
                //Logger.i("TAG", " insertCity: lang Code= " + entry.getKey() + " name= " + entry.getValue() );
                psInsertCityNames.clearParameters();
                psInsertCityNames.setLong(1, city.getOsmId());
                psInsertCityNames.setString(2, entry.getKey());
                String name = entry.getValue();
                String nameNormalized = Utils.normalizeString(name);
                if ( !nameNormalized.equals(name)){
                    //store full name only if normalized name is different
                    psInsertCityNames.setString(3, name);
                }
                psInsertCityNames.setString(4, nameNormalized);
                psInsertCityNames.execute();
            }
            //psInsertCityNames.executeBatch();

        } catch (SQLException e) {
            Logger.e(TAG, "insertCity(), problem with query", e);
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
     * @param isDummy set true for dummy streets because of different index
     * @return id of inserted street. This is not OSM id
     */
    public long insertStreet(Street street, boolean isDummy){
        try {
            int id = streetIdSequence++;
            psInsertStreet.clearParameters();

            street.setId(id);
            psInsertStreet.setLong(1, street.getId());

            String name = street.getName();
            String nameNormalized = Utils.normalizeString(name);
            if ( !nameNormalized.equals(name)){
                //store full name only if normalized name is different
                psInsertStreet.setString(2, name);
            }
            psInsertStreet.setString(3, nameNormalized);
            psInsertStreet.setBytes(4, wkbWriter.write(street.getGeometry()));
            psInsertStreet.execute();

            //register street in JTS memory index - use it later for houses
            if (isDummy){
                // It's seem that QuadTree index has some issue with points. If I use centroid as
                // geom to index then query return all streets around
                Geometry geomFake = Utils.createRectangle(street.getGeometry().getCoordinate(), 50);
                dummyStreetGeomIndex.insert(geomFake.getEnvelopeInternal(), street);

            }
            else {
                streetGeomIndex.insert(street.getGeometry().getEnvelopeInternal(), street);
            }

            // register id of path or track street > to remove such street later if they are without houses
            if (street.isPath()){
                pathStreetIds.add(id);
            }

            // INSERT CONNECTION  TO CITIES

            TLongHashSet cityIds = street.getCityIds();
            TLongIterator iterator = cityIds.iterator();
            while (iterator.hasNext()){

                psInsertStreetCities.setLong(1, id);
                psInsertStreetCities.setLong(2, iterator.next());

                psInsertStreetCities.addBatch();
            }
            psInsertStreetCities.executeBatch();

            return id;

        } catch (SQLException e) {
            Logger.e(TAG, "insertWayStreet(), problem with query", e);
            e.printStackTrace();
            return 0;
        }
    }

    /**
     * Insert house into address database
     * @param street Street to which it belongs the inserted house
     * @param house
     * @return id of inserted house
     */
    public long insertHouse (Street street, House house){

//        Logger.i(TAG, "Insert house into db: " +
//                "\n Street: " + street.toString() +
//                "\n House: " + house.toString());
        HouseDTO houseDTO = new HouseDTO(
                house.getNumber(),
                house.getName(),
                house.getPostCodeId(),
                house.getCenter(),
                street);
        byte[] bytes = houseDTO.getAsBytes();

        long houseId = housesIdSequence++;
        try {
            psInsertHouse.setLong(1, house.getOsmId());
            psInsertHouse.setInt(2, street.getId());
            psInsertHouse.setString(3, street.getName());
            psInsertHouse.setString(4, house.getNumber());
            psInsertHouse.setString(5, house.getName());
            psInsertHouse.setString(6, house.getPostCode());
            psInsertHouse.setBytes(7, bytes);
            psInsertHouse.setBytes(8, wkbWriter.write(house.getCenter()));

            psInsertHouse.execute();

            // LOGf number of created point in exponencial
            if (housesIdSequence % 10000 == 0){
                int pow = (int) Math.log10(housesIdSequence);
                if (housesIdSequence % (int) Math.pow(10 , pow) == 0){
                    Logger.i(TAG, "Inserted houses: " + housesIdSequence);
                }
            }

            return houseId;

        } catch (SQLException e) {
            Logger.e(TAG, "insertWayStreet(), problem with query", e);
            e.printStackTrace();
            return 0;
        }
    }

    /**
     * Insert new (still not used) postcodes into database
     * @param postCode postcode to insert
     * @return code of inserted postCode or -1 in case of any exception
     */
    private int insertPostCode(String postCode) {

        postCodesIdSequence++;

        try {
            //Logger.i(TAG,"Insert postCode into Database, postCode: " + postCode);
            psInsertPostCode.setInt(1, postCodesIdSequence);
            psInsertPostCode.setString(2, postCode);
            psInsertPostCode.execute();

            postCodesMap.put(postCode, postCodesIdSequence);

            return postCodesIdSequence;

        } catch (SQLException e) {
            Logger.e(TAG, "insertPostCode(), problem with query", e);
            e.printStackTrace();
            return -1;
        }
    }


    /**
     * Temporary method that create table with houses for which was not able to find proper street
     * @param house removed house
     */
    public void insertRemovedHouse (House house){

        try {
            psInsertRemovedHouse.setString(1, house.getStreetName());
            psInsertRemovedHouse.setString(2, house.getPlace());
            psInsertRemovedHouse.setString(3, house.getNumber());
            psInsertRemovedHouse.setString(4, house.getName());
            psInsertRemovedHouse.setString(5, house.getPostCode());
            psInsertRemovedHouse.setBytes(6, wkbWriter.write(house.getCenter()));

            psInsertRemovedHouse.execute();

        } catch (SQLException e) {
            Logger.e(TAG, "insertWayStreet(), problem with query", e);
            e.printStackTrace();
        }
    }

    /**
     * Find cities without streets and then create dummy street with the same name and same location     *
     */
    public void createDummyStreets() {
        String sql = "SELECT " + TN_CITIES_NAMES + "." +COL_CITY_ID + ", " + COL_NAME + ", " + COL_NAME_NORM + ", " +
                "AsBinary (" + COL_CENTER_GEOM + ")" +
        " FROM " + TN_CITIES_NAMES + " LEFT JOIN (SELECT DISTINCT " + COL_CITY_ID +" FROM " + TN_STREET_IN_CITIES +
                ") AS City_With_Streets ON " + TN_CITIES_NAMES + "."+ COL_CITY_ID + " = City_With_Streets." + COL_CITY_ID +
                " JOIN " + TN_CITIES + " ON " + COL_ID + " = " + TN_CITIES_NAMES + "."+ COL_CITY_ID +
                " WHERE City_With_Streets." + COL_CITY_ID + " IS NULL" +
                " AND " + COL_LANG_CODE + " = \'" + DEFAULT_LANG_CODE + "\'";

        try {
            Logger.i(TAG, sql);
            ResultSet rs = getStmt().executeQuery(sql);

            List<Street> streetsToInsert = new ArrayList<>();

            while (rs.next()) {

                long cityId = rs.getLong(1);
                String name = rs.getString(2);
                String nameNorm = rs.getString(3);
                byte[] data = rs.getBytes(4);
                Point center = (Point) wkbReader.read(data);

                if (name == null){
                    name = nameNorm;
                }
                // create street itself
                streetsToInsert.add(createDummyStreet(name, cityId, center));
            }

            // now insert created dummy street into the database
            for (Street street : streetsToInsert){
                //Logger.i(TAG, "Insert dummy street: " + street.toString());
                insertStreet(street, false);
            }

        } catch (SQLException e) {
            Logger.e(TAG, "createDummyStreets(), problem with query", e);
            e.printStackTrace();
        } catch (ParseException e) {
            Logger.e(TAG, "createDummyStreets(), problem with parsing center geometry", e);
            e.printStackTrace();
        }
    }

    /**
     * Create street object that can be inserted into database
     * @param name name of city  from which is dummy created
     * @param cityId if of city from which is street created
     * @param center center of city
     * @return new street object that is in the center of city and has zero length
     */
    public Street createDummyStreet (String name, long cityId, Point center) {
        GeometryFactory gf = new GeometryFactory();

        Coordinate coordinate = new Coordinate(center.getX(), center.getY());
        LineString ls = gf.createLineString(new Coordinate[]{coordinate, coordinate});
        MultiLineString mls = gf.createMultiLineString(new LineString[]{ls});

        Street street = new Street(name, null, mls );
        street.addCityId(cityId);

        return street;
    }


    /**************************************************/
    /*                  SELECT PART                   */
    /**************************************************/


    /**
     * Load street from database, The cityIds are not loaded
     * @param streetId id of street
     * @return street or null if street with such id is not in DB
     */
    public Street selectStreet(int streetId) {

        Street streetLoaded = null;
        try{
            psSelectStreet.setInt(1, streetId);
            ResultSet rs = psSelectStreet.executeQuery();

            while (rs.next()){

                streetLoaded = new Street();
                streetLoaded.setId(rs.getInt(1));
                String name = rs.getString(2);
                String nameNorm = rs.getString(3);
                if (name == null){
                    name = nameNorm;
                }
                streetLoaded.setName(name);

                byte[] data = rs.getBytes(4);
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

    /**
     * Select houses that are assigned to street. It return them as blob not as object
     * @param streetId
     * @return compressed byte  array that contains serialized houses for street
     */
    public byte[] selectHousesInStreet (int streetId){
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        int count = 0;
        try {
            psSelectHousesInStreet.clearParameters();
            psSelectHousesInStreet.setInt(1, streetId);
            ResultSet rs = psSelectHousesInStreet.executeQuery();

            while (rs.next()){

                byte[] data = rs.getBytes(1);
                if (data != null) {
                    housesPreparedAsBlobForStreets++;
                    count++;
                    baos.write(data);
                }
            }
        }
        catch (SQLException e) {
            Logger.e(TAG, "selectHousesInStreet(), problem with query", e);
            e.printStackTrace();
            return baos.toByteArray();
        } catch (IOException e) {
            Logger.e(TAG, "selectHousesInStreet(), problem loading data", e);
            e.printStackTrace();
            baos.toByteArray();
        } finally {
            locus.api.utils.Utils.closeStream(baos);
        }

        byte[] data = baos.toByteArray();
        return Utils.compressByteArray(data);
    }


    /**
     * Look for street that is places in defined city and specific name
     * @param cityName name of the city where street is inside
     * @param streetName name of street to select
     * @return street or null of no street for such name ans city exists
     */
    public Street selectStreetByNames (String cityName, String streetName){

        //Logger.i(TAG, "Select street for city: " + cityName + " And sreet name: " + streetName);
        if (cityName.length() == 0 ||streetName.length() == 0 ){
            //some name is not defined do not search
            return null;
        }

        Street streetLoaded = null;
        try{
            psSelectStreetByNames.setString(1, Utils.normalizeString(cityName));
            psSelectStreetByNames.setString(2, Utils.normalizeString(streetName));

            ResultSet rs = psSelectStreetByNames.executeQuery();

            while (rs.next()){

                streetLoaded = new Street();
                streetLoaded.setId(rs.getInt(1));
                String name = rs.getString(2);
                String nameNorm = rs.getString(3);
                if (name == null){
                    name = nameNorm;
                }
                streetLoaded.setName(name);

                byte[] data = rs.getBytes(4);
                if (data != null) {
                    MultiLineString mls = (MultiLineString) wkbReader.read(data);
                    streetLoaded.setGeometry(mls);
                }
            }

            return streetLoaded;

        } catch (SQLException e) {
            Logger.e(TAG, "selectStreetByNames(), problem with query", e);
            e.printStackTrace();
        } catch (ParseException e) {
            Logger.e(TAG, "selectStreetByNames(), problem with geometry parsing", e);
            e.printStackTrace();
        }

        return streetLoaded;
    }

    /**
     * Set houses in street. Houses are serialized into byte[]
     * @param streetId
     * @param houseData
     * @return id of updated street
     */
    public long updateStreetSetHouseBlob(long streetId, byte[] houseData) {
        try{
            psUpdateStreet.setBytes(1,houseData);
            psUpdateStreet.setLong(2, streetId);
            psUpdateStreet.execute();

            return streetId;
        } catch (SQLException e) {
            Logger.e(TAG, "updateStreetSetHouseBlob(), problem with query", e);
            e.printStackTrace();
        }
        return -1;
    }

    /**
     * Loads the same street (with the same name) that are in cities with specified list of ids
     * @param street define name of street and possible cityIds where street can be in
     * @return
     */
    public List<Street> selectStreetInCities(Street street) {

        // more then one street can be loaded
        Map<Integer, Street> loadedStreetMap  = new HashMap<>();
        String sql = "";
        try {
            TLongHashSet cityIds = street.getCityIds();

            if (cityIds.size() == 0){
                Logger.w(TAG, "selectStreetInCities:  street has no cityId, street " + street.toString() );
            }

            // prepare list of city ids
            StringBuilder isInIds = new StringBuilder("(");
            TLongIterator iterator = cityIds.iterator();
            while (iterator.hasNext()){
                isInIds.append(String.valueOf(iterator.next()));

                if (iterator.hasNext()){
                    isInIds.append(", ");
                }
            }
            isInIds.append(")");

            String name = escapeSqlString (street.getName());

            sql = "SELECT " + COL_STREET_ID+ ", " + COL_CITY_ID+ ", "+ COL_NAME + ", AsBinary(" + COL_GEOM + ")";
            sql += " FROM " + TN_STREETS + " JOIN " + TN_STREET_IN_CITIES;
            sql += " ON " + COL_ID + " = " + COL_STREET_ID;
            sql += " WHERE " + COL_NAME + " like '" + name +"'";
            sql += " AND " + COL_CITY_ID + " IN " + isInIds.toString();

            ResultSet rs = getStmt().executeQuery(sql);

            while (rs.next()){

                int streetId = rs.getInt(1);

                // check if exist street in map from previous result
                Street streetLoaded = loadedStreetMap.get(streetId);


                if (streetLoaded == null){
                    // load completly whole street
                    streetLoaded = new Street();

                    streetLoaded.setId(rs.getInt(1));
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
            psSimplifyCityGeom.setLong(2, city.getOsmId());
            psSimplifyCityGeom.setBytes(1, wkbWriter.write(mp));
            psSimplifyCityGeom.execute();


        } catch (SQLException e) {
            Logger.i(TAG, "Exception when simplify city: " + city.getOsmId());
        }
    }

    public void deleteStreet (int streetId){
        try {

            psDeleteStreet.clearParameters();
            psDeleteStreet.setInt(1, streetId);
            psDeleteStreet.execute();

            psDeleteStreetCities.clearParameters();
            psDeleteStreetCities.setInt(1, streetId);
            psDeleteStreetCities.execute();

        } catch (SQLException e) {
            Logger.e(TAG, "deleteStreet(): Exception when delete street with id: " + streetId, e);
        }
    }


    /**************************************************/
    /*                OTHER TOOLS
    /**************************************************/



    public List<Street> getStreetsAround(Point centerPoint, int minNumber) {

        double distance = 200;

        List<Street> streetsFromIndex = new ArrayList();

        int numOfResize = 0;
        Polygon searchBound = Utils.createRectangle(centerPoint.getCoordinate(), distance);
        while (streetsFromIndex.size() < minNumber) {
            //Logger.i(TAG,"getStreetsAround(): bounding box: " +Utils.geomToGeoJson(searchBound));
            streetsFromIndex = streetGeomIndex.query(searchBound.getEnvelopeInternal());
            if (numOfResize == 4) {
                //Logger.i(TAG, "getStreetsAround(): Max num of resize reached for center point: " + Utils.geomToGeoJson(centerPoint));
                break;
            }
            numOfResize++;
            distance = distance * 2;
            searchBound = Utils.createRectangle(centerPoint.getCoordinate(), distance);
        }

        PreparedGeometry pg = PreparedGeometryFactory.prepare(searchBound);
        for (int i = streetsFromIndex.size() -1; i >= 0; i--){
            Street street = streetsFromIndex.get(i);
            if ( !pg.intersects(street.getGeometry().getEnvelope())){
                //Logger.i(TAG, "getDummyStreetsAround(): remove street because not intersect: " + street.toString());
                streetsFromIndex.remove(i);
            }
        }
        return streetsFromIndex;
    }

    /**
     * Select nearest streets
     * @param centerPoint
     * @param minNumber
     * @return
     */
    public List<Street> getDummyStreetsAround(Point centerPoint, int minNumber) {

        double distance = 200;

        List<Street> streetsFromIndex = new ArrayList();
        Polygon searchBound = Utils.createRectangle(centerPoint.getCoordinate(), distance);
        int numOfResize = 0;
        while (streetsFromIndex.size() < minNumber) {

            //Logger.i(TAG,"getDummyStreetsAround(): bounding box: " +Utils.geomToGeoJson(searchBound));
            streetsFromIndex = dummyStreetGeomIndex.query(searchBound.getEnvelopeInternal());
            if (numOfResize == 4) {
                //Logger.i(TAG, "getDummyStreetsAround(): Max num of resize reached for center point: " + Utils.geomToGeoJson(centerPoint));
                break;
            }
            numOfResize++;
            distance = distance * 2;
            searchBound = Utils.createRectangle(centerPoint.getCoordinate(), distance);
        }

        // FILTER - Quadtree return only items that MAY intersect > it's needed to check it

        PreparedGeometry pg = PreparedGeometryFactory.prepare(searchBound);
        for (int i = streetsFromIndex.size() -1; i >= 0; i--){
            Street street = streetsFromIndex.get(i);
            if ( !pg.intersects(street.getGeometry().getEnvelope())){
                //Logger.i(TAG, "getDummyStreetsAround(): remove street because not intersect: " + street.toString());
                streetsFromIndex.remove(i);
            }
        }

        return streetsFromIndex;
    }

    /**
     * Search for houses that have the same center geom and the same housenumber
     * and delete duplicated record
     * */
    public void deleteDuplicatedHouses() {
        String sql = "DELETE FROM " + TN_HOUSES + " WHERE rowid IN (";
        sql += " SELECT rowid FROM " + TN_HOUSES;
        sql += " GROUP BY " +  COL_CENTER_GEOM + ", " + COL_NUMBER ;
        sql += " HAVING count(*) > 1)" ;

        try {
            for (int i = 0; i < 3; i++) {
                // Honestly do not why but it's needed to run it several times, but it help to clean all duplicates
                executeStatement(sql);
            }

        } catch (SQLException e) {
            Logger.e(TAG, "deleteDuplicatedHouses(): problem with query: " + sql, e);
        }
    }

    /**
     * Get reference id for specified post code. If post code is not defined
     * then add into table of post codes
     * @param postCode code for which want to get reference
     * @return reference to postcode or -1 if not possible to obtaion or create postcode id
     */
    public int getPostCodeId (String postCode) {
        if (postCode == null || postCode.length() == 0){
            return -1;
        }
        Integer postCodeId = postCodesMap.get(postCode);
        if (postCodeId == null){
            postCodeId = insertPostCode (postCode);
        }
        return postCodeId;
    }



    /**
     * Get current value of id of last inserted street
     * @return
     */
    public int getStreetIdSequence (){
        return  streetIdSequence;
    }

    /**
     * Escape special characters for SQL query
     * @param name String to escape
     * @return escaped string
     */
    private String escapeSqlString(String name) {
        name = name.replace("'", "''");
        return name;
    }


    public TIntArrayList getPathStreetIds() {
        return pathStreetIds;
    }


}
