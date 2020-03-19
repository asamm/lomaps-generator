package com.asamm.osmTools.generatorDb;

import com.asamm.osmTools.generatorDb.dataContainer.ADataContainer;
import com.asamm.osmTools.generatorDb.db.ADatabaseHandler;
import com.asamm.osmTools.utils.Logger;

public abstract class AGenerator {

    private static final String TAG = AGenerator.class.getSimpleName();

	protected ADatabaseHandler db;
	
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
	
	public abstract void proceedData(ADataContainer dc) ;


}
