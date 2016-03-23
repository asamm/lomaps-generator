package com.asamm.osmTools.generatorDb.plugin;

import com.asamm.osmTools.generatorDb.utils.Utils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by voldapet on 2016-02-22 .
 */
public class ConfigurationCountry extends AConfiguration {



    /**
     * Define which admin level will be used for generation of country boundary
     * */
    private int adminLevel = 2;


    /**
     * Definition of coutries and it files with geometry to be created
     */
    private List<CountryConf> countriesConf = new ArrayList<>();


    public ConfigurationCountry () {
        genType = GenerateType.COUNTRY_BOUNDARY;
    }

    @Override
    public void validate() {

        for (CountryConf countryConf : countriesConf){
            if (countryConf.fileGeom == null) {
                throw new IllegalArgumentException(
                        "invalid parameters, missing definition of output file for country boundary geom" +
                                " for country: " + countryConf.countryName);
            }
        }
    }


    /**************************************************/
    /*             GETTERS & SETTERS
    /**************************************************/


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



    public List<CountryConf> getCountriesConf() {
        return countriesConf;
    }

    public void setCountriesConf(List<CountryConf> countriesConf) {
        this.countriesConf = countriesConf;
    }



    /**************************************************/
    /*        COUNTRY CONFIGURATION CLASS
    /**************************************************/

    public static class CountryConf {

        /**
         * File where to store geom for country
         */
        File fileGeom;

        String countryName = "";

        public CountryConf (String countryName, String fileGeomPath){

            setCountryName(countryName);
            this.fileGeom = checkFile(fileGeomPath);
        }

        public File getFileGeom() {
            return fileGeom;
        }

        public String getCountryName() {
            return countryName;
        }

        public void setCountryName(String countryName) {
            if (countryName != null){
                this.countryName = countryName;
            }
        }

        @Override
        public int hashCode (){
            int hash = 1;
            hash = hash * 31 + fileGeom.hashCode();
            hash = hash * 31 + countryName.hashCode();
            return hash;
        }
    }
}
