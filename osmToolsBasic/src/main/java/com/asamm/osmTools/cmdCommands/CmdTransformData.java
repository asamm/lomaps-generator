package com.asamm.osmTools.cmdCommands;

import com.asamm.osmTools.generatorDb.plugin.DataPluginLoader;
import com.asamm.osmTools.mapConfig.ItemMap;

import java.io.File;
import java.io.IOException;

/**
 * Created by voldapet on 16/1/2017.
 * Set cdm tools to start generation of customized data - eq. city residential areas
 */
public class CmdTransformData extends Cmd {

    private static final String TAG = CmdTransformData.class.getSimpleName();

     /**
     * Definition where PBF file with customized OSM data will be created
     */
    private File mFileTransformedData;

    public CmdTransformData(ItemMap map) {
        super(map, ExternalApp.OSMOSIS);
    }

    public void addDataTransform() throws IOException {

        // read extracted pbf
        addReadPbf(getMap().getPathSource());
        // set plugin to create city residential areas
        addCommand("--" + DataPluginLoader.PLUGIN_DATA_TRANSFORM);

        addSort();

        addWritePbf(getMap().getPathTranform(), true);
        //addWriteXml(getMap().getPathTranform(),true);
    }
}
