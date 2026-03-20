package com.asamm.osmTools.cmdCommands

import org.apache.commons.io.FileUtils
import java.io.File
import java.nio.file.Path

/**
 * Builder for Osmosis pipeline commands.
 * Wraps [ProcessCommand.Builder] and exposes only Osmosis-specific operations,
 * so pipeline methods like [readPbf] or [writePbf] are unavailable on plain builders.
 */
class OsmosisBuilder(baseArgs: List<String>, private val workDir: File?) {

    private val inner = ProcessCommand.Builder(baseArgs)

    // ── Read ──────────────────────────────────────────────────────────────────

    fun readPbf(path: String) = apply { inner.add("--read-pbf", path) }

    fun readXml(path: String) = apply { inner.add("--read-xml", path) }

    fun readSource(path: Path): OsmosisBuilder = when {
        path.toString().endsWith(".pbf") -> readPbf(path.toString())
        path.toString().endsWith(".xml") -> readXml(path.toString())
        else -> throw IllegalArgumentException("Unsupported source file extension: '$path'")
    }

    // ── Write ─────────────────────────────────────────────────────────────────

    fun writePbf(path: String, omitMetadata: Boolean = false) = apply {
        FileUtils.forceMkdir(File(path).parentFile)
        inner.add("--write-pbf", path)
        if (omitMetadata) inner.add("omitmetadata=true")
    }

    fun writeXml(path: String) = apply {
        FileUtils.forceMkdir(File(path).parentFile)
        inner.add("--wx", path)
    }

    // ── Pipeline operations ───────────────────────────────────────────────────

    fun sort() = apply { inner.add("--sort") }

    fun buffer() = apply { inner.add("--buffer") }

    fun merge() = apply { inner.add("--merge") }

    fun tee(count: Int) = apply { inner.add("--tee", count.toString()) }

    fun completeWays() = apply { inner.add("completeWays=true") }

    fun completeRelations() = apply { inner.add("completeRelations=true") }

    fun cascadingRelations() = apply { inner.add("cascadingRelations=true") }

    // ── Raw escape hatch ──────────────────────────────────────────────────────

    fun add(vararg args: String) = apply { inner.add(*args) }

    fun addNotBlank(arg: String?) = apply { inner.addNotBlank(arg) }

    // ── Execute ───────────────────────────────────────────────────────────────

    fun build() = inner.build(workingDir = workDir)

    fun execute() = build().execute()
}