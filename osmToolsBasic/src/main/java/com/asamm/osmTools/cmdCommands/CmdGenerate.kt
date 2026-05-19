package com.asamm.osmTools.cmdCommands

import com.asamm.osmTools.config.AppConfig
import com.asamm.osmTools.mapConfig.ItemMap
import com.asamm.osmTools.utils.Logger
import java.nio.file.Path
import java.util.*

class CmdGenerate private constructor(
    private val inputPbf: Path,
    private val outputMap: Path,
    private val bbox: String,
    private val type: String,
    private val prefLang: String?,
    private val zoomInterval: String?,
) : Cmd(ExternalApp.OSMOSIS) {

    // ── Section 1: ItemMap-based generation ──────────────────────────────────

    constructor(map: ItemMap) : this(
        inputPbf = map.pathSource,
        outputMap = map.pathMapsforgeGenerate,
        bbox = "${map.boundary.minLat},${map.boundary.minLon},${map.boundary.maxLat},${map.boundary.maxLon}",
        type = resolveType(map),
        prefLang = map.prefLang?.takeIf { it.isNotEmpty() },
        zoomInterval = map.forceInterval?.takeIf { it.isNotEmpty() },
    ) {
        require(map.pathSource.toFile().exists()) {
            "Extracted map for generation: ${map.pathSource} does not exist."
        }
    }

    // ── Shared generation logic ───────────────────────────────────────────────

    /**
     * Build the full Osmosis/mapfile-writer command.
     * The returned [ProcessCommand] can be executed immediately or retried.
     */
    fun createCmd(): ProcessCommand {

        //MapWriterConfig(input = ,)
        prepareDirectory(outputMap)
        return osmosisBuilder()
            .readPbf(inputPbf.toString())
            .add("--mapfile-writer", "file=$outputMap")
            .add("type=$type")
            .addNotBlank(prefLang?.let { "preferred-languages=$it" })
            .add("bbox=$bbox")
            .add("tag-conf-file=${AppConfig.config.mapsforgeConfig.tagMapping.toAbsolutePath()}")
            .addNotBlank(zoomInterval?.let { "zoom-interval-conf=$it" })
            .add("simplification-factor=0.5")
            .add("bbox-enlargement=5")
            .add("label-position=true")
            .add("tag-values=true")
            .add("threads=${Runtime.getRuntime().availableProcessors()}")
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
            val out = outputMap.toFile()
            if (out.exists()) {
                Logger.w(TAG, "Deleting partially-written file before retry: ${out.absolutePath}")
                out.delete()
            }
        }) else null
        return executeWithRetry(cmd, numRepeat, onRetry)
    }

    companion object {

        private val TAG: String = CmdGenerate::class.java.simpleName

        private fun resolveType(map: ItemMap): String {
            val sourcePath = map.pathSource
            return when (map.forceType?.lowercase(Locale.getDefault())) {
                "hd" -> "hd"
                "ram" -> "ram"
                else -> if (sourcePath.toFile().length() / 1024 / 1024 < 1100L) "ram" else "hd"
            }
        }
    }
}