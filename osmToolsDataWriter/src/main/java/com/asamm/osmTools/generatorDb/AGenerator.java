package com.asamm.osmTools.generatorDb;

import com.asamm.osmTools.generatorDb.data.AOsmObject;
import com.asamm.osmTools.generatorDb.data.WayEx;
import com.asamm.osmTools.generatorDb.dataContainer.ADataContainer;
import com.asamm.osmTools.generatorDb.db.ADatabaseHandler;
import com.asamm.osmTools.utils.Logger;
import org.openstreetmap.osmosis.core.domain.v0_6.Node;

import java.util.List;

public abstract class AGenerator {

    private static final String TAG = AGenerator.class.getSimpleName();

	private ADatabaseHandler db;
	
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
		for (int i = 0, m = nodes.size(); i < m; i++) {
			addNodeImpl(nodes.get(i), db);
		}
		
		// handle ways
        List<WayEx> ways = dc.getWays();
        for (int i = 0, m = ways.size(); i < m; i++) {
			addWayImp(ways.get(i), db);
		}
	}

	protected abstract AOsmObject addNodeImpl(Node node, ADatabaseHandler db);

	protected abstract AOsmObject addWayImp(WayEx way, ADatabaseHandler db);
}
