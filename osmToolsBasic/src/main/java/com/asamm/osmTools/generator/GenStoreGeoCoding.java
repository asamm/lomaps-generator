package com.asamm.osmTools.generator;

import com.asamm.osmTools.Parameters;
import com.asamm.osmTools.cmdCommands.CmdStoreGeo;
import com.asamm.osmTools.generatorDb.plugin.ConfigurationCountry;
import com.asamm.osmTools.mapConfig.ItemMap;
import com.asamm.osmTools.mapConfig.ItemMapPack;
import com.asamm.osmTools.mapConfig.MapSource;
import com.asamm.osmTools.utils.Logger;
import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;

/**
 * Created by voldapet on 2016-09-16 .
 */
public class GenStoreGeoCoding extends  AGenerator{

    private static final String TAG = GenStoreGeoCoding.class.getSimpleName();

    // parsed configuration of maps
    private MapSource mMapSource;

    public GenStoreGeoCoding () throws IOException, XmlPullParserException {

        mMapSource = parseConfigXml(new File(Parameters.getConfigStoreGeoPath()));

    }

    public void process () throws IOException, InterruptedException {

        // are there any data in mappack
        if (!mMapSource.hasData()){
            Logger.w(TAG, "No data was obtain from config xml");
            return;
        }

        // load mappack and do actions for mappack items
        Iterator<ItemMapPack> packs = mMapSource.getMapPacksIterator();
        while (packs.hasNext()) {
            ItemMapPack mp = packs.next();

            actionExtract(mp, mMapSource);

            Logger.i(TAG, "Map pack: " + mp.getName());
            actionCountryBorder (mp, mMapSource, ConfigurationCountry.StorageType.GEO_DATABASE);


            // iterate over all maps and perform actions
            for (int i = 0, m = mp.getMapsCount(); i < m; i++) {
                ItemMap map = mp.getMap(i);

                // TODO UNCOMMENT TO BE ABLE CREATE FULL GOECODING DB
                //actionStoreGeoCodingDB(map);
            }


        }

    }


    // GENETATE STORE GEO DB

    /**
     *  Create regions, cities from current map
     *  NOT IMPLEMENTED YET!
     */
    private void actionStoreGeoCodingDB (ItemMap map) throws IOException, InterruptedException {

        // check if map has defined generation of addresses > all map that have definition for adressess are used
        // for generation of geocoding
        if (!map.hasAction(Parameters.Action.ADDRESS_POI_DB)) {
            return;
        }

        //Address generation
        CmdStoreGeo cmdStoreGeoFilter = new CmdStoreGeo(map);
        cmdStoreGeoFilter.addTaskSimplifyForStoreGeo();
        Logger.i(TAG, "Filter data for StoreGeocoding DB, command: " + cmdStoreGeoFilter.getCmdLine());
        cmdStoreGeoFilter.execute();

        CmdStoreGeo cmdStoreGeo = new CmdStoreGeo(map);
        cmdStoreGeo.addGeneratorStoreGeoCodingDb();
        Logger.i(TAG, "Generate StoreGeoCoding, command: " + cmdStoreGeo.getCmdLine());
        cmdStoreGeo.execute();

        // delete tmp file
        cmdStoreGeo.deleteTmpFile();
    }
}
