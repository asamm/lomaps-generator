package com.asamm.osmTools.generatorDb.db;

import com.asamm.osmTools.generatorDb.address.Street;
import com.asamm.osmTools.utils.Logger;
import com.vividsolutions.jts.io.*;
import org.sqlite.SQLiteConfig;

import javax.naming.directory.InvalidAttributesException;
import java.io.File;
import java.sql.*;
import java.util.List;

public abstract class ADatabaseHandler {

	private static final String TAG = ADatabaseHandler.class.getSimpleName();

    protected WKBWriter wkbWriter;
    protected WKBReader wkbReader;
    protected WKTWriter wktWriter;
    protected WKTReader wktReader;

	private boolean ready;
	private File dbFile;

	protected Connection conn;
	private Statement stmt;
	
	public ADatabaseHandler(File file, boolean deleteExistingDb) {
		this.dbFile = file;
		if (file.exists() && deleteExistingDb) {
			file.delete();
		}
		if (file.getParentFile() != null) {
			file.getParentFile().mkdirs();
		}
		ready = false;
	}

	protected void initialize() throws ClassNotFoundException, 
		SQLException, InvalidAttributesException {

        // init geometry writer
        wkbWriter = new WKBWriter();
        wktWriter = new WKTWriter(2);
        wkbReader = new WKBReader();
        wktReader = new WKTReader();

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

		// loading SpatiaLite
//        executeStatement("SELECT load_extension('/usr/local/lib/mod_spatialite')");
//        executeStatement("SELECT load_extension('/usr/lib/x86_64-linux-gnu/libspatialite.so.5')");
//        executeStatement("SELECT load_extension('/usr/lib/x86_64-linux-gnu/libsqlite3.so.0.8.6')");
        executeStatement("SELECT load_extension('/usr/local/lib/mod_spatialite')");
//        executeStatement("SELECT load_extension('mod_spatialite')");
//        executeStatement("SELECT load_extension('spatialite')");

		// enabling Spatial Metadata using v.2.4.0 this automatically
		// initializes SPATIAL_REF_SYS and GEOMETRY_COLUMNS
		executeStatement("SELECT InitSpatialMetadata()");

		// now set tables
		setTables(conn);
			
		// be ready for transactions
		conn.setAutoCommit(false);
			
		// set ready flag
		ready = true;
	}

    protected PreparedStatement createPreparedStatement (String sql) throws SQLException {
        return conn.prepareStatement(sql);
    }

	protected void executeStatement(String sql) throws SQLException {
		Logger.i(TAG, "executeStatement(" + sql + ")");
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
	
	protected abstract void setTables(Connection conn) 
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
		} catch (Exception e) {
            Logger.e(TAG, "destroy()", e);
		}
	}
	
	protected String getEscapedText(String text) {
		text = text.replace("'", "''");
		return text;
	}


}
