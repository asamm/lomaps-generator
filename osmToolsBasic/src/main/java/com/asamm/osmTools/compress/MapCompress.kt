package com.asamm.osmTools.compress

import com.asamm.osmTools.config.Action
import com.asamm.osmTools.config.AppConfig
import com.asamm.osmTools.mapConfig.ItemMap
import com.asamm.osmTools.mapConfig.ItemMapPack
import com.asamm.osmTools.mapConfig.MapSource
import com.asamm.osmTools.utils.Logger
import com.asamm.osmTools.utils.TimeWatch
import com.asamm.osmTools.utils.Utils
import com.asamm.osmTools.utils.versionToDate
import kotlinx.coroutines.*
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
            val allMaps = mapSource.getAllMaps
            allMaps
                .map { map ->
                    async(dispatcher) {
                        compressMap(map)
                    }
                }
                .awaitAll()
        }
    }

    /**
     * Recursively collects all maps from a mapPack, including nested mapPacks.
     */
    private fun getAllMaps(mapPack: ItemMapPack): List<ItemMap> {
        val maps = mutableListOf<ItemMap>()
        maps.addAll(mapPack.maps)
        mapPack.mapPacks.forEach { nestedPack ->
            maps.addAll(getAllMaps(nestedPack))
        }
        return maps
    }

    fun compressMap(map: ItemMap) {
        if (map.isPlanet) return

        if (map.hasAction(Action.GENERATE_MAPSFORGE)) {
            compressMapsforge(map, isForLmClassic = true)
            compressMapsforge(map, isForLmClassic = false)
        }
        if (map.hasAction(Action.GENERATE_MBTILES)) {
            compressMbtiles(map)
        }
    }

    private fun compressMapsforge(map: ItemMap, isForLmClassic: Boolean) {

        if (!map.hasAction(Action.GENERATE_MAPSFORGE) && !map.hasAction(Action.ADDRESS_POI_DB)) {
            // map hasn't any result file for compress
            return
        }

        if (!AppConfig.config.overwrite &&
            if (isForLmClassic) map.pathResultMapsforgeClassic.toFile().exists() else map.pathResultMapsforge.toFile()
                .exists()
        ) {
            Logger.d(
                TAG, "File with compressed result " +
                        "${if (isForLmClassic) map.pathResultMapsforgeClassic else map.pathResultMapsforge} already exist - skipped."
            )
            return
        }

        Logger.i(
            TAG,
            "Compressing mapsforge map: " + if (isForLmClassic) map.pathResultMapsforgeClassic else map.pathResultMapsforge
        )
        val time = TimeWatch()


        // change lastChange attribute of generated file this workaround how to set date of map file in Locus
        val filesToCompress: MutableList<File> = ArrayList()
        if (map.hasAction(Action.GENERATE_MAPSFORGE)) {
            val mapFile: File = map.pathMapsforgeGenerate.toFile()
            require(mapFile.exists()) { "Map file for compression: ${map.pathMapsforgeGenerate} does not exist." }

            val versionDate = versionToDate(AppConfig.config.version).time
            // rewrite bytes in header to set new creation date
            val raf = RandomAccessFile(mapFile, "rw")
            raf.seek(36)
            raf.writeLong(versionDate)
            raf.close()

            filesToCompress.add(mapFile)
        }

        if (map.hasAction(Action.ADDRESS_POI_DB)) {
            if (isForLmClassic) {
                require(map.pathAddressPoiDb.toFile().exists()) {
                    "Address DB file for compression: ${map.pathAddressPoiDb} does not exist."
                }
                filesToCompress.add(map.pathAddressPoiDb.toFile())
            } else {
                require(map.pathAddressDb.toFile().exists()) {
                    "Address DB file for compression: ${map.pathAddressDb} does not exist."
                }
                filesToCompress.add(map.pathAddressDb.toFile())
            }
        }

        if (!isForLmClassic && map.hasAction(Action.POI_DB_V2) && !Utils.isLocalDEV()) {
            require(map.pathPoiV2Db.toFile().exists()) {
                "POI DB V2 file for compression: ${map.pathPoiV2Db} does not exist."
            }
            filesToCompress.add(map.pathPoiV2Db.toFile())
        }

        // compress file
        Utils.compressFiles(
            filesToCompress,
            if (isForLmClassic) map.pathResultMapsforgeClassic.toFile() else map.pathResultMapsforge.toFile()
        )

        val resultFile = if (isForLmClassic) map.pathResultMapsforgeClassic else map.pathResultMapsforge
        Logger.i(TAG, "Compression done in ${time.elapsedTimeSec} sec → ${resultFile.fileName}")
    }

    private fun compressMbtiles(map: ItemMap) {
        if (!map.hasAction(Action.GENERATE_MBTILES) || !map.hasAction(Action.POI_DB_V2)) {
            // map hasn't any result file for compress
            return
        }
        if (!AppConfig.config.overwrite && map.pathResultMbtiles.toFile().exists()) {
            Logger.d(TAG, "Zip file with compressed mbtiles ${map.pathResultMbtiles} already exist.")
            return
        }

        Logger.i(TAG, "Compressing mbtiles map: " + map.pathResultMbtiles)
        val fileToCompress: MutableList<File> = mutableListOf()
        if (map.hasAction(Action.GENERATE_MBTILES)) {
            require(map.pathMbtiles.toFile().exists()) {
                "Mbtiles file for compression: ${map.pathMbtiles} does not exist."
            }
            fileToCompress.add(map.pathMbtiles.toFile())
        }

        if (map.hasAction(Action.POI_DB_V2) && !Utils.isLocalDEV()) {
            require(map.pathPoiV2Db.toFile().exists()) {
                "POI DB V2 file for compression: ${map.pathPoiV2Db} does not exist."
            }
            fileToCompress.add(map.pathPoiV2Db.toFile())
        }

        val time = TimeWatch()

        // compress file
        Utils.compressFiles(fileToCompress, map.pathResultMbtiles.toFile())
        Logger.i(TAG, "MbTiles compression done in " + time.elapsedTimeSec + " sec")
    }


}