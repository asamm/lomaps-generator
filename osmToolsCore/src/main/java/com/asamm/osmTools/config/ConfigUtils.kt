package com.asamm.osmTools.config

import java.io.File
import java.nio.file.Path

object ConfigUtils {


    /**
     * Define that actions will be performed based on command line definition of actions in[cliActions]
     * For specific CLI actions, additional actions are added
     */
    fun addAdditionalActions(cliActions: MutableList<Action>) {

        // for MapsForge generation add Coastline action, transform and merge
        when {
            cliActions.contains(Action.GENERATE_MAPSFORGE) -> {

                var index = cliActions.indexOf(Action.GENERATE_MAPSFORGE)

                // Add additional actions before Action.GENERATE_MAPSFORGE
                cliActions.addAll(index, listOf(Action.EXTRACT,Action.COASTLINE, Action.TRANSFORM, Action.MERGE))

                // Additional actions after Action.GENERATE_MAPSFORGE but before Action.UPLOAD
                index = cliActions.indexOf(Action.UPLOAD)
                if (index != -1) {
                    cliActions.addAll(index, listOf(Action.COMPRESS, Action.CREATE_JSON))
                }
                else{
                    cliActions.addAll(listOf(Action.COMPRESS, Action.CREATE_JSON))
                }
            }
        }
    }

    /**
     * Check that pyhgtmap util is installed and available in the path
     */
    fun getCheckPyhgtmapPath(): String {
        val pyhgtmap = "pyhgtmap"
        val command = checkApps(listOf(pyhgtmap))

        if (command.isNullOrEmpty()) {
            // If none of the commands succeeded, throw an exception
            throw Exception("pyhgtmap not installed. Please install it using 'pip install pyhgtmap'")
        }
        return command
    }

    fun getCheckOsmiumPath() :String {
        val osmiumPaths = if (isWindows()) listOf("osmium.exe", "c:\\Users\\petrv\\miniconda3\\Library\\bin\\osmium.exe") else listOf("osmium")

        val command = checkApps(osmiumPaths)

        if (command.isNullOrEmpty()) {
            // If none of the commands succeeded, throw an exception
            throw Exception("Omium not found in locatios: $osmiumPaths")
        }
        return command
    }

    // Function to check if Python is installed and return its path
    fun findPythonPath(): String {
        // First try to find "python", if not found, try "python3"
        val pythonCommands = if (isWindows()) listOf("python3.exe", "python.exe") else listOf("python", "python3")

        val command = checkApps(pythonCommands)

        if (command.isEmpty()) {
            // If none of the commands succeeded, throw an exception
            throw Exception("Python not found. Tried commands: $pythonCommands")
        }
        return command
    }

    // Function to check if ogr2ogr is installed and return its path
    fun findOgr2ogrPath(): String {
        // First try to find "python", if not found, try "python3"
        val ogrCommands = if (isWindows()) listOf("ogr2ogr.exe", "ogr2ogr") else listOf("ogr2ogr")

        val command = checkApps(ogrCommands, "--version", "GDAL")

        if (command.isEmpty()) {
            // If none of the commands succeeded, throw an exception
            throw Exception("Ogr2ogr not found. Tried commands: $ogrCommands . Please install ogr2ogr")
        }
        return command
    }

    /**
     * Check that planetiler util is installed and available in the path
     */
    fun getCheckPath(pathToCheck: Path): Path {

        if ( !pathToCheck.toFile().exists()) {
            throw Exception("File not found in location: ${pathToCheck} ")
        }

        return pathToCheck
    }


    fun checkApps(commands: List<String>, argument: String = "--version", expectedOutput:String = ""): String {
        for (command in commands) {
            try {
                // Use ProcessBuilder to run the "python --version" or "python3 --version" command
                val process = ProcessBuilder(command, argument)
                    .redirectErrorStream(true)
                    .start()

                // Wait for the process to complete and check if Python is found
                val output = process.inputStream.bufferedReader().readText()
                process.waitFor()

                val fileNameWithExtension = command.substringAfterLast(File.separator)
                val fileNameWithoutExtension = fileNameWithExtension.substringBeforeLast(".")

                if (output.contains(fileNameWithoutExtension, ignoreCase = true) ||
                        (expectedOutput.isNotEmpty() && output.contains(expectedOutput)))  {
                    // If Python is found, return the path of the executable
                    return command
                }
            } catch (e: Exception) {
                // Ignore and try the next command
            }
        }
        return ""
    }

    // Helper function to check if the system is Windows
    public fun isWindows(): Boolean {
        return System.getProperty("os.name").lowercase().contains("win")
    }


}