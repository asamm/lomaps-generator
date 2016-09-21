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

    private static final String IDX_REGION_OSM_ID_TYPE = "idx_region_osm_id_type";

    private String url = "jdbc:mysql://104.155.156.154:3306/storedb";
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

    /** Insert international names of city*/
    private PreparedStatement psInsertRegionNames;

    /** Load region from database for defined OSM ID and entity type*/
    private PreparedStatement psSelectRegionByOSM;

    /** Update region geometry*/
    private PreparedStatement psUpdateRegionGeomByOSM;


    public DatabaseStoreMysql () throws SQLException {

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

    /**
     * Create needed tables for geotables
     */
    private void createTables() throws SQLException {

        String sql = "CREATE TABLE IF NOT EXISTS "+ TN_GEO_REGION +" (";
        sql += COL_ID+" INT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY, ";
        sql += COL_OSM_ID + " BIGINT NOT NULL, ";
        sql += COL_OSM_DATA_TYPE + " CHAR(1) NOT NULL, ";
        sql += COL_STORE_REGION_ID + " VARCHAR(100), ";
        sql += COL_TYPE+" TINYINT UNSIGNED NOT NULL, ";
        sql += COL_TIMESTAMP + " TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, ";
        sql += COL_GEOM+ " GEOMETRY NOT NULL," ;
        sql += " UNIQUE KEY "+ IDX_REGION_OSM_ID_TYPE + " (" + COL_OSM_ID + "," + COL_OSM_DATA_TYPE +")";
        sql += ")";
        executeStatement(sql);

        sql = "CREATE TABLE IF NOT EXISTS "+ TN_GEO_REGION_NAME +" (";
        sql += COL_REGION_ID+" INT UNSIGNED NOT NULL,";
        sql += COL_LANG_CODE+" VARCHAR(3) NOT NULL, ";
        sql += COL_NAME+" TEXT, ";
        sql += COL_NAME_NORM+" TEXT NOT NULL," ;
        sql += " FOREIGN KEY (" + COL_REGION_ID +") REFERENCES " + TN_GEO_REGION +"("+COL_ID+") ON DELETE CASCADE" ;
        sql +=   ")";
        executeStatement(sql);

    }



    private void initPreparedStatements() throws SQLException {

        psInsertRegion = conn.prepareStatement(
                "INSERT INTO " + TN_GEO_REGION + " (" + COL_OSM_ID + ", " + COL_OSM_DATA_TYPE + ", "
                        + COL_STORE_REGION_ID + ", " + COL_TYPE + ", " + COL_GEOM +
                        ") VALUES (?, ?, ?, ?, ST_GeomFromWKB(?, 4326))",
                Statement.RETURN_GENERATED_KEYS);

        psInsertRegionNames = createPreparedStatement( "INSERT INTO "+ TN_GEO_REGION_NAME +
                " ("+COL_REGION_ID+", "+COL_LANG_CODE+", "+COL_NAME+", "+COL_NAME_NORM+
                " ) VALUES (?, ?, ?, ?)");

        psSelectRegionByOSM = createPreparedStatement( " SELECT + ST_AsBinary(" + COL_GEOM + ") FROM " + TN_GEO_REGION +
                " WHERE " + COL_OSM_ID + " = ? AND " + COL_OSM_DATA_TYPE + " = ?"   );

        psUpdateRegionGeomByOSM = createPreparedStatement( "UPDATE " + TN_GEO_REGION + " SET " + COL_GEOM +
                " = ST_GeomFromWKB(?, 4326) WHERE " + COL_OSM_ID + " = ? AND " + COL_OSM_DATA_TYPE + " = ?"   );
    }

    /**
     * Remove all geo tables from database
     */
    public void cleanTables() {
        String sql = "";
        try {

            sql ="DROP TABLE IF EXISTS  "+TN_GEO_REGION ;
            executeStatement(sql);

            sql ="DROP TABLE IF EXISTS  "+TN_GEO_REGION_NAME ;
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
     * Insert region into store database
     * @param region region to insert
     */
    public void insertRegion(Region region, ConfigurationCountry.CountryConf countryConf) {

        Geometry geomSimplified = GeomUtils.simplifyMultiPolygon(region.getGeom(), Const.GEO_COUNTRY_POLYGON_SIMPLIFICATION_DISTANCE);

        try {
            psInsertRegion.clearParameters();

            psInsertRegion.setLong(1, region.getOsmId());
            psInsertRegion.setString(2, getOsmEntityTypeCode(region.getEntityType()));
            if (countryConf == null){
                psInsertRegion.setString(3, null);
            }else {
                psInsertRegion.setString(3, countryConf.getStoreRegionCode());
            }
            psInsertRegion.setInt(4, region.getAdminLevel());

            psInsertRegion.setBytes(5, wkbWriter.write(geomSimplified));
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
                //Logger.i("TAG", " insertCity: lang Code= " + entry.getKey() + " name= " + entry.getValue() );
                psInsertRegionNames.clearParameters();
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
                psInsertRegionNames.execute();
            }
        }

        catch (MySQLIntegrityConstraintViolationException e){
            // same region probably exist in geo table. Union previous geometry with new one
            unionExistedEntryWithNew(region);
        }

        catch (SQLException e) {
            Logger.e(TAG, "insertRegion(), problem with query", e);
            e.printStackTrace();
        }




    }

    private void unionExistedEntryWithNew(Region region) {

        // simplify geometry of region to insert
        Geometry geomSimplified = GeomUtils.simplifyMultiPolygon(region.getGeom(), Const.GEO_COUNTRY_POLYGON_SIMPLIFICATION_DISTANCE);

        Geometry geom = null;

        try {

            Logger.i(TAG, "unionExistedEntryWithNew Union geometry for OSM element:  " + region.getOsmId()
                    + ", type: " + region.getEntityType());

            // LOAD EXISTED GEOM
            psSelectRegionByOSM.setLong(1,region.getOsmId());
            psSelectRegionByOSM.setString(2, getOsmEntityTypeCode(region.getEntityType()));

            ResultSet rs = psSelectRegionByOSM.executeQuery();
            rs.next();
            byte[] data = rs.getBytes(1);
            geom = wkbReader.read(data);

            // union geom with new one
            geom = geom.union(geomSimplified);
            Logger.i(TAG, "unionExistedEntryWithNew: Union geometry " + GeomUtils.geomToGeoJson(geom));

            // update geometry in database
            psUpdateRegionGeomByOSM.setBytes(1, wkbWriter.write(geom));
            psUpdateRegionGeomByOSM.setLong(2, region.getOsmId());
            psUpdateRegionGeomByOSM.setString(3, getOsmEntityTypeCode(region.getEntityType()));
            psUpdateRegionGeomByOSM.execute();




        } catch (SQLException e) {
            Logger.e(TAG, "unionExistedEntryWithNew(), problem with query", e);
            e.printStackTrace();
        } catch (ParseException e) {
            Logger.e(TAG, "unionExistedEntryWithNew(), problem parsing WKB geometry", e);
            e.printStackTrace();
        }

    }

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
        return "n";
    }
}
