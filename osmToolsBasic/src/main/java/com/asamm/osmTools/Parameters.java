/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.asamm.osmTools;

import com.asamm.osmTools.utils.Consts;
import com.asamm.osmTools.utils.Logger;
import com.asamm.osmTools.utils.Utils;

import java.io.File;
import java.nio.file.Path;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Hashtable;
import java.util.List;

/**
 *
 * @author volda
 */
public class Parameters {

    private static final String TAG = Parameters.class.getSimpleName();

    // BASIC PARAMETERS

    // flag if files should be rewrote during work. Useful for testing
    private static boolean mRewriteFiles = false;

    // ACTION PARAMETERS

    private static final char NO_SHORTCUT = Character.MIN_VALUE;



    public enum GenType {
        LOMAPS,

        STORE_GEOCODING;

        public static GenType createFromValue(String type) {

            if (type == null || type.length() == 0) {
                throw new IllegalArgumentException("Type parameter is empty");
            }

            // set type
            if (type.equals("lomaps")) {
                return  LOMAPS;
            }
            else if (type.equals("storegeo")) {
                return STORE_GEOCODING;
            }
            else {
                throw new IllegalArgumentException(
                        "type parameter '" + type + "' incorrect. Supported types are 'lomaps' or 'storegeo'");
            }
        }
    }

    public enum Action {

        DOWNLOAD("download", 'd'),

        EXTRACT("extract", 'e'),

        COASTLINE("coastline", NO_SHORTCUT),

        TOURIST("tourist", 't'),

        TRANFORM("transform", NO_SHORTCUT),

        CONTOUR("contour", 'c'),

        MERGE("merge", NO_SHORTCUT),

        GENERATE("generate", 'g'),

        GRAPH_HOPPER("graphHopper", 'h'),

        ADDRESS_POI_DB("apDb", 'a'),

        COMPRESS("compress", NO_SHORTCUT),

        UPLOAD("upload", 'u'),

        CREATE_JSON("create_json", NO_SHORTCUT),

        STORE_GEO_DB("storeGeoDb", NO_SHORTCUT);

        // label of action defined in XML file
        private String mLabel;
        // shortcut used in command line
        private char mShortcut;

        Action(String label, char shortcut) {
            this.mLabel = label;
            this.mShortcut = shortcut;
        }

        public String getLabel() {
            return mLabel;
        }

        public char getShortcut() {
            return mShortcut;
        }
    }

    // DEFINED PARAMETERS FROM ARGUMENTS

    // path to base config file
    private static final String mConfigPath = Consts.DIR_BASE + "config" + Consts.FILE_SEP + "config.xml";

    private static final String mConfigStoreGeoPath = Consts.DIR_BASE + "config" + Consts.FILE_SEP + "config_store_geodb.xml";
    // path to base config file for address/poi database
    private static final String mConfigApDbPath = Consts.DIR_BASE + "config" + Consts.FILE_SEP + "config_apDb.xml";

    private static final String mConfigAddressPath = Consts.DIR_BASE + "config" + Consts.FILE_SEP + "config_address.xml";

    private static final String mUploadDefinitionJsonPath = Consts.DIR_BASE + "storeUploadeDefinition.json";

    private static final String mMapDescriptionDefinition = Consts.DIR_BASE + "config" + Consts.FILE_SEP + "map_description_definition.json";

    // name of version itself (like "2014.06.10") it also set the date to the file
    private static String mVersionName;

    // flag if is generated loMaps or storegeocoding database
    private static GenType genType;
    // list of defined actions
    private static List<Action> mActionList;

    // directory where are stored static data that doesn't have to be stored on SSD disk (ContourLines, Coastline, etc)
    private static String  mDataDir;
    // defined directory with HGT files
    private static String mHgtDir;
    // flag if mailing results/errors is enabled
    private static boolean mIsMailing;



    // date of last map modifications which is defined by version name
    private static long mSourceDataLastModifyDate;

    // set basic values
    static {
        mVersionName = "";
        mActionList = new ArrayList<>();
        mHgtDir = "hgt"; // default location for SRTM files
        mIsMailing = false;
    }

    // DIRECTORY PARAMETERS

    // path to osmosis command
    private static String mOsmosisExe;
    private static String mOsmium;
    private static String mOgr2ogr;
    private static String mPythonDir;
    private static String mPython2Dir;
    private static String mShp2osmDir;
    private static String mStoreUploaderPath;

    private static boolean mIsDev = false;
    private static String mPreShellCommand;
    private static String mPostShellCommand;
    // path to graphHopper shell script
    private static String mGraphHopperExe;

    public static String mapOutputFormat = "osm.pbf";  //posibilities: "osm" or "pbf"
    //Contour lines definition 
    public static String phyghtDir;
    public static String contourStep = "20"; //meters;
    public static String contourNoSRTM = "noSRTMdata";
    public static String mTouristTagMapping;
    private static String mContourTagMapping;

    private static String mCustomDataDir;
    
    // TOURIST

    public static long touristNodeId    = 15000000000L;
    public static long touristWayId     = 16000000000L;
    public static long contourNodeId    = 18000000000L;
    public static long contourWayId     = 20000000000L;
    public static long costlineBorderId = 22000000000L;
                                    
    // by this variable decide if print for every tags new way or only
    // find the phighest parent tags and print only one way with this highest tag 
    public static final boolean printHighestWay = false;

    // the list of bicycle network type
    public static  Hashtable<String,Integer> bycicleNetworkType = new Hashtable<>();
    public static  Hashtable<String,Integer> hikingNetworkType = new Hashtable<>();
    public static  ArrayList<String> hikingColourType;

    // description in header of map file
    public static String MAP_COMMENT =
            "<div><h4>Vector maps for <a href=\"http://www.locusmap.eu\">Locus</a> application</h4>"
            + " Created by <a href=\"http://code.google.com/p/mapsforge/\">Mapsforge</a> Map-Writer"
            + "<br />"
            + " Data source OpenStreetMap community"
            + "<br /><br/>"
            + "</div>";


    // META DATA TABLE

    private static final int mDbDataPoiVersion = 1;
    private static final int mDbDataAddressVersion = 2;


    /*                     GETTERS                     */

    // DEFINED PARAMETERS FROM ARGUMENTS

    public static boolean isRewriteFiles() {
        return mRewriteFiles;
    }

    public static void setRewriteFiles(boolean rewrite) {
        mRewriteFiles = rewrite;
    }

    public static String getConfigPath() {
        return mConfigPath;
    }

    public static String getConfigStoreGeoPath() {
        return mConfigStoreGeoPath;
    }

    public static String getCoastlineShpFile() {

        // set path to land polygon shape file
        return getDataDir() + "coastlines" +
                Consts.FILE_SEP + "land-polygons" + Consts.FILE_SEP + "land_polygons.shp";
    }

    public static String getConfigApDbPath() {
        return mConfigApDbPath;
    }

    public static String getConfigAddressPath () {
        return mConfigAddressPath;
    }

    public static String getUploadDefinitionJsonPath() {
        return mUploadDefinitionJsonPath;
    }

    public static String getMapDescriptionDefinitionJsonPath (){
        return mMapDescriptionDefinition;
    }

    public static String getContourTagMapping() {
        return mContourTagMapping;
    }

    public static String getVersionName() {
        return mVersionName;
    }

    public static long getSourceDataLastModifyDate() {
        return mSourceDataLastModifyDate;
    }

    public static boolean isActionRequired(Action action) {
        return mActionList.contains(action);
    }

    public static List<Action> getActions() {
        return mActionList;
    }

    public static GenType getGenType() {
        return genType;
    }


    /**
     * Return defined path to the directory with static data. If dir is not defined then
     * working directory is returned
     * @return path to directory with static data
     */
    public static String getDataDir() {
        if (mDataDir == null || mDataDir.length() == 0){
            return Consts.DIR_BASE;
        }
        return mDataDir;
    }

    public static String getHgtDir() {
        return mHgtDir;
    }

    public static String getOgr2ogr() {
        return mOgr2ogr;
    }

    public static String getPythonDir() {
        return mPythonDir;
    }

    public static String getPython2Dir() {
        return mPython2Dir;
    }

    public static String getShp2osmDir() {
        return mShp2osmDir;
    }

    public static boolean isMailing() {
        return mIsMailing;
    }

    public static String getPreShellCommand() {
        return mPreShellCommand;
    }

    public static String getPostShellCommand() {
        return mPostShellCommand;
    }

    // DIRECTORY PARAMETERS

    public static String getOsmosisExe() {
        return mOsmosisExe;
    }

    public static String getOsmium(){
        return mOsmium;
    }

    public static String getGraphHopperExe() {
        return mGraphHopperExe;
    }

    public static String getCustomDataDir() {
        return mCustomDataDir;
    }

    public static String getStoreUploaderPath() {
        return mStoreUploaderPath;
    }

    public static boolean getIsDev() {
        return mIsDev;
    }

    // DB META DATA PARAMS

    public static int getDbDataPoiVersion() {
        return mDbDataPoiVersion;
    }

    public static int getDbDataAddressVersion() {
        return mDbDataAddressVersion;
    }


    /*               BASIC FUNCTIONS                   */

    /**
     * Core function that parse input arguments (defined by user) to
     * various static variables used during generating
     * @param args array of defined arguments
     */
    static void parseArgs(String[] args){
        // do basic check
        if (args == null || args.length < 4) {
            String errorText = "Wrong parameter, please define parameters: "
                   + "--version <version_name_in_format_ yyyy.MM.dd> --actions <adectgu> "
                   + "--email <true_or_false_if_send_email> --hgtdir <pathToSrtmData>";
            Logger.w(TAG, errorText);
            System.exit(1);
            return;
        }

        // iterate over all arguments
        for (int i = 0; i < args.length; i++) {

            // set version name
            if (args[i].equals("--version") && !args[++i].startsWith("--") ){
                mVersionName = args[i] ;

                // parse version to date
                SimpleDateFormat sdf =  new SimpleDateFormat("yyyy.MM.dd");
                Date date;

                try {
                    date = sdf.parse(mVersionName);
                    mSourceDataLastModifyDate = date.getTime();
                } catch (ParseException e){
                    Logger.w(TAG, "Wrong data format. Use yyyy.MM.dd");
                    System.exit(1);
                }
            }

            if (args[i].equals("--type") && !args[++i].startsWith("--") ){
                setGeneratorType(args[i]);
            }

            if (args[i].equals("--storeUploader") && !args[++i].startsWith("--") ){
                setStoreUploaderPath(args[i]);
            }

            if (args[i].equals("--isDev")){
                setIsDev();
            }
            // set defined actions
            if (args[i].equals("--actions") && !args[++i].startsWith("--") ){
                setActions(args[i]);
            }

            // get directory with HGT files
            if (args[i].equals("--hgtdir") && !args[++i].startsWith("--") ){
                mHgtDir = args[i] ;

                // check if dir exists
                if (!new File(mHgtDir).exists()){
                    Logger.w(TAG, "Directory for hgt cache " + mHgtDir + " doesn't exist!");
                    System.exit(1);
                }
            }

            // static data that are not in the same working dir as result
            if (args[i].equals("--datadir") && !args[++i].startsWith("--") ){

                mDataDir = Consts.fixDirectoryPath(new File(args[i]).getAbsolutePath());

                // check if dir exists
                if (!new File(mDataDir).exists()){
                    Logger.w(TAG, "Directory for static data " + mDataDir + " doesn't exist!");
                    System.exit(1);
                }
            }

            // check email
            if (args[i].equals("--email") && !args[++i].startsWith("--") ){
                if (args[i].toLowerCase().equals("yes")){
                    mIsMailing = true;
                } else if (args[i].toLowerCase().equals("no")){
                    mIsMailing = false;
                } else {
                    Logger.w(TAG, "Wrong value for argument --email. Possible values yes|no");
                    System.exit(1);
                }
            }
        }

           // check parameter 'version'
        if (mVersionName == null || mVersionName.length() == 0) {
            Logger.w(TAG, "parseArgs(), missing argument 'version'");
            System.exit(1);
        }

        // check parameter 'actions'
        if (mActionList.size() < 1) {
            Logger.w(TAG, "parseArgs(), missing argument 'actions'");
            System.exit(1);
        }

        // check if path for to upload script is defined
        if (getStoreUploaderPath() == null){
            Logger.w(TAG, "Please define path to Store uploader script. Use parameter  --storeUploader <path>");
            System.exit(1);
        }
        if (! new File(getStoreUploaderPath()).exists()){
            Logger.w(TAG, "Store uploader script doesn't exist in the location: " + getStoreUploaderPath());
            System.exit(1);
        }
    }

    /**
     * Parse cmd type parameter value to recognize if LoMaps are generated or if Store GeoCoding database is generated
     */
    private static void setGeneratorType(String s) {
        genType = GenType.createFromValue(s);
    }

    /**
     * Set path to store uploader script
     * @param path path to uploader
     */
    private static void setStoreUploaderPath(String path){
        mStoreUploaderPath = new File(path).getAbsolutePath().toString();
    }

    /**
     * Parse cmd parameter to boolean value if is upload data to dev or prod environment of Locus Store
     */
    private static void setIsDev(){
        mIsDev = true;
    }

    /**
     * Define that actions will be performed based on command line definition
     * @param cmdActions cmd argument defined the actions to performed
     */
    private static void setActions(String cmdActions) {
        // firstly check parameter
        validateActionArg(cmdActions);

        // handle download action
        if (hasArgAction(cmdActions, Action.DOWNLOAD)){
            addAction(Action.DOWNLOAD);
        }

        // handle extract action
        if (hasArgAction(cmdActions, Action.EXTRACT)){
            addAction(Action.EXTRACT);
        }

        // handle graphhopper request
        if (hasArgAction(cmdActions, Action.GRAPH_HOPPER)) {
            addAction(Action.GRAPH_HOPPER);
        }

        // handle Address/POI db request
        if (hasArgAction(cmdActions, Action.ADDRESS_POI_DB)) {
            addAction(Action.ADDRESS_POI_DB);
        }

        // handle action for generating tourist tracks
        if (hasArgAction(cmdActions, Action.TOURIST)) {
            addAction(Action.TOURIST);
        }

        // action for generating contours
        if (hasArgAction(cmdActions, Action.CONTOUR)){
            addAction(Action.CONTOUR);
        }

        // handle generate action
        if (hasArgAction(cmdActions, Action.GENERATE)){
            // add merge and coastlines
            addAction(Action.COASTLINE);
            addAction(Action.TRANFORM);
            addAction(Action.MERGE);

            // finally add generate
            addAction(Action.GENERATE);
            addAction(Action.COMPRESS);
            addAction(Action.CREATE_JSON);
        }

        // handle upload action
        if (cmdActions.contains("u")){
            //addAction(Action.CREATE_JSON);
            addAction(Action.UPLOAD);
        }
    }

    private static void validateActionArg(String arg) {
        Action[] actions = Action.values();
        for (int i = 0; i < arg.length(); i++) {
            char argAction = arg.charAt(i);

            // check validity
            boolean valid = false;
            for (int j = 0; j < actions.length; j++) {
                if (actions[j].getShortcut() == argAction) {
                    valid = true;
                    break;
                }
            }

            // handle result
            if (!valid) {
                Logger.w(TAG, "Unknown action '" + argAction + "'");
                Logger.w(TAG, "Wrong type of actions, possibilities: \n"
                        + "d \tfor downloading\n"
                        + "e \texract data from donwloaded files\n"
                        + "t \tcreate cyclo and hiking paths"
                        + "c \tcreate pbf files with contour lines\n"
                        + "g \tgenerate map files and also create zip archive generated files\n"
                        + "u \tupload data to GCS server");
                throw new IllegalArgumentException("Actions '" + arg + "', are not valid");
            }
        }
    }

    /**
     * Check if defined arguments contains specific action.
     * @param arg arguments from user that are checked
     * @param act required action
     * @return <code>true</code> if action is defined in arguments
     */
    private static boolean hasArgAction(String arg, Action act) {
        for (int i = 0, m = arg.length(); i < m; i++) {
            if (arg.charAt(i) == act.getShortcut()) {
                return true;
            }
        }
        return false;
    }

    private static void addAction(Action action) {
        if (!mActionList.contains(action)) {
            mActionList.add(action);
        }
    }

    /**
     * Function that perform finalizing variables based on
     * prepared parameters.
     */
    static void initialize() {

        // path to the mapsforge definition file for generation
        mTouristTagMapping = Consts.DIR_BASE + "config" + Consts.FILE_SEP + "tag-mapping-tourist.xml";
        mContourTagMapping = Consts.DIR_BASE + "config" + Consts.FILE_SEP + "tag-mapping-contour.xml";

        // osmosisDir
        String osmosisPath = "osmosis" + Consts.FILE_SEP + "bin" + Consts.FILE_SEP;

        // shp2osm script location
        mShp2osmDir = "shp2osm" + Consts.FILE_SEP + "shp2osm.py";

        // graphHopper path
        mGraphHopperExe = new File("graphHopper" + Consts.FILE_SEP + "graphhopper.sh").
                getAbsolutePath();

        mCustomDataDir = Consts.DIR_BASE + "custom_data";

        if (Utils.isSystemUnix()){
            mOsmosisExe = new File(osmosisPath + "osmosis").getAbsolutePath();
            mOsmium = "osmium";
            phyghtDir = "/usr/local/bin/phyghtmap";
            mOgr2ogr = "ogr2ogr";
            mPythonDir = "/usr/bin/python3";
            mPython2Dir = "/usr/bin/python2";
            mPreShellCommand = "";
            mPostShellCommand = "";
        } else if (Utils.isSystemWindows()){
            mOsmosisExe = new File (osmosisPath + "osmosis.bat").getAbsolutePath();
            mOsmium = "osmium";
            phyghtDir = "C:\\Python27\\Scripts\\phyghtmap.exe";
            mOgr2ogr = "C:\\Program Files\\FWTools2.4.7\\bin\\ogr2ogr.exe";
            mPythonDir = "C:\\Python27\\python.exe";
            mPython2Dir = "C:\\Python27\\python.exe";
            mPreShellCommand = "c:\\work\\cygwin64\\bin\\bash.exe -c '";
            mPostShellCommand = "'";
        } else {
            mOsmosisExe = new File (osmosisPath + "osmosis").getAbsolutePath();
            mOsmium = "osmium";
            phyghtDir = "/usr/bin/phyghtmap";
            mOgr2ogr = "ogr2ogr";
            mPythonDir = "/usr/bin/python";
            mPreShellCommand = "";
            mPostShellCommand = "";
        }

        // ============= TOURIST VARIABLES ====================

        // set attributes for order of cyclo routes
        bycicleNetworkType.put("lcn", 1);
        bycicleNetworkType.put("rcn", 2);
        bycicleNetworkType.put("ncn", 3);
        bycicleNetworkType.put("icn", 4);
            
        // set list of possible colour of hiking routes (guess from osmc:symbol tag
        hikingColourType = new ArrayList<String>();
        hikingColourType.add("blue");
        hikingColourType.add("yellow");
        hikingColourType.add("green");
        hikingColourType.add("red");
        hikingColourType.add("black");
        hikingColourType.add("white");
        
        // set order of hiking network tag
        hikingNetworkType.put("lwn", 1);
        hikingNetworkType.put("rwn", 2);
        hikingNetworkType.put("nwn", 3);
        hikingNetworkType.put("iwn", 4);
        
        // Map COMMENT
        MAP_COMMENT =
            " <div><h4>Vector maps for <a href=\"http://www.locusmap.eu\">Locus</a> application</h4>"
            + " Created by <a href=\"http://code.google.com/p/mapsforge/\">Mapsforge</a> Map-Writer"
            + "<br />"
            + " Map data source OpenStreetMap community";
        if (mActionList.contains(Action.CONTOUR)){
            MAP_COMMENT +=
                 "<br /> "
                + " Contour lines source <a href=\"http://srtm.usgs.gov\">SRTM</a> and "
                    + "<a href=\"http://www.viewfinderpanoramas.org\">Viewfinder Panoramas</a>";
        }
        MAP_COMMENT +=
             "<br /><br />"
            + "</div>";


        // check validity of directories on the end
        checkDirectories();
    }

    private static void checkDirectories() {
        // check osmosis directory
        File fileOsmosis = new File(getOsmosisExe());
        if (!fileOsmosis.exists() || !fileOsmosis.isFile()) {
            throw new IllegalArgumentException("Check of osmosis file exists:" + getOsmosisExe());
        }

        // check graphHopper directory
        if (isActionRequired(Action.GRAPH_HOPPER)) {
            File fileGraphHopper = new File(getGraphHopperExe());
            if (!fileGraphHopper.exists() || !fileGraphHopper.isFile()) {
                throw new IllegalArgumentException("Invalid GraphHopper file:" + getGraphHopperExe());
            }
        }

        // check temporary directory
        File tmpDirF = new File(Consts.DIR_TMP);
        if (!tmpDirF.exists()) {
            Logger.d(TAG, "creating dir " + tmpDirF.getAbsolutePath());
            tmpDirF.mkdirs();
        }
    }
}
