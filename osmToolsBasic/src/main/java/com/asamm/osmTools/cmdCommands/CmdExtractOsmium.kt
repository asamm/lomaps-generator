package com.asamm.osmTools.cmdCommands

import com.asamm.osmTools.mapConfig.ItemMap
import com.asamm.osmTools.mapConfig.MapSource
import com.asamm.osmTools.utils.Utils
import net.minidev.json.JSONArray
import net.minidev.json.JSONObject
import java.io.File

private const val CONFIG_TMP_JSON_FILE = "osmium_extract_config.json"

class CmdExtractOsmium(ms: MapSource, sourceId: String) : Cmd(ExternalApp.OSMIUM) {

    private val map: ItemMap = ms.getMapById(sourceId)

    private val extractsJ = JSONArray()

    fun hasMapForExtraction(): Boolean = extractsJ.isNotEmpty()

    fun addExtractMap(mapToAdd: ItemMap) {
        Utils.createParentDirs(mapToAdd.pathSource)
        val extractJ = JSONObject().apply {
            put("output", mapToAdd.pathSource.toString())
            put("polygon", JSONObject().apply {
                put("file_name", mapToAdd.pathPolygon.toString())
                put("file_type", "poly")
            })
        }
        extractsJ.add(extractJ)
    }

    /**
     * Write the JSON config, build the extract command, and execute it.
     */
    fun createCmd(completeRelations: Boolean) {
        val configFile = writeConfigJsonFile()
        val strategy = if (completeRelations) "smart" else "simple"
        builder()
            .add("extract", "-c", configFile.path)
            .add("--strategy", strategy)
            .add("-v")
            .add(map.pathSource.toString())
            .add("--fsync")
            .execute()
        configFile.delete()
    }

    private fun writeConfigJsonFile(): File {
        val configJ = JSONObject().apply { put("extracts", extractsJ) }
        val file = File(CONFIG_TMP_JSON_FILE)
        Utils.writeStringToFile(file, configJ.toJSONString(), false)
        return file
    }

}