package com.asamm.osmTools.cmdCommands

import com.asamm.osmTools.config.AppConfig
import com.asamm.osmTools.utils.Utils
import java.nio.file.Path

class CmdPlanetiler : Cmd(ExternalApp.PLANETILER) {

    private val cores = Runtime.getRuntime().availableProcessors()

    fun generateOutdoorTiles(input: Path, output: Path, poly: Path) {
        builder()
            .add(
                "--osm-path", input.toString(),
                "--output", output.toString(),
                "--force",
                "--threads=$cores",
                "--download_dir=${AppConfig.config.planetConfig.planetilerDownloadDir}",
                "--tmpdir=${AppConfig.config.temporaryDir}",
                "--poly", poly.toString(),
                "--only_layers=" + AppConfig.config.planetConfig.lomapsOutdoorsLayers.joinToString(",")
            )
            .apply {
                if (Utils.isLocalDEV()) {
                    add("--threads=${cores / 2}")
                } else {
                    add("--download", "--nodemap-type=array", "--storage=mmap")
                }
            }
            .execute()
    }

    fun generateLoMapsPlanetPmtiles(input: Path, output: Path, poly: Path) {
        builder()
            .add(
                "--osm-path", input.toString(),
                "--output", output.toString(),
                "--force",
                "--threads=$cores",
                "--download_dir=${AppConfig.config.planetConfig.planetilerDownloadDir}",
                "--tmpdir=${AppConfig.config.temporaryDir}",
                "--download",
                "--poly", poly.toString()
            )
//            .apply {
//                if (!Utils.isLocalDEV()) {
//                    add("--nodemap-type=array", "--storage=mmap")
//                }
//            }
            .add("lomaps_contour_minzoom=13")
            .execute()
    }
}