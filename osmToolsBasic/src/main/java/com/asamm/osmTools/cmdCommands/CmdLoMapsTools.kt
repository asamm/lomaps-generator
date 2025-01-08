/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.asamm.osmTools.cmdCommands

import com.asamm.osmTools.config.AppConfig
import com.asamm.osmTools.mapConfig.ItemMap
import com.asamm.osmTools.utils.Logger
import java.nio.file.Path

/**
 * Command for generation of tourist ways
 */
class CmdLoMapsTools() : Cmd(ExternalApp.LOMAPS_TOOLS) {

    private val TAG: String = CmdLoMapsTools::class.java.simpleName

    fun generateTourist(pathToSource: Path, pathToTourist: Path) {
        addCommand("tourist2ways")
        if (AppConfig.config.overwrite) {
            addCommand("--overwrite")
        }
        addCommands("-i", pathToSource.toString())
        addCommands("-o", pathToTourist.toString())

        addCommands("--nodeid", AppConfig.config.touristConfig.nodeId.toString())
        addCommands("--wayid", AppConfig.config.touristConfig.wayId.toString())

        // Do not use it for production commands add nodes of way into the output file. In normal case way nodes are not
        // needed because they are part of original way of original OSM planet file
        //addCommand("--addwaynodes")

        Logger.i(TAG, "Command: " + getCmdLine())

        execute()
        reset()
    }

    /**
     * Run Py OSM Up To Date on specific file
     */
    fun osmUpdate(path: Path) {
        if (AppConfig.config.verbose) {
            addCommand("-v")
        }
        addCommands("osmupdate", "-i", path.toString())

        Logger.i(TAG, "Command: " + getCmdLine())
        execute()
        reset()
    }
}
