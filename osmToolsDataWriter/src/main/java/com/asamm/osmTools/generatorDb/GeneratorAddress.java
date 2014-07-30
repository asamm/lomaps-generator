package com.asamm.osmTools.generatorDb;

import java.sql.SQLException;

import com.asamm.osmTools.generatorDb.data.AOsmObject;
import com.asamm.osmTools.generatorDb.data.OsmAddress;
import com.asamm.osmTools.generatorDb.data.WayEx;
import com.asamm.osmTools.generatorDb.db.ADatabaseHandler;
import com.asamm.osmTools.utils.Logger;
import org.openstreetmap.osmosis.core.domain.v0_6.Node;

public class GeneratorAddress extends AGenerator {
	
	private static final String TAG = GeneratorAddress.class.getSimpleName();
	
	public GeneratorAddress() throws SQLException {
		super();
        Logger.d(TAG, "Prepared GeneratorAddress");
	}

	@Override
	protected ADatabaseHandler prepareDatabase() {
//		return new DatabaseAddress(getConf().getFileDatabase());
		return null;
	}

	@Override
	protected AOsmObject addNodeImpl(Node node, ADatabaseHandler db) {
 		// generate OSM poi object
		OsmAddress addr = OsmAddress.create(node);
		if (addr == null) {
			return null;
		}

		// add to database
		if (db != null) {
//			db.insertObject(addr);
		} 
		return addr;
	}

	@Override
	protected AOsmObject addWayImp(WayEx way, ADatabaseHandler db) {
		return null;
	}
}
