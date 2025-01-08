/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.asamm.osmTools.cmdCommands

import com.asamm.osmTools.Parameters
import com.asamm.osmTools.mapConfig.ItemMap
import com.asamm.osmTools.utils.Consts
import java.io.File
import java.io.IOException
import java.util.*

/**
 *
 * @author volda
 */
class CmdMerge(val map: ItemMap) : Cmd(ExternalApp.OSMOSIS), CmdOsmosis {
    init {
        // check parameters
        checkFileLocalPath(map)
    }


    /**
     * Create command lines commands to merge standard lomaps
     * @throws IOException
     */
    @Throws(IOException::class)
    fun createCmd() {

        addReadPbf(map.pathSource.toString())

        addReadPbf(map.pathCoastline.toString())
        addMerge()

        // try to add custom data
        addCustomData()

        addReadPbf(map.pathTranform.toString())
        addMerge()

        addBuffer()
        addWritePbf(map.pathMerge.toString(), true)
    }


    @Throws(IOException::class)
    fun createSeaCmd(tmpCoastPath: String, tmpBorderPath: String) {
        addReadXml(tmpCoastPath)
        addSort()
        addReadXml(tmpBorderPath)
        addSort()
        addBuffer()
        addMerge()
        addWritePbf(map.pathCoastline.toString(), true)
    }

    @Throws(IOException::class)
    fun xml2pbf(inputPath: String, outputPath: String) {
        addReadXml(inputPath)
        addSort()
        addWritePbf(outputPath, true)
    }

    fun addMerge() {
        addCommand("--merge")
    }

    /**
     * Some custom map contains custom xml data. For example map4trip. Marge also such data for this maps
     */
    private fun addCustomData() {
        if (map.id != null && map.id == MAP4TRIP_MAP_ID) {
            // add map4trip custom data
            addMap4TripCustomData()
        }
    }

    /**
     * Check if exist any custom map data for Map4trip map and add them into merging
     */
    private fun addMap4TripCustomData() {
        val dataDirF = File(Parameters.getCustomDataDir() + Consts.FILE_SEP + "map4trip")
        require(!(!dataDirF.exists() || !dataDirF.isDirectory)) { "Custom data folder: " + dataDirF.absolutePath + " does not exist" }

        val listOfFiles = dataDirF.listFiles()
        for (file in listOfFiles!!) {
            if (file.name.lowercase(Locale.getDefault()).endsWith("osm.xml")) {
                // filter only OSM XML files
                addReadXml(file.absolutePath)
                addMerge()
            }
        }
    }

    companion object {
        private const val MAP4TRIP_MAP_ID = "czech_republic_map4trip"
    }
}
