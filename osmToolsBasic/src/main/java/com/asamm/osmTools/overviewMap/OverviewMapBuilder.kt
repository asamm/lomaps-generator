package com.asamm.osmTools.overviewMap

import com.asamm.osmTools.config.AppConfig
import com.asamm.osmTools.overviewMap.centerline.CenterlineExtractor
import com.asamm.osmTools.overviewMap.reader.GpkgFeatureReader
import com.asamm.osmTools.overviewMap.reader.ShpFeatureReader
import com.asamm.osmTools.overviewMap.writer.OsmPbfWriter
import com.asamm.osmTools.utils.Logger
import org.locationtech.jts.geom.MultiPolygon
import org.locationtech.jts.geom.Polygon
import kotlin.io.path.exists

/**
 * Orchestrates the Natural Earth data pipeline:
 * 1. Download and extract SHP and GeoPackage sources
 * 2. Read features from all configured layers
 * 3. Transform attributes to OSM tags
 * 4. Write output OSM PBF file
 */
class OverviewMapBuilder {

    companion object {
        private const val TAG = "OverviewMapBuilder"
    }

    /**
     * Generate OSM PBF file with simplified data from low zooms that are used fro merging with original OSM planet file
     */
    fun buildOverviewOsmPbf() {
        val cfg = AppConfig.config.overviewMapConfig

        if (cfg.outputPbf.exists() && !AppConfig.config.overwrite) {
            Logger.i(TAG, "Natural Earth PBF already exists, skipping: ${cfg.outputPbf}")
            return
        }

        // 1. Download and extract data sources
        Logger.i(TAG, "================ NATURAL EARTH DATA PREPARATION ================")

        val gpkgPath = OverviewMapDataDownloader.ensureNeGpkg(cfg)
        val shpDir = OverviewMapDataDownloader.ensureBaseMapShp(cfg)
        val ecoregionsDir = OverviewMapDataDownloader.ensureEcoregionsShp(cfg)

        // 2. Read features from all configured layers
        val allFeatures = mutableListOf<OverviewMapFeature>()
        val shpReader = ShpFeatureReader()
        val gpkgReader = GpkgFeatureReader()
        val centerlineExtractor = CenterlineExtractor()

        for (layerDef in OverviewMapLayers.ALL) {
            val features = when (layerDef.source) {
                DataSource.BASE_MAP_SHP -> shpReader.readFeatures(shpDir, layerDef)
                DataSource.GPKG -> gpkgReader.readFeatures(gpkgPath, layerDef)
                DataSource.ECOREGIONS_SHP -> shpReader.readFeatures(ecoregionsDir, layerDef)
            }
            Logger.i(
                TAG,
                "Layer '${layerDef.layerName}': ${features.size} features (zoom ${layerDef.minZoom}-${layerDef.maxZoom})"
            )

            if (layerDef.toCenterLine) {
                // Convert polygon features to centerline LineStrings (parallel per feature)
                val converted = features.parallelStream()
                    .flatMap { feature ->
                        val centerlines = when (val geom = feature.geometry) {
                            is Polygon -> listOfNotNull(centerlineExtractor.extract(geom))
                            is MultiPolygon -> centerlineExtractor.extractAll(geom)
                            else -> {
                                Logger.w(TAG, "Skipping non-polygon geometry for centerline extraction: ${geom.geometryType}")
                                emptyList()
                            }
                        }
                        centerlines.stream().map { line -> feature.copy(geometry = line) }
                    }
                    .toList()
                allFeatures.addAll(converted)
                Logger.i(TAG, "Converted ${features.size} polygons to centerlines")
            } else {
                allFeatures.addAll(features)
            }
        }

        Logger.i(TAG, "Total features: ${allFeatures.size}")

        // 3. Write to OSM PBF
        val writer = OsmPbfWriter(
            outputPath = cfg.outputPbf,
            startNodeId = cfg.startNodeId,
            startWayId = cfg.startWayId,
            startRelationId = cfg.startRelationId,
        )
        writer.write(allFeatures)

        // 4. Clean up extracted directories; ZIPs are kept for future runs
        // TODO uncomment
        //OverviewDataDownloader.deleteExtractedData(cfg)

        Logger.i(TAG, "================ OVERVIEW MAP DATA COMPLETE ================")
    }
}