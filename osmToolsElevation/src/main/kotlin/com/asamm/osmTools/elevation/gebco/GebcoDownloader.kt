package com.asamm.osmTools.elevation.gebco

import com.asamm.osmTools.config.AppConfig
import com.asamm.osmTools.utils.FileDownloader
import com.asamm.osmTools.utils.Logger
import com.asamm.osmTools.utils.Utils
import com.asamm.osmTools.utils.ZipUtils
import java.io.IOException
import java.nio.file.*

/**
 * Handles downloading and unpacking of GEBCO bathymetry data.
 *
 * GEBCO (General Bathymetric Chart of the Oceans) provides global gridded
 * bathymetry data in NetCDF format. Two datasets are needed:
 *
 * 1. **Elevation grid** – ocean floor depth (negative values) and land elevation.
 * 2. **TID grid** (Type Identifier) – classifies each pixel as land, ocean,
 *    interpolated, etc. Used to filter out land pixels so that only ocean
 *    floor bathymetry is retained in the final terrain-RGB output.
 *
 * The data is distributed as ZIP archives containing NetCDF (.nc) files
 * plus auxiliary files (readme, metadata). All contents are extracted into
 * separate directories (elevation dir, TID dir).
 *
 * Flow:
 *  1. Skip if the target directory already contains files.
 *  2. Download the ZIP archive to a temporary file.
 *  3. Extract all contents from the ZIP into the configured directory.
 *  4. Clean up the temporary ZIP file.
 */
object GebcoDownloader {

    private const val TAG = "GebcoDownloader"

    /**
     * Ensures both GEBCO elevation and TID data are unpacked on disk.
     *
     * Downloads and extracts each archive only when the target directory is empty/missing.
     *
     * @throws IOException If download or extraction fails.
     */
    @Throws(IOException::class)
    fun ensureGebcoFiles() {
        val cfg = AppConfig.config.terrainRgbConfig

        // Create work directory if needed
        Files.createDirectories(cfg.gebcoWorkDir)

        // Download and extract elevation data
        ensureExtracted(
            targetDir = cfg.gebcoElevationDir,
            downloadUrl = cfg.gebcoElevationUrl,
            label = "GEBCO elevation"
        )

        // Download and extract TID data
        ensureExtracted(
            targetDir = cfg.gebcoTidDir,
            downloadUrl = cfg.gebcoTidUrl,
            label = "GEBCO TID"
        )

        Logger.i(TAG, "All GEBCO data files are ready")
    }

    /**
     * Downloads and extracts a GEBCO ZIP archive into [targetDir] if not already present.
     *
     * The directory is considered "ready" when it exists and contains at least one .nc file.
     *
     * @param targetDir   Directory where the archive contents will be extracted.
     * @param downloadUrl URL of the ZIP archive to download.
     * @param label       Human-readable label for logging.
     */
    private fun ensureExtracted(targetDir: Path, downloadUrl: String, label: String) {
        if (isDirectoryReady(targetDir)) {
            Logger.i(TAG, "$label data already extracted, skipping: $targetDir")
            return
        }

        val zipFile = targetDir.resolveSibling("${targetDir.fileName}.zip")

        try {
            // Step 1: Download ZIP archive
            if (!Files.exists(zipFile)) {
                Logger.i(TAG, "Downloading $label ZIP archive from $downloadUrl")

                // Check available disk space – GEBCO ZIPs are ~7-8 GB, need space for ZIP + extracted
                FileDownloader.checkDiskSpace(zipFile, 20L * 1024 * 1024 * 1024) // 20 GB safety margin

                FileDownloader.download(
                    url = downloadUrl,
                    destination = zipFile
                )

                Logger.i(TAG, "$label ZIP downloaded: $zipFile (${Utils.formatBytesToHuman(Files.size(zipFile))})")
            } else {
                Logger.i(TAG, "$label ZIP already downloaded, skipping download: $zipFile")
            }

            // Step 2: Extract all files from ZIP into target directory
            Logger.i(TAG, "Extracting $label archive into $targetDir")
            ZipUtils.unzipFile(zipFile, targetDir)

            // Step 3: Clean up ZIP archive to save disk space
            Files.deleteIfExists(zipFile)
            Logger.i(TAG, "$label ZIP archive removed: $zipFile")

        } catch (e: Exception) {
            Logger.e(TAG, "Failed to download/extract $label data", e)
            // Keep ZIP on disk for retry (avoid re-downloading ~8 GB)
            throw IOException("Failed to prepare $label data: ${e.message}", e)
        }
    }

    /**
     * Checks whether [dir] exists and contains at least one .nc file.
     */
    private fun isDirectoryReady(dir: Path): Boolean {
        if (!Files.isDirectory(dir)) return false
        return Files.list(dir).use { stream ->
            stream.anyMatch { it.fileName.toString().lowercase().endsWith(".nc") }
        }
    }
}
