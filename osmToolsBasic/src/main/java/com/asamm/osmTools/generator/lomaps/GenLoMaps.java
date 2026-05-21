package com.asamm.osmTools.generator.lomaps;

import com.asamm.osmTools.config.Action;
import com.asamm.osmTools.config.AppConfig;
import com.asamm.osmTools.generator.AGenerator;
import com.asamm.osmTools.generator.PlanetUpdater;
import com.asamm.osmTools.mapConfig.ConfigXmlParser;
import com.asamm.osmTools.mapConfig.ItemMap;
import com.asamm.osmTools.mapConfig.MapSource;
import com.asamm.osmTools.utils.Logger;
import com.asamm.osmTools.utils.Utils;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.List;

/**
 * Top-level orchestrator for the LoMaps generation pipeline.
 * <p>
 * Delegates all work to three focused pipeline classes:
 * 1. {@link PlanetPipeline} — planet-wide preparation and map generation
 * 2. {@link MapPipeline}    — per-map extraction, databases, and tiles
 * 3. {@link PostPipeline}   — compression, JSON definition, and upload
 */
public class GenLoMaps extends AGenerator {

    private static final String TAG = GenLoMaps.class.getSimpleName();

    private final MapSource mMapSource;

    public GenLoMaps() throws IOException, XmlPullParserException {
        mMapSource = ConfigXmlParser.parseConfigXml(
                AppConfig.config.getMapsforgeConfig().getMapConfigXml().toFile());
    }

    public void process() throws Exception {
        if (!mMapSource.hasData()) {
            Logger.w(TAG, "No data was obtained from config xml");
            return;
        }

        if (!Utils.isLocalDEV()) {
            new PlanetUpdater().update();
        }

        List<Action> actions = AppConfig.config.getActions();
        ItemMap planetItemMap = mMapSource.getMapById(AppConfig.config.getPlanetConfig().getPlanetExtendedId());

        new PlanetPipeline(mMapSource, planetItemMap).run(actions);

        new MapPipeline(this, mMapSource, planetItemMap).run(actions);

        new PostPipeline(mMapSource).run(actions);
    }
}