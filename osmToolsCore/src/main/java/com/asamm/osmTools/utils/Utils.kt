package com.asamm.osmTools.utils

import org.apache.commons.io.FileUtils
import org.apache.commons.io.IOUtils
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.UnknownHostException
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.regex.Pattern
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

object Utils {

    private const val TAG = "Utils"

    /** Returns `true` when the `ENV` environment variable is set to `"DEV"`. */
    fun isLocalDEV(): Boolean = System.getenv("ENV") == "DEV"

    /** Normalises path separators in [name] to the platform separator ([Consts.FILE_SEP]). */
    fun changeSlash(name: String): String = when {
        name.contains("/") -> name.replace("/", Consts.FILE_SEP)
        name.contains("\\") -> name.replace("\\", Consts.FILE_SEP)
        else -> name
    }

    /** Returns the size of [path] in bytes, or 0 if the file is missing or inaccessible. */
    fun fileSize(path: Path): Long = try {
        Files.size(path)
    } catch (_: IOException) {
        0L
    }

    /** Replaces the platform separator ([Consts.FILE_SEP]) in [name] with a Unix forward slash. */
    fun changeSlashToUnix(name: String): String =
        if (name.contains(Consts.FILE_SEP)) name.replace(Consts.FILE_SEP, "/") else name

    /** @see deleteFilesInDir */
    fun deleteFilesInDir(pathToDir: String) = deleteFilesInDir(Path.of(pathToDir))

    /**
     * Deletes all **files** (non-recursive) inside [pathToDir].
     * Sub-directories are left in place. Logs a warning if the path does not exist.
     */
    fun deleteFilesInDir(pathToDir: Path) {
        val dir = pathToDir.toFile()
        if (!dir.exists() || !dir.isDirectory) {
            Logger.w(TAG, "Path for deleting: $pathToDir does not exist or is not directory")
            return
        }
        dir.listFiles()?.filter { it.isFile }?.forEach { it.delete() }
    }

    /**
     * Recursively deletes [pathToDir] and all its contents.
     * Logs a warning if the path does not exist; logs and swallows [IOException] on failure.
     */
    fun deleteDirRecursively(pathToDir: Path) {
        val dir = pathToDir.toFile()
        if (!dir.exists() || !dir.isDirectory) {
            Logger.w(TAG, "Path for deleting: $pathToDir does not exist or is not directory")
            return
        }
        try {
            FileUtils.deleteDirectory(dir)
        } catch (e: IOException) {
            Logger.w(TAG, "deleteDirRecursively()", e)
        }
    }

    /**
     * Deletes [path] without throwing — logs a warning on [IOException].
     * Does nothing if the file does not exist.
     */
    fun deleteFileQuietly(path: Path) {
        try {
            if (path.toFile().exists()) Files.delete(path)
        } catch (e: IOException) {
            Logger.w(TAG, "deleteFile(), Unable to delete file", e)
        }
    }

    /**
     * Copies [source] to [target], creating any missing parent directories.
     *
     * @param replaceExisting overwrite [target] if it already exists
     * @throws IllegalArgumentException on [IOException]
     */
    fun copyFile(source: Path, target: Path, replaceExisting: Boolean) {
        try {
            target.parent?.let { Files.createDirectories(it) }
            if (replaceExisting) {
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
            } else {
                Files.copy(source, target)
            }
        } catch (e: IOException) {
            Logger.e(TAG, "Error copying file: $source to file: $target Error: ${e.message}")
            throw IllegalArgumentException("Error copying file:  ${e.message}")
        }
    }

    /**
     * Moves (renames) [source] to [target].
     *
     * @param replaceExisting overwrite [target] if it already exists
     * @throws IllegalArgumentException on [IOException]
     */
    fun renameFileQuitly(source: Path, target: Path, replaceExisting: Boolean) {
        try {
            if (replaceExisting) {
                Files.move(source, target, StandardCopyOption.REPLACE_EXISTING)
            } else {
                Files.move(source, target)
            }
        } catch (e: IOException) {
            Logger.e(TAG, "Error renaming file: $source to file: $target Error: ${e.message}")
            throw IllegalArgumentException("Error renaming file:  ${e.message}")
        }
    }

    /**
     * Changes the extension of [pathToFile]. If the file has no extension, [newExtension] is appended.
     *
     * @param newExtension new extension including the dot (e.g. `".txt"`)
     */
    fun changeFileExtension(pathToFile: Path, newExtension: String): Path {
        val fileName = pathToFile.fileName.toString()
        val dotIndex = fileName.lastIndexOf('.')
        val baseName = if (dotIndex != -1) fileName.substring(0, dotIndex) else fileName
        return pathToFile.parent.resolve(baseName + newExtension)
    }

    /**
     * Generates the MD5 hash of the file at [pathToFile].
     *
     * @return the MD5 hash as a lowercase hexadecimal string
     * @throws RuntimeException if the file cannot be read or MD5 is unavailable
     */
    fun generateMD5hash(pathToFile: Path): String {
        return try {
            val md = MessageDigest.getInstance("MD5")
            Files.newInputStream(pathToFile).use { fis ->
                val buf = ByteArray(8192)
                var nread: Int
                while (fis.read(buf).also { nread = it } != -1) {
                    md.update(buf, 0, nread)
                }
            }
            md.digest().joinToString("") { "%02x".format(it) }
        } catch (e: IOException) {
            Logger.w(TAG, "generateMD5hash()", e)
            throw RuntimeException("Unable to generate MD5 hash for file: $pathToFile", e)
        }
    }


    /**
     * Compresses [files] into a single ZIP archive at [target] (maximum compression, level 9).
     * Parent directories of [target] are created if missing.
     * The last-modified timestamp of each source file is preserved in the ZIP entry.
     *
     * @throws IllegalArgumentException if any source file does not exist
     */
    @Throws(IOException::class)
    fun compressFiles(files: List<File>, target: File) {
        target.parentFile?.let { FileUtils.forceMkdir(it) }
        ZipOutputStream(FileOutputStream(target)).use { zos ->
            zos.setLevel(9)
            for (file in files) {
                require(file.exists()) { "File '$file' for compress does not exist" }
                val entry = ZipEntry(file.name).apply { time = file.lastModified() }
                zos.putNextEntry(entry)
                FileUtils.copyFile(file, zos)
                zos.closeEntry()
            }
            zos.flush()
        }
    }

    /**
     * Formats [bytes] as a human-readable string with two decimal places (e.g. `"1.23 MB"`).
     * Negative values are treated as 0.
     */
    fun formatBytesToHuman(bytes: Long): String {
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var value = maxOf(bytes, 0L).toDouble()
        var unit = 0
        while (value >= 1024 && unit < units.lastIndex) {
            value /= 1024
            unit++
        }
        return "%.2f %s".format(value, units[unit])
    }

    /** Returns `true` if [str] can be parsed as a [Double]. */
    fun isNumeric(str: String): Boolean = str.toDoubleOrNull() != null

    /**
     * Creates an empty file at [path], including any missing parent directories.
     *
     * @return `true` if the file was created; `false` if it already existed
     * @throws IllegalArgumentException on [IOException]
     */
    fun createEmptyFile(path: String): Boolean {
        val file = File(path)
        if (file.exists()) return false
        return try {
            FileUtils.forceMkdir(file)
            file.createNewFile()
        } catch (e: IOException) {
            throw IllegalArgumentException("Error while creating a new empty file: $e")
        }
    }

    /** @see createParentDirs */
    fun createParentDirs(path: Path): Boolean = createParentDirs(path.toString())

    /**
     * Ensures all parent directories of [path] exist, creating them if necessary.
     * Does nothing if [path] already exists.
     *
     * @throws IllegalArgumentException on [IOException]
     */
    fun createParentDirs(path: String): Boolean {
        val file = File(path)
        if (file.exists()) return false
        try {
            FileUtils.forceMkdir(file.parentFile)
        } catch (e: IOException) {
            throw IllegalArgumentException("Error while creating directory structure: $e")
        }
        return false
    }

    /**
     * Returns the file name without its extension, or an empty string if there is no extension.
     */
    fun getFileNamePart(path: Path): String {
        val fileName = path.fileName.toString()
        val dotIndex = fileName.indexOf('.')
        return if (dotIndex != -1) fileName.substring(0, dotIndex) else ""
    }

    /**
     * Inserts [text] before the extension of [filePath].
     * If there is no extension, [text] is appended to the end.
     */
    fun appendBeforeExtension(filePath: Path, text: String): Path {
        val fileName = filePath.fileName.toString()
        val dotIndex = fileName.indexOf('.')
        val newFileName = if (dotIndex == -1) {
            fileName + text
        } else {
            fileName.substring(0, dotIndex) + text + fileName.substring(dotIndex)
        }
        return filePath.parent.resolve(newFileName)
    }

    /**
     * Moves [source] to [target].
     *
     * @param replaceExisting overwrite [target] if it already exists
     * @return `true` on success; `false` on [IOException] (error is logged)
     */
    fun moveFile(source: Path, target: Path, replaceExisting: Boolean): Boolean {
        return try {
            if (replaceExisting) {
                Files.move(source, target, StandardCopyOption.REPLACE_EXISTING)
            } else {
                Files.move(source, target)
            }
            true
        } catch (e: IOException) {
            Logger.e(TAG, "Error moving file: ${e.message}")
            false
        }
    }

    /** Returns `true` when running on a Windows OS. */
    fun isSystemWindows(): Boolean = System.getProperty("os.name").lowercase().contains("win")

    /** Returns `true` when running on a Unix/Linux OS. */
    fun isSystemUnix(): Boolean = System.getProperty("os.name").lowercase().let {
        it.contains("nix") || it.contains("nux")
    }

    /**
     * Returns the machine hostname.
     * Tries the `hostname` / `computername` environment variables first,
     * then falls back to [java.net.InetAddress.getLocalHost].
     */
    @Throws(UnknownHostException::class)
    fun getHostname(): String {
        val hostname = when {
            isSystemUnix() -> System.getenv("hostname")
            isSystemWindows() -> System.getenv("computername")
            else -> null
        }
        return hostname ?: java.net.InetAddress.getLocalHost().hostName
    }

    /**
     * Returns the total uncompressed size (in bytes) of all entries in the ZIP [file].
     */
    @Throws(ZipException::class, IOException::class)
    fun getZipEntrySize(file: File): Long =
        ZipFile(file).use { zip ->
            zip.entries().asSequence().sumOf { it.size }
        }

    /**
     * Extracts the XML encoding declaration from [data].
     * Matches `<?xml version=... encoding="..."` and returns the encoding name,
     * or `"UTF-8"` if no declaration is found.
     */
    fun getEncoding(data: String): String {
        val mat = Pattern.compile("""<\?xml version\S+ encoding="(\S+)"""").matcher(data)
        return if (mat.find()) mat.toMatchResult().group(1) else "UTF-8"
    }

    /**
     * Writes [text] to [file], silently logging errors to stderr.
     *
     * @param append `true` to append to the file; `false` to overwrite
     */
    fun writeStringToFile(file: File, text: String, append: Boolean) {
        try {
            file.writer().use { it.write(text) }
        } catch (e: IOException) {
            System.err.println("writeStringToFile(), e: $e")
            e.printStackTrace()
        }
    }

    /**
     * Reads the entire file at [path] and returns its content as a [String].
     * Returns an empty string and logs to stderr on [IOException].
     */
    fun readFileToString(path: String, encoding: Charset): String =
        try {
            String(Files.readAllBytes(Paths.get(path)), encoding)
        } catch (e: IOException) {
            System.err.println("readFileToString(), e: $e")
            e.printStackTrace()
            ""
        }

    /**
     * Returns the value of environment variable [name].
     *
     * @throws IllegalArgumentException if the variable is not set
     */
    fun getEnvVariable(name: String): String =
        System.getenv(name) ?: throw IllegalArgumentException("Environment variable $name is not set.")
}