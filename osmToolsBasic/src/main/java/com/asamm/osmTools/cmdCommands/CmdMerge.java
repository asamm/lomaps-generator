/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.asamm.osmTools.cmdCommands;

import com.asamm.osmTools.mapConfig.ItemMap;

import java.io.IOException;

/**
 *
 * @author volda
 */
public class CmdMerge extends Cmd {

    public CmdMerge (ItemMap map){
        super(map, ExternalApp.OSMOSIS);

        // check parameters
        checkFileLocalPath();
    }
    
    public void createCmd() throws IOException {
        addReadPbf(getMap().getPathSource());
        addReadPbf(getMap().getPathContour());
        addBuffer();
        addMerge();
        addWritePbf(getMap().getPathMerge(), true);
    }
    
    public void createSeaCmd(String tmpCoastPath, String tmpBorderPath) throws IOException {
        addReadXml(tmpCoastPath);
        addSort();
        addReadXml(tmpBorderPath);
        addSort();
        addBuffer();
        addMerge();
        addWritePbf(getMap().getPathCoastline(), true);
    }
    
    public void addMerge (){
        addCommand("--merge");
    }
}
