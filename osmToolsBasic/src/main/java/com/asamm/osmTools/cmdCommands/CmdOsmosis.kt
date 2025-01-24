package com.asamm.osmTools.cmdCommands

import java.nio.file.Path

interface CmdOsmosis {

    // READ OSM FILES
    fun addReadSource(readPath: Path) {
        //val source = getMap().getPathSource()
        when {
            readPath.toString().endsWith(".pbf") -> addReadPbf(readPath.toString())
            readPath.toString().endsWith(".xml") -> addReadXml(readPath.toString())
            else -> throw IllegalArgumentException("Invalid source file: '$readPath'")
        }
    }

    fun addReadPbf(readPath: String?) {
        (this as Cmd).addCommand("--read-pbf")
        (this as Cmd).addCommand(readPath)
    }

    fun addReadXml(readPath: String) {
        (this as Cmd).addCommands("--read-xml", readPath)
    }

    // WRITE OSM FILES
    fun addWritePbf(pathToWrite: String, omitMetadata: Boolean) {
        // create output directory
        (this as Cmd).prepareDirectory(pathToWrite)

        // add required commands
        (this as Cmd).addCommands("--write-pbf", pathToWrite)
        if (omitMetadata) {
            addCommand("omitmetadata=true")
        }
    }

    fun addWriteXml(pathToWrite: String) {

        (this as Cmd).prepareDirectory(pathToWrite)

        // add required commands
        (this as Cmd).addCommands("--wx", pathToWrite)
    }

    // ADD SPECIAL OSMOSIS COMMANDS
    fun addSort() {
        (this as Cmd).addCommand("--sort")
    }

    fun addBuffer() {
        (this as Cmd).addCommand("--buffer")
    }

    fun addCompleteWays() {
        (this as Cmd).addCommand("completeWays=true")
    }

    fun addCompleteRelations() {
        (this as Cmd).addCommand("completeRelations=true")
    }

    fun addCascadingRelations() {
        (this as Cmd).addCommand("cascadingRelations=true")
    }

    fun addTee(mapCount: Int) {
        (this as Cmd).addCommands("--tee", mapCount.toString())
    }

}

