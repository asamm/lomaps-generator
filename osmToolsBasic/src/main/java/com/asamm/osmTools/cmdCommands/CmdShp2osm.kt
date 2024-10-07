/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.asamm.osmTools.cmdCommands

import com.asamm.osmTools.Parameters
import com.asamm.osmTools.config.AppConfig
import com.asamm.osmTools.mapConfig.ItemMap
import java.io.File

/**
 *
 * @author volda
 */
class CmdShp2osm(val map: ItemMap, var input: String, var output: String) : Cmd(ExternalApp.NO_EXTERNAL_APP) {


    init {
        //test if shp2osm.py scripot exist
        require(File(Parameters.getShp2osmDir()).exists()) { "Shp2Osm script in location" + Parameters.getShp2osmDir() + "  does not exist!" }
    }

    fun createCmd() {
        addCommand(AppConfig.config.cmdConfig.pythonPath)
        addCommand(Parameters.getShp2osmDir())
        addCommand("--id")
        addCommand(Parameters.costlineBorderId.toString())
        addCommand("--output")
        addCommand(output)
        addCommand(input)
    }
}
