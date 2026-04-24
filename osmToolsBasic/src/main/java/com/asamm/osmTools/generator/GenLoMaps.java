package com.asamm.osmTools.generator;

import com.asamm.locus.MapTilerUploader;
import com.asamm.locus.features.loMaps.LoMapsDbConst;
import com.asamm.osmTools.Main;
import com.asamm.osmTools.cmdCommands.*;
import com.asamm.osmTools.compress.MapCompress;
import com.asamm.osmTools.config.Action;
import com.asamm.osmTools.config.AppConfig;
import com.asamm.osmTools.config.OnlineLoMapsConfig;
import com.asamm.osmTools.config.OverviewMapConfig;
import com.asamm.osmTools.generatorDb.input.definition.WriterAddressDefinition;
import com.asamm.osmTools.generatorDb.input.definition.WriterPoiDefinition;
import com.asamm.osmTools.generatorDb.plugin.ConfigurationCountry;
import com.asamm.osmTools.generatorDb.utils.GeomUtils;
import com.asamm.osmTools.mapConfig.ConfigXmlParser;
import com.asamm.osmTools.mapConfig.ItemMap;
import com.asamm.osmTools.mapConfig.ItemMapPack;
import com.asamm.osmTools.mapConfig.MapSource;
import com.asamm.osmTools.mbtilesextract.mbtiles.MbtilesCreator;
import com.asamm.osmTools.overviewMap.OverviewMapBuilder;
import com.asamm.osmTools.sea.LandArea;
import com.asamm.osmTools.utils.OnlinePlanetVersionsManager;
import com.asamm.osmTools.utils.S3Client;
import com.asamm.osmTools.server.UploadDefinitionCreator;
import com.asamm.osmTools.utils.Logger;
import com.asamm.osmTools.utils.TimeWatch;
import com.asamm.osmTools.utils.Utils;
import com.asamm.osmTools.utils.db.DatabaseData;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.WKTWriter;
import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

/**
 * Created by voldapet on 2016-09-16 .
 */
public class GenLoMaps extends AGenerator {

    private static final String TAG = GenLoMaps.class.getSimpleName();

    // parsed configuration of maps
    private final MapSource mMapSource;


    public GenLoMaps() throws IOException, XmlPullParserException {

        // parse definition xml
        mMapSource = ConfigXmlParser.parseConfigXml(AppConfig.config.getMapsforgeConfig().getMapConfigXml().toFile());
    }

    public void process() throws Exception {

        List<Action> actionList = AppConfig.config.getActions();

        // are there any data in mappack?
        if (!mMapSource.hasData()) {
            Logger.w(TAG, "No data was obtain from config xml");
            return;
        }


        if (!Utils.isLocalDEV()) {
            // run OSM update of planet file (update starts only if needed). Updater downloads planet file if it doesn't exist
            PlanetUpdater planetUpdater = new PlanetUpdater();
            planetUpdater.update();
        }

        // process action on planet level
        processPlanet(actionList, mMapSource);

        // for every action value in array do
        for (Action action : actionList) {

            // skip Contour and Tourist action, because they are processed on planet level
            if (action == Action.CONTOUR
                    || action == Action.TOURIST
                    || action == Action.GENERATE_PMTILES_ONLINE
                    || action == Action.OVERVIEW_MAP) {
                continue;
            }

            // print action header to log
            printLogHeader(action);

            // load mappack and do actions for mappack items
            Iterator<ItemMapPack> packs = mMapSource.getMapPacksIterator();
            while (packs.hasNext()) {
                ItemMapPack mp = packs.next();

                // handle extract first, because we need to handle whole pack at once
                if (action == Action.EXTRACT) {
                    actionExtract(mp, mMapSource);
                    continue;
                }

                if (action == Action.ADDRESS_POI_DB) {
                    actionCountryBorder(mp, mMapSource, ConfigurationCountry.StorageType.GEOJSON);
                }

                actionAllInOne(mp, action);
            }

            // needs to write definition JSON to file (in case that map was generated)
            if (action == Action.CREATE_JSON) {
                UploadDefinitionCreator uploadDefinitionCreator = new UploadDefinitionCreator();
                uploadDefinitionCreator.generateJsonUploadDefinition(mMapSource);
            }

            if (action == Action.COMPRESS) {
                printLogHeader(Action.COMPRESS);
                new MapCompress().compressAllMaps(mMapSource);
            }

            // perform remaining actions
            if (action == Action.UPLOAD) {
                printLogHeader(Action.UPLOAD);
                actionUpload();
            }
        }
    }


    private void printLogHeader(Action action) {
        String line = "================ " + action.getLabel().toUpperCase() + " ================\n";
        Logger.i(TAG, line);
        Main.mySimpleLog.print("\n" + line);
    }

    //               PERFORM ACTIONS

    private void processPlanet(List<Action> actionList, MapSource mMapSource) {
        // find ItemMap with if "planet" and process it
        Logger.i(TAG, "================ PROCESS PLANET MAP ================");
        ItemMap mapPlanet = mMapSource.getMapById(AppConfig.config.getPlanetConfig().getPlanetExtendedId());

        for (Action action : actionList) {
            switch (action) {
                case TOURIST:
                    actionTourist(mapPlanet);
                    break;
                case CONTOUR:
                    actionContour(mapPlanet);
                    break;

                case OVERVIEW_MAP:
                    OverviewMapBuilder overviewMapBuilder = new OverviewMapBuilder();
                    overviewMapBuilder.buildOverviewOsmPbf();
                    break;
            }
        }
        actionMergePlanet(mapPlanet);

        // generate lomaps outdoor planet tiles - will be deprecated when Asamm server is ready
        actionGenerateMbtilesOnline(mapPlanet);

        // generate PMTiles for planet and upload to S3
        actionGeneratePmtiles(mapPlanet);
        actionUploadOnlineToS3(mapPlanet);

        // upload to maptiler
        actionUploadPlanetToMapTiler(mapPlanet);
    }


    public void actionAllInOne(ItemMapPack mp, Action action)
            throws Exception {

        // iterate over all maps and perform actions
        for (int i = 0, m = mp.getMapsCount(); i < m; i++) {
            ItemMap map = mp.getMap(i);

            switch (action) {
                //download, tourist and contour are already processed for whole planet
                case GRAPH_HOPPER:
//                    actionGraphHopper(map);
                    break;
                case ADDRESS_POI_DB:
                    actionAddressPoiDatabase(map);
                    actionInsertMetaData(map);
                    break;
                case COASTLINE:
                    actionCoastline(map);
                    break;
                case TRANSFORM:
                    actionTransformData(map);
                    break;
                case MERGE:
                    actionMerge(map);
                    break;
                case GENERATE_MBTILES:
                    actionGenerateMbtiles(map);
                    break;
                case POI_DB_V2:
                    actionPoiV2Database(map);
                    break;
                case GENERATE_MAPSFORGE:
                    actionGenerate(map);
                    actionInsertMetaData(map);
                    break;
            }
        }

        // iterate over all MapPacks and call same function on them
        for (int i = 0, m = mp.getMapPackCount(); i < m; i++) {
            actionAllInOne(mp.getMapPack(i), action);
        }
    }

    // ACTION ADDRESS/POI DATABASE
    private void actionAddressPoiDatabase(ItemMap map) throws Exception {

        // check if map has defined generation of addresses
        if (!map.hasAction(Action.ADDRESS_POI_DB)) {
            return;
        }

        // check if DB file exits and we should overwrite it
        if (!AppConfig.config.getOverwrite() && map.getPathAddressDb().toFile().exists() && map.getPathAddressPoiDb().toFile().exists()) {
            Logger.d(TAG, "File with Address/POI database '" + map.getPathAddressDb() +
                    "' already exist - skipped.");
            return;
        }

        // load POI DB definitions
        // Generation of POI DB removed in 2025.06.30, because we use POI V2
//        File defFile = AppConfig.config.getPoiAddressConfig().getPoiDbXml().toAbsolutePath().toFile();
//        WriterPoiDefinition definition = new WriterPoiDefinition(defFile);
//
//        // firstly simplify source file
//        Logger.i(TAG, "Filter data for POI DB");
//        CmdLoMapsDbPlugin cmdLoMapsDbPlugin = new CmdLoMapsDbPlugin(map);
//        cmdLoMapsDbPlugin.simplifyForPoi(definition);
//
//        // now execute db poi generating
//        Logger.i(TAG, "Generate POI DB, command: ");
//        cmdLoMapsDbPlugin.generatePoiDb();


        //Utils.deleteFileQuietly(map.getPathAddressDb());
        //Address generation
        if (!map.getPathAddressDb().toFile().exists() || AppConfig.config.getOverwrite()) {
            CmdLoMapsDbPlugin cmdLoMapsDbPlugin = new CmdLoMapsDbPlugin(map);
            Logger.i(TAG, "Filter data for Address DB, command: ");
            cmdLoMapsDbPlugin.simplifyForAddress();

            Logger.i(TAG, "Generate Adrress DB, command: ");
            cmdLoMapsDbPlugin.generateAddressDb();
            cmdLoMapsDbPlugin.deleteTmpFile();
        }


        // Address and old POI generation for LM Classsic
        Logger.i(TAG, "Generate Address and POI DB for LoMaps Classic, command: ");
        // copy address db to the LoMaps Classic path
        Utils.copyFile(map.getPathAddressDb(), map.getPathAddressPoiDb(), true);

        File defFile = AppConfig.config.getPoiAddressConfig().getPoiDbXml().toAbsolutePath().toFile();
        WriterPoiDefinition definition = new WriterPoiDefinition(defFile);

        // firstly simplify source file
        Logger.i(TAG, "Filter data for POI DB");
        CmdLoMapsDbPlugin cmdLoMapsDbPlugin = new CmdLoMapsDbPlugin(map);
        cmdLoMapsDbPlugin.simplifyForPoi(definition);

        // now execute db poi generating
        Logger.i(TAG, "Generate POI DB, command: ");
        cmdLoMapsDbPlugin.generatePoiDb();

        // delete tmp file
        cmdLoMapsDbPlugin.deleteTmpFile();
    }

    private void actionPoiV2Database(ItemMap map) {

        // check if map has defined generation of mbtiles
        if (!map.hasAction(Action.POI_DB_V2) || Utils.isLocalDEV()) {
            return;
        }

        if (AppConfig.config.getOverwrite() ||
                !map.getPathPoiV2Db(true).toFile().exists() ||
                !map.getPathPoiV2Db(false).toFile().exists()) {

            // Initialize POI Database (refresh postgres)
            Logger.i(TAG, "Initialize POI V2 Database");
            new CmdPoiV2().initPoiGeneratorDB();
        }

        // check if DB file exits and we should overwrite it
        if (!AppConfig.config.getOverwrite() && map.getPathPoiV2Db(true).toFile().exists()) {
            Logger.d(TAG, "File with POI V2 database for Mbtiles '" + map.getPathPoiV2Db(true) +
                    "' already exist - skipped.");
        } else {
            //generete POI V2
            Logger.i(TAG, "Generate POI V2 Database for mbtiles: " + map.getPathPoiV2Db(true));
            new CmdPoiV2().generatePoiV2ForMbtiles(map);
        }

        if (!AppConfig.config.getOverwrite() && map.getPathPoiV2Db(false).toFile().exists()) {
            Logger.d(TAG, "File with POI V2 database for Mapsforge '" + map.getPathPoiV2Db(false) +
                    "' already exist - skipped.");
        } else {
            //generete POI V2
            Logger.i(TAG, "Generate POI V2 Database for Mapsforge: " + map.getPathPoiV2Db(false));
            new CmdPoiV2().generatePoiV2ForMapsforge(map);
        }

    }

    // ACTION COASTLINE

    private void actionCoastline(ItemMap map)
            throws IOException, InterruptedException {

        if (!map.hasAction(Action.GENERATE_MAPSFORGE)) {
            return;
        }

        // check if file exits and we should overwrite it
        if (!AppConfig.config.getOverwrite() && map.getPathCoastline().toFile().exists()) {
            Logger.i(TAG, "File with land area " + map.getPathCoastline()
                    + " already exist - skipped.");
            return;
        }

        // start Creation sea and nosea lands
        new LandArea(map).create();
    }

    // ACTION TOURIST

    private void actionTourist(ItemMap map) {
        // check if we want to do this action
        if (!map.hasAction(Action.TOURIST)) {
            return;
        }

        printLogHeader(Action.TOURIST);
        // check if file exits and we should overwrite it
        if (!AppConfig.config.getOverwrite() && map.getPathTourist().toFile().exists()) {
            Logger.i(TAG, "File with tourist path " + map.getPathTourist() + " already exist - skipped.");
            return;
        }

        // for planet it's needed to customize "source" path and use the orig planet file as source
        Path pathToSource = map.getPathSource();
        if (map.isPlanet()) {
            pathToSource = AppConfig.config.getPlanetConfig().getPlanetLatestPath();
        }

        // test if source file exist
        if (!pathToSource.toFile().exists()) {
            throw new IllegalArgumentException("Input file for creation tourist path "
                    + map.getPathSource() + " does not exist!");
        }

        // write to log and start stop watch
        TimeWatch time = new TimeWatch();
        Main.mySimpleLog.print("\nTourist: " + map.getName() + " ...");

        CmdLoMapsTools cmdTourist = new CmdLoMapsTools();
        cmdTourist.generateTourist(pathToSource, map.getPathTourist());

        // notify about result
        Main.mySimpleLog.print("\t\t\tdone " + time.getElapsedTimeSec() + " sec");
        time.stopCount();
    }

    // ACTION TRANSFORM DATA

    private void actionTransformData(ItemMap map) {

        // transform data only maps that are used for generation
        if (!map.hasAction(Action.GENERATE_MAPSFORGE)) {
            return;
        }

        // check if output file with transformed data already exist
        if (map.getPathTranform().toFile().exists()) {
            Logger.i(TAG, "File with transformed data, already exist. Skip data transform action; path: "
                    + map.getPathTranform());
            return;
        }

        Logger.i(TAG, "Transform custom OSM data");
        new CmdTransformData(map).addDataTransform();
    }


    // ACTION CONTOUR

    private void actionContour(ItemMap map) {
        // check if we want to do this action
        if (!map.hasAction(Action.CONTOUR)) {
            return;
        }

        printLogHeader(Action.CONTOUR);
        // check if file exists
        if (map.getPathContour().toFile().exists()) {
            Logger.i(TAG, "File with contours " + map.getPathContour() + ", already exists");
            return;
        }

        // write to log and start stop watch
        TimeWatch time = new TimeWatch();
        Main.mySimpleLog.print("\nContour: " + map.getName() + " ...");
        Logger.i(TAG, "Creating contours: " + map.getPathContour());

        CmdContour cc = new CmdContour(map);
        cc.generate();

//        CmdSort cs = new CmdSort(map);
//        cs.createCmdSort();
//        cs.execute();
//        cs.rename();
        Main.mySimpleLog.print("\t\t\tdone " + time.getElapsedTimeSec() + " sec");

        // stop timeWatch
        time.stopCount();
    }

    // ACTION MERGE
    private void actionMergePlanet(ItemMap map) {

        Logger.i(TAG, "================ MERGE PLANET MAP ================");
        if (!AppConfig.config.getOverwrite() && map.getPathSource().toFile().exists()) {
            Logger.i(TAG, "Merged planet file: " + map.getPathSource() + " already exist");
            return;
        }

        List<Path> pathsToMerge = new ArrayList<>();

        // check if source planet file exists
        if (!AppConfig.config.getPlanetConfig().getPlanetLatestPath().toFile().exists()) {
            throw new IllegalArgumentException("Original planet files doesn't exist: " +
                    AppConfig.config.getPlanetConfig().getPlanetLatestPath());
        }

        pathsToMerge.add(AppConfig.config.getPlanetConfig().getPlanetLatestPath());

        if (AppConfig.config.getActions().contains(Action.TOURIST) && map.hasAction(Action.TOURIST)) {
            if (!map.getPathTourist().toFile().exists()) {
                throw new IllegalArgumentException("File Tourist routes: " + map.getPathTourist() + " does not exist.");
            }

            if (!map.getPathSource().toFile().exists() || !containsTourist(map.getPathSource())) {
                pathsToMerge.add(map.getPathTourist());
            } else {
                Logger.i(TAG, "Source file already contains tourist paths: " + map.getPathSource());
            }
        }
        if (AppConfig.config.getActions().contains(Action.CONTOUR) && map.hasAction(Action.CONTOUR)) {
            if (!map.getPathContour().toFile().exists()) {
                throw new IllegalArgumentException("File Contour: " + map.getPathContour() + " does not exist.");
            }

            if (!map.getPathSource().toFile().exists() || !containsContours(map.getPathSource())) {
                pathsToMerge.add(map.getPathContour());
            } else {
                Logger.i(TAG, "Source file already contains contours: " + map.getPathSource());
            }
        }

        // merge overview map (with data for low zooms)
        if (map.hasAction(Action.OVERVIEW_MAP)) {

            OverviewMapConfig cfg = AppConfig.config.getOverviewMapConfig();
            if (!cfg.getOutputPbf().toAbsolutePath().toFile().exists()) {
                throw new IllegalStateException("Overview PBF not found: " + cfg.getOutputPbf() + ". " +
                        "Run the overview map build step first.");
            }
            pathsToMerge.add(cfg.getOutputPbf().toAbsolutePath());
        }

        // merge
        Utils.createParentDirs(map.getPathSource());
        CmdOsmium cmdOsmium = new CmdOsmium();
        cmdOsmium.merge(pathsToMerge, map.getPathSource());
    }

    private void actionMerge(ItemMap map) {

        if (!map.hasAction(Action.GENERATE_MAPSFORGE)) {
            return;
        }

        // test if merged file already exist
        if (!AppConfig.config.getOverwrite()) {
            if (map.getPathMerge().toFile().exists() || (map.hasAction(Action.GENERATE_MAPSFORGE) && map.getPathGenerate().toFile().exists())) {
                // nothing to do file already exist
                Logger.i(TAG, "Merged file: " + map.getPathMerge() + " already exist. Or generated file exist: " + map.getPathGenerate());
                map.setMerged(true);
                return;
            }
        }

        List<Path> pathsToMerge = new ArrayList<>();

        // test if extracted map from planet exist
        if (!map.getPathSource().toFile().exists()) {
            throw new IllegalArgumentException("Extracted base map for merging: " +
                    map.getPathSource() + " does not exist.");
        }
        pathsToMerge.add(map.getPathSource());

        if (map.hasSea()) {
            if (!map.getPathCoastline().toFile().exists()) {
                throw new IllegalArgumentException("Coastlines path: " + map.getPathCoastline() + " does not exist.");
            }
            pathsToMerge.add(map.getPathCoastline());
        }

        if (AppConfig.config.getActions().contains(Action.TRANSFORM)) {
            if (!map.getPathTranform().toFile().exists()) {
                throw new IllegalArgumentException("Transformed data path: " + map.getPathTranform() + " does not exist.");
            }
            pathsToMerge.add(map.getPathTranform());
        }

        if (pathsToMerge.size() == 1) {
            Logger.i(TAG, "Only one file to merge: " + map.getPathSource() + ". Nothing to do.");
            return;
        }

        TimeWatch time = new TimeWatch();
        // prepare cmd line and string for log
        Main.mySimpleLog.print("\nMarging: " + map.getName() + " ...");
        CmdOsmium cmdOsmium = new CmdOsmium();
        cmdOsmium.merge(pathsToMerge, map.getPathMerge());
        Main.mySimpleLog.print("\t\t\tdone " + time.getElapsedTimeSec() + " sec");
        time.stopCount();

        //set information about margin
        map.setMerged(true);
    }

    private void actionGenerateMbtiles(ItemMap map) {

        if (!map.hasAction(Action.GENERATE_MBTILES)) {
            return;
        }

        // check if file exits and we should overwrite it
        if (!AppConfig.config.getOverwrite() && map.getPathMbtiles().toFile().exists()) {
            Logger.i(TAG, "File with mbtiles " + map.getPathMbtiles() + " already exist - skipped.");
            return;
        }

        ItemMap planetMbtilesMap = mMapSource.getMapById(AppConfig.config.getPlanetConfig().getPlanetExtendedId());

        // check if source planet file exist
        if (!planetMbtilesMap.getPathMbtiles().toFile().exists()) {

            TimeWatch time = new TimeWatch();
            Logger.i(TAG, "Generating Planet MbTiles: " + planetMbtilesMap.getPathMbtiles());
            Main.mySimpleLog.print("\nGenerate Planet: " + planetMbtilesMap.getName() + " ...");

            CmdPlanetiler cmdPlanetiler = new CmdPlanetiler();
            cmdPlanetiler.generateLoMapsOpenMapTiles(
                    planetMbtilesMap.getPathSource(),
                    planetMbtilesMap.getPathMbtiles(),
                    planetMbtilesMap.getPathPolygon());

            // clean tmp
            Main.mySimpleLog.print("\t\t\tdone " + time.getElapsedTimeSec() + " sec");

            return;
        }

        // Generate particular MBTILES
        TimeWatch time = new TimeWatch();
        Logger.i(TAG, "Generate mbtiles: " + map.getName());
        Main.mySimpleLog.print("\nGenerate mbtiles: " + map.getName() + " ...");

        MbtilesCreator mbtilesCreator = new MbtilesCreator();
        mbtilesCreator.createMbtiles(
                planetMbtilesMap.getPathMbtiles(),
                map.getPathMbtiles(),
                map.getPathPolygon(),
                map.getName(),
                1, 14);

        // notify about result
        Main.mySimpleLog.print("\t\t\tdone " + time.getElapsedTimeSec() + " sec");
        time.stopCount();
    }

    // ACTION GENERATE

    private void actionGenerate(ItemMap map) {

        if (map.hasAction(Action.GENERATE_MAPSFORGE)) {
            if (AppConfig.config.getOverwrite() || !map.getPathGenerate().toFile().exists()) {
                CmdGenerate cg = new CmdGenerate(map);

                // write to log and start stop watch
                TimeWatch time = new TimeWatch();
                Logger.i(TAG, "Generating map: " + map.getPathGenerate());
                Main.mySimpleLog.print("\nGenerate: " + map.getName() + " ...");
                cg.execute(2, true);

                // clean tmp
                Logger.i(TAG, "Deleting files in tmp dir: " + AppConfig.config.getTemporaryDir());
                Utils.deleteFilesInDir(AppConfig.config.getTemporaryDir());

                Main.mySimpleLog.print("\t\t\tdone " + time.getElapsedTimeSec() + " sec");
            } else {
                Logger.i(TAG, "Generated map " + map.getPathGenerate() + " already exists. Nothing to do.");
            }
        }
    }


    private void actionGenerateMbtilesOnline(ItemMap map) {
        if (map.hasAction(Action.GENERATE_MBTILES_ONLINE) && AppConfig.config.getActions().contains(Action.GENERATE_MBTILES_ONLINE)) {
            Logger.i(TAG, "================ GENERATE MBTILES ONLINE " + map.getName() + " ================");
            if (AppConfig.config.getOverwrite() || !map.getPathGenMlOutdoor().toFile().exists()) {

                // write to log and start stop watch
                TimeWatch time = new TimeWatch();
                Logger.i(TAG, "Generating MapLibre outdoor map: " + map.getPathGenMlOutdoor());
                Main.mySimpleLog.print("\nGenerate: " + map.getName() + " ...");

                CmdPlanetiler cmdPlanetiler = new CmdPlanetiler();
                cmdPlanetiler.generateOutdoorTiles(map.getPathSource(), map.getPathGenMlOutdoor(), map.getPathPolygon());

                // clean tmp
                Main.mySimpleLog.print("\t\t\tdone " + time.getElapsedTimeSec() + " sec");
            } else {
                Logger.i(TAG, "Generated MapLibre Outdoor map " + map.getPathGenMlOutdoor() + " already exists. Nothing to do.");
            }
        }

    }

    // ACTION CONVERT TO PMTILES
    private void actionGeneratePmtiles(ItemMap mapPlanet) {
        if (mapPlanet.hasAction(Action.GENERATE_PMTILES_ONLINE) && AppConfig.config.getActions().contains(Action.GENERATE_PMTILES_ONLINE)) {
            Logger.i(TAG, "================ GENERATE PMTILES ONLINE " + mapPlanet.getName() + " ================");
            if (AppConfig.config.getOverwrite() || !mapPlanet.getPathGenPmtilesOnline().toFile().exists()) {

                // In first step check or generate planet mbtiles
                actionGenerateMbtiles(mapPlanet);

                // write to log and start stop watch
                TimeWatch time = new TimeWatch();
                Logger.i(TAG, "Convert MBtiles to PMTiles: " + mapPlanet.getPathGenPmtilesOnline());
                Main.mySimpleLog.print("\nGenerate PMTiles: " + mapPlanet.getName() + " ...");

                CmdPmtiles cmdPmtiles = new CmdPmtiles();
                cmdPmtiles.convertToPmtiles(mapPlanet.getPathMbtiles(), mapPlanet.getPathGenPmtilesOnline());

                // validate generated PMTiles
                Logger.i(TAG, "Verift converted PMTiles: " + mapPlanet.getPathGenPmtilesOnline());
                cmdPmtiles.verifyPmtiles(mapPlanet.getPathGenPmtilesOnline());


                // clean tmp
                Main.mySimpleLog.print("\t\t\tdone " + time.getElapsedTimeSec() + " sec");
            } else {
                Logger.i(TAG, "PMTiles map " + mapPlanet.getPathGenPmtilesOnline() + " already exists. Nothing to do.");
            }
        }
    }

    private void actionUploadOnlineToS3(ItemMap itemMap) {
        if (itemMap.hasAction(Action.GENERATE_PMTILES_ONLINE) && AppConfig.config.getActions().contains(Action.GENERATE_PMTILES_ONLINE)) {
            Logger.i(TAG, "================ UPLOAD PMTILES ONLINE TO S3 " + itemMap.getName() + " ================");
            if (!itemMap.getPathGenPmtilesOnline().toFile().exists()) {
                throw new IllegalArgumentException("File with generated PMTiles map: " + itemMap.getPathGenPmtilesOnline() + " does not exist.");
            }
            Logger.i(TAG, "Prepare for upload to S3, PMTiles file: " + itemMap.getPathGenPmtilesOnline());
            try (S3Client s3Client = S3Client.Companion.fromAppConfig()) {

                OnlineLoMapsConfig cfg = AppConfig.config.getOnlineLoMapsConfig();
                boolean isDev = Utils.isLocalDEV();
                String latestPrefix = isDev ? cfg.getS3pmtilesPathDev() : cfg.getS3pmtilesPath();
                String versionsPrefix = isDev ? cfg.getS3pmtilesVersionsPathDev() : cfg.getS3pmtilesVersionsPath();

                OnlinePlanetVersionsManager versionsManager = new OnlinePlanetVersionsManager(
                        s3Client, versionsPrefix, latestPrefix, cfg.getS3pmtilesVersionsKeep()
                );
                versionsManager.publish(
                        itemMap.getPathGenPmtilesOnline().toFile(),
                        AppConfig.config.getVersion(),
                        itemMap.getPathGenPmtilesOnline().getFileName().toString()
                );
            }

        }
    }

    private void actionUploadPlanetToMapTiler(ItemMap itemMap) {

        if (AppConfig.config.getActions().contains(Action.UPLOAD_MAPTILER)) {
            Logger.i(TAG, "==== UPLOAD TO MAPTILER ====");
            if (!itemMap.getPathGenMlOutdoor().toFile().exists()) {
                throw new IllegalArgumentException("File with generated MapLibre outdoor map: " + itemMap.getPathGenMlOutdoor() + " does not exist.");
            }
            Logger.i(TAG, "Prepare for upload to MapTiler, map file: " + itemMap.getPathGenMlOutdoor());
            MapTilerUploader uploader = new MapTilerUploader();
            uploader.uploadAndInitializeMapTiles(itemMap.getPathGenMlOutdoor().toFile());
            Logger.i(TAG, "Tiles uploaded");
        }
    }

    // ACTION META TABLE

    private void actionInsertMetaData(ItemMap itemMap) throws Exception {

        if (!itemMap.hasAction(Action.GENERATE_MAPSFORGE)) {
            return;
        }

        // create area coverage (it's intersection of country border and data json)
        Geometry geom = WriterAddressDefinition.createDbGeom(
                itemMap.getPathJsonPolygon().toString(), itemMap.getPathCountryBoundaryGeoJson().toString());

        if (!geom.isValid()) {
            geom = GeomUtils.fixInvalidGeom(geom);
        }

        if (!geom.isValid() || geom.isEmpty() || geom.getArea() == 0) {
            Logger.i(TAG, GeomUtils.geomToGeoJson(geom));
            throw new IllegalArgumentException("Country map geom is not valid, map : " + itemMap.getName());
        }

        // insert metadata to db file for LoMaps Classic
        insertMetadata(itemMap, itemMap.getPathAddressPoiDb().toFile(), geom);

        // insert metadata to db file for LM4
        insertMetadata(itemMap, itemMap.getPathAddressDb().toFile(), geom);

    }

    private void insertMetadata(ItemMap itemMap, File dbFile, Geometry geom) throws Exception {

        if (dbFile == null || !dbFile.exists()) {
            Logger.w(TAG, "DB file for inserting metadata doesn't exist: " + dbFile);
        }

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy.MM.dd");
        Date dateVersion = sdf.parse(AppConfig.config.getVersion());

        DatabaseData dbData = new DatabaseData(dbFile);
        WKTWriter wktWriter = new WKTWriter();

        dbData.insertData(LoMapsDbConst.VAL_AREA, wktWriter.write(geom));
        dbData.insertData(LoMapsDbConst.VAL_COUNTRY, itemMap.getCountryName());
        dbData.insertData(LoMapsDbConst.VAL_DESCRIPTION, AppConfig.config.getMapsforgeConfig().getMapMetaDataDescription());
        dbData.insertData(LoMapsDbConst.VAL_LANGUAGES, itemMap.getPrefLang());
        dbData.insertData(LoMapsDbConst.VAL_OSM_DATE, String.valueOf(dateVersion.getTime()));
        dbData.insertData(LoMapsDbConst.VAL_REGION_ID, itemMap.getRegionId());
        dbData.insertData(LoMapsDbConst.VAL_VERSION, AppConfig.config.getVersion());
        dbData.insertData(LoMapsDbConst.VAL_DB_POI_VERSION, String.valueOf(AppConfig.config.getPoiAddressConfig().getDbPoiVersion()));
        dbData.insertData(LoMapsDbConst.VAL_DB_ADDRESS_VERSION, String.valueOf(AppConfig.config.getPoiAddressConfig().getDbAddressVersion()));

        dbData.destroy();
    }

    // ACTION UPLOAD

    private void actionUpload() {

        TimeWatch time = new TimeWatch();
        Logger.i(TAG, "Start action upload ");
        Main.mySimpleLog.print("Uplad data....");

        CmdUpload cmdUpload = new CmdUpload();
        cmdUpload.upload(1);

        Main.mySimpleLog.print("\t\t\tdone " + time.getElapsedTimeSec() + " sec");
    }

    // Other TOOLS
    public boolean containsContours(Path sourcePath) {
        CmdOsmium cmdOsmium = new CmdOsmium();
        String meterContourId = "w" + AppConfig.config.getContourConfig().getWayIdMeter();
        String feetContourId = "w" + AppConfig.config.getContourConfig().getWayIdFeet();
        return cmdOsmium.containsId(sourcePath, meterContourId) || cmdOsmium.containsId(sourcePath, feetContourId);
    }

    public boolean containsTourist(Path sourcePath) {
        CmdOsmium cmdOsmium = new CmdOsmium();
        String touristId = "w" + AppConfig.config.getTouristConfig().getWayId();
        return cmdOsmium.containsId(sourcePath, touristId);
    }
}
