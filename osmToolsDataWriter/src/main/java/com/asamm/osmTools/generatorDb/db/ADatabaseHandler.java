package com.asamm.osmTools.generatorDb.db;

import com.asamm.osmTools.utils.Logger;
import com.asamm.osmTools.utils.Utils;
import org.locationtech.jts.io.WKBReader;
import org.locationtech.jts.io.WKBWriter;
import org.locationtech.jts.io.WKTReader;
import org.locationtech.jts.io.WKTWriter;
import org.sqlite.SQLiteConfig;

import javax.naming.directory.InvalidAttributesException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;

public abstract class ADatabaseHandler {

    private static final String TAG = ADatabaseHandler.class.getSimpleName();

    protected WKBWriter wkbWriter;
    protected WKBReader wkbReader;
    protected WKTWriter wktWriter;
    protected WKTReader wktReader;

    private boolean ready;
    protected File dbFile;
    boolean deleteExistingDb;

    protected Connection conn;
    private Statement stmt;

    public ADatabaseHandler(File file, boolean deleteExistingDb) throws Exception {
        this.dbFile = file;
        this.deleteExistingDb = deleteExistingDb;

        if (file.exists() && deleteExistingDb) {
            Logger.i(TAG, "Delete DB file: " + file.getAbsolutePath());
            file.delete();
        }

        if (file.getParentFile() != null) {
            file.getParentFile().mkdirs();
        }
        ready = false;

        // init geometry column writers / readers
        wkbWriter = new WKBWriter();
        wktWriter = new WKTWriter(2);
        wkbReader = new WKBReader();
        wktReader = new WKTReader();

        initialize();
    }

    private void initialize() throws ClassNotFoundException,
            SQLException, InvalidAttributesException, IOException {

        // load the SQLite-JDBC driver using the current class loader
        Class.forName("org.sqlite.JDBC");

        // prepare configuration
        SQLiteConfig config = new SQLiteConfig();
        config.enableLoadExtension(true);

        // prepare connection to database
        conn = DriverManager.getConnection(
                "jdbc:sqlite:" + dbFile.getAbsolutePath(),
                config.toProperties());

        // connect
        stmt = conn.createStatement();

        // set timeout to 30 sec.
        stmt.setQueryTimeout(30);

        // load spatialite extension
        loadSpatialite();

        // be ready for transactions
        conn.setAutoCommit(false);

        // set ready flag
        ready = true;
    }

    public void loadSpatialite() throws SQLException, IOException {

        try {
            executeStatement("SELECT load_extension('mod_spatialite')");
        } catch (SQLException e) {
            Logger.e(TAG,"Primary load failed: " + e.getMessage());
            // try windows dll
            executeStatement("SELECT load_extension('mod_spatialite.dll')");
        }

        // initializing Spatial Metadata
        executeStatement("SELECT InitSpatialMetadata(1)");

    }

    protected abstract void cleanTables();

    protected PreparedStatement createPreparedStatement(String sql) throws SQLException {
        return conn.prepareStatement(sql);
    }

    protected void executeStatement(String sql) throws SQLException {
        //Logger.i(TAG, "executeStatement(" + sql + ")");
        stmt.execute(sql);
    }

    public Statement getStmt() {
        return stmt;
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

    protected abstract void setTables()
            throws SQLException, InvalidAttributesException;

    public boolean isReady() {
        return ready;
    }

    public void destroy() throws SQLException {
        ready = false;
        try {
            if (stmt != null) {
                stmt.close();
            }
            // also commit
            commit(true);

            vacuum();
        } catch (Exception e) {
            Logger.e(TAG, "destroy()", e);
        }
    }

    protected String getEscapedText(String text) {
        text = text.replace("'", "''");
        return text;
    }

    protected void restartConnection() {
        commit(true);
        try {
            conn.close();

            initialize();

            initPreparedStatements();

            // set ready flag
            ready = true;
        } catch (SQLException e) {
            Logger.e(TAG, "restartConnection()", e);
            e.printStackTrace();
        } catch (InvalidAttributesException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }

    }

    protected abstract void initPreparedStatements() throws SQLException;

    protected void vacuum() {

        try {
            Connection conn = DriverManager.getConnection(
                    "jdbc:sqlite:" + dbFile.getAbsolutePath());

            Statement statement = conn.createStatement();
            statement.execute("VACUUM");
            statement.close();

            conn.close();

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
