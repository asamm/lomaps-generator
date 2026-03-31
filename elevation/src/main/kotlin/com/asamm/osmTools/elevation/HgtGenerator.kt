package com.asamm.osmTools.elevation

import com.asamm.osmTools.config.HgtResampling
import com.asamm.pmtiles.Compression
import com.asamm.pmtiles.MmapPmTilesReader
import kotlinx.coroutines.*
import java.io.BufferedOutputStream
import java.io.DataOutputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.*

/**
 * Converts terrain-RGB PMTiles into HGT (SRTM-compatible) elevation files.
 *
 * HGT files are raw binary rasters of signed 16-bit big-endian integers (meters),
 * each covering 1°×1° of latitude/longitude on a WGS84 grid:
 * - **SRTM-3** (3 arc-second): 1201×1201 grid — used when source maxZoom ≤ 11
 * - **SRTM-1** (1 arc-second): 3601×3601 grid — used when source maxZoom ≥ 12
 *
 * Processing pipeline:
 * 1. Determine geographic bounds from the PMTiles header
 * 2. Enumerate all 1°×1° cells within those bounds
 * 3. For each cell (in parallel via coroutines on [Dispatchers.Default]):
 *    a. Pre-compute tile/pixel coordinate arrays for the target grid
 *    b. Read and decode terrain-RGB tiles on demand (cached per cell via [TerrainRgbCodec])
 *    c. Bilinear-interpolate elevation at each HGT grid point
 *    d. Write the HGT file if the cell contains any valid data
 *
 * The [MmapPmTilesReader] is thread-safe, so multiple coroutines can read tiles
 * concurrently without locking.
 *
 * Usage:
 * ```kotlin
 * HgtGenerator.generate(
 *     input     = Path.of("planet.pmtiles"),
 *     outputDir = Path.of("hgt/"),
 *     encoding  = TerrainRgbCodec.Encoding.MAPBOX,
 * )
 * ```
 */
object HgtGenerator {

    // ── HGT format constants ─────────────────────────────────────────────────

    /** SRTM-1 grid: 1 arc-second resolution, 3601×3601 pixels per 1° tile. */
    const val SRTM1_GRID = 3601

    /** SRTM-3 grid: 3 arc-second resolution, 1201×1201 pixels per 1° tile. */
    const val SRTM3_GRID = 1201

    /** HGT no-data value: -32768 (standard SRTM void marker). */
    const val NODATA: Short = -32768

    /** Maximum latitude for Web Mercator projection (~85.051°). */
    private const val WEB_MERCATOR_MAX_LAT = 85.051129

    /** Default tile size for terrain-RGB tiles. */
    private const val TILE_SIZE = 512

    private const val INITIAL_LOG_INTERVAL_MS = 10_000L
    private const val MAX_LOG_INTERVAL_MS = 300_000L
    private const val HGT_WRITE_BUFFER = 64 * 1024    // 64 KB write buffer

    // ── Public types ─────────────────────────────────────────────────────────

    fun interface ProgressListener {
        fun onProgress(processedCells: Long, totalCells: Long, writtenFiles: Long)
    }

    data class GenerateResult(
        val totalCells: Long,
        val writtenFiles: Long,
        val gridSize: Int,
    )

    // ── Main entry point ─────────────────────────────────────────────────────

    /**
     * Generates HGT files from a terrain-RGB PMTiles archive.
     *
     * Reads tiles at the source's maximum zoom level and resamples to the
     * appropriate SRTM grid (3" for zoom ≤ 11, 1" for zoom ≥ 12).
     *
     * Uses Kotlin coroutines on [Dispatchers.Default] for parallel cell processing.
     * The parallelism level is bounded by the Default dispatcher's thread count
     * (= number of CPU cores), or overridden via [concurrency].
     *
     * @param input       Source terrain-RGB PMTiles file.
     * @param outputDir   Directory where HGT files will be written (created if absent).
     * @param encoding    How the source tiles encode elevation in RGB pixels.
     * @param resampling  Interpolation method: [HgtResampling.BILINEAR] (2×2) or [HgtResampling.BICUBIC] (4×4).
     * @param concurrency Max number of cells processed concurrently (default: CPU cores).
     * @param listener    Optional progress callback (exponential backoff: 10 s → 5 min).
     * @return Generation summary.
     */
    fun generate(
        input: Path,
        outputDir: Path,
        encoding: TerrainRgbCodec.Encoding = TerrainRgbCodec.Encoding.TERRARIUM,
        resampling: HgtResampling = HgtResampling.BILINEAR,
        concurrency: Int = Runtime.getRuntime().availableProcessors(),
        listener: ProgressListener? = null,
    ): GenerateResult {
        Files.createDirectories(outputDir)

        MmapPmTilesReader(input).use { reader ->
            val header = reader.header
            val zoom = header.maxZoom
            val isGzip = header.tileCompression == Compression.GZIP

            // Choose grid resolution based on source zoom level
            val gridSize = if (zoom >= 12) SRTM1_GRID else SRTM3_GRID

            // Determine geographic bounds from the PMTiles header (E7 = degrees × 10^7)
            val minLat = floor(header.minLatE7 / 1e7).toInt().coerceAtLeast(-90)
            val maxLat = ceil(header.maxLatE7 / 1e7).toInt().coerceAtMost(90)
            val minLon = floor(header.minLonE7 / 1e7).toInt().coerceAtLeast(-180)
            val maxLon = ceil(header.maxLonE7 / 1e7).toInt().coerceAtMost(180)

            // Build list of all 1°×1° cells (lat = SW corner latitude)
            val cells = ArrayList<Pair<Int, Int>>()
            for (lat in minLat until maxLat) {
                if (lat >= WEB_MERCATOR_MAX_LAT.toInt() || lat + 1 <= -WEB_MERCATOR_MAX_LAT.toInt()) continue
                for (lon in minLon until maxLon) {
                    cells.add(lat to lon)
                }
            }
            val totalCells = cells.size.toLong()

            // Semaphore limits concurrency — Dispatchers.Default provides the thread pool
            val semaphore = kotlinx.coroutines.sync.Semaphore(concurrency)

            var processed = 0L
            var writtenFiles = 0L
            var nextLogTime = System.currentTimeMillis() + INITIAL_LOG_INTERVAL_MS
            var logInterval = INITIAL_LOG_INTERVAL_MS

            // Run all cells as coroutines on Dispatchers.Default
            runBlocking {
                // Launch all cells concurrently, bounded by semaphore
                val deferreds = cells.map { (lat, lon) ->
                    async(Dispatchers.Default) {
                        semaphore.acquire()
                        try {
                            val grid = processCell(lat, lon, zoom, gridSize, encoding, resampling, reader, isGzip)
                            CellResult(lat, lon, grid)
                        } finally {
                            semaphore.release()
                        }
                    }
                }

                // Collect results sequentially and write HGT files
                for (deferred in deferreds) {
                    val result = deferred.await()

                    if (result.grid != null) {
                        // Skip if already exists (resume support)
                        val hgtPath = outputDir.resolve(hgtFilename(result.lat, result.lon))
                        if (!Files.exists(hgtPath)) {
                            writeHgtFile(outputDir, result.lat, result.lon, result.grid)
                        }
                        writtenFiles++
                    }
                    processed++

                    // Progress with exponential backoff
                    val now = System.currentTimeMillis()
                    if (now >= nextLogTime) {
                        listener?.onProgress(processed, totalCells, writtenFiles)
                        logInterval = minOf(logInterval * 2, MAX_LOG_INTERVAL_MS)
                        nextLogTime = now + logInterval
                    }
                }
            }

            listener?.onProgress(processed, totalCells, writtenFiles)
            return GenerateResult(totalCells, writtenFiles, gridSize)
        }
    }

    // ── Pipeline types ───────────────────────────────────────────────────────

    private class CellResult(val lat: Int, val lon: Int, val grid: ShortArray?)

    // ── Single cell processing ───────────────────────────────────────────────

    /**
     * Generates the elevation grid for one 1°×1° HGT cell.
     *
     * Steps:
     * 1. Pre-compute tile X coordinates for each column (avoids redundant math)
     * 2. Pre-compute tile Y coordinates for each row (avoids redundant trig)
     * 3. For each grid point, bilinear-interpolate elevation from the source tiles
     *
     * @param cellLat SW corner latitude (integer degrees).
     * @param cellLon SW corner longitude (integer degrees).
     * @return Elevation grid as ShortArray (row-major, N→S, W→E), or null if entirely no-data.
     */
    private fun processCell(
        cellLat: Int,
        cellLon: Int,
        zoom: Int,
        gridSize: Int,
        encoding: TerrainRgbCodec.Encoding,
        resampling: HgtResampling,
        reader: MmapPmTilesReader,
        isGzip: Boolean,
    ): ShortArray? {
        val grid = ShortArray(gridSize * gridSize) { NODATA.toInt().toShort() }

        // Per-cell tile cache: decoded elevation grids keyed by packed (tileX, tileY).
        // Uses a sentinel (NO_TILE) for absent tiles so getOrPut() doesn't re-fetch them.
        val tileCache = HashMap<Long, TerrainRgbCodec.DecodedTile>()

        // Pre-compute fractional tile Y for each row (latitude)
        // Row 0 = north edge (cellLat + 1°), last row = south edge (cellLat)
        val rowTileY = DoubleArray(gridSize) { row ->
            val lat = cellLat + 1.0 - row.toDouble() / (gridSize - 1)
            val clampedLat = lat.coerceIn(-WEB_MERCATOR_MAX_LAT, WEB_MERCATOR_MAX_LAT)
            latToTileY(clampedLat, zoom)
        }

        // Pre-compute fractional tile X for each column (longitude)
        // Column 0 = west edge (cellLon), last column = east edge (cellLon + 1°)
        val colTileX = DoubleArray(gridSize) { col ->
            val lon = cellLon + col.toDouble() / (gridSize - 1)
            lonToTileX(lon, zoom)
        }

        var hasData = false

        for (row in 0 until gridSize) {
            val tileYf = rowTileY[row]

            for (col in 0 until gridSize) {
                val tileXf = colTileX[col]

                // Interpolate elevation from source pixels using the configured method
                val elevation = when (resampling) {
                    HgtResampling.BILINEAR -> sampleBilinear(tileXf, tileYf, zoom, tileCache, reader, encoding, isGzip)
                    HgtResampling.BICUBIC -> sampleBicubic(tileXf, tileYf, zoom, tileCache, reader, encoding, isGzip)
                }

                if (!elevation.isNaN()) {
                    grid[row * gridSize + col] = elevation.roundToInt()
                        .coerceIn(-32767, 32767)
                        .toShort()
                    hasData = true
                }
            }
        }

        return if (hasData) grid else null
    }

    // ── Tile loading ─────────────────────────────────────────────────────────

    /**
     * Loads a tile from the PMTiles reader and decodes it to elevations via [TerrainRgbCodec].
     *
     * @return Decoded tile with elevation grid, or null if the tile doesn't exist.
     */
    private fun loadAndDecodeTile(
        tileX: Int, tileY: Int, zoom: Int,
        reader: MmapPmTilesReader,
        encoding: TerrainRgbCodec.Encoding,
        isGzip: Boolean,
    ): TerrainRgbCodec.DecodedTile? {
        val raw = reader.getTile(zoom, tileX, tileY) ?: return null
        return TerrainRgbCodec.decodeTileToElevations(raw, encoding, isGzip)
    }

    // ── Bilinear interpolation ───────────────────────────────────────────────

    /**
     * Samples elevation at a fractional tile coordinate using bilinear interpolation.
     *
     * Finds the 4 nearest source pixels (which may span adjacent tiles at boundaries),
     * then blends them based on the fractional position.
     *
     * Returns [Float.NaN] if any of the 4 samples is no-data (avoids blending
     * real elevation with void values at coastlines/data boundaries).
     */
    private fun sampleBilinear(
        tileXf: Double, tileYf: Double,
        zoom: Int,
        cache: HashMap<Long, TerrainRgbCodec.DecodedTile>,
        reader: MmapPmTilesReader,
        encoding: TerrainRgbCodec.Encoding,
        isGzip: Boolean,
    ): Float {
        val tileX = tileXf.toInt()
        val tileY = tileYf.toInt()

        val pixelXf = (tileXf - tileX) * TILE_SIZE
        val pixelYf = (tileYf - tileY) * TILE_SIZE

        val px0 = pixelXf.toInt().coerceIn(0, TILE_SIZE - 1)
        val py0 = pixelYf.toInt().coerceIn(0, TILE_SIZE - 1)
        val px1 = px0 + 1
        val py1 = py0 + 1

        val fx = (pixelXf - px0).toFloat()
        val fy = (pixelYf - py0).toFloat()

        val e00 = getElevationAt(tileX, tileY, px0, py0, zoom, cache, reader, encoding, isGzip)
        val e10 = getElevationAt(tileX, tileY, px1, py0, zoom, cache, reader, encoding, isGzip)
        val e01 = getElevationAt(tileX, tileY, px0, py1, zoom, cache, reader, encoding, isGzip)
        val e11 = getElevationAt(tileX, tileY, px1, py1, zoom, cache, reader, encoding, isGzip)

        if (e00.isNaN() || e10.isNaN() || e01.isNaN() || e11.isNaN()) return Float.NaN

        return (1 - fx) * (1 - fy) * e00 +
                fx * (1 - fy) * e10 +
                (1 - fx) * fy * e01 +
                fx * fy * e11
    }

    /**
     * Samples elevation using bicubic interpolation (Keys convolution, a = -0.5).
     *
     * Uses a 4×4 pixel neighborhood for sharper results than bilinear.
     * Returns [Float.NaN] if any of the 16 samples is no-data.
     */
    private fun sampleBicubic(
        tileXf: Double, tileYf: Double,
        zoom: Int,
        cache: HashMap<Long, TerrainRgbCodec.DecodedTile>,
        reader: MmapPmTilesReader,
        encoding: TerrainRgbCodec.Encoding,
        isGzip: Boolean,
    ): Float {
        val tileX = tileXf.toInt()
        val tileY = tileYf.toInt()

        val pixelXf = (tileXf - tileX) * TILE_SIZE
        val pixelYf = (tileYf - tileY) * TILE_SIZE

        // Center pixel of the 4×4 neighborhood (pixel containing the sample point)
        val px1 = pixelXf.toInt().coerceIn(0, TILE_SIZE - 1)
        val py1 = pixelYf.toInt().coerceIn(0, TILE_SIZE - 1)

        // Fractional offset within the center pixel [0, 1)
        val fx = (pixelXf - px1).toFloat()
        val fy = (pixelYf - py1).toFloat()

        // 4×4 neighborhood: px1-1, px1, px1+1, px1+2 (same for Y)
        // Sample all 16 pixels
        val samples = FloatArray(16)
        for (dy in -1..2) {
            for (dx in -1..2) {
                val e = getElevationAt(tileX, tileY, px1 + dx, py1 + dy, zoom, cache, reader, encoding, isGzip)
                if (e.isNaN()) return Float.NaN
                samples[(dy + 1) * 4 + (dx + 1)] = e
            }
        }

        // Interpolate 4 rows along X, then interpolate results along Y
        val col0 = cubicInterp(samples[0], samples[1], samples[2], samples[3], fx)
        val col1 = cubicInterp(samples[4], samples[5], samples[6], samples[7], fx)
        val col2 = cubicInterp(samples[8], samples[9], samples[10], samples[11], fx)
        val col3 = cubicInterp(samples[12], samples[13], samples[14], samples[15], fx)

        return cubicInterp(col0, col1, col2, col3, fy)
    }

    /**
     * 1D cubic interpolation using the Keys convolution kernel (a = -0.5).
     *
     * Given 4 equally-spaced samples p0..p3 and fractional position t ∈ [0,1)
     * between p1 and p2, returns the interpolated value.
     *
     * The kernel weights are:
     *   w0 = -0.5t³ + t² - 0.5t
     *   w1 =  1.5t³ - 2.5t² + 1
     *   w2 = -1.5t³ + 2t² + 0.5t
     *   w3 =  0.5t³ - 0.5t²
     */
    private fun cubicInterp(p0: Float, p1: Float, p2: Float, p3: Float, t: Float): Float {
        val t2 = t * t
        val t3 = t2 * t
        return p1 + 0.5f * t * (p2 - p0) +
                t2 * (2f * p0 - 5f * p1 + 4f * p2 - p3) * 0.5f +
                t3 * (-p0 + 3f * p1 - 3f * p2 + p3) * 0.5f
    }

    /** Sentinel for absent tiles — cached to avoid re-fetching from PMTiles. */
    private val NO_TILE = TerrainRgbCodec.DecodedTile(0, 0, FloatArray(0))

    private fun getElevationAt(
        tileX: Int, tileY: Int,
        px: Int, py: Int,
        zoom: Int,
        cache: HashMap<Long, TerrainRgbCodec.DecodedTile>,
        reader: MmapPmTilesReader,
        encoding: TerrainRgbCodec.Encoding,
        isGzip: Boolean,
    ): Float {
        var tx = tileX
        var ty = tileY
        var x = px
        var y = py
        // Handle pixel underflow into previous tile (needed for bicubic's -1 offset)
        if (x < 0) { tx--; x += TILE_SIZE }
        if (y < 0) { ty--; y += TILE_SIZE }
        // Handle pixel overflow into next tile
        if (x >= TILE_SIZE) { tx++; x -= TILE_SIZE }
        if (y >= TILE_SIZE) { ty++; y -= TILE_SIZE }

        val maxTile = 1 shl zoom
        tx = ((tx % maxTile) + maxTile) % maxTile

        if (ty < 0 || ty >= maxTile) return Float.NaN

        val key = packXY(tx, ty)
        val tile = cache.getOrPut(key) {
            loadAndDecodeTile(tx, ty, zoom, reader, encoding, isGzip) ?: NO_TILE
        }
        if (tile === NO_TILE) return Float.NaN

        val cx = x.coerceIn(0, tile.width - 1)
        val cy = y.coerceIn(0, tile.height - 1)

        return tile.elevations[cy * tile.width + cx]
    }

    /** Packs two tile indices into a single Long key for the cache HashMap. */
    private fun packXY(x: Int, y: Int): Long = (x.toLong() shl 32) or (y.toLong() and 0xFFFFFFFFL)

    // ── Coordinate conversion (WGS84 ↔ Web Mercator tiles) ──────────────────

    private fun lonToTileX(lon: Double, zoom: Int): Double {
        return (lon + 180.0) / 360.0 * (1 shl zoom)
    }

    private fun latToTileY(lat: Double, zoom: Int): Double {
        val latRad = Math.toRadians(lat)
        return (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * (1 shl zoom)
    }

    // ── HGT file I/O ────────────────────────────────────────────────────────

    private fun writeHgtFile(outputDir: Path, lat: Int, lon: Int, grid: ShortArray) {
        val file = outputDir.resolve(hgtFilename(lat, lon))
        DataOutputStream(BufferedOutputStream(FileOutputStream(file.toFile()), HGT_WRITE_BUFFER)).use { dos ->
            for (value in grid) {
                dos.writeShort(value.toInt())
            }
        }
    }

    private fun hgtFilename(lat: Int, lon: Int): String {
        val latPrefix = if (lat >= 0) "N" else "S"
        val lonPrefix = if (lon >= 0) "E" else "W"
        return "${latPrefix}${abs(lat).toString().padStart(2, '0')}" +
                "${lonPrefix}${abs(lon).toString().padStart(3, '0')}.hgt"
    }
}