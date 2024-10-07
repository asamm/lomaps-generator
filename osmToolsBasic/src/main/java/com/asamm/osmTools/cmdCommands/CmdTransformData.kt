package com.asamm.osmTools.cmdCommands

import com.asamm.osmTools.generatorDb.plugin.DataPluginLoader
import com.asamm.osmTools.mapConfig.ItemMap
import java.io.File
import java.io.IOException

/**
 * Created by voldapet on 16/1/2017.
 * Set cdm tools to start generation of customized data - eq. city residential areas
 */
class CmdTransformData(val map: ItemMap) : Cmd(ExternalApp.OSMOSIS), CmdOsmosis {
    /**
     * Definition where PBF file with customized OSM data will be created
     */
    private val mFileTransformedData: File? = null

    @Throws(IOException::class)
    fun addDataTransform() {
        // read extracted pbf

        addReadPbf(map.pathSource)
        // set plugin to create city residential areas
        addCommand("--" + DataPluginLoader.PLUGIN_DATA_TRANSFORM)

        addSort()

        addWritePbf(map.pathTranform, true)
        //addWriteXml(getMap().getPathTranform(),true);
    }

    companion object {
        private val TAG: String = CmdTransformData::class.java.simpleName
    }
}
