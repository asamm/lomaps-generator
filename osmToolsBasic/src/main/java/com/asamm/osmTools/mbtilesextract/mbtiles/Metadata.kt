package com.asamm.osmTools.mbtilesextract.mbtiles

class Metadata( ) {

    companion object {

        // defined metadata keys

        const val NAME_KEY = "name"
        const val DESCRIPTION_KEY = "description"
        const val TYPE_KEY = "type"
        const val VERSION_KEY = "version"
        const val FORMAT_KEY = "format"
        const val ATTRIBUTION_KEY = "attribution"
        const val MAXZOOM_KEY = "maxzoom"
        const val MINZOOM_KEY = "minzoom"
        const val CENTER_KEY = "center"
        const val BOUNDS_KEY = "bounds"
        const val COMPRESSION_KEY = "compression"
        const val JSON_KEY = "json"

        // some const values
        const val FORMAT_VALUE_PBF = "pbf"
        const val TYPE_VALUE_BASELAYER = "baselayer"


        // ASAMM values
        const val LOMAPS_GEOM_KEY = "lomaps.geom"

    }

    // create map <string, string> for metadata
    val data: MutableMap<String, String> = mutableMapOf()

    fun getValue(key: String): String? {
        return data[key]
    }

    fun setValues(key: String, value: String) {
        data[key] = value
    }

    fun setValue(key: String, value: String) {
        data[key] = value
    }
}