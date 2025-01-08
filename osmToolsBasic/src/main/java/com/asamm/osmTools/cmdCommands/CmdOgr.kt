/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.asamm.osmTools.cmdCommands

import com.asamm.osmTools.config.AppConfig
import com.asamm.osmTools.mapConfig.ItemMap
import com.asamm.osmTools.utils.Logger

/**
 *
 * @author volda
 */
class CmdOgr : Cmd(ExternalApp.OGR2OGR) {

    private val TAG: String = CmdOgr::class.java.simpleName

    fun clipGlobalLandPolyToMapBounds(map: ItemMap) {
        // test if shpfile for extracting exists

        require(AppConfig.config.coastlineConfig.landPolygonShp.toFile().exists()) {
            ("Shapefile with world polygons "
                    + AppConfig.config.coastlineConfig.landPolygonShp + " does not exist")
        }

        //ogr2ogr -clipsrc 14.0 35.7 14.66 36.2   malta.shp water_polygons.shp
        addCommand("-clipsrc")

        addCommand(map.boundary.minLon.toString())
        addCommand(map.boundary.minLat.toString())
        addCommand(map.boundary.maxLon.toString())
        addCommand(map.boundary.maxLat.toString())
        addCommand(map.pathShp.toString())
        addCommand(AppConfig.config.coastlineConfig.landPolygonShp.toString())
        addCommand("-skipfailures")

        Logger.i(TAG, "Command: " + getCmdLine())
        execute()
        reset()
    }
}
