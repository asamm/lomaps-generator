package com.asamm.osmTools.generator

import com.asamm.mapsforge.writer.config.BoundingBox
import com.asamm.mapsforge.writer.config.MapWriterConfig
import com.asamm.mapsforge.writer.config.ZoomIntervalConfig
import com.asamm.mapsforge.writer.write.runWriter
import com.asamm.osmTools.config.AppConfig
import com.asamm.osmTools.mapConfig.ItemMap
import com.asamm.osmTools.utils.MercatorUtils
import java.nio.file.Path
import kotlin.io.path.absolute

object MapsforgeTilerRunner {

    @JvmStatic
    fun generatePlanetMap(map: ItemMap) {
        val config = MapWriterConfig(
            input = map.pathSource,
            output = map.pathMapsforgeGenerate,
            bbox = BoundingBox(
                minLat = -MercatorUtils.WEB_MERCATOR_MAX_LAT,
                minLon = -180.0,
                maxLat = MercatorUtils.WEB_MERCATOR_MAX_LAT,
                maxLon = 180.0,
            ),
            tagConfFile = AppConfig.config.mapsforgeConfig.tagMapping.toAbsolutePath(),
            labelPosition = true,
            simplificationFactor = 0.5,
            bboxEnlargement = 5,
            comment = AppConfig.config.mapsforgeConfig.mapDescription,
            preferredLanguages = map.prefLang?.takeIf { it.isNotEmpty() }?.split(","),
            nodeMapType = "sparsearray",
            workDir = AppConfig.config.temporaryDir,
        )
        runWriter(config)
    }

    @JvmStatic
    fun generateOverviewMap() {
        val cfg = AppConfig.config.overviewMapConfig
        require(cfg.outputPbf.toFile().exists()) {
            "Overview PBF not found: ${cfg.outputPbf}. Run the overview map build step first."
        }
        val outputMap = Path.of(cfg.outputPbf.toString().replace(".osm.pbf", ".osm.map")).absolute()
        val config = MapWriterConfig(
            input = cfg.outputPbf.absolute(),
            output = outputMap,
            bbox = BoundingBox(
                minLat = -MercatorUtils.WEB_MERCATOR_MAX_LAT,
                minLon = -180.0,
                maxLat = MercatorUtils.WEB_MERCATOR_MAX_LAT,
                maxLon = 180.0,
            ),
            tagConfFile = AppConfig.config.mapsforgeConfig.tagMapping.toAbsolutePath(),
            labelPosition = true,
            simplificationFactor = 0.5,
            bboxEnlargement = 5,
            comment = AppConfig.config.mapsforgeConfig.mapDescription,
            zoomIntervalConfig = ZoomIntervalConfig.parse("3,1,4,8,5,9"),
            nodeMapType = "sparsearray",
            workDir = AppConfig.config.temporaryDir,
        )
        runWriter(config)
    }
}