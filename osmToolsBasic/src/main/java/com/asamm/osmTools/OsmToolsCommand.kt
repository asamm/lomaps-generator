package com.asamm.osmTools.utils.cli

import com.asamm.osmTools.utils.Logger
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.validate
import com.github.ajalt.clikt.parameters.types.file
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

class OsmToolsCommand : CliktCommand(name = "OsmTools") {


    val version: String by argument(help = "Name of map version. This is used for versioning of map in Locus Store")
        .validate { it ->
            require(validateDate(it)) {
                "Invalid date format. Use yyyy.MM.dd"
            }
        }

    val configFile: File by argument(help = "Path to configuration file").file(mustExist = true)


    override fun run() {

        echo("OsmTools version: $version , path to config file ${configFile.absolutePath}" )
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