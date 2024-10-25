package com.asamm.osmTools.cmdCommands

import com.asamm.osmTools.utils.Logger
import java.nio.file.Path

class CmdPlanetiler : Cmd(ExternalApp.PLANETILER) {

    private val TAG: String = CmdPlanetiler::class.java.simpleName

    private val cores = Runtime.getRuntime().availableProcessors()

    fun generateOutdoorTiles(input: Path, output: Path, poly: Path) {
        addCommands("--osm-path", input.toString(), "--output", output.toString(), "--outdoor",
            "--poly", poly.toString(), "--force", "--threads=$cores", "--process_threads=1")
        addCommands("--only_layers=hiking,contour")

        Logger.i(TAG, "Command: " + getCmdLine())
        execute()
        reset()
    }

    fun generateOpenMapTiles(input: Path, output: Path, poly: Path) {
        addCommands("--osm-path", input.toString(), "--output", output.toString(),
             "--force", "--threads=$cores", "--process_threads=1")
        addCommands("--exclude-layers=hiking,contour")
        // TODO for production
        // addCommands("--area=planet", "--bounds=planet")
        // addCommands("--fetch-wikidata")

        // TODO remove for produciton
        addCommands("--poly", poly.toString())

        addCommands(  "--nodemap-type=array", "--storage=mmap")
        Logger.i(TAG, "Command: " + getCmdLine())
        execute()
        reset()
    }
}