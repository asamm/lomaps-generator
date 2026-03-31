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

    /**
     * Extract a zoom-filtered subset of [input] into [output].
     *
     * Equivalent to: `pmtiles extract <input> <output> --minzoom=N --maxzoom=N`
     */
    fun extract(input: Path, output: Path, minZoom: Int, maxZoom: Int) {
        prepareDirectory(output)
        builder()
            .add("extract", input.toString(), output.toString())
            .add("--minzoom=$minZoom")
            .add("--maxzoom=$maxZoom")
            .execute()
    }
}