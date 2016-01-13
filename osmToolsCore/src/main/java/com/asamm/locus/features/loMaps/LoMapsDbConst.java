package com.asamm.locus.features.loMaps;

/**
 * Set of table names, names of columns for LoMaps database
 */
public class LoMapsDbConst {

	// POI TABLES

    public static final String TN_FOLDERS_ROOT = "FoldersRoot";
	public static final String TN_FOLDERS_SUB = "FoldersSub";
	public static final String TN_TAG_KEYS = "TagKeys";
	public static final String TN_TAG_VALUES = "TagValues";
	
	public static final String TN_POINTS = "Points";
	public static final String TN_POINTS_ROOT_SUB = "Points_Root_Sub";
	public static final String TN_POINTS_KEY_VALUE = "Points_Key_Value";

    // ADDRESSES TABLES

    public static final String TN_STREETS = "Streets";
    public static final String TN_STREET_IN_CITIES = "Street_In_Cities";
    public static final String TN_CITIES = "Cities";
    public static final String TN_CITIES_NAMES = "Cities_Names";
    public static final String TN_HOUSES = "Houses";
    public static final String TN_HOUSES_REMOVED = "Houses_Removed";
    public static final String TN_POSTCODES = "postcodes";

    // METADATA TABLE

    public static final String TN_META_DATA = "MetaData";

    // NAMES OF COLUMNS

	public static final String COL_ID = "id";
    public static final String COL_HASH = "hash";
    public static final String COL_DATA = "data";
    public static final String COL_TYPE = "type";
	public static final String COL_NAME = "name";
    public static final String COL_NAME_NORM = "namenorm";
    public static final String COL_NUMBER = "number";
    public static final String COL_POST_CODE = "postcode";
    public static final String COL_GEOM = "geom";
    public static final String COL_CENTER_GEOM = "center";
    public static final String COL_VALUE = "value";
    public static final String COL_LANG_CODE = "langcode";
    public static final String COL_STREET_NAME = "streetname";
    public static final String COL_PLACE_NAME = "placename";
    public static final String COL_PARENT_CITY_NAME = "parentcityname";
    public static final String COL_LON = "lon";
    public static final String COL_LAT = "lat";
	
	public static final String COL_POINTS_ID = TN_POINTS + "_" + COL_ID;
	public static final String COL_FOL_ROOT_ID = TN_FOLDERS_ROOT + "_" + COL_ID;
	public static final String COL_FOL_SUB_ID = TN_FOLDERS_SUB + "_" + COL_ID;
	public static final String COL_KEYS_ID = TN_TAG_KEYS + "_" + COL_ID;
	public static final String COL_VALUES_ID = TN_TAG_VALUES + "_" + COL_ID;

    public static final String COL_CITY_ID = "cityid";
    public static final String COL_CITY_PART_ID = "citypartid";
    public static final String COL_STREET_ID = "streetid";
	
	public static final String COL_EMAIL = "email";
	public static final String COL_PHONE = "phone";
	public static final String COL_URL = "url";

    // NAMES OF INDEXES

    public static final String IDX_CITIES_NAMES_NAMENORM = "idx_cities_names_namenorm";
    public static final String IDX_STREETS_NAMENORM = "idx_streets_namenorm";
    public static final String IDX_CITIES_NAMES_CITYID = "idx_cities_names_cityid";
    public static final String IDX_STREETS_IN_CITIES_CITYID = "idx_strees_in_cities_cityid";
    public static final String IDX_HOUSES_STREETID = "idx_houses_streetid";

    // METADATA TABLE KEYS

    public static final String VAL_AREA = "area";
    public static final String VAL_DESCRIPTION = "description";
    public static final String VAL_OSM_DATE = "osmdate";
    public static final String VAL_REGION_ID = "regionid";
    public static final String VAL_VERSION = "version";
    public static final String VAL_DB_POI_VERSION = "versiondbpoi";
    public static final String VAL_DB_ADDRESS_VERSION = "versiondbaddress";

	public static final String DATA_SEPARATOR = "|";

    public enum EntityType {

        UNKNOWN ("U"),

        POIS("P"),

        WAYS("W"),

        RELATION("R");

        // code of entity used in database
        private String mCode;

        EntityType(String code) {
            this.mCode = code;
        }

        public String getCode() {
            return mCode;
        }
    }
}
