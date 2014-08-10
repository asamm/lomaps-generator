package com.asamm.osmTools.generatorDb;

import java.io.File;

import com.asamm.osmTools.generatorDb.data.AOsmObject;
import com.asamm.osmTools.generatorDb.data.OsmPoi;
import com.asamm.osmTools.generatorDb.data.WayEx;
import com.asamm.osmTools.generatorDb.db.ADatabaseHandler;
import com.asamm.osmTools.generatorDb.db.DatabasePoi;
import org.openstreetmap.osmosis.core.domain.v0_6.Entity;
import org.openstreetmap.osmosis.core.domain.v0_6.Node;

public class GeneratorPoi extends AGenerator {
	
//	private static final Logger log = Logger.getLogger(GeneratorPoi.class);
	
	// output DB file
	private File outputDb;
	
	// handler for tags
	private DataWriterDefinition nodeHandler;
	
	public GeneratorPoi(File outputDbFile, DataWriterDefinition nodeHandler) throws Exception {
		this.outputDb = outputDbFile;
		this.nodeHandler = nodeHandler; 
		
		// initialize generator
		initialize();
	}

	@Override
	protected ADatabaseHandler prepareDatabase() throws Exception {
		return new DatabasePoi(outputDb, nodeHandler);
	}

	@Override
	protected AOsmObject addNodeImpl(Node node, ADatabaseHandler db) {
		return addObject(node, db);
	}

	@Override
	protected AOsmObject addWayImp(WayEx way, ADatabaseHandler db) {
		return addObject(way, db);
	}
	
	private AOsmObject addObject(Entity entity, ADatabaseHandler db) {
 		// generate OSM POI object
		OsmPoi poi = OsmPoi.create(entity, nodeHandler);
		if (poi == null) {
			return null;
		}

		// add to database
		((DatabasePoi) db).insertObject(poi);
		return poi;
	}
}
