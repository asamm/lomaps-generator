package com.asamm.osmTools.generatorDb.db;

import com.asamm.osmTools.generatorDb.address.Region;
import com.asamm.osmTools.generatorDb.plugin.ConfigurationCountry;
import com.asamm.osmTools.generatorDb.utils.Const;
import com.asamm.osmTools.generatorDb.utils.GeomUtils;
import com.asamm.osmTools.generatorDb.utils.Language;
import com.asamm.osmTools.utils.Logger;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.io.*;
import org.openstreetmap.osmosis.core.domain.v0_6.EntityType;

import java.sql.*;
import java.util.Map;

import static com.asamm.store.LocusServerConst.*;

/**
 * Created by voldapet on 2016-09-19 .
 */
public class DatabaseStoreMysql {

    private static final String TAG = DatabaseStoreMysql.class.getSimpleName();

    public static final String LANGUAGE_LOCAL_POSTFIX = "def";

    private static final String IDX_DATASTORE_REGION_ID = "idx_datastore_region_id";

    private String url = "jdbc:mysql://104.155.156.154:3306/storedb"; // DEV
    private String password = "a4cuntvTXDqogv24QDZOQ9qR"; // DEV
    //private String url = "jdbc:mysql://localhost:3306/storedb";
    //private String url = "jdbc:mysql://35.184.159.49:3306/storedb"; // PROD
    private String username = "storedb";

    private Connection conn = null;

    private Statement stmt = null;

    protected WKBWriter wkbWriter;
    protected WKBReader wkbReader;

    protected WKTWriter wktWriter;
    protected WKTReader wktReader;


    // PREPARED STATEMENTS

    /**
     * Precompiled statement for inserting regions into db
     */
    private PreparedStatement psInsertRegion;

    /** */
    private PreparedStatement psSelectRegionIdByDatastoreId;

    /**
     * Insert international names of city
     */
    private PreparedStatement psInsertRegionNames;

    /**
     * Load region from database for defined OSM ID and entity type
     */
    private PreparedStatement psSelectRegionByOSM;

    /**
     * Update region geometry
     */
    private PreparedStatement psUpdateRegionGeomByOSM;


    public DatabaseStoreMysql() {

        initConnection();

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
    private void initConnection() {

        try {
            Class.forName("com.mysql.jdbc.Driver");

            this.conn = DriverManager.getConnection(url, username, password);

            this.stmt = conn.createStatement();
        } catch (ClassNotFoundException e) {
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
                if (closeConnection) {
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
    private void createTables() {

        String sql = "";
        try {
            sql = "CREATE TABLE IF NOT EXISTS " + TN_SLS_REGION + " (";
            sql += COL_ID + " INT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY, ";
            sql += COL_PARENT_ID + " INT UNSIGNED, ";
            sql += COL_STORE_REGION_ID + " VARCHAR(100), ";
            sql += COL_REGION_LEVEL + " TINYINT UNSIGNED NOT NULL, ";
            sql += COL_REGION_CODE + " VARCHAR(10), ";
            sql += COL_TIMESTAMP + " TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP ";
            sql += ", " + COL_NAME + "_" + Const.DEFAULT_LANG_CODE + " VARCHAR(511) NOT NULL ";
            for (Language language : Language.values()) {
                sql += ", " + COL_NAME + "_" + language.getCode() + " VARCHAR(511) NOT NULL ";
            }
            sql += "," + COL_GEOM + " GEOMETRY NOT NULL,";
            sql += " UNIQUE KEY " + IDX_DATASTORE_REGION_ID + " (" + COL_STORE_REGION_ID + ")";
            sql += ")";
            executeStatement(sql);


        } catch (SQLException e) {
            Logger.e(TAG, "createTables(), problem with query: " + sql, e);
            e.printStackTrace();
        }
    }


    private void initPreparedStatements() {

        try {
            psSelectRegionIdByDatastoreId = conn.prepareStatement("SELECT " + COL_ID + " FROM " + TN_SLS_REGION +
                    " WHERE " + COL_STORE_REGION_ID + " = ? ");

            String sqlInsertRegion = "INSERT INTO " + TN_SLS_REGION + " (";
            sqlInsertRegion += COL_PARENT_ID + ", " + COL_STORE_REGION_ID;
            sqlInsertRegion += ", " + COL_REGION_CODE + "," + COL_REGION_LEVEL;
            sqlInsertRegion += ", " + COL_NAME + "_" + Const.DEFAULT_LANG_CODE;
            for (Language lang : Language.values()) {
                sqlInsertRegion += ", " + COL_NAME + "_" + lang.getCode();
            }
            sqlInsertRegion += ", " + COL_GEOM + ") VALUES (";
            sqlInsertRegion += createQuestionMarksForINStatement(5 + Language.values().length);
            sqlInsertRegion += ", GeomFromWKB(?, 4326))";
            sqlInsertRegion += " ON DUPLICATE KEY UPDATE ";
            sqlInsertRegion += COL_PARENT_ID + " = VALUES(" + COL_PARENT_ID + ")";
            sqlInsertRegion += ", " + COL_STORE_REGION_ID + " = VALUES(" + COL_STORE_REGION_ID + ")";
            sqlInsertRegion += ", " + COL_REGION_CODE + " = VALUES(" + COL_REGION_CODE + ")";
            sqlInsertRegion += ", " + COL_REGION_LEVEL + " = VALUES(" + COL_REGION_LEVEL + ")";

            sqlInsertRegion += ", " + COL_NAME + "_" + Const.DEFAULT_LANG_CODE + " = VALUES(" + COL_NAME + "_" + Const.DEFAULT_LANG_CODE + ")";
            for (Language lang : Language.values()) {
                sqlInsertRegion += ", " + COL_NAME + "_" + lang.getCode() + " = VALUES(" + COL_NAME + "_" + lang.getCode() + ")";
            }
            sqlInsertRegion += ", " + COL_GEOM + " = VALUES(" + COL_GEOM + ")";
            psInsertRegion = conn.prepareStatement(sqlInsertRegion);


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

            sql = "DROP TABLE IF EXISTS  " + TN_SLS_REGION;
            executeStatement(sql);

            //re create tables again
            createTables();

        } catch (SQLException e) {
            Logger.e(TAG, "cleanTables(), problem with query: " + sql, e);
            e.printStackTrace();
        }
    }

    protected PreparedStatement createPreparedStatement(String sql) throws SQLException {
        return conn.prepareStatement(sql);
    }

    protected void executeStatement(String sql) throws SQLException {
        //Logger.i(TAG, "executeStatement(" + sql + ")");
        stmt.execute(sql);
    }

    /**
     * In mysql db  has region primary id as integer. Method get this in id from SQL database based on
     * old datastore regionId
     *
     * @param dataStoreRegionId datastore id of region to get id
     * @return id of region or -1 if such region does not exist in db
     */
    public int getRegionId(String dataStoreRegionId) {
        Logger.i(TAG, "getRegionId(), Get region id based on dataStoreId: " + dataStoreRegionId);

        try {
            psSelectRegionIdByDatastoreId.clearParameters();
            psSelectRegionIdByDatastoreId.setString(1, dataStoreRegionId);

            ResultSet rs = psSelectRegionIdByDatastoreId.executeQuery();

            while (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            Logger.e(TAG, "getRegionId(), problem with query", e);
            e.printStackTrace();
        }
        return -1;
    }

    /**
     * Load geometry of specified region from SQL database
     *
     * @param osmId      osm object id
     * @param entityType type of osm object
     * @return geometry for region or null if do not exist any region in database for id and object type
     */
    public Geometry getRegionGeom(long osmId, EntityType entityType) {

        Geometry geom = null;
        try {

            psSelectRegionByOSM.setLong(1, osmId);
            psSelectRegionByOSM.setString(2, getOsmEntityTypeCode(entityType));

            ResultSet rs = psSelectRegionByOSM.executeQuery();
            if (rs.next()) {
                byte[] data = rs.getBytes(1);
                geom = wkbReader.read(data);
            }
        } catch (SQLException e) {
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
     *
     * @param region region to insert
     */
    public void insertRegion(Region region, ConfigurationCountry.CountryConf countryConf) {

        Logger.i(TAG, "insertRegion(), Insert region to DB: " + region.getEnName() +
                ", datastoreID: " + countryConf.getDataStoreRegionId());
        Geometry geomSimplified = GeomUtils.simplifyMultiPolygon(region.getGeom(), Const.GEO_COUNTRY_POLYGON_SIMPLIFICATION_DISTANCE);

        try {

            // get parent region id based on datastore id
            int parentId = -1;
            if (!countryConf.getDataStoreRegionId().equals("wo")) {
                parentId = getRegionId(countryConf.getDataStoreParentRegionId());
                if (parentId == -1) {
                    throw new RuntimeException("Can not obtain SQL region id for: " + countryConf.getDataStoreParentRegionId());
                }
            } else {
                parentId = 0; // for worldwide is parent id 0
            }

            // prepare names
            // check names
            Map<String, String> names = updateMultiLangs(region.getNamesInternational(), null, true);

            conn.setAutoCommit(false);

            int counter = 1;
            psInsertRegion.setInt(counter++, parentId);
            psInsertRegion.setString(counter++, countryConf.getDataStoreRegionId());
            psInsertRegion.setString(counter++, countryConf.getRegionCode());
            psInsertRegion.setInt(counter++, region.getAdminLevel());

            String nameLocal = region.getNamesInternational().get(LANGUAGE_LOCAL_POSTFIX);
            nameLocal = (nameLocal == null) ? names.get(Language.ENGLISH.getCode()) : nameLocal;
            psInsertRegion.setString(counter++, nameLocal); // add name in local language

            for (Language language : Language.values()) {
                String name = names.get(language.getCode());
                psInsertRegion.setString(counter++, name);
            }

            psInsertRegion.setBytes(counter++, wkbWriter.write(geomSimplified));

            int responseCode = psInsertRegion.executeUpdate();

            commit(false);
        } catch (SQLException e) {
            Logger.e(TAG, "insertRegion(), problem with query", e);
            try {
                conn.rollback();
            } catch (SQLException e1) {
                e1.printStackTrace();
            }
        } finally {
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
     *
     * @param entityType type of object to get store database code
     * @return code for store database
     */
    private String getOsmEntityTypeCode(EntityType entityType) {

        if (entityType == EntityType.Relation) {
            return "r";
        } else if (entityType == EntityType.Way) {
            return "w";
        } else if (entityType == EntityType.Node) {
            return "n";
        } else if (entityType == EntityType.Bound) {
            return "b";
        }
        return "n";
    }


    /**
     * For prepared statements that contains IN section is needed customize SQL because number
     * of question marks is not always the same. This method create set of question marks based on size of list
     *
     * @param size num of question marks to create
     * @return set of question marks for example "?,?,?"
     */
    private String createQuestionMarksForINStatement(int size) {

        String str = "";
        for (int i = 0; i < size; i++) {
            if (i == size - 1) {
                str += " ?";
            } else {
                str += " ?,";
            }
        }
        return str;
    }

    /**
     * Feature compare new and old language mutations. It checks if English name is defined and checks if all languages have
     * defined the name. If custom lang is not defined then the EN name is used for these languages
     * Also compare old non-en values to new one to recognize if new non-english values should be replaced with
     * new EN value
     *
     * @param newLangs   lang mutations to save
     * @param oldLangs   old set of languages to compare with new one. set null for first import
     * @param strictMode Set <code>true</code> method raise exception if default EN value is not defined (useful for names)
     * @return
     * @throws RuntimeException
     */
    protected Map<String, String> updateMultiLangs(
            Map<String, String> newLangs, Map<String, String> oldLangs, boolean strictMode) throws RuntimeException {

        // check if EN name is defined
        String enName = newLangs.get(Language.ENGLISH.getCode());
        if (strictMode && (enName == null || enName.length() == 0)) {
            throw new RuntimeException("Text value in english is not defined");
        }

        String enNameOld = "";
        if (oldLangs != null) {
            enNameOld = oldLangs.get(Language.ENGLISH.getCode());
        }

        // check if every language has defined the name if not copy use the EN value, also compare the new value with old

        for (Language language : Language.values()) {

            String newName = newLangs.get(language.getCode());

            if (newName != null && newName.length() > 0) {
                if (newName.equals(enNameOld)) {
                    // the new name is the same as old en name > use current en name
                    newLangs.put(language.getCode(), enName);
                }
            } else {
                newLangs.put(language.getCode(), enName);
            }
        }
        return newLangs;
    }
}
