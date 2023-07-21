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
                if (tags.type != null && tags.type.equalsIgnoreCase("superroute")
                    && !tags.isOsmSymbolDefined() && !tags.isIwnNwnRwnLwn()){
                    tagsArray.remove(i);
                    //Logger.i(TAG, "Remove superroute, ID: " + tags.parentRelId);
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
     * In Germany are often background color the same as foreground
     * The goal is to keep foreground color empty if same as background
     * The osmc symbol with missing foreground will not be displayed
     */
    private void removeSameOsmcForegroundColor(){

        for (Tags tags : tagsArray) {
            if (tags.osmc_background != null && !tags.osmc_background.isEmpty()
                    && tags.osmc_foreground != null && !tags.osmc_foreground.isEmpty()){

                if (tags.osmc_foreground.startsWith(tags.osmc_background)){
                    Logger.i(TAG, "Remove osmc foreground for way, ID: " + this.id);
                    tags.osmc_foreground = "";
                }
            }
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
            int counter_symbol_order = 0;
            Set<String> colors = new HashSet<>(); // list of unique osmc colors for specific type of tourist route
            Set<String> osmc_foregrounds = new HashSet<>(); // list of unique osmc foregrounds icons for specific type of tourist route

            // check if there is any iwn/rwn...without defined OSMC color
            for (Tags tags : touristTags) {
                if (tags.isIwnNwnRwnLwn() && (tags.osmc_color == null || tags.osmc_color.isEmpty())) {
                    // this is any local or national, international hiking route without defined osmc color set it
                    // as first order and simulate it as red color (another red color will have the same order as this one)
                    colors.add("red");
                    counter++;
                    break;
                }
            }

            for (Tags tags : touristTags){

                if (tags.osmc_color != null && tags.osmc_color.length() > 0 && !colors.contains(tags.osmc_color)){
                    // such color isn't is list of used colors, increase the order
                    colors.add(tags.osmc_color);
                    tags.osmc_order = counter;
                    counter++;
                }
                if (tags.osmc_foreground != null && tags.osmc_foreground.length() > 0 && !osmc_foregrounds.contains(tags.osmc_foreground)){
                    // such symbol isn't is list of used osmc foregrounds
                    osmc_foregrounds.add(tags.osmc_foreground);
                    tags.osmc_symbol_order = counter_symbol_order;
                    counter_symbol_order++;
                }
            }
        });
    }

    /**
     * Combine the ref value and name into combination "name,  ref"
     */
    private void mergeRefAndName() {

        for (Tags tags : this.tagsArray){
            String ref = (tags.ref == null) ? "" : tags.ref;
            String name = (tags.name == null) ? "" : tags.name;

            if (name.length() > 0){
                if (ref.length() > 0 && !name.contains(ref)) {
                    // append ref to the existing name
                    tags.name = name + ", " + ref;
                }
            }
            else if (ref.length() > 0 && !name.contains(ref)) {
                // name is empty replace it by ref value
                tags.name = ref;
            }
        }
    }

    private void copyOriginalTagsToNewWays(){

        for (Tags tags : tagsArray) {

            // try to obtain the original highway tag and set it also into new hiking way
            tags.highway = originalTags.highway;
            tags.sac_scale = originalTags.sac_scale;

            tags.tunnel = originalTags.tunnel;
            tags.bridge = originalTags.bridge;
        }
    }
    
    public String toXml(){

        if (id == 97541653){
            Logger.i(TAG, "Way ID: " + id);
        }

        // copy specific tags from original OSM way into created tourist ways
        copyOriginalTagsToNewWays();

        // remove SuperRoute relation before organize the order and OSMC colors
        removeSuperRoute();

        // organize tourist tags by hiking, cycling and compute the order when multiple lines are on the same way
        this.computeOsmcOrder();

        // remove duplicated color in foreground and background
        this.removeSameOsmcForegroundColor();

        this.mergeRefAndName();

        String str = "";   
        if (Parameters.printHighestWay){
             //find the highest TAGS ib tags array
            Tags tags = this.getTheHighestTags();
            if (tags == null) {
                return ""; 
            }
            str += this.toXmlString(tags,Parameters.touristWayId);
            Parameters.touristWayId++;
        } 
        else {  
            for (Tags tags : tagsArray) {

                //str += this.toXmlString(tags, refsLength);
                str += this.toXmlString(tags, Parameters.touristWayId);
                Parameters.touristWayId++;
            }
        }
        
        return str;
    }


    /**
     * function write way with attributes nodes and specified tags to string
     * @param tags 
     * @return XML string
     */
    public String toXmlString(Tags tags, long wayId){
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
        str += tags.toXml();

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