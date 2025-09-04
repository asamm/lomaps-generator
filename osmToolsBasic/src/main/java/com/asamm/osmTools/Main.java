/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.asamm.osmTools;

import com.asamm.osmTools.generator.GenLoMaps;
import com.asamm.osmTools.generator.GenStoreRegionDB;
import com.asamm.osmTools.utils.*;

import java.util.logging.Handler;
import java.util.logging.Logger;

/**
 * @author volda
 */
public class Main {

    private static final String TAG = Main.class.getSimpleName();

    public static final Logger LOG = com.asamm.osmTools.utils.Logger.create();
    public static final MyLogger mySimpleLog
            = new MyLogger(Consts.DIR_LOGS + Consts.FILE_SEP + "osm2vec_simple.log");

}
