package com.asamm.osmTools.generator

import com.asamm.osmTools.cmdCommands.CmdGdalbuildvrt
import com.asamm.osmTools.cmdCommands.CmdGdalwarp
import com.asamm.osmTools.cmdCommands.CmdPmtiles
import com.asamm.osmTools.cmdCommands.CmdRioRgbify
import com.asamm.osmTools.config.AppConfig
import com.asamm.osmTools.server.S3Client
import com.asamm.osmTools.utils.Logger
import com.asamm.store.LocusStoreEnv
import java.io.File
import java.nio.file.Path

/**
 * Generates RGB-encoded terrain tiles (Mapbox Terrain-RGB) from a folder of HGT files.
 *
 * Pipeline:
 *  1. `gdalbuildvrt`  – mosaic all *.hgt files into a single VRT
 *  2. `gdalwarp`      – crop to safe lat/lon range (avoids high-latitude distortion)
 *  3. `rio rgbify`    – encode elevation as RGB MBTiles
 *  4. (optional) `pmtiles convert` – convert MBTiles → PMTiles when [output] ends in `.pmtiles`
 *  5. (optional) upload final file to S3
 */
class GenTerrainRgb(
    private val hgtDir: Path,
    private val output: File,
    private val minZoom: Int,
    private val maxZoom: Int,
) {
    private val TAG = GenTerrainRgb::class.java.simpleName

    fun process(upload: Boolean) {
        Logger.i(TAG, "Generating RGB terrain tiles from: $hgtDir → ${output.absolutePath}")

        val tempDir = AppConfig.config.temporaryDir.resolve("terrain_rgb").toFile().also { it.mkdirs() }
        val vrtRaw     = tempDir.resolve("global_4326.vrt").toPath()
        val vrtCropped = tempDir.resolve("global_cropped.vrt").toPath()

        try {
            buildVrt(vrtRaw)
            cropVrt(vrtRaw, vrtCropped)
            val finalFile = generateTiles(vrtCropped)
            if (upload) uploadToS3(finalFile)
        } finally {
            vrtRaw.toFile().delete()
            vrtCropped.toFile().delete()
        }

        Logger.i(TAG, "== Terrain RGB generation finished ==")
    }

    /** Step 1: mosaic all HGT files in [hgtDir] into one VRT with nodata = -32768. */
    private fun buildVrt(output: Path) {
        val hgtFiles = hgtDir.toFile()
            .walkTopDown()
            .filter { it.isFile && it.extension.equals("hgt", ignoreCase = true) }
            .toList()
        require(hgtFiles.isNotEmpty()) { "No HGT files found in: $hgtDir" }

        Logger.i(TAG, "Building VRT from ${hgtFiles.size} HGT files → $output")
        CmdGdalbuildvrt().buildVrt(nodata = -32768, output = output, inputFiles = hgtFiles)
    }

    /** Step 2: warp/crop [input] VRT to the safe lat/lon range → [output] VRT. */
    private fun cropVrt(input: Path, output: Path) {
        Logger.i(TAG, "Cropping VRT to safe lat/lon range → $output")
        CmdGdalwarp().warpToVrt(input = input, output = output)
    }

    /**
     * Step 3 (+4): run rio-rgbify on [vrt].
     * If [output] is `.pmtiles`, produce a temp MBTiles and convert it.
     */
    private fun generateTiles(vrt: Path): File {
        return when {
            output.extension.equals("pmtiles", ignoreCase = true) -> generatePmtiles(vrt)
            else -> generateMbtiles(vrt, output)
        }
    }

    private fun generateMbtiles(vrt: Path, dest: File): File {
        if (dest.exists() && !AppConfig.config.overwrite) {
            Logger.i(TAG, "MBTiles already exists, skipping: ${dest.absolutePath}")
            return dest
        }
        Logger.i(TAG, "Running rio rgbify: ${dest.absolutePath}")
        CmdRioRgbify().generate(
            input = vrt,
            outputMbtiles = dest.toPath(),
            minZoom = minZoom,
            maxZoom = maxZoom,
        )
        Logger.i(TAG, "MBTiles generated: ${dest.absolutePath}")
        return dest
    }

    private fun generatePmtiles(vrt: Path): File {
        if (output.exists() && !AppConfig.config.overwrite) {
            Logger.i(TAG, "PMTiles already exists, skipping: ${output.absolutePath}")
            return output
        }
        val tempMbtiles = File(output.parent, output.nameWithoutExtension + ".mbtiles")
        try {
            generateMbtiles(vrt, tempMbtiles)
            Logger.i(TAG, "Converting MBTiles → PMTiles: ${output.absolutePath}")
            CmdPmtiles().convertToPmtiles(tempMbtiles.toPath(), output.toPath())
            Logger.i(TAG, "PMTiles generated: ${output.absolutePath}")
        } finally {
            if (tempMbtiles.exists()) {
                tempMbtiles.delete()
                Logger.i(TAG, "Deleted temporary MBTiles: ${tempMbtiles.absolutePath}")
            }
        }
        return output
    }

    private fun uploadToS3(file: File) {
        val cfg = AppConfig.config.onlineLoMapsConfig
        val s3Key = when (AppConfig.config.locusStoreEnv) {
            LocusStoreEnv.DEV -> "${cfg.s3terrainRgbPathDev}/${file.name}"
            else -> "${cfg.s3terrainRgbPath}/${file.name}"
        }
        Logger.i(TAG, "Uploading to S3: $s3Key")
        S3Client.fromAppConfig().use { it.uploadFile(file, s3Key) }
        Logger.i(TAG, "Upload finished: $s3Key")
    }
}