/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.asamm.osmTools.tourist;

import com.asamm.osmTools.Parameters;
import com.asamm.osmTools.generator.GenLoMaps;
import com.asamm.osmTools.utils.Logger;
import org.apache.commons.lang3.StringEscapeUtils;
import org.kxml2.io.KXmlParser;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 *
 * @author volda
 */
public class Way {

    private static final String TAG = Way.class.getSimpleName();

    long id;
    String visible;
    ArrayList<Node> nodes;
    ArrayList<Tags> tagsArray;
    private Tags originalTags;
    boolean isInWayList;

    public Way () {
        //define array of nodes
        nodes = new ArrayList<Node>();

        originalTags = new Tags();
    }
    
    
    public void inicializeTourist  (KXmlParser parser, WayList wl) {
        // resize
        //Main.cycloId++;
        fillAttributes(parser);

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

    /**
     * The international trails are often organized into so called super route relation
     * https://wiki.openstreetmap.org/wiki/Relation:superroute
     * This super route may have different color then national routes. For example international E8 route has blue
     * color but local national routes use RED marked trails for it. If super route isn't removed the Blue a Red color
     * is printed in the map
     */
    private void removeSuperRoute() {
        if (tagsArray != null){
            for(int i = tagsArray.size() - 1; i >= 0; --i) {
                Tags tags = tagsArray.get(i);
                if (tags.type != null && tags.type.equalsIgnoreCase("superroute")){
                    tagsArray.remove(i);
                    Logger.i(TAG, "Remove superroute, ID: " + tags.parentRelId);
                }
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

    /**
     * Generate the #Tags.osmOrder value. The goal is avoid multiple lines of the same color, groups order by type of
     * route (separate order for Hiking, Cycling or Ski routes)
     */
    private void computeOsmcOrder(){

        //TODO replace this ugly map with enum of tourist types and organize using groupBy stream
        Map<String, List<Tags>> tagsTypesMap = new HashMap<>();
        tagsTypesMap.put("hiking", new ArrayList<>());
        tagsTypesMap.put("cycling", new ArrayList<>());
        tagsTypesMap.put("ski", new ArrayList<>());

        // organize tags by type
        tagsArray.stream().forEach(tags -> {
            if (tags.isHiking()){
                tagsTypesMap.get("hiking").add(tags);
            }
            else if (tags.isRegularBycicle() || tags.isMtb()){
                tagsTypesMap.get("cycling").add(tags);
            }
            else if (tags.isSki()){
                tagsTypesMap.get("ski").add(tags);
            }
        });

        tagsTypesMap.forEach((t, touristTags) -> {

            int counter = 0;
            Set<String> colors = new HashSet<>(); // list of unique osmc colors for specific type of toursit route
            for (Tags tags : touristTags){

                if (tags.osmc_color != null && tags.osmc_color.length() > 0 && !colors.contains(tags.osmc_color)){
                    // such color isn't is list of used colors, increase the order
                    colors.add(tags.osmc_color);
                    tags.osmc_order = counter;
                    counter++;
                }
            }
        });
    }
    
    public String toXml(){

        // remove SuperRoute relation before organize the order and OSMC colors
        removeSuperRoute();

        // organize tourist tags by hiking, cycling and compute the order when multiple lines are on the same way
        this.computeOsmcOrder();

        String str = "";   
        if (Parameters.printHighestWay){
             //find the highest TAGS ib tags array
            Tags tags = this.getTheHighestTags();
            if (tags == null) {
                return ""; 
            }
            str += this.toXmlString(tags,0, Parameters.touristWayId);
            Parameters.touristWayId++;
        } 
        else {  
            // print the same number of ways as tags
            // refsLength is keng of string of ref tags this is used for offset 
            // of ref tag in situation when two or more ways are overlayed
            int refsLength =0;
            for (Tags tags : tagsArray) {

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

        // try to obtain the original highway tag and set it also into new hiking way
        tags.highway = originalTags.highway;



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

    public Tags getOriginalTags() {
        return originalTags;
    }

    public void addOriginalTag(Tag t) {
        this.originalTags.setValue(t);
    }
}