/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.asamm.osmTools;

import com.asamm.locus.features.loMaps.LoMapsDbConst;
import com.asamm.osmTools.cmdCommands.*;
import com.asamm.osmTools.generatorDb.WriterAddressDefinition;
import com.asamm.osmTools.generatorDb.WriterPoiDefinition;
import com.asamm.osmTools.generatorDb.utils.GeomUtils;
import com.asamm.osmTools.mapConfig.ItemMap;
import com.asamm.osmTools.mapConfig.ItemMapPack;
import com.asamm.osmTools.mapConfig.MapSource;
import com.asamm.osmTools.sea.Sea;
import com.asamm.osmTools.server.UploadDefinitionCreator;
import com.asamm.osmTools.tourist.Tourist;
import com.asamm.osmTools.utils.*;
import com.asamm.osmTools.utils.db.DatabaseData;
import com.asamm.osmTools.utils.io.ZipUtils;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.io.WKTWriter;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.json.simple.JSONArray;
import org.json.simple.parser.JSONParser;
import org.kxml2.io.KXmlParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 *
 * @author volda
 */
class Actions {

    private static final String TAG = Actions.class.getSimpleName();

    // parsed configuration of maps
    private MapSource mMapSource;

    protected Actions() {
        mMapSource = new MapSource();
    }
    
    protected void doActions(List<Parameters.Action> actionList)
            throws Exception {
        
        // read config xml and parse mapPack
        parseConfigXml();
        
        // are there any data in mappack?
        if (!mMapSource.hasData()){
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

                if (action == Parameters.Action.ADDRESS_POI_DB){
                    actionCountryBorder (mp);
                }

                actionAllInOne(mp, action);
            }

            // needs to write definition JSON to file (in case that map was generated)
            if (action == Parameters.Action.CREATE_JSON){
                UploadDefinitionCreator.getInstace().writeToFile();
            }

            // perform remaining actions
            if (action == Parameters.Action.UPLOAD) {

                actionUpload();
            }
        }
    }


    private void parseConfigXml() throws IOException, XmlPullParserException {
        FileInputStream fis = null;
        try {
            File xmlFile = new File(Parameters.getConfigPath());
            // test if config file exist
            if (!xmlFile.exists()){
                throw new IllegalArgumentException("Config file "+ xmlFile.getAbsolutePath() + " does not exist!" );
            }

            // prepare variables
            fis = new FileInputStream(xmlFile);
            KXmlParser parser = new KXmlParser();
            parser.setInput(fis, "utf-8");
            int tag;
            String tagName;

            ItemMapPack mapPack = null;

            // finally parse data
            while ((tag = parser.next()) != KXmlParser.END_DOCUMENT) {
                if (tag == KXmlParser.START_TAG) {
                    tagName = parser.getName();
                    if (tagName.equalsIgnoreCase("maps")) {
                        String rewriteFiles = parser.getAttributeValue(null, "rewriteFiles");
                        Parameters.setRewriteFiles(
                                rewriteFiles != null && rewriteFiles.equalsIgnoreCase("yes"));
                    } else if (tagName.equalsIgnoreCase("mapPack")) {
                        mapPack = new ItemMapPack(mapPack);
                        mapPack.fillAttributes(parser);
                    } else if (tagName.equalsIgnoreCase("map")) {
                        ItemMap map = new ItemMap(mapPack);
                        // set variables from xml to map object
                        map.fillAttributes(parser);
                        // set variables with path to files
                        map.setPaths();
                        // set boundaries from polygons
                        map.setBoundsFromPolygon();
                        // finally add map to container
                        mapPack.addMap(map);

                        //***ONLY FOR CREATION ORDERS HOW TO CREATE NEW REGION ON NEW SERVER **/
//                        StringBuilder sb = new StringBuilder("regionData.add(new String[]{");
//                                sb.append("\"").append(mapPack.regionId).append("\", ")
//                                   .append("\"").append(map.regionId).append("\", ")
//                                   .append("\"").append(map.name).append("\"});");
//                        System.out.println(sb.toString());
                    }
                } else if (tag == KXmlParser.END_TAG) {
                    tagName = parser.getName();
                    if (tagName.equals("mapPack")) {
                        if (mapPack.getParent() != null) {
                            mapPack.getParent().addMapPack(mapPack);
                            mapPack = mapPack.getParent();
                        } else {
                            // validate mapPack and place it into list
                            mMapSource.addMapPack(mapPack);
                            mapPack = null;
                        }
                    }
                }
            }
        } finally {
            IOUtils.closeQuietly(fis);
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
        for (int i = 0, m = mp.getMapsCount(); i < m; i++){
            ItemMap map = mp.getMap(i);

            switch (action){
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
        for (int i = 0, m = mp.getMapPackCount(); i < m; i++){
            actionAllInOne(mp.getMapPack(i), action);
        }
    }

    // ACTION EXTRACT

    public void actionExtract(ItemMapPack mp, final MapSource ms)
            throws IOException, InterruptedException {
         Logger.d(TAG, "actionExtract(" + mp + ", " + ms + ")");
        // create hashTable where identificator is sourceId of map and values is an list of
        // all map with same sourceId
        Map<String, List<ItemMap>> mapTableBySourceId = new Hashtable<>();

        // fill hash table with values
        for (int i = 0, m = mp.getMapsCount(); i < m; i++) {
            ItemMap actualMap = mp.getMap(i);

            if (actualMap.hasAction(Parameters.Action.EXTRACT)) {
                List<ItemMap> itemsToExtract = mapTableBySourceId.get(actualMap.getSourceId());
                if (itemsToExtract == null) {
                    itemsToExtract = new ArrayList<>();
                    mapTableBySourceId.put(actualMap.getSourceId(), itemsToExtract);
                }

                // test if file for extract exist. If yes don't add it into ar
                String writeFileLocation = actualMap.getPathSource();
                if (!new File(writeFileLocation).exists()){
                    itemsToExtract.add(actualMap);
                } else {
                    Logger.i(TAG, "Map for extraction: " +writeFileLocation+ " already exist. No action performed" );
                }
            }
        }

        // get valid sources and sort them by availability
        Iterator<String> keys = mapTableBySourceId.keySet().iterator();
        List<String> sources = new ArrayList<>();
        while (keys.hasNext()) {
            // get content and check if we need to process any maps
            String key = keys.next();
            List<ItemMap> ar = mapTableBySourceId.get(key);
            if (ar.isEmpty()) {
                continue;
            }

            // add to list
            sources.add(key);
        }

        // sort by availability
        Collections.sort(sources, new Comparator<String>() {

            @Override
            public int compare(String source1, String source2) {
                // get required parameters
                String file1 = ms.getMapById(source1).getPathSource();
                boolean ex1 = new File(file1).exists();
                String file2 = ms.getMapById(source2).getPathSource();
                boolean ex2 = new File(file2).exists();

                // compare data
                if (ex1) {
                    return -1;
                } else if (ex2) {
                    return 1;
                } else {
                    return 0;
                }
            }
        });

        // finally handle data
        for (int i = 0, m = sources.size(); i < m; i++) {
            String sourceId = sources.get(i);
            List<ItemMap> ar = mapTableBySourceId.get(sourceId);

            CmdExtract ce =  new CmdExtract(ms, sourceId);
            ce.addReadSource();
            ce.addTee(ar.size());
            ce.addBuffer();

            // add all maps
            for (ItemMap map : ar) {
                ce.addBoundingPolygon(map);
                ce.addWritePbf(map.getPathSource(), true);
            }

            // write to log and start stop watch
            TimeWatch time = new TimeWatch();
            Logger.i(TAG, "Extracting maps from source: " + sourceId);
            Main.mySimpleLog.print("\nExtract Maps from: " + sourceId + " ...");

            // now create simple array
            ce.execute();
            Main.mySimpleLog.print("\t\t\tdone "+time.getElapsedTimeSec()+" sec");
        }

        // execute extract also on sub-packs
        for (int i = 0, m = mp.getMapPackCount(); i < m; i++) {
            actionExtract(mp.getMapPack(i), ms);
        }
    }

    /**
     * Create country boundaries for map items in mappack
     *
     * @param mp map pack to create countries for it's items
     */
    private void actionCountryBorder(ItemMapPack mp) throws IOException, InterruptedException {

        // map where key is mappack id and value is list of map to generate boundaries from source
        Map<String, List<ItemMap>> mapTableBySourceId = prepareCountriesForSource(mp);


        // for every source run generation of country borders
        for (Map.Entry<String, List<ItemMap>> entry : mapTableBySourceId.entrySet()) {

            ItemMap sourceMap = mMapSource.getMapById(entry.getKey());
            List<ItemMap> mapToCreate = entry.getValue();

            if (mapToCreate.size() == 0){
                continue;
            }

            // filter only boundary values from source
            CmdCountryBorders cmdCBfilter = new CmdCountryBorders(sourceMap);
            cmdCBfilter.addTaskFilter();
            Logger.i(TAG, "Filter for generation country bound, command: " + cmdCBfilter.getCmdLine());
            cmdCBfilter.execute();

            CmdCountryBorders cmdBorders = new CmdCountryBorders(sourceMap);
            cmdBorders.addGeneratorCountryBoundary();
            cmdBorders.addCountries(mapToCreate);
            Logger.i(TAG, "Generate country boundary, command: " + cmdBorders.getCmdLine() );
            cmdBorders.execute();

            // delete tmp file
            cmdBorders.deleteTmpFile();
        }
    }

    private Map<String, List<ItemMap>> prepareCountriesForSource (ItemMapPack mp) {

        // map where key is mappack id and value is list of map to generate boundaries from source
        Map<String, List<ItemMap>> mapTableBySourceId = new HashMap<>();

        // fill hash table with values in first step proccess map in mappack
        for (ItemMap map : mp.getMaps()) {

            if (!map.hasAction(Parameters.Action.GENERATE) && !map.hasAction(Parameters.Action.ADDRESS_POI_DB)) {
                continue;
            }

            if ( new File(map.getPathCountryBoundaryGeoJson()).exists()) {
                // boundary geom for this map exists > skip it
                Logger.i(TAG, "Country boundaries exits for map: " + map.getNameReadable());
                continue;
            }

            // get the source item for map
            String sourceId = map.getSourceId();
            // get the list of countries that will be created from source
            List<ItemMap> mapsFromSource = mapTableBySourceId.get(sourceId);
            if (mapsFromSource == null) {
                mapsFromSource = new ArrayList<>();
            }
            mapsFromSource.add(map);
            mapTableBySourceId.put(sourceId, mapsFromSource);
        }

        // now get submaps for mappack and their mappack...
        for (ItemMapPack mapPack : mp.getMapPacks()){

            // for every map pack get country boundaries that will be generated
            Map<String, List<ItemMap>> sourcesSubMap = prepareCountriesForSource(mapPack);

            // combine result with parent source map
            for (Map.Entry<String, List<ItemMap>> entry : sourcesSubMap.entrySet()) {

                List<ItemMap> subMaps = mapTableBySourceId.get(entry.getKey());
                if (subMaps == null) {
                    subMaps = new ArrayList<>();
                }
                subMaps.addAll(entry.getValue());
                mapTableBySourceId.put(entry.getKey(), subMaps);
            }
        }

        return mapTableBySourceId;
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

        // check if we want to generate GraphHopper data
        if (!map.hasAction(Parameters.Action.ADDRESS_POI_DB)) {
            return;
        }

        // check if DB file exits and we should overwrite it
        if (!Parameters.isRewriteFiles() && new File(map.getPathAddressPoiDb()).exists()) {
            Logger.d(TAG, "File with Address/POI database '" + map.getPathAddressPoiDb() +
                    "' already exist - skipped." );
            return;
        }

        // load definitions

        File defFile = new File(Parameters.getConfigApDbPath());
        WriterPoiDefinition definition = new WriterPoiDefinition(defFile);
//
        // firstly simplify source file
        CmdDataPlugin cmdPoiFilter = new CmdDataPlugin(map);
        cmdPoiFilter.addTaskSimplifyForPoi(definition);
        Logger.i(TAG, "Filter data for POI DB, command: " + cmdPoiFilter.getCmdLine() );
        cmdPoiFilter.execute();

        // now execute db poi generating
        CmdDataPlugin cmdPoi = new CmdDataPlugin(map);
        cmdPoi.addGeneratorPoiDb();
        Logger.i(TAG, "Generate POI DB, command: " + cmdPoi.getCmdLine());
        cmdPoi.execute();

        //Address generation
        CmdDataPlugin cmdAddressFilter = new CmdDataPlugin(map);
        cmdAddressFilter.addTaskSimplifyForAddress();
        Logger.i(TAG, "Filter data for Address DB, command: " + cmdAddressFilter.getCmdLine());
        //cmdAddressFilter.execute();

        CmdDataPlugin cmdAddres = new CmdDataPlugin(map);
        cmdAddres.addGeneratorAddress();
        Logger.i(TAG, "Generate Adrress DB, command: " + cmdAddres.getCmdLine() );
        //cmdAddres.execute();

        // delete tmp file
        cmdAddres.deleteTmpFile();

    }

    // ACTION COASTLINE

    private void actionCoastline(ItemMap map)
            throws IOException, InterruptedException {
        // check if we want to do this action
        if (!map.requireCoastline()){
            return;
        }

        // check if file exits and we should overwrite it
        if (!Parameters.isRewriteFiles()  && new File(map.getPathCoastline()).exists()){
            Logger.i(TAG, "File with coastlines " + map.getPathCoastline()
                    + " already exist - skipped." );
            return;
        }

        // start Creation sea contourlines
        new Sea(map).create();
    }

    // ACTION TOURIST

    private void actionTourist(ItemMap map) throws IOException, XmlPullParserException {
        // check if we want to do this action
        if (!map.hasAction(Parameters.Action.TOURIST)) {
            return;
        }

        // check if file exits and we should overwrite it
        if (!Parameters.isRewriteFiles()  && new File(map.getPathTourist()).exists()){
            Logger.i(TAG, "File with tourist path " + map.getPathTourist()
                    + " already exist - skipped." );
            return;
        }

        // test if source file exist
        if (!new File (map.getPathSource()).exists()){
            throw new IllegalArgumentException("Input file for creation tourist path "
                    +map.getPathSource()+" does not exist!");
        }

        // start tourist
        Tourist tourist =  new Tourist(map);

        // write to log and start stop watch
        TimeWatch time = new TimeWatch();
        Main.mySimpleLog.print("\nTourist: " + map.getName() + " ...");

        // start creating of tourist data
        tourist.create();

        // notify about result
        Main.mySimpleLog.print("\t\t\tdone " + time.getElapsedTimeSec() + " sec");
        time.stopCount();
    }

    // ACTION CONTOUR

    private void actionContour(ItemMap map) throws IOException, InterruptedException{
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
        Main.mySimpleLog.print("\nContour: "+map.getName()+" ...");
        Logger.i(TAG, "Creating contours: "+map.getPathContour());

        // create commands for generation contours
        CmdContour cc = new CmdContour(map);
        cc.createCmd();
        String cmdRunLastLine = cc.execute();

        // test if exist noSRTM file, delete it before try to create new, because rename new contour
        File contourNoSrtm = new File (map.getPathContour()+"."+Parameters.contourNoSRTM);
        if (contourNoSrtm.exists()){
            if (!contourNoSrtm.delete()){
                throw new IllegalArgumentException("Can not delete file: " + contourNoSrtm.getAbsolutePath());
            }
        }

        // vracim si hodnotu posledni lajny a ptam se jestli tam je string ktery
        // vrati phyhgtmap kdyz jsme v oblasti bez SRTMdat JAk to udelat lepe???
        if (cmdRunLastLine.toLowerCase().contains("no files for this area")) {
            Utils.createEmptyFile(map.getPathContour()+"."+Parameters.contourNoSRTM);
            Main.mySimpleLog.print("\t\t\t no SRTM data");
            Logger.i(TAG, "No SRTM data for map: "+map.getPathContour());
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
            Main.mySimpleLog.print("\t\t\tdone "+time.getElapsedTimeSec()+" sec");

            // stop timeWatch
            time.stopCount();
        }
    }

    // ACTION MERGE

    private void actionMerge(ItemMap map) throws IOException, InterruptedException{
        boolean isContour = (Parameters.isActionRequired(Parameters.Action.CONTOUR) &&
                map.hasAction(Parameters.Action.CONTOUR) && map.getContourSep().equals("no")); // map has set the contour will be separeted
        boolean isTourist = Parameters.isActionRequired(Parameters.Action.TOURIST) &&
                map.hasAction(Parameters.Action.TOURIST);
        boolean isCoastline = map.requireCoastline();
        Logger.i(TAG, "merge:" + isContour + ", " + isTourist + ", " + isCoastline);
        if (!map.hasAction(Parameters.Action.CONTOUR) &&
                !map.hasAction(Parameters.Action.TOURIST) &&
                !isCoastline){
            //Nothing for merginf map will be skipped
            //System.out.println("Nic pro "+map.name);
            return;
        }

        // test if merged file already exist
        if (!Parameters.isRewriteFiles()  && new File (map.getPathMerge()).exists()) {
            // nothing to do file already exist
            Logger.i(TAG, "Merged file: " + map.getPathMerge() + " already exist");
            //set information that map is merged
            map.isMerged = true;
            return;
        }

        // test if file with flag no srtm exist
        boolean existNoSrtm = new File(map.getPathContour()+"."+Parameters.contourNoSRTM).exists();
        if (isContour){
            if (!new File(map.getPathContour()).exists() && !existNoSrtm) {
                //there no file
                throw new IllegalArgumentException ("Contour lines: "+map.getPathContour()+ " does not exist");
            }
            else if (existNoSrtm && !new File(map.getPathContour()).exists()){
                //no SRTM exist -> contour will not be merged
                isContour = false;
            }
        }

        if (isTourist && !new File (map.getPathTourist()).exists()){
            throw new IllegalArgumentException ("Tourist path: "+map.getPathContour()+ " does not exist.");
        }
        if (isCoastline && !new File (map.getPathCoastline()).exists()){
            throw new IllegalArgumentException("Coastlines path: "+ map.getPathCoastline() + " does not exist.");
        }

        // test if extracted map exist
        if (!new File(map.getPathSource()).exists()){
            throw new IllegalArgumentException ("Extracted base map for merging: " +
                    map.getPathSource() + " does not exist.");
        }

        TimeWatch time = new TimeWatch();
        // prepare cmd line and string for log
        String logStr = "Merging maps: "+map.getPathSource() +" and ";
        CmdMerge cm = new CmdMerge(map);
        cm.createCmd();

        Main.mySimpleLog.print("\nMarging: "+map.getName()+" ...");
        Logger.i(TAG, "Merge map parts, command: " + cm.getCmdLine() );
        cm.execute();
        Main.mySimpleLog.print("\t\t\tdone "+time.getElapsedTimeSec()+" sec");
        time.stopCount();

        //set information about margin
        map.isMerged = true;
    }

    // ACTION GENERATE

    private void actionGenerate(ItemMap map) throws IOException, InterruptedException {

        // generate contour lines
        if (map.hasAction(Parameters.Action.GENERATE) && map.getContourSep().equals("yes")) {
            if (Parameters.isRewriteFiles()  || !new File(map.getPathGenerateContour()).exists()){
                CmdGenerate cgc = new CmdGenerate((map));
                cgc.createCmdContour();

                // write to log and start stop watch
                TimeWatch time = new TimeWatch();
                Logger.i(TAG, "Generating separated contour map: "+map.getPathGenerateContour());

                Main.mySimpleLog.print("\nGenerate contours: "+map.getName()+" ...");
                cgc.execute();

                // clean tmp
                Logger.i(TAG, "Deleting files in tmp dir: " + Consts.DIR_TMP);
                Utils.deleteFilesInDir(Consts.DIR_TMP);

                Main.mySimpleLog.print("\t\t\tdone "+time.getElapsedTimeSec()+" sec");

            } else {
                Logger.i(TAG, "Generated contourlines " + map.getPathGenerateContour()
                        + " already exists. Nothing to do.");
            }
        }

        if (map.hasAction(Parameters.Action.GENERATE)){
            if (Parameters.isRewriteFiles()  || !new File(map.getPathGenerate()).exists()){
                CmdGenerate cg = new CmdGenerate(map);
                cg.createCmd();

                // write to log and start stop watch
                TimeWatch time = new TimeWatch();
                Logger.i(TAG, "Generating map: "+map.getPathGenerate());

                Main.mySimpleLog.print("\nGenerate: "+map.getName()+" ...");
                cg.execute();

                // clean tmp
                Logger.i(TAG, "Deleting files in tmp dir: "+Consts.DIR_TMP);
                Utils.deleteFilesInDir(Consts.DIR_TMP);

                Main.mySimpleLog.print("\t\t\tdone "+time.getElapsedTimeSec()+" sec");
            } else {
                Logger.i(TAG, "Generated map "+map.getPathGenerate()+ " already exists. Nothing to do.");
            }
        }
    }

    // ACTION META TABLE

    private void actionInsertMetaData (ItemMap itemMap) throws Exception {

        if (!itemMap.hasAction(Parameters.Action.ADDRESS_POI_DB) || !itemMap.hasAction(Parameters.Action.GENERATE)) {
            return;
        }

        File dbAddressPoiFile = new File(itemMap.getPathAddressPoiDb());
        if (dbAddressPoiFile.getParentFile() != null) {
            dbAddressPoiFile.getParentFile().mkdirs();
        }

        DatabaseData dbData = new DatabaseData(dbAddressPoiFile);

        // read description from definition json
        JSONParser parser = new JSONParser();
        JSONArray descriptionJson = (JSONArray) parser.parse(
                new FileReader(Parameters.getMapDescriptionDefinitionJsonPath()));

        // parse version into java date
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy.MM.dd");
        Date dateVersion = sdf.parse(Parameters.getVersionName());

        // insert version of map
        // create area coverage (it's intersection of country border and data json)
        Geometry geom = WriterAddressDefinition.createDbGeom(
                itemMap.getPathJsonPolygon(), itemMap.getPathCountryBoundaryGeoJson());

        if (!geom.isValid()){
            geom = GeomUtils.fixInvalidGeom(geom);
        }


        if ( !geom.isValid() || geom.isEmpty() || geom.getArea() == 0){
            Logger.i(TAG, GeomUtils.geomToGeoJson(geom));
            throw new IllegalArgumentException("Country map geom is not valid, map : " + itemMap.getName());
        }

        WKTWriter wktWriter = new WKTWriter();

        dbData.insertData(LoMapsDbConst.VAL_AREA, wktWriter.write(geom));
        dbData.insertData(LoMapsDbConst.VAL_COUNTRY, itemMap.getCountryName());
        dbData.insertData(LoMapsDbConst.VAL_DESCRIPTION, descriptionJson.toJSONString());
        dbData.insertData(LoMapsDbConst.VAL_OSM_DATE, String.valueOf(dateVersion.getTime()));
        dbData.insertData(LoMapsDbConst.VAL_REGION_ID, itemMap.getRegionId());
        dbData.insertData(LoMapsDbConst.VAL_VERSION, Parameters.getVersionName());
        dbData.insertData(LoMapsDbConst.VAL_DB_POI_VERSION, String.valueOf(Parameters.getDbDataPoiVersion()));
        dbData.insertData(LoMapsDbConst.VAL_DB_ADDRESS_VERSION, String.valueOf(Parameters.getDbDataAddressVersion()));

        dbData.destroy();

    }

    // ACTION COMPRESS

    private void actionCompress(ItemMap map) throws IOException{

        if (!map.hasAction(Parameters.Action.GENERATE) && !map.hasAction(Parameters.Action.ADDRESS_POI_DB)){
            // map hasn't any result file for compress
            return;
        }

        if (!Parameters.isRewriteFiles() && new File(map.getPathResult()).exists()) {
            Logger.d(TAG, "File with compressed result '" + map.getPathResult() +
                    "' already exist - skipped." );
            return;
        }

        Logger.i(TAG, "Compressing: " + map.getPathResult());
        TimeWatch time = new TimeWatch();
        Main.mySimpleLog.print("\nCompress: " + map.getName() + " ...");

        // change lastChange attribute of generated file
        // this workaround how to set date of map file in Locus
        List<String> filesToCompress = new ArrayList<>();
        if (map.hasAction(Parameters.Action.GENERATE)){
            File mapFile = new File(map.getPathGenerate());
            if (!mapFile.exists()){
                throw new IllegalArgumentException("Map file for compression: " + map.getPathGenerate()+" does not exist.");
            }
            // rewrite bytes in header to set new creation date
            RandomAccessFile raf = new RandomAccessFile(mapFile, "rw");
            raf.seek(36);
            raf.writeLong(Parameters.getSourceDataLastModifyDate());
            raf.close();

            filesToCompress.add(map.getPathGenerate());
        }

        if (map.hasAction(Parameters.Action.ADDRESS_POI_DB)){
            File poiDbFile = new File(map.getPathAddressPoiDb());
            if (!poiDbFile.exists()) {
                throw new IllegalArgumentException("POI DB file for compression: " + map.getPathAddressPoiDb()+" does not exist.");
            }
            filesToCompress.add(map.getPathAddressPoiDb());
        }

        // compress file
        Utils.compressFiles(filesToCompress, map.getPathResult());
        Main.mySimpleLog.print("\t\t\tdone "+time.getElapsedTimeSec()+" sec");
    }

    // ACTION UPLOAD

    private void actionUpload() throws IOException, InterruptedException {

        TimeWatch time = new TimeWatch();
        Logger.i(TAG, "Start action upload ");
        Main.mySimpleLog.print("Uplad data....");

        CmdUpload cmdUpload = new CmdUpload(CmdUpload.UploadAction.UPDATE_ITEM);
        cmdUpload.createCmd();
        cmdUpload.execute();

        Main.mySimpleLog.print("\t\t\tdone "+time.getElapsedTimeSec()+" sec");
    }

    // ACTION CREATE JSON UPLOAD DEFINITION

    public void actionCreateJSON(ItemMap map) throws IOException{
        UploadDefinitionCreator dc = UploadDefinitionCreator.getInstace();

        if (map.hasAction(Parameters.Action.GENERATE)){
            dc.addMap(map);
        }
    }
}