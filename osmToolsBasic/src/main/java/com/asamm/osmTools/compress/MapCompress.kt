package com.asamm.osmTools.compress

import kotlinx.coroutines.*
import com.asamm.osmTools.Main
import com.asamm.osmTools.config.Action
import com.asamm.osmTools.config.AppConfig
import com.asamm.osmTools.mapConfig.ItemMap
import com.asamm.osmTools.mapConfig.MapSource
import com.asamm.osmTools.utils.Logger
import com.asamm.osmTools.utils.TimeWatch
import com.asamm.osmTools.utils.Utils
import com.asamm.osmTools.utils.versionToDate
import java.io.File
import java.io.RandomAccessFile

class MapCompress {
    
    val TAG = MapCompress::class.java.simpleName

    fun compressAllMaps(mapSource: MapSource) {
        runBlocking {
            compressMapParallel(mapSource)
        }
    }
    /**
     * Reads a MapSource object and compresses the map files.
     */
    suspend private fun compressMapParallel(mapSource: MapSource) {

        // compress maps in parallel run at the same time multiple threads based on number of cores
        val cores = Runtime.getRuntime().availableProcessors() - 1
        val dispatcher = Dispatchers.IO.limitedParallelism(cores)

        coroutineScope {
            mapSource.getMapPacksIterator().asSequence()
                .flatMap { mapPack -> mapPack.maps.asSequence() }
                .map { map ->
                    async(dispatcher) {
                        compressMap(map)
                    }
                }
                .toList()
                .awaitAll()
        }


    }


    fun compressMap(map: ItemMap) {
        if (map.hasAction(Action.GENERATE_MAPSFORGE)) {
            compressMapsforge(map)
        }
        if (map.hasAction(Action.GENERATE_MBTILES)) {
            compressMbtiles(map)
        }
    }
    
    private fun compressMapsforge(map: ItemMap) {

        if (!map.hasAction(Action.GENERATE_MAPSFORGE) && !map.hasAction(Action.ADDRESS_POI_DB)) {
            // map hasn't any result file for compress
            return
        }

        if (!AppConfig.config.overwrite && map.getPathResultMapsforge().toFile().exists()) {
            Logger.d( TAG, "File with compressed result  ${map.getPathResultMapsforge()} already exist - skipped."
            )
            return
        }

        Logger.i(TAG, "Compressing mapsforge map: " + map.getPathResultMapsforge())
        val time = TimeWatch()
        Main.mySimpleLog.print("Compress: ${map.getName()} ...")


        // change lastChange attribute of generated file this workaround how to set date of map file in Locus
        val filesToCompress: MutableList<File> = ArrayList()
        if (map.hasAction(Action.GENERATE_MAPSFORGE)) {
            val mapFile: File = map.getPathGenerate().toFile()
            require(mapFile.exists()) { "Map file for compression: ${map.getPathGenerate()} does not exist." }

            val versionDate = versionToDate(AppConfig.config.version).time
            // rewrite bytes in header to set new creation date
            val raf = RandomAccessFile(mapFile, "rw")
            raf.seek(36)
            raf.writeLong(versionDate)
            raf.close()

            filesToCompress.add(mapFile)
        }

        if (map.hasAction(Action.ADDRESS_POI_DB)) {
            require(map.getPathAddressPoiDb().toFile().exists()) {
                "Address DB file for compression: ${map.getPathAddressPoiDb()} does not exist." }
            filesToCompress.add(map.getPathAddressPoiDb().toFile())
        }


        // compress file
        Utils.compressFiles(filesToCompress, map.getPathResultMapsforge().toFile())

        Main.mySimpleLog.print("\t\t\tdone " + time.elapsedTimeSec + " sec")
    }

    private fun compressMbtiles(map: ItemMap){
        if (!map.hasAction(Action.GENERATE_MBTILES) || !map.hasAction(Action.POI_DB_V2)) {
            // map hasn't any result file for compress
            return
        }
        if (!AppConfig.config.overwrite && map.pathResultMbtiles.toFile().exists()) {
            Logger.d( TAG, "Zip file with compressed mbtiles ${map.pathResultMbtiles} already exist." )
            return
        }

        Logger.i(TAG, "Compressing mbtiles map: " + map.getPathResultMbtiles())
        val fileToCompress: MutableList<File> = mutableListOf()
        if (map.hasAction(Action.GENERATE_MBTILES)){
            require(map.pathMbtiles.toFile().exists()){
                "Mbtiles file for compression: ${map.pathMbtiles} does not exist."}
            fileToCompress.add(map.pathMbtiles.toFile())
        }

        if (map.hasAction(Action.POI_DB_V2) && !Utils.isLocalDEV()) {
            require(map.pathPoiV2Db.toFile().exists()) {
                "POI DB V2 file for compression: ${map.pathPoiV2Db} does not exist." }
            fileToCompress.add(map.pathPoiV2Db.toFile())
        }

        val time = TimeWatch()
        Main.mySimpleLog.print("Compress mbtiles: ${map.getName()} ...")

        // compress file
        Utils.compressFiles(fileToCompress, map.pathResultMbtiles.toFile())
        Main.mySimpleLog.print("\t\t\tdone " + time.elapsedTimeSec + " sec")
    }


}