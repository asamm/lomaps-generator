package com.asamm.osmTools.generatorDb.plugin;

import com.asamm.osmTools.generatorDb.utils.Utils;

import java.io.File;

/**
 * Created by voldapet on 2016-02-22 .
 */
public class ConfigurationCountry extends AConfiguration {


    /**
     * File where to store geom for country
     */
    private File fileGeom;

    /**
     * Define which admin level will be used for generation of country boundary
     * */
    private int adminLevel = 2;


    private String countryName = "";

    public ConfigurationCountry () {
        genType = GenerateType.COUNTRY_BOUNDARY;
    }

    @Override
    public void validate() {

        if (fileGeom == null) {
            throw new IllegalArgumentException(
                    "invalid parameters, missing definition of output file for country boundary geom");
        }
    }


    /**************************************************/
    /*             GETTERS & SETTERS
    /**************************************************/

    public File getFileGeom() {
        return fileGeom;
    }

    public void setFileGeom(String fileGeomPath) {
        this.fileGeom = checkFile(fileGeomPath);
    }

    public int getAdminLevel() {
        return adminLevel;
    }

    public void setAdminLevel(String strAdminLevel) {
        if (!Utils.isNumeric(strAdminLevel)){
            throw new IllegalArgumentException(
                    "admin level parameter: '" + strAdminLevel + "' incorrect. Please define numeric value for country " +
                            "admin level");
        }

        adminLevel = Integer.valueOf(strAdminLevel);
    }

    public String getCountryName() {
        return countryName;
    }

    public void setCountryName(String countryName) {
        if (countryName != null){
            this.countryName = countryName;
        }
    }
}
