package com.asamm.osmTools.mbtilesextract.tiles

import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.Polygon
import org.locationtech.jts.operation.union.CascadedPolygonUnion

class TileCalculator {

    private val geometryFactory: GeometryFactory = GeometryFactory()

    /**
     * Computes the list of map tiles (x, y, z) that cover the given geometry from zoom level 0 to the specified zoom level.
     *
     * @param geometry The JTS geometry (Polygon or MultiPolygon) defining the area.
     * @param maxZoom The maximum zoom level to compute tiles for (default is 14).
     * @return A list of map tiles represented as triples (x, y, z).
     */
    fun computeTiles(geometry: Geometry, maxZoom: Int = 14): List<Triple<Int, Int, Int>> {
        val tiles = mutableListOf<Triple<Int, Int, Int>>()

        for (zoom in 0..maxZoom) {
            val envelope = geometry.envelopeInternal
            val minTileX = TileUtils.lonToTileX(envelope.minX, zoom)
            val maxTileX = TileUtils.lonToTileX(envelope.maxX, zoom)
            val minTileY = TileUtils.latToTileY(envelope.maxY, zoom)
            val maxTileY = TileUtils.latToTileY(envelope.minY, zoom)

            for (x in minTileX..maxTileX) {
                for (y in minTileY..maxTileY) {
                    val tileGeometry = tileToPolygon(x, y, zoom)
                    if (geometry.intersects(tileGeometry)) {
                        tiles.add(Triple(x, y, zoom))
                    }
                }
            }
        }

        return tiles
    }


    /**
     * Creates a JTS geometry that exactly covers the list of tiles.
     *
     * @param tiles The list of tiles represented as triples (x, y, z).
     * @param zoom The zoom level of the tiles for this level the geometry will be created.
     * @return A JTS geometry that covers the tiles.
     */
    fun createTileCoverageGeometry(tiles: List<Triple<Int, Int, Int>>): Geometry {

        // Merge tiles to reduce the number of polygons
        val mergedTiles = mergeTiles(tiles.toSet())

        val polygons = mergedTiles.map { (x, y, z) -> tileToPolygon(x, y, z) }

        // Union all polygons to single geometry
        return CascadedPolygonUnion(polygons).union()
    }

    /**
     * Merges tiles by replacing 4 adjacent tiles at zoom Z with a single tile at zoom Z-1.
     * Returns a new list of tiles optimized for coverage.
     */
    fun mergeTiles(tiles: Set<Triple<Int, Int, Int>>): Set<Triple<Int, Int, Int>> {
        // Organize tiles by zoom level
        val tilesByZoom = tiles.groupBy { it.third }.toSortedMap(reverseOrder()) // Start from max zoom

        val mergedTiles = mutableSetOf<Triple<Int, Int, Int>>()
        // remove tile with zoom 0
        val remainingTiles = tiles.filter { it.third != 0 }.toMutableSet()

        for (zoom in tilesByZoom.keys.sortedDescending()) {
            if (zoom == 0) break // Zoom 0 tiles can't be merged further

            val parentTiles = mutableSetOf<Triple<Int, Int, Int>>()

            for ((x, y, z) in tilesByZoom[zoom] ?: emptyList()) {
                val parentTile = Triple(x / 2, y / 2, z - 1)

                // Check if this parent tile is eligible for merging (i.e., all 4 children exist)
                val allChildrenExist = listOf(
                    Triple(parentTile.first * 2, parentTile.second * 2, zoom),
                    Triple(parentTile.first * 2 + 1, parentTile.second * 2, zoom),
                    Triple(parentTile.first * 2, parentTile.second * 2 + 1, zoom),
                    Triple(parentTile.first * 2 + 1, parentTile.second * 2 + 1, zoom)
                ).all { it in remainingTiles }

                if (allChildrenExist) {
                    // Remove all 4 child tiles and replace with parent
                    remainingTiles.removeAll(
                        listOf(
                            Triple(parentTile.first * 2, parentTile.second * 2, zoom),
                            Triple(parentTile.first * 2 + 1, parentTile.second * 2, zoom),
                            Triple(parentTile.first * 2, parentTile.second * 2 + 1, zoom),
                            Triple(parentTile.first * 2 + 1, parentTile.second * 2 + 1, zoom)
                        )
                    )
                    parentTiles.add(parentTile)
                } else {
                    remainingTiles.remove(parentTile)
                }
            }

            // Add merged parent tiles to the set
            remainingTiles.addAll(parentTiles)
        }

        return remainingTiles
    }

    fun tileToPolygon(x: Int, y: Int, zoom: Int): Polygon {
        val lonMin = TileUtils.tileXToLon(x, zoom)
        val lonMax = TileUtils.tileXToLon(x + 1, zoom)
        val latMin = TileUtils.tileYToLat(y + 1, zoom)
        val latMax = TileUtils.tileYToLat(y, zoom)

        val coordinates = arrayOf(
            Coordinate(lonMin, latMin),
            Coordinate(lonMin, latMax),
            Coordinate(lonMax, latMax),
            Coordinate(lonMax, latMin),
            Coordinate(lonMin, latMin)
        )

        return geometryFactory.createPolygon(coordinates)
    }
}