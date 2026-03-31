package com.asamm.osmTools.elevation

import com.asamm.osmTools.config.AppConfig
import com.asamm.osmTools.elevation.mapterhorn.MapterhornDownloader
import com.asamm.osmTools.utils.Logger
import com.asamm.osmTools.utils.S3Client
import com.asamm.osmTools.utils.Utils
import com.asamm.pmtiles.PmTilesExtract
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.use

class ElevationPlanetBuilder {

    private companion object {
        const val TAG = "TerrainRgbPlanetBuilder"
    }

        /**
         * Prepares the Terrain RGB planet file for LoMaps.
         *
         * Steps:
         * 1. Downloads the raw planet file from Mapterhorn if not already present.
         * 2. Extracts tiles up to the configured max zoom into a temporary file.
         * 3. Simplifies elevation precision to 1 meter for better compression.
         *
         */
    fun prepareTerrainRgb4LoMaps() {
        val cfg = AppConfig.config.terrainRgbConfig

        if (cfg.planetFile.exists()) {
            Logger.i(TAG, "Terrain RGB planet file already exists, skipping preparation: ${cfg.planetFile}")
            return
        }

        // 1. Download raw planet from Mapterhorn if not already present
        MapterhornDownloader.ensurePlanetFile()

        // 2. Extract only up to maxZoom from the mapterhorn raw planet file into a temporary file
        val extractedTmp = cfg.planetFile.resolveSibling("${cfg.planetFile.fileName}_extracted.tmp")
        try {
            if (extractedTmp.exists()) {
                // Previous run completed extract but failed on simplify — reuse the temp file
                Logger.i(TAG, "Extracted temp file already exists, skipping extract: $extractedTmp")
            } else {
                Logger.i(TAG, "Extracting tiles (maxZoom=${cfg.maxZoom}) from ${cfg.mapterhornRawFile} → $extractedTmp")

                val extractResult = PmTilesExtract.extract(
                    input = cfg.mapterhornRawFile,
                    output = extractedTmp,
                    maxZoom = cfg.maxZoom,
                    listener = { copied, total, bytes ->
                        Logger.i(TAG, "Extract progress: $copied / $total tiles (${copied * 100 / total}%, ${bytes / (1024 * 1024)} MB)")
                    }
                )

                if (extractResult == null) {
                    Logger.w(TAG, "No tiles found for maxZoom=${cfg.maxZoom}")
                    return
                }

                Logger.i(TAG, "Extract complete: ${extractResult.totalTiles} tiles, " +
                        "zoom ${extractResult.minZoom}–${extractResult.maxZoom}, " +
                        "output ${extractResult.outputSize / (1024 * 1024)} MB")
            }

            // 3. Simplify elevation precision (round to 1 m) for better compression
            Logger.i(TAG, "Simplifying terrain RGB (1 m precision) from $extractedTmp → ${cfg.planetFile}")

            val simplifyResult = TerrainRgbSimplifier.process(
                input = extractedTmp,
                output = cfg.planetFile,
                encoding = TerrainRgbCodec.Encoding.TERRARIUM,
                basePrecisionMeters = 1.0,
                listener = { processed, total, inBytes, outBytes ->
                    Logger.i(TAG, "Simplify progress: $processed / $total tiles " +
                            "(${processed * 100 / total}%, in ${inBytes / (1024 * 1024)} MB → out ${outBytes / (1024 * 1024)} MB)")
                }
            )

            if (simplifyResult != null) {
                Logger.i(TAG, "Simplify complete: ${simplifyResult.totalTiles} tiles, " +
                        "in ${simplifyResult.inputBytes / (1024 * 1024)} MB → out ${simplifyResult.outputBytes / (1024 * 1024)} MB, " +
                        "file ${simplifyResult.outputFileSize / (1024 * 1024)} MB")
            }

            // Clean up temporary extracted file only after successful simplification
            extractedTmp.deleteIfExists()
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to prepare terrain RGB planet file", e)
            // Keep extractedTmp on disk so the extract step can be skipped on retry
            throw e
        }
    }

    /**
     * Step 4: Convert the prepared terrain RGB planet file into HGT elevation files.
     *
     * Reads the most detailed zoom level from [TerrainRgbConfig.planetFile],
     * reprojects from Web Mercator to WGS84 grid, and writes one HGT file
     * per 1°×1° cell into [TerrainRgbConfig.hgtOutputDir].
     *
     * Resolution is determined by the source max zoom:
     * - zoom ≤ 11 → SRTM-3 (3 arc-second, 1201×1201)
     * - zoom ≥ 12 → SRTM-1 (1 arc-second, 3601×3601)
     */
    fun generateHgt() {
        val cfg = AppConfig.config.terrainRgbConfig

        if (!cfg.planetFile.exists()) {
            Logger.e(TAG, "Planet file not found, run preparePlanet4LoMaps first: ${cfg.planetFile}")
            return
        }

        Logger.i(TAG, "Generating HGT files from ${cfg.planetFile} → ${cfg.hgtOutputDir} " +
                "(resampling: ${cfg.hgtResampling})")

        val result = HgtGenerator.generate(
            input = cfg.planetFile,
            outputDir = cfg.hgtOutputDir,
            encoding = TerrainRgbCodec.Encoding.TERRARIUM,
            resampling = cfg.hgtResampling,
            listener = { processed, total, written ->
                Logger.i(TAG, "HGT progress: $processed / $total cells " +
                        "(${processed * 100 / total}%, $written files written)")
            }
        )

        Logger.i(TAG, "HGT generation complete: ${result.writtenFiles} / ${result.totalCells} cells " +
                "(grid ${result.gridSize}×${result.gridSize})")
    }


    public fun uploadTerrainRgbPlanetToS3() {
        val cfgTerrain = AppConfig.config.terrainRgbConfig
        val planetFile = cfgTerrain.planetFile.toFile()

        Logger.i(TAG, "================ UPLOAD TERRAIN RGB PLANET TO S3 ================")

        require(planetFile.exists()) {
            "Terrain RGB planet file does not exist: ${cfgTerrain.planetFile}"
        }

        Logger.i(TAG, "Prepare for upload to S3, terrain RGB file: ${cfgTerrain.planetFile}")

        S3Client.fromAppConfig().use { s3Client ->
            val onlineCfg = AppConfig.config.onlineLoMapsConfig
            val s3key = if (Utils.isLocalDEV()) {
                onlineCfg.s3terrainRgbPathDev
            } else {
                onlineCfg.s3terrainRgbPath
            } + "/" + cfgTerrain.planetFile.fileName

            s3Client.uploadFile(planetFile, s3key)
        }
    }
}