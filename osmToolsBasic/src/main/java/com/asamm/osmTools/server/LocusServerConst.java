/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.asamm.osmTools.server;

import java.util.HashMap;

/**
 *
 * @author voldapet
 */
public class LocusServerConst {
    
    public static final String PROTOCOL = "HTTP";
    //public static final String HOST = "localhost:8888";
    public static final String HOST = "locus-map.appspot.com";
    //public static final String HOST = "locus-store-dev.appspot.com";
    public static final String servletUrl = "store";


   
    // servlet relative URL - users 
    public static final String SECTION_DATA = "data";
    // URL parameter for main request action
    
    public static final String ACTION = "action";
//    public static final String ACTION_DATA_CREATE_ITEM = "createItem";
//    public static final String ACTION_DATA_UPDATE_ITEM = "updateItem";

    public static final String LOCATIONS = "location";


    // ==================  VECTOR ITEM DEFINITION FOR JSON UPLOAD  ==================

    //public static final int VECTOR_ITEM_TYPE_UNIT = 1;  //define that it is item with file

    //public static final int VECTOR_VERSION_CODE = 125; //
    //public static final boolean VECTOR_CAN_BE_WELCOME_PRESENT = true;

   // public static final long[] VECTOR_IMAGE_IDS = new long[] {5120937556967424L, 5683887510388736L, 5737979670691840L, 6218248282439680L, 6246837463810048L, 6298375192313856L};
   // public static final long VECTOR_IC0N_ID = 5414151014842368L;

    //public static final String[] VECTOR_USAGE_IDS = new String[] {"maps.universal.locus_vector"};
   // public static final String VECTOR_PROVIDER_ID = "asammsw";

    //public static final int VECTOR_AVAILABLE_FOR = 7; // STORE_ITEM_TIME_PERIOD_YEAR
    //  -----------------------------------------------


    //public static final String VECTOR_DESCRIPTION = "Locus maps work fully offline and are available for the whole world. " +
//            "These maps are placed directly in your device and for this reason you can change their appearance using built-in themes. " +
//            "Long tap on name of downloaded map (Menu > Maps > Vector) will populate dialog " +
//            "where it is possible to change the theme of the map..\n"+
//            "\n<ul></ul>"+
//            "\nAfter purchase of vector maps, follow please these instructions:"+
//            "\n<ol>"+
//            "\n<li>open Menu  - Maps - Offline tab </li>"+
//            "\n<li>select vector map in a list</li> "+
//            "\n</ol>"+
//            "\n<h4>Locus Guarantee</h4>"+
//            "\nLocus Map guarantees one year period when you can repeatedly download one version of your purchased vector map for free. The guarantee does not refer to map updates.";


    // ================== JSON STRINGS ==================
    public static final String JSON_DATA = "jsonData";
    public static final String STATUS = "status";
    public static final String MESSAGE = "message";
    public static final String JSON_ITEMS = "items";

    public static final String LISTING = "listing";
    public static final String NAME = "name";
    public static final String CODE = "code";
    public static final String ICON = "iconId";
    public static final String IMAGE_IDS = "imageIds";
    public static final String LOCOINS = "loCoins";
    public static final String ENABLED = "enabled";
    public static final String ITEM_TYPE = "itemType";
    public static final String AVAILABLE_FOR = "availableFor";
    public static final String DESCRIPTION = "description";
    public static final String PREFERED_LANG = "preferedLang";
    public static final String CAN_BE_WELCOME_PRESENT = "canBeWelcomePresent";
    public static final String ITEM_AREA = "itemArea";

    public static final String VERSION = "version";
    public static final String USAGE_IDS = "usageIds";
    public static final String REGION_IDS = "regionIds";
    public static final String PROVIDER_ID = "providerId";
    public static final String SUPPORTED_APK = "supportedApk";
    public static final String SET_ACTIVE = "setActive";

    public static final String FILE = "file";
    public static final String FILE_NAME = "fileName";
    public static final String FILE_SIZE = "fileSize";
    public static final String FILE_SIZE_FINAL = "fileSizeFinal";
    public static final String FILE_MD5_HASH = "fileMD5hash";
    public static final String FILE_UNPACK = "clientFileUnpack";
    public static final String FILE_DELETE_SOURCE = "clientDeleteSource";
    public static final String FILE_REFRESH_MAPS = "clientRefreshMaps";
    public static final String DESTINATION_PATH = "clientDestination";
    public static final String FILE_URL = "url";




    /******* LOCUS VERSION TYPE CODE ****************/
    
    public static final int PACKAGE_FREE                = 0;
    public static final int PACKAGE_PRO                 = 1;
    
    // PRO versions
    public static final int PACKAGE_PRO_MOBIROO 	    = 2;
    public static final int PACKAGE_PRO_SAMSUNG     	= 3;
    public static final int PACKAGE_PRO_AMAZON          = 4;
    public static final int PACKAGE_PRO_COMPUTER_BILD	= 5;
    
    // FREE versions
    public static final int PACKAGE_FREE_SAMSUNG 	    = 1003;
    public static final int PACKAGE_FREE_AMAZON 	    = 1004;
    public static final int PACKAGE_FREE_UBINURI 	    = 1005;
    public static final int PACKAGE_FREE_XIAOMI 	    = 1006;

    // GIS versions
    public static final int PACKAGE_GIS			        = 3001;
    public static final int PACKAGE_GIS_T_VEKTOR	    = 3002;
    
    // AIR versions
    public static final int PACKAGE_AIR                 = 4001;
    
    // SPECIAL version for testing (act as a Pro)
    public static final int PACKAGE_TESTING 		    = 999;
    
    // cracked version
    public static final int PACKAGE_CRACKED             = 2001;



    public static final HashMap<Integer, Integer> supportedVersions = new HashMap<Integer, Integer>();
    static {
        supportedVersions.put(LocusServerConst.PACKAGE_FREE, 338);
        supportedVersions.put(LocusServerConst.PACKAGE_PRO, 338);
        supportedVersions.put(LocusServerConst.PACKAGE_TESTING, 338);

        supportedVersions.put(LocusServerConst.PACKAGE_PRO_AMAZON, 338);
        supportedVersions.put(LocusServerConst.PACKAGE_PRO_COMPUTER_BILD, 338);
        supportedVersions.put(LocusServerConst.PACKAGE_PRO_MOBIROO, 338);
        supportedVersions.put(LocusServerConst.PACKAGE_PRO_SAMSUNG, 338);

        supportedVersions.put(LocusServerConst.PACKAGE_FREE_AMAZON, 338);
        supportedVersions.put(LocusServerConst.PACKAGE_FREE_UBINURI, 338);
        supportedVersions.put(LocusServerConst.PACKAGE_FREE_SAMSUNG, 338);
        supportedVersions.put(LocusServerConst.PACKAGE_FREE_XIAOMI, 338);

        supportedVersions.put(LocusServerConst.PACKAGE_GIS, 6);

    }



}
