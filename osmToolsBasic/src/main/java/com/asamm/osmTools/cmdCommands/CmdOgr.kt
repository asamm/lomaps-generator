package com.asamm.osmTools.cmdCommands

import com.asamm.osmTools.config.AppConfig
import com.asamm.osmTools.mapConfig.ItemMap

class CmdOgr : Cmd(ExternalApp.OGR2OGR) {

    fun clipGlobalLandPolyToMapBounds(map: ItemMap) {
        require(AppConfig.config.coastlineConfig.landPolygonShp.toFile().exists()) {
            "Shapefile with world polygons ${AppConfig.config.coastlineConfig.landPolygonShp} does not exist"
        }
        // ogr2ogr -clipsrc <minLon> <minLat> <maxLon> <maxLat> <output.shp> <input.shp> -skipfailures
        builder()
            .add("-clipsrc")
            .add(
                map.boundary.minLon.toString(),
                map.boundary.minLat.toString(),
                map.boundary.maxLon.toString(),
                map.boundary.maxLat.toString()
            )
            .add(map.pathShp.toString())
            .add(AppConfig.config.coastlineConfig.landPolygonShp.toString())
            .add("-skipfailures")
            .execute()
    }
}