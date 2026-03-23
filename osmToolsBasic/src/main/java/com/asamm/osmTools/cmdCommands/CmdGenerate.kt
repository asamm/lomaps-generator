package com.asamm.osmTools.cmdCommands

import com.asamm.osmTools.config.AppConfig
import com.asamm.osmTools.mapConfig.ItemMap
import com.asamm.osmTools.utils.Logger
import java.util.Locale

class CmdGenerate(val map: ItemMap) : Cmd(ExternalApp.OSMOSIS) {

    init {
        if (map.isMerged) {
            require(map.pathMerge.toFile().exists()) {
                "Merged map for generation: ${map.pathMerge} does not exist!"
            }
            require(AppConfig.config.mapsforgeConfig.tagMapping.toFile().exists()) {
                "Map writer definition file: ${AppConfig.config.mapsforgeConfig.tagMapping} does not exist."
            }
            if (!map.pathMerge.toFile().exists()) {
                map.isMerged = false
            }
        } else {
            require(map.pathSource.toFile().exists()) {
                "Extracted map for generation: ${map.pathSource} does not exist."
            }
        }
    }

    /**
     * Build the full Osmosis/mapfile-writer command for this map.
     * The returned [ProcessCommand] can be executed immediately or retried.
     */
    fun createCmd(): ProcessCommand {
        val cores = Runtime.getRuntime().availableProcessors()
        val sourcePath = if (map.isMerged) map.pathMerge else map.pathSource
        val type = when (map.forceType?.lowercase(Locale.getDefault())) {
            "hd"  -> "hd"
            "ram" -> "ram"
            else  -> if (sourcePath.toFile().length() / 1024 / 1024 < 1100L) "ram" else "hd"
        }
        prepareDirectory(map.pathGenerate)
        return osmosisBuilder()
            .apply {
                if (map.isMerged) readPbf(map.pathMerge.toString())
                else readPbf(map.pathSource.toString())
            }
            .add("--mapfile-writer", "file=${map.pathGenerate}")
            .add("type=$type")
            .addNotBlank(map.prefLang?.takeIf { it.isNotEmpty() }?.let { "preferred-languages=$it" })
            .add("bbox=${map.boundary.minLat},${map.boundary.minLon},${map.boundary.maxLat},${map.boundary.maxLon}")
            .add("tag-conf-file=${AppConfig.config.mapsforgeConfig.tagMapping.toAbsolutePath()}")
            .addNotBlank(map.forceInterval?.takeIf { it.isNotEmpty() }?.let { "zoom-interval-conf=$it" })
            .add("simplification-factor=0.5")
            .add("bbox-enlargement=5")
            .add("label-position=true")
            .add("tag-values=true")
            .add("threads=$cores")
            .add("comment=${AppConfig.config.mapsforgeConfig.mapDescription}")
            .build()
    }

    /**
     * Build and execute the generation command with up to [numRepeat] retries.
     * If [deleteFile] is true, the partially-written output is deleted before each retry.
     */
    fun execute(numRepeat: Int, deleteFile: Boolean): String? {
        val cmd = createCmd()
        val onRetry: (() -> Unit)? = if (deleteFile) ({
            val out = map.pathGenerate.toFile()
            if (out.exists()) {
                Logger.w(TAG, "Deleting partially-written file before retry: ${out.absolutePath}")
                out.delete()
            }
        }) else null
        return executeWithRetry(cmd, numRepeat, onRetry)
    }

    companion object {
        private val TAG: String = CmdGenerate::class.java.simpleName
    }
}