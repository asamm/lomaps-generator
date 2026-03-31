package com.asamm.pmtiles

import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream
import java.nio.channels.Channels
import java.nio.file.Path
import java.util.zip.GZIPOutputStream

/**
 * Writes a PMTiles v3 archive to [output].
 *
 * Tile data is buffered in a temporary file and flushed to [output] on [finalize].
 * Identical tiles (same byte content) are automatically deduplicated — only one copy
 * is stored and shared via run-length entries.
 *
 * Tiles should be written in ascending Hilbert-curve order ([zxyToTileId]) for best
 * clustering. Out-of-order writes are supported but result in `clustered = false`.
 *
 * Performance characteristics for TB-scale archives:
 * - Tile temp file writes are buffered with a 4 MB write-back buffer.
 * - Deduplication uses a 64-bit FNV-1a hash — collision probability is negligible
 *   even for billions of tiles.
 * - Final tile-data copy uses [java.nio.channels.FileChannel.transferTo] for
 *   kernel-level zero-copy when [output] is a [FileOutputStream].
 *
 * Usage:
 * ```kotlin
 * PmTilesWriter(outputPath).use { writer ->
 *     writer.writeTile(zxyToTileId(z, x, y), tileBytes)
 *     // …
 *     writer.finalize(
 *         PmTilesHeader(tileType = TileType.MVT, tileCompression = Compression.GZIP),
 *         """{"name":"My Tileset"}"""
 *     )
 * }
 * ```
 *
 * @param output  Destination stream. Ownership is transferred; [close] will close it.
 */
class PmTilesWriter(private val output: OutputStream) : AutoCloseable {

    /** Convenience constructor — wraps the file in a large [BufferedOutputStream]. */
    constructor(file: File) : this(BufferedOutputStream(FileOutputStream(file), WRITE_BUFFER))
    constructor(path: Path) : this(path.toFile())

    // Internal mutable entry — converted to immutable Entry in finalize()
    private class MEntry(val tileId: Long, var offset: Long, var length: Int, var runLength: Int)

    private val tileEntries   = ArrayList<MEntry>(INITIAL_ENTRY_CAPACITY)
    private val hashToOffset  = HashMap<Long, Long>(INITIAL_ENTRY_CAPACITY * 4 / 3 + 1)   // pre-sized to avoid rehash
    private val tileTemp      = File.createTempFile("pmtiles_", ".tmp").also { it.deleteOnExit() }
    private val tileTempOut   = BufferedOutputStream(FileOutputStream(tileTemp), WRITE_BUFFER)
    private var tileDataSize  = 0L
    private var addressedTiles = 0L
    private var clustered      = true

    /**
     * Appends a tile to the archive.
     *
     * Identical tile content (same bytes) is deduplicated: the second occurrence
     * references the first occurrence's offset rather than writing a second copy.
     *
     * @param tileId  Hilbert-curve tile ID (use [zxyToTileId] to compute from z/x/y).
     * @param data    Raw tile bytes (apply tile compression before passing here).
     */
    fun writeTile(tileId: Long, data: ByteArray) {
        if (tileEntries.isNotEmpty() && tileId < tileEntries.last().tileId) {
            clustered = false
        }

        val hash     = fnv1a64(data)
        val existing = hashToOffset[hash]

        if (existing != null) {
            val last = tileEntries.last()
            if (tileId == last.tileId + last.runLength && last.offset == existing) {
                last.runLength++          // extend current run
            } else {
                tileEntries.add(MEntry(tileId, existing, data.size, 1))
            }
        } else {
            tileTempOut.write(data)
            tileEntries.add(MEntry(tileId, tileDataSize, data.size, 1))
            hashToOffset[hash] = tileDataSize
            tileDataSize += data.size
        }

        addressedTiles++
    }

    /**
     * Finalizes the archive.
     *
     * Sorts entries, builds the directory structure, compresses metadata, and writes
     * everything to [output] in the canonical PMTiles v3 layout:
     * ```
     * [127-byte header][root directory][compressed metadata][leaf directories][tile data]
     * ```
     *
     * Must be called exactly once after all tiles have been added.
     *
     * @param baseHeader   Header template. Counts, offsets, zooms, and clustering flag
     *                     are computed automatically and override the template values.
     * @param metadataJson Raw JSON string to embed as archive metadata (GZIP-compressed).
     */
    fun finalize(baseHeader: PmTilesHeader, metadataJson: String) {
        tileTempOut.close()

        // Sort in-place to avoid allocating a second list for 100M+ entries
        tileEntries.sortBy { it.tileId }
        val sorted = ArrayList<Entry>(tileEntries.size)
        for (e in tileEntries) sorted.add(Entry(e.tileId, e.offset, e.length, e.runLength))
        val uniqueTileContents = hashToOffset.size.toLong()

        // Release MEntry list and hash map — no longer needed, frees memory for directory building
        tileEntries.clear(); tileEntries.trimToSize()
        hashToOffset.clear()

        val minZoom = if (sorted.isEmpty()) 0 else tileIdToZxy(sorted.first().tileId).first
        val maxZoom = if (sorted.isEmpty()) 0 else tileIdToZxy(sorted.last().tileId).first

        val (rootBytes, leavesBytes) = optimizeDirectories(sorted)

        val compressedMeta = ByteArrayOutputStream().also { buf ->
            GZIPOutputStream(buf, GZIP_BUFFER).use { gz ->
                gz.write(metadataJson.toByteArray(Charsets.UTF_8))
            }
        }.toByteArray()

        val rootOffset   = HEADER_SIZE.toLong()
        val metaOffset   = rootOffset + rootBytes.size
        val leavesOffset = metaOffset + compressedMeta.size
        val dataOffset   = leavesOffset + leavesBytes.size

        val header = baseHeader.copy(
            clustered           = clustered,
            internalCompression = Compression.GZIP,
            minZoom             = minZoom,
            maxZoom             = maxZoom,
            addressedTilesCount = addressedTiles,
            tileEntriesCount    = sorted.size.toLong(),
            tileContentsCount   = uniqueTileContents,
            rootOffset          = rootOffset,
            rootLength          = rootBytes.size.toLong(),
            metadataOffset      = metaOffset,
            metadataLength      = compressedMeta.size.toLong(),
            leafDirectoryOffset = leavesOffset,
            leafDirectoryLength = leavesBytes.size.toLong(),
            tileDataOffset      = dataOffset,
            tileDataLength      = tileDataSize,
        )

        output.write(serializeHeader(header))
        output.write(rootBytes)
        output.write(compressedMeta)
        output.write(leavesBytes)
        output.flush()

        // Zero-copy tile data transfer via FileChannel.transferTo when possible
        FileInputStream(tileTemp).channel.use { src ->
            val dst = when (output) {
                is FileOutputStream        -> output.channel
                is BufferedOutputStream    -> tryUnwrapToFileChannel(output)
                else                       -> null
            }
            if (dst != null) {
                var transferred = 0L
                while (transferred < tileDataSize) {
                    transferred += src.transferTo(transferred, tileDataSize - transferred, dst)
                }
            } else {
                // Fallback: loop because transferTo may transfer fewer bytes than requested
                val ch = Channels.newChannel(output)
                var transferred = 0L
                while (transferred < tileDataSize) {
                    transferred += src.transferTo(transferred, tileDataSize - transferred, ch)
                }
            }
        }
    }

    override fun close() {
        runCatching { tileTempOut.close() }
        runCatching { output.close() }
        tileTemp.delete()
    }

    // ─── Directory optimization ───────────────────────────────────────────────

    private companion object {
        /** Target root-directory size: fill the first 16 KiB page minus the header. */
        const val TARGET_ROOT_BYTES    = 16384 - HEADER_SIZE
        const val WRITE_BUFFER         = 4 * 1024 * 1024   // 4 MB I/O buffer
        const val INITIAL_ENTRY_CAPACITY = 1024 * 1024     // pre-size for ~1M tiles
        const val GZIP_BUFFER          = 8192

        fun tryUnwrapToFileChannel(bos: BufferedOutputStream): java.nio.channels.WritableByteChannel? = try {
            val f = bos.javaClass.getDeclaredField("out").also { it.isAccessible = true }.get(bos)
            if (f is FileOutputStream) f.channel else null
        } catch (_: Exception) { null }
    }

    /**
     * 64-bit FNV-1a hash of [data].
     *
     * Collision probability for N tiles ≈ N² / 2^64.
     * For 10^9 tiles: ~0.003% — negligible vs the 32-bit alternative (~12.5%).
     */
    private fun fnv1a64(data: ByteArray): Long {
        var h = -3750763034362895579L   // FNV-1a 64-bit offset basis
        for (b in data) {
            h = h xor (b.toLong() and 0xFF)
            h *= 1099511628211L         // FNV-1a 64-bit prime
        }
        return h
    }

    /**
     * Returns `(rootBytes, leavesBytes)`.
     *
     * If all entries fit in [TARGET_ROOT_BYTES], leaves are empty.
     * Otherwise splits into leaf directories, doubling leaf size until root fits.
     */
    private fun optimizeDirectories(entries: List<Entry>): Pair<ByteArray, ByteArray> {
        if (entries.isEmpty()) return Pair(serializeDirectory(emptyList()), ByteArray(0))

        // Skip flat serialization for large entry sets — it will never fit in TARGET_ROOT_BYTES
        // and would waste time gzip-compressing millions of entries into one huge buffer.
        if (entries.size <= 8192) {
            val flat = serializeDirectory(entries)
            if (flat.size <= TARGET_ROOT_BYTES) return Pair(flat, ByteArray(0))
        }

        // Start leaf size proportional to entry count so the root has ~TARGET_ROOT_BYTES/~8 entries,
        // avoiding many doubling iterations for large archives.
        var leafSize = maxOf(4096, entries.size / (TARGET_ROOT_BYTES / 8))
        while (true) {
            val (root, leaves) = buildRootsLeaves(entries, leafSize)
            if (root.size <= TARGET_ROOT_BYTES) return Pair(root, leaves)
            leafSize *= 2
        }
    }

    /**
     * Splits [entries] into leaf directories of [leafSize] each, serializes them,
     * and builds a root directory pointing to each leaf.
     */
    private fun buildRootsLeaves(entries: List<Entry>, leafSize: Int): Pair<ByteArray, ByteArray> {
        val rootEntries = ArrayList<Entry>(entries.size / leafSize + 1)
        val leavesOut   = ByteArrayOutputStream(entries.size / leafSize * 2048)

        var i = 0
        while (i < entries.size) {
            val slice      = entries.subList(i, minOf(i + leafSize, entries.size))
            val serialized = serializeDirectory(slice)
            rootEntries.add(Entry(slice[0].tileId, leavesOut.size().toLong(), serialized.size, 0))
            leavesOut.write(serialized)
            i += leafSize
        }

        return Pair(serializeDirectory(rootEntries), leavesOut.toByteArray())
    }
}