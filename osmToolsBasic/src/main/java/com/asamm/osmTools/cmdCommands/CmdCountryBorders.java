package com.asamm.osmTools.cmdCommands;

import com.asamm.osmTools.generatorDb.plugin.ConfigurationCountry;
import com.asamm.osmTools.generatorDb.plugin.DataPluginLoader;
import com.asamm.osmTools.mapConfig.ItemMap;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Created by voldapet on 2016-03-15 .
 */
public class CmdCountryBorders extends  Cmd{



    private static final int COUNTRY_BOUND_ADMIN_LEVELS[] = new int[] {2,3,4};



    /**
     * File to filter source item
     */
    private File mFilteredTempMap;

    ConfigurationCountry.StorageType storageType;

    public CmdCountryBorders (ItemMap sourceItem, ConfigurationCountry.StorageType storageType){

        super(sourceItem, ExternalApp.OSMOSIS);

        this.storageType = storageType;


        mFilteredTempMap = new File(sourceItem.getPathSource() + "_tmp_border");
    }

    /**
     * Delete tmop file where are store result of filtering of admin boundaries
     */
    public void deleteTmpFile() {
        mFilteredTempMap.delete();
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
        addCommand("reject-ways");
        addCommand("--tf");
        addCommand("accept-nodes");
        addCommand("place=continent");

        addCommand("outPipe.0=Nodes");

        // add second task
        addReadSource();
        addCommand("--tf");
        addCommand("reject-relations");
        addCommand("--tf");
        addCommand("accept-ways");

        String cmdAdminLevel = "";
        for (int i=0; i < COUNTRY_BOUND_ADMIN_LEVELS.length; i++ ){
            if (i == 0){
                cmdAdminLevel = "admin_level=" + COUNTRY_BOUND_ADMIN_LEVELS[i];
            }
            else {
                cmdAdminLevel += "," + COUNTRY_BOUND_ADMIN_LEVELS[i];
            }
        }
        addCommand(cmdAdminLevel);
        addCommand("border_type=territorial");

        addCommand("--used-node");
        addCommand("outPipe.0=Ways");

        // add third task
        addReadSource();
        addCommand("--tf");
        addCommand("accept-relations");
        cmdAdminLevel = "";
        for (int i=0; i < COUNTRY_BOUND_ADMIN_LEVELS.length; i++ ){
            if (i == 0){
                cmdAdminLevel = "admin_level=" + COUNTRY_BOUND_ADMIN_LEVELS[i];
            }
            else {
                cmdAdminLevel += "," + COUNTRY_BOUND_ADMIN_LEVELS[i];
            }
        }
        addCommand(cmdAdminLevel);
        addCommand("border_type=territorial");

        addCommand("--used-way");
        addCommand("--used-node");
        addCommand("outPipe.0=Relations");

        // add merge task
        // add merge task
        addCommand("--merge");
        addCommand("inPipe.0=Nodes");
        addCommand("inPipe.1=Ways");
        addCommand("outPipe.0=NodesWays");
        addCommand("--merge");
        addCommand("inPipe.0=Relations");
        addCommand("inPipe.1=NodesWays");

        // add export path
        addWritePbf(mFilteredTempMap.getAbsolutePath(), true);
    }


    /**
     * Prepare command line for generation precise country boundary
     */
    public void addGeneratorCountryBoundary() {

        //addReadPbf(mFileTempMap.getAbsolutePath());
        addReadPbf(mFilteredTempMap.getAbsolutePath());

        addCommand("--" + DataPluginLoader.PLUGIN_LOMAPS_DB);
        addCommand("-type=country");

        if (storageType == ConfigurationCountry.StorageType.GEOJSON){
            addCommand("-storageType=geojson");
        }
        else if (storageType == ConfigurationCountry.StorageType.STORE_REGION_DB){
            addCommand("-storageType=geodatabase");
        }
    }

    /**
     * Add all maps for which will be generated boundaries from source item
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
                sb.append(",");
                sb.append(map.getCountryName()).append(",");
            }

            sb.append(map.getParentRegionId()).append(",");
            sb.append(map.getRegionId());

            // for geo database is not needed to define path to geojson file
            if (storageType == ConfigurationCountry.StorageType.GEOJSON){
                sb.append(",");
                sb.append(map.getPathCountryBoundaryGeoJson());
            }
            else if (storageType == ConfigurationCountry.StorageType.STORE_REGION_DB){
                sb.append(",");

                String regionCode = (map.getRegionCode().length() == 0) ?
                            ConfigurationCountry.COUNTRY_CODE_NOT_DEFINED : map.getRegionCode();

                sb.append(regionCode);
            }
        }

        addCommand(sb.toString());
    }

    public File getFilteredTempMap() {
        return mFilteredTempMap;
    }
}

