/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.asamm.osmTools.cmdCommands

import com.asamm.osmTools.Parameters
import com.asamm.osmTools.mapConfig.ItemMap
import com.asamm.osmTools.sea.Boundaries
import com.asamm.osmTools.utils.Logger
import java.io.File
import java.io.IOException
import java.util.*

/**
 *
 * @author volda
 */
class CmdGenerate(val map: ItemMap) : Cmd(ExternalApp.OSMOSIS), CmdOsmosis {
    private var isRam = true

    init {
        // if is there action to merge test files for merge
        if (map.isMerged) {
            require(map.pathMerge.toFile().exists()) {
                "Merged map for generation: " +
                        map.pathMerge + " does not exist!"
            }

            require(File(Parameters.mTouristTagMapping).exists()) {
                ("Map writter definition file:  "
                        + Parameters.mTouristTagMapping + " does not exist.")
            }

            if (!map.pathMerge.toFile().exists()) {
                map.isMerged = false
            }


            // getMap().isMerged = !new getMap().mergePath+"."+Parameters.contourNoSRTM).exists();
        } else require(
            map.pathSource.toFile().exists()
        ) { "Extracted map for generation = " + map.pathSource + " does not exist." }
    }


    @Throws(IOException::class, InterruptedException::class)
    fun execute(numRepeat: Int, deleteFile: Boolean): String? {
        var numRepeat = numRepeat
        try {
            return execute()
        } catch (e: Exception) {
            if (numRepeat > 0) {
                numRepeat--
                val generatedFile = map.pathGenerate.toFile()

                if (deleteFile) {
                    Logger.w(TAG, "Delete file from previous not success execution: " + generatedFile.absolutePath)
                    if (generatedFile.exists()) {
                        generatedFile.delete()
                    }
                }
                Logger.w(TAG, "Re-execute generation: " + getCmdLine())
                execute(numRepeat, deleteFile)
            } else {
                throw e
            }
        }
        return null
    }

    @Throws(IOException::class)
    fun createCmdContour() {
        addReadPbf(map.pathContour.toString())
        addMapWriterContour()
        addType()
        addPrefLang()
        addBbox(map.boundary)
        addTagConf(Parameters.getContourTagMapping())
        addZoomIntervalContour()
        addBboxEnlargement(1)
        //ddWayClipping();
        //addMapComment();
    }

    @Throws(IOException::class)
    fun createCmd() {
        //TODO remove
        //addCommand("-v");

        // if is there merged map generate map from merged. otherwise create from exported

        if (map.isMerged) {
            addReadPbf(map.pathMerge.toString())
        } else {
            addReadPbf(map.pathSource.toString())
        }

        //addBuffer();
        addMapWriter()
        addType()
        addPrefLang()
        addBbox(map.boundary)
        addTagConf(Parameters.mTouristTagMapping)
        //addDebugFile();
        addZoomInterval()

        addSimplificationFactor()
        addBboxEnlargement(5)

        addLabelPosition()

        addCommand("tag-values=true")

        //addMapboxPolylabel();

        // get num of cpu cores
        val cores = Runtime.getRuntime().availableProcessors()

        addThreads(cores)

        addMapComment()
    }


    @Throws(IOException::class)
    private fun addMapWriter() {
        // prepare directory
        prepareDirectory(map.pathGenerate.toString())

        // add commands
        addCommand("--mapfile-writer")
        addCommand("file=" + map.pathGenerate)
    }

    @Throws(IOException::class)
    private fun addMapWriterContour() {
        // prepare directory
        prepareDirectory(map.pathGenerateContour.toString())

        // add commands
        addCommand("--mapfile-writer")
        addCommand("file=" + map.pathGenerateContour)
    }

    private fun addPrefLang() {
        if (map.prefLang != null && map.prefLang.length > 0) {
            addCommand("preferred-languages=" + map.prefLang)
        }
    }

    private fun addMapComment() {
        addCommand("comment=" + Parameters.MAP_COMMENT)
    }

    private fun addLabelPosition() {
        addCommand("label-position=true")
    }

    private fun addMapboxPolylabel() {
        addCommand("polylabel=true")
    }

    private fun addThreads(threads: Int) {
        addCommand("threads=$threads")
    }

    private fun addType() {
        //firstly decide based on forceType from config xml
        if (map.forceType != null && !map.forceType.isEmpty()) {
            if (map.forceType.lowercase(Locale.getDefault()) == "hd") {
                addCommand("type=hd")
                return
            }
            if (map.forceType.lowercase(Locale.getDefault()) == "ram") {
                addCommand("type=ram")
                isRam = true
                return
            }
        }


        //set FileSizeLimit determined when use HD and when RAM
        val fileSizeLimit = 650
        // get map size
        var mapSizeMb = map.pathSource.toFile().length()
        if (map.isMerged) {
            mapSizeMb = map.pathMerge.toFile().length()
        }
        mapSizeMb = mapSizeMb / 1024 / 1024
        //System.out.println("Velikost souboru "+getMap().file+" je:  "+ mapSizeMb);
        if (mapSizeMb < fileSizeLimit) {
            addCommand("type=ram")
            //set boolean value for coastline
            isRam = true
        } else {
            addCommand("type=hd")
        }
    }

    private fun addZoomIntervalContour() {
        addCommand("zoom-interval-conf=12,10,12,15,13,21")
    }

    private fun addSimplificationFactor() {
        //default
        //addCommand("simplification-factor=2.5");
        //addCommand("simplification-factor=5");
        addCommand("simplification-factor=0.5")
    }

    private fun addWayClipping() {
        addCommand("way-clipping=false")
    }


    private fun addZoomInterval() {
        //default
        // addCommand(" zoom-interval-conf=5,0,7,10,8,11,14,12,21"); default
        // kech 5,0,6,8,7,9,11,10,12,15,13,21
        // muj novy
        //addCommand("zoom-interval-conf=6,0,7,9,8,9,12,10,12,15,13,21");
        // new intervals
        //addCommand("zoom-interval-conf=5,0,6,8,7,9,11,10,12,15,13,21");

        if (map.forceInterval != null && !map.forceInterval.isEmpty()) {
            addCommand("zoom-interval-conf=" + map.forceInterval)
        }
    }

    private fun addBbox(bounds: Boundaries) {
        addCommand(
            ("bbox="
                    + bounds.minLat.toString() + "," + bounds.minLon.toString() + "," + bounds.maxLat.toString() + "," + bounds.maxLon.toString())
        )
    }

    private fun addDebugFile() {
        addCommand("debug-file=true")
    }

    private fun addTagConf(path: String) {
        addCommand("tag-conf-file=$path")
    }

    private fun addBboxEnlargement(pixels: Int) {
        addCommand("bbox-enlargement=$pixels")
    }

    companion object {
        private val TAG: String = CmdGenerate::class.java.simpleName
    }
}
