/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.asamm.osmTools.cmdCommands;

import com.asamm.osmTools.Parameters;
import com.asamm.osmTools.mapConfig.ItemMap;

import java.io.File;
import java.io.IOException;

/**
 *
 * @author volda
 */
public class CmdContour extends Cmd{
    
    public CmdContour(ItemMap map) {
        super(map, ExternalApp.NO_EXTERNAL_APP);
        // check phyghtmap location
        if (!new File(Parameters.phyghtDir).exists()){
            throw new IllegalArgumentException("Phyghtmap in location: " +
                    Parameters.phyghtDir + " does not exist");
        }

        // check location of polygon
        if (!new File(map.getPathPolygon()).exists()){
            throw new IllegalArgumentException("Polygon file: " + map.getPathPolygon() + " does not exist");
        }

        // finally set parameters
        addCommand(Parameters.phyghtDir);
    }
    
    public void createCmd() throws IOException {
        // prepare directory
        prepareDirectory(getMap().getPathContour());

        // add required commands
        addCommand("--polygon=" + getMap().getPathPolygon());
        addCommand("--step=" + Parameters.contourStep);
        addCommand("--no-zero-contour");
        addCommand("--output-prefix=" + getMap().getPathContour());
        //addCommand("--source=view1,srtm1,view3,srtm3");
        //addCommand("--source=view1,view3,srtm1,srtm3");
        addCommand("--source=view3,view1,srtm1,srtm3");
        addCommand("--line-cat=200,100");
        addCommand("--start-node-id=" + Parameters.contourNodeId);
        addCommand("--start-way-id=" + Parameters.contourWayId);
        addCommand("--write-timestamp");
        addCommand("--max-nodes-per-tile=0");
        addCommand("--hgtdir=" + Parameters.getHgtDir());
        addCommand("-j 7"); //number of paralel jobs (POSIX only)
        if (Parameters.mapOutputFormat.endsWith("pbf")){
            //
            addCommand("--pbf");
        }
    }
    
    public void rename(){
        File ccDir = new File(getMap().getPathContour()).getParentFile();
        File files[];
        if (!ccDir.exists()){
            throw new IllegalArgumentException("Try to rename contour "+ getMap().getPathContour() +
                    ". Contour data dir does not exist");
        }
        files = ccDir.listFiles();
        
        for (int i = 0; i < files.length; i++){
            File file = files[i];
            if (file.isFile() && file.getName().startsWith(getMap().getName())){
                file.renameTo(new File(getMap().getPathContour()));
                break;
            }
        }
    }
    
    
}
