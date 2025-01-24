package com.asamm.osmTools.config

enum class Action {

    @Deprecated("Do not use this action")
    DOWNLOAD("download", true),

    EXTRACT("extract"),

    COASTLINE("coastline"),

    TOURIST("tourist", true),

    TRANSFORM("transform"),

    CONTOUR("contour", true),

    MERGE("merge"),

    GENERATE_MAPLIBRE("generate_maplibre"),

    UPLOAD_MAPTILER("upload_maptiler"),

    GENERATE_MAPSFORGE("generate_mapsforge"),

    GRAPH_HOPPER("graphHopper"),

    ADDRESS_POI_DB("address_poi_db", true),

    COMPRESS("compress"),

    UPLOAD("upload", true),

    CREATE_JSON("create_json"),

    STORE_GEO_DB("storeGeoDb"),

    UNKNOWN("unknown");

    private val label: String

    // is this action used in CLI
    private val cli: Boolean

    /**
     * Define action with label and if it is used in CLI
     */
    constructor(label: String, isCli: Boolean = false) {
        this.label = label
        this.cli = isCli
    }

    fun getLabel(): String {
        return label
    }

    /**
     * Get list of actions those can be used in CLI commands
     */
    companion object {
        @JvmStatic
        fun getCliActions(): List<Action> {
            return Action.entries.filter { it.cli }
        }

        @JvmStatic
        fun getActionByLabel(label: String): Action {
            return Action.entries.find { it.label == label } ?: UNKNOWN
        }
    }

}