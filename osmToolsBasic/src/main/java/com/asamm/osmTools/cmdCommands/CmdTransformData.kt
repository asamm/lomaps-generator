package com.asamm.osmTools.cmdCommands

import com.asamm.osmTools.generatorDb.plugin.DataPluginLoader
import com.asamm.osmTools.mapConfig.ItemMap

/**
 * Transforms OSM data — e.g. generates city residential areas — using the
 * Osmosis data-transform plugin. The result is written to [ItemMap.pathTranform].
 */
class CmdTransformData(val map: ItemMap) : Cmd(ExternalApp.OSMOSIS) {

    fun addDataTransform() {
        osmosisBuilder()
            .readPbf(map.pathSource.toString())
            .add("--${DataPluginLoader.PLUGIN_DATA_TRANSFORM}")
            .sort()
            .writePbf(map.pathTranform.toString(), omitMetadata = true)
            .execute()
    }
}