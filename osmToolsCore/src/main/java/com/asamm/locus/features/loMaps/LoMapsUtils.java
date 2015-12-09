package com.asamm.locus.features.loMaps;

/**
 * Created by voldapet on 2015-12-09 .
 *
 */
public class LoMapsUtils {



    /**************************************************/
    /*          SERIALIZED HOUSE DATA
    /**************************************************/

    /**
     * Test if serialized house data contains house number
     * @param bHeader first byte of serialized house
     * @return true is data contains information about house number
     */
    public static boolean isHouseNumberDefined (byte bHeader){
        if (((bHeader >> 0) & 1) == 1){
            return true;
        }
        return false;
    }

    /**
     * Test if serialized house data contains house name
     * @param bHeader first byte of serialized house
     * @return true is data contains information about house name
     */
    public static boolean isHouseNameDefined (byte bHeader){
        if (((bHeader >> 1) & 1) == 1){
            return true;
        }
        return false;
    }
    /**
     * Test if serialized house data contains post code for house
     * @param bHeader first byte of serialized house
     * @return true is data contains information about post code
     */
    public static boolean isPostcodeIdDefined(byte bHeader){
        if (((bHeader >> 2) & 1) == 1){
            return true;
        }
        return false;
    }

}
