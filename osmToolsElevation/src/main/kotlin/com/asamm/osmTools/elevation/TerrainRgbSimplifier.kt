package com.asamm.osmTools.elevation

import com.asamm.pmtiles.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import java.nio.file.Path
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Reduces terrain-RGB PMTiles size by rounding elevation values per zoom level.
 *
 * Rounding creates uniform pixel neighborhoods that compress much better
 * with lossless WebP encoding. Uses Kotlin coroutines on [Dispatchers.Default]
 * for the CPU-bound tile decode → round → encode pipeline.
 *
 * Precision scales by zoom level: basePrecisionMeters at the source max zoom,
 * then 1.41× coarser per zoom level below (zoom < 6 clamped to zoom 6).
 *
 * Usage:
 * ```kotlin
 * TerrainRgbSimplifier.process(
 *     input  = Path.of("terrain.pmtiles"),
 *     output = Path.of("terrain_simplified.pmtiles"),
 *     encoding = TerrainRgbCodec.Encoding.MAPBOX,
 *     basePrecisionMeters = 1.0,
 * )
 * ```
 */
object TerrainRgbSimplifier {

    private const val INITIAL_LOG_INTERVAL_MS = 10_000L
    private const val MAX_LOG_INTERVAL_MS = 300_000L

    fun interface ProgressListener {
        fun onProgress(processedTiles: Long, totalTiles: Long, inputBytes: Long, outputBytes: Long)
    }

    data class ProcessResult(
        val totalTiles: Long,
        val inputBytes: Long,
        val outputBytes: Long,
        val outputFileSize: Long,
    )

    /**
     * Processes all tiles in [input], rounding elevation values per zoom level,
     * and writes the result as lossless WebP tiles to [output].
     *
     * Processing pipeline (per tile):
     *  1. Decompress GZIP if tile compression is enabled
     *  2. Decode image (WebP/PNG) to pixel array
     *  3. Round each pixel's 24-bit elevation value to the zoom-appropriate step
     *  4. Re-encode as lossless WebP (better compression of uniform neighborhoods)
     *  5. Recompress GZIP if needed
     *
     * Parallelism: coroutines on [Dispatchers.Default] with a [Semaphore] limiting
     * concurrency. The main coroutine reads tiles (mmap, fast) and writes results
     * (sequential for PMTiles writer), while worker coroutines handle CPU-bound work.
     *
     * @param input               Source PMTiles file (read via memory-mapped I/O).
     * @param output              Destination PMTiles file (created/overwritten).
     * @param encoding            Pixel-to-elevation encoding of the source tiles.
     * @param basePrecisionMeters Rounding precision in meters at the source max zoom.
     * @param concurrency         Max number of tiles processed concurrently (default: CPU cores).
     * @param listener            Optional progress callback (exponential backoff: 10 s → 5 min).
     * @return Processing summary, or `null` if the source contains no tiles.
     */
    fun process(
        input: Path,
        output: Path,
        encoding: TerrainRgbCodec.Encoding = TerrainRgbCodec.Encoding.TERRARIUM,
        basePrecisionMeters: Double = 1.0,
        concurrency: Int = Runtime.getRuntime().availableProcessors(),
        listener: ProgressListener? = null,
    ): ProcessResult? {

        MmapPmTilesReader(input).use { reader ->
            val header = reader.header
            val maxZoom = header.maxZoom
            val totalTiles = header.addressedTilesCount
            if (totalTiles == 0L) return null

            val isGzip = header.tileCompression == Compression.GZIP
            val metadata = reader.metadata()

            // Semaphore bounds concurrency — Dispatchers.Default provides the thread pool
            val semaphore = Semaphore(concurrency * 4) // keep pipeline well-fed

            var processed = 0L
            var totalInBytes = 0L
            var totalOutBytes = 0L
            var nextLogTime = System.currentTimeMillis() + INITIAL_LOG_INTERVAL_MS
            var logInterval = INITIAL_LOG_INTERVAL_MS

            runBlocking {
                PmTilesWriter(output).use { writer ->
                    // Collect tiles and their deferred results in order
                    val pending = ArrayDeque<PendingTile>()

                    for (tile in reader.allTiles()) {
                        val tileId = zxyToTileId(tile.z, tile.x, tile.y)
                        val rawStep = computeRawStep(
                            tile.z, maxZoom, basePrecisionMeters, encoding.rawUnitsPerMeter
                        )
                        val tileData = tile.data
                        val inSize = tileData.size

                        // Launch processing coroutine, bounded by semaphore
                        semaphore.acquire()
                        val deferred = async(Dispatchers.Default) {
                            try {
                                if (rawStep <= 1) tileData
                                else processSingleTile(tileData, rawStep, isGzip)
                            } finally {
                                semaphore.release()
                            }
                        }
                        pending.addLast(PendingTile(tileId, inSize, deferred))

                        // Drain completed tiles from the front to keep memory bounded
                        // and preserve tile order for clustering
                        while (pending.size >= concurrency * 4) {
                            val p = pending.removeFirst()
                            val result = p.deferred.await()
                            writer.writeTile(p.tileId, result)
                            totalInBytes += p.inputSize
                            totalOutBytes += result.size
                            processed++

                            val now = System.currentTimeMillis()
                            if (now >= nextLogTime) {
                                listener?.onProgress(processed, totalTiles, totalInBytes, totalOutBytes)
                                logInterval = minOf(logInterval * 2, MAX_LOG_INTERVAL_MS)
                                nextLogTime = now + logInterval
                            }
                        }
                    }

                    // Drain remaining
                    while (pending.isNotEmpty()) {
                        val p = pending.removeFirst()
                        val result = p.deferred.await()
                        writer.writeTile(p.tileId, result)
                        totalInBytes += p.inputSize
                        totalOutBytes += result.size
                        processed++
                    }

                    listener?.onProgress(processed, totalTiles, totalInBytes, totalOutBytes)

                    writer.finalize(
                        PmTilesHeader(
                            tileType        = TileType.WEBP,
                            tileCompression = header.tileCompression,
                            minLonE7        = header.minLonE7,
                            minLatE7        = header.minLatE7,
                            maxLonE7        = header.maxLonE7,
                            maxLatE7        = header.maxLatE7,
                            centerZoom      = header.centerZoom,
                            centerLonE7     = header.centerLonE7,
                            centerLatE7     = header.centerLatE7,
                        ),
                        metadata
                    )
                }
            }

            return ProcessResult(
                totalTiles     = processed,
                inputBytes     = totalInBytes,
                outputBytes    = totalOutBytes,
                outputFileSize = output.toFile().length(),
            )
        }
    }

    // ── Pipeline types ───────────────────────────────────────────────────────

    private class PendingTile(
        val tileId: Long,
        val inputSize: Int,
        val deferred: Deferred<ByteArray>,
    )

    // ── Precision calculation ────────────────────────────────────────────────

    /**
     * Computes the raw-unit rounding step for a given zoom level.
     *
     * At [baseZoom] (the source max zoom), precision is [basePrecisionM] meters.
     * Each zoom level below that gets 1.41× coarser (roughly matching the
     * halved spatial resolution per zoom step: sqrt(2) ≈ 1.41).
     * Zoom levels below 6 are clamped to zoom 6 precision.
     */
    private fun computeRawStep(
        zoom: Int, baseZoom: Int, basePrecisionM: Double, unitsPerMeter: Int
    ): Int {
        val effectiveZoom = max(zoom, 6)
        val precisionM = basePrecisionM * Math.pow(1.41, (baseZoom - effectiveZoom).toDouble())
        return max(1, (precisionM * unitsPerMeter).roundToInt())
    }

    // ── Single tile processing ───────────────────────────────────────────────

    /**
     * Processes a single tile: decode → round pixels → encode.
     * Runs on a Dispatchers.Default coroutine. Delegates to [TerrainRgbCodec].
     */
    private fun processSingleTile(tileBytes: ByteArray, rawStep: Int, isGzip: Boolean): ByteArray {
        val img = TerrainRgbCodec.decodeTileToImage(tileBytes, isGzip)
            ?: throw IllegalStateException("Cannot decode tile image (${tileBytes.size} bytes)")

        TerrainRgbCodec.roundPixels(img, rawStep)

        val encoded = TerrainRgbCodec.encodeLosslessWebP(img)
        return if (isGzip) TerrainRgbCodec.gzip(encoded) else encoded
    }
}