package com.asamm.osmTools.cmdCommands

import com.asamm.osmTools.generatorDb.plugin.ConfigurationCountry
import com.asamm.osmTools.generatorDb.plugin.DataPluginLoader
import com.asamm.osmTools.mapConfig.ItemMap
import java.io.File

class CmdCountryBorders(val map: ItemMap, val storageType: ConfigurationCountry.StorageType) :
    Cmd(ExternalApp.OSMOSIS) {

    /** Temporary file holding just the boundary-filtered data from [map]. */
    val filteredTempMap: File = File(map.pathSource.toString() + "_tmp_border")

    /**
     * Filter the source map down to boundary elements (admin levels 2/3/4) and
     * write the result to [filteredTempMap]. Previously: addTaskFilter() + execute().
     */
    fun filterBoundaries() {
        val adminLevels = COUNTRY_BOUND_ADMIN_LEVELS.joinToString(",") { it.toString() }
        val adminLevelCmd = "admin_level=$adminLevels"

        osmosisBuilder()
            // Pass 1 – continent nodes
            .readSource(map.pathSource)
            .add("--tf", "reject-relations")
            .add("--tf", "reject-ways")
            .add("--tf", "accept-nodes")
            .add("place=continent")
            .add("outPipe.0=Nodes")
            // Pass 2 – admin ways
            .readSource(map.pathSource)
            .add("--tf", "reject-relations")
            .add("--tf", "accept-ways")
            .add(adminLevelCmd)
            .add("border_type=territorial")
            .add("--used-node")
            .add("outPipe.0=Ways")
            // Pass 3 – admin relations
            .readSource(map.pathSource)
            .add("--tf", "accept-relations")
            .add(adminLevelCmd)
            .add("border_type=territorial")
            .add("--used-way")
            .add("--used-node")
            .add("outPipe.0=Relations")
            // Merge all three passes
            .add("--merge")
            .add("inPipe.0=Nodes", "inPipe.1=Ways", "outPipe.0=NodesWays")
            .add("--merge")
            .add("inPipe.0=Relations", "inPipe.1=NodesWays")
            .writePbf(filteredTempMap.absolutePath, omitMetadata = true)
            .execute()
    }

    /**
     * Run the country-boundary generator plugin over [filteredTempMap] for all [maps].
     * Previously: addGeneratorCountryBoundary() + addCountries(maps) + execute().
     */
    fun generateBoundaries(maps: List<ItemMap>) {
        val storageTypeArg = when (storageType) {
            ConfigurationCountry.StorageType.GEOJSON         -> "-storageType=geojson"
            ConfigurationCountry.StorageType.STORE_REGION_DB -> "-storageType=geodatabase"
        }

        osmosisBuilder()
            .readPbf(filteredTempMap.absolutePath)
            .add("--${DataPluginLoader.PLUGIN_LOMAPS_DB}")
            .add("-type=country")
            .addNotBlank(storageTypeArg)
            .add(buildCountriesArg(maps))
            .execute()
    }

    private fun buildCountriesArg(maps: List<ItemMap>): String {
        val sb = StringBuilder("-countries=")
        maps.forEachIndexed { index, m ->
            if (index > 0) sb.append(",")
            sb.append(m.countryName).append(",")
            sb.append(m.parentRegionId).append(",")
            sb.append(m.regionId)
            when (storageType) {
                ConfigurationCountry.StorageType.GEOJSON ->
                    sb.append(",").append(m.pathCountryBoundaryGeoJson)
                ConfigurationCountry.StorageType.STORE_REGION_DB -> {
                    val code = m.regionCode.ifEmpty { ConfigurationCountry.COUNTRY_CODE_NOT_DEFINED }
                    sb.append(",").append(code)
                }
            }
        }
        return sb.toString()
    }

    companion object {
        private val COUNTRY_BOUND_ADMIN_LEVELS = intArrayOf(2, 3, 4)
    }
}