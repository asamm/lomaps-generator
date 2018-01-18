package com.asamm.osmTools.generatorDb.plugin;

import java.io.File;

/**
 * Created by voldapet on 2016-02-22 .
 */
public class ConfigurationAddress extends AConfiguration {

    /*
    * XML file with configuration of admin levels
    */
    protected File fileConfigXml;

    /*
     * File with geometry of country border
     */
    protected File fileCountryGeom;

    /*
     * Id of map for which is created address db
     */
    private String mapId;

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
     * @return File object of XML with definition of admin levels for cities and regions
     */
    public File getFileConfigXml() {
        return this.fileConfigXml;
    }

    public void setFileConfigXml(String pathToXmlfile) {
        this.fileConfigXml = checkFile(pathToXmlfile);
    }

    /**
     * Get file with GeoJson that define border of country for which database is generated
     * @return
     */
    public File getFileCountryGeom() {
        return fileCountryGeom;
    }

    public void setFileCountryGeom(String fileCountryGeom) {
        this.fileCountryGeom = checkFile(fileCountryGeom);
    }


    /**
     * @return the id of map that address is created for
     */
    public String getMapId() {
        return mapId;
    }

    /**
     *
     * @param mapId id of generated map
     */
    public void setMapId(String mapId) {
        this.mapId = mapId;
    }

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
