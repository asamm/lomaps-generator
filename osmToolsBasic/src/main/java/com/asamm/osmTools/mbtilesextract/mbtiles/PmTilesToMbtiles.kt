package com.asamm.osmTools.mbtilesextract.mbtiles

import com.asamm.geoutils.PolyReader
import com.asamm.osmTools.config.AppConfig
import com.asamm.osmTools.generatorDb.utils.GeomUtils
import com.asamm.osmTools.mbtilesextract.tiles.Tile
import com.asamm.osmTools.mbtilesextract.tiles.TileCoord
import com.asamm.osmTools.utils.Logger
import com.asamm.pmtiles.PmTilesExtract
import com.asamm.pmtiles.TileType
import net.minidev.json.JSONObject
import net.minidev.json.JSONValue
import java.nio.file.Files
import java.nio.file.Path

/**
 * Extracts per-map MBTiles from a planet PMTiles file, filtering to the tile coverage
 * of the supplied polygon. Replaces the previous SQLite-to-SQLite [MbtilesCreator] flow.
 *
 * Y-axis convention: PMTiles delivers tiles in XYZ (y=0 at north). MBTiles stores TMS
 * (y=0 at south). The conversion `tmsY = (2^z - 1) - xyzY` is applied on every tile.
 */
class PmTilesToMbtiles {

    companion object {
        private const val TAG = "PmTilesToMbtiles"
    }

    /**
     * Extracts tiles from [planetPmtiles] that intersect [polygon], writing them to
     * a new MBTiles file at [outputMbtiles].
     *
     * @param planetPmtiles  Planet PMTiles source (read via mmap — large files are fine).
     * @param outputMbtiles  Destination MBTiles file (created or overwritten).
     * @param polygon        `.poly` file defining the map area.
     * @param name           Map name written into MBTiles metadata.
     * @param minZoom        Minimum zoom level to include (default 0).
     * @param maxZoom        Maximum zoom level to include (default 14).
     */
    fun extract(
        planetPmtiles: Path,
        outputMbtiles: Path,
        polygon: Path,
        name: String,
        minZoom: Int = 0,
        maxZoom: Int = 14,
    ) {
        val polyGeom = PolyReader().read(polygon.toFile())

        if (outputMbtiles.toFile().exists()) {
            Files.delete(outputMbtiles)
        }

        var tileCount = 0

        Mbtiles.newWriteToFileDatabase(outputMbtiles).use { mbtiles ->
            val sourceInfo = PmTilesExtract.forEachTile(
                input = planetPmtiles,
                maxZoom = maxZoom,
                minZoom = minZoom,
                area = polyGeom,
            ) { z, x, y, data ->
                // PMTiles → MBTiles: flip y from XYZ to TMS convention
                val tmsY = (1 shl z) - 1 - y.toInt()
                mbtiles.insertTile(Tile(TileCoord(x.toInt(), tmsY, z), data))
                tileCount++
            }

            Logger.i(TAG, "Extracted $tileCount tiles → ${outputMbtiles.fileName}")

            mbtiles.insertMetadata(
                buildMetadata(name, polyGeom, sourceInfo, minZoom, maxZoom)
            )
        }
    }

    private fun buildMetadata(
        name: String,
        polyGeom: org.locationtech.jts.geom.Geometry,
        sourceInfo: PmTilesExtract.SourceInfo?,
        minZoom: Int,
        maxZoom: Int,
    ): Metadata {
        val metadata = Metadata()

        metadata.setValue(Metadata.NAME_KEY, name)
        metadata.setValue(Metadata.DESCRIPTION_KEY, AppConfig.config.mbtilesConfig.mapDescription)
        metadata.setValue(Metadata.ATTRIBUTION_KEY, AppConfig.config.mbtilesConfig.mapAttribution)
        metadata.setValue(Metadata.VERSION_KEY, AppConfig.config.version)
        metadata.setValue(Metadata.TYPE_KEY, Metadata.TYPE_VALUE_BASELAYER)
        metadata.setValue(Metadata.MINZOOM_KEY, minZoom.toString())
        metadata.setValue(Metadata.MAXZOOM_KEY, maxZoom.toString())

        val mbtilesCreator = MbtilesCreator()
        metadata.setValue(Metadata.BOUNDS_KEY, mbtilesCreator.createMetadataBounds(polyGeom))
        val center = polyGeom.centroid
        metadata.setValue(Metadata.CENTER_KEY, "${center.x},${center.y}")

        metadata.setValue(Metadata.LOMAPS_GEOM_KEY, GeomUtils.geomToGeoJson(polyGeom))

        // Set format from the PMTiles header tile type (more authoritative than the metadata JSON)
        val format = when (sourceInfo?.header?.tileType) {
            TileType.MVT -> "pbf"
            TileType.PNG -> "png"
            TileType.JPEG -> "jpg"
            TileType.WEBP -> "webp"
            else -> null
        }
        format?.let { metadata.setValue(Metadata.FORMAT_KEY, it) }

        // Copy compression and full TileJSON (vector_layers) from the planet PMTiles metadata JSON
        val sourceJson = sourceInfo?.metadataJson?.let { JSONValue.parse(it) as? JSONObject }
        sourceJson?.get("compression")?.let { metadata.setValue(Metadata.COMPRESSION_KEY, it.toString()) }
        sourceJson?.let { metadata.setValue(Metadata.JSON_KEY, it.toJSONString()) }

        return metadata
    }
}
