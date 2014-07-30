package com.asamm.locus.data.spatialite;

public class DbPoiConst {

	public static final String TN_FOLDERS_ROOT = "FoldersRoot";
	public static final String TN_FOLDERS_SUB = "FoldersSub";
	public static final String TN_TAG_KEYS = "TagKeys";
	public static final String TN_TAG_VALUES = "TagValues";
	
	public static final String TN_POINTS = "Points";
	public static final String TN_POINTS_ROOT_SUB = "Points_Root_Sub";
	public static final String TN_POINTS_KEY_VALUE = "Points_Key_Value";
	
	public static final String COL_ID = "id";
	public static final String COL_NAME = "name";
	
	public static final String COL_POINTS_ID = TN_POINTS + "_" + COL_ID;
	public static final String COL_FOL_ROOT_ID = TN_FOLDERS_ROOT + "_" + COL_ID;
	public static final String COL_FOL_SUB_ID = TN_FOLDERS_SUB + "_" + COL_ID;
	public static final String COL_KEYS_ID = TN_TAG_KEYS + "_" + COL_ID;
	public static final String COL_VALUES_ID = TN_TAG_VALUES + "_" + COL_ID;
	
	public static final String COL_EMAIL = "email";
	public static final String COL_PHONE = "phone";
	public static final String COL_URL = "url";
	
	public static final String DATA_SEPARATOR = "|";
}
