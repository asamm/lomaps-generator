package com.asamm.osmTools.overviewMap

import com.asamm.osmTools.config.AppConfig
import com.asamm.osmTools.overviewMap.reader.GpkgFeatureReader
import com.asamm.osmTools.overviewMap.reader.ShpFeatureReader
import com.asamm.osmTools.overviewMap.writer.OsmPbfWriter
import com.asamm.osmTools.utils.Logger
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

    fun build() {
        val cfg = AppConfig.config.naturalEarthConfig

        if (cfg.outputPbf.exists() && !AppConfig.config.overwrite) {
            Logger.i(TAG, "Natural Earth PBF already exists, skipping: ${cfg.outputPbf}")
            return
        }

        // 1. Download and extract data sources
        Logger.i(TAG, "================ NATURAL EARTH DATA PREPARATION ================")

        val gpkgPath = NaturalEarthDownloader.ensureGpkg(cfg)
        val shpDir = NaturalEarthDownloader.ensureNaturalEarthBaseShp(cfg)

        // 2. Read features from all configured layers
        val allFeatures = mutableListOf<NaturalEarthFeature>()
        val shpReader = ShpFeatureReader()
        val gpkgReader = GpkgFeatureReader()

        for (layerDef in NaturalEarthLayers.ALL) {
            val features = when (layerDef.source) {
                DataSource.BASE_MAP_SHP -> shpReader.readFeatures(shpDir, layerDef)
                DataSource.GPKG -> gpkgReader.readFeatures(gpkgPath, layerDef)
            }
            Logger.i(TAG, "Layer '${layerDef.layerName}': ${features.size} features (zoom ${layerDef.minZoom}-${layerDef.maxZoom})")
            allFeatures.addAll(features)
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
        //NaturalEarthDownloader.deleteExtractedData(cfg)

        Logger.i(TAG, "================ NATURAL EARTH DATA COMPLETE ================")
    }
}