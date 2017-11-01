package com.asamm.osmTools.generatorDb.db;

import com.asamm.osmTools.generatorDb.address.Region;
import com.asamm.osmTools.generatorDb.plugin.ConfigurationCountry;
import com.asamm.osmTools.generatorDb.utils.Const;
import com.asamm.osmTools.generatorDb.utils.GeomUtils;
import com.asamm.osmTools.generatorDb.utils.Utils;
import com.asamm.osmTools.utils.Logger;
import com.mysql.jdbc.exceptions.jdbc4.MySQLIntegrityConstraintViolationException;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.io.*;
import org.openstreetmap.osmosis.core.domain.v0_6.Entity;
import org.openstreetmap.osmosis.core.domain.v0_6.EntityType;

import java.sql.*;
import java.util.Map;
import java.util.Set;


import static com.asamm.store.LocusServerConst.*;

/**
 * Created by voldapet on 2016-09-19 .
 */
public class DatabaseStoreMysql {

    private static final String TAG = DatabaseStoreMysql.class.getSimpleName();

    private static final String IDX_DATASTORE_REGION_ID = "idx_datastore_region_id";

    private String url = "jdbc:mysql://104.155.156.154:3306/storedb";
    //private String url = "jdbc:mysql://localhost:3306/storedb";
    private String username = "storedb";
    private String password = "a4cuntvTXDqogv24QDZOQ9qR";


    private Connection conn = null;

    private Statement stmt = null;

    protected WKBWriter wkbWriter;
    protected WKBReader wkbReader;

    protected WKTWriter wktWriter;
    protected WKTReader wktReader;



    // PREPARED STATEMENTS

    /** Precompiled statement for inserting regions into db*/
    private PreparedStatement psInsertRegion;

    /** */
    private PreparedStatement psSelectRegionIdByDatastoreId;

    /** Insert international names of city*/
    private PreparedStatement psInsertRegionNames;

    /** Load region from database for defined OSM ID and entity type*/
    private PreparedStatement psSelectRegionByOSM;

    /** Update region geometry*/
    private PreparedStatement psUpdateRegionGeomByOSM;


    public DatabaseStoreMysql ()  {

        initConnection ();

        createTables();

        wkbWriter = new WKBWriter(2, ByteOrderValues.LITTLE_ENDIAN);
        wkbReader = new WKBReader();
        wktWriter = new WKTWriter();
        wktReader = new WKTReader();

        initPreparedStatements();

    }

    /**
     * Initialize database connection to MySQL database
     */
    private void initConnection()  {

        try {
            Class.forName("com.mysql.jdbc.Driver");

            this.conn = DriverManager.getConnection(url,username,password);

            this.stmt = conn.createStatement();
        }
        catch (ClassNotFoundException e) {
            Logger.e(TAG, "initConnection()", e);
            e.printStackTrace();
        } catch (SQLException e) {
            Logger.e(TAG, "initConnection():  cannot init connection or create statement", e);
            e.printStackTrace();
        }
    }

    public void destroy() {
        try {
            if (stmt != null) {
                stmt.close();
            }

        } catch (Exception e) {
            Logger.e(TAG, "destroy()", e);
        }
    }

    protected void commit(boolean closeConnection) {
        try {
            if (conn != null) {
                conn.commit();
                if (closeConnection ) {
                    conn.close();
                }
            }
        } catch (Exception e) {
            Logger.e(TAG, "commit()", e);
        }
    }

    /**
     * Create needed tables for geotables
     */
    private void createTables()  {

        String sql = "";
        try {
            sql = "CREATE TABLE IF NOT EXISTS " + TN_GEO_REGION + " (";
            sql += COL_ID + " INT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY, ";
            sql += COL_PARENT_ID + " INT UNSIGNED, ";
            sql += COL_OSM_ID + " BIGINT NOT NULL DEFAULT 0, ";
            sql += COL_OSM_DATA_TYPE + " CHAR(1) NOT NULL DEFAULT 'u', ";
            sql += COL_STORE_REGION_ID + " VARCHAR(100), ";
            sql += COL_TYPE + " TINYINT UNSIGNED NOT NULL, ";
            sql += COL_TIMESTAMP + " TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, ";
            sql += COL_GEOM + " GEOMETRY NOT NULL,";
            sql += " UNIQUE KEY " + IDX_DATASTORE_REGION_ID + " (" + COL_STORE_REGION_ID + ")";
            sql += ")";
            executeStatement(sql);

            sql = "CREATE TABLE IF NOT EXISTS " + TN_GEO_REGION_NAME + " (";
            sql += COL_REGION_ID + " INT UNSIGNED NOT NULL,";
            sql += COL_LANG_CODE + " VARCHAR(3) NOT NULL, ";
            sql += COL_NAME + " TEXT, ";
            sql += COL_NAME_NORM + " TEXT NOT NULL,";
            sql += " FOREIGN KEY (" + COL_REGION_ID + ") REFERENCES " + TN_GEO_REGION + "(" + COL_ID + ") ON DELETE CASCADE";
            sql += ")";
            executeStatement(sql);
        } catch (SQLException e) {
            Logger.e(TAG, "createTables(), problem with query: " + sql, e);
            e.printStackTrace();
        }
    }


    private void initPreparedStatements() {

        try {
            psSelectRegionIdByDatastoreId = conn.prepareStatement("SELECT " + COL_ID + " FROM " + TN_GEO_REGION +
                    " WHERE " + COL_STORE_REGION_ID + " = ? ");

            psInsertRegion = conn.prepareStatement(
                    "INSERT INTO " + TN_GEO_REGION + " (" + COL_PARENT_ID + ", " + COL_OSM_ID + ", " + COL_OSM_DATA_TYPE + ", "
                            + COL_STORE_REGION_ID + ", " + COL_TYPE + ", " + COL_GEOM +
                            ") VALUES (?, ?, ?, ?, ?, ST_GeomFromWKB(?, 4326))",
                    Statement.RETURN_GENERATED_KEYS);

            psInsertRegionNames = createPreparedStatement("REPLACE INTO " + TN_GEO_REGION_NAME +
                    " (" + COL_REGION_ID + ", " + COL_LANG_CODE + ", " + COL_NAME + ", " + COL_NAME_NORM +
                    " ) VALUES (?, ?, ?, ?)");

            psSelectRegionByOSM = createPreparedStatement(" SELECT + ST_AsBinary(" + COL_GEOM + ") FROM " + TN_GEO_REGION +
                    " WHERE " + COL_OSM_ID + " = ? AND " + COL_OSM_DATA_TYPE + " = ?");

            psUpdateRegionGeomByOSM = createPreparedStatement("UPDATE " + TN_GEO_REGION + " SET " + COL_GEOM +
                    " = ST_GeomFromWKB(?, 4326) WHERE " + COL_OSM_ID + " = ? AND " + COL_OSM_DATA_TYPE + " = ?");

        } catch (SQLException e) {
            Logger.e(TAG, "initPreparedStatements(), problem with creation prepared statement", e);
                    e.printStackTrace();
        }
    }

    /**
     * Remove all geo tables from database
     */
    public void cleanTables() {
        String sql = "";
        try {
            sql ="DROP TABLE IF EXISTS  "+TN_GEO_REGION_NAME ;
            executeStatement(sql);

            sql ="DROP TABLE IF EXISTS  "+TN_GEO_REGION ;
            executeStatement(sql);

            //re create tables again
            createTables();

        } catch (SQLException e) {
            Logger.e(TAG, "cleanTables(), problem with query: " + sql, e);
            e.printStackTrace();
        }
    }

    protected PreparedStatement createPreparedStatement (String sql) throws SQLException {
        return conn.prepareStatement(sql);
    }

    protected void executeStatement(String sql) throws SQLException {
        //Logger.i(TAG, "executeStatement(" + sql + ")");
        stmt.execute(sql);
    }

    /**
     * In mysql db  has region primary id as integer. Method get thid in id from SQL database based on
     * old datastore regionId
     * @param dataStoreRegionId datastore id of region to get id
     * @return id of region or -1 if such region does not exist in db
     */
    public int getRegionId (String dataStoreRegionId){
        Logger.i(TAG, "getRegionId(), Get region id based on dataStoreId: " + dataStoreRegionId);

        try {
            psSelectRegionIdByDatastoreId.clearParameters();
            psSelectRegionIdByDatastoreId.setString(1, dataStoreRegionId);

            ResultSet rs = psSelectRegionIdByDatastoreId.executeQuery();

            while (rs.next()){
                return rs.getInt(1);
            }
        }
        catch (SQLException e) {
            Logger.e(TAG, "getRegionId(), problem with query", e);
            e.printStackTrace();
        }
        return -1;
    }

    /**
     * Load geometry of specified region from SQL database
     * @param osmId osm object id
     * @param entityType type of osm object
     * @return geometry for region or null if do not exist any region in database for id and object type
     */
    public Geometry getRegionGeom (long osmId, EntityType  entityType) {

        Geometry geom = null;
        try {

            psSelectRegionByOSM.setLong(1, osmId);
            psSelectRegionByOSM.setString(2, getOsmEntityTypeCode(entityType));

            ResultSet rs = psSelectRegionByOSM.executeQuery();
            if (rs.next()){
                byte[] data = rs.getBytes(1);
                geom = wkbReader.read(data);
            }
        }
        catch (SQLException e) {
            Logger.e(TAG, "getRegionGeom(), problem with query", e);
            throw new RuntimeException("Problem when getting geometry for element osmId: " + osmId
                    + " and osm type: " + entityType.toString());
        } catch (ParseException e) {
                e.printStackTrace();
        }
        return geom;
}

    /**
     * Insert region into store database
     * @param region region to insert
     */
    public void insertRegion(Region region, ConfigurationCountry.CountryConf countryConf) {

        Logger.i(TAG, "insertRegion(), Insert region to DB: " + region.getEnName());
        Geometry geomSimplified = GeomUtils.simplifyMultiPolygon(region.getGeom(), Const.GEO_COUNTRY_POLYGON_SIMPLIFICATION_DISTANCE);

        try {
            // check if region already exist in database > if exist update the geometry
            Geometry oldGeom = getRegionGeom(region.getOsmId(), region.getEntityType());
            if (oldGeom != null){
                geomSimplified = geomSimplified.union(oldGeom);
                Logger.i(TAG, "insertRegion: Union geometry for existed region: " + region.getEnName());

                // update geometry in database
                psUpdateRegionGeomByOSM.setBytes(1, wkbWriter.write(geomSimplified));
                psUpdateRegionGeomByOSM.setLong(2, region.getOsmId());
                psUpdateRegionGeomByOSM.setString(3, getOsmEntityTypeCode(region.getEntityType()));
                psUpdateRegionGeomByOSM.execute();

                return;
            }


            // get parent region id based on datastore id
            int parentId = -1;
            if ( !countryConf.getDataStoreRegionId().equals("wo")){
                parentId = getRegionId(countryConf.getDataStoreParentRegionId());

                if (parentId == -1){
                    throw new RuntimeException("Can not obtain SQL region id for: " + countryConf.getDataStoreParentRegionId());
                }
            }


            conn.setAutoCommit(false);

            psInsertRegion.clearParameters();

            if ( !countryConf.getDataStoreParentRegionId().equals("wo")){
                psInsertRegion.setInt(1, parentId);
            }
            else {
                // parent id for worldwide is null
                psInsertRegion.setNull(1, java.sql.Types.INTEGER);
            }

            psInsertRegion.setLong(2, region.getOsmId());
            psInsertRegion.setString(3, getOsmEntityTypeCode(region.getEntityType()));
            psInsertRegion.setString(4, countryConf.getDataStoreRegionId());
            psInsertRegion.setInt(5, region.getAdminLevel());

            psInsertRegion.setBytes(6, wkbWriter.write(geomSimplified));
            psInsertRegion.execute();

            // obtain autogenerated id for insert of languages
            ResultSet rs = psInsertRegion.getGeneratedKeys();
            rs.next();
            int autoGeneratedID = rs.getInt(1);

            // INSERT REGION NAMES

            // add default name into list of lang mutation
            region.addNameInternational(Const.DEFAULT_LANG_CODE, region.getName());
            Set<Map.Entry<String, String>> entrySet = region.getNamesInternational().entrySet();
            for (Map.Entry<String, String> entry : entrySet){
                psInsertRegionNames.setInt(1, autoGeneratedID);
                psInsertRegionNames.setString(2, entry.getKey());
                String name = entry.getValue();
                String nameNormalized = Utils.normalizeNames(name);
                if ( !nameNormalized.equals(name)){
                    //store full name only if normalized name is different
                    psInsertRegionNames.setString(3, name);
                }
                else {
                    psInsertRegionNames.setNull(3, Types.VARCHAR);
                }
                psInsertRegionNames.setString(4, nameNormalized);
                psInsertRegionNames.addBatch();
            }
            psInsertRegionNames.executeBatch();
            commit(false);
        }
        catch (SQLException e) {
            Logger.e(TAG, "insertRegion(), problem with query", e);
            try {
                conn.rollback();
            } catch (SQLException e1) {
                e1.printStackTrace();
            }
        }
        finally {
            try {
                conn.setAutoCommit(true);
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

//    private void unionExistedEntryWithNew(Region region) {
//
//        // simplify geometry of region to insert
//        Geometry geomSimplified = GeomUtils.simplifyMultiPolygon(region.getGeom(), Const.GEO_COUNTRY_POLYGON_SIMPLIFICATION_DISTANCE);
//
//        Geometry geom = null;
//
//        try {
//
//            //Logger.i(TAG, "unionExistedEntryWithNew Union geometry for OSM element:  " + region.getOsmId()
//            //        + ", type: " + region.getEntityType());
//
//            // LOAD EXISTED GEOM
//            psSelectRegionByOSM.setLong(1,region.getOsmId());
//            psSelectRegionByOSM.setString(2, getOsmEntityTypeCode(region.getEntityType()));
//
//            ResultSet rs = psSelectRegionByOSM.executeQuery();
//            rs.next();
//            byte[] data = rs.getBytes(1);
//            geom = wkbReader.read(data);
//
//            // union geom with new one
//            geom = geom.union(geomSimplified);
//            //Logger.i(TAG, "unionExistedEntryWithNew: Union geometry " + GeomUtils.geomToGeoJson(geom));
//
//            // update geometry in database
//            psUpdateRegionGeomByOSM.setBytes(1, wkbWriter.write(geom));
//            psUpdateRegionGeomByOSM.setLong(2, region.getOsmId());
//            psUpdateRegionGeomByOSM.setString(3, getOsmEntityTypeCode(region.getEntityType()));
//            psUpdateRegionGeomByOSM.execute();
//
//
//
//
//        } catch (SQLException e) {
//            Logger.e(TAG, "unionExistedEntryWithNew(), problem with query", e);
//            e.printStackTrace();
//        } catch (ParseException e) {
//            Logger.e(TAG, "unionExistedEntryWithNew(), problem parsing WKB geometry", e);
//            e.printStackTrace();
//        }
//
//    }

    /**
     * Convert entity type into database osm type code
     * @param entityType type of object to get store database code
     * @return code for store database
     */
    private String getOsmEntityTypeCode (EntityType entityType){

        if (entityType == EntityType.Relation){
            return "r";
        }
        else if (entityType == EntityType.Way){
            return "w";
        }
        else if (entityType == EntityType.Node){
            return "n";
        }
        else if (entityType == EntityType.Bound){
            return "b";
        }
        return "n";
    }
}
