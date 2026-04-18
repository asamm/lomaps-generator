package com.asamm.osmTools.utils

import kotlin.math.*

/**
 * Utility functions for Web Mercator (EPSG:3857) tile coordinate conversions.
 *
 * All functions follow the standard slippy map tile naming convention:
 * - Tile (0, 0) is the top-left (north-west) corner at any zoom level.
 * - X increases eastward, Y increases southward.
 * - Zoom level 0 has a single tile covering the whole world.
 *
 * The valid latitude range for Web Mercator is approximately ±85.0511°,
 * beyond which the Mercator projection diverges to infinity.
 */
object MercatorUtils {

    /**
     * Maximum latitude (in degrees) representable in Web Mercator projection.
     * Corresponds to atan(sinh(π)) ≈ 85.0511287798066°.
     */
    const val WEB_MERCATOR_MAX_LAT = 85.0511287798066

    // ── Tile → Geographic ───────────────────────────────────────────────────

    /**
     * Converts a tile X index to the longitude of its western (left) edge.
     *
     * @param x    Tile column index.
     * @param zoom Zoom level.
     * @return Longitude in degrees (−180 to +180).
     */
    fun tileXToLon(x: Int, zoom: Int): Double {
        return x.toDouble() / (1 shl zoom) * 360.0 - 180.0
    }

    /**
     * Converts a tile Y index to the latitude of its northern (top) edge.
     *
     * @param y    Tile row index (0 = north).
     * @param zoom Zoom level.
     * @return Latitude in degrees.
     */
    fun tileYToLat(y: Int, zoom: Int): Double {
        val n = PI - 2.0 * PI * y.toDouble() / (1 shl zoom)
        return Math.toDegrees(atan(sinh(n)))
    }

    /**
     * Converts a **fractional** tile Y coordinate to latitude.
     *
     * Useful for computing the latitude of individual pixels within a tile:
     * pixel row `py` in tile `tileY` corresponds to `y = tileY + (py + 0.5) / tileSize`.
     *
     * @param y    Fractional tile row (e.g. 3.5 = midpoint of tile row 3).
     * @param zoom Zoom level.
     * @return Latitude in degrees.
     */
    fun tileYToLat(y: Double, zoom: Int): Double {
        val n = PI - 2.0 * PI * y / (1 shl zoom)
        return Math.toDegrees(atan(sinh(n)))
    }

    // ── Geographic → Tile (integer — tile index) ────────────────────────────

    /**
     * Converts a longitude to the integer tile X index at the given zoom level.
     *
     * @param lon  Longitude in degrees.
     * @param zoom Zoom level.
     * @return Tile column index.
     */
    fun lonToTileX(lon: Double, zoom: Int): Int {
        return ((lon + 180.0) / 360.0 * (1 shl zoom)).toInt()
    }

    /**
     * Converts a latitude to the integer tile Y index at the given zoom level.
     *
     * @param lat  Latitude in degrees. Must be within ±[WEB_MERCATOR_MAX_LAT].
     * @param zoom Zoom level.
     * @return Tile row index.
     */
    fun latToTileY(lat: Double, zoom: Int): Int {
        val radLat = Math.toRadians(lat)
        return ((1.0 - ln(tan(radLat) + 1 / cos(radLat)) / PI) / 2.0 * (1 shl zoom)).toInt()
    }

    // ── Geographic → Tile (fractional — for sub-tile positioning) ───────────

    /**
     * Converts a longitude to a fractional tile X coordinate.
     *
     * Useful for computing the exact position within a tile (e.g. pixel offset).
     *
     * @param lon  Longitude in degrees.
     * @param zoom Zoom level.
     * @return Fractional tile column.
     */
    fun lonToTileXExact(lon: Double, zoom: Int): Double {
        return (lon + 180.0) / 360.0 * (1 shl zoom)
    }

    /**
     * Converts a latitude to a fractional tile Y coordinate.
     *
     * Useful for computing the exact position within a tile (e.g. pixel offset).
     *
     * @param lat  Latitude in degrees. Must be within ±[WEB_MERCATOR_MAX_LAT].
     * @param zoom Zoom level.
     * @return Fractional tile row.
     */
    fun latToTileYExact(lat: Double, zoom: Int): Double {
        val latRad = Math.toRadians(lat)
        return (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * (1 shl zoom)
    }
}