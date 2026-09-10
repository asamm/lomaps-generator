package com.asamm.osmTools.cmdCommands

import com.asamm.osmTools.config.AppConfig
import com.asamm.osmTools.generatorDb.utils.GeomUtils
import com.asamm.osmTools.mapConfig.ItemMap
import com.asamm.osmTools.utils.Utils
import java.nio.file.Path

class CmdPoiV2 : Cmd(ExternalApp.POI_V2_TOOL) {

    private val tempGeoJsonFile: Path = AppConfig.config.temporaryDir.resolve("poi_db_coverage.geojson")

    /**
     * Initialize PostgreSQL POI database — runs only once per process.
     */
    fun initPoiGeneratorDB() {
        initDbOnce
    }

    private fun executeInitDbScript() {
        builder()
            .add(AppConfig.config.cmdConfig.poiDbV2Init.toString())
            .execute()
    }

    companion object {

        /** Init script runs on first access and never again — [lazy] is thread-safe by default. */
        private val initDbOnce: Unit by lazy { CmdPoiV2().executeInitDbScript() }
    }

    /**
     * Generate POI V2 database using tile coverage geometry, shared by both mbtiles and mapsforge.
     */
    fun generatePoiV2Db(map: ItemMap) {
        prepareGeoJsonFileWithCoverage(map)
        Utils.createParentDirs(map.pathPoiV2Db.toAbsolutePath())
        builder()
            .add(AppConfig.config.cmdConfig.poiDbV2Generator.toString())
            .add(tempGeoJsonFile.toAbsolutePath().toString())
            .add(map.pathPoiV2Db.toAbsolutePath().toString())
            .execute()
        Utils.deleteFileQuietly(tempGeoJsonFile)
    }

    private fun prepareGeoJsonFileWithCoverage(map: ItemMap) {
        Utils.writeStringToFile(tempGeoJsonFile.toFile(), GeomUtils.geomToGeoJson(map.tileCoverageGeometry), false)
    }
}
