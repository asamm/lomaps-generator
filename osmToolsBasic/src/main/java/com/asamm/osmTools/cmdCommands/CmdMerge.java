/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.asamm.osmTools.cmdCommands;

import com.asamm.osmTools.Parameters;
import com.asamm.osmTools.mapConfig.ItemMap;
import com.asamm.osmTools.utils.Consts;

import java.io.File;
import java.io.IOException;

/**
 *
 * @author volda
 */
public class CmdMerge extends Cmd {

    private static final String MAP4TRIP_MAP_ID = "czech_republic_map4trip";

    public CmdMerge (ItemMap map){
        super(map, ExternalApp.OSMOSIS);

        // check parameters
        checkFileLocalPath();
    }



    /**
     * Create command lines commands to merge standard lomaps
     * @throws IOException
     */
    public void createCmd() throws IOException {


        boolean isContour = (Parameters.isActionRequired(Parameters.Action.CONTOUR) &&
                getMap().hasAction(Parameters.Action.CONTOUR) && getMap().getContourSep().equals("no")); // map has set the contour will be separeted
        boolean isTourist = Parameters.isActionRequired(Parameters.Action.TOURIST) &&
                getMap().hasAction(Parameters.Action.TOURIST);
        boolean isCoastline = getMap().hasSea();

        // prepare cmd line and string for log

        addReadPbf(getMap().getPathSource());

        addReadPbf(getMap().getPathCoastline());
        addMerge();

        if (isTourist){
            addReadXml(getMap().getPathTourist());
            //addBoundingPolygon(map);
            addMerge();
        }

        // try to add custom data
        addCustomData();


        if (isContour){
            addReadPbf(getMap().getPathContour());
            addMerge();
        }


        addBuffer();
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

    public void xml2pbf (String inputPath, String outputPath) throws IOException {
        addReadXml(inputPath);
        addSort();
        addWritePbf(outputPath, true);

    }
    
    public void addMerge (){
        addCommand("--merge");
    }

    /**
     * Some custom map contains custom xml data. For example map4trip. Marge also such data for this maps
     */
    private void addCustomData () {

        if (getMap().getId() != null && getMap().getId().equals(MAP4TRIP_MAP_ID)){
            // add map4trip custom data
            addMap4TripCustomData();
        }
    }

    /**
     * Check if exist any custom map data for Map4trip map and add them into merging
     */
    private void addMap4TripCustomData () {

        File dataDirF = new File(Parameters.getCustomDataDir() + Consts.FILE_SEP + "map4trip");
        if ( !dataDirF.exists() || !dataDirF.isDirectory()){
            throw new IllegalArgumentException ("Custom data folder: "+dataDirF.getAbsolutePath()+ " does not exist");
        }

        File[] listOfFiles = dataDirF.listFiles();
        for (File file : listOfFiles ){
            if (file.getName().toLowerCase().endsWith("osm.xml")){
                // filter only OSM XML files
                addReadXml(file.getAbsolutePath());
                addMerge();
            }
        }
    }
}
