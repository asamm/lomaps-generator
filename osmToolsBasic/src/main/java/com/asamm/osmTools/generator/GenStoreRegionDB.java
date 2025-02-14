package com.asamm.osmTools.generator;

import com.asamm.osmTools.generatorDb.plugin.ConfigurationCountry;
import com.asamm.osmTools.mapConfig.ItemMapPack;
import com.asamm.osmTools.mapConfig.MapSource;
import com.asamm.osmTools.utils.Logger;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Iterator;

/**
 * Created by voldapet on 2016-09-16 .
 */
public class GenStoreRegionDB extends AGenerator {

    private static final String TAG = GenStoreRegionDB.class.getSimpleName();

    // parsed configuration of maps
    private MapSource mMapSource;

    public GenStoreRegionDB(Path configXmlPath) throws IOException, XmlPullParserException {
        mMapSource = parseConfigXml(configXmlPath.toFile());
    }

    public void process() throws IOException, InterruptedException {
        // are there any data in mappack
        if (!mMapSource.hasData()) {
            Logger.w(TAG, "No data was obtain from config xml");
            return;
        }

        // load mappack and do actions for mappack items
        Iterator<ItemMapPack> packs = mMapSource.getMapPacksIterator();
        while (packs.hasNext()) {
            ItemMapPack mp = packs.next();

            actionExtract(mp, mMapSource);

            Logger.i(TAG, "Map pack: " + mp.getName());
            actionCountryBorder(mp, mMapSource, ConfigurationCountry.StorageType.STORE_REGION_DB);

        }
    }
}
