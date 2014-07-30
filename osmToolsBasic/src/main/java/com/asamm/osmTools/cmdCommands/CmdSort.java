/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.asamm.osmTools.cmdCommands;

import com.asamm.osmTools.Main;
import com.asamm.osmTools.mapConfig.ItemMap;

import java.io.File;

/**
 *
 * @author volda
 */
public class CmdSort extends Cmd{
    
    public CmdSort(ItemMap map) {
        super(map, ExternalApp.OSMOSIS);
    }

    public void createCmdSort() {
        addReadPbf();
        //addReadXml();
        addSort();
        addWritePbf(true);
    }

    private void addReadPbf() {
        if (!new File(getMap().getPathContour()).exists()) {
            throw new IllegalArgumentException("Soubor vrstevnic: " +
                    getMap().getPathContour()+ " neexistuje");
        }
        
        addCommand("--read-pbf");
        addCommand(getMap().getPathContour());
    }
    private void addReadXml() {
        if (!new File(getMap().getPathContour()).exists()){
            throw new IllegalArgumentException("Soubor vrstevnic: " +
                    getMap().getPathContour()+ " neexistuje");
        }
        addCommand("--read-xml");
        addCommand(getMap().getPathContour());
    }
    
   
    protected void addWritePbf (boolean omitMetadata) {
        addCommand("--write-pbf");
        addCommand(getMap().getPathContour()+".sorted");
        if (omitMetadata){
            addCommand("omitmetadata=true");
        }
    }
    private void addWriteXml () {
        addCommand("--wx");
        addCommand(getMap().getPathContour()+".sorted");
        addCommand("compressionMethod=bzip2");
        
    }

    public void rename () {
        //ocekavam, ze probehlo sortovani a prejmenuji mapu zpet na puvodni jmeno bez postfixu sorted
        File fSorted = new File(getMap().getPathContour() + ".sorted");
        File fUnsorted = new File(getMap().getPathContour());
              
        if (!fSorted.exists()){
            throw new IllegalArgumentException("Sorted contourline " +
                    getMap().getPathContour() + ".sorted does not exist.");
        }
        if (fUnsorted.exists()){
            fUnsorted.delete();
        }
        
        Main.LOG.info("Rename contour line: " + fSorted.getName() +
                " to: " + fUnsorted.getName());
        fSorted.renameTo(new File(getMap().getPathContour()));
    }
}

