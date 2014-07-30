package com.asamm.osmTools.generatorDb.db;

import com.asamm.osmTools.utils.Logger;
import org.sqlite.SQLiteConfig;

import javax.naming.directory.InvalidAttributesException;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public abstract class ADatabaseHandler {

	private static final String TAG = ADatabaseHandler.class.getSimpleName();

	private boolean ready;
	private File dbFile;

	private Connection conn;
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
        executeStatement("SELECT load_extension('/usr/local/lib/mod_spatialite')");

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
	
	protected void executeStatement(String sql) throws SQLException {
		Logger.d(TAG, "executeStatement(" + sql + ")");
		stmt.execute(sql);
	}
	
	protected Statement getStmt() {
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
