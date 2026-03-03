package com.asamm.osmTools.cmdCommands

import org.apache.commons.io.FileUtils
import java.nio.file.Path

class CmdPmtiles : Cmd(ExternalApp.PMTILES) {

     fun convertToPmtiles(inputMbtiles: Path, outputPmtiles: Path) {
         addCommands(
             "convert",
             inputMbtiles.toString(),
             outputPmtiles.toString()
         )

         FileUtils.forceMkdir(outputPmtiles.parent.toFile())

         execute()
         reset()
     }

    fun verifyPmtiles(pmtiles: Path) {
        addCommands(
            "verify",
            pmtiles.toString()
        )

        execute()
        reset()
    }
}