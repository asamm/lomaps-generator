package com.asamm.osmTools.utils

import java.io.File

/**
 * Coordinates publishing a new planet.pmtiles version on S3.
 *
 * Flow for each publish:
 *  1. Upload the local file to `<versionsPrefix>/<version>/<fileName>` — the versioned copy.
 *  2. Server-side copy from the versioned key to `<latestPrefix>/<fileName>` — replaces the
 *     previous "latest" object without re-uploading the bytes.
 *  3. Prune `<versionsPrefix>` so only the [keep] most recent versions remain (ordered
 *     lexicographically by the version directory name — matches the date-based scheme like
 *     `2026.04.21`).
 *
 * @param s3Client        S3 client bound to the bucket that stores the pmtiles
 * @param versionsPrefix  Prefix under which versioned copies live, e.g. `pmtiles/versions`
 * @param latestPrefix    Prefix where the "latest" pointer lives, e.g. `pmtiles`
 * @param keep            Number of most recent versions to retain (must be >= 1)
 */
class OnlinePlanetVersionsManager(
    private val s3Client: S3Client,
    versionsPrefix: String,
    latestPrefix: String,
    private val keep: Int
) {

    companion object {
        val TAG: String = OnlinePlanetVersionsManager::class.java.simpleName
    }

    init {
        require(keep >= 1) { "keep must be at least 1, got $keep" }
    }

    private val versionsPrefix: String = versionsPrefix.trimEnd('/')
    private val latestPrefix: String = latestPrefix.trimEnd('/')

    /**
     * Uploads [localFile] as a new version [version], promotes it to latest, then prunes old versions.
     *
     * @param localFile File to upload (must exist)
     * @param version   Version identifier, e.g. `2026.04.21` — becomes a directory segment in S3
     * @param fileName  Object name under both the versioned and latest prefixes; defaults to the local file name
     */
    @JvmOverloads
    fun publish(localFile: File, version: String, fileName: String = localFile.name) {
        require(version.isNotBlank()) { "version must not be blank" }
        require(!version.contains('/')) { "version must not contain '/': $version" }

        val versionedKey = "$versionsPrefix/$version/$fileName"
        val latestKey = "$latestPrefix/$fileName"

        Logger.i(TAG, "Publishing version '$version' of '$fileName'")

        s3Client.uploadFile(localFile, versionedKey)

        Logger.i(TAG, "Promoting version '$version' to latest: $latestKey")
        s3Client.copyObject(versionedKey, latestKey)

        try {
            pruneOldVersions(fileName)
        } catch (t: Throwable) {
            // Pruning is best-effort: the main goal (upload + latest) is already done.
            Logger.e(TAG, "Pruning old versions failed: ${t.message}")
        }
    }

    /**
     * Lists version directories under [versionsPrefix] that contain [fileName] and deletes all
     * except the [keep] most recent (lexicographic order).
     */
    private fun pruneOldVersions(fileName: String) {
        val allKeys = s3Client.listObjects("$versionsPrefix/")
        val suffix = "/$fileName"

        // Collect only versions that actually contain our target file — avoids deleting
        // sibling content that might live under the same versions prefix.
        val versions = allKeys
            .filter { it.endsWith(suffix) }
            .mapNotNull { key ->
                val tail = key.removePrefix("$versionsPrefix/")
                val version = tail.removeSuffix(suffix)
                if (version.isNotEmpty() && !version.contains('/')) version else null
            }
            .distinct()
            .sortedDescending()

        if (versions.size <= keep) {
            Logger.i(TAG, "Found ${versions.size} version(s) of '$fileName', within keep=$keep — nothing to prune")
            return
        }

        val toDelete = versions.drop(keep)
        Logger.i(TAG, "Found ${versions.size} version(s) of '$fileName', keeping $keep, deleting ${toDelete.size}: $toDelete")

        for (version in toDelete) {
            s3Client.deleteObject("$versionsPrefix/$version/$fileName")
        }
    }
}