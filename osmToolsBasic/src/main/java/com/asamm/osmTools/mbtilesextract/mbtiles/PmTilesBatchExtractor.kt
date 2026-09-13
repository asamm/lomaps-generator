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
 * Extracts per-map MBTiles from a planet PMTiles file, processing the specs in chunks.
 *
 * Per chunk:
 *  1. Compute per-map tile ID sets in parallel (polygon intersection at each zoom level).
 *  2. Fold them into a reverse index: tileId → list of spec indices.
 *  3. Open the chunk's MBTiles output connections.
 *  4. Traverse planet PMTiles once; dispatch each tile to every matching connection.
 *  5. Write metadata and close connections in parallel (each runs index creation + VACUUM).
 *
 * The reverse index is boxed (`HashMap<Long, MutableList<Int>>`) and costs on the order of
 * 100 bytes per unique tile id, so it dominates heap usage: a whole-planet run at z14 reaches
 * hundreds of millions of entries — tens of GB — if every map is indexed at once. Chunking
 * bounds that at the cost of one planet traversal per chunk. See
 * [com.asamm.osmTools.config.MbtilesConfig.batchChunkSize].
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
     * Extracts MBTiles for all [specs] from [planetPmtiles], in chunks of
     * [com.asamm.osmTools.config.MbtilesConfig.batchChunkSize] maps per planet traversal.
     */
    fun extractBatch(planetPmtiles: Path, specs: List<MapSpec>) {
        if (specs.isEmpty()) return

        val chunkSize = AppConfig.config.mbtilesConfig.batchChunkSize.coerceAtLeast(1)
        val chunks = specs.chunked(chunkSize)

        Logger.i(
            TAG, "Batch MBTiles extraction: ${specs.size} maps from ${planetPmtiles.fileName} " +
                    "in ${chunks.size} chunk(s) of up to $chunkSize maps"
        )

        chunks.forEachIndexed { index, chunk ->
            Logger.i(TAG, "Chunk ${index + 1}/${chunks.size}: ${chunk.size} maps")
            extractChunk(planetPmtiles, chunk)
        }

        Logger.i(TAG, "Batch extraction complete: ${specs.size} MBTiles written")
    }

    /**
     * Runs the full five-phase extraction for one chunk of [specs]. Everything allocated here
     * becomes garbage when the call returns, which is what keeps peak heap bounded.
     */
    private fun extractChunk(planetPmtiles: Path, specs: List<MapSpec>) {

        // Phase 1: read polygon geometries + compute tile ID sets in parallel.
        // Parallel writes target distinct indices; the terminal operation establishes
        // happens-before for the reads that follow.
        val geoms = arrayOfNulls<Geometry>(specs.size)
        val tileIdSets = arrayOfNulls<Set<Long>>(specs.size)

        specs.indices.toList().parallelStream().forEach { i ->
            val geom = PolyReader().read(specs[i].pathPolygon.toFile())
            geoms[i] = geom
            tileIdSets[i] = PmTilesExtract.computeAreaTileIds(geom, specs[i].minZoom, specs[i].maxZoom)
        }

        // Phase 2: fold the per-map sets into the reverse index (tileId → spec indices).
        // Each set is released as soon as it is folded in, so the per-map sets and the index
        // are not both fully resident. The index key set doubles as the union tile id set.
        val reverseIndex = HashMap<Long, MutableList<Int>>()

        for (i in specs.indices) {
            for (tileId in tileIdSets[i]!!) {
                reverseIndex.getOrPut(tileId) { mutableListOf() }.add(i)
            }
            tileIdSets[i] = null
        }
        val unionTileIds: Set<Long> = reverseIndex.keys

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
                                buildMetadata(specs[i], geoms[i]!!, metaSourceInfo)
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

        Logger.i(TAG, "Chunk complete: ${specs.size} MBTiles written")
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
