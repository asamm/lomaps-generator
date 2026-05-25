package com.asamm.osmTools.generator.lomaps;

import com.asamm.locus.MapTilerUploader;
import com.asamm.osmTools.cmdCommands.*;
import com.asamm.osmTools.config.Action;
import com.asamm.osmTools.config.AppConfig;
import com.asamm.osmTools.config.OnlineLoMapsConfig;
import com.asamm.osmTools.config.OverviewMapConfig;
import com.asamm.osmTools.mapConfig.ItemMap;
import com.asamm.osmTools.mapConfig.MapSource;
import com.asamm.osmTools.overviewMap.OverviewMapBuilder;
import com.asamm.osmTools.residential.ResidentialBuilder;
import com.asamm.osmTools.utils.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Sequential pipeline for all planet-level processing steps.
 *
 * Steps run in fixed order; each step self-checks whether its action is present
 * or whether its output already exists (skip-if-exists logic stays inside each method).
 */
class PlanetPipeline {

    private static final String TAG = PlanetPipeline.class.getSimpleName();

    private final MapSource mapSource;
    private final ItemMap planet;

    PlanetPipeline(MapSource mapSource, ItemMap planet) {
        this.mapSource = mapSource;
        this.planet = planet;
    }

    void run(List<Action> actions) {
        Logger.i(TAG, "================ PROCESS PLANET MAP ================");

        if (actions.contains(Action.TOURIST))      tourist(planet);
        if (actions.contains(Action.CONTOUR))      contour(planet);
        if (actions.contains(Action.RESIDENTIAL))  residential(planet);
        if (actions.contains(Action.OVERVIEW_MAP)) overviewMap();

        merge(planet, actions);

        if (actions.contains(Action.GENERATE_MAPSFORGE)) {
            mapsforgePlanetMap(planet);
            extractMapsforgeItems(planet);
        }

        if (actions.contains(Action.GENERATE_MBTILES_ONLINE)) mbtilesOnline(planet);

        if (actions.contains(Action.GENERATE_PMTILES_ONLINE)) {
            pmtilesPlanetOnline(planet);
            uploadToS3(planet);
        }

        if (actions.contains(Action.UPLOAD_MAPTILER)) uploadToMapTiler(planet);
    }

    // ---- TOURIST ----

    private void tourist(ItemMap planet) {
        Logger.i(TAG, "================ TOURIST ================");
        if (!planet.hasAction(Action.TOURIST)) return;

        if (!AppConfig.config.getOverwrite() && planet.getPathTourist().toFile().exists()) {
            Logger.i(TAG, "File with tourist path " + planet.getPathTourist() + " already exist - skipped.");
            return;
        }

        Path pathToSource = AppConfig.config.getPlanetConfig().getPlanetLatestPath();
        if (!pathToSource.toFile().exists()) {
            throw new IllegalArgumentException("Input file for tourist path " + pathToSource + " does not exist!");
        }
        new CmdLoMapsTools().generateTourist(pathToSource, planet.getPathTourist());
    }

    // ---- CONTOUR ----

    private void contour(ItemMap planet) {
        Logger.i(TAG, "================ CONTOUR ================");
        if (!planet.hasAction(Action.CONTOUR)) return;

        if (planet.getPathContour().toFile().exists()) {
            Logger.i(TAG, "File with contours " + planet.getPathContour() + " already exists");
            return;
        }
        new CmdContour(planet).generate();
    }

    // ---- OVERVIEW MAP ----

    private void overviewMap() {
        Logger.i(TAG, "================ OVERVIEW MAP ================");
        new OverviewMapBuilder().buildOverviewOsmPbf();
    }

    // ---- RESIDENTIAL ----

    private void residential(ItemMap planetItemMap) {
        Logger.i(TAG, "================ RESIDENTIAL ================");

        if (planetItemMap.getPathResidential().toFile().exists()) {
            Logger.i(TAG, "Residential PBF already exists, skipping: " + planetItemMap.getPathResidential());
            return;
        }
        ResidentialBuilder.INSTANCE.buildResidentialPbf(planetItemMap.getPathResidential());
    }

    // ---- MERGE ----

    private void merge(ItemMap itemMap, List<Action> actions) {
        Logger.i(TAG, "================ MERGE PLANET MAP ================");

        if (!AppConfig.config.getOverwrite() && itemMap.getPathSource().toFile().exists()) {
            Logger.i(TAG, "Merged itemMap file " + itemMap.getPathSource() + " already exists");
            return;
        }

        List<Path> toMerge = new ArrayList<>();

        Path planetLatest = AppConfig.config.getPlanetConfig().getPlanetLatestPath();
        if (!planetLatest.toFile().exists()) {
            throw new IllegalArgumentException("Original latest OSM PBF file doesn't exist: " + planetLatest);
        }
        toMerge.add(planetLatest);

        if (actions.contains(Action.TOURIST) && itemMap.hasAction(Action.TOURIST)) {
            if (!itemMap.getPathTourist().toFile().exists()) {
                throw new IllegalStateException("Tourist routes file not found: " + itemMap.getPathTourist());
            }
            toMerge.add(itemMap.getPathTourist());
        }

        if (actions.contains(Action.CONTOUR) && itemMap.hasAction(Action.CONTOUR)) {
            if (!itemMap.getPathContour().toFile().exists()) {
                throw new IllegalStateException("Contour file not found: " + itemMap.getPathContour());
            }
            toMerge.add(itemMap.getPathContour());
        }

        if (itemMap.hasAction(Action.OVERVIEW_MAP)) {
            OverviewMapConfig cfg = AppConfig.config.getOverviewMapConfig();
            if (!cfg.getOutputPbf().toAbsolutePath().toFile().exists()) {
                throw new IllegalStateException("Overview PBF not found: " + cfg.getOutputPbf() +
                        ". Run the overview map build step first.");
            }
            toMerge.add(cfg.getOutputPbf().toAbsolutePath());
        }

        if (actions.contains(Action.RESIDENTIAL) && itemMap.hasAction(Action.RESIDENTIAL)) {
            if (!itemMap.getPathResidential().toFile().exists()) {
                throw new IllegalStateException("Residential PBF not found: " + itemMap.getPathResidential() +
                        ". Run the residential build step first.");
            }
            toMerge.add(itemMap.getPathResidential());
        }

        Utils.createParentDirs(itemMap.getPathSource());
        new CmdOsmium().merge(toMerge, itemMap.getPathSource());
    }

    // ---- MAPSFORGE PLANET ----

    /**
     * Generate mapsforge map with planet coverage
     * @param planet item for planet map
     */
    private void mapsforgePlanetMap(ItemMap planet) {
        if (!AppConfig.config.getOverwrite() && planet.getPathMapsforgeGenerate().toFile().exists()) {
            Logger.i(TAG, "Planet mapsforge map already exists: " + planet.getPathMapsforgeGenerate());
            return;
        }

        Logger.i(TAG, "Generating planet mapsforge map: " + planet.getPathMapsforgeGenerate());
        MapsforgeTilerRunner.generatePlanetMap(planet);
    }

    private void extractMapsforgeItems(ItemMap planet) {
        MapsforgeTilerRunner.extractFromPlanetMap(planet.getPathMapsforgeGenerate(), mapSource);
    }

    // ---- MBTILES ONLINE (MapLibre outdoor) ----

    private void mbtilesOnline(ItemMap planet) {
        if (!planet.hasAction(Action.GENERATE_MBTILES_ONLINE)) return;

        Logger.i(TAG, "================ GENERATE MBTILES ONLINE " + planet.getFileName() + " ================");
        if (!AppConfig.config.getOverwrite() && planet.getPathGenMlOutdoor().toFile().exists()) {
            Logger.i(TAG, "MapLibre outdoor map already exists: " + planet.getPathGenMlOutdoor());
            return;
        }

        TimeWatch time = new TimeWatch();
        Logger.i(TAG, "Generating MapLibre outdoor map: " + planet.getPathGenMlOutdoor());

        new CmdPlanetiler().generateOutdoorTiles(
                planet.getPathSource(), planet.getPathGenMlOutdoor(), planet.getPathPolygon());

        Logger.i(TAG, "MapLibre outdoor map done in " + time.getElapsedTimeSec() + " sec");
    }

    // ---- PMTILES ----

    private void pmtilesPlanetOnline(ItemMap planet) {
        if (!planet.hasAction(Action.GENERATE_PMTILES_ONLINE)) return;

        Logger.i(TAG, "================ GENERATE PMTILES ONLINE " + planet.getFileName() + " ================");
        if (!AppConfig.config.getOverwrite() && planet.getPathGenPmtilesOnline().toFile().exists()) {
            Logger.i(TAG, "PMTiles already exists: " + planet.getPathGenPmtilesOnline());
            return;
        }

        generatePlanetMbtiles(planet);

        TimeWatch time = new TimeWatch();
        Logger.i(TAG, "Converting MBtiles to PMTiles: " + planet.getPathGenPmtilesOnline());

        CmdPmtiles cmdPmtiles = new CmdPmtiles();
        cmdPmtiles.convertToPmtiles(planet.getPathMbtiles(), planet.getPathGenPmtilesOnline());
        cmdPmtiles.verifyPmtiles(planet.getPathGenPmtilesOnline());

        Logger.i(TAG, "PMTiles done in " + time.getElapsedTimeSec() + " sec");
    }

    private void generatePlanetMbtiles(ItemMap planet) {
        if (!planet.hasAction(Action.GENERATE_MBTILES)) return;
        if (!AppConfig.config.getOverwrite() && planet.getPathMbtiles().toFile().exists()) return;

        TimeWatch time = new TimeWatch();
        Logger.i(TAG, "Generating Planet MbTiles: " + planet.getPathMbtiles());

        new CmdPlanetiler().generateLoMapsOpenMapTiles(
                planet.getPathSource(), planet.getPathMbtiles(), planet.getPathPolygon());

        Logger.i(TAG, "Planet MbTiles done in " + time.getElapsedTimeSec() + " sec");
    }

    // ---- S3 UPLOAD ----

    private void uploadToS3(ItemMap planet) {
        if (!planet.hasAction(Action.GENERATE_PMTILES_ONLINE)) return;

        Logger.i(TAG, "================ UPLOAD PMTILES ONLINE TO S3 " + planet.getFileName() + " ================");
        if (!planet.getPathGenPmtilesOnline().toFile().exists()) {
            throw new IllegalArgumentException("PMTiles file not found: " + planet.getPathGenPmtilesOnline());
        }

        try (S3Client s3Client = S3Client.Companion.fromAppConfig()) {
            OnlineLoMapsConfig cfg = AppConfig.config.getOnlineLoMapsConfig();
            boolean isDev = Utils.isLocalDEV();
            String latestPrefix   = isDev ? cfg.getS3pmtilesPathDev()         : cfg.getS3pmtilesPath();
            String versionsPrefix = isDev ? cfg.getS3pmtilesVersionsPathDev() : cfg.getS3pmtilesVersionsPath();

            new OnlinePlanetVersionsManager(s3Client, versionsPrefix, latestPrefix, cfg.getS3pmtilesVersionsKeep())
                    .publish(
                            planet.getPathGenPmtilesOnline().toFile(),
                            AppConfig.config.getVersion(),
                            planet.getPathGenPmtilesOnline().getFileName().toString());
        }
    }

    // ---- MAPTILER UPLOAD ----

    private void uploadToMapTiler(ItemMap planet) {
        Logger.i(TAG, "==== UPLOAD TO MAPTILER ====");
        if (!planet.getPathGenMlOutdoor().toFile().exists()) {
            throw new IllegalArgumentException("MapLibre outdoor map not found: " + planet.getPathGenMlOutdoor());
        }
        new MapTilerUploader().uploadAndInitializeMapTiles(planet.getPathGenMlOutdoor().toFile());
        Logger.i(TAG, "Tiles uploaded");
    }

    // ---- HELPERS ----

    private boolean containsContours(java.nio.file.Path sourcePath) {
        CmdOsmium cmd = new CmdOsmium();
        String meter = "w" + AppConfig.config.getContourConfig().getWayIdMeter();
        String feet  = "w" + AppConfig.config.getContourConfig().getWayIdFeet();
        return cmd.containsId(sourcePath, meter) || cmd.containsId(sourcePath, feet);
    }

    private boolean containsTourist(java.nio.file.Path sourcePath) {
        String touristId = "w" + AppConfig.config.getTouristConfig().getWayId();
        return new CmdOsmium().containsId(sourcePath, touristId);
    }
}