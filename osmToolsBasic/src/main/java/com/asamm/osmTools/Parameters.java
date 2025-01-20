/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.asamm.osmTools;

import com.asamm.osmTools.config.Action;
import com.asamm.osmTools.config.AppConfig;
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

    // date of last map modifications which is defined by version name
    private static long mSourceDataLastModifyDate;

    // DIRECTORY PARAMETERS

    public static long touristWayId     = 16000000000000L;
    // by this variable decide if print for all REF number for the routes or only the REF for the most important
    // (international, national, regional...) route
    public static final boolean printHighestWay = false;

    // the list of bicycle network type
    public static  Hashtable<String,Integer> bycicleNetworkType = new Hashtable<>();
    public static  Hashtable<String,Integer> hikingNetworkType = new Hashtable<>();
    public static  ArrayList<String> hikingColourType;

    // the list of values of "state" tag that represents not active tourist route (such route is not add into tourist ways)
    public static List<String> invalidStatesForTouristRoute = Arrays.asList("proposed", "disused", "removed", "abandoned") ;

    // DEFINED PARAMETERS FROM ARGUMENTS
    public static long getSourceDataLastModifyDate() {
        return mSourceDataLastModifyDate;
    }

}
