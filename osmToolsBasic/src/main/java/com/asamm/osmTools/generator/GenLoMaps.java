package com.asamm.osmTools.generator;

import com.asamm.locus.features.loMaps.LoMapsDbConst;
import com.asamm.osmTools.Main;
import com.asamm.osmTools.Parameters;
import com.asamm.osmTools.cmdCommands.*;
import com.asamm.osmTools.generatorDb.input.definition.WriterAddressDefinition;
import com.asamm.osmTools.generatorDb.input.definition.WriterPoiDefinition;
import com.asamm.osmTools.generatorDb.plugin.ConfigurationCountry;
import com.asamm.osmTools.generatorDb.utils.GeomUtils;
import com.asamm.osmTools.mapConfig.ItemMap;
import com.asamm.osmTools.mapConfig.ItemMapPack;
import com.asamm.osmTools.mapConfig.MapSource;
import com.asamm.osmTools.sea.LandArea;
import com.asamm.osmTools.server.UploadDefinitionCreator;
import com.asamm.osmTools.tourist.Tourist;
import com.asamm.osmTools.utils.*;
import com.asamm.osmTools.utils.db.DatabaseData;
import com.asamm.osmTools.utils.io.ZipUtils;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.WKTWriter;
import net.minidev.json.JSONArray;
import net.minidev.json.parser.JSONParser;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.RandomAccessFile;
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
        mMapSource = parseConfigXml(new File(Parameters.getConfigPath()));
    }

    public void process() throws Exception {

        List<Parameters.Action> actionList = Parameters.getActions();

        // are there any data in mappack?
        if (!mMapSource.hasData()) {
            Logger.w(TAG, "No data was obtain from config xml");
            return;
        }

        // for every action value in array do
        for (int i = 0, m = actionList.size(); i < m; i++) {
            Parameters.Action action = actionList.get(i);

            // print action header to log
            printLogHeader(action);

            // load mappack and do actions for mappack items
            Iterator<ItemMapPack> packs = mMapSource.getMapPacksIterator();
            while (packs.hasNext()) {
                ItemMapPack mp = packs.next();

                // handle extract first, because we need to handle whole pack at once
                if (action == Parameters.Action.EXTRACT) {
                    actionExtract(mp, mMapSource);
                    continue;
                }

                if (action == Parameters.Action.ADDRESS_POI_DB) {
                    actionCountryBorder(mp, mMapSource, ConfigurationCountry.StorageType.GEOJSON);
                }

                actionAllInOne(mp, action);
            }

            // needs to write definition JSON to file (in case that map was generated)
            if (action == Parameters.Action.CREATE_JSON) {
                UploadDefinitionCreator.getInstace().writeToJsonDefFile();
            }

            // perform remaining actions
            if (action == Parameters.Action.UPLOAD) {

                actionUpload();
            }
        }
    }


    private void printLogHeader(Parameters.Action action) {
        String line = "================ " + action.getLabel().toUpperCase() + " ================\n";
        Logger.i(TAG, line);
        Main.mySimpleLog.print("\n" + line);
    }

    /**************************************************/
    /*                PERFORM ACTIONS                 */

    /**************************************************/

    public void actionAllInOne(ItemMapPack mp, Parameters.Action action)
            throws Exception {

        // iterate over all maps and perform actions
        for (int i = 0, m = mp.getMapsCount(); i < m; i++) {
            ItemMap map = mp.getMap(i);

            switch (action) {
                //download
                case DOWNLOAD:
                    actionDownload(map);
                    break;
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
                case TOURIST:
                    actionTourist(map);
                    break;
                case TRANFORM:
                    actionTransformData(map);
                case CONTOUR:
                    actionContour(map);
                    break;
                case MERGE:
                    actionMerge(map);
                    break;
                case GENERATE:
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


    // ACTION DOWNLOAD

    private void actionDownload(ItemMap map) {
        // check if we want to do this action
        if (!map.hasAction(Parameters.Action.DOWNLOAD)) {
            return;
        }

        // get download url
        String downloadUrl = map.getUrl();

        // check if file exists
        if (new File(map.getPathSource()).exists()) {
//            Logger.i(TAG, "File " + map.getPathSource() + " already exists. No download needed");
            return;
        }

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
        if (!map.hasAction(Parameters.Action.GRAPH_HOPPER)) {
            return;
        }

        // check if file exits and we should overwrite it
        if (!Parameters.isRewriteFiles() && new File(map.getPathGraphHopper()).exists()) {
//            Logger.i(TAG, "File with GraphHopper '" + map.getPathGraphHopper()
//                    + "' already exist - skipped." );
            return;
        }

        // clear working directory
        File fileSource = new File(map.getPathSource());
        File ghDir = new File(fileSource.getParentFile(),
                FilenameUtils.getBaseName(map.getPathSource()) + "-gh");
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
        ZipUtils.pack(ghDir, new File(map.getPathGraphHopper()), true);
        FileUtils.deleteDirectory(ghDir);
    }

    // ACTION ADDRESS/POI DATABASE

    private void actionAddressPoiDatabase(ItemMap map) throws Exception {

        // check if map has defined generation of addresses
        if (!map.hasAction(Parameters.Action.ADDRESS_POI_DB)) {
            return;
        }

        // check if DB file exits and we should overwrite it
        if (!Parameters.isRewriteFiles() && new File(map.getPathAddressPoiDb()).exists()) {
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

        if (!map.hasAction(Parameters.Action.GENERATE)) {
            return;
        }

        // check if file exits and we should overwrite it
        if (!Parameters.isRewriteFiles() && new File(map.getPathCoastline()).exists()) {
            Logger.i(TAG, "File with land area " + map.getPathCoastline()
                    + " already exist - skipped.");
            return;
        }

        // start Creation sea and nosea lands
        new LandArea(map).create();
    }

    // ACTION TOURIST

    private void actionTourist(ItemMap map) throws IOException, XmlPullParserException {
        // check if we want to do this action
        if (!map.hasAction(Parameters.Action.TOURIST)) {
            return;
        }

        // check if file exits and we should overwrite it
        if (!Parameters.isRewriteFiles() && new File(map.getPathTourist()).exists()) {
            Logger.i(TAG, "File with tourist path " + map.getPathTourist()
                    + " already exist - skipped.");
            return;
        }

        // test if source file exist
        if (!new File(map.getPathSource()).exists()) {
            throw new IllegalArgumentException("Input file for creation tourist path "
                    + map.getPathSource() + " does not exist!");
        }

        // start tourist
        Tourist tourist = new Tourist(map);

        // write to log and start stop watch
        TimeWatch time = new TimeWatch();
        Main.mySimpleLog.print("\nTourist: " + map.getName() + " ...");

        // start creating of tourist data
        tourist.create();

        // notify about result
        Main.mySimpleLog.print("\t\t\tdone " + time.getElapsedTimeSec() + " sec");
        time.stopCount();
    }

    // ACTION TRANSFORM DATA

    private void actionTransformData(ItemMap map) throws IOException, InterruptedException {

        // transform data only maps that are used for generation
        if (!map.hasAction(Parameters.Action.GENERATE)) {
            return;
        }

        // check if output file with transformed data already exist
        if (new File(map.getPathTranform()).exists()) {
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

    private void actionContour(ItemMap map) throws IOException, InterruptedException {
        // check if we want to do this action
        if (!map.hasAction(Parameters.Action.CONTOUR)) {
            return;
        }

        // check if file exists
        if (new File(map.getPathContour()).exists()) {
            Logger.i(TAG, "File with contours " + map.getPathContour() + ", already exists");
            return;
        }

        // write to log and start stop watch
        TimeWatch time = new TimeWatch();
        Main.mySimpleLog.print("\nContour: " + map.getName() + " ...");
        Logger.i(TAG, "Creating contours: " + map.getPathContour());

        // create commands for generation contours
        CmdContour cc = new CmdContour(map);
        cc.createCmd();
        Logger.i(TAG, "Command: " + cc.getCmdLine());
        String cmdRunLastLine = cc.execute();


        // test if exist noSRTM file, delete it before try to create new, because rename new contour
        File contourNoSrtm = new File(map.getPathContour() + "." + Parameters.contourNoSRTM);
        if (contourNoSrtm.exists()) {
            if (!contourNoSrtm.delete()) {
                throw new IllegalArgumentException("Can not delete file: " + contourNoSrtm.getAbsolutePath());
            }
        }

        // vracim si hodnotu posledni lajny a ptam se jestli tam je string ktery
        // vrati phyhgtmap kdyz jsme v oblasti bez SRTMdat JAk to udelat lepe???
        if (cmdRunLastLine.toLowerCase().contains("no files for this area")) {
            Utils.createEmptyFile(map.getPathContour() + "." + Parameters.contourNoSRTM);
            Main.mySimpleLog.print("\t\t\t no SRTM data");
            Logger.i(TAG, "No SRTM data for map: " + map.getPathContour());
            //STOP TIMEWATCH
            time = null;
        }
        // else map contour lines was creted rename it and sort
        else {
            cc.rename();

            CmdSort cs = new CmdSort(map);
            cs.createCmdSort();
            cs.execute();
            cs.rename();
            Main.mySimpleLog.print("\t\t\tdone " + time.getElapsedTimeSec() + " sec");

            // stop timeWatch
            time.stopCount();
        }
    }

    // ACTION MERGE

    private void actionMerge(ItemMap map) throws IOException, InterruptedException {

        if (!map.hasAction(Parameters.Action.GENERATE)) {
            return;
        }

        boolean isContour = (Parameters.isActionRequired(Parameters.Action.CONTOUR) &&
                map.hasAction(Parameters.Action.CONTOUR) && map.getContourSep().equals("no")); // map has set the contour will be separeted
        boolean isTourist = Parameters.isActionRequired(Parameters.Action.TOURIST) &&
                map.hasAction(Parameters.Action.TOURIST);
        boolean isCoastline = map.hasSea();
        Logger.i(TAG, "merge:" + isContour + ", " + isTourist + ", " + isCoastline);
        if (!map.hasAction(Parameters.Action.CONTOUR) &&
                !map.hasAction(Parameters.Action.TOURIST) &&
                !isCoastline) {
            //Nothing for merginf map will be skipped
            //System.out.println("Nic pro "+map.name);
            return;
        }

        // test if merged file already exist
        if (!Parameters.isRewriteFiles() && new File(map.getPathMerge()).exists()) {
            // nothing to do file already exist
            Logger.i(TAG, "Merged file: " + map.getPathMerge() + " already exist");
            //set information that map is merged
            map.isMerged = true;
            return;
        }

        // test if file with flag no srtm exist
        boolean existNoSrtm = new File(map.getPathContour() + "." + Parameters.contourNoSRTM).exists();
        if (isContour) {
            if (!new File(map.getPathContour()).exists() && !existNoSrtm) {
                //there no file
                throw new IllegalArgumentException("Contour lines: " + map.getPathContour() + " does not exist");
            } else if (existNoSrtm && !new File(map.getPathContour()).exists()) {
                //no SRTM exist -> contour will not be merged
                isContour = false;
            }
        }

        if (isTourist && !new File(map.getPathTourist()).exists()) {
            throw new IllegalArgumentException("Tourist path: " + map.getPathContour() + " does not exist.");
        }
        if (isCoastline && !new File(map.getPathCoastline()).exists()) {
            throw new IllegalArgumentException("Coastlines path: " + map.getPathCoastline() + " does not exist.");
        }

        // test if extracted map exist
        if (!new File(map.getPathSource()).exists()) {
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

        // generate contour lines
        if (map.hasAction(Parameters.Action.GENERATE) && map.getContourSep().equals("yes")) {
            if (Parameters.isRewriteFiles() || !new File(map.getPathGenerateContour()).exists()) {
                CmdGenerate cgc = new CmdGenerate((map));
                cgc.createCmdContour();

                // write to log and start stop watch
                TimeWatch time = new TimeWatch();
                Logger.i(TAG, "Generating separated contour map: " + map.getPathGenerateContour());

                Main.mySimpleLog.print("\nGenerate contours: " + map.getName() + " ...");
                cgc.execute();

                // clean tmp
                Logger.i(TAG, "Deleting files in tmp dir: " + Consts.DIR_TMP);
                Utils.deleteFilesInDir(Consts.DIR_TMP);

                Main.mySimpleLog.print("\t\t\tdone " + time.getElapsedTimeSec() + " sec");

            } else {
                Logger.i(TAG, "Generated contourlines " + map.getPathGenerateContour()
                        + " already exists. Nothing to do.");
            }
        }

        if (map.hasAction(Parameters.Action.GENERATE)) {
            if (Parameters.isRewriteFiles() || !new File(map.getPathGenerate()).exists()) {
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

    // ACTION META TABLE

    private void actionInsertMetaData(ItemMap itemMap) throws Exception {

        if (!itemMap.hasAction(Parameters.Action.ADDRESS_POI_DB) || !itemMap.hasAction(Parameters.Action.GENERATE)) {
            return;
        }

        File dbAddressPoiFile = new File(itemMap.getPathAddressPoiDb());
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
        Date dateVersion = sdf.parse(Parameters.getVersionName());

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
        dbData.insertData(LoMapsDbConst.VAL_VERSION, Parameters.getVersionName());
        dbData.insertData(LoMapsDbConst.VAL_DB_POI_VERSION, String.valueOf(Parameters.getDbDataPoiVersion()));
        dbData.insertData(LoMapsDbConst.VAL_DB_ADDRESS_VERSION, String.valueOf(Parameters.getDbDataAddressVersion()));

        dbData.destroy();

    }

    // ACTION COMPRESS

    private void actionCompress(ItemMap map) throws IOException {

        if (!map.hasAction(Parameters.Action.GENERATE) && !map.hasAction(Parameters.Action.ADDRESS_POI_DB)) {
            // map hasn't any result file for compress
            return;
        }

        if (!Parameters.isRewriteFiles() && new File(map.getPathResult()).exists()) {
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
        if (map.hasAction(Parameters.Action.GENERATE)) {
            File mapFile = new File(map.getPathGenerate());
            if (!mapFile.exists()) {
                throw new IllegalArgumentException("Map file for compression: " + map.getPathGenerate() + " does not exist.");
            }
            // rewrite bytes in header to set new creation date
            RandomAccessFile raf = new RandomAccessFile(mapFile, "rw");
            raf.seek(36);
            raf.writeLong(Parameters.getSourceDataLastModifyDate());
            raf.close();

            filesToCompress.add(map.getPathGenerate());
        }

        if (map.hasAction(Parameters.Action.ADDRESS_POI_DB)) {

            File poiDbFile = new File(map.getPathAddressPoiDb());
            if (!poiDbFile.exists()) {
                throw new IllegalArgumentException("POI DB file for compression: " + map.getPathAddressPoiDb() + " does not exist.");
            }

            filesToCompress.add(poiDbFile.getAbsolutePath());
        }

        // compress file
        Utils.compressFiles(filesToCompress, map.getPathResult());

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

        if (map.hasAction(Parameters.Action.GENERATE)) {
            dc.addMap(map);
        }

    }
}
