package com.asamm.osmTools.cmdCommands

import com.asamm.osmTools.config.AppConfig
import com.asamm.osmTools.utils.Logger
import java.nio.file.Path
import kotlin.io.path.absolutePathString

/**
 * Command for running Osmium tool.
 */
class CmdOsmium : Cmd(ExternalApp.OSMIUM) {

    enum class ExtractStrategy {

        /**
         * Runs in a single pass. The extract will contain all nodes inside the region and all ways
         * referencing those nodes as well as all relations referencing any nodes or ways already included.
         * Ways crossing the region boundary will not be reference-complete. Relations will not be reference-complete.
         */
        SIMPLE,

        /**
         * Runs in two passes. The extract will contain all nodes inside the region and all ways referencing those nodes
         * as well as all nodes referenced by those ways. The extract will also contain all relations referenced
         * by nodes inside the region or ways already included and, recursively, their parent relations.
         * The ways are reference-complete, but the relations are not.
         */
        COMPLETE_WAYS,

        /**
         * Runs in three passes. The extract will contain all nodes inside the region and all ways referencing those
         * nodes as well as all nodes referenced by those ways. The extract will also contain all relations referenced
         * by nodes inside the region or ways already included and, recursively, their parent relations.
         * The extract will also contain all nodes and ways (and the nodes they reference) referenced by
         * relations tagged “type=multipolygon” directly referencing any nodes in the region or ways referencing nodes
         * in the region. The ways are reference-complete, and all multipolygon relations referencing nodes in the
         * regions or ways that have nodes in the region are reference-complete.
         * Other relations are not reference-complete.
         */
        SMART;
    }

    private val TAG: String = CmdOsmium::class.java.getSimpleName()

    fun extractByPolygon(input: Path, output: Path, polygon: Path, strategy: ExtractStrategy = ExtractStrategy.COMPLETE_WAYS) {
        addCommands("extract", "--polygon", polygon.toString(), input.toString(), "-o", output.toString(),
            "--fsync", "--strategy", strategy.name.lowercase())
        if (AppConfig.config.overwrite){
            addCommand("--overwrite")
        }
        Logger.i(TAG, "Command: " + getCmdLine())

        execute()
        reset()
    }

    fun merge(inputPaths: MutableList<Path>, outputPath: Path) {
        addCommands("merge")
        inputPaths.forEach {addCommand(it.toString()) }
        addCommands("-o", outputPath.toString())
        if (AppConfig.config.overwrite){
            addCommand("--overwrite")
        }
        Logger.i(TAG, "Command: " + getCmdLine())
        execute()
        reset()
    }
}