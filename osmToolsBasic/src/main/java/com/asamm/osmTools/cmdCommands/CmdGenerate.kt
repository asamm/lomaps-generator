package com.asamm.osmTools.cmdCommands

import com.asamm.osmTools.config.AppConfig
import com.asamm.osmTools.mapConfig.ItemMap
import com.asamm.osmTools.utils.Logger
import com.asamm.osmTools.utils.MercatorUtils
import java.nio.file.Path
import java.util.Locale
import kotlin.io.path.absolute

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
        inputPbf = if (map.isMerged) map.pathMerge else map.pathSource,
        outputMap = map.pathGenerate,
        bbox = "${map.boundary.minLat},${map.boundary.minLon},${map.boundary.maxLat},${map.boundary.maxLon}",
        type = resolveType(map),
        prefLang = map.prefLang?.takeIf { it.isNotEmpty() },
        zoomInterval = map.forceInterval?.takeIf { it.isNotEmpty() },
    ) {
        if (map.isMerged) {
            require(map.pathMerge.toFile().exists()) {
                "Merged map for generation: ${map.pathMerge} does not exist!"
            }
            require(AppConfig.config.mapsforgeConfig.tagMapping.toFile().exists()) {
                "Map writer definition file: ${AppConfig.config.mapsforgeConfig.tagMapping} does not exist."
            }
        } else {
            require(map.pathSource.toFile().exists()) {
                "Extracted map for generation: ${map.pathSource} does not exist."
            }
        }
    }

    // ── Shared generation logic ───────────────────────────────────────────────

    /**
     * Build the full Osmosis/mapfile-writer command.
     * The returned [ProcessCommand] can be executed immediately or retried.
     */
    fun createCmd(): ProcessCommand {
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

    // ── Section 2: Overview map generation ───────────────────────────────────

    companion object {

        private val TAG: String = CmdGenerate::class.java.simpleName

        private fun resolveType(map: ItemMap): String {
            val sourcePath = if (map.isMerged) map.pathMerge else map.pathSource
            return when (map.forceType?.lowercase(Locale.getDefault())) {
                "hd"  -> "hd"
                "ram" -> "ram"
                else  -> if (sourcePath.toFile().length() / 1024 / 1024 < 1100L) "ram" else "hd"
            }
        }

        /**
         * Creates a [CmdGenerate] configured to generate the global overview .map file
         * from the Natural Earth PBF produced by [com.asamm.osmTools.overviewMap.OverviewMapBuilder].
         *
         * Output path is derived from [com.asamm.osmTools.config.OverviewMapConfig.outputPbf] by replacing the
         * `.osm.pbf` extension with `.osm.map` in the same directory.
         *
         * Two zoom intervals are used:
         *  - 3,0,4  — world-level overview (zooms 0–4, base zoom 3)
         *  - 8,5,9  — regional detail      (zooms 5–9, base zoom 8)
         */
        @JvmStatic
        fun forOverviewMap(): CmdGenerate {
            val cfg = AppConfig.config.overviewMapConfig
            require(cfg.outputPbf.toFile().exists()) {
                "Overview PBF not found: ${cfg.outputPbf}. Run the overview map build step first."
            }
            val outputMap = Path.of(cfg.outputPbf.toString().replace(".osm.pbf", ".osm.map")).absolute()
            return CmdGenerate(
                inputPbf = cfg.outputPbf.absolute(),
                outputMap = outputMap,
                bbox = "${- MercatorUtils.WEB_MERCATOR_MAX_LAT + 10},-179.9,${MercatorUtils.WEB_MERCATOR_MAX_LAT-10},179.9",
                type = "ram",
                prefLang = null,
                zoomInterval = "3,1,4,8,5,9",
                //zoomInterval = "3,1,4",
            )
        }
    }
}