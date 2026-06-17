package com.asamm.pmtiles

import com.asamm.pmtiles.PmTilesExtract.extract
import com.asamm.pmtiles.PmTilesExtract.forEachTile
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.Polygon
import org.locationtech.jts.geom.prep.PreparedGeometryFactory
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/**
 * Extracts tiles from a PMTiles archive up to a given max zoom level.
 *
 * Designed for planet-scale (TB+) files:
 * - mmap-based reading (no full-file load into heap)
 * - streaming writes via [PmTilesWriter] with temp-file buffering
 * - deduplication handled automatically by [PmTilesWriter]
 * - two-pass: fast directory-only scan to count matching tiles, then copy with progress
 *
 * Usage:
 * ```kotlin
 * PmTilesExtract.extract(
 *     input  = Path.of("planet.pmtiles"),
 *     output = Path.of("filtered.pmtiles"),
 *     maxZoom = 8,
 *     listener = { copied, total, bytes ->
 *         println("$copied / $total tiles (${copied * 100 / total}%)")
 *     }
 * )
 * ```
 */
object PmTilesExtract {

    /**
     * Progress callback invoked periodically during extraction.
     *
     *
     * @param copiedTiles Number of tiles written so far.
     * @param totalTiles  Total tiles to extract (from the counting pass).
     * @param copiedBytes Cumulative tile-data bytes written.
     */
    fun interface ProgressListener {
        fun onProgress(copiedTiles: Long, totalTiles: Long, copiedBytes: Long)
    }


    /** Metadata returned by [forEachTile] for the caller to build output file metadata. */
    data class SourceInfo(val header: PmTilesHeader, val metadataJson: String)

    /** Summary returned by [extract]. */
    data class ExtractResult(
        val totalTiles: Long,
        val tileBytes: Long,
        val minZoom: Int,
        val maxZoom: Int,
        val outputSize: Long,
    )

    // ── Constants ────────────────────────────────────────────────────────────

    private const val INITIAL_LOG_INTERVAL_MS = 10_000L   // first log after 10s
    private const val MAX_LOG_INTERVAL_MS = 300_000L     // cap at 5 min
    private const val SEGMENT_BYTES = 512L * 1024L * 1024L

    /**
     * Extracts tiles from [input] into a new PMTiles archive at [output].
     *
     * Source header settings (tile type, compression) are preserved.
     * Metadata JSON is copied through with patched zoom range.
     * When [area] is provided, the output bounds in the header are tightened to the area envelope.
     *
     * @param input    Source PMTiles file (read via memory-mapped I/O).
     * @param output   Destination PMTiles file (created/overwritten).
     * @param maxZoom  Maximum zoom level to include (clamped to source maxZoom).
     * @param area     Optional JTS geometry (Polygon or MultiPolygon, holes supported).
     *                 When set, only tiles whose geographic footprint intersects [area] are written.
     * @param listener Optional progress callback, invoked with exponential backoff (10s, 20s, 40s, … up to 5 min).
     * @return Summary of the extraction, or `null` if no tiles were written.
     */
    fun extract(
        input: Path,
        output: Path,
        maxZoom: Int,
        area: Geometry? = null,
        listener: ProgressListener? = null,
    ): ExtractResult? {
        require(maxZoom >= 0) { "maxZoom must be >= 0, got $maxZoom" }

        FileChannel.open(input, StandardOpenOption.READ).use { channel ->
            val source = mmapSource(channel)
            val reader = PmTilesReader(source)
            val header = reader.header

            val effectiveMaxZoom = minOf(maxZoom, header.maxZoom)

            // If requested maxZoom is below source minZoom, no tiles can match
            if (effectiveMaxZoom < header.minZoom) return null

            // Precompute the set of tile IDs that intersect the area (null = no spatial filter)
            val areaTileIds: Set<Long>? = area?.let {
                computeAreaTileIds(it, header.minZoom, effectiveMaxZoom)
            }

            // Pass 1: count matching tiles (directory scan only, no tile data read)
            val totalTiles = countTilesInRange(
                source, header,
                header.rootOffset, header.rootLength.toInt(),
                effectiveMaxZoom, areaTileIds
            )
            if (totalTiles == 0L) return null

            val metadata = patchMetadataZoom(reader.metadata(), header.minZoom, effectiveMaxZoom)

            // Tighten output bounds to area envelope when area filter is active
            val (outMinLonE7, outMinLatE7, outMaxLonE7, outMaxLatE7) = if (area != null) {
                val env = area.envelopeInternal
                listOf(
                    (env.minX * 1e7).toInt().coerceIn(-1_800_000_000, 1_800_000_000),
                    (env.minY * 1e7).toInt().coerceIn(-850_000_000, 850_000_000),
                    (env.maxX * 1e7).toInt().coerceIn(-1_800_000_000, 1_800_000_000),
                    (env.maxY * 1e7).toInt().coerceIn(-850_000_000, 850_000_000),
                )
            } else {
                listOf(header.minLonE7, header.minLatE7, header.maxLonE7, header.maxLatE7)
            }

            // Pass 2: copy matching tiles
            var copied = 0L
            var copiedBytes = 0L
            var nextLogTime = System.currentTimeMillis() + INITIAL_LOG_INTERVAL_MS
            var logInterval = INITIAL_LOG_INTERVAL_MS

            PmTilesWriter(output).use { writer ->
                traverseFiltered(
                    source, header,
                    header.rootOffset, header.rootLength.toInt(),
                    effectiveMaxZoom, areaTileIds
                ) { tileId, data ->
                    writer.writeTile(tileId, data)
                    copied++
                    copiedBytes += data.size
                    val now = System.currentTimeMillis()
                    if (now >= nextLogTime) {
                        listener?.onProgress(copied, totalTiles, copiedBytes)
                        logInterval = minOf(logInterval * 2, MAX_LOG_INTERVAL_MS)
                        nextLogTime = now + logInterval
                    }
                }

                // Always report final state
                listener?.onProgress(copied, totalTiles, copiedBytes)

                writer.finalize(
                    PmTilesHeader(
                        tileType = header.tileType,
                        tileCompression = header.tileCompression,
                        minLonE7 = outMinLonE7,
                        minLatE7 = outMinLatE7,
                        maxLonE7 = outMaxLonE7,
                        maxLatE7 = outMaxLatE7,
                        centerZoom = minOf(header.centerZoom, effectiveMaxZoom),
                        centerLonE7 = header.centerLonE7,
                        centerLatE7 = header.centerLatE7,
                    ),
                    metadata
                )
            }

            return ExtractResult(
                totalTiles = copied,
                tileBytes = copiedBytes,
                minZoom = header.minZoom,
                maxZoom = effectiveMaxZoom,
                outputSize = output.toFile().length(),
            )
        }
    }

    /**
     * Convenience overload of [extract] that accepts a bounding box in WGS84 degrees instead of
     * a JTS geometry. All zoom levels present in the source are included.
     *
     * @param input    Source PMTiles file.
     * @param output   Destination PMTiles file (created/overwritten).
     * @param minLon   West longitude in degrees.
     * @param minLat   South latitude in degrees.
     * @param maxLon   East longitude in degrees.
     * @param maxLat   North latitude in degrees.
     * @param listener Optional progress callback.
     */
    fun extractBbox(
        input: Path,
        output: Path,
        minLon: Double,
        minLat: Double,
        maxLon: Double,
        maxLat: Double,
        listener: ProgressListener? = null,
    ): ExtractResult? {
        val area = GEOM_FACTORY.createPolygon(arrayOf(
            Coordinate(minLon, minLat),
            Coordinate(minLon, maxLat),
            Coordinate(maxLon, maxLat),
            Coordinate(maxLon, minLat),
            Coordinate(minLon, minLat),
        ))
        return extract(input, output, Int.MAX_VALUE, area, listener)
    }

    /**
     * Streams every tile from [input] that passes the zoom range and optional area filter
     * to [tileAction], without writing any output file.
     *
     * Tile coordinates delivered to [tileAction] use **XYZ convention** (y=0 at north pole),
     * matching the PMTiles / web-mercator standard. Callers targeting MBTiles (TMS) must flip:
     * `tmsY = (1 shl z) - 1 - y`.
     *
     * @param input    Source PMTiles file.
     * @param maxZoom  Maximum zoom level to stream (clamped to source maxZoom).
     * @param minZoom  Minimum zoom level to stream (default 0).
     * @param area     Optional JTS geometry. When set, only tiles intersecting [area] are delivered.
     * @param tileAction Callback receiving (z, x, y, tileData) for each matching tile.
     * @return Source file info (header + metadata JSON) for the caller to build output metadata,
     *         or `null` if no tiles matched.
     */
    fun forEachTile(
        input: Path,
        maxZoom: Int,
        minZoom: Int = 0,
        area: Geometry? = null,
        tileAction: (z: Int, x: Long, y: Long, data: ByteArray) -> Unit,
    ): SourceInfo? {
        require(maxZoom >= 0) { "maxZoom must be >= 0, got $maxZoom" }
        require(minZoom in 0..maxZoom) { "minZoom must be in 0..maxZoom" }

        FileChannel.open(input, StandardOpenOption.READ).use { channel ->
            val source = mmapSource(channel)
            val reader = PmTilesReader(source)
            val header = reader.header

            val effectiveMaxZoom = minOf(maxZoom, header.maxZoom)
            val effectiveMinZoom = maxOf(minZoom, header.minZoom)
            if (effectiveMaxZoom < effectiveMinZoom) return null

            // Area tile IDs are computed only for the effective zoom range —
            // tiles outside that range are naturally absent from the set.
            val areaTileIds: Set<Long>? = area?.let {
                computeAreaTileIds(it, effectiveMinZoom, effectiveMaxZoom)
            }

            traverseFiltered(
                source, header,
                header.rootOffset, header.rootLength.toInt(),
                effectiveMaxZoom, areaTileIds
            ) { tileId, data ->
                val (z, x, y) = tileIdToZxy(tileId)
                // When areaTileIds is null, we must check minZoom explicitly.
                // When it is set, effectiveMinZoom is already enforced by the set contents.
                if (z >= effectiveMinZoom) tileAction(z, x, y, data)
            }

            return SourceInfo(header, reader.metadata())
        }
    }

    /**
     * Streams tiles from [input] that are present in the pre-computed [tileIds] set,
     * delivering each as `(tileId, z, x, y, data)` to [tileAction].
     *
     * Designed for batch extraction where tile ID sets are pre-computed in parallel and
     * a single traversal dispatches tiles to multiple output files via a reverse index.
     * The `tileId` is passed to the callback so callers can do O(1) reverse-index lookups
     * without needing to recompute it from (z, x, y).
     *
     * @param input      Source PMTiles file.
     * @param tileIds    Pre-computed set of Hilbert-curve tile IDs to extract.
     * @param maxZoom    Maximum zoom level to stream (clamped to source maxZoom).
     * @param minZoom    Minimum zoom level to stream (default 0).
     * @param tileAction Callback receiving (tileId, z, x, y, tileData) for each matching tile.
     * @return Source file info for metadata building, or `null` if [tileIds] is empty.
     */
    fun forEachTileById(
        input: Path,
        tileIds: Set<Long>,
        maxZoom: Int,
        minZoom: Int = 0,
        tileAction: (tileId: Long, z: Int, x: Long, y: Long, data: ByteArray) -> Unit,
    ): SourceInfo? {
        require(maxZoom >= 0) { "maxZoom must be >= 0, got $maxZoom" }
        require(minZoom in 0..maxZoom) { "minZoom must be in 0..maxZoom" }
        if (tileIds.isEmpty()) return null

        FileChannel.open(input, StandardOpenOption.READ).use { channel ->
            val source = mmapSource(channel)
            val reader = PmTilesReader(source)
            val header = reader.header

            val effectiveMaxZoom = minOf(maxZoom, header.maxZoom)
            val effectiveMinZoom = maxOf(minZoom, header.minZoom)
            if (effectiveMaxZoom < effectiveMinZoom) return null

            traverseFiltered(
                source, header,
                header.rootOffset, header.rootLength.toInt(),
                effectiveMaxZoom, tileIds,
            ) { tileId, data ->
                val (z, x, y) = tileIdToZxy(tileId)
                if (z >= effectiveMinZoom) tileAction(tileId, z, x, y, data)
            }

            return SourceInfo(header, reader.metadata())
        }
    }


    // ── Memory-mapped source ─────────────────────────────────────────────────

    private fun mmapSource(channel: FileChannel): (Long, Int) -> ByteArray {
        val fileSize = channel.size()
        val segments = buildList<MappedByteBuffer> {
            var offset = 0L
            while (offset < fileSize) {
                val size = minOf(SEGMENT_BYTES, fileSize - offset)
                add(channel.map(FileChannel.MapMode.READ_ONLY, offset, size))
                offset += size
            }
        }
        return { offset, length ->
            val result = ByteArray(length)
            var remaining = length
            var srcOff = offset
            var dstOff = 0
            while (remaining > 0) {
                val segIdx = (srcOff / SEGMENT_BYTES).toInt()
                val segOff = (srcOff % SEGMENT_BYTES).toInt()
                val seg = segments[segIdx].duplicate()
                seg.position(segOff)
                val chunk = minOf(remaining, seg.remaining())
                seg.get(result, dstOff, chunk)
                remaining -= chunk
                srcOff += chunk
                dstOff += chunk
            }
            result
        }
    }

    // ── Pass 1: count tiles without reading tile data ──────────────────────

    private fun countTilesInRange(
        source: (Long, Int) -> ByteArray,
        header: PmTilesHeader,
        dirOffset: Long,
        dirLength: Int,
        maxZoom: Int,
        areaTileIds: Set<Long>?,
    ): Long {
        val entries = deserializeDirectory(source(dirOffset, dirLength))
        var total = 0L
        for (entry in entries) {
            if (entry.runLength > 0) {
                for (i in 0 until entry.runLength) {
                    val tileId = entry.tileId + i
                    val (z, _, _) = tileIdToZxy(tileId)
                    if (z <= maxZoom && (areaTileIds == null || tileId in areaTileIds)) total++
                }
            } else {
                total += countTilesInRange(
                    source, header,
                    header.leafDirectoryOffset + entry.offset, entry.length,
                    maxZoom, areaTileIds
                )
            }
        }
        return total
    }

    // ── Pass 2: filtered directory traversal ─────────────────────────────────

    /**
     * Walks all directories recursively, invoking [action] for each tile that passes
     * zoom ≤ [maxZoom] and (when set) is present in [areaTileIds].
     * Tile data is lazy-loaded: read once per run entry, only when at least one tile matches.
     */
    private fun traverseFiltered(
        source: (Long, Int) -> ByteArray,
        header: PmTilesHeader,
        dirOffset: Long,
        dirLength: Int,
        maxZoom: Int,
        areaTileIds: Set<Long>?,
        action: (tileId: Long, data: ByteArray) -> Unit,
    ) {
        val entries = deserializeDirectory(source(dirOffset, dirLength))
        for (entry in entries) {
            if (entry.runLength > 0) {
                var data: ByteArray? = null
                for (i in 0 until entry.runLength) {
                    val tileId = entry.tileId + i
                    val (z, _, _) = tileIdToZxy(tileId)
                    if (z <= maxZoom && (areaTileIds == null || tileId in areaTileIds)) {
                        if (data == null) {
                            data = source(header.tileDataOffset + entry.offset, entry.length)
                        }
                        action(tileId, data)
                    }
                }
            } else {
                traverseFiltered(
                    source, header,
                    header.leafDirectoryOffset + entry.offset, entry.length,
                    maxZoom, areaTileIds, action
                )
            }
        }
    }

    // ── Area tile precomputation ─────────────────────────────────────────────

    /**
     * Builds the complete set of Hilbert-curve tile IDs that spatially intersect [geometry]
     * for every zoom level in [minZoom]..[maxZoom].
     *
     * Uses [PreparedGeometryFactory] for fast repeated intersection tests.
     * Correctly excludes tiles that fall entirely inside holes of a MultiPolygon.
     */
    fun computeAreaTileIds(geometry: Geometry, minZoom: Int, maxZoom: Int): Set<Long> {
        val prepared = PreparedGeometryFactory().create(geometry)
        val env = geometry.envelopeInternal
        val ids = HashSet<Long>()

        for (zoom in minZoom..maxZoom) {
            val x0 = lonToTileX(env.minX, zoom)
            val x1 = lonToTileX(env.maxX, zoom)
            val y0 = latToTileY(env.maxY, zoom)   // north → smaller Y
            val y1 = latToTileY(env.minY, zoom)   // south → larger Y
            for (x in x0..x1) {
                for (y in y0..y1) {
                    if (prepared.intersects(tilePolygon(x, y, zoom))) {
                        ids.add(zxyToTileId(zoom, x.toLong(), y.toLong()))
                    }
                }
            }
        }
        return ids
    }

    private fun tilePolygon(x: Int, y: Int, zoom: Int): Polygon {
        val lonMin = tileXToLon(x, zoom)
        val lonMax = tileXToLon(x + 1, zoom)
        val latMin = tileYToLat(y + 1, zoom)
        val latMax = tileYToLat(y, zoom)
        return GEOM_FACTORY.createPolygon(
            arrayOf(
                Coordinate(lonMin, latMin),
                Coordinate(lonMin, latMax),
                Coordinate(lonMax, latMax),
                Coordinate(lonMax, latMin),
                Coordinate(lonMin, latMin),
            )
        )
    }

    private val GEOM_FACTORY = GeometryFactory()

    // ── Metadata zoom patching ───────────────────────────────────────────────

    /**
     * Patches "minzoom" and "maxzoom" values in the metadata JSON string.
     * Uses simple regex replacement to avoid a JSON library dependency.
     */
    private fun patchMetadataZoom(metadataJson: String, minZoom: Int, maxZoom: Int): String {
        var result = metadataJson
        result = result.replace(
            Regex(""""minzoom"\s*:\s*"?\d+"?"""),
            """"minzoom":"$minZoom""""
        )
        result = result.replace(
            Regex(""""maxzoom"\s*:\s*"?\d+"?"""),
            """"maxzoom":"$maxZoom""""
        )
        return result
    }
}
