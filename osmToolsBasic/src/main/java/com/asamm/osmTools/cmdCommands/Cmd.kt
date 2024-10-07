/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.asamm.osmTools.cmdCommands

import com.asamm.osmTools.Main
import com.asamm.osmTools.Parameters
import com.asamm.osmTools.config.AppConfig
import com.asamm.osmTools.generator.GenLoMaps
import com.asamm.osmTools.mapConfig.ItemMap
import com.asamm.osmTools.utils.Logger
import org.apache.commons.io.FileUtils
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader


open class Cmd(val externalApp: ExternalApp) {

    enum class ExternalApp {
        NO_EXTERNAL_APP,

        OSMOSIS,

        GRAPHOPPER,

        STORE_UPLOAD,

        OSMIUM,

        LOMAPS_TOOLS,

        PYHGTMAP,
    }


    // list of added commands
    val cmdList: MutableList<String> = mutableListOf()

    init {

        // add basic
        initializeExternApp()
    }

    private fun initializeExternApp() {
        when (externalApp) {
            ExternalApp.OSMOSIS -> addCommand(Parameters.getOsmosisExe())
            ExternalApp.OSMIUM -> addCommand(AppConfig.config.cmdConfig.osmium)
            ExternalApp.GRAPHOPPER -> addCommands(Parameters.getPreShellCommand(),Parameters.getGraphHopperExe())
            ExternalApp.STORE_UPLOAD -> addCommands("java", "-jar", Parameters.getStoreUploaderPath())
            ExternalApp.LOMAPS_TOOLS -> {
                addCommand(AppConfig.config.cmdConfig.pythonPath)
                addCommand(AppConfig.config.touristConfig.lomapsToolsPy.toString())
            }

            ExternalApp.PYHGTMAP -> {
                addCommand(AppConfig.config.cmdConfig.pyghtmap)
            }

            ExternalApp.NO_EXTERNAL_APP -> Unit // do nothing

        }
    }


    fun prepareDirectory(pathToWrite: String) {
        FileUtils.forceMkdir(File(pathToWrite).getParentFile())
    }


    fun addBoundingPolygon(map: ItemMap) {
        // test if polygon exist in specified path
        require(File(map.getPathPolygon()).exists()) { "Bounding polygon: " + map.getPathPolygon() + " doesn't exists" }

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

    /** */ /*                      TOOLS                     */
    /** */
    @Throws(IOException::class, InterruptedException::class)
    fun execute(): String? {
        return runCommands(createArray())
    }

    fun executePb(): ProcessBuilder {
        return createProcessBuilder(createArray())
    }

    private fun createArray(): Array<String> {
        // add ending command
        if (externalApp == ExternalApp.GRAPHOPPER) {
            addCommand(Parameters.getPostShellCommand())
        }

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

    private fun createProcessBuilder(mCmdArray: Array<String>): ProcessBuilder {
        val pb = ProcessBuilder(*mCmdArray)
        pb.redirectErrorStream(true)

        // set working directory based on external software
        if (externalApp == ExternalApp.OSMOSIS) {
            pb.directory(File(Parameters.getOsmosisExe()).getParentFile().getParentFile())
        } else if (externalApp == ExternalApp.GRAPHOPPER) {
            pb.directory(File(Parameters.getGraphHopperExe()).getParentFile())
        }

        // return builder
        return pb
    }

    @Throws(IOException::class, InterruptedException::class)
    private fun runCommands(mCmdArray: Array<String>): String? {
        var line: String?
        var lastOutpuLine: String? = null
        var stdInput: BufferedReader? = null
        try {
            Main.myRunTimeLog.print(getCmdLine() + "\n")
            val pb = createProcessBuilder(mCmdArray)

            val runTime = pb.start()
            stdInput = BufferedReader(InputStreamReader(runTime.getInputStream()))

            // read the output from the command
            while ((stdInput.readLine().also { line = it }) != null) {
                Logger.i(TAG,line)
                Main.myRunTimeLog.print(line + "\n")
                lastOutpuLine = line
            }
            val exitVal = runTime.waitFor()

            // break program when wrong exit value
            if (exitVal != 0) {
                Logger.e(TAG,"Wrong return value from sub command, exit value: " + exitVal)

                val errorMsg = "exception happened when run cmd: \n" + getCmdLine()
                throw IllegalArgumentException(errorMsg)
            }

            // return result
            cmdList.clear()
            return lastOutpuLine
        } finally {
            try {
                if (stdInput != null) {
                    stdInput.close()
                }
            } catch (ex: IOException) {
                throw IOException(ex.toString())
            }
        }
    }

    protected fun checkFileLocalPath(map: ItemMap) {
        require(File(map.getPathSource()).exists()) {
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