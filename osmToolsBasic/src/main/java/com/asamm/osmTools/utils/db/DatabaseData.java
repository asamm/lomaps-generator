package com.asamm.osmTools.utils.db;

import com.asamm.locus.features.dbAddressPoi.DbAddressPoiConst;
import com.asamm.osmTools.utils.Logger;
import org.sqlite.SQLiteConfig;

import javax.naming.directory.InvalidAttributesException;
import java.io.File;
import java.sql.*;

import static com.asamm.locus.features.dbAddressPoi.DbAddressPoiConst.*;
import static com.asamm.locus.features.dbAddressPoi.DbAddressPoiConst.COL_ID;

/**
 * Created by voldapet on 2015-10-27 .
 * Controller fot inserting metadata into data database
 *
 */
public class DatabaseData {

    private static final String TAG = DatabaseData.class.getSimpleName();

    /**
     * Location of SQLite database
     */
    private File dbFile;

    protected Connection conn;

    private Statement stmt;

    PreparedStatement psInsertMetaData;

    public DatabaseData(File file) throws Exception{

        this.dbFile = file;

        initialize();

        createTables();

        initPreparedStatemets ();
    }

    private void initialize() throws ClassNotFoundException,
            SQLException, InvalidAttributesException {

        // load the SQLite-JDBC driver using the current class loader
        Class.forName("org.sqlite.JDBC");

        // prepare configuration
        SQLiteConfig config = new SQLiteConfig();

        // prepare connection to database
        conn = DriverManager.getConnection(
                "jdbc:sqlite:" + dbFile.getAbsolutePath(),
                config.toProperties());

        // connect
        stmt = conn.createStatement();

        // set timeout to 30 sec.
        stmt.setQueryTimeout(30);

        conn.setAutoCommit(false);

    }

    public void createTables () {

        try {
            String sql = "DROP TABLE IF EXISTS " + DbAddressPoiConst.TN_META_DATA;
            executeStatement(sql);

            sql = "CREATE TABLE IF NOT EXISTS " + DbAddressPoiConst.TN_META_DATA + " ( ";
            sql += COL_ID + " TEXT PRIMARY KEY,";
            sql += COL_VALUE + " TEXT)";
            executeStatement(sql);

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void initPreparedStatemets() throws SQLException {
        psInsertMetaData = createPreparedStatement("INSERT INTO " +  TN_META_DATA
                + " ("+COL_ID+", "+COL_VALUE+") " +
                "VALUES (?, ?)");
    }


    protected PreparedStatement createPreparedStatement (String sql) throws SQLException {
        return conn.prepareStatement(sql);
    }

    protected void executeStatement(String sql) throws SQLException {
        Logger.i(TAG, "executeStatement(" + sql + ")");
        stmt.execute(sql);
    }

    public void commit(boolean closeConnection) {
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



    public void insertData (String key, String value){
        try {
            psInsertMetaData.clearParameters();
            psInsertMetaData.setString(1, key);
            psInsertMetaData.setString(2, value);

            psInsertMetaData.execute();

        }
        catch (SQLException e) {
            Logger.e(TAG, "insertData(), problem with query", e);
            e.printStackTrace();
        }
    }

}
