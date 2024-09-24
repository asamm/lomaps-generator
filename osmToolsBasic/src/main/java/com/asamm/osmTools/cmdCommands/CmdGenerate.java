/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.asamm.osmTools.cmdCommands;

import com.asamm.osmTools.Parameters;
import com.asamm.osmTools.config.Action;
import com.asamm.osmTools.mapConfig.ItemMap;
import com.asamm.osmTools.sea.Boundaries;
import com.asamm.osmTools.utils.Logger;

import java.io.File;
import java.io.IOException;

/**
 *
 * @author volda
 */
public class CmdGenerate extends Cmd {

    private static final String TAG = CmdGenerate.class.getSimpleName();

    private boolean isRam = true;

    public CmdGenerate(ItemMap map) {
        super(map, ExternalApp.OSMOSIS);

        // if is there action to merge test files for merge
        if (getMap().isMerged){
            if (!new File(getMap().getPathMerge()).exists()){
                throw  new IllegalArgumentException("Merged map for generation: "+
                        getMap().getPathMerge() + " does not exist!");
            }
            
            if (!new File(Parameters.mTouristTagMapping).exists()){
                throw new IllegalArgumentException("Map writter definition file:  "
                        +Parameters.mTouristTagMapping +" does not exist.");
            }
            
            if (!new File(getMap().getPathMerge()).exists()){
                getMap().isMerged = false;
            }
            
           // getMap().isMerged = !new File(getMap().mergePath+"."+Parameters.contourNoSRTM).exists();
        } else if (!new File(getMap().getPathSource()).exists()){
            throw new IllegalArgumentException("Extracted map for generation = "+ getMap().getPathSource() + " does not exist." );
            
        }
    }


    public String execute(int numRepeat, boolean deleteFile) throws IOException, InterruptedException {

        try {
            return super.execute();
        } catch (Exception e) {

            if (numRepeat > 0){

                numRepeat--;
                File generatedFile = new File(getMap().getPathGenerate());

                if (deleteFile){
                    Logger.w(TAG, "Delete file from previous not success execution: " + generatedFile.getAbsolutePath());
                    if (generatedFile.exists()){
                        generatedFile.delete();
                    }
                }
                Logger.w(TAG, "Re-execute generation: " + getCmdLine());
                execute(numRepeat, deleteFile);
            }
            else {
                throw e;
            }
        }
        return null;
    }

    public void createCmdContour() throws IOException {
        addReadPbf(getMap().getPathContour());
        addMapWriterContour();
        addType();
        addPrefLang();
        addBbox(getMap().getBoundary());
        addTagConf(Parameters.getContourTagMapping());
        addZoomIntervalContour();
        addBboxEnlargement(1);
        //ddWayClipping();
        //addMapComment();
    }

    public void createCmd() throws IOException {
        //TODO remove
        //addCommand("-v");

        // if is there merged map generate map from merged. otherwise create from exported
        if (getMap().isMerged){
            addReadPbf(getMap().getPathMerge());
        } else {
            addReadPbf(getMap().getPathSource());
        }

        //addBuffer();
        addMapWriter();
        addType();
        addPrefLang();
        addBbox(getMap().getBoundary());
        addTagConf(Parameters.mTouristTagMapping);
        //addDebugFile();
        addZoomInterval();

        addSimplificationFactor();
        addBboxEnlargement(5);

        addLabelPosition();

        addCommand("tag-values=true");
        //addMapboxPolylabel();

        // get num of cpu cores
        int cores = Runtime.getRuntime().availableProcessors();

        addThreads(cores);

        addMapComment();
    }



    private void addMapWriter() throws IOException {
        // prepare directory
        prepareDirectory(getMap().getPathGenerate());

        // add commands
        addCommand("--mapfile-writer");
        addCommand("file=" + getMap().getPathGenerate());
    }

    private void addMapWriterContour() throws IOException {
        // prepare directory
        prepareDirectory(getMap().getPathGenerateContour());

        // add commands
        addCommand("--mapfile-writer");
        addCommand("file=" + getMap().getPathGenerateContour());
    }
    
    private void addPrefLang() {
        if (getMap().getPrefLang() != null && getMap().getPrefLang().length() > 0){
            addCommand("preferred-languages="+getMap().getPrefLang());
        }
    }
    
    private void addMapComment() {
        addCommand("comment=" + Parameters.MAP_COMMENT);
    }

    private void addLabelPosition() {
        addCommand("label-position=true");
    }

    private void addMapboxPolylabel() {
        addCommand("polylabel=true");
    }

    private void addThreads(int threads) {
        addCommand("threads="+threads);
    }

    private void addType(){
        //firstly decide based on forceType from config xml
        if (getMap().getForceType() != null && !getMap().getForceType().isEmpty()){
            if (getMap().getForceType().toLowerCase().equals("hd")){
                addCommand("type=hd");
                return;
            }
            if (getMap().getForceType().toLowerCase().equals("ram")){
                addCommand("type=ram");
                isRam = true;
                return;
            }            
        }
        
        //set FileSizeLimit determined when use HD and when RAM
        int fileSizeLimit = 650;
        // get map size
        long mapSizeMb = new File(getMap().getPathSource()).length();
        if (getMap().isMerged){
            mapSizeMb = new File(getMap().getPathMerge()).length();
        } 
        mapSizeMb = mapSizeMb /1024/1024;
        //System.out.println("Velikost souboru "+getMap().file+" je:  "+ mapSizeMb);
        if (mapSizeMb < fileSizeLimit) {
            addCommand("type=ram");
            //set boolean value for coastline
            isRam = true;
        
        } else {
            addCommand("type=hd");
        }  
    }
    
    private void addZoomIntervalContour () {
        addCommand("zoom-interval-conf=12,10,12,15,13,21"); 
    }

    private void addSimplificationFactor () {
        //default
        //addCommand("simplification-factor=2.5");
        //addCommand("simplification-factor=5");
        addCommand("simplification-factor=0.5");
    }

    private void addWayClipping () {
        addCommand("way-clipping=false");
    }


    private void addZoomInterval(){
        //default
        // addCommand(" zoom-interval-conf=5,0,7,10,8,11,14,12,21"); default
        // kech 5,0,6,8,7,9,11,10,12,15,13,21
        // muj novy
        //addCommand("zoom-interval-conf=6,0,7,9,8,9,12,10,12,15,13,21");
        // new intervals
        //addCommand("zoom-interval-conf=5,0,6,8,7,9,11,10,12,15,13,21");
        
        if (getMap().getForceInterval() != null && !getMap().getForceInterval().isEmpty()){
            addCommand("zoom-interval-conf=" + getMap().getForceInterval());
        }
    }
    
    private void addBbox(Boundaries bounds){
        addCommand("bbox="
                + Double.toString(bounds.getMinLat())
                + "," + Double.toString(bounds.getMinLon())
                + "," + Double.toString(bounds.getMaxLat())
                + "," + Double.toString(bounds.getMaxLon()));
    }
    
    private void addDebugFile(){
         addCommand("debug-file=true");
    }
    
    private void addTagConf(String path){
        addCommand("tag-conf-file="+path);
    }
    
    private void addBboxEnlargement(int pixels){
        addCommand("bbox-enlargement="+pixels);
    }
}
