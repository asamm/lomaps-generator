package com.asamm.osmTools.mbtilesextract.mbtiles

import com.asamm.geoutils.PolyReader
import com.asamm.osmTools.config.AppConfig
import com.asamm.osmTools.generatorDb.utils.GeomUtils
import com.asamm.osmTools.mbtilesextract.tiles.TileCalculator
import com.asamm.osmTools.utils.Logger
import com.asamm.osmTools.utils.Utils
import org.locationtech.jts.geom.Geometry
import java.nio.file.Path

class MbtilesCreator {

    val TAG: String = MbtilesCreator::class.java.simpleName

    /**
     * Create MBtiles file from planet PMtiles file and polygon file
     * @param pathMbTilesPlanet path to source Mbtiles file
     * @param pathMbtiles path to target MBtiles file
     * @param pathPolygon path to polygon file defining the area of extraction from planet
     */
    fun createMbtiles(
        pathMbTilesPlanet: Path,
        pathMbtiles: Path,
        pathPolygon: Path,
        name: String,
        minZoom: Int = 0,
        maxZoom: Int = 14
    ) {

        val polyGeom = PolyReader().read(pathPolygon.toFile())

        // compute tiles for polygon
        val tiles: List<Triple<Int, Int, Int>> = TileCalculator().computeTiles(polyGeom, maxZoom)
        Logger.i(TAG, "Number of tiles to load: ${tiles.size}")

        // open planet mbtiles file
        val mbtilesPlanet: Mbtiles = Mbtiles.newReadOnlyDatabase(pathMbTilesPlanet)

        // delete the target mbtiles file if it exists
        if (pathMbtiles.toFile().exists()) {
            Utils.deleteFileQuietly(pathMbtiles)
        }

        // create new mbtiles file and write tiles to it
        Mbtiles.newWriteToFileDatabase(pathMbtiles).use { mbtilesToWrite ->

            // orgganize tiles by zoom
            val tilesByZoom = tiles.groupBy { it.third }

            // iterate over all zoom levels
            var loadedTiles = 0
            for (zoom in minZoom..maxZoom) {
                //Logger.i(TAG, "Processing zoom $zoom")
                val tilesInZoom = tilesByZoom[zoom] ?: continue
                // for every x in zoom level get min y and max y
                for (x in tilesInZoom.groupBy { it.first }) {
                    val minTileY = x.value.minBy { it.second }?.second
                    val maxTileY = x.value.maxBy { it.second }?.second

                    val tileIterator = mbtilesPlanet.getTilesInZoomAndRange(zoom, x.key, minTileY!!, maxTileY!!)

                    while (tileIterator.hasNext()) {
                        val tile = tileIterator.next()
                        //Logger.i(TAG, tile?.coord.toString() + ": " + tile?.bytes?.size)
                        // write tile to mbtiles
                        mbtilesToWrite.insertTile(tile)
                        loadedTiles++
                    }
                }
            }
            Logger.i(TAG, "Number of loaded tiles $loadedTiles")

            // write metadata to mbtiles
            val metadataPlanet = mbtilesPlanet.getMetadata()

            mbtilesToWrite.insertMetadata(createMetadata(metadataPlanet, name, polyGeom, minZoom, maxZoom))
        }
    }

    fun createMetadata(
        metadataPlanet: Metadata,
        name: String,
        polyGeom: Geometry,
        minZoom: Int,
        maxZoom: Int
    ): Metadata {

        val metadata = Metadata()
        // set name
        metadata.setValue(Metadata.NAME_KEY, name)
        metadata.setValue(Metadata.DESCRIPTION_KEY, AppConfig.config.mbtilesConfig.mapDescription)
        metadata.setValue(Metadata.ATTRIBUTION_KEY, AppConfig.config.mbtilesConfig.mapAttribution)
        metadata.setValue(Metadata.VERSION_KEY, AppConfig.config.version)

        metadata.setValue(Metadata.TYPE_KEY, Metadata.TYPE_VALUE_BASELAYER)

        metadata.setValue(Metadata.BOUNDS_KEY, createMetadataBounds(polyGeom))
        metadata.setValue(Metadata.CENTER_KEY, createMetadataCenter(polyGeom))
        metadata.setValue(Metadata.MINZOOM_KEY, minZoom.toString())
        metadata.setValue(Metadata.MAXZOOM_KEY, maxZoom.toString())

        metadataPlanet.getValue(Metadata.JSON_KEY)?.let {
            metadata.setValue(Metadata.JSON_KEY, it)
        }
        metadataPlanet.getValue(Metadata.FORMAT_KEY)?.let {
            metadata.setValue(Metadata.FORMAT_KEY, it)
        }
        metadataPlanet.getValue(Metadata.COMPRESSION_KEY)?.let {
            metadata.setValue(Metadata.COMPRESSION_KEY, it)
        }

        metadata.setValue(Metadata.LOMAPS_GEOM_KEY, GeomUtils.geomToGeoJson(polyGeom))

        return metadata
    }

    private fun createMetadataCenter(polyGeom: Geometry): String {
        val center = polyGeom.centroid
        return "${center.x},${center.y}"
    }

    fun createMetadataBounds(polyGeom: Geometry): String {

        val coordinates = polyGeom.envelope.boundary.coordinates
        val xMin = coordinates.minOf { it.x }
        val xMax = coordinates.maxOf { it.x }
        val yMin = coordinates.minOf { it.y }
        val yMax = coordinates.maxOf { it.y }
        //val boundsString = "${bounds.},${bounds.minY},${bounds.maxX},${bounds.maxY}"
        val boundsString = "$xMin,$yMin,$xMax,$yMax"

        return boundsString
    }


}