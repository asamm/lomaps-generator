package com.asamm.osmTools.generator.lomaps;

import com.asamm.osmTools.cmdCommands.CmdLoMapsDbPlugin;
import com.asamm.osmTools.cmdCommands.CmdPlanetiler;
import com.asamm.osmTools.cmdCommands.CmdPoiV2;
import com.asamm.osmTools.config.Action;
import com.asamm.osmTools.config.AppConfig;
import com.asamm.osmTools.generator.AGenerator;
import com.asamm.osmTools.generatorDb.input.definition.WriterPoiDefinition;
import com.asamm.osmTools.generatorDb.plugin.ConfigurationCountry;
import com.asamm.osmTools.mapConfig.ItemMap;
import com.asamm.osmTools.mapConfig.ItemMapPack;
import com.asamm.osmTools.mapConfig.MapSource;
import com.asamm.osmTools.mbtilesextract.mbtiles.MbtilesCreator;
import com.asamm.osmTools.utils.Logger;
import com.asamm.osmTools.utils.TimeWatch;
import com.asamm.osmTools.utils.Utils;

import java.io.File;
import java.util.Iterator;
import java.util.List;

/**
 * Iterates over all map packs and maps, executing the per-map pipeline stages
 * in action-priority order (all packs/maps for stage X before stage Y).
 * <p>
 * Steps that require pack-level preparation (EXTRACT, country border) are run
 * before descending into individual maps. The Kotlin {@link AGenerator} reference
 * is used for those two inherited operations.
 */
class MapPipeline {

    private static final String TAG = MapPipeline.class.getSimpleName();

    private final AGenerator generator;
    private final MapSource mapSource;
    /**
     * Planet ItemMap — needed for the fallback planet-mbtiles generation inside per-map mbtiles.
     */
    private final ItemMap planet;

    MapPipeline(AGenerator generator, MapSource mapSource, ItemMap planet) {
        this.generator = generator;
        this.mapSource = mapSource;
        this.planet = planet;
    }

    void run(List<Action> actions) throws Exception {

        // EXTRACT — handled at pack level; must complete before ADDRESS_POI_DB
        if (actions.contains(Action.EXTRACT_OSM_PLANET)) {
            forEachPack(mp -> generator.actionExtractOsm(mp, mapSource));
        }

        // ADDRESS/POI DB — country-border prep per pack, then generation per map
        if (actions.contains(Action.ADDRESS_POI_DB)) {
            forEachPack(mp -> {
                generator.actionCountryBorder(mp, mapSource, ConfigurationCountry.StorageType.GEOJSON);
                forEachMap(mp, map -> {
                    addressPoiDatabase(map);
                });
            });
        }

        // MBTILES — per map
        if (actions.contains(Action.GENERATE_MBTILES)) {
            forEachPack(mp -> forEachMap(mp, this::generateMbtiles));
        }

        // POI V2 — per map
        if (actions.contains(Action.POI_DB_V2)) {
            forEachPack(mp -> forEachMap(mp, this::poiV2Database));
        }
    }

    // ---- ADDRESS / POI DB ----

    private void addressPoiDatabase(ItemMap map) throws Exception {
        if (!map.hasAction(Action.ADDRESS_POI_DB)) return;

        if (!AppConfig.config.getOverwrite()
                && map.getPathAddressDb().toFile().exists()
                && map.getPathAddressPoiDb().toFile().exists()) {
            Logger.d(TAG, "Address/POI database already exists, skipping: " + map.getPathAddressDb());
            return;
        }

        if (!map.getPathAddressDb().toFile().exists() || AppConfig.config.getOverwrite()) {
            CmdLoMapsDbPlugin cmd = new CmdLoMapsDbPlugin(map);
            Logger.i(TAG, "Filter data for Address DB");
            cmd.simplifyForAddress();
            Logger.i(TAG, "Generate Address DB");
            cmd.generateAddressDb();
            cmd.deleteTmpFile();
        }

        // Copy address DB to LoMaps Classic path and append POI data
        Logger.i(TAG, "Generate Address and POI DB for LoMaps Classic");
        Utils.copyFile(map.getPathAddressDb(), map.getPathAddressPoiDb(), true);

        File defFile = AppConfig.config.getPoiAddressConfig().getPoiDbXml().toAbsolutePath().toFile();
        WriterPoiDefinition definition = new WriterPoiDefinition(defFile);

        CmdLoMapsDbPlugin cmd = new CmdLoMapsDbPlugin(map);
        Logger.i(TAG, "Filter data for POI DB");
        cmd.simplifyForPoi(definition);
        Logger.i(TAG, "Generate POI DB");
        cmd.generatePoiDb();
        cmd.deleteTmpFile();
    }

    // ---- MBTILES ----

    private void generateMbtiles(ItemMap map) {
        if (!map.hasAction(Action.GENERATE_MBTILES)) return;

        if (!AppConfig.config.getOverwrite() && map.getPathMbtiles().toFile().exists()) {
            Logger.i(TAG, "MBtiles already exists, skipping: " + map.getPathMbtiles());
            return;
        }

        // Ensure planet mbtiles exists first
        if (!planet.getPathMbtiles().toFile().exists()) {
            TimeWatch time = new TimeWatch();
            Logger.i(TAG, "Generating planet MbTiles: " + planet.getPathMbtiles());

            new CmdPlanetiler().generateLoMapsOpenMapTiles(
                    planet.getPathSource(), planet.getPathMbtiles(), planet.getPathPolygon());

            Logger.i(TAG, "Planet MbTiles done in " + time.getElapsedTimeSec() + " sec");
            return;
        }

        TimeWatch time = new TimeWatch();
        Logger.i(TAG, "Generate mbtiles: " + map.getName());

        new MbtilesCreator().createMbtiles(
                planet.getPathMbtiles(),
                map.getPathMbtiles(),
                map.getPathPolygon(),
                map.getName(),
                1, 14);

        Logger.i(TAG, "MbTiles done in " + time.getElapsedTimeSec() + " sec");
    }

    // ---- POI V2 ----

    private void poiV2Database(ItemMap map) {
        if (!map.hasAction(Action.POI_DB_V2) || Utils.isLocalDEV()) return;

        new CmdPoiV2().initPoiGeneratorDB();

        if (!AppConfig.config.getOverwrite() && map.getPathPoiV2Db().toFile().exists()) {
            Logger.d(TAG, "POI V2 DB already exists, skipping: " + map.getPathPoiV2Db());
        } else {
            Logger.i(TAG, "Generate POI V2 DB: " + map.getPathPoiV2Db());
            new CmdPoiV2().generatePoiV2Db(map);
        }
    }

    // ---- ITERATION HELPERS ----

    private void forEachPack(PackConsumer consumer) throws Exception {
        Iterator<ItemMapPack> it = mapSource.getMapPacksIterator();
        while (it.hasNext()) {
            consumer.accept(it.next());
        }
    }

    private void forEachMap(ItemMapPack mp, MapConsumer consumer) throws Exception {
        for (int i = 0; i < mp.getMapsCount(); i++) {
            consumer.accept(mp.getMap(i));
        }
        for (int i = 0; i < mp.getMapPackCount(); i++) {
            forEachMap(mp.getMapPack(i), consumer);
        }
    }

    @FunctionalInterface
    private interface PackConsumer {
        void accept(ItemMapPack mp) throws Exception;
    }

    @FunctionalInterface
    private interface MapConsumer {
        void accept(ItemMap map) throws Exception;
    }
}