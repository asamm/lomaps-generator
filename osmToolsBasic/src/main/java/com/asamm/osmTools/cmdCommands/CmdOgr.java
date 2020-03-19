/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.asamm.osmTools.cmdCommands;

import com.asamm.osmTools.Parameters;
import com.asamm.osmTools.mapConfig.ItemMap;

import java.io.File;

/**
 *
 * @author volda
 */
public class CmdOgr extends Cmd{

    public CmdOgr(ItemMap map) {
        super(map, ExternalApp.NO_EXTERNAL_APP);

        // test if shpfile for extracting exist
        //this.map  =  map;
        if (!new File(Parameters.getCoastlineShpFile()).exists()){
            throw  new IllegalArgumentException("Shapefile with world polygons "
                    + Parameters.getCoastlineShpFile() +" does not exist");
        }

        // add base parameter
        addCommand(Parameters.getOgr2ogr());
    }
    
    public void createCmd(){
        //ogr2ogr -clipsrc 14.0 35.7 14.66 36.2   malta.shp water_polygons.shp
        addCommand("-clipsrc");

        addCommand(Double.toString(getMap().getBoundary().getMinLon()));
        addCommand(Double.toString(getMap().getBoundary().getMinLat()));
        addCommand(Double.toString(getMap().getBoundary().getMaxLon()));
        addCommand(Double.toString(getMap().getBoundary().getMaxLat()));
        addCommand(getMap().getPathShp());
        addCommand(Parameters.getCoastlineShpFile());
        addCommand("-skipfailures");
    }
}
