package com.asamm.osmTools.generatorDb.plugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by voldapet on 2016-02-22 .
 */
public class ConfigurationCountry extends AConfiguration {


    /** Define location for created country boundary geometry*/
    public enum StorageType {
        /**
         * Boundary is store as GeoJson file
         */
        GEOJSON,

        /**
         * Boundary is send into Locus store geo databse
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
            if (countryConf.dataStoreRegionId == null) {
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

        /*
         * Read able name of country to generate borders
         */
        String countryName = "";

        /*
         * Datastore id of parent region
         */
        String dataStoreParentRegionId = "";

        /*
         * Datastore id of region to get it's boundary
         */
        String dataStoreRegionId = "";


        public CountryConf (String countryName, String dataStoreParentRegionId, String dataStoreRegionId){

            setCountryName(countryName);
            setDataStoreRegionId(dataStoreRegionId);
            setDataStoreParentRegionId(dataStoreParentRegionId);
        }


        public CountryConf (String countryName, String dataStoreParentRegionId, String storeRegionCode, String fileGeomPath){

            setCountryName(countryName);
            setDataStoreRegionId(dataStoreRegionId);
            setDataStoreRegionId(storeRegionCode);
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

        public String getDataStoreRegionId() {
            return dataStoreRegionId;
        }

        public void setDataStoreRegionId(String dataStoreRegionId) {
            if (dataStoreRegionId != null){
                this.dataStoreRegionId = dataStoreRegionId;
            }
        }

        public String getDataStoreParentRegionId() {
            return dataStoreParentRegionId;
        }

        public void setDataStoreParentRegionId(String dataStoreParentRegionId) {
            if (dataStoreParentRegionId != null){
                this.dataStoreParentRegionId = dataStoreParentRegionId;
            }
        }

        @Override
        public int hashCode (){
            int hash = 1;
            hash = hash * 31 + dataStoreRegionId.hashCode();
            hash = hash * 31 + dataStoreParentRegionId.hashCode();
            hash = hash * 31 + countryName.hashCode();
            return hash;
        }
    }
}
