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
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;

/**
 *
 * @author volda
 */
public class LandArea {
    ItemMap map;
    String tmpLandPath;
    String tmpBorderPath;
    double seaBorderMargin = 0.00006;  // create sea area little bit smaller
                                       //then borders because thin blue border
    
    public LandArea(ItemMap map) throws IOException, InterruptedException {
        this.map = map;
        this.tmpBorderPath = Consts.DIR_TMP + "seaBorder_"
                + map.getName() + ".osm.xml";
        this.tmpLandPath = Consts.DIR_TMP + "seaCoastline_"
                + map.getName() + ".osm.xml";
    }
    
    public void create() throws IOException, InterruptedException{
        // test if shp file with land polygons exist.
        if (!new File(map.getPathShp()).exists()){
            Main.LOG.info("Starting create shape file with land area: "+map.getPathShp());
            createCoastShp();
        } else {
            Main.LOG.info("Shape File with land area for map: " +
                    map.getName() + " already exist.");
        }

        // test if osm file with coastline exist
        if (!new File(map.getPathCoastline()).exists()) {
            Main.LOG.info("Starting convert shape file with coastlines to OSM file: " + map.getPathCoastline());



            // check if map has sea and it's needed to create blue sea rectangle
            if (map.hasSea()){
                // create sea border
                createBoundSeaXml();
                // create OSM boundary
                createLandOsm(tmpLandPath);
                // merge tmp convert shp file with border
                mergeBoundsToCoast();

            }
            else {

                createLandOsm(tmpLandPath);

                // convert land osm xml to pbf
                CmdMerge cm = new CmdMerge(map);
                cm.xml2pbf(tmpLandPath, map.getPathCoastline());
                cm.execute();
            }

            //clean tmp directory
            cleanTmp();
        } else {
            Main.LOG.info("OSM File with land area for map: " + map.getName() + " already exist.");
        }
    }

    /**
     * Cut SHP Land to country SHP file
     * @throws IOException
     * @throws InterruptedException
     */
    private void createCoastShp() throws IOException, InterruptedException{
        // prepare directories
        FileUtils.forceMkdir(new File(map.getPathShp()).getParentFile());

        // execute generating
        CmdOgr co = new CmdOgr(map);
        co.createCmd();
        co.execute();
    }

    /**
     * Convert SHP land area to OSM xml
     * @throws IOException
     * @throws InterruptedException
     */
    private void createLandOsm(String outputFile) throws IOException, InterruptedException{
        CmdShp2osm cs = new CmdShp2osm(map, map.getPathShp(), outputFile);
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
        
        sb.append(way.toXmlString(tags, Parameters.costlineBorderId++));
        
        sb.append("\n</osm>");
        
        
        // write to the file
        Main.LOG.info("Writing sea(map) borders into file: "+tmpBorderPath);
        FileUtils.writeStringToFile(new File(tmpBorderPath), sb.toString(), false);
    }

    private void mergeBoundsToCoast() throws IOException, InterruptedException {
        // firstly test if files for merging exists
        if (!new File(tmpLandPath).exists()) {
            throw new IllegalArgumentException("Temporary coastline file " + tmpLandPath + "does not exist!");
        }
        if (!new File (tmpBorderPath).exists()){
            throw new IllegalArgumentException("Temporary sea border file " + tmpBorderPath + "does not exist!");
        }
        
        // create directory for output
        FileUtils.forceMkdir(new File(map.getPathCoastline()).getParentFile());

        // execute merge
        Main.LOG.info("Merge map border and coastlines "+ tmpLandPath +" and "+ tmpBorderPath +"into file: "+ map.getPathCoastline());
        CmdMerge cm = new CmdMerge(map);
        cm.createSeaCmd(tmpLandPath, tmpBorderPath);
        cm.execute();
    }

    private void cleanTmp() {
        File tcp =  new File(tmpLandPath);
        File tbp = new File (tmpBorderPath);
        if (tcp.exists()) {
            tcp.delete();
        }
        if (tbp.exists()){
            tbp.delete();
        }
    }
     
    
}
