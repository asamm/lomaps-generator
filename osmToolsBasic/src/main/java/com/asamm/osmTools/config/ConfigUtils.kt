package com.asamm.osmTools.config

import java.io.File

object ConfigUtils {


    /**
     * Define that actions will be performed based on command line definition of actions in[cliActions]
     * For specific CLI actions, additional actions are added
     */
    fun addAdditionalActions(cliActions: MutableSet<Action>) {

        // for MapsForge generation add Coastline action, transform and merge
        when {
            cliActions.contains(Action.GENERATE_MAPSFORGE) -> {
                cliActions.add(Action.COASTLINE)
                cliActions.add(Action.TRANSFORM)
                cliActions.add(Action.MERGE)

                cliActions.add(Action.COMPRESS)
                cliActions.add(Action.CREATE_JSON)
            }
        }
    }

    // Function to check if Python is installed and return its path
    fun findPythonPath(): String {
        // First try to find "python", if not found, try "python3"
        val pythonCommands = if (isWindows()) listOf("python.exe", "python3.exe") else listOf("python", "python3")

        for (pythonCommand in pythonCommands) {
            try {
                // Use ProcessBuilder to run the "python --version" or "python3 --version" command
                val process = ProcessBuilder(pythonCommand, "--version")
                    .redirectErrorStream(true)
                    .start()

                // Wait for the process to complete and check if Python is found
                val output = process.inputStream.bufferedReader().readText()
                process.waitFor()

                if (output.startsWith("Python")) {
                    // If Python is found, return the path of the executable
                    return pythonCommand
                }
            } catch (e: Exception) {
                // Ignore and try the next command
            }
        }

        // If none of the commands succeeded, throw an exception
        throw Exception("Python not found")
    }

    // Helper function to check if the system is Windows
    private fun isWindows(): Boolean {
        return System.getProperty("os.name").lowercase().contains("win")
    }

    /**
     * Check that pyhgtmap util is installed and available in the path
     */
    fun getCheckPyhgtmapPath(): String {
        val pyhgtmap = "pyhgtmap"
        val process = ProcessBuilder(pyhgtmap, "--version")
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().readText()
        process.waitFor()

        if (output.startsWith("pyhgtmap")) {
            return pyhgtmap
        } else {
            throw Exception("pyhgtmap not installed. Please install it using 'pip install pyhgtmap'")
        }
    }
}