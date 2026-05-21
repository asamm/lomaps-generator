package com.asamm.osmTools.cmdCommands

import com.asamm.osmTools.config.AppConfig
import java.nio.file.Path

class CmdLoMapsTools : Cmd(ExternalApp.LOMAPS_TOOLS) {

    fun generateTourist(pathToSource: Path, pathToTourist: Path) {
        // On Windows, osmium inside the Python tool exits with code 15 due to
        // a known pyosmium issue: https://github.com/osmcode/pyosmium/issues/280
        val acceptedCodes = if (com.asamm.osmTools.config.ConfigUtils.isWindows()) setOf(0, 15) else setOf(0)
        builder()
            .add("tourist2ways")
            .addIf(AppConfig.config.overwrite, "--overwrite")
            .add("-i", pathToSource.toString())
            .add("-o", pathToTourist.toString())
            .add("--nodeid", AppConfig.config.touristConfig.nodeId.toString())
            .add("--wayid", AppConfig.config.touristConfig.wayId.toString())
            .execute(acceptedExitCodes = acceptedCodes)
    }

    fun osmUpdate(path: Path) {
        builder()
            .addIf(AppConfig.config.loggerConfig.verbose, "-v")
            .add("osmupdate", "-i", path.toString())
            .execute()
    }
}