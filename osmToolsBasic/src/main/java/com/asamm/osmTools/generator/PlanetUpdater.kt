package com.asamm.osmTools.generator

import com.asamm.osmTools.cmdCommands.CmdLoMapsTools
import com.asamm.osmTools.cmdCommands.CmdOsmium
import com.asamm.osmTools.config.AppConfig
import com.asamm.osmTools.utils.Logger
import com.asamm.osmTools.utils.UtilsHttp
import java.net.URL
import java.nio.file.Path
import java.time.Duration
import java.time.Instant

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
        } else if (timestamp.isBefore(Instant.now().minus(Duration.ofDays(1)))) {
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
        if (UtilsHttp.downloadFile(destinationPath, downloadUrl.toString())) {
            Logger.i(TAG, "File $destinationPath successfully downloaded.")
        } else {
            throw IllegalArgumentException("File $downloadUrl was not downloaded.")
        }
    }
}