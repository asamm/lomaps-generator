package com.asamm.osmTools.cmdCommands

import com.asamm.osmTools.config.AppConfig
import com.asamm.osmTools.utils.Logger
import com.asamm.osmTools.utils.Utils
import java.nio.file.Path

class CmdPlanetiler : Cmd(ExternalApp.PLANETILER) {

    private val TAG: String = CmdPlanetiler::class.java.simpleName

    private val cores = Runtime.getRuntime().availableProcessors()

    fun generateOutdoorTiles(input: Path, output: Path, poly: Path) {

        addCommands(
            "--osm-path", input.toString(),
            "--output", output.toString(),
            "--force",
            "--threads=$cores",
            "--download_dir=${AppConfig.config.planetConfig.planetilerDownloadDir}",
            "--tmpdir=${AppConfig.config.temporaryDir}",
            "--poly", poly.toString(),
            "--only_layers=" + AppConfig.config.planetConfig.lomapsOutdoorsLayers.joinToString(",")
        )

        if (Utils.isLocalDEV()) {
            // only for local DEV testing
            addCommand("--threads=${cores/2}")

        } else {
            addCommands(
                "--download",
                "--nodemap-type=array",
                "--storage=mmap",

            )
        }

        Logger.i(TAG, "Command: " + getCmdLine())
        execute()
    }

    fun generateLoMapsOpenMapTiles(input: Path, output: Path, poly: Path) {
        addCommands(
            "--osm-path", input.toString(),
            "--output", output.toString(),
            "--force",
            "--threads=$cores",
            "--download_dir=${AppConfig.config.planetConfig.planetilerDownloadDir}",
            "--tmpdir=${AppConfig.config.temporaryDir}",
            "--download",
            "--poly", poly.toString()
        )

        addCommand("--download")

        if (!Utils.isLocalDEV()) {
            addCommands(
                "--nodemap-type=array",
                "--storage=mmap",
            )
        }

        Logger.i(TAG, "Command: " + getCmdLine())
        execute()
    }

}