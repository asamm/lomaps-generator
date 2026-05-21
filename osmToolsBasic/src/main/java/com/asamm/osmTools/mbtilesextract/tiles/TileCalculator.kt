package com.asamm.osmTools.mbtilesextract.tiles

import com.asamm.geoutils.PolyReader
import com.asamm.osmTools.utils.MercatorUtils
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.Polygon
import org.locationtech.jts.geom.prep.PreparedGeometryFactory
import org.locationtech.jts.operation.union.CascadedPolygonUnion
import java.io.File
import java.util.stream.Collectors

class TileCalculator {

    private val geometryFactory: GeometryFactory = GeometryFactory()

    /**
     * Computes the list of map tiles (x, y, z) that cover the area defined by the polygon in the given file.
     *
     * @param polyFile The file containing the polygon geometry.
     * @param maxZoom The maximum zoom level to compute tiles for (default is 14).
     * @return A list of map tiles represented as triples (x, y, z).
     */
    fun computeTiles(polyFile: File, maxZoom: Int = 14): List<Triple<Int, Int, Int>> {
        val polyGeom = PolyReader().read(polyFile)
        return computeTiles(polyGeom, maxZoom)
    }

    /**
     * Computes tiles at [maxZoom] that intersect [geometry], including correct handling of holes
     * (tiles fully inside a hole are excluded). Uses [PreparedGeometryFactory] for fast repeated
     * intersection tests and parallel processing across tile columns.
     *
     * Lower zoom levels are not computed here — [mergeTiles] compresses the result upward.
     *
     * @param geometry The JTS geometry (Polygon or MultiPolygon) defining the area.
     * @param maxZoom The maximum zoom level to compute tiles for (default is 14).
     * @return A list of map tiles represented as triples (x, y, z).
     */
    fun computeTiles(geometry: Geometry, maxZoom: Int = 14): List<Triple<Int, Int, Int>> {
        val prepared = PreparedGeometryFactory().create(geometry)
        val envelope = geometry.envelopeInternal
        val minTileX = MercatorUtils.lonToTileX(envelope.minX, maxZoom)
        val maxTileX = MercatorUtils.lonToTileX(envelope.maxX, maxZoom)
        val minTileY = MercatorUtils.latToTileY(envelope.maxY, maxZoom)
        val maxTileY = MercatorUtils.latToTileY(envelope.minY, maxZoom)

        return (minTileX..maxTileX).toList().parallelStream().flatMap { x ->
            (minTileY..maxTileY).mapNotNull { y ->
                if (prepared.intersects(tileToPolygon(x, y, maxZoom))) Triple(x, y, maxZoom) else null
            }.stream()
        }.collect(Collectors.toList())
    }

    /**
     * Computes the tile coverage geometry for the polygon in the given file.
     * Convenience wrapper around [computeTiles] + [createTileCoverageGeometry].
     */
    fun computeTileCoverageGeometry(polyFile: File, maxZoom: Int = 14): Geometry =
        createTileCoverageGeometry(computeTiles(polyFile, maxZoom))

    /**
     * Creates a JTS geometry that covers the list of tiles, preserving holes where tiles are absent.
     *
     * @param tiles The list of tiles represented as triples (x, y, z).
     * @return A JTS geometry that covers the tiles, with holes where tiles are missing.
     */
    fun createTileCoverageGeometry(tiles: List<Triple<Int, Int, Int>>): Geometry {
        val mergedTiles = mergeTiles(tiles.toSet())
        val polygons = mergedTiles.toList().parallelStream()
            .map { (x, y, z) -> tileToPolygon(x, y, z) }
            .collect(Collectors.toList())
        return CascadedPolygonUnion(polygons).union()
    }

    /**
     * Merges tiles by replacing 4 sibling tiles at zoom Z with their parent at zoom Z-1,
     * but only when all 4 siblings are present (preserving holes in the tile set).
     * Returns a compressed set of tiles spanning multiple zoom levels.
     */
    fun mergeTiles(tiles: Set<Triple<Int, Int, Int>>): Set<Triple<Int, Int, Int>> {
        val tilesByZoom = tiles.groupBy { it.third }.toSortedMap(reverseOrder())
        val remainingTiles = tiles.filter { it.third != 0 }.toMutableSet()

        for (zoom in tilesByZoom.keys.sortedDescending()) {
            if (zoom == 0) break

            val parentTiles = mutableSetOf<Triple<Int, Int, Int>>()

            for ((x, y, z) in tilesByZoom[zoom] ?: emptyList()) {
                val parentTile = Triple(x / 2, y / 2, z - 1)
                val siblings = listOf(
                    Triple(parentTile.first * 2,     parentTile.second * 2,     zoom),
                    Triple(parentTile.first * 2 + 1, parentTile.second * 2,     zoom),
                    Triple(parentTile.first * 2,     parentTile.second * 2 + 1, zoom),
                    Triple(parentTile.first * 2 + 1, parentTile.second * 2 + 1, zoom)
                )
                if (siblings.all { it in remainingTiles }) {
                    remainingTiles.removeAll(siblings.toSet())
                    parentTiles.add(parentTile)
                }
            }

            remainingTiles.addAll(parentTiles)
        }

        return remainingTiles
    }

    fun tileToPolygon(x: Int, y: Int, zoom: Int): Polygon {
        val lonMin = MercatorUtils.tileXToLon(x, zoom)
        val lonMax = MercatorUtils.tileXToLon(x + 1, zoom)
        val latMin = MercatorUtils.tileYToLat(y + 1, zoom)
        val latMax = MercatorUtils.tileYToLat(y, zoom)

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