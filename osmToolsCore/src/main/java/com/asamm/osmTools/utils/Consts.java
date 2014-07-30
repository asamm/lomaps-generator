package com.asamm.osmTools.utils;

import java.io.File;

/**
 * Created by menion on 30.7.14.
 */
public class Consts {

    // MAIN FILES/DIRECTORIES

    public static final String FILE_SEP =
            File.separator;
    public static final String DIR_BASE =
            fixDirectoryPath(new File("").getAbsolutePath());
    public static final String DIR_TMP =
            fixDirectoryPath(DIR_BASE + "_tmp");
    public static final String DIR_LOGS =
            fixDirectoryPath(DIR_BASE + "_logs");

    public static String fixDirectoryPath(String path) {
        if (!path.endsWith(File.separator)) {
            path = path + File.separator;
        }
        return path;
    }

    // BASIC PARAMETERS

    public static final int MAX_ELEVATION = 9000;

    public enum EntityType {
        UNKNOWN, POIS, WAYS
    }
}
