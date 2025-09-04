/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.asamm.osmTools.cmdCommands

import com.asamm.osmTools.Main
import com.asamm.osmTools.config.AppConfig
import com.asamm.osmTools.config.ConfigUtils
import com.asamm.osmTools.generator.AGenerator
import com.asamm.osmTools.generator.AGenerator.Companion
import com.asamm.osmTools.mapConfig.ItemMap
import com.asamm.osmTools.utils.Logger
import com.asamm.osmTools.utils.Utils
import org.apache.commons.io.FileUtils
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader


open class Cmd(val externalApp: ExternalApp) {

    enum class ExternalApp {
        NO_EXTERNAL_APP,

        OSMOSIS,

        STORE_UPLOAD,

        OSMIUM,

        LOMAPS_TOOLS,

        OGR2OGR,

        PYHGTMAP,

        PLANETILER,

        POI_V2_TOOL
    }


    // list of added commands
    val cmdList: MutableList<String> = mutableListOf()

    init {

        // add basic
        initializeExternApp()
    }

    private fun initializeExternApp() {
        when (externalApp) {
            ExternalApp.OSMIUM -> addCommand(AppConfig.config.cmdConfig.osmium)
            ExternalApp.STORE_UPLOAD -> addCommands(
                "java",
                "-jar",
                ConfigUtils.getCheckPath(AppConfig.config.storeUploaderPath).toString()
            )

            ExternalApp.LOMAPS_TOOLS -> {
                addCommand(ConfigUtils.findPythonPath(AppConfig.config.touristConfig.lomapsToolsPy))
                addCommand(AppConfig.config.touristConfig.lomapsToolsPy.toString())
            }

            ExternalApp.PYHGTMAP -> {
                addCommand(AppConfig.config.cmdConfig.pyghtmap)
            }

            ExternalApp.PLANETILER -> {
                // get amount RAM in system in GB
                val ram = Runtime.getRuntime().totalMemory() / (1024 * 1024 * 1024) - 6

                if (ConfigUtils.isWindows()) {
                    addCommands(
                        "c:\\Program Files\\Java\\jdk-21\\bin\\java.exe", "-jar",
                        ConfigUtils.getCheckPath(AppConfig.config.cmdConfig.planetiler).toString()
                    )
                } else {
                    addCommands(
                        "java",
                        "-Xmx${AppConfig.config.cmdConfig.planetilerRamXmx}",
                        "-Xmn${AppConfig.config.cmdConfig.planetilerRamXmn}","-jar",
                        ConfigUtils.getCheckPath(AppConfig.config.cmdConfig.planetiler).toString()
                    )
                }
            }

            ExternalApp.OSMOSIS -> addCommand(
                ConfigUtils.getCheckPath(AppConfig.config.cmdConfig.osmosis.toAbsolutePath()).toString()
            )

            ExternalApp.OGR2OGR -> addCommand(ConfigUtils.findOgr2ogrPath())

            ExternalApp.POI_V2_TOOL -> {
                // not commands is added, only check if the path is correct
                if (!Utils.isLocalDEV()) {
                    ConfigUtils.getCheckPath(AppConfig.config.cmdConfig.poiDbV2Init).toString()

                    ConfigUtils.getCheckPath(AppConfig.config.cmdConfig.poiDbV2Generator).toString()
                }
            }

            ExternalApp.NO_EXTERNAL_APP -> Unit // do nothing
        }
    }

    fun prepareDirectory(pathToWrite: String) {
        FileUtils.forceMkdir(File(pathToWrite).getParentFile())
    }

    fun addBoundingPolygon(map: ItemMap) {
        // test if polygon exist in specified path
        require(
            map.getPathPolygon().toFile().exists()
        ) { "Bounding polygon: " + map.getPathPolygon() + " doesn't exists" }

        // finally add to command list
        addCommand("--bp")
        addCommand("file=" + map.getPathPolygon())

        //addCommand("completeWays=yes");
        //addCommand("completeRelations=yes");
        if (map.getClipIncompleteEntities()) {
            addCommand("clipIncompleteEntities=true")
        }
    }

    fun addCommand(cmd: String?) {
        // check command
        if (cmd == null || cmd.length == 0) {
            return
        }

        // add to the list
        cmdList.add(cmd)
    }

    fun addCommands(vararg cmds: String) {
        for (cmd in cmds) {
            addCommand(cmd)
        }
    }

    // TOOLS

    fun execute(): String? {
        return runCommands(createArray())
    }

    fun executeQuietly(): String? {
        return runCommands(createArray(), false)
    }

    fun executePb(): ProcessBuilder {
        return createProcessBuilder(createArray())
    }

    private fun createArray(): Array<String> {
        // create array
        var cmdArray = cmdList.toTypedArray()
        return cmdArray
    }

    fun getCmdLine(): String {
        var line = ""
        for (param in cmdList) {
            line += param + " "
        }
        return line
    }

    protected fun createProcessBuilder(mCmdArray: Array<String>): ProcessBuilder {
        val pb = ProcessBuilder(*mCmdArray)
        pb.redirectErrorStream(true)

        // set custom working directory based on external software
        if (externalApp == ExternalApp.OSMOSIS) {
            pb.directory(AppConfig.config.cmdConfig.osmosis.toFile().getParentFile().getParentFile())
        }
        // return builder
        return pb
    }

    @Throws(IOException::class, InterruptedException::class)
    private fun runCommands(mCmdArray: Array<String>, printError: Boolean = true): String? {
        var line: String?
        var lastOutpuLine: String? = null
        var stdInput: BufferedReader? = null
        try {
            Logger.i(TAG, getCmdLine() + "\n")
            val pb = createProcessBuilder(mCmdArray)

            val runTime = pb.start()
            stdInput = BufferedReader(InputStreamReader(runTime.getInputStream()))

            // read the output from the command
            while ((stdInput.readLine().also { line = it }) != null) {
                Logger.i(TAG, line + "\n")
                lastOutpuLine = line
            }
            val exitVal = runTime.waitFor()

            // break program when wrong exit value
            if (exitVal != 0) {
                // on windows accept also exit value 15
                if (ConfigUtils.isWindows() && externalApp == ExternalApp.LOMAPS_TOOLS && exitVal == 15) {
                    // Python Osmium on Windows is not correctly ended and it's needed to kill the process for this reason
                    // accept exit value 15 https://github.com/osmcode/pyosmium/issues/280
                    Logger.w(TAG, "Due to osmium issue accept Exit value 15 on Windows")
                } else {
                    Logger.e(TAG, "Wrong return value from sub command, exit value: " + exitVal)
                    if (printError) {
                        val errorMsg = "exception happened when run cmd: \n" + getCmdLine()
                        throw IllegalArgumentException(errorMsg)
                    }
                }
            }

            // return result
            cmdList.clear()
            return lastOutpuLine
        } finally {
            try {
                if (stdInput != null) {
                    stdInput.close()
                }

                reset() // reset command list
            } catch (ex: IOException) {
                throw IOException(ex.toString())
            }
        }
    }

    protected fun checkFileLocalPath(map: ItemMap) {
        require(map.getPathSource().toFile().exists()) {
            "Extracted map: " +
                    map.getPathSource() + " does not exist"
        }
    }

    protected fun reset() {
        cmdList.clear()

        initializeExternApp()
    }

    companion object {
        private val TAG: String = Cmd::class.java.getSimpleName()
    }
}