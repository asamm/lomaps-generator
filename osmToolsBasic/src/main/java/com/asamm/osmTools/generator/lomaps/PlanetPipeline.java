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
import com.asamm.pmtiles.PmTilesExtract;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Sequential pipeline for all planet-level processing steps.
 * <p>
 * Steps run in fixed order; each step self-checks whether its action is present
 * or whether its output already exists (skip-if-exists logic stays inside each method).
 */
public class PlanetPipeline {

    private static final String TAG = PlanetPipeline.class.getSimpleName();

    private final MapSource mapSource;
    private final ItemMap planet;

    public PlanetPipeline(MapSource mapSource, ItemMap planet) {
        this.mapSource = mapSource;
        this.planet = planet;
    }

    public void run(List<Action> actions) {
        Logger.i(TAG, "================ PROCESS PLANET MAP ================");

        if (actions.contains(Action.TOURIST)) tourist(planet);
        if (actions.contains(Action.CONTOUR)) contour(planet);
        if (actions.contains(Action.RESIDENTIAL)) residential(planet);
        if (actions.contains(Action.OVERVIEW_MAP)) overviewMap();

        merge(planet, actions);

        if (actions.contains(Action.GENERATE_MAPSFORGE)) {
            mapsforgePlanetMap(planet);
            extractMapsforgeItems(planet);
        }

        if (actions.contains(Action.GENERATE_MBTILES_ONLINE)) mbtilesOnline(planet);

        if (actions.contains(Action.GENERATE_PMTILES)) {
            pmtilesPlanetOnline(planet);
        }

        if (actions.contains(Action.UPLOAD_S3)) {
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

        if (actions.contains(Action.CONTOUR) && itemMap.hasAction(Action.CONTOUR)) {
            if (!itemMap.getPathContour().toFile().exists()) {
                throw new IllegalStateException("Contour file not found: " + itemMap.getPathContour());
            }
            toMerge.add(itemMap.getPathContour());
        }

        if (actions.contains(Action.TOURIST) && itemMap.hasAction(Action.TOURIST)) {
            if (!itemMap.getPathTourist().toFile().exists()) {
                throw new IllegalStateException("Tourist routes file not found: " + itemMap.getPathTourist());
            }
            toMerge.add(itemMap.getPathTourist());
        }

        if (actions.contains(Action.RESIDENTIAL) && itemMap.hasAction(Action.RESIDENTIAL)) {
            if (!itemMap.getPathResidential().toFile().exists()) {
                throw new IllegalStateException("Residential PBF not found: " + itemMap.getPathResidential() +
                        ". Run the residential build step first.");
            }
            toMerge.add(itemMap.getPathResidential());
        }

        if (itemMap.hasAction(Action.OVERVIEW_MAP)) {
            OverviewMapConfig cfg = AppConfig.config.getOverviewMapConfig();
            if (!cfg.getOutputPbf().toAbsolutePath().toFile().exists()) {
                throw new IllegalStateException("Overview PBF not found: " + cfg.getOutputPbf() +
                        ". Run the overview map build step first.");
            }
            toMerge.add(cfg.getOutputPbf().toAbsolutePath());
        }

        Utils.createParentDirs(itemMap.getPathSource());
        new CmdOsmium().merge(toMerge, itemMap.getPathSource());
    }

    // ---- MAPSFORGE PLANET ----

    /**
     * Generate mapsforge map with planet coverage
     *
     * @param planet item for planet map
     */
    private void mapsforgePlanetMap(ItemMap planet) {
        if (!AppConfig.config.getOverwrite() && planet.getPathMapsforgeGenerate().toFile().exists()) {
            Logger.i(TAG, "Planet mapsforge map already exists: " + planet.getPathMapsforgeGenerate());
            return;
        }
        Logger.i(TAG, "================ GENERATE MAPSFORGE " + planet.getFileName() + " ================");
        Logger.i(TAG, "Generating planet mapsforge map: " + planet.getPathMapsforgeGenerate());
        MapsforgeTilerRunner.generatePlanetMap(planet);
    }

    private void extractMapsforgeItems(ItemMap planet) {
        Logger.i(TAG, "================ EXTRACT MAPSFORGE ================");
        MapsforgeTilerRunner.extractFromPlanetMap(planet.getPathMapsforgeGenerate(), mapSource);
    }

    // ---- MBTILES ONLINE (MapLibre outdoor) ----

    private void mbtilesOnline(ItemMap planet) {
        if (!planet.hasAction(Action.GENERATE_MBTILES_ONLINE)) return;

        Logger.i(TAG, "================ GENERATE MBTILES FOR MAPTILER " + planet.getFileName() + " ================");
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
        if (!planet.hasAction(Action.GENERATE_PMTILES)) return;

        Logger.i(TAG, "================ GENERATE PMTILES " + planet.getFileName() + " ================");
        if (!AppConfig.config.getOverwrite() && planet.getPathPmtiles().toFile().exists()) {
            Logger.i(TAG, "PMTiles already exists: " + planet.getPathPmtiles());
            return;
        }

        generatePlanetPmtiles(planet);

        new CmdPmtiles().verifyPmtiles(planet.getPathPmtiles());
    }

    /**
     * Generates planet-level PMTiles directly via planetiler.
     * Used as the source for both S3 publishing (online) and per-map MBTiles extraction (offline).
     */
    void generatePlanetPmtiles(ItemMap planet) {
        if (!AppConfig.config.getOverwrite() && planet.getPathPmtiles().toFile().exists()) return;

        Logger.i(TAG, "================ GENERATE PMTILES " + planet.getFileName() + " ================");

        TimeWatch time = new TimeWatch();
        Logger.i(TAG, "Generating Planet PMTiles: " + planet.getPathPmtiles());

        new CmdPlanetiler().generateLoMapsPlanetPmtiles(
                planet.getPathSource(), planet.getPathPmtiles());

        Logger.i(TAG, "Planet PMTiles done in " + time.getElapsedTimeSec() + " sec");
    }

    // ---- S3 UPLOAD ----

    private void uploadToS3(ItemMap planet) {
        // Driven solely by the --release flag (which injects UPLOAD_S3 into the action list
        Logger.i(TAG, "================ UPLOAD PMTILES ONLINE TO S3 " + planet.getFileName() + " ================");
        if (!planet.getPathPmtiles().toFile().exists()) {
            throw new IllegalArgumentException("PMTiles file not found: " + planet.getPathPmtiles());
        }

        try (S3Client s3Client = S3Client.Companion.fromAppConfig()) {
            OnlineLoMapsConfig cfg = AppConfig.config.getOnlineLoMapsConfig();
            boolean isDev = Utils.isLocalDEV();
            String latestPrefix = isDev ? cfg.getS3pmtilesPathDev() : cfg.getS3pmtilesPath();
            String versionsPrefix = isDev ? cfg.getS3pmtilesVersionsPathDev() : cfg.getS3pmtilesVersionsPath();

            Path uploadFile = planet.getPathPmtiles();
            Path tempFile = null;

            if (isDev && cfg.getDevBbox() != null) {
                tempFile = extractForDevBbox(planet.getPathPmtiles(), cfg.getDevBbox());
                uploadFile = tempFile;
            }

            try {
                new OnlinePlanetVersionsManager(s3Client, versionsPrefix, latestPrefix, cfg.getS3pmtilesVersionsKeep())
                        .publish(
                                uploadFile.toFile(),
                                AppConfig.config.getVersion(),
                                planet.getPathPmtiles().getFileName().toString());
            } finally {
                if (tempFile != null) {
                    tempFile.toFile().delete();
                }
            }
        }
    }

    private Path extractForDevBbox(Path sourcePath, java.util.List<Double> devBbox) {
        Path tmpFile = AppConfig.config.getTemporaryDir().resolve("dev_bbox_" + sourcePath.getFileName());
        Utils.createParentDirs(tmpFile);

        Logger.i(TAG, "Extracting DEV bbox " + devBbox + " from " + sourcePath + " → " + tmpFile);

        PmTilesExtract.INSTANCE.extractBbox(
                sourcePath,
                tmpFile,
                devBbox.get(0),
                devBbox.get(1),
                devBbox.get(2),
                devBbox.get(3),
                (copied, total, bytes) -> Logger.i(TAG, "DEV bbox extract: " + copied + "/" + total + " tiles")
        );

        return tmpFile;
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
}