package com.asamm.osmTools.config

import com.asamm.osmTools.config.AppConfig.config
import com.asamm.osmTools.utils.Logger
import com.asamm.osmTools.utils.Utils
import java.io.File
import java.nio.file.Path

/**
 * Thrown when an external tool cannot be resolved.
 *
 * [summary] and [detail] are kept apart from the PATH dump so that
 * [ConfigUtils.verifyExternalTools] can aggregate many failures and still print the PATH once,
 * while a single uncaught throw carries it as part of its message.
 */
class ExternalToolException(val summary: String, val detail: String = "") :
    Exception(summary + detail + ConfigUtils.pathInfo())

object ConfigUtils {

    private val TAG: String = ConfigUtils::class.java.simpleName


    /**
     * Resolves the full ordered action list for [mode] by starting from its base actions,
     * appending any [extraActions] (e.g. [Action.UPLOAD] when the --upload flag is set),
     * and then injecting all required dependencies.
     */
    fun resolveActions(mode: LoMapsMode, extraActions: List<Action> = emptyList()): MutableList<Action> {
        val actions = (mode.baseActions() + extraActions).toMutableList()
        expandActionDependencies(actions)
        return actions
    }

    /**
     * Inserts implicit dependency actions directly before the action that requires them.
     * Iterates in reverse so that earlier insertions do not shift the indices of later ones.
     */
    private fun expandActionDependencies(actions: MutableList<Action>) {
        for (index in actions.size - 1 downTo 0) {
            when (actions[index]) {

                Action.GENERATE_MAPSFORGE -> {
                    actions.addAll(index, listOf(Action.RESIDENTIAL))
                    if (!actions.contains(Action.POI_DB_V2)) {
                        actions.addAll(index, listOf(Action.POI_DB_V2))
                    }
                }

                Action.ADDRESS_POI_DB -> {
                    if (!actions.contains(Action.EXTRACT_OSM_PLANET)) {
                        actions.addAll(index, listOf(Action.EXTRACT_OSM_PLANET))
                    }
                }

                Action.GENERATE_MBTILES -> {
                    if (!Utils.isLocalDEV() && !actions.contains(Action.POI_DB_V2)) {
                        actions.addAll(index, listOf(Action.OVERVIEW_MAP, Action.POI_DB_V2))
                    }
                }

                Action.GENERATE_MBTILES_ONLINE, Action.GENERATE_PMTILES -> {
                    if (!actions.contains(Action.OVERVIEW_MAP)) {
                        actions.addAll(index, listOf(Action.OVERVIEW_MAP))
                    }
                }

                Action.UPLOAD_S3 -> {
                    if (!actions.contains(Action.GENERATE_PMTILES)) {
                        actions.addAll(index, listOf(Action.GENERATE_PMTILES))
                    }
                }

                Action.UPLOAD -> {
                    if (!actions.contains(Action.COMPRESS))    actions.add(index, Action.COMPRESS)
                    if (!actions.contains(Action.CREATE_JSON)) actions.add(index, Action.CREATE_JSON)
                }

                else -> {}
            }
        }
        // guard: COMPRESS always needs CREATE_JSON
        if (actions.contains(Action.COMPRESS) && !actions.contains(Action.CREATE_JSON)) {
            actions.add(Action.CREATE_JSON)
        }
    }

    /**
     * Check that pyhgtmap util is installed and available in the path
     */
    fun getCheckPyhgtmapPath(): String {
        val lookup = resolveApp(listOf("pyhgtmap"))

        if (!lookup.found) {
            throw ExternalToolException(
                "pyhgtmap not installed. Please install it using 'pip install pyhgtmap'.", lookup.diagnostics()
            )
        }
        return lookup.command
    }

    fun getCheckOsmiumPath(): String {
        val osmiumPaths =
            if (isWindows()) listOf("osmium.exe", "c:\\Users\\petrv\\miniconda3\\Library\\bin\\osmium.exe") else listOf(
                "osmium"
            )

        val lookup = resolveApp(osmiumPaths)

        if (!lookup.found) {
            throw ExternalToolException("osmium not found in locations: $osmiumPaths", lookup.diagnostics())
        }
        return lookup.command
    }

    // Function to check if Python VENV is installed "near" the script location
    fun findPythonPath(scriptPath: Path): String {
        // try to find python in venv near the script
        val venvPython = if (isWindows()) {
            scriptPath.parent.parent.resolve("venv").resolve("Scripts").resolve("python.exe").toString()
        } else {
            scriptPath.parent.parent.resolve("venv").resolve("bin").resolve("python").toString()
        }

        // verify that the venvPython path exists
        if (File(venvPython).exists()) {
            return venvPython
        }

        // as fallback try to find python in system
        val pythonCommands =
            if (isWindows()) listOf("python3.exe", "python.exe", "python") else listOf("python", "python3")

        val lookup = resolveApp(pythonCommands)

        if (!lookup.found) {
            throw ExternalToolException("Python not found. Tried commands: $pythonCommands", lookup.diagnostics())
        }
        return lookup.command
    }

    // Function to check if ogr2ogr is installed and return its path
    fun findOgr2ogrPath(): String {
        // First try to find "python", if not found, try "python3"
        val ogrCommands = if (isWindows()) listOf("ogr2ogr.exe", "ogr2ogr") else listOf("ogr2ogr")

        val lookup = resolveApp(ogrCommands, "--version", "GDAL")

        if (!lookup.found) {
            throw ExternalToolException(
                "ogr2ogr not found. Tried commands: $ogrCommands. Please install ogr2ogr.", lookup.diagnostics()
            )
        }
        return lookup.command
    }


    /**
     * Resolve the go-pmtiles CLI on the PATH.
     *
     * The PATH is the one the JVM inherited, which is not an interactive shell's: a service manager
     * gives its jobs a minimal PATH and does not source ~/.bashrc. When the binary lives outside the
     * default system directories, the environment that starts the generator has to add it — that is
     * what the `export PATH=...` line in the generated wrapper scripts is for.
     */
    fun getCheckPmtilesPath(): String {
        val pmtilesCommands = if (isWindows()) listOf("pmtiles.exe", "pmtiles") else listOf("pmtiles")
        val lookup = resolveApp(pmtilesCommands, "version", "pmtiles")

        if (!lookup.found) {
            throw ExternalToolException(
                "PMTiles CLI not found. Tried commands: $pmtilesCommands. Install it " +
                        "(https://docs.protomaps.com/pmtiles/cli) and make sure the directory holding the " +
                        "binary is on the PATH of whatever starts the generator.",
                lookup.diagnostics()
            )
        }
        return lookup.command
    }

    fun getCheckGdalPath(tool: String): String {
        val cmds = if (isWindows()) listOf("$tool.exe", tool) else listOf(tool)
        val lookup = resolveApp(cmds, "--version", "GDAL")
        if (!lookup.found) {
            throw ExternalToolException("$tool not found. Tried: $cmds. Please install GDAL.", lookup.diagnostics())
        }
        return lookup.command
    }

    /**
     * Check that rio (rasterio CLI) is installed and available in the path.
     * Required by the rio-rgbify plugin.
     */
    fun getCheckRioRgbifyPath(): String {
        val rioCommands = if (isWindows()) listOf("rio.exe", "rio") else listOf("rio")
        val lookup = resolveApp(rioCommands, "--version", "")
        if (!lookup.found) {
            throw ExternalToolException(
                "rio not found. Tried commands: $rioCommands. Please install via: pip install rio-rgbify",
                lookup.diagnostics()
            )
        }
        return lookup.command
    }

    /**
     * Actions whose pipeline stage shells out to planetiler.
     * [Action.GENERATE_MBTILES] reaches it through `MapPipeline.generateMbtilesBatch`,
     * the other two through `PlanetPipeline`.
     */
    private val PLANETILER_ACTIONS = setOf(
        Action.GENERATE_PMTILES,
        Action.GENERATE_MBTILES_ONLINE,
        Action.GENERATE_MBTILES,
    )

    /**
     * Resolve every external tool the selected [actions] need, before any work starts.
     *
     * Each tool is otherwise resolved lazily, the first time its stage runs. That turned a missing
     * CLI into a failure hours into a run: the planet PMTiles generation completed after 3h13m and
     * only then did the pmtiles CLI lookup fail, discarding the whole run. Every check runs here even
     * if an earlier one failed, so a single run reports all missing tools rather than one per attempt.
     *
     * The check is deliberately stricter than the pipeline: stages skip themselves when their output
     * already exists, so a tool required by [actions] may never actually be invoked. Use
     * `--skip_tool_check` on a machine that legitimately reuses such output without the tool installed.
     */
    fun verifyExternalTools(actions: List<Action>) {

        val checks = linkedMapOf<String, () -> Unit>()

        // Used by nearly every stage: the planet sources are merged with osmium before anything else.
        checks["osmium"] = { config.cmdConfig.osmium }

        // osmosis only backs the LoMaps db plugin, reached from MapPipeline.addressPoiDatabase.
        if (Action.ADDRESS_POI_DB in actions) {
            checks["osmosis"] = { getCheckPath(config.cmdConfig.osmosis.toAbsolutePath()) }
        }
        if (Action.TOURIST in actions) {
            checks["lomaps_tools.py"] = { getCheckPath(config.touristConfig.lomapsToolsPy) }
            checks["python (for lomaps_tools.py)"] = { findPythonPath(config.touristConfig.lomapsToolsPy) }
        }
        if (Action.CONTOUR in actions) {
            checks["pyhgtmap"] = { config.cmdConfig.pyghtmap }
            checks["hgt directory"] = { getCheckPath(config.contourConfig.hgtDir) }
        }
        if (actions.any { it in PLANETILER_ACTIONS }) {
            checks["planetiler"] = { getCheckPath(config.cmdConfig.planetiler) }
        }
        if (Action.GENERATE_PMTILES in actions) {
            checks["pmtiles CLI"] = { getCheckPmtilesPath() }
        }
        if (Action.POI_DB_V2 in actions && !Utils.isLocalDEV()) {
            checks["poi db v2 init script"] = { getCheckPath(config.cmdConfig.poiDbV2Init) }
            checks["poi db v2 generator script"] = { getCheckPath(config.cmdConfig.poiDbV2Generator) }
        }
        if (Action.UPLOAD in actions) {
            checks["locus store uploader"] = { getCheckPath(config.storeUploaderPath) }
        }

        val failures = mutableListOf<String>()
        for ((name, check) in checks) {
            try {
                check()
                Logger.d(TAG, "External tool available: $name")
            } catch (e: ExternalToolException) {
                // Taken apart so the PATH dump is not repeated once per failing tool.
                failures += "$name -> ${e.summary}${e.detail}"
            } catch (e: Exception) {
                failures += "$name -> ${e.message}"
            }
        }

        if (failures.isNotEmpty()) {
            throw Exception(
                "${failures.size} of ${checks.size} required external tools are not available. " +
                        "Aborting before any work starts (pass --skip_tool_check to run anyway):" +
                        failures.joinToString(prefix = "\n  - ", separator = "\n  - ") +
                        pathInfo()
            )
        }
        Logger.i(TAG, "All ${checks.size} required external tools resolved.")
    }

    /**
     * Check that planetiler util is installed and available in the path
     */
    fun getCheckPath(pathToCheck: Path): Path {

        if (!pathToCheck.toFile().exists()) {
            throw Exception("File not found in location: ${pathToCheck} ")
        }

        return pathToCheck
    }

    /**
     * Outcome of an external-tool lookup: the command that worked, plus why every other
     * candidate was rejected.
     *
     * The rejection reasons matter more than they look. A tool that is installed but
     * unreachable — absent from the PATH of a systemd unit, hidden behind `ProtectHome=`,
     * not executable by the service user, or failing to fork — is indistinguishable from a
     * tool that was never installed unless the real failure is carried out of the lookup.
     */
    data class AppLookup(val command: String, val rejections: List<String>) {

        val found: Boolean get() = command.isNotEmpty()

        /** Rejection reasons, indented and ready to append to an error message. */
        fun diagnostics(): String =
            rejections.joinToString(prefix = "\n  ", separator = "\n  ")
    }

    /** The PATH these lookups resolved against, as one indented line. */
    fun pathInfo(): String = "\n  PATH=${System.getenv("PATH") ?: "<unset>"}"

    /**
     * Try each of [commands] in turn and return the first that runs and whose output looks right.
     * Unlike a bare "not found", the result also reports why the others were rejected.
     */
    fun resolveApp(
        commands: List<String>,
        argument: String = "--version",
        expectedOutput: String = ""
    ): AppLookup {
        val rejections = mutableListOf<String>()

        for (command in commands) {
            try {
                val process = ProcessBuilder(command, argument)
                    .redirectErrorStream(true)
                    .start()

                val output = process.inputStream.bufferedReader().readText()
                process.waitFor()

                val fileNameWithExtension = command.substringAfterLast(File.separator)
                val fileNameWithoutExtension = fileNameWithExtension.substringBeforeLast(".")

                val valid = when {
                    expectedOutput.isNotEmpty() -> output.contains(expectedOutput)
                    output.contains(fileNameWithoutExtension, ignoreCase = true) -> true
                    else -> output.isNotBlank() && process.exitValue() == 0
                }
                if (valid) return AppLookup(command, rejections)

                rejections += "'$command $argument' ran (exit ${process.exitValue()}) but its output did not look " +
                        "like $command: " + (output.trim().take(200).ifEmpty { "<no output>" })
            } catch (e: Exception) {
                // Usually "error=2, No such file or directory" when the binary is not on the PATH,
                // but also permission denied, exec format errors and fork failures.
                rejections += "'$command $argument' could not be started: ${e.message}"
            }
        }
        return AppLookup("", rejections)
    }

    // Helper function to check if the system is Windows
    fun isWindows(): Boolean {
        return System.getProperty("os.name").lowercase().contains("win")
    }

    /**
     * Loads AWS S3 credentials from environment variables and injects them into [config].
     *
     * Expected variables:
     *  - `S3_ACCESS_KEY` → [OnlineLoMapsConfig.s3accessKey]
     *  - `S3_SECRET_KEY` → [OnlineLoMapsConfig.s3secretKey]
     *
     * Throws [IllegalStateException] if either variable is missing or blank.
     */
    fun loadAwsCredentialsFromEnv() {
        val accessKey = System.getenv("S3_ACCESS_KEY")
            ?.takeIf { it.isNotBlank() }
            ?: error("Environment variable S3_ACCESS_KEY is not set or blank")
        val secretKey = System.getenv("S3_SECRET_KEY")
            ?.takeIf { it.isNotBlank() }
            ?: error("Environment variable S3_SECRET_KEY is not set or blank")

        config.onlineLoMapsConfig.s3accessKey = accessKey
        config.onlineLoMapsConfig.s3secretKey = secretKey
    }


}