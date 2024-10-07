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
    
    ItemMap map;
    
    public CmdOgr(ItemMap map) {
        super(ExternalApp.NO_EXTERNAL_APP);
        
        this.map = map;
        
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

        addCommand(Double.toString(map.getBoundary().getMinLon()));
        addCommand(Double.toString(map.getBoundary().getMinLat()));
        addCommand(Double.toString(map.getBoundary().getMaxLon()));
        addCommand(Double.toString(map.getBoundary().getMaxLat()));
        addCommand(map.getPathShp());
        addCommand(Parameters.getCoastlineShpFile());
        addCommand("-skipfailures");
    }
}
