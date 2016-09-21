package com.asamm.osmTools.generatorDb.plugin;

import com.asamm.osmTools.generatorDb.utils.Utils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by voldapet on 2016-02-22 .
 */
public class ConfigurationCountry extends AConfiguration {


    /** Where will be created country boundaries stored*/
    public enum StorageType {
        /**
         * Boundary is store as GeoJson file
         */
        GEOJSON,

        /**
         * Boundary is send into Locuc store geo databse
         */
        GEO_DATABASE
    };



    /**
     * Definition of coutries and it files with geometry to be created
     */
    private List<CountryConf> countriesConf = new ArrayList<>();

    private StorageType storageType;

    public ConfigurationCountry () {
        genType = GenerateType.COUNTRY_BOUNDARY;
    }

    @Override
    public void validate() {

        for (CountryConf countryConf : countriesConf){
            if (countryConf.storeRegionCode == null) {
                throw new IllegalArgumentException(
                        "invalid parameters, missing definition of mapregionId: " + countryConf.countryName);
            }
            if (storageType == StorageType.GEOJSON){
                if (countryConf.fileGeom == null) {
                    throw new IllegalArgumentException(
                            "invalid parameters, missing definition of output file for country boundary geom" +
                                    " for country: " + countryConf.countryName);
                }
            }
        }

    }


    /**************************************************/
    /*             GETTERS & SETTERS
    /**************************************************/

    public List<CountryConf> getCountriesConf() {
        return countriesConf;
    }

    public void setCountriesConf(List<CountryConf> countriesConf) {
        this.countriesConf = countriesConf;
    }

    public StorageType getStorageType() {
        return storageType;
    }

    public void setStorageType(StorageType storageType) {
        this.storageType = storageType;
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

        String storeRegionCode = "";

        public CountryConf (String countryName, String storeRegionCode){

            setCountryName(countryName);
            setStoreRegionCode(storeRegionCode);
        }


        public CountryConf (String countryName, String storeRegionCode, String fileGeomPath){

            setCountryName(countryName);
            setStoreRegionCode(storeRegionCode);
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

        public String getStoreRegionCode() {
            return storeRegionCode;
        }

        public void setStoreRegionCode(String storeRegionCode) {
            if (storeRegionCode != null){
                this.storeRegionCode = storeRegionCode;
            }
        }

        @Override
        public int hashCode (){
            int hash = 1;
            hash = hash * 31 + storeRegionCode.hashCode();
            hash = hash * 31 + countryName.hashCode();
            return hash;
        }
    }
}
