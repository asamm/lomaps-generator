package com.asamm.osmTools

import com.asamm.osmTools.utils.Logger
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.defaultLazy
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.validate
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.clikt.parameters.types.file
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/** crate enum with string codes like COLOR.code */
enum class LoMapsAction() {
    DOWNLOAD,
    UPLOAD,
    TOURIST,
    CONTOURS,
    GENERATE_MAPLIBRE,
    GENERATE_MAPSFORGE,
}

enum class LocusStoreEnv() {
    DEV,
    PROD,
}

class OsmToolsCommand : CliktCommand(
    name = "OsmTools",
    help = "Tool for processing OSM data and generating maps"
) {

    // verbose mode
    val verbose by option("-v", "--verbose", help = "Prints more detailed information").flag()

    // set Locus Store environment (where to upload maps)
    val locusStoreEnv by option("-e", "--ls_environment", help = "Set Locus Store environment - where to upload maps.  " +
            "Possible values: ${LocusStoreEnv.values().joinToString(", ")}")
        .enum<LocusStoreEnv>()
        .default(LocusStoreEnv.PROD)


    override fun run() = Unit // do nothing

}

// LOMAPS SUBCOMMAND

class LoMapsCommand : CliktCommand(
    name = "lomaps",
    help = "Subcommand to generate maps for Locus Store") {

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
    val actions by option(help = "Action to perform. Possible values: ${LoMapsAction.values().joinToString(", ")}")
        .convert { input ->
            input.split(",").map {
                try {
                    LoMapsAction.valueOf(it.trim().uppercase())
                } catch (e: IllegalArgumentException) {
                    fail("Invalid action: $it. Allowed values are ${LoMapsAction.values().joinToString(", ")}.")
                }
            }
        }
        .default(emptyList())



    override fun run() {

        echo("OsmTools version: $version , path to config file ${configFile.absoluteFile}")
        echo("Actions to perform: ${actions}")
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

class StoreGeoCommand : CliktCommand(
    name = "storegeo",
    help = "Subcommand to generate map borders for regions in Locus Store") {

    override fun run() {
        TODO("Not yet implemented")
    }
}

fun main(args: Array<String>) {
    OsmToolsCommand()
        .subcommands(LoMapsCommand(), StoreGeoCommand())
        .main(args)
}