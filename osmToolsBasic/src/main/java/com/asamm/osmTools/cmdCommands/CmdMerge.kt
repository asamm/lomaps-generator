/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.asamm.osmTools.cmdCommands

import com.asamm.osmTools.mapConfig.ItemMap
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
}
