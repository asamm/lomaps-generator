package com.asamm.osmTools.cmdCommands

import com.asamm.osmTools.config.AppConfig
import com.asamm.osmTools.generatorDb.utils.GeomUtils
import com.asamm.osmTools.mapConfig.ItemMap
import com.asamm.osmTools.mbtilesextract.tiles.TileCalculator
import com.asamm.osmTools.utils.Logger
import com.asamm.osmTools.utils.Utils
import java.nio.file.Path

class CmdPoiV2: Cmd(ExternalApp.POI_V2_TOOL)  {

    val tempGeoJsonFile: Path = AppConfig.config.temporaryDir.resolve("poi_db_coverage.geojson")

    companion object {
        private val TAG: String = CmdPoiV2::class.java.simpleName
    }

    fun initPoiGeneratorDB(){
        addCommand(AppConfig.config.cmdConfig.poiDbV2Init.toString())
        Logger.i(TAG, "Command: " + getCmdLine())
        execute()
    }

    fun generatePoiV2ForMbtiles(map: ItemMap) {

        prepareGeoJsonFileWithCoverage(map);

        addCommand(AppConfig.config.cmdConfig.poiDbV2Generator.toString())
        addCommands(tempGeoJsonFile.toAbsolutePath().toString())
        addCommands(map.getPathPoiV2Db(true).toAbsolutePath().toString())

        // create folder structure for poi db
        Utils.createParentDirs(map.getPathPoiV2Db(true).toAbsolutePath())

        Logger.i(TAG, "Command: " + getCmdLine())
        execute()
        Utils.deleteFileQuietly(tempGeoJsonFile)
    }

    /**
     * Android use original map coverage not mbtiles coverage
     */
    fun generatePoiV2ForMapsforge(map: ItemMap) {

        addCommand(AppConfig.config.cmdConfig.poiDbV2Generator.toString())
        addCommands(map.pathJsonPolygon.toAbsolutePath().toString())
        addCommands(map.getPathPoiV2Db(false).toAbsolutePath().toString())

        // create folder structure for poi db
        Utils.createParentDirs(map.getPathPoiV2Db(false).toAbsolutePath())

        Logger.i(TAG, "Command: " + getCmdLine())
        execute()
    }

    private fun prepareGeoJsonFileWithCoverage(map: ItemMap) {

        // compute tiles for map polygon
        val tiles: List<Triple<Int, Int, Int>> = TileCalculator().computeTiles(map.pathPolygon.toFile(), 14)

        // get exact geometry for tiles in max zoom
        val geometry = TileCalculator().createTileCoverageGeometry(tiles)

        // write geometry to temporary geojson file
        Utils.writeStringToFile(tempGeoJsonFile.toFile(), GeomUtils.geomToGeoJson(geometry).toString(), false)

    }
}