package com.asamm.osmTools.elevation

import java.awt.image.BufferedImage
import java.awt.image.DataBufferByte
import java.awt.image.DataBufferInt
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import com.luciad.imageio.webp.WebPImageWriterSpi
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import javax.imageio.stream.MemoryCacheImageOutputStream

/**
 * Shared codec for terrain-RGB tile data.
 *
 * Provides stateless, thread-safe utility methods for:
 * - Decoding tile bytes (WebP/PNG, optionally GZIP-wrapped) into pixel arrays or elevation grids
 * - Encoding elevation back into RGB pixels
 * - Lossless WebP encoding
 * - GZIP compress/decompress
 *
 * All methods are pure functions operating on primitive arrays — no object allocation
 * in hot paths, safe for concurrent use from any number of threads.
 */
object TerrainRgbCodec {

    /** Maximum valid 24-bit RGB raw value (2^24 - 1). */
    const val RAW_MAX = 16_777_215

    // ── Encoding ─────────────────────────────────────────────────────────────

    /**
     * Terrain-RGB pixel encoding: defines how RGB pixel values map to elevation.
     *
     * Both encodings pack elevation into a 24-bit raw value `R*65536 + G*256 + B`,
     * but use different scaling/offset to convert to meters.
     *
     * @param rawUnitsPerMeter How many raw integer units equal 1 meter (used for rounding precision).
     */
    enum class Encoding(val rawUnitsPerMeter: Int) {
        /** `elevation = (R*65536 + G*256 + B) * 0.1 - 10000`; 1 raw unit = 0.1 m */
        MAPBOX(10),
        /** `elevation = R*256 + G + B/256 - 32768`; 1 raw unit = 1/256 m */
        TERRARIUM(256),
    }

    // ── Elevation decode/encode ──────────────────────────────────────────────

    /**
     * Decodes RGB pixel values to elevation in meters.
     *
     * @return Elevation in meters as a float.
     */
    fun decodeElevation(r: Int, g: Int, b: Int, encoding: Encoding): Float {
        return when (encoding) {
            Encoding.MAPBOX ->
                (r * 65536 + g * 256 + b) * 0.1f - 10000f
            Encoding.TERRARIUM ->
                r * 256f + g + b / 256f - 32768f
        }
    }

    /**
     * Encodes elevation in meters back to a 24-bit raw RGB value.
     *
     * @return Packed raw value (R in bits 16–23, G in bits 8–15, B in bits 0–7).
     */
    fun encodeRaw(meters: Float, encoding: Encoding): Int {
        val raw = when (encoding) {
            Encoding.MAPBOX -> ((meters + 10000f) / 0.1f).toInt()
            Encoding.TERRARIUM -> ((meters + 32768f) * 256f).toInt()
        }
        return raw.coerceIn(0, RAW_MAX)
    }

    // ── Tile image decode ────────────────────────────────────────────────────

    /**
     * Decoded tile: elevation values in meters as a flat float array (row-major).
     * [Float.NaN] indicates no-data (missing pixel or transparent alpha).
     */
    class DecodedTile(val width: Int, val height: Int, val elevations: FloatArray)

    /**
     * Decodes tile image bytes into an elevation float array.
     *
     * Steps:
     * 1. Decompress GZIP if [isGzip] is true
     * 2. Decode image (WebP/PNG) via ImageIO SPI
     * 3. Convert each pixel's RGB to elevation using [encoding]
     * 4. Transparent pixels (alpha=0) become [Float.NaN]
     *
     * Thread-safe: no shared mutable state.
     *
     * @param tileBytes Raw tile bytes (possibly GZIP-compressed image data).
     * @param encoding  How RGB maps to elevation.
     * @param isGzip    Whether tile bytes are GZIP-wrapped.
     * @return Decoded tile with elevation grid, or null if image cannot be decoded.
     */
    fun decodeTileToElevations(tileBytes: ByteArray, encoding: Encoding, isGzip: Boolean): DecodedTile? {
        val imageBytes = if (isGzip) gunzip(tileBytes) else tileBytes
        val img = ImageIO.read(ByteArrayInputStream(imageBytes)) ?: return null

        val w = img.width
        val h = img.height
        val elevations = FloatArray(w * h)

        // Read raw pixel bytes directly from DataBuffer to avoid ColorModel.getRGB()
        // which may apply sRGB color-space conversion and corrupt terrain-RGB values.
        val argbPixels = readRawArgbPixels(img)

        for (i in argbPixels.indices) {
            val p = argbPixels[i]
            val a = (p ushr 24) and 0xFF
            if (a == 0) {
                elevations[i] = Float.NaN
            } else {
                val r = (p ushr 16) and 0xFF
                val g = (p ushr 8) and 0xFF
                val b = p and 0xFF
                elevations[i] = decodeElevation(r, g, b, encoding)
            }
        }

        return DecodedTile(w, h, elevations)
    }

    /**
     * Decodes tile image bytes into a [BufferedImage].
     *
     * Handles optional GZIP decompression. Use this when you need pixel-level
     * access (e.g., for rounding and re-encoding).
     *
     * @return Decoded image, or null if the bytes cannot be decoded.
     */
    fun decodeTileToImage(tileBytes: ByteArray, isGzip: Boolean): BufferedImage? {
        val imageBytes = if (isGzip) gunzip(tileBytes) else tileBytes
        return ImageIO.read(ByteArrayInputStream(imageBytes))
    }

    // ── Pixel rounding ───────────────────────────────────────────────────────

    /**
     * Rounds elevation-encoded RGB pixels in-place on a [BufferedImage].
     *
     * Both Mapbox and Terrarium encode elevation into the same 24-bit raw value:
     * `raw = R * 65536 + G * 256 + B`. Rounding to [rawStep] snaps nearby values
     * to the same number, creating uniform pixel regions that compress much better
     * with lossless WebP.
     *
     * Tight integer-only loop — no float conversion, no allocation per pixel.
     * For a 256×256 tile: 65,536 iterations.
     */
    fun roundPixels(img: BufferedImage, rawStep: Int) {
        val w = img.width
        val h = img.height
        val pixels = img.getRGB(0, 0, w, h, null, 0, w)
        val half = rawStep / 2

        for (i in pixels.indices) {
            val p = pixels[i]
            val a = p and 0xFF000000.toInt()
            val r = (p ushr 16) and 0xFF
            val g = (p ushr 8) and 0xFF
            val b = p and 0xFF

            val raw = r * 65536 + g * 256 + b
            val rounded = ((raw + half) / rawStep * rawStep).coerceIn(0, RAW_MAX)

            pixels[i] = a or
                    ((rounded shr 16 and 0xFF) shl 16) or
                    ((rounded shr 8 and 0xFF) shl 8) or
                    (rounded and 0xFF)
        }

        img.setRGB(0, 0, w, h, pixels, 0, w)
    }

    // ── Image encoding ───────────────────────────────────────────────────────

    /**
     * Encodes a [BufferedImage] as lossless WebP.
     *
     * Instantiates the WebP writer directly via [WebPImageWriterSpi] to avoid
     * triggering [javax.imageio.ImageIO] class initialization, which crashes when
     * the GeoTools TIFF SPI tries to load the absent JAI library.
     *
     * Thread-safe: creates a new writer instance per call.
     */
    fun encodeLosslessWebP(img: BufferedImage): ByteArray {
        val bos = ByteArrayOutputStream(img.width * img.height * 3)
        val writer = WebPImageWriterSpi().createWriterInstance()
        try {
            val param = writer.defaultWriteParam
            if (param.canWriteCompressed()) {
                param.compressionMode = ImageWriteParam.MODE_EXPLICIT
                val types = param.compressionTypes
                val lossless = types?.firstOrNull { it.contains("lossless", ignoreCase = true) }
                if (lossless != null) param.compressionType = lossless
            }
            MemoryCacheImageOutputStream(bos).use { ios ->
                writer.output = ios
                writer.write(null, IIOImage(img, null, null), param)
            }
        } finally {
            writer.dispose()
        }
        return bos.toByteArray()
    }

    // ── Raw pixel access ───────────────────────────────────────────────────

    /**
     * Reads raw ARGB pixels directly from the image's [DataBuffer], bypassing
     * [BufferedImage.getRGB] which may apply sRGB color-space conversion via
     * [java.awt.image.ColorModel.getRGB].
     *
     * For terrain-RGB data we need the literal byte values as stored in the image
     * file — any gamma or color-space transform would corrupt the elevation encoding.
     *
     * Handles the most common image types produced by WebP/PNG ImageIO decoders:
     * - TYPE_INT_ARGB / TYPE_INT_ARGB_PRE — direct int array copy
     * - TYPE_INT_RGB — adds 0xFF alpha
     * - TYPE_3BYTE_BGR — byte reorder B,G,R → A,R,G,B
     * - TYPE_4BYTE_ABGR / TYPE_4BYTE_ABGR_PRE — byte reorder A,B,G,R → A,R,G,B
     * - TYPE_INT_BGR — bit reorder
     * - Other / TYPE_CUSTOM — fallback to getRGB() (best effort)
     *
     * @return IntArray of packed 0xAARRGGBB values, one per pixel, row-major.
     */
    fun readRawArgbPixels(img: BufferedImage): IntArray {
        val w = img.width
        val h = img.height
        val n = w * h

        return when (img.type) {
            BufferedImage.TYPE_INT_ARGB, BufferedImage.TYPE_INT_ARGB_PRE -> {
                // Pixel is already stored as 0xAARRGGBB — direct copy
                val src = (img.raster.dataBuffer as DataBufferInt).data
                src.copyOf(n)
            }

            BufferedImage.TYPE_INT_RGB -> {
                // Stored as 0x00RRGGBB — add full opacity alpha
                val src = (img.raster.dataBuffer as DataBufferInt).data
                IntArray(n) { src[it] or 0xFF000000.toInt() }
            }

            BufferedImage.TYPE_INT_BGR -> {
                // Stored as 0x00BBGGRR — swap R↔B and add alpha
                val src = (img.raster.dataBuffer as DataBufferInt).data
                IntArray(n) { i ->
                    val p = src[i]
                    val r = p and 0xFF
                    val g = (p ushr 8) and 0xFF
                    val b = (p ushr 16) and 0xFF
                    0xFF000000.toInt() or (r shl 16) or (g shl 8) or b
                }
            }

            BufferedImage.TYPE_3BYTE_BGR -> {
                // Bytes stored as [B, G, R, B, G, R, ...]
                val src = (img.raster.dataBuffer as DataBufferByte).data
                IntArray(n) { i ->
                    val off = i * 3
                    val b = src[off].toInt() and 0xFF
                    val g = src[off + 1].toInt() and 0xFF
                    val r = src[off + 2].toInt() and 0xFF
                    0xFF000000.toInt() or (r shl 16) or (g shl 8) or b
                }
            }

            BufferedImage.TYPE_4BYTE_ABGR, BufferedImage.TYPE_4BYTE_ABGR_PRE -> {
                // Bytes stored as [A, B, G, R, A, B, G, R, ...]
                val src = (img.raster.dataBuffer as DataBufferByte).data
                IntArray(n) { i ->
                    val off = i * 4
                    val a = src[off].toInt() and 0xFF
                    val b = src[off + 1].toInt() and 0xFF
                    val g = src[off + 2].toInt() and 0xFF
                    val r = src[off + 3].toInt() and 0xFF
                    (a shl 24) or (r shl 16) or (g shl 8) or b
                }
            }

            else -> {
                // Fallback for TYPE_CUSTOM or exotic types — use getRGB() (may do conversion)
                img.getRGB(0, 0, w, h, null, 0, w)
            }
        }
    }

    // ── Compression helpers ──────────────────────────────────────────────────

    fun gunzip(data: ByteArray): ByteArray =
        GZIPInputStream(ByteArrayInputStream(data)).use { it.readBytes() }

    fun gzip(data: ByteArray): ByteArray {
        val bos = ByteArrayOutputStream(data.size)
        GZIPOutputStream(bos).use { it.write(data) }
        return bos.toByteArray()
    }
}