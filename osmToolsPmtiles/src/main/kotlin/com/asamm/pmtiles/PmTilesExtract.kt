package com.asamm.pmtiles

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

    /**
     * Extracts all tiles with zoom ≤ [maxZoom] from [input] into a new PMTiles archive at [output].
     *
     * Source header settings (tile type, compression, bounds, center) are preserved.
     * Metadata JSON is copied through with patched zoom range.
     *
     * @param input    Source PMTiles file (read via memory-mapped I/O).
     * @param output   Destination PMTiles file (created/overwritten).
     * @param maxZoom  Maximum zoom level to include (clamped to source maxZoom).
     * @param listener Optional progress callback, invoked with exponential backoff (10s, 20s, 40s, … up to 5 min).
     * @return Summary of the extraction, or `null` if no tiles were written.
     */
    fun extract(
        input: Path,
        output: Path,
        maxZoom: Int,
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

            // Pass 1: count matching tiles (directory scan only, no tile data read)
            val totalTiles = countTilesInRange(
                source, header,
                header.rootOffset, header.rootLength.toInt(),
                effectiveMaxZoom
            )
            if (totalTiles == 0L) return null

            val metadata = patchMetadataZoom(reader.metadata(), header.minZoom, effectiveMaxZoom)

            // Pass 2: copy matching tiles
            var copied = 0L
            var copiedBytes = 0L
            var nextLogTime = System.currentTimeMillis() + INITIAL_LOG_INTERVAL_MS
            var logInterval = INITIAL_LOG_INTERVAL_MS

            PmTilesWriter(output).use { writer ->
                traverseFiltered(
                    source, header,
                    header.rootOffset, header.rootLength.toInt(),
                    effectiveMaxZoom
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
                        tileType        = header.tileType,
                        tileCompression = header.tileCompression,
                        minLonE7        = header.minLonE7,
                        minLatE7        = header.minLatE7,
                        maxLonE7        = header.maxLonE7,
                        maxLatE7        = header.maxLatE7,
                        centerZoom      = minOf(header.centerZoom, effectiveMaxZoom),
                        centerLonE7     = header.centerLonE7,
                        centerLatE7     = header.centerLatE7,
                    ),
                    metadata
                )
            }

            return ExtractResult(
                totalTiles = copied,
                tileBytes  = copiedBytes,
                minZoom    = header.minZoom,
                maxZoom    = effectiveMaxZoom,
                outputSize = output.toFile().length(),
            )
        }
    }

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
    ): Long {
        val entries = deserializeDirectory(source(dirOffset, dirLength))
        var total = 0L
        for (entry in entries) {
            if (entry.runLength > 0) {
                for (i in 0 until entry.runLength) {
                    val (z, _, _) = tileIdToZxy(entry.tileId + i)
                    if (z <= maxZoom) total++
                }
            } else {
                total += countTilesInRange(
                    source, header,
                    header.leafDirectoryOffset + entry.offset, entry.length,
                    maxZoom
                )
            }
        }
        return total
    }

    // ── Pass 2: filtered directory traversal ─────────────────────────────────

    /**
     * Walks all directories recursively, invoking [action] for each tile with zoom ≤ [maxZoom].
     * Tile data is lazy-loaded: read once per run entry, only when at least one tile matches.
     */
    private fun traverseFiltered(
        source: (Long, Int) -> ByteArray,
        header: PmTilesHeader,
        dirOffset: Long,
        dirLength: Int,
        maxZoom: Int,
        action: (tileId: Long, data: ByteArray) -> Unit,
    ) {
        val entries = deserializeDirectory(source(dirOffset, dirLength))
        for (entry in entries) {
            if (entry.runLength > 0) {
                var data: ByteArray? = null
                for (i in 0 until entry.runLength) {
                    val tileId = entry.tileId + i
                    val (z, _, _) = tileIdToZxy(tileId)
                    if (z <= maxZoom) {
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
                    maxZoom, action
                )
            }
        }
    }

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
