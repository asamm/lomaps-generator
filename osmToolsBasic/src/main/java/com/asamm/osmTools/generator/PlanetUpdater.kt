package com.asamm.osmTools.generator

import com.asamm.osmTools.cmdCommands.CmdLoMapsTools
import com.asamm.osmTools.cmdCommands.CmdOsmium
import com.asamm.osmTools.config.AppConfig
import com.asamm.osmTools.utils.FileDownloader
import com.asamm.osmTools.utils.Logger
import java.net.URL
import java.nio.file.Path
import java.time.Duration
import java.time.Instant

/**
 * PlanetUpdater is responsible for managing the update process of the OSM planet file used in the application.
 * <p>
 * It checks the age of the current planet file and decides whether to download a new version or update it incrementally.
 * - If the file is older than one month, it downloads a fresh planet file from the configured URL.
 * - If the file is older than one day, it performs an OSM update using the latest changes.
 * <p>
 * The class uses python lomaps-tools script for incremental updates
 */
class PlanetUpdater {

    private val TAG: String = PlanetUpdater::class.java.getSimpleName()

    fun update() {

        // check how old is the planet file
        val cmdOsmium = CmdOsmium()
        val timestamp = cmdOsmium.getTimeStamp(AppConfig.config.planetConfig.planetLatestPath)

        // if the planet file is older than 1 month download new one
        if (timestamp.isBefore(Instant.now().minus(Duration.ofDays(30)))) {
            Logger.i(TAG, "Planet file is older than 1 month. Downloading new one.")
            downloadPlanetFile(
                AppConfig.config.planetConfig.planetLatestURL,
                AppConfig.config.planetConfig.planetLatestPath
            )
        }

        if (timestamp.isBefore(Instant.now().minus(Duration.ofDays(1)))) {
            Logger.i(TAG, "Planet file is older than 1 day. Start OSM Update.")
            val cmdLoMapsTools = CmdLoMapsTools()
            cmdLoMapsTools.osmUpdate(AppConfig.config.planetConfig.planetLatestPath)
        } else {
            Logger.i(TAG, "Planet file is up to date.")
        }
    }

    /**
     * Download planet file from given URL to a destination path
     */
    fun downloadPlanetFile(downloadUrl: URL, destinationPath: Path) {
        // download planet file
        if (FileDownloader.download(downloadUrl.toString(), destinationPath)) {
            Logger.i(TAG, "File $destinationPath successfully downloaded.")
        } else {
            throw IllegalArgumentException("File $downloadUrl was not downloaded.")
        }
    }
}