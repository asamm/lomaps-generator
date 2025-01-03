package com.asamm.osmTools.generator;

import com.asamm.locus.MapTilerUploader;
import com.asamm.locus.client.model.Tileset;
import com.asamm.locus.client.model.TilesetPage;
import com.asamm.locus.features.loMaps.LoMapsDbConst;
import com.asamm.osmTools.Main;
import com.asamm.osmTools.Parameters;
import com.asamm.osmTools.cmdCommands.*;
import com.asamm.osmTools.config.Action;
import com.asamm.osmTools.config.AppConfig;
import com.asamm.osmTools.generatorDb.input.definition.WriterAddressDefinition;
import com.asamm.osmTools.generatorDb.input.definition.WriterPoiDefinition;
import com.asamm.osmTools.generatorDb.plugin.ConfigurationCountry;
import com.asamm.osmTools.generatorDb.utils.GeomUtils;
import com.asamm.osmTools.mapConfig.ItemMap;
import com.asamm.osmTools.mapConfig.ItemMapPack;
import com.asamm.osmTools.mapConfig.MapSource;
import com.asamm.osmTools.sea.LandArea;
import com.asamm.osmTools.server.UploadDefinitionCreator;
import com.asamm.osmTools.utils.*;
import com.asamm.osmTools.utils.db.DatabaseData;
import com.asamm.osmTools.utils.io.ZipUtils;
import net.minidev.json.JSONArray;
import net.minidev.json.parser.JSONParser;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.WKTWriter;
import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Created by voldapet on 2016-09-16 .
 */
public class GenLoMaps extends AGenerator {

    private static final String TAG = GenLoMaps.class.getSimpleName();

    // parsed configuration of maps
    private MapSource mMapSource;


    public GenLoMaps() throws IOException, XmlPullParserException {

        // parse definition xml
        mMapSource = parseConfigXml(AppConfig.config.getMapConfigXml().toFile());
    }

    public void process() throws Exception {

        Set<Action> actionList = AppConfig.config.getActions();

        // are there any data in mappack?
        if (!mMapSource.hasData()) {
            Logger.w(TAG, "No data was obtain from config xml");
            return;
        }

        // run OSM update of planet file (update starts only if needed). Updater downloads planet file if it doesn't exist
        PlanetUpdater planetUpdater = new PlanetUpdater();
        // TODO anable for produciton
        //planetUpdater.update();

        // process action on planet level
        processPlanet(actionList, mMapSource);

        // for every action value in array do
        for (Action action : actionList) {

            // skip Contour and Tourist action, because they are processed on planet level
            if (action == Action.CONTOUR || action == Action.TOURIST) {
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
                UploadDefinitionCreator.getInstace().writeToJsonDefFile();
            }

            // perform remaining actions
            if (action == Action.UPLOAD) {

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

    private void processPlanet(Set<Action> actionList, MapSource mMapSource) {
        // find ItemMap with if "planet" and process it
        ItemMap map = mMapSource.getMapById(AppConfig.config.getPlanetConfig().getPlanetExtendedId());
        if (map != null) {
            for (Action action : actionList) {
                switch (action) {
                    case TOURIST:
                        actionTourist(map);
                        break;
                    case CONTOUR:
                        actionContour(map);
                        break;
                }
            }
            actionMergePlanet(map);

            // generate maplibre
            actionGenerateMapLibre(map);

            // upload to maptiler
            actionUploadPlanetToMapTiler(map);
        }
    }


    public void actionAllInOne(ItemMapPack mp, Action action)
            throws Exception {

        // iterate over all maps and perform actions
        for (int i = 0, m = mp.getMapsCount(); i < m; i++) {
            ItemMap map = mp.getMap(i);

            switch (action) {
                //download, tourist and contour are processed reparetly for whole planet
                case GRAPH_HOPPER:
                    actionGraphHopper(map);
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
                case MERGE:
                    actionMerge(map);
                    break;
                case GENERATE_MAPSFORGE:
                    actionGenerate(map);
                    actionInsertMetaData(map);
                    break;
                case COMPRESS:
                    actionCompress(map);
                    break;
                case CREATE_JSON:
                    actionCreateJSON(map);
                    break;
            }
        }

        // iterate over all MapPacks and call same function on them
        for (int i = 0, m = mp.getMapPackCount(); i < m; i++) {
            actionAllInOne(mp.getMapPack(i), action);
        }
    }


    private class PackForExtract {

        String sourceId;
        List<ItemMap> maps;
    }

    // action UPDATE PLANET

    private void actionUpdatePlanet() {

    }

    // ACTION DOWNLOAD
    @Deprecated
    private void actionDownload(ItemMap map) {
        // check if we want to do this action
        if (!map.hasAction(Action.DOWNLOAD)) {
            return;
        }

        // get download url
        String downloadUrl = map.getUrl();

        // check if file exists
        if (map.getPathSource().toFile().exists()) {
//            Logger.i(TAG, "File " + map.getPathSource() + " already exists. No download needed");
            return;
        }

        printLogHeader(Action.DOWNLOAD);

        // try to download
        if (UtilsHttp.downloadFile(map.getPathSource(), downloadUrl)) {
            Logger.i(TAG, "File " + map.getPathSource() + " successfully downloaded.");
        } else {
            throw new IllegalArgumentException("File " + downloadUrl + " was not downloaded.");
        }
    }

    // ACTION GRAPHHOPPER

    private void actionGraphHopper(ItemMap map) throws IOException, InterruptedException {
        // check if we want to generate GraphHopper data
        if (!map.hasAction(Action.GRAPH_HOPPER)) {
            return;
        }

        // check if file exits and we should overwrite it
        if (!Parameters.isRewriteFiles() && map.getPathGraphHopper().toFile().exists()) {
//            Logger.i(TAG, "File with GraphHopper '" + map.getPathGraphHopper()
//                    + "' already exist - skipped." );
            return;
        }

        // clear working directory
        File fileSource = map.getPathSource().toFile();
        File ghDir = new File(fileSource.getParentFile(),
                FilenameUtils.getBaseName(map.getPathSource().toString()) + "-gh");
        FileUtils.deleteDirectory(ghDir);

        // execute graphHopper
        CmdGraphHopper cmd = new CmdGraphHopper(map);
        cmd.execute();

        // check result and move it to correct directory
        Collection<File> files = FileUtils.listFiles(ghDir, null, false);
        if (files.size() == 0) {
            throw new UnknownError("Generating of GraphHopper wasn't successful");
        }

        // move (pack) files
        ZipUtils.pack(ghDir, map.getPathGraphHopper().toFile(), true);
        FileUtils.deleteDirectory(ghDir);
    }

    // ACTION ADDRESS/POI DATABASE

    private void actionAddressPoiDatabase(ItemMap map) throws Exception {

        // check if map has defined generation of addresses
        if (!map.hasAction(Action.ADDRESS_POI_DB)) {
            return;
        }

        // check if DB file exits and we should overwrite it
        if (!Parameters.isRewriteFiles() && map.getPathAddressPoiDb().toFile().exists()) {
            Logger.d(TAG, "File with Address/POI database '" + map.getPathAddressPoiDb() +
                    "' already exist - skipped.");
            return;
        }

        // load definitions

        File defFile = new File(Parameters.getConfigApDbPath());
        WriterPoiDefinition definition = new WriterPoiDefinition(defFile);

        // firstly simplify source file
        CmdLoMapsDbPlugin cmdPoiFilter = new CmdLoMapsDbPlugin(map);
        cmdPoiFilter.addTaskSimplifyForPoi(definition);
        Logger.i(TAG, "Filter data for POI DB, command: " + cmdPoiFilter.getCmdLine());
        cmdPoiFilter.execute(0, true);

        // now execute db poi generating
        CmdLoMapsDbPlugin cmdPoi = new CmdLoMapsDbPlugin(map);
        cmdPoi.addGeneratorPoiDb();
        Logger.i(TAG, "Generate POI DB, command: " + cmdPoi.getCmdLine());
        cmdPoi.execute(0, true);

        //Address generation
        CmdLoMapsDbPlugin cmdAddressFilter = new CmdLoMapsDbPlugin(map);
        cmdAddressFilter.addTaskSimplifyForAddress();
        Logger.i(TAG, "Filter data for Address DB, command: " + cmdAddressFilter.getCmdLine());
        cmdAddressFilter.execute(0, false);

        CmdLoMapsDbPlugin cmdAddres = new CmdLoMapsDbPlugin(map);
        cmdAddres.addGeneratorAddress();
        Logger.i(TAG, "Generate Adrress DB, command: " + cmdAddres.getCmdLine());
        cmdAddres.execute(0, false);

        // delete tmp file
        cmdAddres.deleteTmpFile();

    }

    // ACTION COASTLINE

    private void actionCoastline(ItemMap map)
            throws IOException, InterruptedException {

        if (!map.hasAction(Action.GENERATE_MAPSFORGE)) {
            return;
        }

        // check if file exits and we should overwrite it
        if (!Parameters.isRewriteFiles() && map.getPathCoastline().toFile().exists()) {
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
            Logger.i(TAG, "File with tourist path ${map.getPathTourist()} already exist - skipped.");
            return;
        }

        // for planet it's needed to customize "source" path and use the orig planet file as source
        Path pathToSource = map.getPathSource();
        if (map.getId().equals(AppConfig.config.getPlanetConfig().getPlanetExtendedId())) {
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

    private void actionTransformData(ItemMap map) throws IOException, InterruptedException {

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

        CmdTransformData cdt = new CmdTransformData(map);
        cdt.addDataTransform();
        Logger.i(TAG, "Transform custom OSM data, command: " + cdt.getCmdLine());
        cdt.execute();
    }


    // ACTION CONTOUR

    private void actionContour(ItemMap map) {
        // check if we want to do this action
        if (!map.hasAction(Action.CONTOUR)) {
            return;
        }

        printLogHeader(Action.CONTOUR);
        // check if file exists
        if (map.getPathContour().toFile().exists() && !AppConfig.config.getOverwrite()) {
            Logger.i(TAG, "File with contours " + map.getPathContour() + ", already exists");
            return;
        }

        // write to log and start stop watch
        TimeWatch time = new TimeWatch();
        Main.mySimpleLog.print("\nContour: " + map.getName() + " ...");
        Logger.i(TAG, "Creating contours: " + map.getPathContour());

        // create commands for generation contours
        CmdContour cc = new CmdContour(map);
        cc.generate();
        Logger.i(TAG, "Command: " + cc.getCmdLine());

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

            if ( !map.getPathSource().toFile().exists() || !containsTourist(map.getPathSource())) {
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

        // merge
        Utils.createParentDirs(map.getPathSource());
        CmdOsmium cmdOsmium = new CmdOsmium();
        cmdOsmium.merge(pathsToMerge, map.getPathSource());
    }

    private void actionMerge(ItemMap map) throws IOException, InterruptedException {

        if (!map.hasAction(Action.GENERATE_MAPSFORGE)) {
            return;
        }

        boolean isCoastline = map.hasSea();
        if (!map.hasAction(Action.CONTOUR) &&
                !map.hasAction(Action.TOURIST) &&
                !isCoastline) {
            //Nothing for merginf map will be skipped
            //System.out.println("Nic pro "+map.name);
            return;
        }

        // test if merged file already exist
        if (!Parameters.isRewriteFiles() && map.getPathMerge().toFile().exists()) {
            // nothing to do file already exist
            Logger.i(TAG, "Merged file: " + map.getPathMerge() + " already exist");
            //set information that map is merged
            map.isMerged = true;
            return;
        }

        if (isCoastline && !map.getPathCoastline().toFile().exists()) {
            throw new IllegalArgumentException("Coastlines path: " + map.getPathCoastline() + " does not exist.");
        }

        // test if extracted map exist
        if (!map.getPathSource().toFile().exists()) {
            throw new IllegalArgumentException("Extracted base map for merging: " +
                    map.getPathSource() + " does not exist.");
        }

        TimeWatch time = new TimeWatch();
        // prepare cmd line and string for log
        String logStr = "Merging maps: " + map.getPathSource() + " and ";
        CmdMerge cm = new CmdMerge(map);
        cm.createCmd();

        Main.mySimpleLog.print("\nMarging: " + map.getName() + " ...");
        Logger.i(TAG, "Merge map parts, command: " + cm.getCmdLine());
        cm.execute();
        Main.mySimpleLog.print("\t\t\tdone " + time.getElapsedTimeSec() + " sec");
        time.stopCount();

        //set information about margin
        map.isMerged = true;
    }

    // ACTION GENERATE

    private void actionGenerate(ItemMap map) throws IOException, InterruptedException {

        if (map.hasAction(Action.GENERATE_MAPSFORGE)) {
            if (Parameters.isRewriteFiles() || !map.getPathGenerate().toFile().exists()) {
                CmdGenerate cg = new CmdGenerate(map);
                cg.createCmd();

                // write to log and start stop watch
                TimeWatch time = new TimeWatch();
                Logger.i(TAG, "Generating map: " + map.getPathGenerate());
                Logger.i(TAG, "Generating map cmd: " + cg.getCmdLine());

                Main.mySimpleLog.print("\nGenerate: " + map.getName() + " ...");
                cg.execute(2, true);

                // clean tmp
                Logger.i(TAG, "Deleting files in tmp dir: " + Consts.DIR_TMP);
                Utils.deleteFilesInDir(Consts.DIR_TMP);

                Main.mySimpleLog.print("\t\t\tdone " + time.getElapsedTimeSec() + " sec");
            } else {
                Logger.i(TAG, "Generated map " + map.getPathGenerate() + " already exists. Nothing to do.");
            }
        }
    }


    private void actionGenerateMapLibre(ItemMap map) {
        if (map.hasAction(Action.GENERATE_MAPLIBRE)) {
            if (AppConfig.config.getOverwrite() || !map.getPathGenMlOutdoor().toFile().exists()) {

                // write to log and start stop watch
                TimeWatch time = new TimeWatch();
                Logger.i(TAG, "Generating MapLibre outdoor map: " + map.getPathGenMlOutdoor());
                Main.mySimpleLog.print("\nGenerate: " + map.getName() + " ...");

                CmdPlanetiler cmdPlanetiler = new CmdPlanetiler();
                cmdPlanetiler.generateOutdoorTiles(map.getPathSource(), map.getPathGenMlOutdoor(), map.getPathPolygon());

                //cmdPlanetiler.generateOpenMapTiles(map.getPathSource(), map.getPathGenMlOutdoor(), map.getPathPolygon());

                // clean tmp
                Main.mySimpleLog.print("\t\t\tdone " + time.getElapsedTimeSec() + " sec");
            } else {
                Logger.i(TAG, "Generated MapLibre Outdoor map " + map.getPathGenMlOutdoor() + " already exists. Nothing to do.");
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

        if (!itemMap.hasAction(Action.ADDRESS_POI_DB) || !itemMap.hasAction(Action.GENERATE_MAPSFORGE)) {
            return;
        }

        File dbAddressPoiFile = itemMap.getPathAddressPoiDb().toFile();
        if (dbAddressPoiFile.getParentFile() != null) {
            dbAddressPoiFile.getParentFile().mkdirs();
        }

        DatabaseData dbData = new DatabaseData(dbAddressPoiFile);

        // read description from definition json
        JSONParser parser = new JSONParser(JSONParser.DEFAULT_PERMISSIVE_MODE);
        JSONArray descriptionJson = (JSONArray) parser.parse(
                new FileReader(Parameters.getMapDescriptionDefinitionJsonPath()));

        // parse version into java date
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy.MM.dd");
        Date dateVersion = sdf.parse(AppConfig.config.getVersion());

        // insert version of map
        // create area coverage (it's intersection of country border and data json)
        Geometry geom = WriterAddressDefinition.createDbGeom(
                itemMap.getPathJsonPolygon(), itemMap.getPathCountryBoundaryGeoJson());

        if (!geom.isValid()) {
            geom = GeomUtils.fixInvalidGeom(geom);
        }

        if (!geom.isValid() || geom.isEmpty() || geom.getArea() == 0) {
            Logger.i(TAG, GeomUtils.geomToGeoJson(geom));
            throw new IllegalArgumentException("Country map geom is not valid, map : " + itemMap.getName());
        }

        WKTWriter wktWriter = new WKTWriter();

        dbData.insertData(LoMapsDbConst.VAL_AREA, wktWriter.write(geom));
        dbData.insertData(LoMapsDbConst.VAL_COUNTRY, itemMap.getCountryName());
        dbData.insertData(LoMapsDbConst.VAL_DESCRIPTION, descriptionJson.toJSONString());
        dbData.insertData(LoMapsDbConst.VAL_LANGUAGES, itemMap.getPrefLang());
        dbData.insertData(LoMapsDbConst.VAL_OSM_DATE, String.valueOf(dateVersion.getTime()));
        dbData.insertData(LoMapsDbConst.VAL_REGION_ID, itemMap.getRegionId());
        dbData.insertData(LoMapsDbConst.VAL_VERSION, AppConfig.config.getVersion());
        dbData.insertData(LoMapsDbConst.VAL_DB_POI_VERSION, String.valueOf(Parameters.getDbDataPoiVersion()));
        dbData.insertData(LoMapsDbConst.VAL_DB_ADDRESS_VERSION, String.valueOf(Parameters.getDbDataAddressVersion()));

        dbData.destroy();

    }

    // ACTION COMPRESS

    private void actionCompress(ItemMap map) throws IOException {

        if (!map.hasAction(Action.GENERATE_MAPSFORGE) && !map.hasAction(Action.ADDRESS_POI_DB)) {
            // map hasn't any result file for compress
            return;
        }

        if (!Parameters.isRewriteFiles() && map.getPathResult().toFile().exists()) {
            Logger.d(TAG, "File with compressed result '" + map.getPathResult() +
                    "' already exist - skipped.");
            return;
        }

        Logger.i(TAG, "Compressing: " + map.getPathResult());
        TimeWatch time = new TimeWatch();
        Main.mySimpleLog.print("\nCompress: " + map.getName() + " ...");

        // change lastChange attribute of generated file
        // this workaround how to set date of map file in Locus
        List<String> filesToCompress = new ArrayList<>();
        if (map.hasAction(Action.GENERATE_MAPSFORGE)) {
            File mapFile = map.getPathGenerate().toFile();
            if (!mapFile.exists()) {
                throw new IllegalArgumentException("Map file for compression: " + map.getPathGenerate() + " does not exist.");
            }
            // rewrite bytes in header to set new creation date
            RandomAccessFile raf = new RandomAccessFile(mapFile, "rw");
            raf.seek(36);
            raf.writeLong(Parameters.getSourceDataLastModifyDate());
            raf.close();

            filesToCompress.add(map.getPathGenerate().toString());
        }

        if (map.hasAction(Action.ADDRESS_POI_DB)) {

            File poiDbFile = map.getPathAddressPoiDb().toFile();
            if (!poiDbFile.exists()) {
                throw new IllegalArgumentException("POI DB file for compression: " + map.getPathAddressPoiDb() + " does not exist.");
            }
            filesToCompress.add(poiDbFile.getAbsolutePath());
        }

        // compress file
        Utils.compressFiles(filesToCompress, map.getPathResult().toString());

        Main.mySimpleLog.print("\t\t\tdone " + time.getElapsedTimeSec() + " sec");
    }

    // ACTION UPLOAD

    private void actionUpload() throws IOException, InterruptedException {

        TimeWatch time = new TimeWatch();
        Logger.i(TAG, "Start action upload ");
        Main.mySimpleLog.print("Uplad data....");

        CmdUpload cmdUpload = new CmdUpload();
        cmdUpload.createCmd();
        cmdUpload.execute(1);

        Main.mySimpleLog.print("\t\t\tdone " + time.getElapsedTimeSec() + " sec");
    }

    // ACTION CREATE JSON UPLOAD DEFINITION

    public void actionCreateJSON(ItemMap map) throws IOException {
        UploadDefinitionCreator dc = UploadDefinitionCreator.getInstace();

        if (map.hasAction(Action.GENERATE_MAPSFORGE)) {
            dc.addMap(map);
        }

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
