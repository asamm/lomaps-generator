/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.asamm.osmTools.server;

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
     public static final String TYPE = "type";
    
    
    public static final String ACTION_DATA_ADD_ITEM_VECTOR = "addItemVector";
    public static final int ITEM_TYPE_VECTOR = 1;
 
    
    // ================== JSON STRINGS ==================
    public static final String JSON_DATA = "jsonData";
    public static final String STATUS = "status";
    public static final String MSG = "msg";
    
    public static final String ACTION_DATA_NAME = "name";
    public static final String ACTION_DATA_USAGE_ID = "usageId";
    public static final String ACTION_DATA_REGION_ID = "regionId";
    public static final String ACTION_DATA_PROVIDER_ID = "providerId";
    public static final String ACTION_DATA_FILE_NAME = "fileName";
    public static final String ACTION_DATA_FILE_SIZE = "fileSize";
    public static final String ACTION_DATA_FILE_SIZE_FINAL = "fileSizeFinal";
    public static final String ACTION_DATA_FILE_MD5_HASH = "fileMD5hash";
    public static final String ACTION_DATA_FILE_PATH = "filePath";
    public static final String ACTION_DATA_FILE_ACTIONS = "fileActions"; // what happen after downloading to client
    public static final String ACTION_DATA_VERSION_NAME = "versionName";
    public static final String ACTION_DATA_VERSION_CODE = "versionCode";
    public static final String ACTION_DATA_DESTINATION_PATH = "clientDestination";
    public static final String ACTION_DATA_SUPPORTED_APK = "supportedApk";
    public static final String ACTION_DATA_ICON = "vectorIcon";
    public static final String ACTION_DATA_IMAGES = "vectorImages";
    
    public static final String ACTION_DATA_DESCRIPTION = "vectorDescription";
    public static final String ACTION_DATA_PREFERED_LANG = "preferedLang";
    public static final String ACTION_DATA_CAN_BE_WELCOME_PRESENT = "canBeWelcomePresent";    
    
    public static final String STORE_DATA_PROVIDER_ID_ASAMMSW = "asammsw";
    public static final String STORE_DATA_USAGE_ID_MAPS_VECTOR = "maps.universal.locus_vector";    
    
  
    /******* LOCUS VERSION TYPE CODE ****************/
    
    public static final int PACKAGE_FREE                = 0;
    public static final int PACKAGE_PRO                 = 1;
    
    // PRO versions
    public static final int PACKAGE_PRO_MOBIROO 	= 2;
    public static final int PACKAGE_PRO_SAMSUNG 	= 3;
    public static final int PACKAGE_PRO_AMAZON          = 4;
    public static final int PACKAGE_PRO_COMPUTER_BILD	= 5;
    
    // FREE versions
    public static final int PACKAGE_FREE_SAMSUNG 	= 1003;
    public static final int PACKAGE_FREE_AMAZON 	= 1004;
    public static final int PACKAGE_FREE_UBINURI 	= 1005;

    // GIS versions
    public static final int PACKAGE_GIS			= 3001;
    public static final int PACKAGE_GIS_T_VEKTOR	= 3002;
    
    // AIR versions
    public static final int PACKAGE_AIR                 = 4001;
    
    // SPECIAL version for testing (act as a Pro)
    public static final int PACKAGE_TESTING 		= 999;
    
    // cracked version
    public static final int PACKAGE_CRACKED             = 2001;
}
