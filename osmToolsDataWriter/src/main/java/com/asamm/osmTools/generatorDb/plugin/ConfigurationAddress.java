package com.asamm.osmTools.generatorDb.plugin;

import com.asamm.osmTools.generatorDb.utils.Utils;

import java.io.File;

/**
 * Created by voldapet on 2016-02-22 .
 */
public class ConfigurationAddress extends AConfiguration {

    /**
     * File to store database address
     */
    private File fileDb;

    /**
     * XML file with configuration of admin levels
     */
    private File fileConfig;

    /**
     * Id of map for which is created address db
     */
    private String mapId;

    public ConfigurationAddress () {
        genType = GenerateType.ADDRESS;
    }

    @Override
    public void validate() {
        if (fileDb == null) {
            throw new IllegalArgumentException(
                    "invalid parameters, missing database file");
        }

        if (fileConfig == null){
            throw new IllegalArgumentException(
                    "invalid parameters, config xml file not defined");
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

    public File getFileConfig() {
        return this.fileConfig;
    }

    public void setFileConfig(String file) {
        this.fileConfig = checkFile(file);
    }
}
