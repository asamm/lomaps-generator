package com.asamm.osmTools.generatorDb.db;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;

public class DatabaseAddress extends ADatabaseHandler {

	public DatabaseAddress(File file) {
		super(file, true);
		// TODO Auto-generated constructor stub
	}

	@Override
	protected void setTables(Connection conn) throws SQLException {
//		// prepare database
//		// creating a POINT table
//		String sql = "CREATE TABLE points (";
//		sql += "id INTEGER NOT NULL PRIMARY KEY,";
//		sql += "type TEXT NOT NULL,";
//		sql += "name TEXT NOT NULL,";
//		sql += "desc TEXT)";
//		stmt.execute(sql);
//		
//		// creating a POINT Geometry column
//		sql = "SELECT AddGeometryColumn('points', ";
//		sql += "'geom', 4326, 'POINT', 'XY')";
//		stmt.execute(sql);
	}

//	@Override
//	protected void insertObject(AOsmObject obj, Statement stmt) {
//		// check data
//		if (!(obj instanceof OsmPoi)) {
//			return;
//		}
//		
//		OsmPoi poi = (OsmPoi) obj;
//		StringBuilder sb = new StringBuilder();
//		try {
//			// generate query
//			sb.append("INSERT INTO points (type, name, desc, geom) VALUES (");
//			sb.append("'").append(getEscapedText(poi.getType())).append("',");
//			sb.append("'").append(getEscapedText(poi.getName())).append("',");
//			sb.append("'").append(getEscapedText(poi.getDescription())).append("',");
//			sb.append("GeomFromText('POINT(").append(poi.getLon()).append(" ").
//			append(poi.getLat()).append(")', 4326))");
//				
//			// execute query
//			stmt.executeUpdate(sb.toString());
//		} catch (SQLException e) {
//			log.error("insertPoi(), problem with query:" + sb.toString(), e);
//		}
//	}
}
