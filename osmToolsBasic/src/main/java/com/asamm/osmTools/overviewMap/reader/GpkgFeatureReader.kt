package com.asamm.osmTools.overviewMap.reader

import com.asamm.osmTools.overviewMap.FeatureAttributes
import com.asamm.osmTools.overviewMap.LayerDefinition
import com.asamm.osmTools.overviewMap.OverviewMapFeature
import com.asamm.osmTools.utils.Logger
import mil.nga.geopackage.GeoPackageManager
import mil.nga.geopackage.features.user.FeatureDao
import mil.nga.geopackage.features.user.FeatureRow
import org.geotools.api.referencing.operation.MathTransform
import org.geotools.geometry.jts.JTS
import org.geotools.referencing.CRS
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.io.WKBReader
import org.locationtech.jts.operation.overlayng.OverlayNG
import org.locationtech.jts.operation.overlayng.OverlayNGRobust
import java.nio.file.Path

// Reads features from a GeoPackage file. Uses mil.nga.geopackage to access WKB, parses with JTS,
// applies CRS transform if needed.
class GpkgFeatureReader {

    companion object {
        private const val TAG = "GpkgFeatureReader"
    }

    /**
     * Reads all features from the specified GeoPackage file and layer definition.
     * Opens the GeoPackage, checks for the existence of the layer, applies optional attribute filtering,
     * transforms geometries to WGS84 if needed, clips to Web Mercator bounds, and builds OSM tag maps.
     *
     * @param gpkgFile Path to the GeoPackage file.
     * @param layerDef Definition of the layer to read, including filters and tag mapping.
     * @return List of OverviewFeature objects representing the features in the layer.
     */
    fun readFeatures(gpkgFile: Path, layerDef: LayerDefinition): List<OverviewMapFeature> {
        val layer = layerDef.layerName

        // Open the GeoPackage file
        return GeoPackageManager.open(true, gpkgFile.toFile()).use { geoPackage ->
            // Check if the requested layer exists
            if (!geoPackage.isFeatureTable(layer)) {
                Logger.w(TAG, "Layer '$layer' not found in GeoPackage: $gpkgFile")
                return@use emptyList()
            }

            val featureDao = geoPackage.getFeatureDao(layer)
            // Compute CRS transform if needed (source → WGS84)
            val transform = resolveCrsTransform(layer, featureDao)
            val wkbReader = WKBReader()

            val cursor = featureDao.queryForAll()
            val features = try {
                buildList {
                    for (row in cursor) {
                        val attrs = row.asFeatureAttributes()
                        // Apply filter if present
                        if (layerDef.filter?.invoke(attrs) == false) {
                            continue
                        }

                        // Read and transform geometry
                        val geometry = row.readGeometry(wkbReader, transform)
                        if (geometry == null) {
                            Logger.d(TAG, "Layer '$layer' contains features with missing geometry in $gpkgFile")
                            continue
                        }

                        // Add feature with tags
                        add(OverviewMapFeature(geometry, buildTags(attrs, layerDef), layer))
                    }
                }
            } finally {
                cursor.close()
            }

            Logger.i(TAG, "Read ${features.size} features from GPKG layer '$layer'")
            features
        }
    }

    /**
     * Resolves the coordinate reference system (CRS) transform from the source CRS of the feature layer
     * to WGS84 (EPSG:4326). Attempts to decode the CRS from the authority (e.g., EPSG), or from WKT definition.
     * Returns null if no transform is needed (already WGS84).
     *
     * @param layer Name of the layer for logging.
     * @param featureDao FeatureDao providing SRS information.
     * @return MathTransform to WGS84, or null if not required.
     */
    private fun resolveCrsTransform(layer: String, featureDao: FeatureDao): MathTransform? {
        val destCrs = CRS.decode("EPSG:4326", true)
        val srs = featureDao.srs
        // Try to decode CRS from authority (e.g. EPSG:4326), else from WKT, else default to WGS84
        val sourceCrs = srs?.let { s ->
            val org = s.organization
            val srid = s.organizationCoordsysId
            if (org != null && srid > 0L) {
                runCatching { CRS.decode("$org:$srid", true) }.getOrNull()
            } else null
        } ?: srs?.definition?.takeIf { it.isNotBlank() }?.let { wkt ->
            runCatching { CRS.parseWKT(wkt) }.getOrNull()
        } ?: run {
            Logger.w(TAG, "No parseable CRS for layer '$layer', defaulting to EPSG:4326")
            destCrs
        }
        // Return transform if source and dest differ
        return if (!CRS.equalsIgnoreMetadata(sourceCrs, destCrs))
            CRS.findMathTransform(sourceCrs, destCrs, true)
        else null
    }

    /**
     * Reads, transforms, validates and clips the geometry from this row. Returns null to skip the row.
     **/
    private fun FeatureRow.readGeometry(wkbReader: WKBReader, transform: MathTransform?): Geometry? {

        val wkb = geometry?.wkb?.takeIf { it.isNotEmpty() } ?: return null

        var geom = wkbReader.read(wkb)

        // Transform geometry to WGS84 if needed
        if (transform != null && !transform.isIdentity) {
            geom = JTS.transform(geom, transform)
        }

        // skip only when invalid AND empty; invalid-but-non-empty geometries are kept as-is
        if (!geom.isValid && geom.isEmpty) return null

        // Clip geometry to Web Mercator bounds if needed
        if (!MERCATOR_BOUNDS_GEOM.envelopeInternal.contains(geom.envelopeInternal)) {
            geom = OverlayNGRobust.overlay(geom, MERCATOR_BOUNDS_GEOM, OverlayNG.INTERSECTION)
            if (geom.isEmpty) return null
        }
        return geom
    }

    /** Builds the OSM tag map: static tags + zoom bounds + any attribute-mapped tags. */
    private fun buildTags(attrs: FeatureAttributes, layerDef: LayerDefinition): Map<String, String> = buildMap {
        putAll(layerDef.staticTags)
        put(TAG_NE_MIN_ZOOM, layerDef.minZoom.toString())
        put(TAG_NE_MAX_ZOOM, layerDef.maxZoom.toString())
        layerDef.attributeMapper?.invoke(attrs)?.let { putAll(it) }
    }

    /** Wraps a FeatureRow as a FeatureAttributes adapter. Returns null for missing columns. */
    private fun FeatureRow.asFeatureAttributes(): FeatureAttributes =
        FeatureAttributes { name ->
            runCatching {
                // hasColumn() avoids the exception thrown by getValue() for unknown columns
                if (columns.hasColumn(name)) getValue(name) else null
            }.getOrNull()
        }
}
