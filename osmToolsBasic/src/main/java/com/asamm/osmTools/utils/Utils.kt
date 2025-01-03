package com.asamm.osmTools.utils

import com.asamm.osmTools.config.AppConfig
import java.io.File
import java.nio.file.Path


val TAG = "Utils"

fun isScriptInSystemPath(scriptName: String): Boolean {
    // Get the system PATH environment variable
    val systemPath = System.getenv("PATH") ?: return false

    // Split the PATH into individual directories
    val pathDirs = systemPath.split(File.pathSeparator)

    // Check if the script exists in any of the directories in PATH
    return pathDirs.any { dir ->
        val scriptFile = File(dir, scriptName)
        scriptFile.exists() && scriptFile.isFile
    }
}


fun deleteFilesInDir(directory: Path) {

    Logger.i(TAG, "Deleting files in tmp dir: " + AppConfig.config.temporaryDir);

    val directory = directory.toFile()
    if (directory.exists() && directory.isDirectory) {
        directory.listFiles()?.forEach { file ->
            file.deleteRecursively()
        }
    }
}
