/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.asamm.osmTools.cmdCommands

import com.asamm.osmTools.cmdCommands.Cmd.ExternalApp
import com.asamm.osmTools.config.AppConfig
import com.asamm.osmTools.mapConfig.ItemMap
import java.io.IOException

/**
 * Command for generation of tourist ways
 */
class CmdTourist(val map: ItemMap) : Cmd(ExternalApp.LOMAPS_TOOLS) {

    @Throws(IOException::class)
    fun createCmd() {
        addCommand("tourist2ways")
        addCommand("-i")
        addCommand(map.getPathSource())
        addCommand("-o")
        addCommand(map.getPathTourist())
        if (AppConfig.config.overwrite) {
            addCommand("--overwrite")
        }
        // TODO remove addCommand("--addwaynodes") for production
        addCommand("--addwaynodes")
    }
}
