package com.asamm.osmTools.mbtilesextract.mbtiles

import com.asamm.geoutils.PolyReader
import com.asamm.osmTools.config.AppConfig
import com.asamm.osmTools.generatorDb.utils.GeomUtils
import com.asamm.osmTools.mbtilesextract.tiles.Tile
import com.asamm.osmTools.mbtilesextract.tiles.TileCoord
import com.asamm.osmTools.utils.Logger
import com.asamm.pmtiles.PmTilesExtract
import com.asamm.pmtiles.TileType
import net.minidev.json.JSONObject
import net.minidev.json.JSONValue
import org.locationtech.jts.geom.Geometry
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Executors

/**
 * Extracts per-map MBTiles from a planet PMTiles file for all supplied specs in a single traversal.
 *
 * Algorithm:
 *  1. Compute per-map tile ID sets in parallel (polygon intersection at each zoom level).
 *  2. Build a union tile ID set + reverse index: tileId → list of spec indices.
 *  3. Open all MBTiles output connections.
 *  4. Traverse planet PMTiles once; dispatch each tile to every matching connection.
 *  5. Write metadata and close connections in parallel (each runs index creation + VACUUM).
 */
class PmTilesBatchExtractor {

    companion object {
        private const val TAG = "PmTilesBatchExtractor"
    }

    /**
     * Describes one map to extract.
     *
     * @param pathMbtiles  Destination MBTiles file (created or overwritten).
     * @param pathPolygon  `.poly` file defining the map area.
     * @param name         Map name written into MBTiles metadata.
     * @param minZoom      Minimum zoom level to include.
     * @param maxZoom      Maximum zoom level to include.
     */
    data class MapSpec(
        val pathMbtiles: Path,
        val pathPolygon: Path,
        val name: String,
        val minZoom: Int,
        val maxZoom: Int,
    )

    /**
     * Extracts MBTiles for all [specs] from [planetPmtiles] in a single PMTiles traversal.
     */
    fun extractBatch(planetPmtiles: Path, specs: List<MapSpec>) {
        if (specs.isEmpty()) return

        Logger.i(TAG, "Batch MBTiles extraction: ${specs.size} maps from ${planetPmtiles.fileName}")

        // Phase 1: read polygon geometries + compute tile ID sets in parallel
        data class SpecData(val geom: Geometry, val tileIds: Set<Long>)

        val specData: List<SpecData> = specs.parallelStream().map { spec ->
            val geom = PolyReader().read(spec.pathPolygon.toFile())
            val tileIds = PmTilesExtract.computeAreaTileIds(geom, spec.minZoom, spec.maxZoom)
            SpecData(geom, tileIds)
        }.toList()

        // Phase 2: build union tile ID set + reverse index (tileId → spec indices)
        val unionTileIds = HashSet<Long>()
        val reverseIndex = HashMap<Long, MutableList<Int>>()

        for (i in specs.indices) {
            for (tileId in specData[i].tileIds) {
                if (unionTileIds.add(tileId)) {
                    reverseIndex[tileId] = mutableListOf(i)
                } else {
                    reverseIndex.getValue(tileId).add(i)
                }
            }
        }

        Logger.i(TAG, "Union tile set: ${unionTileIds.size} unique tile IDs across ${specs.size} maps")

        // Phase 3: open all MBTiles output connections
        val connections: List<Mbtiles> = specs.map { spec ->
            if (spec.pathMbtiles.toFile().exists()) {
                Files.delete(spec.pathMbtiles)  // throws IOException if the file is locked/undeletable
            }
            Mbtiles.newWriteToFileDatabase(spec.pathMbtiles)
        }

        // Phase 4: single-pass traversal — dispatch each tile to all matching connections
        val globalMinZoom = specs.minOf { it.minZoom }
        val globalMaxZoom = specs.maxOf { it.maxZoom }
        var totalTileWrites = 0L

        val sourceInfo: PmTilesExtract.SourceInfo? = try {
            PmTilesExtract.forEachTileById(
                input   = planetPmtiles,
                tileIds = unionTileIds,
                maxZoom = globalMaxZoom,
                minZoom = globalMinZoom,
            ) { tileId, z, x, y, data ->
                val indices = reverseIndex[tileId] ?: return@forEachTileById
                val tmsY = (1 shl z) - 1 - y.toInt()
                val tile = Tile(TileCoord(x.toInt(), tmsY, z), data)
                for (idx in indices) {
                    connections[idx].insertTile(tile)
                    totalTileWrites++
                }
            }
        } catch (e: Exception) {
            connections.forEach { runCatching { it.close() } }
            throw e
        }

        Logger.i(TAG, "Traversal complete: $totalTileWrites tile writes across ${specs.size} MBTiles")

        // Phase 5: write metadata + close each connection in parallel (index + VACUUM per file)
        val metaSourceInfo = sourceInfo
        val parallelism = Runtime.getRuntime().availableProcessors()
        val closePool = Executors.newFixedThreadPool(parallelism)
        try {
            val futures = specs.indices.map { i ->
                closePool.submit<Unit> {
                    try {
                        connections[i].use { mbtiles ->
                            mbtiles.insertMetadata(
                                buildMetadata(specs[i], specData[i].geom, metaSourceInfo)
                            )
                        }
                        Logger.d(TAG, "Closed ${specs[i].pathMbtiles.fileName}")
                    } catch (e: Exception) {
                        Logger.e(TAG, "Failed to finalize ${specs[i].pathMbtiles.fileName}: ${e.message}")
                    }
                }
            }
            futures.forEach { it.get() }
        } finally {
            closePool.shutdown()
        }

        Logger.i(TAG, "Batch extraction complete: ${specs.size} MBTiles written")
    }

    private fun buildMetadata(
        spec: MapSpec,
        polyGeom: Geometry,
        sourceInfo: PmTilesExtract.SourceInfo?,
    ): Metadata {
        val metadata = Metadata()
        metadata.setValue(Metadata.NAME_KEY, spec.name)
        metadata.setValue(Metadata.DESCRIPTION_KEY, AppConfig.config.mbtilesConfig.mapDescription)
        metadata.setValue(Metadata.ATTRIBUTION_KEY, AppConfig.config.mbtilesConfig.mapAttribution)
        metadata.setValue(Metadata.VERSION_KEY, AppConfig.config.version)
        metadata.setValue(Metadata.TYPE_KEY, Metadata.TYPE_VALUE_BASELAYER)
        metadata.setValue(Metadata.MINZOOM_KEY, spec.minZoom.toString())
        metadata.setValue(Metadata.MAXZOOM_KEY, spec.maxZoom.toString())

        val mbtilesCreator = MbtilesCreator()
        metadata.setValue(Metadata.BOUNDS_KEY, mbtilesCreator.createMetadataBounds(polyGeom))
        val center = polyGeom.centroid
        metadata.setValue(Metadata.CENTER_KEY, "${center.x},${center.y}")
        metadata.setValue(Metadata.LOMAPS_GEOM_KEY, GeomUtils.geomToGeoJson(polyGeom))

        // Set format from the PMTiles header tile type (more authoritative than the metadata JSON)
        val format = when (sourceInfo?.header?.tileType) {
            TileType.MVT  -> "pbf"
            TileType.PNG  -> "png"
            TileType.JPEG -> "jpg"
            TileType.WEBP -> "webp"
            else          -> null
        }
        format?.let { metadata.setValue(Metadata.FORMAT_KEY, it) }

        // Copy compression and full TileJSON (vector_layers) from the planet PMTiles metadata JSON
        val sourceJson = sourceInfo?.metadataJson?.let { JSONValue.parse(it) as? JSONObject }
        sourceJson?.get("compression")?.let { metadata.setValue(Metadata.COMPRESSION_KEY, it.toString()) }
        sourceJson?.let { metadata.setValue(Metadata.JSON_KEY, it.toJSONString()) }

        return metadata
    }
}
