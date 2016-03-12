package com.asamm.osmTools.generatorDb.utils;

/**
 * Constants used for generation
 *
 * Created by voldapet on 2016-03-09 .
 */
public final class Const {



    // CITY PARAMETERS

    /**
     * Max value of point distance from original geom for simplification of city bounds
     */
    public static final double CITY_POLYGON_SIMPLIFICATION_DISTANCE = 50;

    // STREET PARAMETERS

    /**
     * Maximal distance between street geoms (theirs envelope) that is accepted that geoms can be part of the same street
     */
    public static final int MAX_DISTANCE_BETWEEN_STREET_SEGMENTS = 300;

    /**
     * Ratio of distance and city radius (test max distance for founded cities
     */
    public static final int MAX_FOUNDED_CITY_DISTANCE_RADIUS_RATIO = 3;

    /**
     * Maximal diagonal length of the envelope of the street
     */
    public static final int MAX_DIAGONAL_STREET_LENGTH = 30000;


    // HOUSE CONTROLLER PARAMETERS

    /**
     * How fare can be unnamed street from house without street name
     */
    public static final int MAX_DISTANCE_UNNAMED_STREET = 200;

    /**
     * Max distance between named street and house that have defined addr:street name
     */
    public static final int MAX_DISTANCE_NAMED_STREET = 400;

    /**
     * How fare can be named street for house that have defined only place name
     */
    public static final int MAX_DISTANCE_PLACENAME_STREET = 500;


    // ADDRESS DATABASE PARAMETERS

    /**
     * Custom language code that mark out local default language
     */
    public static final String DEFAULT_LANG_CODE = "def";


}
