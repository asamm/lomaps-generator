package com.asamm.osmTools.elevation.gebco

import com.asamm.osmTools.config.AppConfig
import com.asamm.osmTools.config.TerrainResampling
import com.asamm.osmTools.elevation.TerrainRgbCodec
import com.asamm.osmTools.utils.Logger
import com.asamm.osmTools.utils.MercatorUtils
import com.asamm.osmTools.utils.Utils
import com.asamm.pmtiles.*
import com.asamm.pmtiles.PmTilesCluster
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import java.awt.image.BufferedImage
import java.nio.file.Path
import kotlin.math.*

/**
 * Generates bathymetry (ocean-floor) terrain-RGB tiles from GEBCO NetCDF data.
 *
 * The pipeline:
 * 1. Opens GEBCO elevation + TID grids via [GebcoReader].
 * 2. For each tile at the configured max zoom level:
 *    a. Computes the geographic bounds of the tile (Web Mercator → WGS84).
 *    b. Resamples the GEBCO source grid to a 256×256 tile using the selected method.
 *    c. Land pixels (TID=0) are encoded as transparent (alpha=0) → no-data.
 *    d. Ocean pixels are encoded using Terrarium encoding: `R*256 + G + B/256 - 32768`.
 *    e. The tile is encoded as lossless WebP.
 * 3. After all max-zoom tiles are generated, builds a zoom pyramid (max-1 → 0)
 *    by averaging 2×2 child tiles for each parent tile.
 * 4. Writes all tiles as a PMTiles archive.

 * - GEBCO data is read in blocks per tile row to minimize I/O.
 */
object BathymetryTileGenerator {

    private const val TAG = "BathymetryTileGen"
    private const val TILE_SIZE = 256

    // consts for logging
    private const val INITIAL_LOG_INTERVAL_MS = 10_000L
    private const val MAX_LOG_INTERVAL_MS = 300_000L

    // ── Public API ─────────────────────────────────────────────────────────

    fun interface ProgressListener {
        fun onProgress(processedTiles: Long, totalTiles: Long, phase: String)
    }

    /**
     * Summary returned after a successful [generate] call.
     *
     * @param totalTiles     Total number of tiles written across all zoom levels.
     * @param maxZoomTiles   Number of tiles written at the highest zoom level only.
     * @param outputFileSize Size of the resulting PMTiles file in bytes.
     */
    data class GenerateResult(
        val totalTiles: Long,
        val maxZoomTiles: Long,
        val outputFileSize: Long,
    )

    /**
     * Generates bathymetry terrain-RGB PMTiles from GEBCO data.
     *
     * @param reader      Open [GebcoReader] with elevation + TID data.
     * @param output      Destination PMTiles file path (created/overwritten).
     * @param maxZoom     Maximum zoom level for the tile pyramid (e.g. 7).
     * @param resampling  Interpolation method for GEBCO → tile resampling.
     * @param encoding    Terrain-RGB encoding to use (default: TERRARIUM).
     * @param clampLandToZero When **true**, land pixels (TID=0) and positive
     *                        elevations are set to 0 (sea level) instead of
     *                        transparent no-data. Produces filled tiles where
     *                        coastlines render as 0 m depth.
     * @param concurrency Max parallel tile processing (default: CPU cores).
     * @param listener    Optional progress callback.
     * @return Generation summary.
     */
    fun generate(
        reader: GebcoReader,
        output: Path,
        maxZoom: Int = 7,
        resampling: TerrainResampling = TerrainResampling.BILINEAR,
        encoding: TerrainRgbCodec.Encoding = TerrainRgbCodec.Encoding.TERRARIUM,
        clampLandToZero: Boolean = true,
        concurrency: Int = Runtime.getRuntime().availableProcessors(),
        listener: ProgressListener? = null,
    ): GenerateResult {

        val numTiles = (0..maxZoom).sumOf { z -> (1L shl z) * (1L shl z) }
        Logger.i(TAG, "Generating bathymetry tiles: zoom 0–$maxZoom, ~$numTiles total tiles, " +
                "resampling=$resampling, concurrency=$concurrency")

        var totalTilesWritten = 0L
        var maxZoomTilesWritten = 0L

        // create directory if needed and delete existing file
        output.toFile().apply {
            parentFile?.mkdirs()
            if (exists() && AppConfig.config.overwrite) {
                Logger.w(TAG, "Output file already exists and will be overwritten: ${output.toAbsolutePath()}")
                delete()
            }
        }

        runBlocking {
            PmTilesWriter(output).use { writer ->

                val semaphore = Semaphore(concurrency)

                // ── Generate tiles from maxZoom → 0 (highest detail first) ──
                // The resulting archive will NOT be clustered; call PmTilesCluster
                // afterwards to reorder tile data into Hilbert-curve order.
                for (zoom in maxZoom downTo 0) {
                    val zTiles = 1 shl zoom
                    val zTotalTiles = zTiles.toLong() * zTiles
                    var zProcessed = 0L
                    var zWritten = 0L

                    var nextLogTime = System.currentTimeMillis() + INITIAL_LOG_INTERVAL_MS
                    var logInterval = INITIAL_LOG_INTERVAL_MS

                    val pending = ArrayDeque<PendingTile>()

                    for (y in 0 until zTiles) {
                        for (x in 0 until zTiles) {
                            val tileId = zxyToTileId(zoom, x, y)

                            semaphore.acquire()
                            val deferred = async(Dispatchers.Default) {
                                try {
                                    generateMaxZoomTile(reader, zoom, x, y, resampling, encoding, clampLandToZero)
                                } finally {
                                    semaphore.release()
                                }
                            }
                            pending.addLast(PendingTile(tileId, deferred))

                            // Drain completed tiles to keep memory bounded
                            while (pending.size >= concurrency * 4) {
                                val p = pending.removeFirst()
                                val tileData = p.deferred.await()
                                if (tileData != null) {
                                    writer.writeTile(p.tileId, tileData)
                                    zWritten++
                                }
                                zProcessed++

                                val now = System.currentTimeMillis()
                                if (now >= nextLogTime) {
                                    listener?.onProgress(zProcessed, zTotalTiles, "zoom $zoom")
                                    logInterval = minOf(logInterval * 2, MAX_LOG_INTERVAL_MS)
                                    nextLogTime = now + logInterval
                                }
                            }
                        }
                    }

                    // Drain remaining tiles for this zoom
                    while (pending.isNotEmpty()) {
                        val p = pending.removeFirst()
                        val tileData = p.deferred.await()
                        if (tileData != null) {
                            writer.writeTile(p.tileId, tileData)
                            zWritten++
                        }
                        zProcessed++
                    }

                    totalTilesWritten += zWritten
                    if (zoom == maxZoom) maxZoomTilesWritten = zWritten
                    listener?.onProgress(zProcessed, zTotalTiles, "zoom $zoom complete")
                    Logger.i(TAG, "Zoom $zoom: $zWritten / $zTotalTiles tiles written")
                }

                // ── Finalize PMTiles ─────────────────────────────────────────
                writer.finalize(
                    PmTilesHeader(
                        tileType = TileType.WEBP,
                        tileCompression = Compression.NONE,
                        minLonE7 = -1_800_000_000,
                        minLatE7 = (-MercatorUtils.WEB_MERCATOR_MAX_LAT * 1e7).toInt(),
                        maxLonE7 = 1_800_000_000,
                        maxLatE7 = (MercatorUtils.WEB_MERCATOR_MAX_LAT * 1e7).toInt(),
                        centerZoom = maxZoom / 2,
                        centerLonE7 = 0,
                        centerLatE7 = 0,
                    ),
                    """{"name":"GEBCO Bathymetry Terrain-RGB","description":"Ocean floor bathymetry encoded as Terrarium terrain-RGB","attribution":"GEBCO Compilation Group (2025) GEBCO 2025 Grid "}"""
                )
            }
        }

        val fileSize = output.toFile().length()
        Logger.i(TAG, "Bathymetry PMTiles generated: $totalTilesWritten tiles, " +
                "${Utils.formatBytesToHuman(fileSize)} — clustering...")

        // Cluster the archive so tile data is in Hilbert-curve order
        val clusterResult = PmTilesCluster.cluster(
            input = output,
            deduplicate = true,
        )
        val finalFileSize = output.toFile().length()
        if (clusterResult != null) {
            Logger.i(TAG, "Bathymetry PMTiles clustered: ${clusterResult.tileEntries} entries, " +
                    "${clusterResult.tileContents} unique contents, ${Utils.formatBytesToHuman(finalFileSize)}")
        } else {
            Logger.i(TAG, "Bathymetry PMTiles already clustered")
        }

        return GenerateResult(
            totalTiles = totalTilesWritten,
            maxZoomTiles = maxZoomTilesWritten,
            outputFileSize = finalFileSize,
        )
    }

    // ── Pipeline types ───────────────────────────────────────────────────────

    private class PendingTile(
        val tileId: Long,
        val deferred: Deferred<ByteArray?>,
    )

    // ── Tile generation ──────────────────────────────────────────────────────

    /**
     * Generates a single terrain-RGB tile by resampling from the GEBCO grid.
     *
     * The method converts a Web Mercator tile address (zoom/x/y) into WGS84 geographic
     * coordinates, reads the corresponding rectangular block from the GEBCO NetCDF grid,
     * resamples the block onto a 256×256 pixel tile using the chosen interpolation method,
     * and encodes each pixel as a terrain-RGB value in lossless WebP format.
     *
     * Land pixels (where the GEBCO TID grid marks land, encoded as NaN by the reader)
     * are set to fully transparent (alpha=0), so downstream compositing can layer
     * bathymetry beneath a land/terrain layer without masking artifacts.
     *
     * @param reader     Open [GebcoReader] providing elevation data and TID-based land masking.
     * @param zoom       Zoom level of the tile being generated.
     * @param tileX      Column index (X) of the tile in the Web Mercator grid at [zoom].
     * @param tileY      Row index (Y) of the tile in the Web Mercator grid at [zoom].
     *                   Y=0 is the north-most row (top of the map).
     * @param resampling Interpolation method used to resample GEBCO grid values onto tile pixels.
     *                   Higher-quality methods (bicubic, Lanczos) require larger padding around
     *                   the source region to avoid edge artifacts.
     * @param encoding         Terrain-RGB encoding scheme (e.g. Terrarium: `R*256 + G + B/256 - 32768`).
     * @param clampLandToZero  When true, land and positive-elevation pixels are set to 0
     *                         instead of NaN, producing opaque sea-level pixels.
     * @return Encoded lossless WebP tile bytes, or **null** if the tile is entirely
     *         no-data (all land / outside GEBCO coverage) — callers should skip writing null tiles.
     */
    private fun generateMaxZoomTile(
        reader: GebcoReader,
        zoom: Int,
        tileX: Int,
        tileY: Int,
        resampling: TerrainResampling,
        encoding: TerrainRgbCodec.Encoding,
        clampLandToZero: Boolean,
    ): ByteArray? {

        // ── Step 1: Tile bounds ─────────────────────────────────────────────
        // Convert the tile column/row at the given zoom into WGS84 longitude/latitude bounds.
        // In the Web Mercator tiling scheme, X increases eastward and Y increases southward,
        // so tileY gives the north edge and tileY+1 gives the south edge.
        val lonMin = MercatorUtils.tileXToLon(tileX, zoom)
        val lonMax = MercatorUtils.tileXToLon(tileX + 1, zoom)
        val latMax = MercatorUtils.tileYToLat(tileY, zoom)       // north edge
        val latMin = MercatorUtils.tileYToLat(tileY + 1, zoom)   // south edge

        // ── Step 2: Source region with interpolation padding ────────────────
        // Each resampling kernel needs a certain number of neighboring grid cells beyond
        // the tile boundary to compute interpolated values at edge pixels without clamping.
        // Nearest needs 0 extra cells, bilinear needs 1, bicubic 2, Lanczos-3 needs 3.
        val pad = when (resampling) {
            TerrainResampling.NEAREST -> 0
            TerrainResampling.BILINEAR -> 1
            TerrainResampling.BICUBIC -> 2
            TerrainResampling.LANCZOS -> 3
        }

        // Map tile geographic bounds to GEBCO grid indices (without padding first).
        val rawLatStart = reader.latToIndex(latMin)
        val rawLatEnd = reader.latToIndex(latMax)
        val rawLonStart = reader.lonToIndex(lonMin)
        val rawLonEnd = reader.lonToIndex(lonMax)

        val rawLatCount = abs(rawLatEnd - rawLatStart) + 1
        val rawLonCount = abs(rawLonEnd - rawLonStart) + 1

        // ── Step 2b: Compute stride for low zoom levels ────────────────────
        // At low zooms a single tile covers a huge GEBCO region (e.g. zoom 0 = the whole world
        // = 43200×86400 ≈ 3.7 billion cells, exceeding the Java 2^31 array limit).
        // Subsample with a stride so the block stays manageable. We keep ~4× oversampling
        // relative to TILE_SIZE to preserve resampling quality.
        val latStride = maxOf(1, rawLatCount / (TILE_SIZE * 4))
        val lonStride = maxOf(1, rawLonCount / (TILE_SIZE * 4))

        // Apply padding scaled by stride — the kernel needs `pad` cells in the subsampled
        // grid, which corresponds to `pad * stride` cells in the original grid.
        val scaledLatPad = pad * latStride
        val scaledLonPad = pad * lonStride

        val srcLatStart = (minOf(rawLatStart, rawLatEnd) - scaledLatPad).coerceAtLeast(0)
        val srcLatEnd = (maxOf(rawLatStart, rawLatEnd) + scaledLatPad).coerceAtMost(reader.latSize - 1)
        val srcLonStart = (minOf(rawLonStart, rawLonEnd) - scaledLonPad).coerceAtLeast(0)
        val srcLonEnd = (maxOf(rawLonStart, rawLonEnd) + scaledLonPad).coerceAtMost(reader.lonSize - 1)

        // If the source region is degenerate (e.g. tile falls entirely outside GEBCO coverage),
        // there is nothing to render — return null so the caller skips this tile.
        if (srcLatStart >= srcLatEnd || srcLonStart >= srcLonEnd) {
            return null
        }

        val latCount = srcLatEnd - srcLatStart + 1
        val lonCount = srcLonEnd - srcLonStart + 1

        // ── Step 3: Read GEBCO data ────────────────────────────────────────
        // Read a (possibly strided) block of elevation values from the NetCDF grid.
        // When clampLandToZero is false, land pixels are masked as Float.NaN (transparent).
        // When true, land pixels and positive elevations are clamped to 0 (sea level).
        // The reader is not thread-safe, so access is synchronized across coroutines.
        val block = synchronized(reader) {
            reader.readElevationBlock(
                srcLatStart, srcLonStart, latCount, lonCount,
                clampLandToZero, latStride, lonStride,
            )
        }

        // Effective dimensions and step after subsampling
        val effectiveLatCount = (latCount + latStride - 1) / latStride
        val effectiveLonCount = (lonCount + lonStride - 1) / lonStride
        val effectiveLatStep = reader.latStep * latStride
        val effectiveLonStep = reader.lonStep * lonStride

        // ── Step 4: Resample onto tile pixels ──────────────────────────────
        // Create a 256×256 ARGB image. Each pixel is mapped to its WGS84 center coordinate,
        // then the coordinate is converted to fractional indices within the subsampled block.
        val img = BufferedImage(TILE_SIZE, TILE_SIZE, BufferedImage.TYPE_INT_ARGB)
        var hasData = false

        for (py in 0 until TILE_SIZE) {
            // Map pixel row to latitude via the Mercator projection.
            // Pixels are evenly spaced in Mercator Y (projected space), NOT in latitude.
            // The fractional tile Y for this pixel's center is tileY + (py + 0.5) / TILE_SIZE.
            val tileYFrac = tileY + (py + 0.5) / TILE_SIZE
            val pixLat = MercatorUtils.tileYToLat(tileYFrac, zoom)

            for (px in 0 until TILE_SIZE) {
                // Map pixel column to longitude. Longitude IS linear in Mercator, so
                // simple interpolation is correct here.
                val pixLon = lonMin + (lonMax - lonMin) * (px + 0.5) / TILE_SIZE

                // Convert the pixel's geographic position to fractional row/column within
                // the subsampled block. The effective step accounts for the stride.
                val srcRowF = (pixLat - reader.latAtIndex(srcLatStart)) / effectiveLatStep
                val srcColF = (pixLon - reader.lonAtIndex(srcLonStart)) / effectiveLonStep

                // Sample elevation using the selected interpolation method.
                // Returns Float.NaN for land pixels or when any neighbor in the kernel is land.
                val elevation = when (resampling) {
                    TerrainResampling.NEAREST -> sampleNearest(block, effectiveLatCount, effectiveLonCount, srcRowF, srcColF)
                    TerrainResampling.BILINEAR -> sampleBilinear(block, effectiveLatCount, effectiveLonCount, srcRowF, srcColF)
                    TerrainResampling.BICUBIC -> sampleBicubic(block, effectiveLatCount, effectiveLonCount, srcRowF, srcColF)
                    TerrainResampling.LANCZOS -> sampleLanczos(block, effectiveLatCount, effectiveLonCount, srcRowF, srcColF)
                }

                // ── Step 5: Encode pixel ────────────────────────────────────
                if (elevation.isNaN()) {
                    // Land or no-data: fully transparent pixel (alpha=0).
                    // Downstream compositing will show the land/terrain layer beneath.
                    img.setRGB(px, py, 0x00000000)
                } else {
                    // Ocean pixel: encode elevation into RGB channels using the terrain-RGB
                    // encoding scheme (e.g. Terrarium: elevation = R*256 + G + B/256 - 32768).
                    // Alpha is set to fully opaque (0xFF).
                    val raw = TerrainRgbCodec.encodeRaw(elevation, encoding)
                    val r = (raw shr 16) and 0xFF
                    val g = (raw shr 8) and 0xFF
                    val b = raw and 0xFF
                    img.setRGB(px, py, (0xFF shl 24) or (r shl 16) or (g shl 8) or b)
                    hasData = true
                }
            }
        }

        if (!hasData) return null

        return TerrainRgbCodec.encodeLosslessWebP(img)
    }

    // ── Resampling methods ───────────────────────────────────────────────────

    /**
     * Nearest-neighbor sampling. Returns the elevation at the closest grid point.
     */
    private fun sampleNearest(
        block: FloatArray, rows: Int, cols: Int,
        rowF: Double, colF: Double,
    ): Float {
        val r = rowF.roundToInt().coerceIn(0, rows - 1)
        val c = colF.roundToInt().coerceIn(0, cols - 1)
        return block[r * cols + c]
    }

    /**
     * Bilinear interpolation using 2×2 neighborhood.
     * Returns NaN if any of the 4 samples is NaN.
     */
    private fun sampleBilinear(
        block: FloatArray, rows: Int, cols: Int,
        rowF: Double, colF: Double,
    ): Float {
        val r0 = floor(rowF).toInt().coerceIn(0, rows - 2)
        val c0 = floor(colF).toInt().coerceIn(0, cols - 2)
        val fr = (rowF - r0).toFloat()
        val fc = (colF - c0).toFloat()

        val e00 = block[r0 * cols + c0]
        val e01 = block[r0 * cols + c0 + 1]
        val e10 = block[(r0 + 1) * cols + c0]
        val e11 = block[(r0 + 1) * cols + c0 + 1]

        if (e00.isNaN() || e01.isNaN() || e10.isNaN() || e11.isNaN()) return Float.NaN

        return (1 - fr) * (1 - fc) * e00 +
                (1 - fr) * fc * e01 +
                fr * (1 - fc) * e10 +
                fr * fc * e11
    }

    /**
     * Bicubic interpolation using 4×4 neighborhood (Keys convolution, a=-0.5).
     * Returns NaN if any of the 16 samples is NaN.
     */
    private fun sampleBicubic(
        block: FloatArray, rows: Int, cols: Int,
        rowF: Double, colF: Double,
    ): Float {
        val r1 = floor(rowF).toInt().coerceIn(1, rows - 3)
        val c1 = floor(colF).toInt().coerceIn(1, cols - 3)
        val fr = (rowF - r1).toFloat()
        val fc = (colF - c1).toFloat()

        val samples = FloatArray(16)
        for (dr in -1..2) {
            for (dc in -1..2) {
                val v = block[(r1 + dr) * cols + (c1 + dc)]
                if (v.isNaN()) return Float.NaN
                samples[(dr + 1) * 4 + (dc + 1)] = v
            }
        }

        val col0 = cubicInterp(samples[0], samples[1], samples[2], samples[3], fc)
        val col1 = cubicInterp(samples[4], samples[5], samples[6], samples[7], fc)
        val col2 = cubicInterp(samples[8], samples[9], samples[10], samples[11], fc)
        val col3 = cubicInterp(samples[12], samples[13], samples[14], samples[15], fc)

        return cubicInterp(col0, col1, col2, col3, fr)
    }

    /**
     * 1D cubic interpolation (Keys, a = -0.5).
     */
    private fun cubicInterp(p0: Float, p1: Float, p2: Float, p3: Float, t: Float): Float {
        val t2 = t * t
        val t3 = t2 * t
        return p1 + 0.5f * t * (p2 - p0) +
                t2 * (2f * p0 - 5f * p1 + 4f * p2 - p3) * 0.5f +
                t3 * (-p0 + 3f * p1 - 3f * p2 + p3) * 0.5f
    }

    /**
     * Lanczos-3 interpolation using 6×6 neighborhood.
     * Returns NaN if any of the 36 samples is NaN.
     */
    private fun sampleLanczos(
        block: FloatArray, rows: Int, cols: Int,
        rowF: Double, colF: Double,
    ): Float {
        val a = 3 // Lanczos-3 kernel half-width
        val r0 = floor(rowF).toInt()
        val c0 = floor(colF).toInt()

        var sum = 0.0
        var weightSum = 0.0

        for (dr in -(a - 1)..a) {
            val ri = r0 + dr
            if (ri < 0 || ri >= rows) continue
            val wy = lanczosWeight(rowF - ri, a)

            for (dc in -(a - 1)..a) {
                val ci = c0 + dc
                if (ci < 0 || ci >= cols) continue

                val v = block[ri * cols + ci]
                if (v.isNaN()) return Float.NaN

                val wx = lanczosWeight(colF - ci, a)
                val w = wx * wy
                sum += v * w
                weightSum += w
            }
        }

        return if (weightSum > 0) (sum / weightSum).toFloat() else Float.NaN
    }

    /**
     * Lanczos kernel: L(x) = sinc(x) * sinc(x/a) for |x| < a, else 0.
     */
    private fun lanczosWeight(x: Double, a: Int): Double {
        if (x == 0.0) return 1.0
        val absX = abs(x)
        if (absX >= a) return 0.0
        val piX = PI * x
        val piXa = piX / a
        return (sin(piX) / piX) * (sin(piXa) / piXa)
    }

}

