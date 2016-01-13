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
public class CmdShp2osm extends Cmd{

    String input;
    String output;

    public CmdShp2osm(ItemMap map, String input, String output) {
        super(map, ExternalApp.NO_EXTERNAL_APP);
        this.input = input;
        this.output = output;
        
        // test if python is installed in defined dir
        if (!new File(Parameters.getPython2Dir()).exists()){
            throw new IllegalArgumentException ("Python in location" + Parameters.getPython2Dir() +"  does not exist!");
        }
        
        //test if shp2osm.py scripot exist
        if (!new File (Parameters.getShp2osmDir()).exists()){
            throw new IllegalArgumentException ("Shp2Osm script in location" + Parameters.getShp2osmDir() +"  does not exist!");
        }
    }
    
    public void createCmd(){
        addCommand(Parameters.getPython2Dir());
        addCommand(Parameters.getShp2osmDir());
        addCommand(input);
        addCommand("--obj-count");
        addCommand("100000000");
        addCommand("--output-location");
        addCommand(output);
    }
}
