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

        //test if shp2osm.py scripot exist
        if (!new File (Parameters.getShp2osmDir()).exists()){
            throw new IllegalArgumentException ("Shp2Osm script in location" + Parameters.getShp2osmDir() +"  does not exist!");
        }
    }
    
    public void createCmd(){
        addCommand(Parameters.getPythonDir());
        addCommand(Parameters.getShp2osmDir());
        addCommand("--id");
        addCommand(String.valueOf(Parameters.costlineBorderId));
        addCommand("--output");
        addCommand(output);
        addCommand(input);
    }
}
