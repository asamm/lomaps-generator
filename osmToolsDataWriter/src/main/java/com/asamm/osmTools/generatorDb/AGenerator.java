package com.asamm.osmTools.generatorDb;

import com.asamm.osmTools.generatorDb.data.AOsmObject;
import com.asamm.osmTools.generatorDb.data.WayEx;
import com.asamm.osmTools.generatorDb.db.ADataContainer;
import com.asamm.osmTools.generatorDb.db.ADatabaseHandler;
import com.asamm.osmTools.utils.Logger;
import org.openstreetmap.osmosis.core.domain.v0_6.Node;

import java.util.List;

public abstract class AGenerator {

    private static final String TAG = AGenerator.class.getSimpleName();

	private ADatabaseHandler db;
	
	public AGenerator() {}

	// prepare database
	protected void initialize() throws Exception {
		db = prepareDatabase();
		if (!db.isReady()) {
            Logger.e(TAG, "database is not ready! Terminating ...");
			System.exit(1);
		}
	}
		
	public void destroy() {
		try {
			if (db != null) {
				db.destroy();
			}
		} catch (Exception e) {
            Logger.e(TAG, "destroy()", e);
		}
	}
	
	protected abstract ADatabaseHandler prepareDatabase() throws Exception;
	
	// DATA HANDLING
	
	public void proceedData(ADataContainer dc) {
		// handle nodes
        List<Node> nodes = dc.getNodes();
		for (Node node : nodes) {
			addNodeImpl(node, db);
		}
		
		// handle ways
        List<WayEx> ways = dc.getWays();
		for (WayEx way : ways) {
			addWayImp(way, db);
		}
	}

	protected abstract AOsmObject addNodeImpl(Node node, ADatabaseHandler db);

	protected abstract AOsmObject addWayImp(WayEx way, ADatabaseHandler db);
}
