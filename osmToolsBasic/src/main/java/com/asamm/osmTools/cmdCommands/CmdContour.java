/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.asamm.osmTools.cmdCommands;

import com.asamm.osmTools.Parameters;
import com.asamm.osmTools.mapConfig.ContourUnit;
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

        // check location of polygon
        if (!new File(map.getPathPolygon()).exists()){
            throw new IllegalArgumentException("Polygon file: " + map.getPathPolygon() + " does not exist");
        }

        // finally, set parameters
        addCommand(Parameters.phyghtDir);
    }
    
    public void createCmd() throws IOException {
        // prepare directory
        prepareDirectory(getMap().getPathContour());

        // add required commands
        addCommand("--polygon=" + getMap().getPathPolygon());
        if (getMap().getContourUnit() == ContourUnit.FEET){
            addCommand("--feet");
        }
        addCommand("--step=" + getMap().getContourStep());
        addCommand("--no-zero-contour");
        addCommand("--output-prefix=" + getMap().getPathContour());
        addCommand("--source=" + getMap().getContourSource()); // addCommand("--source=view3,view1");
        addCommand(getContourCatCmd());
        addCommand("--start-node-id=" + Parameters.contourNodeId);
        addCommand("--start-way-id=" + Parameters.contourWayId);
        addCommand("--write-timestamp");
        // add simplification of contour lines
        // for SRTM1 data, some value between 0.00003 and 0.00008 seems reasonable
        addCommand("--simplifyContoursEpsilon=0.00007");
        addCommand("--max-nodes-per-tile=0");
        addCommand("--hgtdir=" + Parameters.getHgtDir());

        int cores = Runtime.getRuntime().availableProcessors();
        addCommand("-j " + String.valueOf(cores)); //number of paralel jobs (POSIX only)
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

    /**
     * Get command for major and minor category of contour lines.
     * Decide based on type of contour unit (meters or feet)
     */
    private String getContourCatCmd(){
        if (getMap().getContourUnit() == ContourUnit.FEET){
            return "--line-cat=400,200";
        }
        // for Meter and as fallback
        return "--line-cat=100,50";
    }
}
