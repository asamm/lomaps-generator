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

    public static final int VECTOR_ITEM_TYPE_UNIT = 1;  //define that it is item with file

    public static final int VECTOR_VERSION_CODE = 103; //
    public static final boolean VECTOR_CAN_BE_WELCOME_PRESENT = true;

    public static final long[] VECTOR_IMAGE_IDS = new long[] {5120937556967424L, 5683887510388736L, 5737979670691840L, 6218248282439680L, 6246837463810048L, 6298375192313856L};
    public static final long VECTOR_IC0N_ID = 5414151014842368L;

    public static final String[] VECTOR_USAGE_IDS = new String[] {"maps.universal.locus_vector"};
    public static final String VECTOR_PROVIDER_ID = "asammsw";

    public static final int VECTOR_AVAILABLE_FOR = 7; // STORE_ITEM_TIME_PERIOD_YEAR
    //  -----------------------------------------------


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


    // ================== JSON STRINGS ==================
    public static final String JSON_DATA = "jsonData";
    public static final String STATUS = "status";
    public static final String MESSAGE = "message";
    public static final String JSON_ITEMS = "items";

    public static final String ACTION_DATA_NAME = "name";
    public static final String ACTION_DATA_CODE = "code";
    public static final String ACTION_DATA_ICON = "iconId";
    public static final String ACTION_DATA_IMAGE_IDS = "imageIds";
    public static final String ACTION_DATA_LOCOINS = "loCoins";
    public static final String ACTION_DATA_ENABLED = "enabled";
    public static final String ACTION_DATA_ITEM_TYPE = "itemType";
    public static final String ACTION_DATA_AVAILABLE_FOR = "availableFor";
    public static final String ACTION_DATA_DESCRIPTION = "description";
    public static final String ACTION_DATA_PREFERED_LANG = "preferedLang";
    public static final String ACTION_DATA_CAN_BE_WELCOME_PRESENT = "canBeWelcomePresent";
    public static final String ACTION_DATA_ITEM_AREA = "itemArea";

    public static final String ACTION_DATA_VERSION = "version";
    public static final String ACTION_DATA_USAGE_IDS = "usageIds";
    public static final String ACTION_DATA_REGION_IDS = "regionIds";
    public static final String ACTION_DATA_PROVIDER_ID = "providerId";
    public static final String ACTION_DATA_SUPPORTED_APK = "supportedApk";

    public static final String ACTION_DATA_FILE = "file";
    public static final String ACTION_DATA_FILE_NAME = "fileName";
    public static final String ACTION_DATA_FILE_SIZE = "fileSize";
    public static final String ACTION_DATA_FILE_SIZE_FINAL = "fileSizeFinal";
    public static final String ACTION_DATA_FILE_MD5_HASH = "fileMD5hash";
    public static final String ACTION_DATA_FILE_UNPACK = "clientFileUnpack";
    public static final String ACTION_DATA_FILE_DELETE_SOURCE = "clientDeleteSource";
    public static final String ACTION_DATA_FILE_REFRESH_MAPS = "clientRefreshMaps";
    public static final String ACTION_DATA_DESTINATION_PATH = "clientDestination";
    public static final String ACTION_DATA_FILE_URL = "url";




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



}
