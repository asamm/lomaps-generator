package com.asamm.osmTools.generatorDb.plugin;

import java.io.File;

public class ConfigurationResidential extends AConfiguration {

    /*
    * XML file with configuration of tag values for transformation
    */
    protected File fileConfigXml;

    /*
     * File with geometry of data boundary (area of map)
     */
    private File fileDataGeom;


    public ConfigurationResidential() {
        genType = GenerateType.ADDRESS;
    }

    @Override
    public void validate() {
    }

    public File getFileConfigXml() {
        return this.fileConfigXml;
    }

    public void setFileConfigXml(String pathToXmlfile) {
        this.fileConfigXml = checkFile(pathToXmlfile);
    }

    public File getFileDataGeom() {
        return fileDataGeom;
    }

    public void setFileDataGeom(String fileDataGeom) {
        this.fileDataGeom = checkFile(fileDataGeom);
    }
}