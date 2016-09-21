package com.asamm.osmTools.generatorDb.plugin;

import java.io.File;

/**
 * Created by voldapet on 2016-02-22 .
 */
public class ConfigurationAddress extends ConfigurationGeoCoding {

    /*
     * File to store database address
     */
    private File fileDb;


    /*
     * File with geometry of data boundary (area of map)
     */
    private File fileDataGeom;

       /*
     * Name of country in which is address db
     */
    private String countryName = "";

    public ConfigurationAddress () {
        genType = GenerateType.ADDRESS;
    }

    @Override
    public void validate() {

        if (fileDb == null) {
            throw new IllegalArgumentException(
                    "invalid parameters, missing database file");
        }

        if (fileConfigXml == null){
            throw new IllegalArgumentException(
                    "invalid parameters, config xml file not defined");
        }

        if (fileCountryGeom == null){
            throw new IllegalArgumentException(
                    "invalid parameters, file with country geom not defined");
        }

        if (fileDataGeom == null){
            throw new IllegalArgumentException(
                    "invalid parameters, file with data geom not defined");
        }
    }


    /**************************************************/
    /*             GETTERS & SETTERS
    /**************************************************/

    /**
     * @return the outputFile
     */
    public File getFileDatabase() {
        return this.fileDb;
    }

    /**
     * @param file the path to the output file
     */
    public void setFileDatabase(String file) {
        this.fileDb = checkFile(file);
    }

    /**
     * Get file with geoJson of area of data. Area of map
     * @return
     */
    public File getFileDataGeom() {
        return fileDataGeom;
    }

    public void setFileDataGeom(String fileDataGeom) {
        this.fileDataGeom = checkFile(fileDataGeom);
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
