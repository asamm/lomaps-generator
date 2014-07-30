package com.asamm.osmTools.generatorDb.db;

import com.asamm.osmTools.generatorDb.DataWriterDefinition;
import com.asamm.osmTools.utils.Logger;
import com.asamm.osmTools.utils.base64.Base64;
import org.openstreetmap.osmosis.core.domain.v0_6.Node;
import org.openstreetmap.osmosis.core.domain.v0_6.Way;
import org.openstreetmap.osmosis.core.store.DataInputStoreReader;
import org.openstreetmap.osmosis.core.store.DataOutputStoreWriter;
import org.openstreetmap.osmosis.core.store.DynamicStoreClassRegister;

import javax.naming.directory.InvalidAttributesException;
import java.io.*;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Hashtable;

public class DataContainerHdd extends ADataContainer {

    private static final String TAG = DataContainerHdd.class.getSimpleName();

	private ADatabaseHandler dbHandler;
	
	private Hashtable<Long, Way> ways;
	
	// dynamic register for database
	private ByteArrayOutputStream baos;
	private DataOutputStoreWriter dosw;
	private DynamicStoreClassRegister dynamicRegister;
	
	public DataContainerHdd(DataWriterDefinition nodeHandler, File tempFile) throws Exception {
		super(nodeHandler);
		this.ways = new Hashtable<Long, Way>();
		dbHandler = new ADatabaseHandler(tempFile, true) {
			
			@Override
			protected void setTables(Connection conn) throws SQLException,
					InvalidAttributesException {
				// create table with types
				String sql = "CREATE TABLE nodes (";
				sql += "id INTEGER NOT NULL PRIMARY KEY,";
				sql += "data BLOB NOT NULL)";
				Statement stmt = conn.createStatement();
				stmt.execute(sql);
				stmt.close();
			}
		};
	}

	@Override
	public void insertNodeToCache(Node node) {
		// check data
		if (node == null) {
			return;
		}
		
		baos.reset();
		node.store(dosw, dynamicRegister);
		try {
			baos.flush();
		} catch (IOException e1) {}
		StringBuilder sb = new StringBuilder();
		try {
			// generate query
			sb.append("INSERT INTO nodes (id, data) VALUES (");
			sb.append("'").append(node.getId()).append("', ");
			sb.append("'").append(Base64.encode(baos.toByteArray())).append("');");
				
			// execute query
			dbHandler.getStmt().executeUpdate(sb.toString());
		} catch (SQLException e) {
            Logger.e(TAG, "insertPoi(), problem with query:" + sb.toString(), e);
		}
	}

	@Override
	public void insertWayToCache(Way way) {
		ways.put(way.getId(), way);
	}

	@Override
	public Node getNodeFromCache(long id) {
		// generate query
		String query = "SELECT data FROM nodes WHERE id=" + id;
						
		// execute query
		try {
			ResultSet rs = dbHandler.getStmt().executeQuery(query);
			if (rs.next()) {
				byte[] nodeData = Base64.decode(rs.getString(1));
				ByteArrayInputStream bais = new ByteArrayInputStream(nodeData);
				DataInputStream dis = new DataInputStream(bais);
				DataInputStoreReader disr = new DataInputStoreReader(dis);
				Node node = new Node(disr, dynamicRegister);
				dis.close();
				return node;
			}
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

	@Override
	public Way getWayFromCache(long id) {
		return ways.get(id);
	}
	
	@Override
	public void destroy() {
		super.destroy();
		try {
			dbHandler.destroy();
		} catch (Exception e) {}
	}

}
