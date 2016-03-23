package com.asamm.osmTools.cmdCommands;

import com.asamm.osmTools.generatorDb.plugin.DataPluginLoader;
import com.asamm.osmTools.mapConfig.ItemMap;
import com.asamm.osmTools.utils.Consts;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Created by voldapet on 2016-03-15 .
 */
public class CmdCountryBorders extends  Cmd{

    private static final int COUNTRY_BOUND_ADMIN_LEVEL = 2;

    /**
     * File to filter source item
     */
    private File mFileTempMap;

    public CmdCountryBorders (ItemMap sourceItem){

        super(sourceItem, ExternalApp.OSMOSIS);

        mFileTempMap = new File(Consts.DIR_TMP, "temp_map_country.osm.pbf");
    }

    /**
     * Delete tmop file where are store result of filtering of admin boundaries
     */
    public void deleteTmpFile() {
        mFileTempMap.delete();
    }

    /**
     * Filter source data to use only boundaries elements
     *
     * @throws IOException
     */
    public void addTaskFilter() throws IOException {

        addReadSource();
        addCommand("--tf");
        addCommand("reject-relations");
        addCommand("--tf");
        addCommand("accept-ways");
        addCommand("admin_level=" + COUNTRY_BOUND_ADMIN_LEVEL);
        addCommand("--used-node");
        addCommand("outPipe.0=Ways");

        // add second task
        addReadSource();
        addCommand("--tf");
        addCommand("accept-relations");
        addCommand("admin_level=" + COUNTRY_BOUND_ADMIN_LEVEL);
        addCommand("--used-way");
        addCommand("--used-node");
        addCommand("outPipe.0=Relations");

        // add merge task
        addCommand("--merge");
        addCommand("inPipe.0=Ways");
        addCommand("inPipe.1=Relations");

        // add export path
        addWritePbf(mFileTempMap.getAbsolutePath(), true);
    }


    /**
     * Prepare commnad line for generation precise country boundary
     */
    public void addGeneratorCountryBoundary() {

        //addReadPbf(mFileTempMap.getAbsolutePath());
        addReadPbf(mFileTempMap.getAbsolutePath());

        addCommand("--" + DataPluginLoader.PLUGIN_COMMAND);
        addCommand("-type=country");
        // TODO it's needed to defined admin level? Probably all countries are in level=2
        addCommand("-countryAdminLevel=" + COUNTRY_BOUND_ADMIN_LEVEL);
    }

    /**
     * Add map for which will be generated boundaries from source
     *
     * @param maps
     */
    public void addCountries (List<ItemMap> maps){

        StringBuilder sb = new StringBuilder("-countries=");
        for (int i=0, size = maps.size(); i < size; i++ ){
            ItemMap map = maps.get(i);

            if (i == 0){
                sb.append(map.getCountryName()).append(",");
            }
            else {
                sb.append(",").append(map.getCountryName()).append(",");
            }
            sb.append(map.getPathCountryBoundaryGeoJson());
        }

        addCommand(sb.toString());
    }
    
}

