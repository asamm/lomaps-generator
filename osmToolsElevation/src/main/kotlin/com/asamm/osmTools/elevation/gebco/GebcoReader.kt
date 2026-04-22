package com.asamm.osmTools.elevation.gebco

import com.asamm.osmTools.utils.Logger
import ucar.ma2.Range
import ucar.ma2.Section
import ucar.nc2.NetcdfFile
import ucar.nc2.dataset.NetcdfDatasets
import java.io.Closeable
import java.nio.file.Files
import java.nio.file.Path

/**
 * Reads GEBCO NetCDF elevation and TID grids and provides elevation sampling
 * with land masking.
 *
 * GEBCO 2024 grids are global regular lat/lon rasters at 15 arc-second resolution
 * (43200 × 86400 pixels). The elevation grid contains bathymetry (negative) and
 * land elevation (positive). The TID grid classifies each pixel:
 *
 * | TID | Meaning |
 * |-----|---------|
 * |  0  | Land    |
 * | >0  | Ocean floor (various source types) |
 *
 * For bathymetry-only output, pixels with TID=0 are treated as no-data.
 *
 * The NetCDF variables are:
 * - `elevation` (or `z`): int16/float, shape [lat, lon]
 * - `tid`: int8/int16, shape [lat, lon]
 * - `lat`: 1D coordinate array (south→north, -90 to +90)
 * - `lon`: 1D coordinate array (west→east, -180 to +180)
 *
 * Thread safety: a single instance is NOT thread-safe. Use one per thread or
 * synchronize externally. Multiple instances reading different files are fine.
 */
class GebcoReader private constructor(
    private val elevationNc: NetcdfFile,
    private val tidNc: NetcdfFile,
    private val lat: DoubleArray,
    private val lon: DoubleArray,
) : Closeable {

    companion object {
        private const val TAG = "GebcoReader"

        /** TID value indicating land — these pixels are masked as no-data. */
        const val TID_LAND = 0

        /**
         * Opens GEBCO elevation and TID NetCDF files from directories.
         *
         * Finds the first .nc file in each directory.
         *
         * @param elevationDir Directory containing the unpacked GEBCO elevation .nc file.
         * @param tidDir       Directory containing the unpacked GEBCO TID .nc file.
         * @return A new [GebcoReader] instance. Caller must [close] it when done.
         */
        fun open(elevationDir: Path, tidDir: Path): GebcoReader {
            val elevFile = findNcFile(elevationDir, "elevation")
            val tidFile = findNcFile(tidDir, "TID")

            Logger.i(TAG, "Opening GEBCO elevation: $elevFile")
            Logger.i(TAG, "Opening GEBCO TID: $tidFile")

            val elevNc = NetcdfDatasets.openFile(elevFile.toString(), null)
            val tidNc = try {
                NetcdfDatasets.openFile(tidFile.toString(), null)
            } catch (e: Exception) {
                elevNc.close()
                throw e
            }

            // Read coordinate arrays
            val lat = readCoordinateArray(elevNc, "lat")
            val lon = readCoordinateArray(elevNc, "lon")

            Logger.i(
                TAG, "GEBCO grid: ${lat.size} × ${lon.size} " +
                        "(lat ${lat.first()}..${lat.last()}, lon ${lon.first()}..${lon.last()})"
            )

            return GebcoReader(elevNc, tidNc, lat, lon)
        }

        /** Finds the first .nc file in [dir]. Throws if none found. */
        private fun findNcFile(dir: Path, label: String): Path {
            return Files.list(dir).use { stream ->
                stream.filter { it.fileName.toString().lowercase().endsWith(".nc") }
                    .findFirst()
                    .orElseThrow { IllegalStateException("No .nc file found in $dir for $label data") }
            }
        }

        /** Search for lat and lon */
        private fun readCoordinateArray(nc: NetcdfFile, varName: String): DoubleArray {

            val variable = nc.findVariable(varName)
                ?: throw IllegalStateException("Variable '$varName' not found in ${nc.location}")
            val data = variable.read()
            return DoubleArray(data.size.toInt()) { data.getDouble(it) }
        }
    }

    /** Number of latitude points in the grid. */
    val latSize: Int get() = lat.size

    /** Number of longitude points in the grid. */
    val lonSize: Int get() = lon.size

    /** Latitude resolution in degrees (distance between adjacent grid points). */
    val latStep: Double = if (lat.size > 1) lat[1] - lat[0] else 0.0

    /** Longitude resolution in degrees. */
    val lonStep: Double = if (lon.size > 1) lon[1] - lon[0] else 0.0

    /** South boundary of the grid. */
    val latMin: Double get() = lat.first()

//    /** North boundary of the grid. */
//    val latMax: Double get() = lat.last()

    /** West boundary of the grid. */
    val lonMin: Double get() = lon.first()

//    /** East boundary of the grid. */
//    val lonMax: Double get() = lon.last()

    /**
     * Reads a rectangular block of elevation data from the GEBCO grid.
     *
     * @param latStart         Start latitude index (inclusive).
     * @param lonStart         Start longitude index (inclusive).
     * @param latCount         Number of latitude rows to read.
     * @param lonCount         Number of longitude columns to read.
     * @param clampLandToZero  When **true**, land pixels (TID=0) and positive elevations
     *                         are clamped to 0 (sea level) instead of [Float.NaN]. This
     *                         produces filled tiles where coastlines render as 0 m depth.
     *                         When **false**, land pixels are set to [Float.NaN] (no-data),
     *                         producing transparent holes suitable for compositing with
     *                         a separate land/terrain layer.
     * @param latStride        Read every Nth latitude row (1 = full resolution).
     *                         Use a stride > 1 for low zoom levels where the source
     *                         region is much larger than the tile and the full block
     *                         would exceed Java array limits.
     * @param lonStride        Read every Nth longitude column (1 = full resolution).
     * @return Float array of size `ceil(latCount/latStride) × ceil(lonCount/lonStride)`,
     *         row-major. Values are elevation in meters; land is either 0 or [Float.NaN]
     *         depending on [clampLandToZero].
     */
    fun readElevationBlock(
        latStart: Int, lonStart: Int, latCount: Int, lonCount: Int,
        clampLandToZero: Boolean = false,
        latStride: Int = 1,
        lonStride: Int = 1,
    ): FloatArray {
        val elevVar = elevationNc.findVariable("elevation")
            ?: elevationNc.findVariable("z")
            ?: throw IllegalStateException("No 'elevation' or 'z' variable in ${elevationNc.location}")

        val tidVar = tidNc.findVariable("tid")
            ?: tidNc.findVariable("TID")
            ?: throw IllegalStateException("No 'tid' or 'TID' variable in ${tidNc.location}")

        val section = Section(
            Range(latStart, latStart + latCount - 1, latStride), // every Nth lat row
            Range(lonStart, lonStart + lonCount - 1, lonStride), // every Nth lon column
        )

        val elevData = elevVar.read(section) // depth/elevation in metres
        val tidData = tidVar.read(section)   // terrain-type flags (land vs. water)

        val noDataValue = if (clampLandToZero) 0.0f else Float.NaN // land fill value

        val resultSize = elevData.size.toInt()
        val result = FloatArray(resultSize)
        for (i in result.indices) {
            val tid = tidData.getInt(i)
            if (tid == TID_LAND) {
                result[i] = noDataValue // land pixel — fill or NaN
            } else {
                val elev = elevData.getFloat(i)
                // Positive elevation in ocean-classified pixels (small islands, reefs,
                // interpolation artifacts) is clamped to sea level when land clamping is on.
                result[i] = if (clampLandToZero && elev > 0f) 0.0f else elev
            }
        }

        return result
    }

    /**
     * Converts a latitude value to the nearest grid index.
     *
     * Values outside the grid are clamped to the first/last index so that
     * tile edges at exactly ±90° (or the Web Mercator limit) still resolve
     * to a valid grid row instead of producing an error.
     */
    fun latToIndex(latitude: Double): Int {
        return ((latitude - latMin) / latStep).toInt().coerceIn(0, lat.size - 1)
    }

    /**
     * Converts a longitude value to the nearest grid index.
     *
     * Values outside the grid (e.g. exactly +180° when the last pixel center
     * is ~179.996°) are clamped to the boundary index.
     */
    fun lonToIndex(longitude: Double): Int {
        return ((longitude - lonMin) / lonStep).toInt().coerceIn(0, lon.size - 1)
    }

    /**
     * Returns the latitude at grid index [idx].
     */
    fun latAtIndex(idx: Int): Double = lat[idx]

    /**
     * Returns the longitude at grid index [idx].
     */
    fun lonAtIndex(idx: Int): Double = lon[idx]

    override fun close() {
        elevationNc.close()
        tidNc.close()
    }
}

