/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.asamm.osmTools.osm;

import com.asamm.osmTools.utils.Utils;
import org.kxml2.io.KXmlParser;

import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 *
 * @author volda
 */
public class Node {
    String ref; // this is only for nodes as members of ways
    Long id;
    String version;
    String visible;
    double lat;
    double lon;
    Tags tags;

    /**
     * Create copy of node
     * @param node
     */
    public Node (Node node){
        this.id = node.id;
        this.ref = node.ref;
        this.version = node.version;
        this.visible = node.visible;
        this.lat = node.lat;
        this.lon = node.lon;
        this.tags = new Tags(node.tags);

    }

    public Node () {
        // dafault action set visible to true
        visible = "true";
        
        tags = new Tags();
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setLat(double lat) {
        this.lat = lat;
    }

    public void setLon(double lon) {
        this.lon = lon;
    }

    public void setTags(Tags tags) {
        this.tags = tags;
    }

    public Long getId() {
        return id;
    }

    public double getLat() {
        return lat;
    }

    public double getLon() {
        return lon;
    }

    public Tags getTags() {
        return tags;
    }

    
    
    public void fillAttributes(KXmlParser parser){
        if (parser.getAttributeValue(null, "ref") != null){
            ref = parser.getAttributeValue(null, "ref");
        }
        if (parser.getAttributeValue(null, "id") != null){
            String str;
            str = parser.getAttributeValue(null, "id");
            if (Utils.isNumeric(str)){
                this.id = Long.parseLong(str);
            } else {
                throw new IllegalArgumentException (
                    "Wrong id value during parsing xml on line: " + parser.getLineNumber()
                );
            }
            
        }
        if (parser.getAttributeValue(null, "version") != null){
            version = parser.getAttributeValue(null, "version");
        }
        if (parser.getAttributeValue(null, "visible") != null){
            visible = parser.getAttributeValue(null, "visible");
        }
        if (parser.getAttributeValue(null, "lat") != null){
            String str;
            str = parser.getAttributeValue(null, "lat");
             if (Utils.isNumeric(str)){
                this.lat = Double.parseDouble(str);
            } else {
                throw new IllegalArgumentException (
                    "Wrong lat value during parsing xml on line: " + parser.getLineNumber()
                );
            }
        }
        if (parser.getAttributeValue(null, "lon") != null){
            String str;
            str = parser.getAttributeValue(null, "lon");
            if (Utils.isNumeric(str)){
                this.lon = Double.parseDouble(str);
            } else {
                throw new IllegalArgumentException (
                    "Wrong lon value during parsing xml on line: " + parser.getLineNumber()
                );
            }
        }
        
        
    }

    /**
     * Test if node is cycle junction in NL, BE
     * @return <code>true</code> if node has cyclo junction
     */
    boolean isCycloNLBEJunction(){
        if (tags == null) {
            return false;
        }
        if (tags.rcn_ref == null){
            return false;
        }
        return true;
    }

    /**
     * Test if node is hiking junction in NL, BE
     * @return <code>true</code> if node is hiking junction in NL, BE
     */
    boolean isHikingNLBEJunction () {
        if (tags == null) {
            return false;
        }
        if (tags.rwn_ref == null){
            return false;
        }
        return true;
    }

    public String toXmlString(){
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        Date date =  new Date();
        
                //format for numver of digits
        DecimalFormat decimf = new DecimalFormat("#.0000000", new DecimalFormatSymbols(java.util.Locale.US));
        
        if (visible == null){
            visible = "";
        }
        String str = "";
        // print node heading
        str += "\n"
                + "  <node id=\""+this.id+"\" user=\"AsammSW\" "
                + "visible=\""+this.visible+"\" version=\"1\" "
                + "timestamp=\""+df.format(date) +"\" "
                + "lat=\""+decimf.format(this.lat)+"\" "
                + "lon=\""+decimf.format(this.lon)+"\">"  ;
        // print tags
        if (isCycloNLBEJunction()){
            str += printCycloJunctionTag();
        }
        else if (isHikingNLBEJunction()){
            str += printHikingJunctionTag();
        }
        // print tags string
        str += tags.toXml();

        //close the way
        str += "\n  </node>";
        
        return str;
    }

    /**
     * Print custom tag for NLBE cycle junction
     * @return
     */
    private String printCycloJunctionTag() {
        //use the same tags as OAM
        return "\n   <tag k=\"cycle_node\" v=\"NLBE\"/>";
    }

    /**
     * Print custom tag for NLBE hiking junction
     * @return osm xml tag element for NLBE hiking junction
     */
    private String printHikingJunctionTag() {
        return "\n   <tag k=\"hiking_node\" v=\"NLBE\"/>";
    }
    
}
