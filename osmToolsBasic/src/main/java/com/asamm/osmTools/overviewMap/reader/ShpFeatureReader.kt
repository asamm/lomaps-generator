package com.asamm.osmTools.overviewMap.reader

import com.asamm.osmTools.overviewMap.FeatureAttributes
import com.asamm.osmTools.overviewMap.LayerDefinition
import com.asamm.osmTools.pbf.OsmFeature
import com.asamm.osmTools.utils.Logger
import org.geotools.data.shapefile.ShapefileDataStore
import org.geotools.geometry.jts.JTS
import org.geotools.referencing.CRS
import org.geotools.referencing.crs.DefaultGeographicCRS
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.operation.overlayng.OverlayNG
import org.locationtech.jts.operation.overlayng.OverlayNGRobust
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * Reads features from ESRI Shapefiles using GeoTools.
 */
class ShpFeatureReader {

    companion object {
        private const val TAG = "ShpFeatureReader"
    }

    /**
     * Reads all features from SHP files in [shpDir] matching the given [layerDef].
     * The layer name is used to find the corresponding .shp file.
     */
    fun readFeatures(shpDir: Path, layerDef: LayerDefinition): List<OsmFeature> {
        // Find SHP file matching the layer name
        val shpFile = findShpFile(shpDir, layerDef.layerName)
        if (shpFile == null) {
            Logger.w(TAG, "SHP file not found for layer '${layerDef.layerName}' in $shpDir")
            return emptyList()
        }

        return readFromFile(shpFile, layerDef)
    }

    /**
     * Reads features from a specific SHP file.
     */
    fun readFromFile(shpFile: Path, layerDef: LayerDefinition): List<OsmFeature> {

        val features = mutableListOf<OsmFeature>()
        val store = ShapefileDataStore(shpFile.toUri().toURL())

        try {
            store.charset = StandardCharsets.UTF_8

            val source = store.featureSource
            val crs = source.schema.coordinateReferenceSystem

            // Determine transform to WGS84 if needed
            val transform = if (crs != null && !CRS.equalsIgnoreMetadata(crs, DefaultGeographicCRS.WGS84)) {
                CRS.findMathTransform(crs, DefaultGeographicCRS.WGS84, true)
            } else {
                null
            }

            val iterator = source.features.features()
            try {
                while (iterator.hasNext()) {
                    val feature = iterator.next()

                    // Apply filter if defined — wrap SimpleFeature in a FeatureAttributes adapter
                    // so the lambda remains independent of the GeoTools API
                    if (layerDef.filter != null) {
                        val attrs = FeatureAttributes { name -> feature.getAttribute(name) }
                        if (!layerDef.filter.invoke(attrs)) continue
                    }

                    var geometry = feature.defaultGeometry as? Geometry ?: continue

                    // Transform to WGS84 if needed
                    if (transform != null) {
                        geometry = JTS.transform(geometry, transform)
                    }

                    // Repair only invalid geometries
                    if (!geometry.isValid) {
                        geometry = geometry.buffer(0.0)
                        if (geometry.isEmpty) continue
                    }

//                    // Clip to Web Mercator valid bounds o
//                    if (!MERCATOR_BOUNDS_GEOM.envelopeInternal.contains(geometry.envelopeInternal)) {
//                        geometry = OverlayNGRobust.overlay(geometry, MERCATOR_BOUNDS_GEOM, OverlayNG.INTERSECTION)
//                        if (geometry.isEmpty) continue
//                    }

                    // Build OSM tags — wrap SimpleFeature in FeatureAttributes adapter
                    val tags = buildMap {
                        putAll(layerDef.staticTags)
                        put(TAG_NE_MIN_ZOOM, layerDef.minZoom.toString())
                        put(TAG_NE_MAX_ZOOM, layerDef.maxZoom.toString())
                        if (layerDef.attributeMapper != null) {
                            val attrs = FeatureAttributes { name -> feature.getAttribute(name) }
                            layerDef.attributeMapper.invoke(attrs)?.let { putAll(it) }
                        }
                    }

                    features.add(OsmFeature(geometry, tags, layerDef.layerName))
                }
            } finally {
                iterator.close()
            }
        } finally {
            store.dispose()
        }

        Logger.i(TAG, "Read ${features.size} features from SHP: $shpFile")
        return features
    }

    /** Finds the .shp file in [shpDir] that filename matches the given [layerName]. Returns null if not found. */
    private fun findShpFile(shpDir: Path, layerName: String): Path? {
        // Try exact match first
        val exact = shpDir.resolve("$layerName.shp")
        if (Files.exists(exact)) return exact

        // Search recursively for matching .shp file
        return Files.walk(shpDir).use { stream ->
            stream.filter { path ->
                val name = path.fileName.toString()
                name.equals("$layerName.shp", ignoreCase = true)
            }.findFirst().orElse(null)
        }
    }
}