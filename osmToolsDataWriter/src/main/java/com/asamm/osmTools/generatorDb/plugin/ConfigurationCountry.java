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
        STORE_REGION_DB
    };



    public static final String COUNTRY_CODE_NOT_DEFINED = "NOTDEFINED";

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
         * File where to store geom for country (only for geojson mode)
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

        /*
         * ISO Alpha code
         */
        String regionCode = null;

        public static CountryConf createStoreRegionDbConf (
                String countryName, String dataStoreParentRegionId, String dataStoreRegionId, String regionCode){

            CountryConf countryConf = new CountryConf();

            countryConf.setCountryName(countryName);
            countryConf.setDataStoreRegionId(dataStoreRegionId);
            countryConf.setDataStoreParentRegionId(dataStoreParentRegionId);
            countryConf.setRegionCode(regionCode);

            return countryConf;
        }


        public static CountryConf createGeoJsonConf (
                String countryName, String dataStoreParentRegionId, String dataStoreRegionId, String fileGeomPath){

            CountryConf countryConf = new CountryConf();

            countryConf.setCountryName(countryName);
            countryConf.setDataStoreRegionId(dataStoreRegionId);
            countryConf.setDataStoreParentRegionId(dataStoreParentRegionId);
            countryConf.setFileGeom(fileGeomPath);

            return countryConf;
        }

        /**************************************************/
        /*             GETTERS & SETTERS
        /**************************************************/




        // GEOJSON FILE

        public File getFileGeom() {
            return fileGeom;
        }

        public void setFileGeom(String fileGeomPath) {
            this.fileGeom = checkFile(fileGeomPath);
        }

        // EN COUNTRY NAME

        public String getCountryName() {
            return countryName;
        }

        public void setCountryName(String countryName) {
            if (countryName != null){
                this.countryName = countryName;
            }
        }

        // DATASTORE REGION ID

        public String getDataStoreRegionId() {
            return dataStoreRegionId;
        }

        public void setDataStoreRegionId(String dataStoreRegionId) {
            if (dataStoreRegionId != null){
                this.dataStoreRegionId = dataStoreRegionId;
            }
        }

        // PARENT DATASTORE REGION ID

        public String getDataStoreParentRegionId() {
            return dataStoreParentRegionId;
        }

        public void setDataStoreParentRegionId(String dataStoreParentRegionId) {
            if (dataStoreParentRegionId != null){
                this.dataStoreParentRegionId = dataStoreParentRegionId;
            }
        }

        // REGION CODE
        public String getRegionCode() {
            return regionCode;
        }

        public void setRegionCode(String regionCode) {
            if (regionCode != null && !regionCode.equals(COUNTRY_CODE_NOT_DEFINED)){
                this.regionCode = regionCode;
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
