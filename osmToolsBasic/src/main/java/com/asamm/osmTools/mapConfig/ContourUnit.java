package com.asamm.osmTools.mapConfig;

import com.asamm.osmTools.utils.Logger;

/**
 * Type of genation of contour lines in meter or feet
 */
public enum ContourUnit {

    UNDEFINED(""),
    METER("m"),
    FEET("f");


    private static final String TAG = ContourUnit.class.getSimpleName();
    private final String unitCode;

    ContourUnit(String unitCode) {
        this.unitCode = unitCode;
    }

    public String getUnitCode() {
        return unitCode;
    }

    public static ContourUnit getFromValue(String unitCode) {
        for (ContourUnit c : ContourUnit.values()) {
            if (c.getUnitCode().equals(unitCode)) {
                return c;
            }
        }
        // no match, log warning and return METER type as fallback
        Logger.w(TAG, "No match for contour unit code: " + unitCode + ". Use METER as fallback");
        return METER;
    }
}
