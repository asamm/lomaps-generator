package com.asamm.osmTools.elevation.mapterhorn

import com.asamm.osmTools.config.AppConfig
import com.asamm.osmTools.utils.FileDownloader
import com.asamm.osmTools.utils.Logger
import kotlinx.serialization.json.Json
import java.io.IOException
import java.nio.file.Files

/**
 * Handles downloading of the Mapterhorn terrain-RGB planet file.
 *
 * Flow:
 *  1. Skip if the destination file already exists.
 *  2. Fetch the Mapterhorn download index JSON and locate the "planet.pmtiles" entry.
 *  3. Verify that the destination volume has enough free disk space.
 *  4. Download the file with MD5 verification via [FileDownloader].
 */
object MapterhornDownloader {

    private const val TAG = "ElevationDownloader"

    private val json = Json { ignoreUnknownKeys = true }

    // --- public API ---

    /**
     * Ensures the terrain-RGB planet file is present on disk.
     *
     * If the file already exists it is considered up-to-date and the method
     * returns immediately. Otherwise the Mapterhorn index is consulted and
     * the file is downloaded.
     *
     * @throws IOException If the index cannot be fetched, the planet entry is
     *                     missing, disk space is insufficient, or the download fails.
     */
    @Throws(IOException::class)
    fun ensurePlanetFile() {
        val terrainRgbConfig = AppConfig.config.terrainRgbConfig
        val destination = terrainRgbConfig.mapterhornRawFile

        if (Files.exists(destination)) {
            Logger.i(TAG, "Mapterhorn raw planet file already exists, skipping download: $destination")
            return
        }

        val jsonItem = fetchPlanetEntry(terrainRgbConfig.mapterhornIndexUrl)
        FileDownloader.checkDiskSpace(destination, jsonItem.sizeBytes)

        Logger.i(TAG, "Mapterhorn raw planet file not found, starting download process")
        FileDownloader.download(destination = destination, url = jsonItem.url, expectedMd5 = jsonItem.md5sum)
    }

    // --- internal helpers ---

    /**
     * Downloads and parses the Mapterhorn index, then returns the entry for planet id defined in
     * [AppConfig.config.terrainRgbConfig.mapterhornPlanetEntryName].
     *
     * @throws IOException If the index cannot be fetched or the planet entry is absent.
     */
    private fun fetchPlanetEntry(indexUrl: String): MapterhornEntry {
        Logger.i(TAG, "Fetching Mapterhorn index from $indexUrl")
        val raw = FileDownloader.downloadAndReadAsString(indexUrl)

        // find planet file in index JSON
        val index: MapterhornIndex = json.decodeFromString(raw)
        val terrainConf = AppConfig.config.terrainRgbConfig

        // find planet entry or throw exception
        return index.items.find { it.name == terrainConf.mapterhornPlanetEntryName }
            ?: throw IOException(
                "Entry '${terrainConf.mapterhornPlanetEntryName}' not found in Mapterhorn index ($indexUrl). " +
                "Available entries: ${index.items.map { it.name }}"
            )
    }

}