/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.asamm.osmTools;

import com.asamm.osmTools.server.LocusServerConst;
import com.asamm.osmTools.utils.Consts;
import com.asamm.osmTools.utils.Logger;
import com.asamm.osmTools.utils.Utils;

import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 *
 * @author volda
 */
public class Parameters {

    private static final String TAG = Parameters.class.getSimpleName();

    // BASIC PARAMETERS

    // flag if files should be rewrote during work. Useful for testing
    private static boolean mRewriteFiles;

    // ACTION PARAMETERS

    private static final char NO_SHORTCUT = Character.MIN_VALUE;

    public enum Action {

        DOWNLOAD("download", 'd'),

        EXTRACT("extract", 'e'),

        COASTLINE("coastline", NO_SHORTCUT),

        TOURIST("tourist", 't'),

        CONTOUR("contour", 'c'),

        MERGE("merge", 'm'),

        GENERATE("generate", 'g'),

        GRAPH_HOPPER("graphHopper", 'h'),

        ADDRESS_POI_DB("apDb", 'a'),

        COMPRESS("compress", NO_SHORTCUT),

        UPLOAD("upload", 'u'),

        CREATE_XML("create_xml", NO_SHORTCUT);

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
    private static String mConfigPath = Consts.DIR_BASE + "config.xml";
    // path to base config file for address/poi database
    private static String mConfigApDbPath = Consts.DIR_BASE + "config_apDb.xml";

    // name of working (version) directory
    private static String mVersionDir;
    // name of version itself (like "2014.06.10")
    private static String mVersionName;
    // list of defined actions
    private static List<Action> mActionList;
    // defined directory with HGT files
    private static String mHgtDir;
    // flag if mailing results/errors is enabled
    private static boolean mIsMailing;
    // date of last map modifications "yyyy-MM-dd"
    private static long mSourceDataLastModifyDate;
    // you can change date in attribute line

    // set basic values
    static {
        mVersionDir = "";
        mVersionName = "";
        mActionList = new ArrayList<Action>();
        mHgtDir = "";
        mIsMailing = false;
        mSourceDataLastModifyDate = new Date().getTime();
    }

    // DIRECTORY PARAMETERS

    // path to osmosis command
    private static String mOsmosisExe;
    public static String ogr2ogr;
    public static String pythonDir;
    public static String shp2osmDir;
    private static String mPreShellCommand;
    private static String mPostShellCommand;
    // path to graphHopper shell script
    private static String mGraphHopperExe;

    // ****** VERSIONING *********
    public static final int VERSION_CODE = 103; //
    public static final boolean CAN_BE_WELCOME_PRESENT = true;

   //  -------------- LOCUS STORE IDs --------           
    public static final long[] VECTOR_IMAGES = new long[]{5120937556967424L, 5683887510388736L, 5737979670691840L, 6218248282439680L, 6246837463810048L, 6298375192313856L};
    public static final long VECTOR_IC0N = 5414151014842368L;
   //  ----------------------------------------------- 
    
//      // -------------- LOCAL HOST -------- 
//    public static final long[] VECTOR_IMAGES = new long[]{5638295627235328l};  
//    public static final long VECTOR_IC0N = 4934608185458688l;
//    //-------------------------------------------
    
    public static final String VECTOR_DESCRIPTION = "Vector maps work fully offline and are available for the whole world. These maps are placed directly in your device and for this reason you can " +
						"change their appearance using built-in themes.\n"+
						"\n<ul></ul>"+ 
						"\nAfter purchase of vector maps, follow please these instructions:"+ 
						"\n<ol>"+
						"\n<li>open Menu  - Maps - Vector tab </li>"+
						"\n<li>select vector map in a list</li> "+
						"\n</ol>"+ 
						"\n<h4>Locus Guarantee</h4>"+
						"\nAll your vector maps you can repeatedly download for 1 year since your purchase in the same vector maps <b>version</b>.";
    
     public static final HashMap<Integer, Integer> supportedVersions = new HashMap<Integer, Integer>();
    static {
        supportedVersions.put(LocusServerConst.PACKAGE_FREE, 236);
        supportedVersions.put(LocusServerConst.PACKAGE_PRO, 236);
        supportedVersions.put(LocusServerConst.PACKAGE_TESTING, 236);
        
        supportedVersions.put(LocusServerConst.PACKAGE_PRO_AMAZON, 236);
        supportedVersions.put(LocusServerConst.PACKAGE_PRO_COMPUTER_BILD, 236);
        supportedVersions.put(LocusServerConst.PACKAGE_PRO_MOBIROO, 236);
        supportedVersions.put(LocusServerConst.PACKAGE_PRO_SAMSUNG, 236);
        
        supportedVersions.put(LocusServerConst.PACKAGE_FREE_AMAZON, 236);
        supportedVersions.put(LocusServerConst.PACKAGE_FREE_UBINURI, 236);
        supportedVersions.put(LocusServerConst.PACKAGE_FREE_SAMSUNG, 236);
        
        supportedVersions.put(LocusServerConst.PACKAGE_GIS, 1);
    
    }                 
    
   
    public static String mapOutputFormat = "osm.pbf";  //posibilities: "osm" or "pbf"
    //Contour lines definition 
    public static String phyghtDir;
    public static String contourStep = "20"; //meters;
    public static String contourNoSRTM = "noSRTMdata";
    public static String touristTagMapping;
    public static String contourTagMapping;
    
    
    public static String outputXml;
    public static String htmlMapHeaderFile = Consts.DIR_BASE + "config" +
            Consts.FILE_SEP + "maps_header.html";
    public static String htmlMapPath; 
    

    

    // ============= TOURIST =================
    public static String tagMappingFile;

    
    public static long touristNodeId    = 15000000000L;
    public static long touristWayId     = 16000000000L;
    public static long contourNodeId    = 18000000000L;
    public static long contourWayId     = 20000000000L;
    public static long costlineBorderId = 22000000000L;
                                    
    public static String coastlineShpFile;
    
    // by this variable decide if print for every tags new way or only
    // find the phighest parent tags and print only one way with this highest tag 
    public static final boolean printHighestWay = false;
    
    // the list of bycicle network type
    public static  Hashtable<String,Integer> bycicleNetworkType = new Hashtable<String, Integer>();
    public static  Hashtable<String,Integer> hikingNetworkType = new Hashtable<String, Integer>();
    public static  ArrayList<String> hikingColourType; 
    
    
    // popis map
    public static String MAP_COMMENT =
            "<div><h4>Vector maps for <a href=\"http://www.locusmap.eu\">Locus</a> application</h4>"
            + " Created by <a href=\"http://code.google.com/p/mapsforge/\">Mapsforge</a> Map-Writer"
            + "<br />"
            + " Data source OpenStreetMap community"
            + "<br /><br/>"
            + "</div>";

    /***************************************************/
    /*                     GETTERS                     */
    /***************************************************/

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

    public static String getConfigApDbPath() {
        return mConfigApDbPath;
    }

    public static String getVersionDir() {
        return mVersionDir;
    }

    public static String getVersionName() {
        return mVersionName;
    }

    public static boolean isActionRequired(Action action) {
        return mActionList.contains(action);
    }

    protected static List<Action> getActions() {
        return mActionList;
    }

    public static String getHgtDir() {
        return mHgtDir;
    }

    public static boolean isMailing() {
        return mIsMailing;
    }

    public static long getSourceDataLastModifyDate() {
        return mSourceDataLastModifyDate;
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

    public static String getGraphHopperExe() {
        return mGraphHopperExe;
    }

    /***************************************************/
    /*               BASIC FUNCTIONS                   */
    /***************************************************/

    /**
     * Core function that parse input arguments (defined by user) to
     * various static variables used during generating
     * @param args array of defined arguments
     */
    static void parseArgs(String[] args){
        // do basic check
        if (args == null || args.length < 4) {
            String errorText = "Wrong parameter, please define parameters: "
                   + "--dir <name_work_dir> --version <version_name> --actions <dectgu> "
                   + "--email <true_or_false_if_send_email> --hgtdir <pathToSrtmData>";
            Logger.w(TAG, errorText);
            System.exit(1);
            return;
        }

        // iterate over all arguments
        for (int i = 0; i < args.length; i++) {

            // set main version directory name
            if (args[i].equals("--dir") && !args[++i].startsWith("--") ){
                mVersionDir = Consts.fixDirectoryPath(args[i]);
            }

            // set version name
            if (args[i].equals("--version") && !args[++i].startsWith("--") ){
                mVersionName = args[i] ;
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

            // get predefined generate date
            if (args[i].equals("--date") && !args[++i].startsWith("--") ){
                String dateInput = args[i];
                SimpleDateFormat sdf =  new SimpleDateFormat("yyyy-MM-dd");
                
                Date date;
                try {
                    date = sdf.parse(dateInput);
                    mSourceDataLastModifyDate = date.getTime();
                } catch (ParseException e){
                    Logger.w(TAG, "Wrong data format. Use yyyy-MM-dd");
                    System.exit(1);
                }
            }
        }

        // check parameter 'dir'
        if (mVersionDir == null || mVersionDir.length() == 0) {
            Logger.w(TAG, "parseArgs(), missing argument 'dir'");
            System.exit(1);
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
    }

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

        // check if we need coastline
        if (hasArgAction(cmdActions, Action.MERGE) ||
                hasArgAction(cmdActions, Action.GENERATE)){
            addAction(Action.COASTLINE);
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
            //add merge
            addAction(Action.MERGE);

            addAction(Action.GENERATE);
            addAction(Action.COMPRESS);
        }

        // handle upload action
        if (cmdActions.contains("u")){
            addAction(Action.UPLOAD);
            addAction(Action.CREATE_XML);
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
                        + "u \tupload data to amazon server and crate xml definiton file");
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
        // path for generated output XML
        outputXml = Consts.DIR_BASE + "_result" + Consts.FILE_SEP + getVersionDir() + "maps.xml";
        htmlMapPath = Consts.DIR_BASE + "_result" + Consts.FILE_SEP + getVersionDir() + "maps.html";
        touristTagMapping = Consts.DIR_BASE + "osmosis" + Consts.FILE_SEP + "tag-mapping-tourist.xml";
        contourTagMapping = Consts.DIR_BASE + "osmosis" + Consts.FILE_SEP + "tag-mapping-contour.xml";
        
        // set path to water polygon shape file
        coastlineShpFile = Consts.DIR_BASE
                        + "_coastlines" + Consts.FILE_SEP + "shp"
                        + Consts.FILE_SEP + "land-polygons" + Consts.FILE_SEP + "land_polygons.shp";
                 //+ "_coastlines" + FILE_SEP + "malta.shp";
        
        // osmosisDir
        String osmosisPath = "osmosis" + Consts.FILE_SEP + "bin" + Consts.FILE_SEP;

        // shp2osm script location
        shp2osmDir = "shp2osm" + Consts.FILE_SEP + "shp2osm.py";

        // graphHopper path
        mGraphHopperExe = new File("graphHopper" + Consts.FILE_SEP + "graphhopper.sh").
                getAbsolutePath();

        // TODO why you set mHgtDir variable again here, when it should be defined by argument during a start??
        if (Utils.isSystemUnix()){
            mOsmosisExe = new File(osmosisPath + "osmosis").getAbsolutePath();
            phyghtDir = "/usr/local/bin/phyghtmap";
            mHgtDir = "/mnt/disk1/data/hgt";
            ogr2ogr = "/usr/bin/ogr2ogr";
            pythonDir = "/usr/bin/python";
            mPreShellCommand = "";
            mPostShellCommand = "";
        } else if (Utils.isSystemWindows()){
            mOsmosisExe = new File (osmosisPath + "osmosis.bat").getAbsolutePath();
            phyghtDir = "C:\\Python27\\Scripts\\phyghtmap.exe";
            mHgtDir = "hgt";
            ogr2ogr = "C:\\Program Files\\FWTools2.4.7\\bin\\ogr2ogr.exe";
            pythonDir = "C:\\Python27\\python.exe";
            mPreShellCommand = "c:\\work\\cygwin64\\bin\\bash.exe -c '";
            mPostShellCommand = "'";
        } else {
            mOsmosisExe = new File (osmosisPath + "osmosis").getAbsolutePath();
            phyghtDir = "/usr/local/bin/phyghtmap";
            mHgtDir = "/mnt/disk1/data/hgt";
            ogr2ogr = "/usr/bin/ogr2ogr";
            pythonDir = "/usr/bin/python";
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
            throw new IllegalArgumentException("Invalid Osmosis file:" + getOsmosisExe());
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
