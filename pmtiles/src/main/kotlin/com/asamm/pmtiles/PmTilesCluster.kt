package com.asamm.pmtiles

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream
import java.nio.channels.Channels
import java.nio.file.Path
import java.util.zip.GZIPOutputStream

/**
 * Re-clusters an existing PMTiles archive so that tile data is stored in
 * Hilbert-curve (tile ID) order, producing `clustered = true` in the output header.
 *
 * This is the Java/Kotlin equivalent of the Go `pmtiles cluster` subcommand.
 *
 * Algorithm:
 * 1. Read the source archive header and verify it is not already clustered.
 * 2. Iterate all directory entries in tile-ID order (they are stored sorted).
 * 3. For each entry, read the tile data from the source, optionally deduplicate
 *    via FNV-1a hash, and write to a temp file in tile-ID order.
 * 4. Build new directory + header and write the final output file.
 *
 * The output file **replaces** the input (same as Go `pmtiles cluster`).
 * If a separate output path is desired, use [cluster(input, output, ...)].
 *
 * Usage:
 * ```kotlin
 * PmTilesCluster.cluster(Path.of("planet.pmtiles"))
 * ```
 */
object PmTilesCluster {

    private const val WRITE_BUFFER = 4 * 1024 * 1024
    private const val GZIP_BUFFER = 8192

    /**
     * Progress callback: [processedEntries] / [totalEntries].
     */
    fun interface ProgressListener {
        fun onProgress(processedEntries: Long, totalEntries: Long)
    }

    data class ClusterResult(
        val addressedTiles: Long,
        val tileEntries: Long,
        val tileContents: Long,
        val outputFileSize: Long,
    )

    /**
     * Clusters a PMTiles archive in-place (overwrites the input file).
     *
     * @param input       Path to the PMTiles file to cluster.
     * @param deduplicate If true, identical tile content is stored only once (default: true).
     * @param listener    Optional progress callback.
     * @return Clustering summary, or null if the archive is already clustered.
     */
    fun cluster(
        input: Path,
        deduplicate: Boolean = true,
        listener: ProgressListener? = null,
    ): ClusterResult? {
        val tmpOutput = input.resolveSibling("${input.fileName}.cluster_tmp")
        try {
            val result = cluster(input, tmpOutput, deduplicate, listener)
                ?: return null

            // Replace original with clustered version
            val inputFile = input.toFile()
            inputFile.delete()
            tmpOutput.toFile().renameTo(inputFile)

            return result
        } catch (e: Exception) {
            tmpOutput.toFile().delete()
            throw e
        }
    }

    /**
     * Clusters a PMTiles archive, writing the result to a separate output file.
     *
     * @param input       Path to the source PMTiles file.
     * @param output      Path to write the clustered PMTiles file.
     * @param deduplicate If true, identical tile content is stored only once (default: true).
     * @param listener    Optional progress callback.
     * @return Clustering summary, or null if the archive is already clustered.
     */
    fun cluster(
        input: Path,
        output: Path,
        deduplicate: Boolean = true,
        listener: ProgressListener? = null,
    ): ClusterResult? {
        FileChannelPmTilesReader(input).use { reader ->
            val header = reader.header

            if (header.clustered) {
                return null // already clustered
            }

            val totalEntries = header.tileEntriesCount

            // Temp file for re-ordered tile data
            val tileTemp = File.createTempFile("pmtiles_cluster_", ".tmp")
            tileTemp.deleteOnExit()

            try {
                // ── Pass 1: Read entries in tileId order, write tile data sequentially ──

                val entries = ArrayList<Entry>()
                val hashToOffset = if (deduplicate) HashMap<Long, OffsetLen>() else null
                var tileDataSize = 0L
                var addressedTiles = 0L
                var processedEntries = 0L

                BufferedOutputStream(FileOutputStream(tileTemp), WRITE_BUFFER).use { tempOut ->
                    for (entry in reader.allEntries()) {
                        // Read tile data from source
                        val data = reader.source(
                            header.tileDataOffset + entry.offset,
                            entry.length
                        )

                        addressedTiles += entry.runLength

                        if (deduplicate && hashToOffset != null) {
                            val hash = fnv1a64(data)
                            val existing = hashToOffset[hash]

                            if (existing != null) {
                                // Deduplicate: check if we can extend the last run
                                if (entries.isNotEmpty()) {
                                    val last = entries.last()
                                    if (entry.tileId == last.tileId + last.runLength
                                        && last.offset == existing.offset
                                    ) {
                                        // Extend RLE
                                        entries[entries.lastIndex] = Entry(
                                            last.tileId, last.offset, last.length,
                                            last.runLength + entry.runLength
                                        )
                                    } else {
                                        entries.add(Entry(entry.tileId, existing.offset, existing.length, entry.runLength))
                                    }
                                } else {
                                    entries.add(Entry(entry.tileId, existing.offset, existing.length, entry.runLength))
                                }
                            } else {
                                // New unique tile content
                                tempOut.write(data)
                                val newEntry = Entry(entry.tileId, tileDataSize, data.size, entry.runLength)
                                entries.add(newEntry)
                                hashToOffset[hash] = OffsetLen(tileDataSize, data.size)
                                tileDataSize += data.size
                            }
                        } else {
                            // No dedup — just write sequentially
                            tempOut.write(data)
                            entries.add(Entry(entry.tileId, tileDataSize, data.size, entry.runLength))
                            tileDataSize += data.size
                        }

                        processedEntries++
                        if (processedEntries % 10_000 == 0L) {
                            listener?.onProgress(processedEntries, totalEntries)
                        }
                    }
                }

                listener?.onProgress(processedEntries, totalEntries)

                val uniqueContents = hashToOffset?.size?.toLong() ?: processedEntries

                // ── Pass 2: Read metadata from source ──

                val metadataRaw = reader.source(header.metadataOffset, header.metadataLength.toInt())

                // ── Pass 3: Build new archive ──

                val (rootBytes, leavesBytes) = optimizeDirectories(entries)

                val rootOffset = HEADER_SIZE.toLong()
                val metaOffset = rootOffset + rootBytes.size
                val leavesOffset = metaOffset + metadataRaw.size
                val dataOffset = leavesOffset + leavesBytes.size

                val minZoom = if (entries.isEmpty()) 0 else tileIdToZxy(entries.first().tileId).first
                val maxZoom = if (entries.isEmpty()) 0 else tileIdToZxy(entries.last().tileId).first

                val newHeader = header.copy(
                    clustered = true,
                    minZoom = minZoom,
                    maxZoom = maxZoom,
                    addressedTilesCount = addressedTiles,
                    tileEntriesCount = entries.size.toLong(),
                    tileContentsCount = uniqueContents,
                    rootOffset = rootOffset,
                    rootLength = rootBytes.size.toLong(),
                    metadataOffset = metaOffset,
                    metadataLength = metadataRaw.size.toLong(),
                    leafDirectoryOffset = leavesOffset,
                    leafDirectoryLength = leavesBytes.size.toLong(),
                    tileDataOffset = dataOffset,
                    tileDataLength = tileDataSize,
                )

                BufferedOutputStream(FileOutputStream(output.toFile()), WRITE_BUFFER).use { out ->
                    out.write(serializeHeader(newHeader))
                    out.write(rootBytes)
                    out.write(metadataRaw)
                    out.write(leavesBytes)
                    out.flush()

                    // Copy tile data from temp file
                    FileInputStream(tileTemp).channel.use { src ->
                        val ch = Channels.newChannel(out)
                        var transferred = 0L
                        while (transferred < tileDataSize) {
                            transferred += src.transferTo(transferred, tileDataSize - transferred, ch)
                        }
                    }
                }

                return ClusterResult(
                    addressedTiles = addressedTiles,
                    tileEntries = entries.size.toLong(),
                    tileContents = uniqueContents,
                    outputFileSize = output.toFile().length(),
                )

            } finally {
                tileTemp.delete()
            }
        }
    }

    // ── Internal helpers ─────────────────────────────────────────────────────

    private data class OffsetLen(val offset: Long, val length: Int)

    private fun fnv1a64(data: ByteArray): Long {
        var h = -3750763034362895579L   // FNV-1a 64-bit offset basis
        for (b in data) {
            h = h xor (b.toLong() and 0xFF)
            h *= 1099511628211L         // FNV-1a 64-bit prime
        }
        return h
    }

    private const val TARGET_ROOT_BYTES = 16384 - HEADER_SIZE

    private fun optimizeDirectories(entries: List<Entry>): Pair<ByteArray, ByteArray> {
        if (entries.isEmpty()) return Pair(serializeDirectory(emptyList()), ByteArray(0))

        if (entries.size <= 8192) {
            val flat = serializeDirectory(entries)
            if (flat.size <= TARGET_ROOT_BYTES) return Pair(flat, ByteArray(0))
        }

        var leafSize = maxOf(4096, entries.size / (TARGET_ROOT_BYTES / 8))
        while (true) {
            val (root, leaves) = buildRootsLeaves(entries, leafSize)
            if (root.size <= TARGET_ROOT_BYTES) return Pair(root, leaves)
            leafSize *= 2
        }
    }

    private fun buildRootsLeaves(entries: List<Entry>, leafSize: Int): Pair<ByteArray, ByteArray> {
        val rootEntries = ArrayList<Entry>(entries.size / leafSize + 1)
        val leavesOut = java.io.ByteArrayOutputStream(entries.size / leafSize * 2048)

        var i = 0
        while (i < entries.size) {
            val slice = entries.subList(i, minOf(i + leafSize, entries.size))
            val serialized = serializeDirectory(slice)
            rootEntries.add(Entry(slice[0].tileId, leavesOut.size().toLong(), serialized.size, 0))
            leavesOut.write(serialized)
            i += leafSize
        }

        return Pair(serializeDirectory(rootEntries), leavesOut.toByteArray())
    }
}

