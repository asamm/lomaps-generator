package com.asamm.osmTools.config

enum class Action(val label: String) {

    @Deprecated("Do not use this action")
    DOWNLOAD("download"),

    EXTRACT_OSM_PLANET("extract"),
    TOURIST("tourist"),
    RESIDENTIAL("residential"),
    CONTOUR("contour"),
    OVERVIEW_MAP("overview_map"),
    GENERATE_MBTILES_ONLINE("generate_mbtiles_online"),
    GENERATE_PMTILES("generate_pmtiles"),
    UPLOAD_S3("upload_s3"),
    UPLOAD_MAPTILER("upload_maptiler"),
    GENERATE_MAPSFORGE("generate_mapsforge"),
    GENERATE_MBTILES("generate_mbtiles"),
    POI_DB_V2("poi_db"),
    ADDRESS_POI_DB("address_poi_db"),
    COMPRESS("compress"),
    UPLOAD("upload"),
    CREATE_JSON("create_json"),
    STORE_GEO_DB("storeGeoDb"),
    UNKNOWN("unknown");
}