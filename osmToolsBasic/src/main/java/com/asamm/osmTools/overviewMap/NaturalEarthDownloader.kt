package com.asamm.osmTools.overviewMap

import com.asamm.osmTools.config.NaturalEarthConfig
import com.asamm.osmTools.utils.FileDownloader
import com.asamm.osmTools.utils.Logger
import com.asamm.osmTools.utils.Utils
import java.nio.file.Files
import java.nio.file.Path

/**
 * Downloads and extracts Natural Earth data sources.
 *
 * ZIP files are kept in [NaturalEarthConfig.dataDir] permanently so re-runs only need to
 * re-extract, not re-download.  After the output PBF is written the caller should invoke
 * [deleteExtractedData] to remove the large extracted directories while leaving the ZIPs intact.
 */
object NaturalEarthDownloader {

    private const val TAG = "NaturalEarthDownloader"

    /**
     * Ensures the World Base Map Shapefiles are extracted and ready.
     * - If the extracted directory already exists, returns it immediately.
     * - If the ZIP already exists locally, extracts from it (no download).
     * - Otherwise downloads the ZIP first, then extracts.
     * @return path to the directory containing extracted SHP files
     */
    fun ensureNaturalEarthBaseShp(config: NaturalEarthConfig): Path {
        val shpDir = config.dataDir.resolve("basemap_shp")
        if (Files.exists(shpDir) && Files.list(shpDir).use { it.anyMatch { f -> f.toString().endsWith(".shp") } }) {
            Logger.i(TAG, "Base map SHP directory already exists: $shpDir")
            return shpDir
        }

        val zipPath = config.dataDir.resolve("World-Base-Map-Shapefiles.zip")
        ensureZip(config.baseMapShpUrl, zipPath)

        Logger.i(TAG, "Extracting: $zipPath → $shpDir")
        Files.createDirectories(shpDir)

        Utils.unzipFile(zipPath, shpDir)
        Logger.i(TAG, "Extraction complete: $shpDir")

        return shpDir
    }

    /**
     * Ensures the Natural Earth GeoPackage is extracted and ready.
     * - If the .gpkg file already exists, returns it immediately.
     * - If the ZIP already exists locally, extracts from it (no download).
     * - Otherwise downloads the ZIP first, then extracts.
     * @return path to the .gpkg file
     */
    fun ensureGpkg(config: NaturalEarthConfig): Path {
        val gpkgDir = config.dataDir.resolve("gpkg")
        val gpkgFile = gpkgDir.resolve("natural_earth_vector.gpkg")
        if (Files.exists(gpkgFile)) {
            Logger.i(TAG, "GeoPackage already exists: $gpkgFile")
            return gpkgFile
        }

        val zipPath = config.dataDir.resolve("natural_earth_vector.gpkg.zip")
        ensureZip(config.gpkgUrl, zipPath)

        Logger.i(TAG, "Extracting: $zipPath → $gpkgDir")
        Files.createDirectories(gpkgDir)

        // unzip
        Utils.unzipFile(zipPath, gpkgDir)

        // Find the .gpkg file in the extracted directory (may be nested)
        val found = Files.walk(gpkgDir).use { stream ->
            stream.filter { it.toString().endsWith(".gpkg") }.findFirst().orElse(null)
        }
        requireNotNull(found) { "No .gpkg file found after extracting: $zipPath" }

        // Move to expected location if needed
        if (found != gpkgFile) {
            Files.createDirectories(gpkgFile.parent)
            Files.move(found, gpkgFile)
        }

        Logger.i(TAG, "Extraction complete: $gpkgFile")
        return gpkgFile
    }

    /**
     * Deletes the extracted data directories (basemap_shp and gpkg) while keeping the
     * downloaded ZIP files.  Call this after the output PBF has been successfully written.
     */
    fun deleteExtractedData(config: NaturalEarthConfig) {
        listOf(
            config.dataDir.resolve("basemap_shp"),
            config.dataDir.resolve("gpkg"),
        ).forEach { dir ->
            if (Files.exists(dir)) {
                Logger.i(TAG, "Deleting extracted data: $dir")
                dir.toFile().deleteRecursively()
            }
        }
    }

    /** Downloads the ZIP only if it is not already present locally. */
    private fun ensureZip(url: String, zipPath: Path) {

        if (Files.exists(zipPath)) {
            Logger.i(TAG, "Using cached ZIP: $zipPath")
            return
        }
        Files.createDirectories(zipPath.parent)

        Logger.i(TAG, "Downloading: $url")
        FileDownloader.download(url, zipPath)
    }
}
