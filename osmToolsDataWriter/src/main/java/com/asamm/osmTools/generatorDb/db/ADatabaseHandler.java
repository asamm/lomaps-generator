package com.asamm.osmTools.generatorDb.db;

import com.asamm.osmTools.utils.Logger;
import com.asamm.osmTools.utils.Utils;
import com.vividsolutions.jts.io.WKBReader;
import com.vividsolutions.jts.io.WKBWriter;
import com.vividsolutions.jts.io.WKTReader;
import com.vividsolutions.jts.io.WKTWriter;
import org.sqlite.SQLiteConfig;

import javax.naming.directory.InvalidAttributesException;
import java.io.File;
import java.io.IOException;
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
        if (Utils.isSystemWindows()) {
            executeStatement("SELECT load_extension('mod_spatialite')");
        } else {
//          executeStatement("SELECT load_extension('/usr/local/lib/mod_spatialite')");
//          executeStatement("SELECT load_extension('/usr/lib/x86_64-linux-gnu/libspatialite.so.5')");
//          executeStatement("SELECT load_extension('/usr/lib/x86_64-linux-gnu/libsqlite3.so.0.8.6')");
            executeStatement("SELECT load_extension('/usr/local/lib/mod_spatialite')");
        }

        // enabling Spatial Metadata using v.2.4.0 this automatically
        // initializes SPATIAL_REF_SYS and GEOMETRY_COLUMNS
        executeStatement("SELECT InitSpatialMetadata(1)");

        // be ready for transactions
        conn.setAutoCommit(false);

        // set ready flag
        ready = true;
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
