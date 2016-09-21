package com.asamm.osmTools.generatorDb.plugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by voldapet on 2016-02-22 .
 * Command line parameters that defines parameters for generation of Locus store geocoding database
 */
public class ConfigurationGeoCoding extends AConfiguration {



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

    public ConfigurationGeoCoding() {
        genType = GenerateType.STORE_GEOCODE;
    }

    @Override
    public void validate() {


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


}
