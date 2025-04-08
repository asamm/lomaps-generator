package com.asamm.osmTools.server

import com.asamm.locus.api.v2.server.admin.StoreAdminFile
import com.asamm.locus.api.v2.server.admin.StoreAdminItem
import com.asamm.osmTools.config.Action
import com.asamm.osmTools.config.AppConfig
import com.asamm.osmTools.mapConfig.ItemMap
import com.asamm.osmTools.mapConfig.MapSource
import com.asamm.osmTools.mapConfig.Platform
import com.asamm.osmTools.utils.Logger
import com.asamm.osmTools.utils.Utils
import net.minidev.json.JSONArray
import net.minidev.json.JSONObject
import net.minidev.json.JSONStyle
import net.minidev.json.parser.JSONParser
import net.minidev.json.parser.ParseException
import org.apache.commons.io.FileUtils
import java.io.File
import java.io.FileReader
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.*
import kotlin.math.ceil

/**
 * Class for generation definition JSON for upload of vector files to GCS and GAE
 */
class UploadDefinitionCreator {

    private val TAG = this.javaClass.simpleName
    /**
     * Contains definition for upload that are common for all maps
     */
    private val defJson: JSONObject

    private val storeItems = mutableListOf<StoreAdminItem>()

    private val ITEM_NAME_VECTOR_POSTFIX = " - LoMaps"

    // define folder that contains vector maps in client
    private val CLIENT_VECTOR_MAP_DESTINATION = "mapsVector/"

    init {
        val parser = JSONParser(JSONParser.DEFAULT_PERMISSIVE_MODE)

        var obj: Any? = null
        try {
            // load general definition that is common for all uploaded LoMaps
            obj = parser.parse(FileReader(AppConfig.config.defaultStoreItemDefinitionPath.toFile()))
        } catch (e: IOException) {
            e.printStackTrace()
        } catch (e: ParseException) {
            e.printStackTrace()
        }

        defJson = obj as JSONObject
    }

    fun generateJsonUploadDefinition(mapSource: MapSource) {

        // load mappack and do actions for mappack items
        for (mapPack in mapSource.getMapPacksIterator()) {
            for (map in mapPack.maps) {
                if ( !hasValidActionsForUpload(map)) continue

                addMap(map)
            }
        }
        writeToJsonDefFile();
    }

    /**
     * Map has to have action for GENERATE_MAPSFORGE and GENERATE_MBTILES
     *
     * @param map map to check
     * @return true if map has action for upload to store
     */
    private fun hasValidActionsForUpload(map: ItemMap): Boolean {
        if (map.hasAction(Action.GENERATE_MAPSFORGE) || map.hasAction(Action.GENERATE_MBTILES)) {

            // it's needed to upload mapsforge and mbtiles to the locus store
            require (map.hasAction(Action.GENERATE_MAPSFORGE) && map.hasAction(Action.GENERATE_MBTILES) , {
                "Map ${map.name} does not have valid actions for upload. " +
                        "Edit config.xml and define ${Action.GENERATE_MBTILES.getLabel()} and ${Action.GENERATE_MAPSFORGE.getLabel()}" })
            return true
        }

        return false;
    }

    /**
     * Add new map to the JSON definition generator
     *
     * @param itemMap map to add into upload definition
     */
    private fun addMap(itemMap: ItemMap) {
        Logger.i(TAG, "creating storeAdmin item for " + itemMap.name)

        storeItems.add(mapToStoreAdminItem(itemMap))
    }

    /**
     * Write generated upload json into def file
     */
    private fun writeToJsonDefFile() {
        //Logger.i(TAG, defString);
        val defFile = AppConfig.config.storeUploadDefinitionJson.toFile()

        try {
            FileUtils.writeStringToFile(defFile, getDefinitionJsonString(), StandardCharsets.UTF_8)
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun getDefinitionJsonString(): String {
        if (storeItems.size == 0) {
            // no map was created into JSON object so there is nothing to write into file
            Logger.w(TAG,
                "Array of items definition for upload is empty - nothing to write into upload definition JSON"
            )
            return ""
        }

        val jsonArray = JSONArray()
        for (sai in storeItems) {
            jsonArray.add(sai.toJson())
        }

        return jsonArray.toJSONString(JSONStyle.NO_COMPRESS)
    }

    /**
     * Create Store Item defintion that contains needed definition for upload one map
     *
     * @param map map to create item definition
     * @return
     */
    private fun mapToStoreAdminItem(map: ItemMap): StoreAdminItem {
        // create copy from common definition json

        val sai = StoreAdminItem(defJson)

        // set map name to listing
        val name = map.nameReadable + ITEM_NAME_VECTOR_POSTFIX
        for (sail in sai.listings) {
            sail.setName(name)
        }

        // compute loCoins based on mapsforge file size
        sai.setLoCoins(computeLocoins(map.pathResultMapsforge.toFile()))

        sai.setRegionDatastoreIds(Arrays.asList(map.regionId))

        // Version
        sai.version.setName(AppConfig.config.version)
        sai.version.setStoreAdminFiles(createJsonFiles(map, sai.version.supportedApks))

        // put polygon definition into item obj.
        sai.setItemArea(map.itemAreaGeoJson)

        return sai
    }


    /**
     * Prepare Admin file obj where are defined important information for ItemVersionFile
     *
     * @param map map we create upload definition for
     * @return item file definition
     */
    private fun createJsonFiles(map: ItemMap, supportedApks: MutableMap<Int, Int>): List<StoreAdminFile> {

        return Platform.values().mapNotNull { platform ->
            val storeAdminFile = createJsonFile(map, supportedApks, platform)
            if (storeAdminFile.appVariantCodes.isNotEmpty()) storeAdminFile else null
        }
    }

    private fun createJsonFile(map: ItemMap, supportedApks: MutableMap<Int, Int>, platform: Platform): StoreAdminFile {

        val saf = StoreAdminFile()
        saf.setClientDeleteSource(true)
        saf.setClientFileUnpack(true)

        saf.setClientDestination(getClientDestinationPath(map))
        saf.setAppVariantCodes(getAppVariantsFromSupportedApks(supportedApks, platform))

        saf.setLocationPath(
            when (platform) {
                Platform.ANDROID -> map.pathResultMapsforge.toAbsolutePath().toString()
                Platform.IOS -> map.pathResultMbtiles.toAbsolutePath().toString()
            }
        )

        // validate

        return saf
    }

    /**
     * Get list of variants for specific platform
     */
    private fun getAppVariantsFromSupportedApks(supportedApks: MutableMap<Int, Int>, platform: Platform): List<Int> {
        // get keys from supported apks
        val supportedApkVariantCodes = supportedApks.keys
        // for platform android return all keys lower than 10000 and for ios keys between 10000 and 10010
        return if (platform == Platform.ANDROID) {
            supportedApkVariantCodes.filter { it < 10000 }
        } else {
            supportedApkVariantCodes.filter { it >= 10000 && it < 10010 }
        }
    }

    /**
     * Define the price for map based on the size of result file
     *
     * @param resultFile
     * @return the amounth of Locoins that will be set for item in store
     */
    private fun computeLocoins(resultFile: File): Float {
        // compute value of loCoins
        val fileSize = resultFile.length()
        val loCoins =
            fileSize / 1024.0 / 1024.0 / 8 // 8 it is because 1MB costed about 0.1 Locoin but we increase price

        val locoinsRounded = ceil(loCoins / 5).toFloat() * 5 // round up to number multiply by 5


        //Logger.i(TAG, "File size in MB: " + fileSize/1024.0/1024.0 + ", Locoins computed: " + loCoins + ", loCoins rounded: " + locoinsRounded);
        return locoinsRounded
    }

    /**
     * Decide based on the file size if item can be welcome present
     * Only files smaller then 1GB can be welcome present
     *
     * @param resultFile
     * @return
     */
    private fun canBeWelcomePresent(resultFile: File): Boolean {
        // set welcome present based on the size of zip file
        //long length1GB = 1024*1024*1024;
        //return resultFile.length() <= length1GB;

        //all vector maps can be welcome present

        return true
    }

    /**
     * Function create relative path which define where will be file
     * stored after downloading into Locus.
     *
     * @return relative path in Locus
     */
    private fun getClientDestinationPath(map: ItemMap): String {
        return Utils.changeSlashToUnix(CLIENT_VECTOR_MAP_DESTINATION + map.dirGen)
    }

}
