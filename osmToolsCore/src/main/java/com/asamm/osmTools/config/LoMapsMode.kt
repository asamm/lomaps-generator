package com.asamm.osmTools.config

/**
 * High-level generation mode passed via the `--mode` CLI flag.
 *
 * Each mode expands to a fixed set of base [Action]s. Dependencies required by those actions
 * (e.g. [Action.EXTRACT_OSM_PLANET] before [Action.ADDRESS_POI_DB]) are injected automatically
 * by [ConfigUtils.resolveActions].
 */
enum class LoMapsMode(val label: String) {

    /**
     * Generates offline vector maps for Locus Store.
     *
     * Upload is optional and controlled by the `--release` CLI flag.
     */
    OFFLINE("offline"),

    /**
     * Generates online planet-level tile maps (always runs on the full planet).
     *
     * Pipeline (after dependency expansion):
     * tourist → contour → overview_map → generate_pmtiles_online
     */
    ONLINE("online");

    /** Base actions for this mode before automatic dependency expansion. */
    fun baseActions(): List<Action> = when (this) {
        OFFLINE -> listOf(
            Action.TOURIST,
            Action.CONTOUR,
            Action.ADDRESS_POI_DB,
            Action.GENERATE_MAPSFORGE,
            Action.GENERATE_MBTILES,
        )
        ONLINE -> listOf(
            Action.TOURIST,
            Action.CONTOUR,
            Action.GENERATE_PMTILES_ONLINE,
        )
    }

    companion object {
        fun fromLabel(label: String): LoMapsMode =
            entries.firstOrNull { it.label.equals(label, ignoreCase = true) }
                ?: throw IllegalArgumentException(
                    "Unknown LoMaps mode '$label'. Valid values: ${entries.map { it.label }.joinToString(", ")}"
                )
    }
}
