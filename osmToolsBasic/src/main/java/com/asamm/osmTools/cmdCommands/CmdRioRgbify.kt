package com.asamm.osmTools.cmdCommands

import java.nio.file.Path

/**
 * Wraps the `rio rgbify` command (rasterio + rio-rgbify plugin).
 *
 * Converts an elevation raster (GeoTIFF or VRT) into an RGB-encoded MBTiles file
 * suitable for MapLibre terrain (Mapbox Terrain-RGB encoding).
 */
class CmdRioRgbify : Cmd(ExternalApp.RIO_RGBIFY) {

    /**
     * Generate RGB terrain tiles from [input] into [outputMbtiles].
     *
     * @param input         Path to the source elevation raster (GeoTIFF or VRT)
     * @param outputMbtiles Path for the output MBTiles file
     * @param minZoom       Minimum tile zoom level (default 3)
     * @param maxZoom       Maximum tile zoom level (default 11)
     * @param workers       Number of parallel workers (default: available CPU cores)
     * @param format        Output tile image format: "webp" or "png" (default "webp")
     */
    fun generate(
        input: Path,
        outputMbtiles: Path,
        minZoom: Int = 3,
        maxZoom: Int = 11,
        workers: Int = Runtime.getRuntime().availableProcessors() -8, // TODO remove limited number of processors
        format: String = "webp",
    ) {
        // prepare directory for generation
        prepareDirectory(outputMbtiles)

        builder()
            .add("-b", "-10000")
            .add("-i", "0.1")
            .add("--min-z", minZoom.toString())
            .add("--max-z", maxZoom.toString())
            .add("-j", workers.toString())
            .add("--format", format)
            .add(input.toAbsolutePath().toString())
            .add(outputMbtiles.toAbsolutePath().toString())
            .execute()
    }
}