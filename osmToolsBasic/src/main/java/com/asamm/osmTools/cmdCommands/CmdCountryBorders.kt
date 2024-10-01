package com.asamm.osmTools.cmdCommands

import com.asamm.osmTools.generatorDb.plugin.ConfigurationCountry
import com.asamm.osmTools.generatorDb.plugin.DataPluginLoader
import com.asamm.osmTools.mapConfig.ItemMap
import java.io.File
import java.io.IOException

/**
 * Created by voldapet on 2016-03-15 .
 */
class CmdCountryBorders(sourceItem: ItemMap, var storageType: ConfigurationCountry.StorageType) :
    Cmd(sourceItem, ExternalApp.OSMOSIS), CmdOsmosis {
    /**
     * File to filter source item
     */
    val filteredTempMap: File = File(sourceItem.pathSource + "_tmp_border")

    /**
     * Delete tmop file where are store result of filtering of admin boundaries
     */
    fun deleteTmpFile() {
        filteredTempMap.delete()
    }

    /**
     * Filter source data to use only boundaries elements
     *
     * @throws IOException
     */
    @Throws(IOException::class)
    fun addTaskFilter() {
        addReadSource(map.pathSource)

        addCommand("--tf")
        addCommand("reject-relations")
        addCommand("--tf")
        addCommand("reject-ways")
        addCommand("--tf")
        addCommand("accept-nodes")
        addCommand("place=continent")

        addCommand("outPipe.0=Nodes")

        // add second task
        addReadSource(map.pathSource)
        addCommand("--tf")
        addCommand("reject-relations")
        addCommand("--tf")
        addCommand("accept-ways")

        var cmdAdminLevel = ""
        for (i in COUNTRY_BOUND_ADMIN_LEVELS.indices) {
            if (i == 0) {
                cmdAdminLevel = "admin_level=" + COUNTRY_BOUND_ADMIN_LEVELS[i]
            } else {
                cmdAdminLevel += "," + COUNTRY_BOUND_ADMIN_LEVELS[i]
            }
        }
        addCommand(cmdAdminLevel)
        addCommand("border_type=territorial")

        addCommand("--used-node")
        addCommand("outPipe.0=Ways")

        // add third task
        addReadSource(map.pathSource)
        addCommand("--tf")
        addCommand("accept-relations")
        cmdAdminLevel = ""
        for (i in COUNTRY_BOUND_ADMIN_LEVELS.indices) {
            if (i == 0) {
                cmdAdminLevel = "admin_level=" + COUNTRY_BOUND_ADMIN_LEVELS[i]
            } else {
                cmdAdminLevel += "," + COUNTRY_BOUND_ADMIN_LEVELS[i]
            }
        }
        addCommand(cmdAdminLevel)
        addCommand("border_type=territorial")

        addCommand("--used-way")
        addCommand("--used-node")
        addCommand("outPipe.0=Relations")

        // add merge task
        // add merge task
        addCommand("--merge")
        addCommand("inPipe.0=Nodes")
        addCommand("inPipe.1=Ways")
        addCommand("outPipe.0=NodesWays")
        addCommand("--merge")
        addCommand("inPipe.0=Relations")
        addCommand("inPipe.1=NodesWays")

        // add export path
        addWritePbf(filteredTempMap.absolutePath, true)
    }


    /**
     * Prepare command line for generation precise country boundary
     */
    fun addGeneratorCountryBoundary() {
        //addReadPbf(mFileTempMap.getAbsolutePath());

        addReadPbf(filteredTempMap.absolutePath)

        addCommand("--" + DataPluginLoader.PLUGIN_LOMAPS_DB)
        addCommand("-type=country")

        if (storageType == ConfigurationCountry.StorageType.GEOJSON) {
            addCommand("-storageType=geojson")
        } else if (storageType == ConfigurationCountry.StorageType.STORE_REGION_DB) {
            addCommand("-storageType=geodatabase")
        }
    }


    /**
     * Add all maps for which will be generated boundaries from source item
     *
     * @param maps
     */
    fun addCountries(maps: List<ItemMap>) {
        val sb = StringBuilder("-countries=")
        var i = 0
        val size = maps.size
        while (i < size) {
            val map = maps[i]

            if (i == 0) {
                sb.append(map.countryName).append(",")
            } else {
                sb.append(",")
                sb.append(map.countryName).append(",")
            }

            sb.append(map.parentRegionId).append(",")
            sb.append(map.regionId)

            // for geo database is not needed to define path to geojson file
            if (storageType == ConfigurationCountry.StorageType.GEOJSON) {
                sb.append(",")
                sb.append(map.pathCountryBoundaryGeoJson)
            } else if (storageType == ConfigurationCountry.StorageType.STORE_REGION_DB) {
                sb.append(",")

                val regionCode =
                    if ((map.regionCode.length == 0)) ConfigurationCountry.COUNTRY_CODE_NOT_DEFINED else map.regionCode

                sb.append(regionCode)
            }
            i++
        }

        addCommand(sb.toString())
    }

    companion object {
        private val COUNTRY_BOUND_ADMIN_LEVELS = intArrayOf(2, 3, 4)
    }
}

