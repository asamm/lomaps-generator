/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.asamm.osmTools.tourist;

import com.asamm.osmTools.Main;
import com.asamm.osmTools.Parameters;
import com.asamm.osmTools.utils.Logger;
import org.apache.commons.lang3.StringEscapeUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 *
 * @author volda
 */
public class Tags {

    private static final String TAG = Tags.class.getSimpleName();

    public String type;
    public String route;
    public String network;
    public String ref;
    public String name;
    public String state;
    public String natural;
    public String layer;
    public String whitesea;
    public long parentRelId;
    public String tunnel;
    public String bridge;

    // for polabska stezka
    public String tracktype;

    // for cyclo nodetrack
    public String rcn;
    public String rcn_ref;
    public String rwn_ref;
    public String note;
    
    //for hiking
    public String highway;
    public String osmcsymbol;
    public String osmc; // store information for mapsforge, boolean whearher tags has values osmcsymbol
    public int osmc_order;
    public int osmc_symbol_order;
    public String osmc_color;
    public String osmc_background;
    public String osmc_foreground;
    public String osmc_text;
    public String osmc_text_color;
    public String osmc_text_length;
    public String kct_barva;
    public String kct_green;
    public String colour;
    public String sac_scale;

    //for ski
    public String pisteType;
    public String pisteGrooming;
    public String pisteDifficulty;

    /**
     * Create copy of tags object
     * @param tags
     */
    public Tags (Tags tags) {
        this.type = tags.type;
        this.route = tags.route;
        this.network = tags.network;
        this.ref = tags.ref;
        this.name = tags.name;
        this.state = tags.state;
        this.natural = tags.natural;
        this.layer = tags.layer;
        this.whitesea = tags.whitesea;
        this.parentRelId = tags.parentRelId;
        this.tunnel = tags.tunnel;
        this.bridge = tags.bridge;

        this.highway = tags.highway;
        this.tracktype = tags.tracktype;
        this.rcn = tags.rcn;
        this.rcn_ref = tags.rcn_ref;
        this.rwn_ref = tags.rwn_ref;
        this.note = tags.note;
        this.osmcsymbol = tags.osmcsymbol;
        this.osmc = tags.osmc;
        this.osmc_order = tags.osmc_order;
        this.osmc_color = tags.osmc_color;
        this.osmc_symbol_order = tags.osmc_symbol_order;
        this.osmc_background = tags.osmc_background;
        this.osmc_foreground = tags.osmc_foreground;
        this.osmc_text = tags.osmc_text;
        this.osmc_text_color = tags.osmc_text_color;
        this.osmc_text_length = tags.osmc_text_length;
        this.sac_scale = tags.sac_scale;
        this.kct_barva = tags.kct_barva;
        this.kct_green = tags.kct_green;
        this.colour = tags.colour;

        this.pisteType = tags.pisteType;
        this.pisteGrooming = tags.pisteGrooming;
        this.pisteDifficulty = tags.pisteDifficulty;
    }

    public Tags (){

    }

    public void setValue(Tag tag){
        if (tag.key != null && tag.val != null){
            if (tag.key.equals("rcn_ref")){
                rcn_ref = tag.val;
                return;
            }
            if (tag.key.equals("rwn_ref")){
                rwn_ref = tag.val;
                return;
            }
            if (tag.key.equals("type")){
                type = tag.val;
                return;
            }
            if (tag.key.equals("route")){
                route = tag.val.toLowerCase();
                return;
            }
            if (tag.key.equals("network")){
                network = tag.val.toLowerCase();
                return;
            }
            if (tag.key.equals("ref")){
                ref = tag.val;
                return;
            }
            if (tag.key.equals("name")){
                name = tag.val;
                return;
            }
            if (tag.key.equals("rcn")){
                rcn = tag.val;
                return;
            }

            if (tag.key.equals("tunnel")){
                tunnel = tag.val;
                return;
            }
            if (tag.key.equals("bridge")){
                bridge = tag.val;
                return;
            }

            if (tag.key.equals("highway")){
                highway = tag.val;
                return;
            }
            if (tag.key.equals("tracktype")){
                tracktype = tag.val;
                return;
            }

            if (tag.key.equals("osmc:symbol")){
                osmcsymbol = tag.val;
            }
            if (tag.key.equals("state")){
                state = tag.val;
            }
            if (tag.key.equals("kct_barva")){
                kct_barva = tag.val;
            }
            if (tag.key.equals("kct_green")){
                kct_green = tag.val;
            }
            if (tag.key.equals("layer")){
                layer = tag.val;
            }
            if (tag.key.equals("colour")){
                colour = tag.val.toLowerCase();
            }
            if (tag.key.equals("piste:type")){
                pisteType = tag.val.toLowerCase();
            }
            if (tag.key.equals("piste:grooming")){
                pisteGrooming = tag.val.toLowerCase();
            }
            if (tag.key.equals("piste:difficulty")){
                pisteDifficulty = tag.val.toLowerCase();
            }
            if (tag.key.equals("sac_scale")){
                sac_scale = tag.val.toLowerCase();
            }
        }
    }

    /**
     * Check if relation is any type hiking, cycling or ski track that can be expanded into ways
     * @return <code>True</code> if relation can be used as source for tourist ways
     */
    public boolean isTouristRelation(){
        return (isRegularBycicle() || isMtb() || isHiking() || isSki());
    }

    /**
     * Test if relation type is cycle track
     * @return
     */
    public boolean isRegularBycicle(){
            return (route != null && route.equals("bicycle"));
    }

    /**
     * Test if relation can be MRB route
     * @return
     */
    public boolean  isMtb() {
        return (route != null && route.equals("mtb"));
    }

    /**
     * Check if tags defines hiking route
     * @return
     */
    public boolean isHiking() {
        return (route != null && (route.equals("hiking") ||
                            route.toLowerCase().equals("foot")));
    }

    /**
     * Test if tags for relation define the ski relation
     * @return <code>true</code> if relation is valid SKI
     */
    public boolean  isSki() {
        return (route != null && (route.equals("piste") || route.equals("ski")));
    }

    /**
     * Check that current route is "in use"
     * @return
     */
    public boolean isValidTypeOrState(){
        if (type != null){
            if (type.equalsIgnoreCase("disused_route") || type.equalsIgnoreCase("disused:route")){
                // route is not "active" remove it from processed way
                return false;
            }
            if ( !type.equalsIgnoreCase("route")){
                // only logging to know what combination may occur for type
                Logger.i(TAG, "IS VALID TYPE: type=" + type);
            }
        }

        if (state != null){
            return !Parameters.invalidStatesForTouristRoute.contains(state);
        }

        return true;
    }
    /**
     * Split osmc:symbol tag into base color, background and foreground color
     */
    public void parseOsmcSymbol () {

        if (osmcsymbol == null || osmcsymbol.isEmpty()){
            //there are NO information about osmc:symbol try to obtain color from another tags
            osmc_color = this.getColorFromColorTags();
            return;
        }
        int position = osmcsymbol.indexOf(":");
        if (position == -1){
            // osmcsymbol has no semicolon ":" for this reason try to validate the whole value of osmcsymbol string
            if(isOsmcColorValid(osmcsymbol)){
                osmc_color = osmcsymbol;
            }
            osmc_color = getColorFromColorTags();
            return;
        }
        int counter = 0;
        //StringTokenizer tokens = new StringTokenizer(osmcsymbol, ":");
        String[] tokens = osmcsymbol.split(":");
        for (int i = 0; i < tokens.length; i++) {
           switch (i){
                case 0:
                    if (isOsmcColorValid(tokens[i].toLowerCase())){
                        osmc_color = tokens[i];
                    }
                    else {
                        osmc_color = getColorFromColorTags();
                    }
                    break;
                case 1: 
                    osmc_background = tokens[i];
                    break;
                case 2: 
                    osmc_foreground = tokens[i];
                    break;
               case 3:
                   if (tokens[i].length() <= 5){
                       osmc_text_length = String.valueOf(tokens[i].length());
                       osmc_text = tokens[i];
                   }
                   break;
               case 4:
                   if (isOsmcColorValid(tokens[i].toLowerCase())){
                       osmc_text_color = tokens[i];
                   }
                   break;
            }
        }
    }

    /**
     * Function try guess color for track from another tags
     */
    private String getColorFromColorTags() {

        if (colour != null && !colour.isEmpty() && isOsmcColorValid(colour)){
            // color tag is defined
            return colour;
        }
        return "";
    }


    /**
     * Test if Tags has all needed tag. It dependance based in type
     * @return 
     */
    public boolean validate () {

        if (isRegularBycicle()){
            if (isTagEmpty(network)){
                Main.LOG.warning("[warn_cyc_001] Relation id="+parentRelId+" has empty network tag." 
                            +      " Set new value netwotk=lcn");
                return true;
            }
            if (!Parameters.bycicleNetworkType.containsKey(network)){
                
                if (network.equals("mtb")){
                    Main.LOG.warning("[warn_cyc_003] Regular cyclo relation id="+parentRelId+" looks like "
                            + "mtb cyclo: network="+network+"."
                            + " Change to mtb - update tag route=mtb");
                    route="mtb";
                    return true;
                }
                
                //TODO try guess wrong network tag.
                //simple set value for lowest cyclo way
                Main.LOG.warning("[warn_cyc_002] Relation id="+parentRelId+" has wrong network tag: network="+network+""
                        + " Set new value lcn");
                network = "lcn";
            }
            this.osmc = "yes";
            return true;
        }
        
        else if (isMtb()){
            if (isTagEmpty(network)){
                // relation has no information about network because pro osm wiki:
                // Note network=* is not defined for mtb routes. 
                //Use distance/ascent/descent/roundtrip instead to better classify a route.
                Main.LOG.warning("[warn_mtb_001] Relation with id="+this.parentRelId+" has empty tag network. "
                       + " Seting new value network=lcn ");
                network = "lcn";
                return true;
            }
            if (!Parameters.bycicleNetworkType.containsKey(network)){
                //TODO try guess wrong network tag.
                //simple set value for lowest cyclo way
                Main.LOG.warning("[warn_mtb_002] Relation id="+parentRelId+" has wrong network tag: network="+network+""
                        + " Set new value network=lcn ");
                network = "lcn";
            }
            this.osmc = "yes";
            return true;
        }
        else if (isHiking()){
            
            // only for type when we want the highest way
            if (Parameters.printHighestWay){
                // for highest way need to know network tag
                if (isTagEmpty(network)){
                    Main.LOG.warning("[warn_hike_001] Relation with id="+this.parentRelId+" has empty network tag."
                        + " Set new value network=lwn ");
                    network = "lwn";
                    return true;
                }
                if(!isNetworkTagValid()){
                    Main.LOG.warning("[warn_hike_002] Relation id="+parentRelId+" has wrong network tak: network="+network+""
                        + " Set new neteork value lwn ");
                    network = "lwn";
                }
            }
            
            //test if has tag osmc:symbol or defined color
            if ((osmcsymbol == null || osmcsymbol.isEmpty()) && (osmc_color == null || osmc_color.isEmpty())){
                //there are NO information about osmc:symbol
                // set attributes for mapsforge
                this.osmc = "no";
                //because osmcsymbol is empty you have to decide besed on network tag. exist?
                if (isTagEmpty(network)){
                    Main.LOG.warning("[warn_hike_003] Relation with id="+this.parentRelId+" has empty network tag."
                        + " Set new value for network=lwn ");
                    network = "lwn";    
                    return true;
                }
                if (!isNetworkTagValid()){
                    Main.LOG.warning("[warn_hike_004] Relation id="+parentRelId+" has wrong network tag: network=\""+network+"\"."
                        + " Set new value lwn ");
                    network = "lwn";
                }
                return true;
            }

            //test if color is valid value of osmc color or empty
            if (!isOsmcColorValid(osmc_color)){
                //color is not valid -> test if network tag is valid
                this.osmc = "no";
                if (isTagEmpty(network)){
                    Main.LOG.warning("[warn_hike_005] Relation with id="+this.parentRelId+" has empty network and non valid osmc:symbol color."
                        + " Tag: osmc_symbol v="+osmcsymbol+" "
                            +  " Set new value for network=lwn ");
                    network = "lwn"; 
                    return true;
                }
                if (!isNetworkTagValid()){
                    Main.LOG.warning("[warn_hike_002] Relation id="+parentRelId+" has wrong network tag: network v=\""+network+"\""
                        + " Set new value lwn ");
                    network = "lwn";
                }
               
                return true;
            }
            
            //color is correct set mapsforge attribute
            this.osmc = "yes";
            
            return true;
        }
        else if (isSki()){
            // nothing to validate
            this.osmc = "yes";
            return true;
        }
        return false;
    }

    /**
     * 
     *
     * @return 
     */
    public String toXml(){
        String str = "";
          
        if (type != null){
            str += "\n   <tag k=\"type\" v=\""+type+"\"/>";
        }
        if (route != null){
            str += "\n   <tag k=\"route\" v=\""+StringEscapeUtils.escapeXml(route)+"\"/>";
        }
        if (natural != null){
            str += "\n   <tag k=\"natural\" v=\""+StringEscapeUtils.escapeXml(natural)+"\"/>";
        }
        if (network != null){
            str += "\n   <tag k=\"network\" v=\""+StringEscapeUtils.escapeXml(network)+"\"/>";
        }
        if (ref != null){
            str += "\n   <tag k=\"ref\" v=\""+StringEscapeUtils.escapeXml(ref)+"\"/>";
        }
        if (name != null){
            str += "\n   <tag k=\"name\" v=\""+StringEscapeUtils.escapeXml(name)+"\"/>";
        }
        
        if (layer != null){
            str += "\n   <tag k=\"layer\" v=\""+StringEscapeUtils.escapeXml(layer)+"\"/>";
        }
        if (whitesea != null){
            str += "\n   <tag k=\"whitesea\" v=\""+StringEscapeUtils.escapeXml(whitesea)+"\"/>";
        }

        if (tunnel != null){
            str += "\n   <tag k=\"tunnel\" v=\""+StringEscapeUtils.escapeXml(tunnel)+"\"/>";
        }
        if (bridge != null){
            str += "\n   <tag k=\"bridge\" v=\""+StringEscapeUtils.escapeXml(bridge)+"\"/>";
        }

        if (highway != null){
            str += "\n   <tag k=\"osmc_highway\" v=\""+StringEscapeUtils.escapeXml(highway)+"\"/>";
            str += "\n   <tag k=\"lm_highway\" v=\""+StringEscapeUtils.escapeXml(highway)+"\"/>";
        }
        if (tracktype != null){
            str += "\n   <tag k=\"tracktype\" v=\""+StringEscapeUtils.escapeXml(tracktype)+"\"/>";
        }

        // for cyclo nodetrack
        if (rcn != null){
            str += "\n   <tag k=\"rcn\" v=\""+StringEscapeUtils.escapeXml(rcn)+"\"/>";
        }
        if (rcn_ref != null){
            str += "\n   <tag k=\"name\" v=\""+StringEscapeUtils.escapeXml(rcn_ref)+"\"/>";
        }
        if (rwn_ref != null){
            str += "\n   <tag k=\"name\" v=\""+StringEscapeUtils.escapeXml(rwn_ref)+"\"/>";
        }
        if (state != null){
            str += "\n   <tag k=\"state\" v=\""+state+"\"/>";
        }
        if (osmc != null){
            str += "\n   <tag k=\"osmc\" v=\""+osmc+"\"/>";
        }
        if (osmc_order > 0) {
            str += "\n   <tag k=\"osmc_order\" v=\"" + StringEscapeUtils.escapeXml(String.valueOf(osmc_order)) + "\"/>";
        }
        if (osmc_symbol_order > 0) {
            str += "\n   <tag k=\"osmc_symbol_order\" v=\"" + StringEscapeUtils.escapeXml(String.valueOf(osmc_symbol_order)) + "\"/>";
        }
        if (osmc_color != null){
            str += "\n   <tag k=\"osmc_color\" v=\""+StringEscapeUtils.escapeXml(osmc_color)+"\"/>";
        }
       
        if (osmc_background != null){
            str += "\n   <tag k=\"osmc_background\" v=\""+StringEscapeUtils.escapeXml(osmc_background)+"\"/>";
        }
        if (osmc_foreground != null){
            str += "\n   <tag k=\"osmc_foreground\" v=\""+StringEscapeUtils.escapeXml(osmc_foreground)+"\"/>";
        }
        if (osmc_text != null){
            str += "\n   <tag k=\"osmc_text\" v=\""+StringEscapeUtils.escapeXml(osmc_text)+"\"/>";
        }
        if (osmc_text_length != null){
            str += "\n   <tag k=\"osmc_text_length\" v=\""+StringEscapeUtils.escapeXml(osmc_text_length)+"\"/>";
        }
        if (osmc_text_color != null){
            str += "\n   <tag k=\"osmc_text_color\" v=\""+StringEscapeUtils.escapeXml(osmc_text_color)+"\"/>";
        }
        if (sac_scale != null){
            str += "\n   <tag k=\"sac_scale\" v=\""+StringEscapeUtils.escapeXml(sac_scale)+"\"/>";
        }
        if (kct_barva != null){
            str += "\n   <tag k=\"kct_barva\" v=\""+StringEscapeUtils.escapeXml(kct_barva)+"\"/>";
        }
        if (kct_green != null){
            str += "\n   <tag k=\"kct_green\" v=\""+StringEscapeUtils.escapeXml(kct_green)+"\"/>";
        }

        if (pisteType != null){
            str += "\n   <tag k=\"piste:type\" v=\""+StringEscapeUtils.escapeXml(pisteType)+"\"/>";
        }
        if (pisteGrooming != null){
            str += "\n   <tag k=\"piste:grooming\" v=\""+StringEscapeUtils.escapeXml(pisteGrooming)+"\"/>";
        }
        if (pisteDifficulty != null){
            str += "\n   <tag k=\"piste:difficulty\" v=\""+StringEscapeUtils.escapeXml(pisteDifficulty)+"\"/>";
        }
        return str;
    }
    
    /**
     * Function set id of parent relation
     * Tags come from one relation value parentRelId store 
     * @param id Id of relation 
     */
    public void setParentRelationId(long id){
        parentRelId = id;
    }
    
    /**
     * function try guess first value from osmc:symbol tag. Check value in list of possible colours
     * when color is not defined, 
     * @param osmcsymbol string containin valaue osmc:symbol tag
     * @return String colour name
     */
    private String getColorFromOsmc(String osmcsymbol){
        int position = osmcsymbol.indexOf(":");
        if (position == -1){
            // value has no color : for this reason try to validate whole value of osmcsymbol string
            if(isOsmcColorValid(osmcsymbol)){
                return osmcsymbol;
            }
            return null;
        }
        return osmcsymbol.substring(0,position);
    }  
    
    private boolean  isTagEmpty(String tag){
        return (tag == null || tag.isEmpty());
    }
    
    private boolean  isNetworkTagValid(){
        if (isRegularBycicle()){
            return Parameters.bycicleNetworkType.containsKey(network);
        }
        if (isMtb()){
            return Parameters.bycicleNetworkType.containsKey(network);
        }
        if (isHiking()) {
            return Parameters.hikingNetworkType.containsKey(network);
        }
        return false;
    }
    
    public boolean  isOsmcColorValid(String color) {
        return Parameters.hikingColourType.contains(color);
    }

    /**
     * Check if network tag defines any level of IWN to LWN
     * @return
     */
    public boolean isIwnNwnRwnLwn(){
        if (this.network != null){
            return Parameters.hikingNetworkType.containsKey(this.network.toLowerCase());
        }
        return false;
    }


    public boolean isOsmSymbolDefined(){
        return (osmc != null && osmc.length() > 0);
    }
        
}
