package com.asamm.osmTools.cmdCommands

import com.asamm.osmTools.config.AppConfig
import java.nio.file.Path
import kotlin.io.path.absolutePathString

class CmdShp2osm : Cmd(ExternalApp.LOMAPS_TOOLS) {

    fun shp2osm(input: Path, output: Path) {
        builder()
            .add("shp2osm")
            .add("--id", AppConfig.config.coastlineConfig.nodeBorderId++.toString())
            .add("--input", input.absolutePathString())
            .add("--output", output.absolutePathString())
            .execute()
    }
}