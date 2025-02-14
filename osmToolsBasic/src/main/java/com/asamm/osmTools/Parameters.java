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



    // DEFINED PARAMETERS FROM ARGUMENTS
    public static long getSourceDataLastModifyDate() {
        return mSourceDataLastModifyDate;
    }

}
