/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.asamm.osmTools.sea;

import com.asamm.osmTools.Main;
import com.asamm.osmTools.Parameters;
import com.asamm.osmTools.cmdCommands.CmdMerge;
import com.asamm.osmTools.cmdCommands.CmdOgr;
import com.asamm.osmTools.cmdCommands.CmdShp2osm;
import com.asamm.osmTools.mapConfig.ItemMap;
import com.asamm.osmTools.tourist.Node;
import com.asamm.osmTools.tourist.Tags;
import com.asamm.osmTools.tourist.Way;
import com.asamm.osmTools.utils.Consts;
import com.asamm.osmTools.utils.Utils;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;

/**
 *
 * @author volda
 */
public class Sea {
    ItemMap map;
    String tmpCoastPath;
    String tmpBorderPath;
    double seaBorderMargin = 0.00006;  // create sea area little bit smaller 
                                       //then borders becasue thin blue border
    
    public Sea(ItemMap map) throws IOException, InterruptedException {
        this.map = map;
        this.tmpBorderPath = Consts.DIR_TMP + "seaBorder_"
                + map.getName() + ".osm.xml";
        this.tmpCoastPath = Consts.DIR_TMP + "seaCoastline_"
                + map.getName() + ".osm.xml";
    }
    
    public void create () throws IOException, InterruptedException{
          // test if shp file with land polygons exist. 
        // For every map export bordered shpfile
        if (!new File(map.getPathShp()).exists()){
            Main.LOG.info("Starting create shape file with coastlines: "+map.getPathShp());
            createCoastShp();
        }
        else {
            Main.LOG.info("Shape File with coastlines for map: " +
                    map.getName() + " already exist.");
        }
        //test if osm file with coastline exist
        if (!new File(map.getPathCoastline()).exists()) {
            Main.LOG.info("Starting convert shape file with coastlines to OSM file: " + map.getPathCoastline());

            createCoastOsm();

            // vytvor hranici more
            createBoundSeaXml();

            // merge tmp convert shp file with border
            mergeBoundsToCoast();

            //clean tmp directory
            cleanTmp();
        } else {
            Main.LOG.info("OSM File with coastlines for map: " + map.getName() + " already exist.");
        }
    }
    private void createCoastShp() throws IOException, InterruptedException{
        FileUtils.forceMkdir(new File(map.getPathShp()));
        CmdOgr co = new CmdOgr(map);
        co.createCmd();
        co.execute();
    }
    
    private void createCoastOsm() throws IOException, InterruptedException{
        CmdShp2osm cs = new CmdShp2osm(map, map.getPathShp(), tmpCoastPath);
        cs.createCmd();
        cs.execute();
    }
    
    /**
     * Function create OSM xml file which contain reactangular as map size.
     */
    private void createBoundSeaXml() throws IOException {
        StringBuilder sb =  new StringBuilder();
        
        Node cornerNW = new Node();
        cornerNW.setId(Parameters.costlineBorderId);
        cornerNW.setLat(map.getBoundary().getMaxLat() - seaBorderMargin);
        cornerNW.setLon(map.getBoundary().getMinLon() + seaBorderMargin);
        Parameters.costlineBorderId++;
        
        Node cornerNE = new Node();
        cornerNE.setId(Parameters.costlineBorderId);
        cornerNE.setLat(map.getBoundary().getMaxLat() - seaBorderMargin);
        cornerNE.setLon(map.getBoundary().getMaxLon() - seaBorderMargin);
        Parameters.costlineBorderId++;
        
        Node cornerSW = new Node();
        cornerSW.setId(Parameters.costlineBorderId);
        cornerSW.setLat(map.getBoundary().getMinLat() + seaBorderMargin);
        cornerSW.setLon(map.getBoundary().getMinLon() + seaBorderMargin);
        Parameters.costlineBorderId++;
        
        Node cornerSE = new Node();
        cornerSE.setId(Parameters.costlineBorderId);
        cornerSE.setLat(map.getBoundary().getMinLat() + seaBorderMargin);
        cornerSE.setLon(map.getBoundary().getMaxLon() - seaBorderMargin);
        Parameters.costlineBorderId++;
        
        // create way outer rectangular off sea
        Way way = new Way();
       
        way.addNode(cornerNW);
        way.addNode(cornerNE);
        way.addNode(cornerSE);
        way.addNode(cornerSW);
        way.addNode(cornerNW);
        
        // set tags for sea rectangular
        Tags tags = new Tags();
        tags.natural = "sea";
        tags.layer = "-5";
        
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                    + "\n<osm version=\"0.6\" generator=\"Asamm world2vec\">");
        
        sb.append(cornerNW.toXmlString());
        sb.append(cornerNE.toXmlString());
        sb.append(cornerSW.toXmlString());
        sb.append(cornerSE.toXmlString());
        
        sb.append(way.toXmlString(tags, 0, Parameters.costlineBorderId));
        Parameters.costlineBorderId++;
        
        sb.append("\n</osm>");
        
        
        // write to the file
        Main.LOG.info("Writing sea(map) borders into file: "+tmpBorderPath);
        FileUtils.writeStringToFile(new File(tmpBorderPath), sb.toString(), false);
    }

    private void mergeBoundsToCoast() throws IOException, InterruptedException {
        // firstly test if files for merging exists
        if (!new File(tmpCoastPath).exists()) {
            throw new IllegalArgumentException("Temporary coastline file " + tmpCoastPath + "does not exist!");
        }
        if (!new File (tmpBorderPath).exists()){
            throw new IllegalArgumentException("Temporary sea border file " + tmpBorderPath + "does not exist!");
        }
        
        // create directory for output
        FileUtils.forceMkdir(new File(map.getPathCoastline()));

        Main.LOG.info("Merge map border and coastlines into file: "+ map.getPathCoastline());
        CmdMerge cm = new CmdMerge(map);
        cm.createSeaCmd(tmpCoastPath, tmpBorderPath);
        cm.execute();
    }

    private void cleanTmp() {
        File tcp =  new File(tmpCoastPath);
        File tbp = new File (tmpBorderPath);
        if (tcp.exists()) {
            tcp.delete();
        }
        if (tbp.exists()){
            tbp.delete();
        }
    }
     
    
}
