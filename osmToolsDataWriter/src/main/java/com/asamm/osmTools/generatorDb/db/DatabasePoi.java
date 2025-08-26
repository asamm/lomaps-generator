package com.asamm.osmTools.generatorDb.db;

import com.asamm.osmTools.generatorDb.data.AOsmObject;
import com.asamm.osmTools.generatorDb.data.OsmPoi;
import com.asamm.osmTools.generatorDb.input.definition.WriterPoiDefinition;
import com.asamm.osmTools.utils.Logger;

import javax.naming.directory.InvalidAttributesException;
import java.io.File;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

import static com.asamm.locus.features.loMaps.LoMapsDbConst.*;


public class DatabasePoi extends ADatabaseHandler {

	private static final String TAG = DatabasePoi.class.getSimpleName();
	
	private WriterPoiDefinition writerPoiDefinition;
	// statement for inserting values into TN_TAG_VALUES table
	private PreparedStatement psInsertTV;
	// statement for inserting points
	private PreparedStatement psInsertP;
	// statement for TN_POINTS_ROOT_SUB
	private PreparedStatement psInsertPRS;
	// statement for TN_POINTS_KEY_VALUE
	private PreparedStatement psInsertPKV;
	
	private Hashtable<String, Long> foldersRoot;
	private Hashtable<String, Long> foldersSub;
	private Hashtable<String, Long> tagKeys;
	private Hashtable<String, Long> tagValues;
	
	public DatabasePoi(File file, WriterPoiDefinition poiDefinition) throws Exception {
		super(file, false);


        this.writerPoiDefinition = poiDefinition;

        setTables();
	}

    @Override
    protected void cleanTables() {
        String sql = "";
        try {

            sql = "SELECT DisableSpatialIndex('" + TN_POINTS + "', '"+COL_GEOM+"')";
            executeStatement(sql);
            sql = "DROP TABLE IF EXISTS  idx_"+ TN_POINTS +"_"+COL_GEOM;
            executeStatement(sql);

            sql = "DROP INDEX IF EXISTS idx_prs_points_id ";
            executeStatement(sql);

            sql = "DROP INDEX IF EXISTS idx_prs_root_sub";
            executeStatement(sql);

            sql = "DROP INDEX IF EXISTS idx_prs_root";
            executeStatement(sql);

            sql = "DROP INDEX IF EXISTS idx_pkv_points_id";
            executeStatement(sql);


            sql ="DROP TABLE IF EXISTS  "+TN_POINTS_ROOT_SUB ;
            executeStatement(sql);

            sql ="DROP TABLE IF EXISTS  "+TN_POINTS_KEY_VALUE ;
            executeStatement(sql);

            sql ="DROP TABLE IF EXISTS  "+TN_POINTS ;
            executeStatement(sql);

        } catch (SQLException e) {
            Logger.e(TAG, "cleanTables(), problem with query: " + sql, e);
            e.printStackTrace();
        }

    }

    @Override
	protected void setTables() throws SQLException, InvalidAttributesException {
		// create table with types
		foldersRoot = insertValueTable(
				TN_FOLDERS_ROOT, writerPoiDefinition.foldersRoot);
		foldersSub = insertValueTable(
				TN_FOLDERS_SUB, writerPoiDefinition.foldersSub);
		tagKeys = insertValueTable(
				TN_TAG_KEYS, writerPoiDefinition.getSupportedKeysForDb());
		tagValues = insertValueTable(
				TN_TAG_VALUES, new ArrayList<String>());
		
		// prepare table for connection all together
		String sql = "CREATE TABLE " + TN_POINTS_ROOT_SUB + " (";
		sql += TN_POINTS + "_" + COL_ID + " INTEGER NOT NULL,";
		sql += TN_FOLDERS_ROOT + "_" + COL_ID + " INTEGER NOT NULL,";
		sql += TN_FOLDERS_SUB + "_" + COL_ID + " INTEGER NOT NULL)";
		executeStatement(sql);
		
		// prepare table for connecting points and it's keys, values
		sql = "CREATE TABLE " + TN_POINTS_KEY_VALUE + " (";
		sql += TN_POINTS + "_" + COL_ID + " INTEGER NOT NULL,";
		sql += TN_TAG_KEYS + "_" + COL_ID + " INTEGER NOT NULL,";
		sql += TN_TAG_VALUES + "_" + COL_ID + " INTEGER NOT NULL)";
		executeStatement(sql);
		
		// creating a POINT table
		sql = "CREATE TABLE " + TN_POINTS + " (";
		sql += COL_ID + " INTEGER NOT NULL,";
        sql += COL_TYPE + " TEXT NOT NULL,";
		sql += COL_NAME + " TEXT)";
		executeStatement(sql);
		
		// creating a POINT Geometry column
		sql = "SELECT AddGeometryColumn('" + TN_POINTS + "', '" + COL_GEOM + "', 4326, 'POINT', 'XY')";
		executeStatement(sql);
		
		// STATEMENTS
		initPreparedStatements();

	}

    @Override
    public void destroy() throws SQLException {
        // finish database
        String sql = "SELECT CreateSpatialIndex('" + TN_POINTS + "', 'geom')";
        executeStatement(sql);

        sql = "CREATE INDEX idx_prs_points_id ON " + TN_POINTS_ROOT_SUB +
                " (" + COL_POINTS_ID + ")";
        executeStatement(sql);

        sql = "CREATE INDEX idx_prs_root_sub ON " + TN_POINTS_ROOT_SUB +
                " (" + COL_FOL_ROOT_ID + ", " + COL_FOL_SUB_ID + ")";
        executeStatement(sql);

        sql = "CREATE INDEX idx_prs_root ON " + TN_POINTS_ROOT_SUB +
                " (" + COL_FOL_ROOT_ID + ")";
        executeStatement(sql);

        sql = "CREATE INDEX idx_pkv_points_id ON " + TN_POINTS_KEY_VALUE +
                " (" + COL_POINTS_ID + ")";
        executeStatement(sql);
        super.destroy();
    }

	@Override
	protected void initPreparedStatements() throws SQLException {
		// statement for values table
		psInsertTV = conn.prepareStatement(
				"INSERT INTO " + TN_TAG_VALUES + "(" + COL_NAME + ") VALUES(?)",
				Statement.RETURN_GENERATED_KEYS);

		// prepare statements
		StringBuilder sbP = new StringBuilder();
		sbP.append("INSERT INTO " + TN_POINTS + " " +
				"(" + COL_ID + ", " + COL_TYPE + ", " + COL_NAME + ", " + COL_GEOM + ") " +
				"VALUES (?, ?, ?, GeomFromText(?, 4326))");
		psInsertP = conn.prepareStatement(sbP.toString(),
				Statement.RETURN_GENERATED_KEYS);

		// prepare statement for TN_POINTS_ROOT_SUB
		StringBuilder sbPRS = new StringBuilder();
		sbPRS.append("INSERT INTO ").append(TN_POINTS_ROOT_SUB).append(" (");
		sbPRS.append(TN_POINTS).append("_").append(COL_ID).append(", ");
		sbPRS.append(TN_FOLDERS_ROOT).append("_").append(COL_ID).append(", ");
		sbPRS.append(TN_FOLDERS_SUB).append("_").append(COL_ID).append(") ");
		sbPRS.append("VALUES (?, ?, ?)");
		psInsertPRS = conn.prepareStatement(
				sbPRS.toString(), Statement.RETURN_GENERATED_KEYS);

		// prepare statement for TN_POINTS_KEY_VALUE
		StringBuilder sbPKV = new StringBuilder();
		sbPKV.append("INSERT INTO ").append(TN_POINTS_KEY_VALUE).append(" (");
		sbPKV.append(TN_POINTS).append("_").append(COL_ID).append(", ");
		sbPKV.append(TN_TAG_KEYS).append("_").append(COL_ID).append(", ");
		sbPKV.append(TN_TAG_VALUES).append("_").append(COL_ID).append(") ");
		sbPKV.append("VALUES (?, ?, ?)");
		psInsertPKV = conn.prepareStatement(
				sbPKV.toString(), Statement.RETURN_GENERATED_KEYS);
	}

	private Hashtable<String, Long> insertValueTable( String tableName,
			List<String> data) throws SQLException {

        String sql = "CREATE TABLE " + tableName + " (";
		sql += COL_ID + " INTEGER NOT NULL PRIMARY KEY,";
		sql += COL_NAME + " TEXT NOT NULL)";
		executeStatement(sql);
		
		Hashtable<String, Long> newFolder = new Hashtable<String, Long>();
		PreparedStatement prepStmt = conn.prepareStatement(
				"INSERT INTO " + tableName + "(" + COL_NAME + ") VALUES(?)",
				Statement.RETURN_GENERATED_KEYS);
		for (int i = 0; i < data.size(); i++) {
			String value = data.get(i);
			prepStmt.setString(1, value);
			int affectedRows = prepStmt.executeUpdate();
	        if (affectedRows == 0) {
	            throw new SQLException("inserting value failed, no rows affected.");
	        }
			
	        ResultSet generatedKeys = prepStmt.getGeneratedKeys();
	        if (generatedKeys.next()) {
	        	newFolder.put(value, generatedKeys.getLong(1));
	        	generatedKeys.close();
	        } else {
	            throw new SQLException("insertValueTable() failed, no generated key obtained.");
	        }
		}
		prepStmt.close();
		return newFolder;
	}
	

    /**************************************************/
    /*                  INSERT PART                   */
    /**************************************************/

	public void insertObject(AOsmObject obj) {
		// check data
		if (obj == null || !(obj instanceof OsmPoi)) {
			return;
		}

		// get types ID
		OsmPoi poi = (OsmPoi) obj;
		long poiRowId;
		try {
			// insert values
            psInsertP.setLong(1, poi.getId());
            psInsertP.setString(2, poi.getEntityType().getCode());
			if (poi.getName() != null && poi.getName().length() > 0) {
				psInsertP.setString(3, poi.getName());
			}
			String geom = String.format("POINT (%f %f)", poi.getLon(), poi.getLat());
			psInsertP.setString(4, getEscapedText(geom));

			// insert data
			int affectedRows = psInsertP.executeUpdate();
	        if (affectedRows == 0) {
	            throw new SQLException("inserting value failed, no rows affected.");
	        }

			// obtain new POI id - this method is not anymore needed, as POIs keep its OSM values
	        ResultSet generatedKeys = psInsertP.getGeneratedKeys();
	        if (generatedKeys.next()) {
	        	poiRowId = generatedKeys.getLong(1);
	        	generatedKeys.close();
	        } else {
	            throw new SQLException("Creating user failed, no generated key obtained.");
	        }
	        
	        // insert main keys into DB
	        List<OsmPoi.Tag> tags = poi.getTags();
	        for (int i = 0, m = tags.size(); i < m; i++) {
	        	OsmPoi.Tag tag = tags.get(i);
	        	long mainKeyId = tagKeys.get(tag.key);
	        	if (mainKeyId < 0) {
	        		throw new IllegalArgumentException("insertObject(), " +
	        				"unable to obtain ID for mainKey:" + tag.key);
	        	}

	        	Long mainValueId = tagValues.get(tag.value);
	        	if (mainValueId == null || mainValueId < 0) {
	        		mainValueId = insertValueIntoValueTable(tag.value);
	        	}
	        	if (mainKeyId < 0) {
	        		throw new IllegalArgumentException("insertObject(), " +
	        				"unable to obtain ID for mainValue:" + tag.value);
	        	}
	        	
	        	psInsertPKV.setLong(1, poiRowId);
	        	psInsertPKV.setLong(2, mainKeyId);
	        	psInsertPKV.setLong(3, mainValueId);
		     
				affectedRows = psInsertPKV.executeUpdate();
		        if (affectedRows == 0) {
		            throw new SQLException("inserting value failed, no rows affected.");
		        }
	        }
	        
	        // insert types (Root/Sub folders) into DB
            List<WriterPoiDefinition.DbRootSubContainer> containers = poi.getRootSubContainers();
	        for (int i = 0, m = containers.size(); i < m; i++) {
                WriterPoiDefinition.DbRootSubContainer nc = containers.get(i);
	        	psInsertPRS.setLong(1, poiRowId);
	        	psInsertPRS.setLong(2, foldersRoot.get(nc.folRoot));
	        	psInsertPRS.setLong(3, foldersSub.get(nc.folSub));
		     
				affectedRows = psInsertPRS.executeUpdate();
		        if (affectedRows == 0) {
		            throw new SQLException("inserting value failed, no rows affected.");
		        }
	        }

	        // clear defined values
	        psInsertP.clearParameters();
		} catch (SQLException e) {
            Logger.e(TAG, "insertPoi(), problem with query", e);
            e.printStackTrace();
		}
	}

    private long insertValueIntoValueTable(String value) throws SQLException {
        psInsertTV.setString(1, value);
        int affectedRows = psInsertTV.executeUpdate();
        if (affectedRows == 0) {
            throw new SQLException("inserting value failed, no rows affected.");
        }

        ResultSet generatedKeys = psInsertTV.getGeneratedKeys();
        long res = -1L;
        if (generatedKeys.next()) {
            res = generatedKeys.getLong(1);
            tagValues.put(value, res);
            generatedKeys.close();
        } else {
            throw new SQLException("insertValueTable() failed, no generated key obtained.");
        }
        return res;
    }
}
