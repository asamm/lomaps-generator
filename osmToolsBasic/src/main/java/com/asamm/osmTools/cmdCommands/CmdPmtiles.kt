package com.asamm.osmTools.cmdCommands

import org.apache.commons.io.FileUtils
import java.nio.file.Path

class CmdPmtiles : Cmd(ExternalApp.PMTILES) {

    fun verifyPmtiles(pmtiles: Path) {
        builder()
            .add("verify", pmtiles.toString())
            .execute()
    }

    /**
     * Extract a zoom-filtered, optionally region-clipped subset of [input] into [output].
     *
     * Output format is determined by the [output] extension (.pmtiles or .mbtiles).
     * [region] must be a GeoJSON file when provided.
     */
    fun extract(input: Path, output: Path, minZoom: Int, maxZoom: Int, region: Path? = null) {
        prepareDirectory(output)
        builder()
            .add("extract", input.toString(), output.toString())
            .add("--minzoom=$minZoom")
            .add("--maxzoom=$maxZoom")
            .apply { region?.let { add("--region=$it") } }
            .execute()
    }
}