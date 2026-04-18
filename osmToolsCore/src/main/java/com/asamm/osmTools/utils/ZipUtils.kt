package com.asamm.osmTools.utils

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.zip.ZipInputStream

object ZipUtils {

    /**
     * Extracts a ZIP archive into [targetDirectory], preserving the directory structure
     * contained in the archive.
     *
     * Uses an 8 MB buffer and buffered streams for performance with large archives.
     * Each file is written atomically via a `.tmp` staging file.
     * Entries that would escape [targetDirectory] (Zip Slip) are rejected with [IOException].
     *
     * @param zipFile         ZIP archive to extract.
     * @param targetDirectory Destination directory (created if absent).
     * @throws IOException    On I/O errors or Zip Slip attempts.
     */
    @Throws(IOException::class)
    fun unzipFile(zipFile: Path, targetDirectory: Path) {
        Files.createDirectories(targetDirectory)
        val canonicalTarget = targetDirectory.toFile().canonicalPath

        ZipInputStream(Files.newInputStream(zipFile).buffered(1024 * 1024)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val destFile = targetDirectory.resolve(entry.name)

                // Zip Slip protection
                if (!destFile.toFile().canonicalPath.startsWith(canonicalTarget)) {
                    throw IOException("ZIP entry escapes target directory: ${entry.name}")
                }

                if (entry.isDirectory) {
                    Files.createDirectories(destFile)
                } else {
                    Files.createDirectories(destFile.parent)

                    val tmpFile = destFile.resolveSibling("${destFile.fileName}.tmp")
                    try {
                        Files.newOutputStream(tmpFile).buffered(1024 * 1024).use { out ->
                            val buffer = ByteArray(8 * 1024 * 1024)
                            var read: Int
                            while (zis.read(buffer).also { read = it } != -1) {
                                out.write(buffer, 0, read)
                            }
                        }
                        Files.move(tmpFile, destFile, StandardCopyOption.REPLACE_EXISTING)
                    } catch (e: IOException) {
                        Files.deleteIfExists(tmpFile)
                        throw e
                    }
                }

                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }
}