package com.asamm.osmTools.cmdCommands;

import com.asamm.osmTools.Parameters;
import com.asamm.osmTools.generatorDb.plugin.DataPluginLoader;
import com.asamm.osmTools.mapConfig.ItemMap;
import com.asamm.osmTools.utils.Consts;

import java.io.File;
import java.io.IOException;

/**
 * Created by voldapet on 2016-09-16 .
 * Create cmd execute to run osmosis plugin for generation content of Store GeoCoding database
 */
public class CmdStoreGeo extends Cmd {

    /**
     * File where filtered data will be stored
     */
    private File mFileTempMap;


    public CmdStoreGeo(ItemMap map) {

        super(map, ExternalApp.OSMOSIS);

        // set parameters
        mFileTempMap = new File(Consts.DIR_TMP, "temp_map_simple.osm.pbf");
    }

    public void addTaskSimplifyForStoreGeo () throws IOException {

        addReadSource();
        addCommand("--tf");
        addCommand("reject-relations");
        addCommand("--tf");
        addCommand("reject-ways");
        addCommand("--tf");
        addCommand("accept-nodes");
        addCommand("place=*");

        addCommand("outPipe.0=Nodes");

        // add second task
        addReadSource();
        addCommand("--tf");
        addCommand("reject-relations");
        addCommand("--tf");
        addCommand("accept-ways");
        addCommand("boundary=*");
        addCommand("place=*");

//        addCommand("landuse=residential");

        addCommand("--used-node");
        // addCommand("idTrackerType=Dynamic");

        addCommand("outPipe.0=Ways");

        // add third task
        addReadSource();
        addCommand("--tf");
        addCommand("accept-relations");
        addCommand("boundary=*");
        addCommand("place=*");

        addCommand("--used-way");
        addCommand("--used-node");
//        addCommand("idTrackerType=Dynamic");
        addCommand("outPipe.0=Relations");

        // add merge task
        addCommand("--merge");
        addCommand("inPipe.0=Nodes");
        addCommand("inPipe.1=Ways");
        addCommand("outPipe.0=NodesWays");
        addCommand("--merge");
        addCommand("inPipe.0=Relations");
        addCommand("inPipe.1=NodesWays");

        // add export path
        addWritePbf(mFileTempMap.getAbsolutePath(), true);
    }

    /**
     * Prepare cmd line to run osmosis for generation StoreGeoDatabase
     */
    public void addGeneratorStoreGeoCodingDb () {

        //addReadPbf(getMap().getPathSource());
        addReadPbf(mFileTempMap.getAbsolutePath());
        addCommand("--" + DataPluginLoader.PLUGIN_LOMAPS_DB);
        addCommand("-type=store_geocode");

        File file = new File(getMap().getPathSource());
        int size = (int) (file.length() / 1024L / 1024L);
        if (size <= 1000) {
            addCommand("-dataContainerType=ram");
        }
        else {
            addCommand("-dataContainerType=hdd");
        }

        // add map id if is not defined then use map name as id
        if (getMap().getCountryName() != null){
            String mapId = getMap().getId();
            if (mapId == null || mapId.length() == 0){
                mapId = getMap().getName();
            }
            addCommand("-mapId=" + mapId);
        }
        //addCommand("-countryName=" + getMap().getCountryName());
        addCommand("-fileConfig=" + Parameters.getConfigAddressPath());
        addCommand("-fileCountryGeom=" + getMap().getPathCountryBoundaryGeoJson());
    }

    /**
     * Delete tmp file where are store result of filtering
     */
    public void deleteTmpFile() {
        mFileTempMap.delete();
    }

}
