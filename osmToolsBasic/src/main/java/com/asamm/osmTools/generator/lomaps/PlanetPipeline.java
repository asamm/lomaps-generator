package com.asamm.osmTools.generator.lomaps;

import com.asamm.locus.MapTilerUploader;
import com.asamm.osmTools.Main;
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
        if (actions.contains(Action.OVERVIEW_MAP)) overviewMap();
        if (actions.contains(Action.RESIDENTIAL))  residential(planet);

        merge(planet, actions);

        if (actions.contains(Action.GENERATE_MAPSFORGE)) {
            generatePlanetMap(planet);
            extractItems(planet);
        }

        if (actions.contains(Action.GENERATE_MBTILES_ONLINE)) mbtilesOnline(planet);

        if (actions.contains(Action.GENERATE_PMTILES_ONLINE)) {
            pmtiles(planet);
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

    private void residential(ItemMap planet) {
        Logger.i(TAG, "================ RESIDENTIAL ================");

        if (!AppConfig.config.getOverwrite() && planet.getPathResidential().toFile().exists()) {
            Logger.i(TAG, "Residential PBF already exists, skipping: " + planet.getPathResidential());
            return;
        }
        ResidentialBuilder.INSTANCE.buildResidentialPbf(planet.getPathResidential());
    }

    // ---- MERGE ----

    private void merge(ItemMap planet, List<Action> actions) {
        Logger.i(TAG, "================ MERGE PLANET MAP ================");

        if (!AppConfig.config.getOverwrite() && planet.getPathSource().toFile().exists()) {
            Logger.i(TAG, "Merged planet file " + planet.getPathSource() + " already exists");
            return;
        }

        List<Path> toMerge = new ArrayList<>();

        Path planetLatest = AppConfig.config.getPlanetConfig().getPlanetLatestPath();
        if (!planetLatest.toFile().exists()) {
            throw new IllegalArgumentException("Original planet file doesn't exist: " + planetLatest);
        }
        toMerge.add(planetLatest);

        if (actions.contains(Action.TOURIST) && planet.hasAction(Action.TOURIST)) {
            if (!planet.getPathTourist().toFile().exists()) {
                throw new IllegalArgumentException("Tourist routes file not found: " + planet.getPathTourist());
            }
            if (!planet.getPathSource().toFile().exists() || !containsTourist(planet.getPathSource())) {
                toMerge.add(planet.getPathTourist());
            } else {
                Logger.i(TAG, "Source already contains tourist paths: " + planet.getPathSource());
            }
        }

        if (actions.contains(Action.CONTOUR) && planet.hasAction(Action.CONTOUR)) {
            if (!planet.getPathContour().toFile().exists()) {
                throw new IllegalArgumentException("Contour file not found: " + planet.getPathContour());
            }
            if (!planet.getPathSource().toFile().exists() || !containsContours(planet.getPathSource())) {
                toMerge.add(planet.getPathContour());
            } else {
                Logger.i(TAG, "Source already contains contours: " + planet.getPathSource());
            }
        }

        if (planet.hasAction(Action.OVERVIEW_MAP)) {
            OverviewMapConfig cfg = AppConfig.config.getOverviewMapConfig();
            if (!cfg.getOutputPbf().toAbsolutePath().toFile().exists()) {
                throw new IllegalStateException("Overview PBF not found: " + cfg.getOutputPbf() +
                        ". Run the overview map build step first.");
            }
            toMerge.add(cfg.getOutputPbf().toAbsolutePath());
        }

        if (actions.contains(Action.RESIDENTIAL) && planet.hasAction(Action.RESIDENTIAL)) {
            if (!planet.getPathResidential().toFile().exists()) {
                throw new IllegalStateException("Residential PBF not found: " + planet.getPathResidential() +
                        ". Run the residential build step first.");
            }
            toMerge.add(planet.getPathResidential());
        }

        Utils.createParentDirs(planet.getPathSource());
        new CmdOsmium().merge(toMerge, planet.getPathSource());
    }

    // ---- MAPSFORGE PLANET ----

    private void generatePlanetMap(ItemMap planet) {
        if (!AppConfig.config.getOverwrite() && planet.getPathMapsforgeGenerate().toFile().exists()) {
            Logger.i(TAG, "Planet mapsforge map already exists: " + planet.getPathMapsforgeGenerate());
            return;
        }
        TimeWatch time = new TimeWatch();
        Logger.i(TAG, "Generating planet mapsforge map: " + planet.getPathMapsforgeGenerate());
        Main.mySimpleLog.print("\nGenerate planet map: " + planet.getName() + " ...");

        MapsforgeTilerRunner.generatePlanetMap(planet);

        Main.mySimpleLog.print("\t\t\tdone " + time.getElapsedTimeSec() + " sec");
    }

    private void extractItems(ItemMap planet) {
        MapsforgeTilerRunner.extractFromPlanetMap(planet.getPathMapsforgeGenerate(), mapSource);
    }

    // ---- MBTILES ONLINE (MapLibre outdoor) ----

    private void mbtilesOnline(ItemMap planet) {
        if (!planet.hasAction(Action.GENERATE_MBTILES_ONLINE)) return;

        Logger.i(TAG, "================ GENERATE MBTILES ONLINE " + planet.getName() + " ================");
        if (!AppConfig.config.getOverwrite() && planet.getPathGenMlOutdoor().toFile().exists()) {
            Logger.i(TAG, "MapLibre outdoor map already exists: " + planet.getPathGenMlOutdoor());
            return;
        }

        TimeWatch time = new TimeWatch();
        Logger.i(TAG, "Generating MapLibre outdoor map: " + planet.getPathGenMlOutdoor());
        Main.mySimpleLog.print("\nGenerate: " + planet.getName() + " ...");

        new CmdPlanetiler().generateOutdoorTiles(
                planet.getPathSource(), planet.getPathGenMlOutdoor(), planet.getPathPolygon());

        Main.mySimpleLog.print("\t\t\tdone " + time.getElapsedTimeSec() + " sec");
    }

    // ---- PMTILES ----

    private void pmtiles(ItemMap planet) {
        if (!planet.hasAction(Action.GENERATE_PMTILES_ONLINE)) return;

        Logger.i(TAG, "================ GENERATE PMTILES ONLINE " + planet.getName() + " ================");
        if (!AppConfig.config.getOverwrite() && planet.getPathGenPmtilesOnline().toFile().exists()) {
            Logger.i(TAG, "PMTiles already exists: " + planet.getPathGenPmtilesOnline());
            return;
        }

        generatePlanetMbtiles(planet);

        TimeWatch time = new TimeWatch();
        Logger.i(TAG, "Converting MBtiles to PMTiles: " + planet.getPathGenPmtilesOnline());
        Main.mySimpleLog.print("\nGenerate PMTiles: " + planet.getName() + " ...");

        CmdPmtiles cmdPmtiles = new CmdPmtiles();
        cmdPmtiles.convertToPmtiles(planet.getPathMbtiles(), planet.getPathGenPmtilesOnline());
        cmdPmtiles.verifyPmtiles(planet.getPathGenPmtilesOnline());

        Main.mySimpleLog.print("\t\t\tdone " + time.getElapsedTimeSec() + " sec");
    }

    private void generatePlanetMbtiles(ItemMap planet) {
        if (!planet.hasAction(Action.GENERATE_MBTILES)) return;
        if (!AppConfig.config.getOverwrite() && planet.getPathMbtiles().toFile().exists()) return;

        TimeWatch time = new TimeWatch();
        Logger.i(TAG, "Generating Planet MbTiles: " + planet.getPathMbtiles());
        Main.mySimpleLog.print("\nGenerate Planet: " + planet.getName() + " ...");

        new CmdPlanetiler().generateLoMapsOpenMapTiles(
                planet.getPathSource(), planet.getPathMbtiles(), planet.getPathPolygon());

        Main.mySimpleLog.print("\t\t\tdone " + time.getElapsedTimeSec() + " sec");
    }

    // ---- S3 UPLOAD ----

    private void uploadToS3(ItemMap planet) {
        if (!planet.hasAction(Action.GENERATE_PMTILES_ONLINE)) return;

        Logger.i(TAG, "================ UPLOAD PMTILES ONLINE TO S3 " + planet.getName() + " ================");
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