package com.asamm.osmTools.cmdCommands

import java.io.File
import java.nio.file.Path

/**
 * Wraps `gdalbuildvrt` — assembles a mosaic VRT from a list of source files.
 */
class CmdGdalbuildvrt : Cmd(ExternalApp.GDALBUILDVRT) {

    /**
     * Build a VRT mosaic from [inputFiles] written to [output].
     *
     * Paths are passed via a temporary `-input_file_list` text file to avoid
     * OS command-line length limits (25 000 HGT files easily exceed ARG_MAX).
     * The temp file is deleted after the command completes.
     *
     * @param nodata      Nodata value assigned to the VRT (e.g. -32768 for void HGT pixels)
     * @param output      Destination VRT file path
     * @param inputFiles  Source raster files (e.g. all *.hgt in a folder)
     */
    fun buildVrt(nodata: Int, output: Path, inputFiles: List<File>) {
        require(inputFiles.isNotEmpty()) { "No input files provided for gdalbuildvrt" }

        val fileList = File.createTempFile("gdalbuildvrt_inputs_", ".txt")
        try {
            fileList.writeText(inputFiles.joinToString("\n") { it.absolutePath })
            builder()
                .add("-vrtnodata", nodata.toString())
                .add("-input_file_list", fileList.absolutePath)
                .add(output.toAbsolutePath().toString())
                .execute()
        } finally {
            fileList.delete()
        }
    }
}

/**
 * Wraps `gdalwarp` — reprojects and/or crops a raster.
 */
class CmdGdalwarp : Cmd(ExternalApp.GDALWARP) {

    /**
     * Warp [input] into [output] (VRT format), cropping to the given bounding box.
     *
     * @param input      Source raster (e.g. a VRT built by [CmdGdalbuildvrt])
     * @param output     Destination VRT file path
     * @param minLon     West bound  (default -180)
     * @param minLat     South bound (default -71.95, excludes deep Antarctica)
     * @param maxLon     East bound  (default  180)
     * @param maxLat     North bound (default  84.95, excludes extreme Arctic)
     * @param nodata     Nodata value carried through warp (default -32768)
     */
    fun warpToVrt(
        input: Path,
        output: Path,
        minLon: Double = -180.0,
        minLat: Double = -71.95,
        maxLon: Double = 180.0,
        maxLat: Double = 84.95,
        nodata: Int = -32768,
        targetSrs: String = "EPSG:4326",
        resampling: String = "bilinear",
    ) {
        builder()
            .add("-of", "VRT")
            .add("-overwrite")
            .add("-t_srs", targetSrs)
            .add("-te", minLon.toString(), minLat.toString(), maxLon.toString(), maxLat.toString())
            .add("-r", resampling)
            .add("-srcnodata", nodata.toString())
            .add("-dstnodata", nodata.toString())
            .add(input.toAbsolutePath().toString())
            .add(output.toAbsolutePath().toString())
            .execute()
    }
}