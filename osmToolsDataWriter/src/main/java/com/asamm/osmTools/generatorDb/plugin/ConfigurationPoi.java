package com.asamm.osmTools.generatorDb.plugin;

import java.io.File;

/**
 * Created by voldapet on 2016-02-22 .
 */
public class ConfigurationPoi extends AConfiguration {

    // source file
    private File fileDb;
    private File fileConfig;

    // testing part
    public boolean testing;
    public int reportFrom;
    public int reportCount;


    public ConfigurationPoi () {
        genType = GenerateType.POI;
    }

    /**
     * After all parameters are loaded and set, confirm loading by this method.
     * It checks all parameters and throw exception in case, something
     * important is missing.
     */
    @Override
    public void validate() {
        if (getFileDatabase() == null) {
            throw new IllegalArgumentException(
                    "invalid parameters, missing database file");
        }

        if (getFileConfig() == null) {
            throw new IllegalArgumentException(
                    "invalid parameters, missing config file");
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
     * Convenience method.
     *
     * @param file the path to the output file
     */
    public void setFileDatabase(String file) {
        this.fileDb = checkFile(file);
    }


    public File getFileConfig() {
        return this.fileConfig;
    }

    public void setFileConfig(String file) {
        this.fileConfig = checkFile(file);
    }
}
