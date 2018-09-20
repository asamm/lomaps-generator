package com.asamm.osmTools.generatorDb.plugin;

import java.io.File;

/**
 * Created by voldapet on 2016-02-22 .
 * Mainly hold path to transform config xml and path to map boundary
 */
public class ConfigurationTransform extends AConfiguration {

    /*
    * XML file with configuration of tag values for transformation
    */
    protected File fileConfigXml;

    /*
     * File with geometry of data boundary (area of map)
     */
    private File fileDataGeom;


    public ConfigurationTransform() {
        genType = GenerateType.ADDRESS;
    }

    @Override
    public void validate() {

//        if (fileConfigXml == null){
//            throw new IllegalArgumentException(
//                    "invalid parameters, config xml file not defined");
//        }
//
//        if (fileDataGeom == null){
//            throw new IllegalArgumentException(
//                    "invalid parameters, file with data geom not defined");
//        }
    }


    /**************************************************/
    /*             GETTERS & SETTERS
    /**************************************************/


    /**
     * @return File object of XML with definition of tag for residential areas and for lakes
     */
    public File getFileConfigXml() {
        return this.fileConfigXml;
    }

    public void setFileConfigXml(String pathToXmlfile) {
        this.fileConfigXml = checkFile(pathToXmlfile);
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

}
