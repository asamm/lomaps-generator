package com.asamm.osmTools.generatorDb;

import com.asamm.osmTools.generatorDb.address.CityController;
import com.asamm.osmTools.generatorDb.address.Region;
import com.asamm.osmTools.generatorDb.dataContainer.ADataContainer;
import com.asamm.osmTools.generatorDb.db.ADatabaseHandler;
import com.asamm.osmTools.generatorDb.db.DatabaseAddress;
import com.asamm.osmTools.generatorDb.db.DatabaseStoreMysql;
import com.asamm.osmTools.utils.Logger;

import java.sql.SQLException;
import java.util.List;

/**
 * Created by voldapet on 2016-09-16 .
 */
public class GeneratorStoreGeocoding extends AGenerator {

    private static final String TAG = GeneratorStoreGeocoding.class.getSimpleName();

    /**
     * Definition what and how generate data
     */
    private WriterGeocodingDefinition wgd;

    /**
     * Controller for creation cities from nodes
     */
    CityController cc;

    /**
     * Handler for MySql database;
     */
    DatabaseStoreMysql mysqlDB;

    public GeneratorStoreGeocoding (WriterGeocodingDefinition wgd) throws SQLException {
        this.wgd = wgd;

        // init database
        this.mysqlDB = new DatabaseStoreMysql();
    }

    @Override
    protected ADatabaseHandler prepareDatabase() throws Exception {
        return null;
    }

    @Override
    public void proceedData(ADataContainer dc) {

        Logger.i(TAG, "=== Start store geocoding db generator ===");

        this.cc = new CityController(dc, null, wgd);


        Logger.i(TAG, "=== Step 1 - load city places ===");
        cc.createCities();

        // ---- step 2 create boundaries -----
        Logger.i(TAG, "=== Step 2 - create boundaries ===");
        cc.createBoundariesRegions();

        // ---- step 3 find center city for boundary -----
        Logger.i(TAG, "=== Step 3 - find center city for boundary ===");
        cc.findCenterCityForBoundary();

        // ---- step 4 create list of cities that are in boundary ----
        Logger.i(TAG, "=== Step 4 - find all cities inside boundaries ===");
        cc.findAllCitiesForBoundary();

        Logger.i(TAG, "=== Step 4B - find parent cities ===");
        cc.findParentCitiesAndRegions();

        Logger.i(TAG, "=== Step 5 - write cities to db ===");
        insertRegionsToDB(dc);
        //insertCitiesToDB(dc);



    }

    private void insertRegionsToDB(ADataContainer dc) {

        List<Region> regions = dc.getRegions();
        for (Region region : regions){
            mysqlDB.insertRegion(region, null);
        }
    }
}
