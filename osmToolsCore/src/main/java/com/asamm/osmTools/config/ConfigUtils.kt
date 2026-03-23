package com.asamm.osmTools.config

import com.asamm.osmTools.config.AppConfig.config
import com.asamm.osmTools.utils.Utils
import java.io.File
import java.nio.file.Path

object ConfigUtils {


    /**
     * Define that actions will be performed based on command line definition of actions in[cliActions]
     * For specific CLI actions, additional actions are added
     */
    fun addAdditionalActions(cliActions: MutableList<Action>) {

        for (index in cliActions.size - 1 downTo 0) {
            when (cliActions[index]) {
                Action.GENERATE_MAPSFORGE -> {
                    cliActions.addAll(index, listOf(Action.EXTRACT, Action.COASTLINE, Action.TRANSFORM, Action.MERGE))
                    if (!cliActions.contains(Action.POI_DB_V2)) {
                        cliActions.addAll(index, listOf(Action.POI_DB_V2))
                    }
                }

                Action.GENERATE_MBTILES -> {
                    if (!Utils.isLocalDEV() && !cliActions.contains(Action.POI_DB_V2)) {
                        cliActions.addAll(index, listOf(Action.POI_DB_V2))
                    }
                }

                Action.UPLOAD -> {
                    cliActions.addAll(index, listOf(Action.COMPRESS, Action.CREATE_JSON))
                }

                else -> {}
            }
        }

        // if actions contains compress and not create_json add it
        if (cliActions.contains(Action.COMPRESS) && !cliActions.contains(Action.CREATE_JSON)) {
            cliActions.add(Action.CREATE_JSON)
        }
    }

    /**
     * Check that pyhgtmap util is installed and available in the path
     */
    fun getCheckPyhgtmapPath(): String {
        val pyhgtmap = "pyhgtmap"
        val command = checkApps(listOf(pyhgtmap))

        if (command.isEmpty()) {
            // If none of the commands succeeded, throw an exception
            throw Exception("pyhgtmap not installed. Please install it using 'pip install pyhgtmap'")
        }
        return command
    }

    fun getCheckOsmiumPath(): String {
        val osmiumPaths =
            if (isWindows()) listOf("osmium.exe", "c:\\Users\\petrv\\miniconda3\\Library\\bin\\osmium.exe") else listOf(
                "osmium"
            )

        val command = checkApps(osmiumPaths)

        if (command.isEmpty()) {
            // If none of the commands succeeded, throw an exception
            throw Exception("Omium not found in locatios: $osmiumPaths")
        }
        return command
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


    fun getCheckPmtilesPath(): String {
        val pmtilesCommands = if (isWindows()) listOf("pmtiles.exe", "pmtiles") else listOf("pmtiles")

        val command = checkApps(pmtilesCommands, "version", "pmtiles")

        if (command.isEmpty()) {
            // If none of the commands succeeded, throw an exception
            throw Exception(
                "PMTiles not found. Tried commands: $pmtilesCommands . " +
                        "Please install PMTiles CLI https://docs.protomaps.com/pmtiles/cli"
            )
        }
        return command
    }

    fun getCheckGdalPath(tool: String): String {
        val cmds = if (isWindows()) listOf("$tool.exe", tool) else listOf(tool)
        val command = checkApps(cmds, "--version", "GDAL")
        if (command.isEmpty()) {
            throw Exception("$tool not found. Tried: $cmds. Please install GDAL.")
        }
        return command
    }

    /**
     * Check that rio (rasterio CLI) is installed and available in the path.
     * Required by the rio-rgbify plugin.
     */
    fun getCheckRioRgbifyPath(): String {
        val rioCommands = if (isWindows()) listOf("rio.exe", "rio") else listOf("rio")
        val command = checkApps(rioCommands, "--version", "")
        if (command.isEmpty()) {
            throw Exception(
                "rio not found. Tried commands: $rioCommands . " +
                        "Please install via: pip install rio-rgbify"
            )
        }
        return command
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
     * Check if any of the provided commands can be executed successfully and contain expected output.
     */
    fun checkApps(commands: List<String>, argument: String = "--version", expectedOutput: String = ""): String {
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

                val valid = when {
                    expectedOutput.isNotEmpty() -> output.contains(expectedOutput)
                    output.contains(fileNameWithoutExtension, ignoreCase = true) -> true
                    else -> output.isNotBlank() && process.exitValue() == 0
                }
                if (valid) return command
            } catch (_: Exception) {
                // Ignore and try the next command
            }
        }
        return ""
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