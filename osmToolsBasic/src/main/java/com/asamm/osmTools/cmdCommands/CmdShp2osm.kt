/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.asamm.osmTools.cmdCommands

import com.asamm.osmTools.Parameters
import com.asamm.osmTools.config.AppConfig
import com.asamm.osmTools.mapConfig.ItemMap
import com.asamm.osmTools.utils.Logger
import java.io.File
import java.nio.file.Path
import kotlin.io.path.absolutePathString

/**
 *
 * @author volda
 */
class CmdShp2osm() : Cmd(ExternalApp.LOMAPS_TOOLS) {

    private val TAG = CmdShp2osm::class.java.simpleName

    fun shp2osm(input: Path, output: Path){

        addCommands("shp2osm")
        addCommands("--id", AppConfig.config.coastlineConfig.nodeBorderId++.toString())
        addCommands("--input", input.absolutePathString())
        addCommands("--output",output.absolutePathString())
        Logger.i(TAG, "Command: " + getCmdLine())

        execute()
        reset()
    }
}
