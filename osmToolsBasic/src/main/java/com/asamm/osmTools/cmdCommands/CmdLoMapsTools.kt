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
        addCommand("-i")
        addCommand(pathToSource.toString())
        addCommand("-o")
        addCommand(pathToTourist.toString())
        if (AppConfig.config.overwrite) {
            addCommand("--overwrite")
        }
        // TODO remove addCommand("--addwaynodes") for production
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
