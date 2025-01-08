/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.asamm.osmTools.sea

import com.asamm.osmTools.Main
import com.asamm.osmTools.cmdCommands.CmdOgr
import com.asamm.osmTools.cmdCommands.CmdOsmium
import com.asamm.osmTools.cmdCommands.CmdShp2osm
import com.asamm.osmTools.config.AppConfig
import com.asamm.osmTools.mapConfig.ItemMap
import com.asamm.osmTools.osm.Node
import com.asamm.osmTools.osm.Tags
import com.asamm.osmTools.osm.Way
import com.asamm.osmTools.utils.Logger
import com.asamm.osmTools.utils.Utils
import com.asamm.osmTools.utils.UtilsHttp
import org.apache.commons.io.FileUtils
import java.io.IOException
import java.nio.file.Path

/**
 *
 * @author volda
 */
class LandArea(var map: ItemMap) {

    private val TAG = LandArea::class.java.simpleName

    val tmpLandPath: Path = AppConfig.config.temporaryDir.resolve("seaCoastline_${map.name}.osm.xml").toAbsolutePath()

    val tmpBorderPath = AppConfig.config.temporaryDir.resolve("seaBorder_${map.name}.osm.xml").toAbsolutePath()

    private var seaBorderMargin: Double = 0.00006 // create sea area little bit smaller

    @Throws(IOException::class, InterruptedException::class)
    fun create() {
        // test if shp file with land polygons exist.
        if (!map.pathShp.toFile().exists()) {
            Main.LOG.info("Starting create shape file with land area: " + map.pathShp)
            createCoastShp()
        } else {
            Main.LOG.info(
                "Shape File with land area for map: ${map.name} already exist."
            )
        }

        // test if osm file with coastline exist
        if (!map.pathCoastline.toFile().exists()) {
            Main.LOG.info("Starting convert shape file with coastlines to OSM file: " + map.pathCoastline)

            // create folders for output
            FileUtils.forceMkdir(map.pathCoastline.parent.toFile())

            // check if map has sea and it's needed to create blue sea rectangle
            if (map.hasSea()) {
                // create sea border
                createBoundSeaXml()
                // create land to osm
                CmdShp2osm().shp2osm(map.pathShp, tmpLandPath)
                // merge tmp convert shp file with border
                mergeBoundsToCoast()
            } else {
                // convert land shp to osm  xml
                CmdShp2osm().shp2osm(map.pathShp, tmpLandPath)
                // convert OSM xml to final PBF
                CmdOsmium().merge(mutableListOf(tmpLandPath), map.pathCoastline)
            }

            //clean tmp directory
            cleanTmp()
        } else {
            Main.LOG.info("OSM File with land area for map: " + map.name + " already exist.")
        }
    }

    /**
     * Cut SHP Land to country SHP file
     */
    @Throws(IOException::class, InterruptedException::class)
    private fun createCoastShp() {

        if ( !AppConfig.config.coastlineConfig.landPolygonShp.toFile().exists()){
            downloadUnzipLandPolygons()
        }

        // prepare directories
        FileUtils.forceMkdir(map.pathShp.toFile().parentFile)

        // execute generating
        CmdOgr().clipGlobalLandPolyToMapBounds(map)
    }

    private fun downloadUnzipLandPolygons(){

        val pathForDownload = AppConfig.config.coastlineConfig.landPolygonShp.parent.parent.resolve("land_polygon.zip")

        if (UtilsHttp.downloadFile(pathForDownload, AppConfig.config.coastlineConfig.landPolygonUrl)) {
            Logger.i(TAG, "File $pathForDownload successfully downloaded.")
        } else {
            throw IllegalArgumentException("File ${AppConfig.config.coastlineConfig.landPolygonUrl} was not downloaded.")
        }
        // unzip file
        Utils.unzipFile(pathForDownload, AppConfig.config.coastlineConfig.landPolygonShp.parent.parent)

        // rename unpacked folder
        Utils.renameFileQuitly(pathForDownload.parent.resolve("land-polygons-complete-4326"),
            AppConfig.config.coastlineConfig.landPolygonShp.parent, true)

        // delete the downloaded file
        Utils.deleteFileQuietly(pathForDownload)

    }


    /**
     * Function create OSM xml file which contain reactangular as map size.
     */
    @Throws(IOException::class)
    private fun createBoundSeaXml() {
        val sb = StringBuilder()

        val cornerNW = Node()
        cornerNW.id = AppConfig.config.coastlineConfig.nodeBorderId
        cornerNW.lat = map.boundary.maxLat - seaBorderMargin
        cornerNW.lon = map.boundary.minLon + seaBorderMargin
        AppConfig.config.coastlineConfig.nodeBorderId++

        val cornerNE = Node()
        cornerNE.id = AppConfig.config.coastlineConfig.nodeBorderId
        cornerNE.lat = map.boundary.maxLat - seaBorderMargin
        cornerNE.lon = map.boundary.maxLon - seaBorderMargin
        AppConfig.config.coastlineConfig.nodeBorderId++

        val cornerSW = Node()
        cornerSW.id = AppConfig.config.coastlineConfig.nodeBorderId
        cornerSW.lat = map.boundary.minLat + seaBorderMargin
        cornerSW.lon = map.boundary.minLon + seaBorderMargin
        AppConfig.config.coastlineConfig.nodeBorderId++

        val cornerSE = Node()
        cornerSE.id = AppConfig.config.coastlineConfig.nodeBorderId
        cornerSE.lat = map.boundary.minLat + seaBorderMargin
        cornerSE.lon = map.boundary.maxLon - seaBorderMargin
        AppConfig.config.coastlineConfig.nodeBorderId++


        // create way outer rectangular off sea
        val way = Way()

        way.addNode(cornerNW)
        way.addNode(cornerNE)
        way.addNode(cornerSE)
        way.addNode(cornerSW)
        way.addNode(cornerNW)


        // set tags for sea rectangular
        val tags = Tags()
        tags.natural = "sea"
        tags.layer = "-5"

        sb.append(
            """<?xml version="1.0" encoding="UTF-8"?>
<osm version="0.6" generator="Asamm world2vec">"""
        )

        sb.append(cornerNW.toXmlString())
        sb.append(cornerNE.toXmlString())
        sb.append(cornerSW.toXmlString())
        sb.append(cornerSE.toXmlString())

        sb.append(way.toXmlString(tags, AppConfig.config.coastlineConfig.nodeBorderId++))

        sb.append("\n</osm>")


        // write to the file
        Main.LOG.info("Writing sea(map) borders into file: $tmpBorderPath")
        FileUtils.writeStringToFile(tmpBorderPath.toFile(), sb.toString(), false)
    }

    @Throws(IOException::class, InterruptedException::class)
    private fun mergeBoundsToCoast() {
        // firstly test if files for merging exists
        require(tmpLandPath.toFile().exists()) { "Temporary coastline file " + tmpLandPath + "does not exist!" }
        require(tmpBorderPath.toFile().exists()) { "Temporary sea border file " + tmpBorderPath + "does not exist!" }

        // create directory for output
        FileUtils.forceMkdir(map.pathCoastline.parent.toFile())

        // execute merge
        Main.LOG.info("Merge map border and coastlines " + tmpLandPath + " and " + tmpBorderPath + "into file: " + map.pathCoastline)
        val cmdOsmium = CmdOsmium()
        cmdOsmium.merge(mutableListOf(tmpBorderPath, tmpLandPath), map.pathCoastline)
    }

    private fun cleanTmp() {
        Utils.deleteFileQuietly(tmpLandPath)
        Utils.deleteFileQuietly(tmpBorderPath)
    }
}
