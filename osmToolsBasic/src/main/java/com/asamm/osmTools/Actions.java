/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.asamm.osmTools;

import com.asamm.osmTools.cmdCommands.*;
import com.asamm.osmTools.generatorDb.DataWriterDefinition;
import com.asamm.osmTools.mapConfig.ItemMap;
import com.asamm.osmTools.mapConfig.ItemMapPack;
import com.asamm.osmTools.mapConfig.MapSource;
import com.asamm.osmTools.sea.Sea;
import com.asamm.osmTools.server.LocusServerHandler;
import com.asamm.osmTools.tourist.Tourist;
import com.asamm.osmTools.utils.*;
import com.asamm.osmTools.utils.io.ZipUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.kxml2.io.KXmlParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
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
            
            // extract action
            Iterator<ItemMapPack> packs = mMapSource.getMapPacksIterator();
            while (packs.hasNext()) {
                ItemMapPack mp = packs.next();

                // handle extract first, because we need to handle whole pack at once
                if (action == Parameters.Action.EXTRACT) {
                    actionExtract(mp, mMapSource);
                    continue;
                }
                actionAllInOne(mp, action);
            }

            // perform remaining actions
            if (action == Parameters.Action.CREATE_XML) {
                CreateXml serverXml = new CreateXml();
                CreateHtml mapHtml  = new CreateHtml();
                serverXml.startWrite();
                mapHtml.startString();

                Iterator<ItemMapPack> packs2 = mMapSource.getMapPacksIterator();
                while (packs2.hasNext()) {
                    ItemMapPack mp = packs2.next();
                    actionCreateXml(mp, mMapSource, serverXml, mapHtml);
                }
                serverXml.finish();
                mapHtml.writeString();
                Main.mySimpleLog.print("\nCreating maps xml - DONE");
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
                        if (mapPack == null) {
                            mapPack = new ItemMapPack();
                        } else {
                            mapPack = new ItemMapPack(mapPack);
                        }
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
                        } else if (mapPack.isValid()) {
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
        String line = "\n================ " + action.getLabel().toUpperCase() + " ================\n";
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
                    break;
                case COMPRESS:
                    actionCompress(map);
                    break;
                case UPLOAD:
                    actionUpload(map);
                    break;
            }
        }

        // iterate over all MapPacks and call same function on them
        for (int i = 0, m = mp.getMapPackCount(); i < m; i++){
            actionAllInOne(mp.getMapPack(i), action);
        }
    }

    // ACTION EXTRACT

    public void actionExtract(ItemMapPack mp, MapSource ms)
            throws IOException, InterruptedException {
Logger.d(TAG, "actionExtract(" + mp + ", " + ms + ")");
        // create hashTable where identificator is sourceId of map and values is an list of
        // all map with same sourceId
        Hashtable<String, List<ItemMap>> mapTableBySourceId =
                new Hashtable<String, List<ItemMap>>();

        // fill hash table with values
        for (int i = 0, m = mp.getMapsCount(); i < m; i++) {
            ItemMap actualMap = mp.getMap(i);

            if (actualMap.hasAction(Parameters.Action.EXTRACT)) {
                List<ItemMap> ar = mapTableBySourceId.get(actualMap.getSourceId());
                if (ar == null) {
                    ar = new ArrayList<ItemMap>();
                    mapTableBySourceId.put(actualMap.getSourceId(), ar);
                }

                // test if file for extract exist. If yes don't add it into ar
                String writeFileLocation = actualMap.getPathSource();
                if (!new File(writeFileLocation).exists()){
                    // create dir for extracted map
                    FileUtils.forceMkdir(new File(actualMap.getPathSource()).getParentFile());

                    // add to container
                    ar.add(actualMap);
                } else {
//                    Logger.i(TAG, "Map for extraction: " +writeFileLocation+ " already exist. No action performed" );
                }
            }
        }

        // create cmd line from hashtable
        Enumeration<String> keys = mapTableBySourceId.keys();
        while (keys.hasMoreElements()) {

            String key = keys.nextElement();
            // list of all maps with same sourceId
            List<ItemMap> ar = mapTableBySourceId.get(key);

            // key is equal to sourceId
            // cmd has an List named cmdList; following cycle fill cmd parameters into List
            // if ar is Empty there is no map for generation
            if (!ar.isEmpty()) {
                CmdExtract ce =  new CmdExtract(ms, key);
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
                Logger.i(TAG, "Extracting maps from source: "+key);
                Main.mySimpleLog.print("\nExtract Maps from: "+key+" ...");

                // now create simple array
                ce.execute();
                Main.mySimpleLog.print("\t\t\tdone "+time.getElapsedTimeSec()+" sec");
            }
        }

        // execute extract also on sub-packs
        for (int i = 0, m = mp.getMapPackCount(); i < m; i++) {
            actionExtract(mp.getMapPack(i), ms);
        }
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

        // check if file exits and we should overwrite it
        if (!Parameters.isRewriteFiles() && new File(map.getPathAddressPoiDb()).exists()) {
//            Logger.i(TAG, "File with Address/POI database '" + map.getPathAddressPoiDb() +
//                    "' already exist - skipped." );
            return;
        }

        // load definitions
        File defFile = new File(Parameters.getConfigApDbPath());
        DataWriterDefinition definition = new DataWriterDefinition(defFile);

        // firstly simplify source file
        CmdAddressPoiDb cmd = new CmdAddressPoiDb(map);
        cmd.addTaskSimplify(definition);
        cmd.execute();

        // now execute map generating
        CmdAddressPoiDb cmdGen = new CmdAddressPoiDb(map);
        cmdGen.addGeneratorDb();
        cmdGen.execute();

        // after generating, pack file and delete original
        File generatedDb = cmdGen.getFileTempDb();
        Utils.compressFile(generatedDb.getAbsolutePath(), map.getPathAddressPoiDb());
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

        // write to log and start stop watch
        TimeWatch time = new TimeWatch();
        Main.mySimpleLog.print("\nSea: " + map.getName() + " ...");

        // start Creation sea contourlines
        Sea sea =  new Sea(map);
        sea.create();

        // notify about result
        Main.mySimpleLog.print("\t\t\tdone " + time.getElapsedTimeSec() + " sec");
        time.stopCount();
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

        // prepare cmd line and string for log
        String logStr = "Merging maps: "+map.getPathSource() +" and ";

        CmdMerge cm = new CmdMerge(map);
        cm.addReadPbf(map.getPathSource());
        if (isCoastline){
            cm.addReadPbf(map.getPathCoastline());
            cm.addMerge();
            logStr += "coastlines: " + map.getPathCoastline() + " ";
        }

        if (isTourist){
            cm.addReadXml(map.getPathTourist());
            //cm.addBoundingPolygon(map);
            cm.addMerge();
            logStr += "tourist: " +map.getPathTourist() + " ";
        }
        if (isContour){
            cm.addReadPbf(map.getPathContour());
            cm.addMerge();
            logStr += "contours: "+map.getPathContour()+ " ";
        }
        cm.addBuffer();
        cm.addWritePbf(map.getPathMerge(), true);

        TimeWatch time = new TimeWatch();
        Logger.i(TAG, logStr);
        Main.mySimpleLog.print("\nMarging: "+map.getName()+" ...");
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
                Logger.i(TAG, "Generating separeted contour map: "+map.getPathGenerateContour());

                Main.mySimpleLog.print("\nGenerate contous: "+map.getName()+" ...");
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

    // ACTION COMPRESS

    private void actionCompress(ItemMap map) throws IOException{
        // check if wants compression
        if (!map.hasAction(Parameters.Action.GENERATE)) {
            return;
        }

        if (!new File(map.getPathResult()).exists()){
            Logger.i(TAG, "Compressing: " + map.getPathResult());
            TimeWatch time = new TimeWatch();
            Main.mySimpleLog.print("\nCompress: "+map.getName()+" ...");

            // change lastChange attribute of generated file
            // this workaround how to set date of map file in Locus
            File source = new File(map.getPathGenerate());
            if (!source.exists()){
                throw new IllegalArgumentException("Source file for compression: "+source+" does not exist.");
            }

            // rewrite bytes in header to set new creation date
            // TODO is this really needed??
            RandomAccessFile raf = new RandomAccessFile(source, "rw");
            raf.seek(36);
            raf.writeLong(Parameters.getSourceDataLastModifyDate());
            raf.close();

            // compress file
            Utils.compressFile(map.getPathGenerate(), map.getPathResult());
            Main.mySimpleLog.print("\t\t\tdone "+time.getElapsedTimeSec()+" sec");
        } else {
            Logger.i(TAG, "Compressed file " + map.getPathResult() + " exists. No action");
        }
    }

    // ACTION UPLOAD

    private void actionUpload(ItemMap map) throws IOException{
        // type of action do for all mappacks/maps
        if (map.hasAction(Parameters.Action.GENERATE)){
            // obtain rellative path from the direcotory results. If null breake script
            String relPath;
            if ((relPath = map.getRelativeResultsPath()) == null){
                throw new IllegalArgumentException("Uplad AWS: no relative path for "+map.getName());
            }

            Logger.i(TAG, "Start action upload for map: "+map.getPathResult());
            // compute MD5  hash for map
            map.setResultMD5hash(Utils.generateMD5hash(map.getPathResult()));
            relPath = Utils.changeSlashToUnix(relPath);

            //System.out.println(relPath);

            AmazonHandler ah = AmazonHandler.getInstance();
            if (!ah.isMapUploaded(map, relPath)){

                Logger.i(TAG, "Map "+map.getPathResult()+" doesn't exists on AS3. Tryt to upload it");
                // upload file to AMAZON
                if (!ah.uploadToAws(map, relPath)){
                    throw new IllegalArgumentException ("Uploading file"+ relPath +" aborted");
                }
            }

            LocusServerHandler lsh = LocusServerHandler.getInstance();
            lsh.uploadInfoToLocusServer(map);
        }
    }

    // ACTION CREATE XML

    public void actionCreateXml(ItemMapPack mp, MapSource ms, CreateXml xml, CreateHtml html) throws IOException{
        html.addDir(mp);
        html.startUl();
        for (int i = 0, m = mp.getMapPackCount(); i < m; i++) {
            actionCreateXml(mp.getMapPack(i), ms, xml, html);
        }

        for (int i = 0, m = mp.getMapsCount(); i < m; i++) {
            ItemMap map = mp.getMap(i);
            if (map.hasAction(Parameters.Action.GENERATE)){
                xml.addMap(ms, map);
                html.addMap(map);
            }
        }
        html.endUl();
        html.endLi();
    }
}