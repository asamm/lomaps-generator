package com.asamm.osmTools.residential

import com.asamm.osmTools.config.AppConfig
import com.asamm.osmTools.overviewMap.DataSource
import com.asamm.osmTools.overviewMap.LayerDefinition
import com.asamm.osmTools.overviewMap.reader.GpkgFeatureReader
import com.asamm.osmTools.pbf.OsmPbfWriter
import com.asamm.osmTools.utils.Logger
import java.nio.file.Path

object ResidentialBuilder {

    private const val TAG = "ResidentialBuilder"

    private const val ATTR_RESIDENTIAL = "lm_residential"
    private const val OSM_TAG_LANDUSE = "lm_landuse"

    /**
     * Reads residential polygon data from the configured GeoPackage and writes it as an OSM PBF
     * to [outputPath].  The `lm_residential` attribute (`residential_city` / `residential_village`)
     * is mapped to the `lm_landuse` OSM tag with the same value.
     */
    fun buildResidentialPbf(outputPath: Path) {
        val cfg = AppConfig.config.residentialConfig

        if (!cfg.sourceGpkg.toFile().exists()) {
            throw IllegalStateException("Residential GPKG not found: ${cfg.sourceGpkg}")
        }

        val layerDef = LayerDefinition(
            layerName = cfg.layerName,
            source = DataSource.GPKG,
            minZoom = 10,
            maxZoom = 14,
            attributeMapper = { attrs ->
                val value = attrs.get(ATTR_RESIDENTIAL)?.toString()?.takeIf { it.isNotBlank() }
                if (value != null) mapOf(OSM_TAG_LANDUSE to value) else emptyMap()
            },
        )

        Logger.i(TAG, "Reading residential areas from GPKG layer '${cfg.layerName}': ${cfg.sourceGpkg}")
        val features = GpkgFeatureReader().readFeatures(cfg.sourceGpkg, layerDef)
            .filter { it.osmTags.containsKey(OSM_TAG_LANDUSE) }

        Logger.i(TAG, "Read ${features.size} residential features, writing PBF: $outputPath")

        OsmPbfWriter(
            outputPath = outputPath,
            startNodeId = cfg.startNodeId,
            startWayId = cfg.startWayId,
            startRelationId = cfg.startWayId,
        ).write(features)

        Logger.i(TAG, "Residential PBF complete: $outputPath")
    }
}