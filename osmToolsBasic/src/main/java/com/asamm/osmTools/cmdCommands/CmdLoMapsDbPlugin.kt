package com.asamm.osmTools.cmdCommands

import com.asamm.locus.features.loMaps.LoMapsDbConst.EntityType
import com.asamm.osmTools.config.AppConfig
import com.asamm.osmTools.generatorDb.input.definition.WriterPoiDefinition
import com.asamm.osmTools.generatorDb.plugin.DataPluginLoader
import com.asamm.osmTools.mapConfig.ItemMap
import com.asamm.osmTools.utils.Utils
import java.io.File
import java.util.Hashtable

class CmdLoMapsDbPlugin(val map: ItemMap) : Cmd(ExternalApp.OSMOSIS) {

    private val tempFilteredMapPath = AppConfig.config.temporaryDir.resolve("temp_map_simple.osm.pbf")

    val fileDb: File = map.pathAddressDb.toAbsolutePath().toFile()

    fun deleteTmpFile() {
        Utils.deleteFileQuietly(tempFilteredMapPath)
    }

    fun simplifyForPoi(definition: WriterPoiDefinition) {
        Utils.deleteFileQuietly(tempFilteredMapPath.toAbsolutePath())
        val filters = getPoiDbFilters(definition, EntityType.POIS) +
                getPoiDbFilters(definition, EntityType.WAYS)
        CmdOsmium().tagFilter(map.pathSource, tempFilteredMapPath.toAbsolutePath(), filters)
    }

    fun simplifyForAddress() {
        val filters = listOf(
            "wr/type=associatedStreet,street",
            "addr:housename",
            "addr:housenumber",
            "wr/addr:interpolation",
            "addr:street2",
            "addr:street",
            "address:house",
            "wr/address:type",
            "wr/boundary",
            "wr/highway",
            "highway=*",
            "place"
        )
        Utils.deleteFileQuietly(tempFilteredMapPath.toAbsolutePath())
        CmdOsmium().tagFilter(map.pathSource, tempFilteredMapPath.toAbsolutePath(), filters)
    }

    fun generatePoiDb() {
        osmosisBuilder()
            .readPbf(tempFilteredMapPath.toAbsolutePath().toString())
            .add("--${DataPluginLoader.PLUGIN_LOMAPS_DB}")
            .add("-type=poi")
            .add("-fileDb=${map.pathAddressPoiDb.toAbsolutePath()}")
            .add("-fileConfig=${AppConfig.config.poiAddressConfig.poiDbXml.toAbsolutePath()}")
            .execute()
    }

    fun generateAddressDb() {
        val sizeMb = (tempFilteredMapPath.toAbsolutePath().toFile().length() / 1024L / 1024L).toInt()
        val containerType = if (sizeMb <= 450) "ram" else "hdd"
        val mapId = map.countryName?.let { map.id?.takeIf { it.isNotEmpty() } ?: map.name }

        osmosisBuilder()
            .readPbf(tempFilteredMapPath.toAbsolutePath().toString())
            .add("--${DataPluginLoader.PLUGIN_LOMAPS_DB}")
            .add("-type=address")
            .add("-dataContainerType=$containerType")
            .addNotBlank(mapId?.let { "-mapId=$it" })
            .add("-fileDb=$fileDb")
            .add("-fileConfig=${AppConfig.config.poiAddressConfig.addressDbXml.toAbsolutePath()}")
            .add("-fileDataGeom=${map.pathJsonPolygon.toAbsolutePath()}")
            .add("-fileCountryGeom=${map.pathCountryBoundaryGeoJson.toAbsolutePath()}")
            .execute()
    }

    fun getPoiDbFilters(definition: WriterPoiDefinition, type: EntityType): List<String> {
        val merged = Hashtable<String, String>()
        for (dbDef in definition.rootSubContainers) {
            if (!dbDef.isValidType(type)) continue
            val existing = merged[dbDef.key]
            merged[dbDef.key] = if (existing == null) {
                dbDef.value.replace("|", ",")
            } else {
                "$existing,${dbDef.value.replace("|", ",")}"
            }
        }
        val prefix = when (type) {
            EntityType.POIS     -> "n/"
            EntityType.WAYS     -> "w/"
            EntityType.RELATION -> "r/"
            EntityType.UNKNOWN  -> ""
        }
        return merged.map { (key, value) -> "$prefix$key=$value" }
    }
}