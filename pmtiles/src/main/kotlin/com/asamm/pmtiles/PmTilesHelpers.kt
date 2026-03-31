package com.asamm.pmtiles

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

// ─── Exceptions ──────────────────────────────────────────────────────────────

class MagicNumberNotFoundException : Exception("PMTiles magic number not found")
class SpecVersionUnsupportedException(version: Int) : Exception("Unsupported PMTiles version: $version")

// ─── Enums ───────────────────────────────────────────────────────────────────

enum class Compression(val value: Int) {
    UNKNOWN(0), NONE(1), GZIP(2), BROTLI(3), ZSTD(4);

    companion object {
        fun fromValue(v: Int): Compression = entries.firstOrNull { it.value == v } ?: UNKNOWN
    }
}

enum class TileType(val value: Int) {
    UNKNOWN(0), MVT(1), PNG(2), JPEG(3), WEBP(4), AVIF(5), MLT(6);

    companion object {
        fun fromValue(v: Int): TileType = entries.firstOrNull { it.value == v } ?: UNKNOWN
    }
}

// ─── Entry ───────────────────────────────────────────────────────────────────

/**
 * A single record in a PMTiles directory.
 *
 * @param tileId    Hilbert-curve tile ID.
 * @param offset    Byte offset from the start of the tile-data section (runLength > 0)
 *                  or leaf-directory section (runLength == 0).
 * @param length    Byte length of the referenced region.
 * @param runLength Number of consecutive tiles this entry covers.
 *                  0 means this is a leaf-directory pointer, not a tile.
 */
data class Entry(
    val tileId: Long,
    val offset: Long,
    val length: Int,
    val runLength: Int,
)

// ─── Header ──────────────────────────────────────────────────────────────────

/** Fixed size of the PMTiles v3 header in bytes. */
const val HEADER_SIZE = 127

/**
 * PMTiles v3 file header.
 *
 * Default bounds cover the full world extent.
 */
data class PmTilesHeader(
    val rootOffset: Long = HEADER_SIZE.toLong(),
    val rootLength: Long = 0L,
    val metadataOffset: Long = 0L,
    val metadataLength: Long = 0L,
    val leafDirectoryOffset: Long = 0L,
    val leafDirectoryLength: Long = 0L,
    val tileDataOffset: Long = 0L,
    val tileDataLength: Long = 0L,
    val addressedTilesCount: Long = 0L,
    val tileEntriesCount: Long = 0L,
    val tileContentsCount: Long = 0L,
    val clustered: Boolean = true,
    val internalCompression: Compression = Compression.GZIP,
    val tileCompression: Compression = Compression.NONE,
    val tileType: TileType = TileType.UNKNOWN,
    val minZoom: Int = 0,
    val maxZoom: Int = 0,
    val minLonE7: Int = -1_800_000_000,
    val minLatE7: Int = -850_000_000,
    val maxLonE7: Int = 1_800_000_000,
    val maxLatE7: Int = 850_000_000,
    val centerZoom: Int = 0,
    val centerLonE7: Int = 0,
    val centerLatE7: Int = 0,
)

/**
 * Deserializes the 127-byte PMTiles v3 header from [buf].
 *
 * @throws MagicNumberNotFoundException    if the magic string "PMTiles" is missing.
 * @throws SpecVersionUnsupportedException if the version byte is not 3.
 */
fun deserializeHeader(buf: ByteArray): PmTilesHeader {
    require(buf.size >= HEADER_SIZE) { "Buffer too small for PMTiles header (need $HEADER_SIZE, got ${buf.size})" }
    if (String(buf, 0, 7, Charsets.US_ASCII) != "PMTiles") throw MagicNumberNotFoundException()
    val version = buf[7].toInt() and 0xFF
    if (version != 3) throw SpecVersionUnsupportedException(version)

    val b = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN)
    fun u8(pos: Int) = buf[pos].toInt() and 0xFF

    return PmTilesHeader(
        rootOffset          = b.getLong(8),
        rootLength          = b.getLong(16),
        metadataOffset      = b.getLong(24),
        metadataLength      = b.getLong(32),
        leafDirectoryOffset = b.getLong(40),
        leafDirectoryLength = b.getLong(48),
        tileDataOffset      = b.getLong(56),
        tileDataLength      = b.getLong(64),
        addressedTilesCount = b.getLong(72),
        tileEntriesCount    = b.getLong(80),
        tileContentsCount   = b.getLong(88),
        clustered           = buf[96] == 0x01.toByte(),
        internalCompression = Compression.fromValue(u8(97)),
        tileCompression     = Compression.fromValue(u8(98)),
        tileType            = TileType.fromValue(u8(99)),
        minZoom             = u8(100),
        maxZoom             = u8(101),
        minLonE7            = b.getInt(102),
        minLatE7            = b.getInt(106),
        maxLonE7            = b.getInt(110),
        maxLatE7            = b.getInt(114),
        centerZoom          = u8(118),
        centerLonE7         = b.getInt(119),
        centerLatE7         = b.getInt(123),
    )
}

/** Serializes a [PmTilesHeader] into a 127-byte array. */
fun serializeHeader(h: PmTilesHeader): ByteArray {
    val buf = ByteArray(HEADER_SIZE)
    val b = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN)

    "PMTiles".toByteArray(Charsets.US_ASCII).copyInto(buf)
    buf[7] = 0x03
    b.putLong(8,  h.rootOffset)
    b.putLong(16, h.rootLength)
    b.putLong(24, h.metadataOffset)
    b.putLong(32, h.metadataLength)
    b.putLong(40, h.leafDirectoryOffset)
    b.putLong(48, h.leafDirectoryLength)
    b.putLong(56, h.tileDataOffset)
    b.putLong(64, h.tileDataLength)
    b.putLong(72, h.addressedTilesCount)
    b.putLong(80, h.tileEntriesCount)
    b.putLong(88, h.tileContentsCount)
    buf[96]  = if (h.clustered) 0x01 else 0x00
    buf[97]  = h.internalCompression.value.toByte()
    buf[98]  = h.tileCompression.value.toByte()
    buf[99]  = h.tileType.value.toByte()
    buf[100] = h.minZoom.toByte()
    buf[101] = h.maxZoom.toByte()
    b.putInt(102, h.minLonE7)
    b.putInt(106, h.minLatE7)
    b.putInt(110, h.maxLonE7)
    b.putInt(114, h.maxLatE7)
    buf[118] = h.centerZoom.toByte()
    b.putInt(119, h.centerLonE7)
    b.putInt(123, h.centerLatE7)

    return buf
}

// ─── Varint I/O ──────────────────────────────────────────────────────────────

/** Reads a variable-length unsigned integer (LEB128) from [stream]. */
fun readVarint(stream: ByteArrayInputStream): Long {
    var shift = 0
    var result = 0L
    while (true) {
        val b = stream.read()
        check(b != -1) { "Unexpected end of varint stream" }
        result = result or ((b.toLong() and 0x7FL) shl shift)
        shift += 7
        if (b and 0x80 == 0) break
    }
    return result
}

/** Writes a variable-length unsigned integer (LEB128) to [out]. */
fun writeVarint(out: ByteArrayOutputStream, value: Long) {
    var v = value
    while (true) {
        val byte7 = (v and 0x7FL).toInt()
        v = v ushr 7
        if (v != 0L) out.write(byte7 or 0x80) else { out.write(byte7); break }
    }
}

// ─── Directory serialization ─────────────────────────────────────────────────

private const val GZIP_BUFFER = 8192

/**
 * Deserializes a gzip-compressed directory buffer into an ordered list of [Entry] values.
 *
 * Encoding (after gzip decompression):
 * 1. varint: number of entries
 * 2. N varints: delta-coded tile IDs
 * 3. N varints: run lengths
 * 4. N varints: lengths
 * 5. N varints: offsets (0 = sequential with previous; otherwise stored as offset+1)
 */
fun deserializeDirectory(buf: ByteArray): List<Entry> {
    val decompressed = GZIPInputStream(ByteArrayInputStream(buf), GZIP_BUFFER).use { it.readBytes() }
    val stream = ByteArrayInputStream(decompressed)

    val n = readVarint(stream).toInt()
    val tileIds    = LongArray(n)
    val runLengths = IntArray(n)
    val lengths    = IntArray(n)
    val offsets    = LongArray(n)

    var lastId = 0L
    for (i in 0 until n) { lastId += readVarint(stream); tileIds[i] = lastId }
    for (i in 0 until n) { runLengths[i] = readVarint(stream).toInt() }
    for (i in 0 until n) { lengths[i]    = readVarint(stream).toInt() }
    for (i in 0 until n) {
        val tmp = readVarint(stream)
        offsets[i] = if (i > 0 && tmp == 0L) offsets[i - 1] + lengths[i - 1] else tmp - 1L
    }

    return List(n) { i -> Entry(tileIds[i], offsets[i], lengths[i], runLengths[i]) }
}

/**
 * Serializes a list of [Entry] values into a gzip-compressed directory buffer.
 */
fun serializeDirectory(entries: List<Entry>): ByteArray {
    // Rough estimate: varint overhead ~2 bytes per field, 4 fields per entry
    val raw = ByteArrayOutputStream(entries.size * 8 + 8)

    writeVarint(raw, entries.size.toLong())

    var lastId = 0L
    for (e in entries) { writeVarint(raw, e.tileId - lastId); lastId = e.tileId }
    for (e in entries)  writeVarint(raw, e.runLength.toLong())
    for (e in entries)  writeVarint(raw, e.length.toLong())
    for (i in entries.indices) {
        val e = entries[i]
        if (i > 0 && e.offset == entries[i - 1].offset + entries[i - 1].length) {
            writeVarint(raw, 0L)
        } else {
            writeVarint(raw, e.offset + 1L)
        }
    }

    val rawBytes = raw.toByteArray()
    return ByteArrayOutputStream(rawBytes.size / 2 + 64).also { out ->
        GZIPOutputStream(out, GZIP_BUFFER).use { gz -> gz.write(rawBytes) }
    }.toByteArray()
}

// ─── Tile ID — Hilbert curve ─────────────────────────────────────────────────

/**
 * Rotate/reflect a quadrant during Hilbert-curve traversal.
 *
 * Returns the new (x, y) packed into a single Long to avoid Pair allocation:
 *   new_x = result ushr 32
 *   new_y = result and 0xFFFFFFFFL
 *
 * Valid for coordinates up to 2^31 - 1 (covers all zoom levels 0–31).
 */
private fun rotate(n: Long, x: Long, y: Long, rx: Long, ry: Long): Long {
    if (ry == 0L) {
        val nx = if (rx != 0L) n - 1L - x else x
        val ny = if (rx != 0L) n - 1L - y else y
        return (ny shl 32) or (nx and 0xFFFFFFFFL)   // swap: first=ny (new_x), second=nx (new_y)
    }
    return (x shl 32) or (y and 0xFFFFFFFFL)
}

/**
 * Converts tile coordinates (z, x, y) to a Hilbert-curve tile ID.
 *
 * Supports zoom levels 0–31 (64-bit tile ID space).
 */
fun zxyToTileId(z: Int, x: Long, y: Long): Long {
    require(z in 0..31) { "Tile zoom exceeds 64-bit limit: z=$z" }
    val maxCoord = if (z == 0) 0L else (1L shl z) - 1L
    require(x <= maxCoord && y <= maxCoord) { "Tile x/y outside zoom level bounds (z=$z x=$x y=$y)" }

    var acc = ((1L shl (z * 2)) - 1L) / 3L
    var cx = x
    var cy = y
    for (a in (z - 1) downTo 0) {
        val s   = 1L shl a
        val rx  = s and cx
        val ry  = s and cy
        acc += ((3L * rx) xor ry) shl a
        val packed = rotate(s, cx, cy, rx, ry)
        cx = packed ushr 32
        cy = packed and 0xFFFFFFFFL
    }
    return acc
}

/** Convenience overload accepting [Int] tile coordinates. */
fun zxyToTileId(z: Int, x: Int, y: Int): Long = zxyToTileId(z, x.toLong(), y.toLong())

/**
 * Converts a Hilbert-curve tile ID back to (z, x, y) coordinates.
 *
 * For all valid tile IDs (z ≤ 31), `3 * tileId + 1 ≤ ~4.5 × 10^18 < Long.MAX_VALUE`,
 * so zoom determination is done entirely with Long arithmetic — no BigInteger needed.
 */
fun tileIdToZxy(tileId: Long): Triple<Int, Long, Long> {
    // Compute z = floor((bitLength(3*tileId+1) - 1) / 2) using pure Long arithmetic.
    // 3*tileId+1 is safe in Long for all tileIds within z≤31 space.
    val v = 3L * tileId + 1L
    val z = ((63 - java.lang.Long.numberOfLeadingZeros(v)) / 2).coerceIn(0, 31)

    val acc = ((1L shl (z * 2)) - 1L) / 3L
    var pos = tileId - acc
    var x = 0L
    var y = 0L
    var s = 1L
    val n = 1L shl z        // n = 2^z  (for z=0: n=1, loop skipped)
    while (s < n) {
        val rx = (pos ushr 1) and s
        val ry = (pos xor rx) and s
        val packed = rotate(s, x, y, rx, ry)
        x = (packed ushr 32) + rx
        y = (packed and 0xFFFFFFFFL) + ry
        pos = pos ushr 1
        s = s shl 1
    }
    return Triple(z, x, y)
}

// ─── Directory lookup ─────────────────────────────────────────────────────────

/**
 * Binary-searches [entries] (sorted by tile ID) for the entry that covers [tileId].
 *
 * Returns:
 * - The exact matching [Entry] if found.
 * - The preceding [Entry] if it is a leaf-directory pointer (runLength == 0) or
 *   if its run covers [tileId].
 * - `null` if [tileId] is not present.
 */
fun findTile(entries: List<Entry>, tileId: Long): Entry? {
    var lo = 0
    var hi = entries.size - 1
    while (lo <= hi) {
        val mid = (lo + hi) ushr 1
        val diff = tileId - entries[mid].tileId
        when {
            diff > 0L -> lo = mid + 1
            diff < 0L -> hi = mid - 1
            else      -> return entries[mid]
        }
    }
    if (hi >= 0) {
        val e = entries[hi]
        if (e.runLength == 0)                return e   // leaf directory pointer
        if (tileId - e.tileId < e.runLength) return e   // within run
    }
    return null
}