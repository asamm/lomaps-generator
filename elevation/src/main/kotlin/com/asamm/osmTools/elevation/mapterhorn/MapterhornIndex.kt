package com.asamm.osmTools.elevation.mapterhorn

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents one entry from the Mapterhorn download index JSON.
 *
 * Each entry describes a downloadable file: its name, download URL,
 * MD5 hash for integrity verification, and size in bytes.
 */
@Serializable
data class MapterhornEntry(
    val name: String,
    val url: String,
    val md5sum: String,
    val min_zoom: Int,
    val max_zoom: Int,
    @SerialName("size") val sizeBytes: Long,
)

/**
 * Root object of the Mapterhorn download index JSON.
 *
 * Example: `{ "version": "0.0.9", "items": [ { "name": "...", ... }, ... ] }`
 */
@Serializable
data class MapterhornIndex(
    val version: String = "",
    val items: List<MapterhornEntry> = emptyList(),
)