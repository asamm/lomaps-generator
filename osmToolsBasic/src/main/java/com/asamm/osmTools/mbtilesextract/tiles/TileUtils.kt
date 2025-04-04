package com.asamm.osmTools.mbtilesextract.tiles

import kotlin.math.*

object TileUtils {

    /**
     * Converts longitude to tile X coordinate at a given zoom level.
     */
    fun lonToTileX(lon: Double, zoom: Int): Int {
        return ((lon + 180.0) / 360.0 * (1 shl zoom)).toInt()
    }

    /**
     * Converts latitude to tile Y coordinate at a given zoom level.
     */
    fun latToTileY(lat: Double, zoom: Int): Int {
        val radLat = Math.toRadians(lat)
        return ((1.0 - ln(tan(radLat) + 1 / cos(radLat)) / PI) / 2.0 * (1 shl zoom)).toInt()
    }

    /**
     * Converts tile X coordinate to longitude at a given zoom level.
     */
    fun tileXToLon(x: Int, zoom: Int): Double {
        return x.toDouble() / (1 shl zoom) * 360.0 - 180.0
    }

    /**
     * Converts tile Y coordinate to latitude at a given zoom level.
     */
    fun tileYToLat(y: Int, zoom: Int): Double {
        val n = PI - 2.0 * PI * y.toDouble() / (1 shl zoom)
        return Math.toDegrees(atan(sinh(n)))
    }

}