package com.asamm.osmTools.cmdCommands

import com.asamm.osmTools.config.AppConfig
import org.apache.commons.io.FileUtils
import java.nio.file.Path
import java.time.Instant
import java.util.Date

/**
 * Command wrapper for the Osmium tool.
 * Each public method builds and immediately executes its own [ProcessCommand].
 */
class CmdOsmium : Cmd(ExternalApp.OSMIUM) {

    enum class ExtractStrategy {
        /**
         * Single pass. Contains all nodes inside the region and all ways referencing those nodes,
         * plus all relations referencing any included nodes or ways.
         * Ways crossing the boundary are not reference-complete.
         */
        SIMPLE,

        /**
         * Two passes. Ways are reference-complete; relations are not.
         */
        COMPLETE_WAYS,

        /**
         * Three passes. Ways and multipolygon relations referencing nodes in the region
         * are reference-complete.
         */
        SMART
    }

    fun extractByPolygon(
        input: Path,
        output: Path,
        polygon: Path,
        strategy: ExtractStrategy = ExtractStrategy.COMPLETE_WAYS
    ) {
        builder()
            .add("extract", "--polygon", polygon.toString(), input.toString(),
                "-o", output.toString(), "--fsync", "--strategy", strategy.name.lowercase())
            .addIf(AppConfig.config.overwrite, "--overwrite")
            .execute()
    }

    fun merge(inputPaths: List<Path>, outputPath: Path) {
        FileUtils.forceMkdir(outputPath.parent.toFile())
        builder()
            .add("merge")
            .apply {
                inputPaths
                    .filter { it.toFile().length() > 128 }
                    .forEach { add(it.toString()) }
            }
            .add("-o", outputPath.toString())
            .addIf(AppConfig.config.overwrite, "--overwrite")
            .execute()
    }

    /** Returns true if the file contains any OSM data (non-empty). */
    fun containsData(input: Path): Boolean {
        val lastLine = builder()
            .add("fileinfo", input.toString())
            .executeQuietly()
        return lastLine?.trim()?.startsWith("osmfile") ?: false
    }

    /**
     * Returns true if the OSM file contains an entity with [id].
     * The id format is: 'n' for nodes, 'w' for ways, 'r' for relations
     * e.g. "n13", "w22", "r21".
     */
    fun containsId(input: Path, id: String): Boolean {
        val lastLine = builder()
            .add("getid", "-f", "opl", input.toString(), id)
            .executeQuietly()
        return lastLine?.trim()?.startsWith(id) ?: false
    }

    fun renumber(
        input: Path,
        output: Path,
        nodeStartId: Long = 0,
        wayStartId: Long = 0,
        relationStartId: Long = 0
    ) {
        val startId = "$nodeStartId,$wayStartId,$relationStartId"
        builder()
            .add("renumber", input.toString(), "-o", output.toString())
            .add("--start-id=$startId")
            .addIf(nodeStartId != 0L, "--object-type=node")
            .addIf(wayStartId != 0L, "--object-type=way")
            .addIf(relationStartId != 0L, "--object-type=relation")
            .addIf(AppConfig.config.overwrite, "--overwrite")
            .execute()
    }

    /**
     * Filter the OSM file by tags.
     * @param filters List of filter expressions, e.g. "nw/highway", "r/type=restriction".
     *   See https://docs.osmcode.org/osmium/latest/osmium-tags-filter.html
     */
    fun tagFilter(input: Path, output: Path, filters: List<String>) {
        builder()
            .add("tags-filter", input.toString(), "-o", output.toString())
            .apply { filters.forEach { add(it) } }
            .addIf(AppConfig.config.overwrite, "--overwrite")
            .execute()
    }

    /**
     * Returns the timestamp from the OSM file header,
     * falling back to the file's last-modified time if no header timestamp is present.
     */
    fun getTimeStamp(path: Path): Instant {
        val lines = builder()
            .add("fileinfo", path.toString())
            .build()
            .executeCapture()

        val timestampStr = lines
            .find { it.contains("timestamp=") || it.contains("osmosis_replication_timestamp=") }
            ?.substringAfter("=")

        return if (timestampStr == null) {
            Date(path.toFile().lastModified()).toInstant()
        } else {
            Instant.parse(timestampStr)
        }
    }
}