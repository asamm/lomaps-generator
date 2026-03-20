package com.asamm.osmTools.cmdCommands

import org.apache.commons.io.FileUtils
import java.nio.file.Path

class CmdPmtiles : Cmd(ExternalApp.PMTILES) {

    fun convertToPmtiles(inputMbtiles: Path, outputPmtiles: Path) {
        FileUtils.forceMkdir(outputPmtiles.parent.toFile())
        builder()
            .add("convert", inputMbtiles.toString(), outputPmtiles.toString())
            .execute()
    }

    fun verifyPmtiles(pmtiles: Path) {
        builder()
            .add("verify", pmtiles.toString())
            .execute()
    }
}