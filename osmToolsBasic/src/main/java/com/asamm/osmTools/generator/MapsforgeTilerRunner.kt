package com.asamm.osmTools.generator

import com.asamm.mapsforge.writer.batchextract.BatchExtractor
import com.asamm.mapsforge.writer.batchextract.BatchOptions
import com.asamm.mapsforge.writer.batchextract.ValidatedBatchConfig
import com.asamm.mapsforge.writer.batchextract.ValidatedRegion
import com.asamm.mapsforge.writer.config.BoundingBox
import com.asamm.mapsforge.writer.config.MapWriterConfig
import com.asamm.mapsforge.writer.config.ZoomIntervalConfig
import com.asamm.mapsforge.writer.write.runWriter
import com.asamm.osmTools.config.Action
import com.asamm.osmTools.config.AppConfig
import com.asamm.osmTools.mapConfig.ItemMap
import com.asamm.osmTools.mapConfig.MapSource
import com.asamm.osmTools.utils.Logger
import com.asamm.osmTools.utils.MercatorUtils
import com.asamm.osmTools.utils.Utils
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.absolute

object MapsforgeTilerRunner {

    private val TAG = MapsforgeTilerRunner::class.java.simpleName

    @JvmStatic
    fun generatePlanetMap(map: ItemMap) {

        val bbox: BoundingBox? = if (Utils.isLocalDEV()) {
            null
        } else {
            BoundingBox(
                minLat = -MercatorUtils.WEB_MERCATOR_MAX_LAT,
                minLon = -180.0,
                maxLat = MercatorUtils.WEB_MERCATOR_MAX_LAT,
                maxLon = 180.0,
            )
        }

        val config = MapWriterConfig(
            input = map.pathSource,
            output = map.pathMapsforgeGenerate,
            bbox = bbox,
            tagConfFile = AppConfig.config.mapsforgeConfig.tagMapping.toAbsolutePath(),
            labelPosition = true,
            simplificationFactor = 0.5,
            bboxEnlargement = 5,
            comment = AppConfig.config.mapsforgeConfig.mapDescription,
            threads = Runtime.getRuntime().availableProcessors(),
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
            threads = Runtime.getRuntime().availableProcessors(),
            zoomIntervalConfig = ZoomIntervalConfig.parse("3,1,4,8,5,9"),
            nodeMapType = "sparsearray",
            workDir = AppConfig.config.temporaryDir,
        )
        runWriter(config)
    }

    /**
     * Extracts per-country maps from a planet-scale mapsforge map using batch extraction.
     *
     * Every [ItemMap] in [mMapSource] that has the [Action.GENERATE_MAPSFORGE] action is
     * is extracted (in a single-pass)
     */
    @JvmStatic
    fun extractFromPlanetMap(pathPlanetMap: Path, mMapSource: MapSource) {
        val regions = mMapSource.getAllMaps
            .filter { map -> !map.isPlanet && map.hasAction(Action.GENERATE_MAPSFORGE) }
            .filter { map ->
                if (!AppConfig.config.overwrite && map.pathMapsforgeGenerate.toFile().exists()) {
                    Logger.i(TAG, "Mapsforge map already exists, skipping: ${map.pathMapsforgeGenerate}")
                    false
                } else {
                    true
                }
            }
            .mapNotNull { map ->
                val polygon = map.pathPolygon.toAbsolutePath()
                if (!polygon.toFile().canRead()) {
                    Logger.w(TAG, "Polygon not found for '${map.name}', skipping: $polygon")
                    return@mapNotNull null
                }
                val output = map.pathMapsforgeGenerate.toAbsolutePath()
                Files.createDirectories(output.parent)
                ValidatedRegion(polygon = polygon, output = output, comment = null)
            }

        if (regions.isEmpty()) {
            Logger.i(TAG, "No regions to extract from planet map.")
            return
        }

        Logger.i(TAG, "Batch extract: ${regions.size} regions from $pathPlanetMap")

        BatchExtractor.extractAll(
            ValidatedBatchConfig(
                source = pathPlanetMap.toAbsolutePath(),
                regions = regions,
                options = BatchOptions(),
            )
        )
    }
}