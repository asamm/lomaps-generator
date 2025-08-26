/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.asamm.osmTools.cmdCommands

import com.asamm.osmTools.config.AppConfig
import com.asamm.osmTools.mapConfig.ItemMap
import com.asamm.osmTools.sea.Boundaries
import com.asamm.osmTools.utils.Logger
import java.io.IOException
import java.nio.file.Path
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

            require(AppConfig.config.mapsforgeConfig.tagMapping.toFile().exists()) {
                ("Map writter definition file:  ${AppConfig.config.mapsforgeConfig.tagMapping} does not exist.")
            }

            if (!map.pathMerge.toFile().exists()) {
                map.isMerged = false
            }
        } else {
            require(map.pathSource.toFile().exists()) {
                "Extracted map for generation = " + map.pathSource + " does not exist."
            }
        }
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
        addTagConf(AppConfig.config.mapsforgeConfig.tagMapping.toAbsolutePath())
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

    private fun addPrefLang() {
        if (map.prefLang != null && map.prefLang.length > 0) {
            addCommand("preferred-languages=" + map.prefLang)
        }
    }

    private fun addMapComment() {
        addCommand("comment=" + AppConfig.config.mapsforgeConfig.mapDescription)
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
        val fileSizeLimit = 1100
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

    private fun addTagConf(path: Path) {
        addCommand("tag-conf-file=$path")
    }

    private fun addBboxEnlargement(pixels: Int) {
        addCommand("bbox-enlargement=$pixels")
    }

    companion object {
        private val TAG: String = CmdGenerate::class.java.simpleName
    }
}
