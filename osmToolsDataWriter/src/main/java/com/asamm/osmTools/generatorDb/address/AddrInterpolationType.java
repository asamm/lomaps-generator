package com.asamm.osmTools.generatorDb.address;

import com.asamm.osmTools.generatorDb.utils.Utils;

/**
* Created by voldapet on 2015-11-20 .
*/
public enum AddrInterpolationType {
    ALL(1), EVEN(2), ODD(2), ALPHABETIC(1), INTEGER(1), NONE(1);

    /** Step in interpolation */
    private int step;

    AddrInterpolationType(int step){
        this.step = step;
    }

    /**
     * Find proper enum based on value of tag addr:interpolation
     * @param value content of tag addr:interpolation
     * @return type of interpolation or None if interpolation value is unknown or not defined
     */
    public static AddrInterpolationType fromValue(String value){

        if (value == null){
            return NONE;
        }

        if (Utils.isInteger(value)){
            return INTEGER;
        }
        for(AddrInterpolationType ait : values()) {
            if (ait.name().equalsIgnoreCase(value)) {
                return ait;
            }
        }
        return NONE;
    }

    public int getStep (){
        return step;
    }
}
