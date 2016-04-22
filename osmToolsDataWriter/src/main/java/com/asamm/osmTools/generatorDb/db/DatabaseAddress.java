package com.asamm.osmTools.generatorDb.db;

import com.asamm.osmTools.generatorDb.address.*;
import com.asamm.osmTools.generatorDb.index.IndexController;
import com.asamm.osmTools.generatorDb.utils.Const;
import com.asamm.osmTools.generatorDb.utils.GeomUtils;
import com.asamm.osmTools.generatorDb.utils.Utils;
import com.asamm.osmTools.utils.Logger;
import com.vividsolutions.jts.geom.*;
import com.vividsolutions.jts.io.ParseException;
import com.vividsolutions.jts.io.WKBReader;
import com.vividsolutions.jts.simplify.DouglasPeuckerSimplifier;
import gnu.trove.iterator.TLongIterator;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.hash.THashMap;
import gnu.trove.set.hash.TLongHashSet;
import locus.api.utils.DataWriterBigEndian;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.util.*;

import static com.asamm.locus.features.loMaps.LoMapsDbConst.*;

public class DatabaseAddress extends ADatabaseHandler {

    private static final String TAG = DatabaseAddress.class.getSimpleName();

    public long timeDtoSelectHouses = 0;
    public long timeDtoDeSerializeHouse = 0;
    public long timeDtoCreateDTO = 0;
    public long timeDtoZipData = 0;


    /** increment for street. Streets have own ids there is NO relation with OSM id*/
    private int streetIdSequence = 0;

    private long housesIdSequence = 0;

    public long housesPreparedAsBlobForStreets = 0;

    private int updateStreetHouseBlobCounter = 0;

    /** Counter for used postCodes */
    private int postCodesIdSequence = 0;

    /** Map store the reference between postCode and num of row in table of postCodes */
    private THashMap<String, Integer> postCodesMap;


    /** Precompiled statement for inserting cities into db*/
    private PreparedStatement psInsertRegion;

    /** Insert international names of city*/
    private PreparedStatement psInsertRegionNames;

    /** For testing if region of given id exists in DB*/
    private PreparedStatement psRegionExist;

    /** Precompiled statement for inserting cities into db*/
    private PreparedStatement psInsertCity;

    /** Insert international names of city*/
    private PreparedStatement psInsertCityNames;

    /** Id of streets which geometry is from path or tracks */
    private TIntArrayList pathStreetIds;

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

    private static boolean hasTableOfRemovedHouses = false;

    private static boolean hasCitiesCenterGeomColumn = true;


    public DatabaseAddress(File file) throws Exception {

        super(file, deleteOldDb);

        if (!deleteOldDb){
            cleanTables();
        }

        setTables();

        initPreparedStatements();


        postCodesMap = new THashMap<>();
        pathStreetIds = new TIntArrayList();
	}

    private void initPreparedStatements() throws SQLException {
        // create prepared statemennts

        psInsertRegion = createPreparedStatement(
                "INSERT INTO "+ TN_REGIONS +" ("+COL_ID+", "+COL_GEOM+
                        ") VALUES (?, GeomFromWKB(?, 4326))");

        psInsertRegionNames = createPreparedStatement( "INSERT INTO "+ TN_REGIONS_NAMES +
                " ("+COL_REGION_ID+", "+COL_LANG_CODE+", "+COL_NAME+", "+COL_NAME_NORM+
                " ) VALUES (?, ?, ?, ?)");

        psRegionExist = createPreparedStatement(
                "SELECT "+COL_ID+" FROM "+ TN_REGIONS +" WHERE "+COL_ID+" = ?");


        if (hasCitiesCenterGeomColumn){
            psInsertCity = createPreparedStatement(
                    "INSERT INTO "+ TN_CITIES +" ("+COL_ID+", "+COL_TYPE+", "+ COL_PARENT_CITY_ID +", "+ COL_REGION_ID + ", "+
                            COL_LON+", " + COL_LAT+ ", " + COL_CENTER_GEOM+ ", " + COL_GEOM+
                            ") VALUES (?, ?, ?, ?, ?, ?, GeomFromWKB(?, 4326), GeomFromWKB(?, 4326))");

        }else {
            psInsertCity = createPreparedStatement(
                    "INSERT INTO "+ TN_CITIES +" ("+COL_ID+", "+COL_TYPE+", "+ COL_PARENT_CITY_ID +", "+ COL_REGION_ID + ", "+
                            COL_LON+", " + COL_LAT+ ", " + COL_GEOM+
                            ") VALUES (?, ?, ?, ?, ?, ?, GeomFromWKB(?, 4326))");
        }

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
                        COL_NAME+", "+  COL_POST_CODE_ID+", "+COL_DATA + ", " + COL_CENTER_GEOM +
                        ") VALUES (?, ?, ?, ?, ?, ?,  ?, GeomFromWKB(?, 4326))");

        psInsertRemovedHouse =  createPreparedStatement(
                "INSERT INTO "+ TN_HOUSES_REMOVED +" ("+COL_STREET_NAME+", "+COL_PLACE_NAME+",  "+COL_NUMBER+", "
                        +COL_NAME+", "+COL_POST_CODE_ID+", "+COL_TYPE+", "+COL_CENTER_GEOM+
                        ") VALUES (?, ?, ?, ?, ?, ?,  GeomFromWKB(?, 4326))");

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

            sql = "DROP INDEX IF EXISTS " + IDX_REGIONS_NAMES_LANGCODE;
            executeStatement(sql);

            sql = "DROP INDEX IF EXISTS " + IDX_REGIONS_NAMES_CITYID;
            executeStatement(sql);

            sql = "DROP INDEX IF EXISTS " + IDX_CITIES_NAMES_NAMENORM;
            executeStatement(sql);

            sql = "DROP INDEX IF EXISTS " + IDX_CITIES_NAMES_CITYID;
            executeStatement(sql);

            sql = "DROP INDEX IF EXISTS " + IDX_CITIES_LON_LAT;
            executeStatement(sql);

            sql = "DROP INDEX IF EXISTS " + IDX_STREETS_NAMENORM;
            executeStatement(sql);

            sql = "DROP INDEX IF EXISTS " + IDX_STREETS_IN_CITIES_CITYID;
            executeStatement(sql);

            sql = "DROP INDEX IF EXISTS " + IDX_STREETS_IN_CITIES_STREETID;
            executeStatement(sql);

            sql = "DROP VIEW IF EXISTS " + VIEW_CITIES_DEF_NAMES;
            executeStatement(sql);

            sql = "DELETE FROM VIEWS_GEOMETRY_COLUMNS WHERE view_name = '" + VIEW_CITIES_DEF_NAMES.toLowerCase() + "'";
            executeStatement(sql);


            sql = "SELECT DisableSpatialIndex('" + TN_REGIONS + "', '"+COL_GEOM+"')";
            executeStatement(sql);
            sql = "DROP TABLE IF EXISTS  idx_"+ TN_REGIONS +"_"+COL_GEOM;
            executeStatement(sql);

            sql = "SELECT DisableSpatialIndex('" + TN_CITIES + "', '"+COL_GEOM+"')";
            executeStatement(sql);
            sql = "DROP TABLE IF EXISTS  idx_"+ TN_CITIES +"_"+COL_GEOM;
            executeStatement(sql);

            sql =  "SELECT DisableSpatialIndex ('" + TN_STREETS + "', '"+COL_GEOM+"')";
            executeStatement(sql);
            sql = "DROP TABLE IF EXISTS  idx_"+ TN_STREETS +"_"+COL_GEOM;
            executeStatement(sql);

            if (hasHousesTableWithGeom){
                sql =  "SELECT DisableSpatialIndex ('" + TN_HOUSES + "', '"+COL_CENTER_GEOM+"')";
                executeStatement(sql);
                sql = "DROP TABLE IF EXISTS  idx_"+ TN_HOUSES +"_"+COL_CENTER_GEOM;
                executeStatement(sql);
            }
            if (hasTableOfRemovedHouses){
                sql =  "SELECT DisableSpatialIndex ('" + TN_HOUSES_REMOVED + "', '"+COL_CENTER_GEOM+"')";
                executeStatement(sql);
                sql = "DROP TABLE IF EXISTS  idx_"+ TN_HOUSES_REMOVED +"_"+COL_CENTER_GEOM;
                executeStatement(sql);
            }


            sql ="DROP TABLE IF EXISTS  "+TN_REGIONS ;
            executeStatement(sql);

            sql ="DROP TABLE IF EXISTS  "+TN_REGIONS_NAMES ;
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

        String sql = "CREATE TABLE "+TN_REGIONS+" (";
        sql += COL_ID+" BIGINT NOT NULL PRIMARY KEY) ";
        executeStatement(sql);

        // creating a Boundary Geometry column
        sql = "SELECT AddGeometryColumn('"+TN_REGIONS+"', ";
        sql += "'"+COL_GEOM+"', 4326, 'MULTIPOLYGON', 'XY')";
        executeStatement(sql);

        // TABLE OF INTERNATIONAL REGION NAMES
        sql = "CREATE TABLE "+TN_REGIONS_NAMES+" (";
        sql += COL_REGION_ID+" BIGINT NOT NULL,";
        sql += COL_LANG_CODE+" TEXT NOT NULL, ";
        sql += COL_NAME+" TEXT, ";
        sql += COL_NAME_NORM+" TEXT NOT NULL)";
        executeStatement(sql);

        sql = "CREATE TABLE "+TN_CITIES+" (";
		sql += COL_ID+" BIGINT NOT NULL PRIMARY KEY,";
		sql += COL_TYPE+" INT NOT NULL, ";
        sql += COL_PARENT_CITY_ID +" BIGINT, ";
        sql += COL_REGION_ID + " BIGINT, ";
        sql += COL_LON + " INT, ";
        sql += COL_LAT + " INT )";
		executeStatement(sql);

        if (hasCitiesCenterGeomColumn){
            sql = "SELECT AddGeometryColumn('"+TN_CITIES+"', ";
            sql += "'"+COL_CENTER_GEOM+"', 4326, 'POINT', 'XY')";
            executeStatement(sql);
        }

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
        sql += COL_POST_CODE_ID+" INT, "  ;
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
        sql += COL_POST_CODE_ID +" INT, ";
        sql += COL_TYPE+" TEXT ) ";
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

        // Create index for city before insert them (this is workaround how to avoid error when index was empty)
        buildCityBoundaryIndex();
	}

    @Override
    public void destroy () throws SQLException {

        buildRegionBoundaryIndex();

        buildRegionNamesIndexes();

        buildCityLonLatIndex();

        buildStreetGeomIndex();

        buildViews();

        commit(false);

        psInsertHouse.close();
        psSelectHousesInStreet.close();
        psSelectHousesInStreet.close();
        psInsertStreet.close();
        psDeleteStreet.close();

        // compress street geom
        String sql = "UPDATE " + TN_STREETS +" SET "+ COL_GEOM+ " = CompressGeometry("+COL_GEOM+")";
        executeStatement(sql);

        // ugly workaround how to avoid Locked database when try to remove house table
        commit(false);
        restartConnection();

        if ( !hasHousesTableWithGeom){
            sql = "DROP TABLE IF EXISTS  idx_"+ TN_HOUSES +"_"+COL_CENTER_GEOM;
            executeStatement(sql);
            sql = "DROP TABLE IF EXISTS  "+ TN_HOUSES;
            executeStatement(sql);
        }

        if ( !hasTableOfRemovedHouses){
            sql = "DROP TABLE IF EXISTS  "+ TN_HOUSES_REMOVED;
            executeStatement(sql);
        }

        super.destroy();
    }

    /**************************************************/
    /*                  DB INDEXES PART                   */
    /**************************************************/

    /**
     * Create spatial index for region geoms
     */
    public void buildRegionBoundaryIndex() {
        try {
            commit(false);
            String sql = "SELECT CreateSpatialIndex('" + TN_REGIONS + "', '"+COL_GEOM+"')";
            executeStatement(sql);
        } catch (SQLException e) {
            Logger.e(TAG, "buildRegionBoundaryIndex(), problem with query", e);
            e.printStackTrace();
        }
    }


    /**
     * Build spatial index for city center point and index for city names
     */
    public void buildRegionNamesIndexes() {
        try {
            commit(false);

            String sql = "CREATE INDEX "+ IDX_REGIONS_NAMES_LANGCODE +" ON " + TN_REGIONS_NAMES +
                    " (" + COL_LANG_CODE+ ")";
            executeStatement(sql);

            sql = "CREATE INDEX " + IDX_REGIONS_NAMES_CITYID + " ON " + TN_REGIONS_NAMES +
                    " (" + COL_REGION_ID+ ")";
            executeStatement(sql);

        } catch (SQLException e) {
            Logger.e(TAG, "buildCityNamesIndexes(), problem with query", e);
            e.printStackTrace();
        }
    }

    /**
     * Create spatial index only via boundary polygon of the city
     */
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
     * Build index for columns with center coordinates for city.
     */
    public void buildCityLonLatIndex () {
        try {
            commit(false);

            String sql = "CREATE INDEX "+IDX_CITIES_LON_LAT+" ON " + TN_CITIES +
                    " (" + COL_LON+ ", " + COL_LAT + ")";
            executeStatement(sql);

        } catch (SQLException e) {
            Logger.e(TAG, "buildCityLonLatIndex(), problem with query", e);
            e.printStackTrace();
        }
    }


    /**
     * Build spatial index for city center point and index for city names
     */
    public void buildCityNamesIndexes() {
        try {
            commit(false);

            String sql = "CREATE INDEX "+IDX_CITIES_NAMES_NAMENORM+" ON " + TN_CITIES_NAMES +
                    " (" + COL_NAME_NORM+ ", " + COL_LANG_CODE + ")";
            executeStatement(sql);

            sql = "CREATE INDEX " + IDX_CITIES_NAMES_CITYID + " ON " + TN_CITIES_NAMES +
                    " (" + COL_CITY_ID+ ")";
            executeStatement(sql);

        } catch (SQLException e) {
            Logger.e(TAG, "buildCityNamesIndexes(), problem with query", e);
            e.printStackTrace();
        }
    }


    /**
     * Create indexes via houses geom. Only in case that table oh houses is created
     * see param {@link #hasHousesTableWithGeom}
     */
    public void buildHouseIndexes () {
        try {
            commit(false);
            if (hasHousesTableWithGeom){
                String sql = "SELECT CreateSpatialIndex('" + TN_HOUSES + "', '"+COL_CENTER_GEOM+"')";
                executeStatement(sql);
            }

            if (hasTableOfRemovedHouses){
                String sql = "SELECT CreateSpatialIndex('" + TN_HOUSES_REMOVED + "', '"+COL_CENTER_GEOM+"')";
                executeStatement(sql);
            }

            // index for street id
            String sql = "CREATE INDEX " + IDX_HOUSES_STREETID + " ON " + TN_HOUSES +
                    " (" + COL_STREET_ID+ ")";
            executeStatement(sql);

            commit(false);

        } catch (SQLException e) {
            Logger.e(TAG, "buildHouseIndexes(), problem with query", e);
            e.printStackTrace();
        }
    }

    public void buildStreetGeomIndex () {

        try {
            commit(false);
            String sql = "SELECT CreateSpatialIndex('" + TN_STREETS + "', '"+COL_GEOM+"')";
            executeStatement(sql);
        } catch (SQLException e) {
            Logger.e(TAG, "buildStreetGeomIndex(), problem with query", e);
            e.printStackTrace();
        }
    }

    /**
     * Build index for normalized street name and for Street_in_cities table
     */
    public void buildStreetNameIndex() {
        commit(false);
        try {
            String sql = "CREATE INDEX "+IDX_STREETS_NAMENORM+" ON " + TN_STREETS +
                    " (" + COL_NAME_NORM+  ")";
            executeStatement(sql);

            sql = "CREATE INDEX "+IDX_STREETS_IN_CITIES_CITYID+" ON " + TN_STREET_IN_CITIES +
                    " (" + COL_CITY_ID+  ")";
            executeStatement(sql);

            sql = "CREATE INDEX "+IDX_STREETS_IN_CITIES_STREETID+" ON " + TN_STREET_IN_CITIES +
                    " (" + COL_STREET_ID+  ")";
            executeStatement(sql);

        } catch (SQLException e) {
            Logger.e(TAG, "buildStreetNameIndex(), problem with query", e);
            e.printStackTrace();
        }
    }

    public void buildViews () {
        try {
            String sql = "CREATE VIEW " + VIEW_CITIES_DEF_NAMES + " AS ";
            sql += "SELECT " + TN_CITIES+".ROWID as ROWID, " + TN_CITIES+"."+COL_ID +", " + TN_CITIES_NAMES+"."+COL_NAME + ", ";
            sql += TN_CITIES_NAMES+"."+COL_NAME_NORM + ", " + TN_CITIES+"."+COL_GEOM+ " ";
            sql += "FROM " + TN_CITIES + " ";
            sql += "JOIN "+ TN_CITIES_NAMES + " ON " + TN_CITIES +"."+ COL_ID + " = " + TN_CITIES_NAMES +"."+ COL_CITY_ID + " ";
            sql += "WHERE "+ TN_CITIES_NAMES +"."+ COL_LANG_CODE + " = '" + Const.DEFAULT_LANG_CODE + "'";

            executeStatement(sql);

            sql = "INSERT INTO VIEWS_GEOMETRY_COLUMNS VALUES ('" + VIEW_CITIES_DEF_NAMES.toLowerCase()+ "', '";
            sql += COL_GEOM + "', 'rowid', '" +TN_CITIES.toLowerCase()+ "', '" + COL_GEOM + "', 1)";
            executeStatement(sql);

        } catch (SQLException e) {
            Logger.e(TAG, "buildViews(), problem with query", e);
            e.printStackTrace();
        }

    }

    /**************************************************/
    /*                  INSERT PART                   */
    /**************************************************/

    /**
     * Insert region into database. Region is not add into DV if region with such id already exist in DB
     * @param region region to add
     */
    public void insertRegion(Region region) {
        try {
            // test if region of this id exist in DB
            psRegionExist.clearParameters();
            psRegionExist.setLong(1, region.getOsmId());
            ResultSet rs = psRegionExist.executeQuery();

            if (rs.next()){
                // there is already record for region in DB
                return ;
            }
            psInsertRegion.clearParameters();

            psInsertRegion.setLong(1, region.getOsmId());
            Geometry geomSimplified = GeomUtils.simplifyMultiPolygon(region.getGeom(), Const.CITY_POLYGON_SIMPLIFICATION_DISTANCE);
            psInsertRegion.setBytes(2, wkbWriter.write(geomSimplified));
            psInsertRegion.execute();

            // INSERT REGION NAMES

            // add default name into list of lang mutation
            region.addNameInternational(Const.DEFAULT_LANG_CODE, region.getName());
            Set<Map.Entry<String, String>> entrySet = region.getNamesInternational().entrySet();
            for (Map.Entry<String, String> entry : entrySet){
                //Logger.i("TAG", " insertCity: lang Code= " + entry.getKey() + " name= " + entry.getValue() );
                psInsertRegionNames.clearParameters();
                psInsertRegionNames.setLong(1, region.getOsmId());
                psInsertRegionNames.setString(2, entry.getKey());
                String name = entry.getValue();
                String nameNormalized = Utils.normalizeNames(name);
                if ( !nameNormalized.equals(name)){
                    //store full name only if normalized name is different
                    psInsertRegionNames.setString(3, name);
                }
                psInsertRegionNames.setString(4, nameNormalized);
                psInsertRegionNames.execute();
            }


        } catch (SQLException e) {
            Logger.e(TAG, "insertRegion(), problem with query", e);
            e.printStackTrace();
        }
    }

    /**
     * Insert city object into address database
     * @param city city to insert
     * @param boundary boundary for city (can be null if no boundary is created for city)
     */
    public void insertCity(City city, Boundary boundary) {

        //Logger.i(TAG, "Insert city:  " + city.toString());

        Region region = city.getRegion();

        try {
            //Logger.i(TAG, "Insert city:  " + city.toString());
            psInsertCity.clearParameters();

            psInsertCity.setLong(1, city.getOsmId());
            psInsertCity.setInt(2, city.getType().getTypeCode());

            City parentCity = city.getParentCity();
            if (parentCity != null){
                psInsertCity.setLong(3, parentCity.getOsmId());
            }

            if (region != null){
                psInsertCity.setLong(4, region.getOsmId());
            }

            // write center geom as two integers
            int[] centerI = GeomUtils.pointToIntegerValues(city.getCenter());
            psInsertCity.setInt(5, centerI[0]);
            psInsertCity.setInt(6, centerI[1]);

            if (hasCitiesCenterGeomColumn){
                psInsertCity.setBytes(7, wkbWriter.write(city.getCenter()));
                if (boundary != null ){
                    Geometry geomSimplified = GeomUtils.simplifyMultiPolygon(boundary.getGeom(), Const.CITY_POLYGON_SIMPLIFICATION_DISTANCE);
                    psInsertCity.setBytes(8, wkbWriter.write(geomSimplified));
                }
            }
            else {
                if (boundary != null ){
                    Geometry geomSimplified = GeomUtils.simplifyMultiPolygon(boundary.getGeom(), Const.CITY_POLYGON_SIMPLIFICATION_DISTANCE);
                    psInsertCity.setBytes(7, wkbWriter.write(geomSimplified));
                }
            }

            psInsertCity.execute();

            // INSERT CITY NAMES

            // add default name into list of lang mutation
            city.addNameInternational(Const.DEFAULT_LANG_CODE, city.getName());
            Set<Map.Entry<String, String>> entrySet = city.getNamesInternational().entrySet();
            for (Map.Entry<String, String> entry : entrySet){
                //Logger.i("TAG", " insertCity: lang Code= " + entry.getKey() + " name= " + entry.getValue() );
                psInsertCityNames.clearParameters();
                psInsertCityNames.setLong(1, city.getOsmId());
                psInsertCityNames.setString(2, entry.getKey());
                String name = entry.getValue();
                String nameNormalized = Utils.normalizeNames(name);
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
     * @return id of inserted street. This is not OSM id
     */
    public long insertStreet(Street street){

        if ( !street.isValid()){
            //Logger.i(TAG, "Street for insert is not valid: " + street.toString());
            return -1;
        }

        try {
            int id = streetIdSequence++;
            psInsertStreet.clearParameters();

            street.setId(id);
            psInsertStreet.setLong(1, street.getId());

            String name = street.getName();
            String nameNormalized = Utils.normalizeNames(name);
            if ( !nameNormalized.equals(name)){
                //store full name only if normalized name is different
                psInsertStreet.setString(2, name);
            }
            psInsertStreet.setString(3, nameNormalized);
            psInsertStreet.setBytes(4, wkbWriter.write(street.getGeometry()));
            psInsertStreet.execute();

            //register street in JTS memory index - use it later for houses
            IndexController.getInstance().insertStreet(street.getGeometry().getEnvelopeInternal(), street);

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
//
//        if (house.getOsmId() == 2822221392L){
//            Logger.i(TAG, "Insert house into db: " +
//                    "\n Street: " + street.toString() +
//                    "\n House: " + house.toString());
//        }

        long houseId = housesIdSequence++;
        try {
            psInsertHouse.clearParameters();

            psInsertHouse.setLong(1, house.getOsmId());
            psInsertHouse.setInt(2, street.getId());
            psInsertHouse.setString(3, street.getName());
            psInsertHouse.setString(4, house.getNumber());
            psInsertHouse.setString(5, house.getName());
            psInsertHouse.setInt(6, house.getPostCodeId());
            psInsertHouse.setBytes(7, house.getAsBytes());
            psInsertHouse.setBytes(8, wkbWriter.write(house.getCenter()));

            psInsertHouse.execute();

            // LOG number of created point in exponencial
            if (housesIdSequence % 10000 == 0){
                commit(false);
                int pow = (int) Math.log10(housesIdSequence);
                if (housesIdSequence % (int) Math.pow(10 , pow) == 0){
                    Logger.i(TAG, "Inserted houses: " + housesIdSequence);
                    Utils.printUsedMemory();
                }
            }

            return houseId;

        } catch (SQLException e) {
            Logger.e(TAG, "insertHouse(), problem with query", e);
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

            // put new postcode into map of postcodes
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
    public void insertRemovedHouse (House house, String reason){

        if (hasTableOfRemovedHouses){
            try {
                psInsertRemovedHouse.setString(1, house.getStreetName());
                psInsertRemovedHouse.setString(2, house.getPlace());
                psInsertRemovedHouse.setString(3, house.getNumber());
                psInsertRemovedHouse.setString(4, house.getName());
                psInsertRemovedHouse.setInt(5, house.getPostCodeId());
                psInsertRemovedHouse.setString(6, reason);
                psInsertRemovedHouse.setBytes(7, wkbWriter.write(house.getCenter()));

                psInsertRemovedHouse.execute();

            } catch (SQLException e) {
                Logger.e(TAG, "insertWayStreet(), problem with query", e);
                e.printStackTrace();
            }
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
                " AND " + COL_LANG_CODE + " = \'" + Const.DEFAULT_LANG_CODE + "\'";

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
                insertStreet(street);
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
     * @param street for which street is the blob of houses creted
     * @return compressed byte  array that contains serialized houses for street
     */
    public byte[] createHousesDTOblob(Street street){

        DataWriterBigEndian dw = new DataWriterBigEndian();

        int count = 0; // counter how many houses were converted to DTO
        try {
            // first write origin of houses (it is centroid of the street)
            int[] origin = street.getOriginForHouseDTO();
            dw.writeInt(origin[0]);
            dw.writeInt(origin[1]);

            long start = System.currentTimeMillis();
            psSelectHousesInStreet.clearParameters();
            psSelectHousesInStreet.setInt(1, street.getId());
            ResultSet rs = psSelectHousesInStreet.executeQuery();

            House house = null;
            byte[] dataDTO = null;
            timeDtoSelectHouses += System.currentTimeMillis() - start;

            // read every loaded house from database, deserializate house object and create DTO object from it
            while (rs.next()){

                start = System.currentTimeMillis();
                house = new House(rs.getBytes(1));
                timeDtoDeSerializeHouse += System.currentTimeMillis() - start;

                // crate simplified version od house with relative coordinates
                start = System.currentTimeMillis();
                HouseDTO houseDTO = new HouseDTO(
                        house.getNumber(),
                        house.getName(),
                        house.getPostCodeId(),
                        house.getCenter(),
                        street);
                dataDTO = houseDTO.getAsBytes();
                timeDtoCreateDTO += System.currentTimeMillis() - start;

                if (dataDTO.length > 0) {
                    housesPreparedAsBlobForStreets++;
                    count++;
                    dw.write(dataDTO);
                }
            }
        }
        catch (SQLException e) {
            Logger.e(TAG, "createHousesDTOblob(), problem with query", e);
            e.printStackTrace();
        } catch (IOException e) {
            Logger.e(TAG, "createHousesDTOblob(), problem loading data", e);
            e.printStackTrace();
        }
        if (count == 0){
            //no house was converted to DTO object or street does not have houses > return empty data
            return null;
        }

        long start = System.currentTimeMillis();
        byte[] dataZipped = Utils.compressByteArray(dw.toByteArray());
        timeDtoZipData += System.currentTimeMillis() - start;

        return dataZipped;
    }


    /**
     * Look for street that is places in defined city and specific name
     * @param cityName name of the city where street is inside
     * @param streetName name of street to select
     * @return street or null of no street for such name ans city exists
     */
    public List<Street> selectStreetByNames (String cityName, String streetName){

        //Logger.i(TAG, "Select street for city: " + cityName + " And sreet name: " + streetName);
        if (cityName.length() == 0 ||streetName.length() == 0 ){
            //some name is not defined do not search
            return null;
        }

        List<Street> streetsLoaded = new ArrayList<>();
        try{
            psSelectStreetByNames.setString(1, Utils.normalizeNames(cityName));
            psSelectStreetByNames.setString(2, Utils.normalizeNames(streetName));

            ResultSet rs = psSelectStreetByNames.executeQuery();

            while (rs.next()){

                Street streetLoaded = new Street();
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
                streetsLoaded.add(streetLoaded);
            }

        } catch (SQLException e) {
            Logger.e(TAG, "selectStreetByNames(), problem with query", e);
            e.printStackTrace();
        } catch (ParseException e) {
            Logger.e(TAG, "selectStreetByNames(), problem with geometry parsing", e);
            e.printStackTrace();
        }

        return streetsLoaded;
    }

    /**
     * Update street record and set new blob of houseData
     * IMPORTANT update is executed in batch for every 1000 records and also commit the changes
     * it is needed to run finalizeStreetUpdate to execute last streets
     * @param streetId id of street to update
     * @param houseData blob to set
     * @return id of updated street
     */
    public void updateStreetHouseBlob(long streetId, byte[] houseData) {

        try{
            psUpdateStreet.setBytes(1, houseData);
            psUpdateStreet.setLong(2, streetId);

            psUpdateStreet.addBatch();
            updateStreetHouseBlobCounter++;

            if (updateStreetHouseBlobCounter % 1000 == 0 ){
                psUpdateStreet.executeBatch();
                updateStreetHouseBlobCounter = 0;
                commit(false);
            }

        } catch (SQLException e) {
            Logger.e(TAG, "updateStreetHouseBlob(), problem with query", e);
            e.printStackTrace();
        }
    }

    public void finalizeUpdateStreetHouseBlob() {
        try {
            psUpdateStreet.executeBatch();
            updateStreetHouseBlobCounter = 0;

        } catch (SQLException e) {
            Logger.e(TAG, "updateStreetHouseBlob(), problem with query", e);
            e.printStackTrace();
        }
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

//    public void simplifyCityGeom(City city, Boundary boundary) {
//
//        GeometryFactory geometryFactory = new GeometryFactory();
//        try {
//
//            Geometry geometry = DouglasPeuckerSimplifier.simplify(boundary.getGeom(), 0.001);
//
//            MultiPolygon mp;
//            if (geometry instanceof Polygon) {
//                mp = geometryFactory.createMultiPolygon(new Polygon[]{(Polygon) geometry});
//            } else {
//                mp = (MultiPolygon) geometry;
//            }
//            psSimplifyCityGeom.clearParameters();
//            psSimplifyCityGeom.setLong(2, city.getOsmId());
//            psSimplifyCityGeom.setBytes(1, wkbWriter.write(mp));
//            psSimplifyCityGeom.execute();
//
//
//        } catch (SQLException e) {
//            Logger.i(TAG, "Exception when simplify city: " + city.getOsmId());
//        }
//    }

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
            e.printStackTrace();
        }
    }


    /**************************************************/
    /*                OTHER TOOLS
    /**************************************************/


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
     *
     * @param postCode code for which want to get reference
     *
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
