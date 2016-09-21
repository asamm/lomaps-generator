/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.asamm.store;

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


    /*****************************************************************
     *      LOCUS STORE GEOCODING DATABASE CONST
     * *************************************************************/

    public static final String TN_GEO_REGION = "geo_region";
    public static final String TN_GEO_REGION_NAME = "geo_region_name";


    // NAMES OF COLUMNS

    public static final String COL_ID = "id";
    public static final String COL_OSM_ID = "osmid";
    public static final String COL_OSM_DATA_TYPE = "osmtype";
    public static final String COL_DATA = "data";
    public static final String COL_TYPE = "type";
    public static final String COL_GEOM = "geom";
    public static final String COL_CENTER_GEOM = "center";
    public static final String COL_VALUE = "value";
    public static final String COL_LANG_CODE = "langcode";
    public static final String COL_NAME = "name";
    public static final String COL_NAME_NORM = "namenorm";
    public static final String COL_PLACE_NAME = "placename";
    public static final String COL_PARENT_CITY_ID = "parentcityid";
    public static final String COL_LON = "lon";
    public static final String COL_LAT = "lat";
    public static final String COL_TIMESTAMP = "timestamp";

    public static final String COL_REGION_ID = "region_id";
    public static final String COL_STORE_REGION_ID = "store_region_id";



}
