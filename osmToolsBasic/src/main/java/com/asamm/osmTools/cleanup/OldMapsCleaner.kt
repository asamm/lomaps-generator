package com.asamm.osmTools.cleanup

import com.asamm.osmTools.compress.MapCompress
import com.asamm.osmTools.config.AppConfig
import com.asamm.osmTools.mapConfig.ConfigXmlParser
import com.asamm.osmTools.mapConfig.ItemMap
import com.asamm.osmTools.mapConfig.PathResolver
import com.asamm.osmTools.mapConfig.PathType
import com.asamm.osmTools.utils.Logger
import com.asamm.osmTools.utils.Utils
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Collectors

class OldMapsCleaner {

    val TAG = OldMapsCleaner::class.java.simpleName

    val pathTypesForDeletion = listOf(
        PathType.MERGE,
        PathType.ADDRESS_DB,
        PathType.MAPSFORGE_GENERATE,
        PathType.MAPSFORGE_RESULT,
        PathType.ADDRESS_POI_DB_CLASSIC,
        PathType.TOURIST,
        PathType.EXTRACT,
        PathType.POI_V2_DB_MBTILES,
        PathType.POI_V2_DB_MAPSFORGE,
        PathType.MBTILES_GENERATE,
        PathType.MBTILES_ONLINE_OUTDOOR,
    )

    fun purgePreviousMapGeneration() {

        val mapSource = ConfigXmlParser.parseConfigXml(AppConfig.config.mapsforgeConfig.mapConfigXml.toFile())

        // maps to gets their base paths to be deleted
        val mapsToPurge = filterMapsForPurge(mapSource.getAllMaps)

        val pathsToPurge = mapsToPurge.flatMap { itemMap ->
            val pathResolver = PathResolver(itemMap)
            // get base folder for each type for deletion
            pathTypesForDeletion.map { type -> pathResolver.getBaseDir(type) }
        }.toSet()


        Logger.i(TAG, "Paths to purge:\n${pathsToPurge.joinToString(separator = "\n")}")

        // delete all contents of the folders
        pathsToPurge.forEach { path -> Utils.deleteDirRecursively(path) }

    }



    /**
     * From the list of maps, filter out only those needed for purging. Planet map and one non-planet map,
     */
    private fun filterMapsForPurge(itemMaps: List<ItemMap>): List<ItemMap> {
        // it isn't needed to purge folder for every map (because they are in same version folder)
        // so get any map and planet map
        val mapsForPurge = listOfNotNull(
            itemMaps.firstOrNull { it.isPlanet },
            itemMaps.firstOrNull { !it.isPlanet }
        )

        return mapsForPurge
    }
}