package com.asamm.osmTools.generator.lomaps;

import com.asamm.locus.features.loMaps.LoMapsDbConst;
import com.asamm.osmTools.cmdCommands.CmdLoMapsDbPlugin;
import com.asamm.osmTools.cmdCommands.CmdPlanetiler;
import com.asamm.osmTools.cmdCommands.CmdPoiV2;
import com.asamm.osmTools.config.Action;
import com.asamm.osmTools.config.AppConfig;
import com.asamm.osmTools.generator.AGenerator;
import com.asamm.osmTools.generatorDb.input.definition.WriterAddressDefinition;
import com.asamm.osmTools.generatorDb.input.definition.WriterPoiDefinition;
import com.asamm.osmTools.generatorDb.plugin.ConfigurationCountry;
import com.asamm.osmTools.generatorDb.utils.GeomUtils;
import com.asamm.osmTools.mapConfig.ItemMap;
import com.asamm.osmTools.mapConfig.ItemMapPack;
import com.asamm.osmTools.mapConfig.MapSource;
import com.asamm.osmTools.mbtilesextract.mbtiles.MbtilesCreator;
import com.asamm.osmTools.utils.Logger;
import com.asamm.osmTools.utils.TimeWatch;
import com.asamm.osmTools.utils.Utils;
import com.asamm.osmTools.utils.db.DatabaseData;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.WKTWriter;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
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
                    insertMetaData(map);
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

    // ---- INSERT METADATA ----

    private void insertMetaData(ItemMap map) throws Exception {
        if (!map.hasAction(Action.GENERATE_MAPSFORGE)) return;

        Geometry geom = WriterAddressDefinition.createDbGeom(
                map.getPathJsonPolygon().toString(),
                map.getPathCountryBoundaryGeoJson().toString());

        if (!geom.isValid()) geom = GeomUtils.fixInvalidGeom(geom);

        if (!geom.isValid() || geom.isEmpty() || geom.getArea() == 0) {
            Logger.i(TAG, GeomUtils.geomToGeoJson(geom));
            throw new IllegalArgumentException("Country map geometry is not valid, map: " + map.getName());
        }

        insertMetadata(map, map.getPathAddressPoiDb().toFile(), geom);
        insertMetadata(map, map.getPathAddressDb().toFile(), geom);
    }

    private void insertMetadata(ItemMap map, File dbFile, Geometry geom) throws Exception {
        if (dbFile == null || !dbFile.exists()) {
            Logger.w(TAG, "DB file for metadata doesn't exist: " + dbFile);
            return;
        }

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy.MM.dd");
        Date dateVersion = sdf.parse(AppConfig.config.getVersion());

        DatabaseData dbData = new DatabaseData(dbFile);
        WKTWriter wktWriter = new WKTWriter();

        dbData.insertData(LoMapsDbConst.VAL_AREA, wktWriter.write(geom));
        dbData.insertData(LoMapsDbConst.VAL_COUNTRY, map.getCountryName());
        dbData.insertData(LoMapsDbConst.VAL_DESCRIPTION, AppConfig.config.getMapsforgeConfig().getMapMetaDataDescription());
        dbData.insertData(LoMapsDbConst.VAL_LANGUAGES, map.getPrefLang());
        dbData.insertData(LoMapsDbConst.VAL_OSM_DATE, String.valueOf(dateVersion.getTime()));
        dbData.insertData(LoMapsDbConst.VAL_REGION_ID, map.getRegionId());
        dbData.insertData(LoMapsDbConst.VAL_VERSION, AppConfig.config.getVersion());
        dbData.insertData(LoMapsDbConst.VAL_DB_POI_VERSION, String.valueOf(AppConfig.config.getPoiAddressConfig().getDbPoiVersion()));
        dbData.insertData(LoMapsDbConst.VAL_DB_ADDRESS_VERSION, String.valueOf(AppConfig.config.getPoiAddressConfig().getDbAddressVersion()));

        dbData.destroy();
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

        if (AppConfig.config.getOverwrite()
                || !map.getPathPoiV2Db(true).toFile().exists()
                || !map.getPathPoiV2Db(false).toFile().exists()) {
            Logger.i(TAG, "Initialize POI V2 Database");
            new CmdPoiV2().initPoiGeneratorDB();
        }

        if (!AppConfig.config.getOverwrite() && map.getPathPoiV2Db(true).toFile().exists()) {
            Logger.d(TAG, "POI V2 DB for MBtiles already exists, skipping: " + map.getPathPoiV2Db(true));
        } else {
            Logger.i(TAG, "Generate POI V2 DB for mbtiles: " + map.getPathPoiV2Db(true));
            new CmdPoiV2().generatePoiV2ForMbtiles(map);
        }

        if (!AppConfig.config.getOverwrite() && map.getPathPoiV2Db(false).toFile().exists()) {
            Logger.d(TAG, "POI V2 DB for Mapsforge already exists, skipping: " + map.getPathPoiV2Db(false));
        } else {
            Logger.i(TAG, "Generate POI V2 DB for Mapsforge: " + map.getPathPoiV2Db(false));
            new CmdPoiV2().generatePoiV2ForMapsforge(map);
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