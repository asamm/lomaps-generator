package com.asamm.osmTools.cmdCommands

import com.asamm.locus.features.loMaps.LoMapsDbConst.EntityType
import com.asamm.osmTools.config.AppConfig
import com.asamm.osmTools.generatorDb.input.definition.WriterPoiDefinition
import com.asamm.osmTools.generatorDb.plugin.DataPluginLoader
import com.asamm.osmTools.mapConfig.ItemMap
import com.asamm.osmTools.utils.Logger
import com.asamm.osmTools.utils.Utils
import org.apache.commons.io.FileUtils
import java.io.File
import java.util.*

/**
 * Created by menion on 28.7.14.
 */
class CmdLoMapsDbPlugin(val map: ItemMap) : Cmd(ExternalApp.OSMOSIS), CmdOsmosis {

    private val TAG: String = CmdLoMapsDbPlugin::class.java.simpleName

    // set parameters
    private val tempFilteredMapPath = AppConfig.config.temporaryDir.resolve("temp_map_simple.osm.pbf")

    /**
     * Definition where will be poiDb created
     */
    val fileDb: File = map.pathAddressPoiDb.toAbsolutePath().toFile()


    /**
     * Used for repeated run of the command when is needed to delete file create in previous run
     */
    private val mFileToCreate = fileDb

    /**
     * Delete tmop file where are store result of filtering for pois or address
     */
    fun deleteTmpFile() {
        Utils.deleteFileQuietly(tempFilteredMapPath)
    }

    fun simplifyForPoi(definition: WriterPoiDefinition) {
        // delete temp file if exists
        Utils.deleteFileQuietly(tempFilteredMapPath.toAbsolutePath())

        // prepare filters for osmium filter tag
        var filters = getPoiDbFilters(definition, EntityType.POIS)
        filters.addAll(getPoiDbFilters(definition, EntityType.WAYS))

        CmdOsmium().tagFilter(
            map.pathSource,
            tempFilteredMapPath.toAbsolutePath(),
            filters
        )
    }

    fun simplifyForAddress() {

        var filters = mutableListOf<String>(
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
            "place",
        )

        // delete temp file if exists
        Utils.deleteFileQuietly(tempFilteredMapPath.toAbsolutePath())

        // filter source osm file
        CmdOsmium().tagFilter(
            map.pathSource,
            tempFilteredMapPath.toAbsolutePath(),
            filters
        )
    }


    fun generatePoiDb() {
        FileUtils.deleteQuietly(fileDb)

        addReadPbf(tempFilteredMapPath.toAbsolutePath().toString())
        addCommand("--" + DataPluginLoader.PLUGIN_LOMAPS_DB)
        addCommand("-type=poi")
        addCommand("-fileDb=" + fileDb)
        addCommand("-fileConfig=" + AppConfig.config.poiAddressConfig.poiDbXml.toAbsolutePath())

        Logger.i(TAG, "Command: " + getCmdLine())
        execute()
        reset()
    }

    /**
     * Prepare cmd line to run osmosis for generation post address database
     */
    fun generateAddressDb() {

        addReadPbf(tempFilteredMapPath.toAbsolutePath().toString())

        addCommand("--" + DataPluginLoader.PLUGIN_LOMAPS_DB)
        addCommand("-type=address")

        val size = (tempFilteredMapPath.toAbsolutePath().toFile().length() / 1024L / 1024L).toInt()
        if (size <= 250) {
            addCommand("-dataContainerType=ram")
        } else {
            addCommand("-dataContainerType=hdd")
        }

        // add map id if is not defined then use map name as id
        if (map.countryName != null) {
            var mapId = map.id
            if (mapId == null || mapId.length == 0) {
                mapId = map.name
            }
            addCommand("-mapId=$mapId")
        }
        //addCommand("-countryName=" + getMap().getCountryName());
        addCommand("-fileDb=" + fileDb)
        addCommand("-fileConfig=" + AppConfig.config.poiAddressConfig.addressDbXml.toAbsolutePath())
        addCommand("-fileDataGeom=" + map.pathJsonPolygon.toAbsolutePath())
        addCommand("-fileCountryGeom=" + map.pathCountryBoundaryGeoJson.toAbsolutePath())

        Logger.i(TAG, "Command: " + getCmdLine())
        execute()
        reset()
    }


    /**
     * Get list of filters to filter OSM file to contains only data for POI DB
     */
    fun getPoiDbFilters(definition: WriterPoiDefinition, type: EntityType) : MutableList<String> {

        val nodes = definition.rootSubContainers
        val nodesPrep = Hashtable<String, String>()
        for (dbDef in nodes) {
            // check type
            if (!dbDef.isValidType(type)) {
                continue
            }

            // add to data
            if ( !nodesPrep.containsKey(dbDef.key)) {
                nodesPrep[dbDef.key] = dbDef.value.replace("|", ",")
            } else {
                nodesPrep[dbDef.key] = nodesPrep[dbDef.key] + "," + dbDef.value.replace("|", ",")
            }
        }

        val typePrefix = when (type) {
            EntityType.POIS -> "n/"
            EntityType.WAYS -> "w/"
            EntityType.RELATION -> "r/"
            EntityType.UNKNOWN -> ""
        }

        val filters = nodesPrep.map { (key, value) -> "$typePrefix$key=$value" }.toMutableList()

        return filters
    }
}
