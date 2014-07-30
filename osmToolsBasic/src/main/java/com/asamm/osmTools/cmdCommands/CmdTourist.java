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
public class CmdTourist extends Cmd {

    public CmdTourist(ItemMap map) {
        super(map, ExternalApp.OSMOSIS);
        addCommand("-q");
    }
    
    public void createCmd() throws IOException {
        addReadPbf(getMap().getPathSource());
        addBoundingPolygon(getMap());
        addBuffer();
        addWriteXml("-");
    }
}
