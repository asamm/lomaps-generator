package com.asamm.osmTools.elevation

import com.asamm.osmTools.config.AppConfig
import com.asamm.osmTools.elevation.gebco.BathymetryTileGenerator
import com.asamm.osmTools.elevation.gebco.GebcoDownloader
import com.asamm.osmTools.elevation.gebco.GebcoReader
import com.asamm.osmTools.elevation.mapterhorn.MapterhornDownloader
import com.asamm.osmTools.utils.Logger
import com.asamm.osmTools.utils.S3Client
import com.asamm.osmTools.utils.Utils
import com.asamm.pmtiles.PmTilesCluster
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
    fun prepareTerrainRgbMapternhorn() {
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
                        Logger.i(TAG, "Extract progress: $copied / $total tiles (${copied * 100 / total}%, ${Utils.formatBytesToHuman(bytes)})")
                    }
                )

                if (extractResult == null) {
                    Logger.w(TAG, "No tiles found for maxZoom=${cfg.maxZoom}")
                    return
                }

                Logger.i(TAG, "Extract complete: ${extractResult.totalTiles} tiles, " +
                        "zoom ${extractResult.minZoom}–${extractResult.maxZoom}, " +
                        "output ${Utils.formatBytesToHuman(extractResult.outputSize)}")
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
                            "(${processed * 100 / total}%, in ${Utils.formatBytesToHuman(inBytes)} → out ${Utils.formatBytesToHuman(outBytes)})")
                }
            )

            if (simplifyResult != null) {
                Logger.i(TAG, "Simplify complete: ${simplifyResult.totalTiles} tiles, " +
                        "in ${Utils.formatBytesToHuman(simplifyResult.inputBytes)} → out ${Utils.formatBytesToHuman(simplifyResult.outputBytes)}, " +
                        "file ${Utils.formatBytesToHuman(simplifyResult.outputFileSize)}")
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
     * Convert the prepared terrain RGB planet file into HGT elevation files.
     *
     * Reads the most detailed zoom level from [TerrainRgbConfig.planetFile],
     * reprojects from Web Mercator to WGS84 grid, and writes one HGT file
     * per 1°×1° cell into [TerrainRgbConfig.hgtOutputDir].
     *
     * Resolution is determined by the source max zoom:
     * - zoom ≤ 11 → SRTM-3 (3 arc-second, 1201×1201)
     * - zoom ≥ 12 → SRTM-1 (1 arc-second, 3601×3601)
     */
    fun generateHgtForLand() {
        val cfg = AppConfig.config.terrainRgbConfig

        if (!cfg.planetFile.exists()) {
            Logger.e(TAG, "Planet file not found, run preparePlanet4LoMaps first: ${cfg.planetFile}")
            return
        }

        Logger.i(TAG, "Generating HGT files from ${cfg.planetFile} → ${cfg.hgtOutputDir} " +
                "(resampling: ${cfg.terrainResampling})")

        val result = HgtGenerator.generate(
            input = cfg.planetFile,
            outputDir = cfg.hgtOutputDir,
            encoding = TerrainRgbCodec.Encoding.TERRARIUM,
            resampling = cfg.terrainResampling,
            listener = { processed, total, written ->
                Logger.i(TAG, "HGT progress: $processed / $total cells " +
                        "(${processed * 100 / total}%, $written files written)")
            }
        )

        Logger.i(TAG, "HGT generation complete: ${result.writtenFiles} / ${result.totalCells} cells " +
                "(grid ${result.gridSize}×${result.gridSize})")
    }


    /**
     * Prepares bathymetry (ocean floor) terrain-RGB tiles from GEBCO data.
     *
     * Downloads and extracts GEBCO elevation and TID NetCDF files.
     * Reads elevation + TID grids, filters land pixels, encodes ocean
     *   floor depth as terrain-RGB tiles
     */
    fun prepareBathymetry() {
        val cfg = AppConfig.config.terrainRgbConfig

        Logger.i(TAG, "================ PREPARE BATHYMETRY ================")

        if (cfg.bathymetryPlanetFile.exists() && !AppConfig.config.overwrite) {
            Logger.i(TAG, "Bathymetry planet file already exists, skipping: ${cfg.bathymetryPlanetFile}")
            return
        }

        // Step 1: Download and extract GEBCO NetCDF data (elevation + TID)
        GebcoDownloader.ensureGebcoFiles()

        // Step 2: Read GEBCO data, filter land using TID, generate terrain-RGB tiles
        Logger.i(TAG, "Generating bathymetry terrain-RGB tiles (zoom 0–${cfg.bathymetryMaxZoom})")

        GebcoReader.open(cfg.gebcoElevationDir, cfg.gebcoTidDir).use { reader ->
            val result = BathymetryTileGenerator.generate(
                reader = reader,
                output = cfg.bathymetryPlanetFile,
                maxZoom = cfg.bathymetryMaxZoom,
                resampling = cfg.terrainResampling,
                encoding = TerrainRgbCodec.Encoding.TERRARIUM,
                listener = { processed, total, phase ->
                    Logger.i(TAG, "Bathymetry progress [$phase]: $processed / $total tiles")
                }
            )

            Logger.i(TAG, "Bathymetry generation complete: ${result.totalTiles} tiles, " +
                    "max-zoom: ${result.maxZoomTiles}, file: ${Utils.formatBytesToHuman(result.outputFileSize)}")
        }
    }


    /**
     * Re-clusters the terrain RGB planet PMTiles file so that tile data is stored
     * in Hilbert-curve order (`clustered = true`), improving sequential read performance.
     *
     * Skips if the file is already clustered.
     */
    fun clusterTerrainRgbPlanet() {
        val cfg = AppConfig.config.terrainRgbConfig

        Logger.i(TAG, "================ CLUSTER TERRAIN RGB PLANET ================")

        if (!cfg.planetFile.exists()) {
            Logger.e(TAG, "Planet file not found, run preparePlanet4LoMaps first: ${cfg.planetFile}")
            return
        }

        Logger.i(TAG, "Clustering terrain RGB planet: ${cfg.planetFile}")

        val result = PmTilesCluster.cluster(
            input = cfg.planetFile,
            deduplicate = true,
            listener = { processed, total ->
                if (total > 0) {
                    Logger.i(TAG, "Cluster progress: $processed / $total entries (${processed * 100 / total}%)")
                }
            }
        )

        if (result == null) {
            Logger.i(TAG, "Terrain RGB planet is already clustered, nothing to do.")
        } else {
            Logger.i(TAG, "Clustering complete: ${result.addressedTiles} addressed tiles, " +
                    "${result.tileEntries} entries, ${result.tileContents} unique contents, " +
                    "file: ${Utils.formatBytesToHuman(result.outputFileSize)}")
        }
    }

    /**
     * Uploads the terrain RGB (land elevation) PMTiles planet file to S3.
     */
    fun uploadTerrainRgbToS3() {
        val cfg = AppConfig.config.terrainRgbConfig
        val planetFile = cfg.planetFile.toFile()

        Logger.i(TAG, "================ UPLOAD TERRAIN RGB (LAND) TO S3 ================")

        require(planetFile.exists()) {
            "Terrain RGB planet file does not exist: ${cfg.planetFile}"
        }

        Logger.i(TAG, "Uploading terrain RGB file to S3: ${cfg.planetFile}")

        S3Client.fromAppConfig().use { s3Client ->
            val onlineCfg = AppConfig.config.onlineLoMapsConfig
            val s3key = if (Utils.isLocalDEV()) {
                onlineCfg.s3terrainRgbPathDev
            } else {
                onlineCfg.s3terrainRgbPath
            } + "/" + cfg.planetFile.fileName

            s3Client.uploadFile(planetFile, s3key)
        }
    }

    /**
     * Uploads the bathymetry terrain RGB (ocean floor) PMTiles planet file to S3.
     */
    fun uploadBathymetryToS3() {
        val cfg = AppConfig.config.terrainRgbConfig
        val bathymetryFile = cfg.bathymetryPlanetFile.toFile()

        Logger.i(TAG, "================ UPLOAD BATHYMETRY TERRAIN RGB TO S3 ================")

        require(bathymetryFile.exists()) {
            "Bathymetry planet file does not exist: ${cfg.bathymetryPlanetFile}"
        }

        Logger.i(TAG, "Uploading bathymetry terrain RGB file to S3: ${cfg.bathymetryPlanetFile}")

        S3Client.fromAppConfig().use { s3Client ->
            val onlineCfg = AppConfig.config.onlineLoMapsConfig
            val s3key = if (Utils.isLocalDEV()) {
                onlineCfg.s3bathymetryRgbPathDev
            } else {
                onlineCfg.s3bathymetryRgbPath
            } + "/" + cfg.bathymetryPlanetFile.fileName

            s3Client.uploadFile(bathymetryFile, s3key)
        }
    }
}