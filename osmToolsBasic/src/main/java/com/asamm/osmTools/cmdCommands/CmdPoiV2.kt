package com.asamm.osmTools.cmdCommands

import com.asamm.osmTools.config.AppConfig
import com.asamm.osmTools.generatorDb.utils.GeomUtils
import com.asamm.osmTools.mapConfig.ItemMap
import com.asamm.osmTools.mbtilesextract.tiles.TileCalculator
import com.asamm.osmTools.utils.Utils
import java.nio.file.Path

class CmdPoiV2 : Cmd(ExternalApp.POI_V2_TOOL) {

    private val tempGeoJsonFile: Path = AppConfig.config.temporaryDir.resolve("poi_db_coverage.geojson")

    /**
     * Initialize PostgreSQL POI database — runs only once per process.
     */
    fun initPoiGeneratorDB() {
        if (dbInitialized) return
        builder()
            .add(AppConfig.config.cmdConfig.poiDbV2Init.toString())
            .execute()
        dbInitialized = true
    }

    companion object {
        private var dbInitialized = false
    }

    /**
     * Generate POI V2 database using tile coverage geometry, shared by both mbtiles and mapsforge.
     */
    fun generatePoiV2Db(map: ItemMap) {
        prepareGeoJsonFileWithCoverage(map)
        Utils.createParentDirs(map.getPathPoiV2Db().toAbsolutePath())
        builder()
            .add(AppConfig.config.cmdConfig.poiDbV2Generator.toString())
            .add(tempGeoJsonFile.toAbsolutePath().toString())
            .add(map.getPathPoiV2Db().toAbsolutePath().toString())
            .execute()
        Utils.deleteFileQuietly(tempGeoJsonFile)
    }

    /**
     * For generation of POI V2 DB for mbtiles, we need to create a GeoJSON file with the coverage of the map
     * in order to pass it to the generator tool.
     * The coverage is created by computing the tiles that intersect with the map polygon
     */
    private fun prepareGeoJsonFileWithCoverage(map: ItemMap) {
        val tiles = TileCalculator().computeTiles(map.pathPolygon.toFile(), 14)
        val geometry = TileCalculator().createTileCoverageGeometry(tiles)
        Utils.writeStringToFile(tempGeoJsonFile.toFile(), GeomUtils.geomToGeoJson(geometry).toString(), false)
    }
}