package com.asamm.osmTools.utils

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.TimeUnit

/**
 * Downloads a file from a URL to a local path.
 *
 * Optionally verifies the MD5 hash of the downloaded file.
 * Prints a progress bar to stdout during download.
 */
object FileDownloader {

    private const val TAG = "FileDownloader"
    private const val BUFFER_SIZE = 8 * 1024

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.MINUTES)
        .build()

    /**
     * Downloads the content of [url] and returns it as a raw string.
     *
     * Intended for small JSON responses (index files, manifests, metadata) where
     * saving to disk is unnecessary.
     *
     * @param url The URL to fetch.
     * @return The response body as a UTF-8 string.
     * @throws IOException On network failure or a non-2xx HTTP response.
     */
    @Throws(IOException::class)
    fun downloadAndReadAsString(url: String): String {
        Logger.i(TAG, "Fetching text file from $url")
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code} ${response.message} for $url")
            }
            return response.body.string()
        }
    }

    /**
     * Download a file from [url] and save it to [destination].
     *
     * @param url         The URL to download from.
     * @param destination Local path where the file will be saved.
     *                    Parent directories are created automatically.
     * @param expectedMd5 Optional lowercase hex MD5 to verify after download.
     *                    Pass null to skip verification.
     * @throws IOException On network, I/O failure, or MD5 mismatch.
     */
    @Throws(IOException::class)
    fun download(url: String, destination: Path, expectedMd5: String? = null): Boolean {

        Logger.i(TAG, "Downloading $url -> $destination")

        Files.createDirectories(destination.parent)

        // Temporary file with "T" postfix in final location
        val tmp = destination.resolveSibling(destination.fileName.toString() + ".T")

        try {
            val request = Request.Builder().url(url).get().build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("HTTP ${response.code} ${response.message} for $url")
                }

                val totalBytes = response.body.contentLength()  // -1 if unknown

                response.body.byteStream().use { input ->
                    Files.newOutputStream(tmp).use { output ->
                        copyWithProgress(input, output, totalBytes)
                    }
                }
            }

            if (expectedMd5 != null) {
                verifyMd5(tmp, expectedMd5)
            }

            Files.move(tmp, destination, StandardCopyOption.REPLACE_EXISTING)
            Logger.i(TAG, "Download complete: $destination")

        } catch (e: Exception) {
            Files.deleteIfExists(tmp)
            throw e
        }

        return true
    }

    /**
     * Checks whether the volume containing [destination] has enough free disk space.
     *
     * Uses the parent directory if it already exists, otherwise falls back to the root.
     *
     * @param destination   Target file path whose volume is checked.
     * @param requiredBytes Number of bytes required.
     * @throws IOException If free space is less than [requiredBytes].
     */
    @Throws(IOException::class)
    fun checkDiskSpace(destination: Path, requiredBytes: Long) {
        val absolute = destination.toAbsolutePath()
        val dir = if (Files.exists(absolute.parent)) absolute.parent else absolute.root
        val freeBytes = dir.toFile().freeSpace

        Logger.i(TAG, "Disk space check — required: ${formatBytes(requiredBytes)}  free: ${formatBytes(freeBytes)}")

        if (freeBytes < requiredBytes) {
            throw IOException(
                "Insufficient disk space for $destination: " +
                "required ${formatBytes(requiredBytes)}, available ${formatBytes(freeBytes)}"
            )
        }
    }

    // --- internal helpers ---

    /**
     * Copies data from the input stream to the output stream, updating the MD5 digest and printing progress.
     *
     * @param input       The input stream to read from.
     * @param output      The output stream to write to.
     * @param totalBytes  The total number of bytes to download, or -1 if unknown.
     */
    private fun copyWithProgress(input: InputStream, output: OutputStream, totalBytes: Long) {
        val buffer = ByteArray(BUFFER_SIZE)
        var downloaded = 0L
        var lastPrintTime = System.currentTimeMillis()
        var read: Int

        while (input.read(buffer).also { read = it } != -1) {
            output.write(buffer, 0, read)
            downloaded += read

            val now = System.currentTimeMillis()
            if (now - lastPrintTime >= 500 || (totalBytes > 0 && downloaded == totalBytes)) {
                printProgress(downloaded, totalBytes)
                lastPrintTime = now
            }
        }

        // Ensure 100% is shown and cursor moves to next line
        printProgress(downloaded, totalBytes)
        println()
    }

    private fun printProgress(downloaded: Long, totalBytes: Long) {
        if (totalBytes > 0) {
            val pct = (downloaded * 100 / totalBytes).toInt()
            val filled = pct / 2
            val bar = "█".repeat(filled) + "░".repeat(50 - filled)
            val mb = "%.1f / %.1f MB".format(downloaded / 1_048_576.0, totalBytes / 1_048_576.0)
            print("\r  [$bar] $pct%  $mb  ")
        } else {
            // Content-Length unknown — show spinner + bytes
            val mb = "%.1f MB".format(downloaded / 1_048_576.0)
            print("\r  Downloading… $mb downloaded  ")
        }
        System.out.flush()
    }

    private fun formatBytes(bytes: Long): String = "%.1f GB".format(bytes / 1_073_741_824.0)

    private fun verifyMd5(file: Path, expected: String): Boolean {
        Logger.i(TAG, "Verifying MD5 for $file")
        val actual = Utils.generateMD5hash(file)
        if (!actual.equals(expected, ignoreCase = true)) {
            throw IOException("MD5 mismatch for $file: expected=$expected  actual=$actual")
        }
        Logger.i(TAG, "MD5 OK: $actual")
        return true
    }
}