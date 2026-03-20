package com.asamm.osmTools.cmdCommands

import com.asamm.osmTools.config.AppConfig
import com.asamm.osmTools.config.ConfigUtils
import com.asamm.osmTools.mapConfig.ItemMap
import com.asamm.osmTools.utils.Logger
import com.asamm.osmTools.utils.Utils
import org.apache.commons.io.FileUtils
import java.io.File

/**
 * Base class for external-tool command wrappers.
 *
 */
open class Cmd(externalApp: ExternalApp) {

    /** Base args for this external application (e.g. path to binary + JVM flags). */
    protected val baseArgs: List<String> = resolveBaseArgs(externalApp)

    /** Working directory required by the tool, or null if none is needed. */
    protected val appWorkDir: File? = resolveWorkDir(externalApp)

    /** Create a fresh [ProcessCommand.Builder] seeded with [baseArgs]. */
    protected fun builder(): ProcessCommand.Builder = ProcessCommand.Builder(baseArgs)

    /** Create a fresh [OsmosisBuilder] seeded with [baseArgs] and [appWorkDir]. */
    protected fun osmosisBuilder(): OsmosisBuilder = OsmosisBuilder(baseArgs, appWorkDir)

    /**
     * Execute [cmd] and retry up to [remaining] more times on failure.
     * [onRetry] is invoked before each retry attempt (e.g. to delete a partially-written file).
     */
    protected fun executeWithRetry(
        cmd: ProcessCommand,
        remaining: Int,
        onRetry: (() -> Unit)? = null
    ): String? {
        return try {
            cmd.execute()
        } catch (e: Exception) {
            if (remaining > 0) {
                onRetry?.invoke()
                Logger.w(this::class.java.simpleName, "Retrying command (${remaining - 1} retries left): ${cmd.getCmdLine()}")
                executeWithRetry(cmd, remaining - 1, onRetry)
            } else {
                throw e
            }
        }
    }

    protected fun prepareDirectory(pathToWrite: String) {
        FileUtils.forceMkdir(File(pathToWrite).parentFile)
    }

    protected fun checkFileLocalPath(map: ItemMap) {
        require(map.getPathSource().toFile().exists()) {
            "Extracted map: ${map.getPathSource()} does not exist"
        }
    }

    companion object {

        private fun resolveBaseArgs(app: ExternalApp): List<String> = when (app) {
            ExternalApp.OSMIUM ->
                listOf(AppConfig.config.cmdConfig.osmium)

            ExternalApp.STORE_UPLOAD ->
                listOf(
                    "java", "-jar",
                    ConfigUtils.getCheckPath(AppConfig.config.storeUploaderPath).toString()
                )

            ExternalApp.LOMAPS_TOOLS ->
                listOf(
                    ConfigUtils.findPythonPath(AppConfig.config.touristConfig.lomapsToolsPy),
                    AppConfig.config.touristConfig.lomapsToolsPy.toString()
                )

            ExternalApp.PYHGTMAP ->
                listOf(AppConfig.config.cmdConfig.pyghtmap)

            ExternalApp.PLANETILER ->
                if (ConfigUtils.isWindows()) {
                    listOf(
                        "c:\\Program Files\\Java\\jdk-21\\bin\\java.exe", "-jar",
                        ConfigUtils.getCheckPath(AppConfig.config.cmdConfig.planetiler).toString()
                    )
                } else {
                    listOf(
                        "java",
                        "-Xmx${AppConfig.config.cmdConfig.planetilerRamXmx}",
                        "-Xmn${AppConfig.config.cmdConfig.planetilerRamXmn}",
                        "-jar",
                        ConfigUtils.getCheckPath(AppConfig.config.cmdConfig.planetiler).toString()
                    )
                }

            ExternalApp.OSMOSIS ->
                listOf(ConfigUtils.getCheckPath(AppConfig.config.cmdConfig.osmosis.toAbsolutePath()).toString())

            ExternalApp.OGR2OGR ->
                listOf(ConfigUtils.findOgr2ogrPath())

            ExternalApp.POI_V2_TOOL -> {
                // No base binary — individual methods add the specific script paths.
                // Validate configured paths up front (throws if missing).
                if (!Utils.isLocalDEV()) {
                    ConfigUtils.getCheckPath(AppConfig.config.cmdConfig.poiDbV2Init)
                    ConfigUtils.getCheckPath(AppConfig.config.cmdConfig.poiDbV2Generator)
                }
                emptyList()
            }

            ExternalApp.PMTILES ->
                listOf(ConfigUtils.getCheckPmtilesPath())

            ExternalApp.NO_EXTERNAL_APP -> emptyList()
        }

        private fun resolveWorkDir(app: ExternalApp): File? = when (app) {
            ExternalApp.OSMOSIS ->
                AppConfig.config.cmdConfig.osmosis.toFile().parentFile.parentFile
            else -> null
        }
    }
}