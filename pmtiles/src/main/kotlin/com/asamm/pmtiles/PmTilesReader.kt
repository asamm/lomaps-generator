package com.asamm.pmtiles

import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.zip.GZIPInputStream

/**
 * Reads tiles and metadata from a PMTiles v3 archive.
 *
 * The [source] function provides random-access byte reads:
 * `source(offset, length)` returns exactly [length] bytes starting at [offset].
 *
 * The root directory is parsed once and cached; subsequent [getTileById] calls
 * perform only leaf-directory reads on cache misses.
 *
 * Use [FileChannelPmTilesReader] for file-backed access (thread-safe, no seek lock)
 * or [MmapPmTilesReader] for maximum throughput on large files (OS-managed paging).
 *
 * Example (file):
 * ```kotlin
 * MmapPmTilesReader(path).use { reader ->
 *     println(reader.metadata())
 *     reader.allTiles().forEach { (z, x, y, data) -> … }
 * }
 * ```
 */
class PmTilesReader(val source: (offset: Long, length: Int) -> ByteArray) {

    /** Parsed header — cached after first read. */
    val header: PmTilesHeader by lazy { deserializeHeader(source(0, HEADER_SIZE)) }

    /**
     * Root directory — cached after first access.
     * Eliminates redundant gzip+parse overhead on every [getTileById] call.
     */
    private val rootDirectory: List<Entry> by lazy {
        deserializeDirectory(source(header.rootOffset, header.rootLength.toInt()))
    }

    /**
     * Returns the metadata JSON string embedded in the archive.
     * Decompresses if [PmTilesHeader.internalCompression] is GZIP.
     */
    fun metadata(): String {
        val h = header
        var raw = source(h.metadataOffset, h.metadataLength.toInt())
        if (h.internalCompression == Compression.GZIP) {
            raw = GZIPInputStream(ByteArrayInputStream(raw)).use { it.readBytes() }
        }
        return raw.toString(Charsets.UTF_8)
    }

    /**
     * Returns the raw (possibly tile-compressed) data for tile (z, x, y),
     * or `null` if the tile is absent.
     */
    fun getTile(z: Int, x: Int, y: Int): ByteArray? = getTileById(zxyToTileId(z, x, y))

    /** Long-coordinate overload. */
    fun getTile(z: Int, x: Long, y: Long): ByteArray? = getTileById(zxyToTileId(z, x, y))

    /**
     * Returns the raw tile data for [tileId], or `null` if absent.
     *
     * The root directory is cached; only leaf directories are fetched on demand.
     * Supports up to 4 directory levels (root + 3 leaf levels).
     */
    fun getTileById(tileId: Long): ByteArray? {
        val h = header

        // Search cached root first
        val rootResult = findTile(rootDirectory, tileId) ?: return null
        if (rootResult.runLength > 0) {
            return source(h.tileDataOffset + rootResult.offset, rootResult.length)
        }

        // Follow leaf chain (up to 3 more levels)
        var dirOffset = h.leafDirectoryOffset + rootResult.offset
        var dirLength = rootResult.length
        repeat(3) {
            val dir = deserializeDirectory(source(dirOffset, dirLength))
            val result = findTile(dir, tileId) ?: return null
            if (result.runLength == 0) {
                dirOffset = h.leafDirectoryOffset + result.offset
                dirLength = result.length
            } else {
                return source(h.tileDataOffset + result.offset, result.length)
            }
        }
        return null
    }

    /**
     * Lazily yields every tile in the archive as [TileData] in Hilbert-curve order.
     *
     * Uses an iterative queue rather than recursive coroutines — avoids nested
     * sequence/coroutine overhead for files with many leaf directories.
     */
    fun allTiles(): Sequence<TileData> = sequence {
        val h = header
        // Queue of (dirOffset, dirLength) pairs — BFS preserves tile ID order
        val queue = ArrayDeque<Pair<Long, Int>>()
        queue.addLast(h.rootOffset to h.rootLength.toInt())

        while (queue.isNotEmpty()) {
            val (dirOffset, dirLength) = queue.removeFirst()
            for (entry in deserializeDirectory(source(dirOffset, dirLength))) {
                if (entry.runLength > 0) {
                    val data = source(h.tileDataOffset + entry.offset, entry.length)
                    for (i in 0 until entry.runLength) {
                        val (z, x, y) = tileIdToZxy(entry.tileId + i)
                        yield(TileData(z, x, y, data))
                    }
                } else {
                    queue.addLast((h.leafDirectoryOffset + entry.offset) to entry.length)
                }
            }
        }
    }

    companion object {
        /**
         * Creates a [FileChannelPmTilesReader] backed by a NIO [FileChannel].
         * Thread-safe without locking; works for files of any size.
         */
        fun fromFile(path: Path): FileChannelPmTilesReader = FileChannelPmTilesReader(path)

        /**
         * Creates a [MmapPmTilesReader] using memory-mapped I/O.
         * Best throughput for large files — the OS manages page caching.
         */
        fun fromFileMmap(path: Path): MmapPmTilesReader = MmapPmTilesReader(path)

        /** Creates a [PmTilesReader] over a fully in-memory [ByteArray]. */
        fun fromByteArray(data: ByteArray): PmTilesReader {
            val buf = ByteBuffer.wrap(data).asReadOnlyBuffer()
            return PmTilesReader { offset, length ->
                val slice = buf.duplicate().apply {
                    position(offset.toInt())
                    limit(offset.toInt() + length)
                }
                ByteArray(length).also { slice.get(it) }
            }
        }
    }
}

// ─── TileData ─────────────────────────────────────────────────────────────────

/** Tile coordinates and raw data as returned by [PmTilesReader.allTiles]. */
data class TileData(val z: Int, val x: Long, val y: Long, val data: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TileData) return false
        return z == other.z && x == other.x && y == other.y && data.contentEquals(other.data)
    }
    override fun hashCode(): Int {
        var r = z
        r = 31 * r + x.hashCode()
        r = 31 * r + y.hashCode()
        r = 31 * r + data.contentHashCode()
        return r
    }
}

// ─── FileChannelPmTilesReader ─────────────────────────────────────────────────

/**
 * A [PmTilesReader] backed by a NIO [FileChannel].
 *
 * Uses positional `channel.read(buffer, position)` which is inherently thread-safe
 * and requires no synchronization — multiple threads can read concurrently.
 * Handles files larger than 2 GB without limitation.
 */
class FileChannelPmTilesReader(path: Path) : AutoCloseable {

    private val channel = FileChannel.open(path, StandardOpenOption.READ)

    private val reader = PmTilesReader { offset, length ->
        val buf = ByteBuffer.allocate(length)
        var pos = offset
        while (buf.hasRemaining()) {
            val n = channel.read(buf, pos)
            if (n < 0) break
            pos += n
        }
        buf.array()
    }

    val header: PmTilesHeader get() = reader.header
    fun metadata(): String = reader.metadata()
    fun getTile(z: Int, x: Int, y: Int): ByteArray? = reader.getTile(z, x, y)
    fun getTile(z: Int, x: Long, y: Long): ByteArray? = reader.getTile(z, x, y)
    fun getTileById(tileId: Long): ByteArray? = reader.getTileById(tileId)
    fun allTiles(): Sequence<TileData> = reader.allTiles()

    override fun close() = channel.close()
}

// ─── MmapPmTilesReader ───────────────────────────────────────────────────────

/**
 * A [PmTilesReader] using memory-mapped I/O via [MappedByteBuffer].
 *
 * The file is mapped in [SEGMENT_BYTES]-sized segments (default 512 MB) to stay
 * within [MappedByteBuffer]'s Int-length limit. Reads crossing segment boundaries
 * are handled transparently.
 *
 * Memory mapping delegates page management to the OS: frequently-accessed regions
 * (root directory, hot tile data) stay resident; cold regions are paged out
 * automatically. This gives the best throughput for large random-access workloads.
 *
 * Thread safety: each read creates a `duplicate()` of the relevant segment,
 * giving an independent position/limit view without copying data.
 */
class MmapPmTilesReader(path: Path) : AutoCloseable {

    companion object {
        /** Size of each memory-mapped segment (512 MB — safe below the 2 GB MappedByteBuffer limit). */
        const val SEGMENT_BYTES = 512L * 1024L * 1024L
    }

    private val channel  = FileChannel.open(path, StandardOpenOption.READ)
    private val fileSize = channel.size()

    private val segments: List<MappedByteBuffer> = buildList {
        var offset = 0L
        while (offset < fileSize) {
            val size = minOf(SEGMENT_BYTES, fileSize - offset)
            add(channel.map(FileChannel.MapMode.READ_ONLY, offset, size))
            offset += size
        }
    }

    private val reader = PmTilesReader { offset, length ->
        val result = ByteArray(length)
        var remaining = length
        var srcOff = offset
        var dstOff = 0
        while (remaining > 0) {
            val segIdx  = (srcOff / SEGMENT_BYTES).toInt()
            val segOff  = (srcOff % SEGMENT_BYTES).toInt()
            val seg     = segments[segIdx].duplicate()   // independent position — thread-safe
            seg.position(segOff)
            val chunk   = minOf(remaining, seg.remaining())
            seg.get(result, dstOff, chunk)
            remaining -= chunk
            srcOff    += chunk
            dstOff    += chunk
        }
        result
    }

    val header: PmTilesHeader get() = reader.header
    fun metadata(): String = reader.metadata()
    fun getTile(z: Int, x: Int, y: Int): ByteArray? = reader.getTile(z, x, y)
    fun getTile(z: Int, x: Long, y: Long): ByteArray? = reader.getTile(z, x, y)
    fun getTileById(tileId: Long): ByteArray? = reader.getTileById(tileId)
    fun allTiles(): Sequence<TileData> = reader.allTiles()

    override fun close() = channel.close()
}