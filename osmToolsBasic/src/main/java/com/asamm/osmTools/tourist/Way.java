/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.asamm.osmTools.tourist;

import com.asamm.osmTools.Parameters;
import org.apache.commons.lang3.StringEscapeUtils;
import org.kxml2.io.KXmlParser;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

/**
 *
 * @author volda
 */
public class Way {
    
    long id;
    String visible;
    ArrayList<Node> nodes;
    ArrayList<Tags> tagsArray;
    boolean isInWayList;

    public Way () {
        //define array of nodes
        nodes = new ArrayList<Node>();
    
    }
    
    
    public void inicializeTourist  (KXmlParser parser, WayList wl) {
        // resize
        //Main.cycloId++;
        fillAttributes(parser);

//        if (id == null){
//            throw new IllegalArgumentException("OSM file contains way without ID attribute");
//        }

        // look for way in waylist
        isInWayList = (wl.isWayInList(id));

        // get Tags for this way from wayList
        if (isInWayList) {

            this.tagsArray = wl.wayList.get(id);
            if (this.tagsArray == null) {
                throw new IllegalArgumentException("Cyclo way ID= " + this.id + " has no tags");
            }
        }
    }
    
    public void addNode (Node nd){
        nodes.add(nd);
    }
    
    private void fillAttributes(KXmlParser parser){
        if (parser.getAttributeValue(null, "id") != null){
            String str = parser.getAttributeValue(null, "id");
            id = Long.valueOf(str);
        }
        if (parser.getAttributeValue(null, "visible") != null){
            visible = parser.getAttributeValue(null, "visible");
        }
        
    }
    
    public String toXml(){
        
        String str = "";   
        if (Parameters.printHighestWay){
             //find the highest TAGS ib tags array
            Tags tags = this.getTheHighestTags();
            if (tags == null) {
                return ""; 
            }
            str += this.toXmlString(tags,0,Parameters.touristWayId);
            Parameters.touristWayId++;
        } 
        else {  
            // print the same number of ways as tags
            // refsLength is keng of string of ref tags this is used for offset 
            // of ref tag in situation when two or more ways are overlayed
            int refsLength =0;
            for (Tags tags : tagsArray){
               
                //str += this.toXmlString(tags, refsLength);
                str += this.toXmlString(tags, refsLength, Parameters.touristWayId);
                Parameters.touristWayId++;
                
                // =================  HERE IS OFFSET FOR REF TAG COMPUTED ==========
                if (tags.ref != null){
                    refsLength += tags.ref.length()+2;
                }
            }
        }
        
        return str;
        
    }
    
    /**
     * function write way with attributes nodes and specified tags to string
     * @param tags 
     * @return XML string
     */
    public String toXmlString(Tags tags, int refsLength, long wayId){
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        Date date =  new Date();
        
        String str = "";
        // print way heading
        str += "\n"
                + "  <way id=\""+wayId+"\" user=\"AsammSW\" ";
        if (visible != null){
            str += "visible=\""+StringEscapeUtils.escapeXml(this.visible)+"\"";
        }
        str+=  "version=\"1\" "
                + "timestamp=\""+df.format(date) +"\">";
        // print nodes
        for (Node node : nodes){
            str += "\n   <nd ref=\""+node.id+"\"/>";
        }
        // print tags string
        str += tags.toXml(refsLength);

        //close the way
        str += "\n  </way>";
        return str;
    }
    
   /**
    * Function read the array of Tags and find the highest Tags. The highest mean
    * from type of cyclo way... Internation, regional etc.
    * @return Tags object of specific tags inherited from parent relation
    */
    private Tags getTheHighestTags (){
        
        Tags tags;
        Tags highestTags = null;
        int highestNetworkNum = -9999 ;// lokal cyclo start at 1. 
        Integer pom;
        String net;
        // TODO vyresit kdyz budes mit dve cesty stejne urovne ktere budou nejvyssi!!!
        for (int i = 0; i < tagsArray.size(); i++){
            // test if tags is bycicle
            tags = tagsArray.get(i);
            if (tags.isRegularBycicle()){
                //now know that way is bycicle and it is able to compare based on network tag
                net = tags.network;
                pom = Parameters.bycicleNetworkType.get(net);
                //System.out.println("Cesta " + this.id +" pom: "+ pom);
                if (pom > highestNetworkNum){
                    highestTags = tags;
                    highestNetworkNum = pom;
                }
                continue;
            }
            
            if (tags.isHiking()){
                //now know that way is bycicle and it is able to compare based on network tag
                net = tags.network;
                pom = Parameters.hikingNetworkType.get(net);
                
                System.out.println("Cesta " + this.id +" pom: "+ pom);
                if (pom > highestNetworkNum){
                    highestTags = tags;
                    highestNetworkNum = pom;
                }
                continue;
            }
        }
        
        return highestTags;
    }
    
}