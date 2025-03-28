package com.asamm.osmTools

import com.asamm.osmTools.config.Action
import com.asamm.osmTools.config.AppConfig
import com.asamm.osmTools.config.ConfigUtils
import com.asamm.osmTools.generator.GenLoMaps
import com.asamm.osmTools.generator.GenStoreRegionDB
import com.asamm.osmTools.generator.PlanetUpdater
import com.asamm.osmTools.utils.Logger
import com.asamm.store.LocusStoreEnv
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.clikt.parameters.types.file
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException


class OsmToolsCommand : CliktCommand(
    name = "OsmToolsBasic",
    help = "Tool for processing OSM data and generating maps"
) {

    // verbose mode
    val verbose by option("-v", "--verbose", help = "Prints more detailed information").flag()

    // verbose mode
    val overwrite by option("-ow", "--overwrite", help = "Overwrite output file if exists").flag()

    // set Locus Store environment (where to upload maps)
    val locusStoreEnv by option(
        "-e", "--ls_environment", help = "Set Locus Store environment - where to upload maps.  " +
                "Possible values: ${LocusStoreEnv.values().joinToString(", ")}"
    )
        .enum<LocusStoreEnv>()
        .default(LocusStoreEnv.PROD)


    override fun run() {

        // Load the app configuration (initialize it once)
        AppConfig.loadConfig()

        // Set verbose mode
        AppConfig.config.verbose = verbose

        // Set Locus Store environment
        AppConfig.config.locusStoreEnv = locusStoreEnv

        // Set overwrite mode
        AppConfig.config.overwrite = overwrite
    }

}

// UPDATE PLANET SUBCOMMAND

class UpdatePlanetCommand : CliktCommand(
    name = "update_planet",
    help = "Subcommand to update OSM planet file"
) {

    override fun run() {
        val planetUpdater: PlanetUpdater = PlanetUpdater()
        planetUpdater.update()
    }
}

// LOMAPS SUBCOMMAND

class LoMapsCommand : CliktCommand(
    name = "lomaps",
    help = "Subcommand to generate maps for Locus Store"
) {

    // version of the map
    val version: String by option(
        "-v",
        "--version",
        help = "Name of map version. This is used for versioning of map in Locus Store"
    ).default("")
        .validate { it ->
            require(validateDate(it)) {
                "Invalid date format. Use yyyy.MM.dd"
            }
        }

    // path to a configuration file where are defined maps for generation
    val configFile: File by option("-cf", "--config_file", help = "Path to configuration file").file(mustExist = true)
        .defaultLazy {
            val defaultConfigFile = File("config.xml")
            require(defaultConfigFile.exists()) {
                "Default config file '$defaultConfigFile' doesn't exist. Please specify path to config file"
            }
            defaultConfigFile
        }

    // split action by comma and convert to enum
    val actions by option(help = "Action to perform. Possible values: ${Action.getCliActions().map { it.getLabel() }.joinToString(", ")}")
        .convert { input ->
            input.split(",").map {
                var action = Action.getActionByLabel(it.trim().lowercase())
                // if action is UNKNOWN, end program and print warning
                require(action != Action.UNKNOWN) {
                    // print warning and possible actions but not the UNKNOWN
                    "Unknown action '$it'. Possible values: ${
                        Action.getCliActions().filter { it != Action.UNKNOWN }.map { it.getLabel() }.joinToString(", ")
                    }"
                }
                action
            }.toMutableList()
        }
        .default(mutableListOf())

    val hgtDir: File by option("-hgt", "--hgt_dir", help = "Path to elevation hgt file").file(mustExist = true)
        .defaultLazy {
            val defaultConfigFile = File("hgt")
            require(defaultConfigFile.exists()) {
                "Default folder with elevation data: '$defaultConfigFile' doesn't exist. Please specify path to config file"
            }
            defaultConfigFile
        }

    val dataDir: File by option("-d", "--data_dir", help = "Path to folder where data for generation are stored").file()
        .defaultLazy {
            AppConfig.config.dataDir.toFile() }

    // path to a locus store uploader
    val storeUploaderFile: File by option("-su", "--store_uploader", help = "Path to java .jar file script for LoMaps store uploader")
        .file(mustExist = true)
        .defaultLazy { AppConfig.config.storeUploaderPath.toFile() }

    override fun run() {


        // Set required actions based on the command line arguments
        ConfigUtils.addAdditionalActions(actions)

        // Set actions to the configuration
        AppConfig.config.actions = actions
        AppConfig.config.version = version
        AppConfig.config.dataDir = dataDir.toPath()

        // Set path to the configuration file
        AppConfig.config.mapsforgeConfig.mapConfigXml = configFile.toPath()

        // Set path to the store uploader
        AppConfig.config.storeUploaderPath = storeUploaderFile.toPath()

        // Set path to the hgt directory
        AppConfig.config.contourConfig.hgtDir = hgtDir.toPath()

        echo("Configuration : ${AppConfig.config.toString()}")
        echo(AppConfig.config.mapsforgeConfig.mapDescription)
        val genLoMaps = GenLoMaps();
        genLoMaps.process();


        echo("== Map generation finished ==")
    }

    /**
     * Validate date format
     */
    private fun validateDate(dateStr: String): Boolean {
        try {
            val formatter = DateTimeFormatter.ofPattern("yyyy.MM.dd")
            LocalDate.parse(dateStr, formatter)
            return true
        } catch (e: DateTimeParseException) {
            Logger.e("Command", "Invalid date format. Use yyyy.MM.dd")
            return false
        }
    }
}

// GENERATE COUNTRY BORDERS

class StoreGeoCommand : CliktCommand(
    name = "storegeo",
    help = "Subcommand to generate map borders for regions in Locus Store"
) {

    // path to a configuration file where are defined maps for generation
    val configFile: File by option("-cf", "--config_file", help = "Path to configuration XML for generation of country boundaries").file(mustExist = true)
        .defaultLazy {
            val defaultConfigFile = File("config/config_store_geodb.xml")
            require(defaultConfigFile.exists()) {
                "Default config file '$defaultConfigFile' doesn't exist. Please specify path to config file"
            }
            defaultConfigFile
        }


    override fun run() {
        val genStoreGeo = GenStoreRegionDB(configFile.toPath());
        genStoreGeo.process();
    }
}

fun main(args: Array<String>) {
    OsmToolsCommand()
        .subcommands(LoMapsCommand(), UpdatePlanetCommand(), StoreGeoCommand())
        .main(args)
}