package com.asamm.osmTools.cmdCommands

import com.asamm.osmTools.generatorDb.plugin.DataPluginLoader
import com.asamm.osmTools.mapConfig.ItemMap

class CmdResidential(val map: ItemMap) : Cmd(ExternalApp.OSMOSIS) {

    fun execute() {
        osmosisBuilder()
            .readPbf(map.pathSource.toString())
            .add("--${DataPluginLoader.PLUGIN_DATA_RESIDENTIAL}")
            .sort()
            .writePbf(map.pathResidential.toString(), omitMetadata = true)
            .execute()
    }
}