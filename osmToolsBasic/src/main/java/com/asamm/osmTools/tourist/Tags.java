/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.asamm.osmTools.tourist;

import org.apache.commons.lang3.StringEscapeUtils;
import com.asamm.osmTools.Main;
import com.asamm.osmTools.Parameters;

/**
 *
 * @author volda
 */
public class Tags {
    public String type;
    public String route;
    public String network;
    public String ref;
    public String name;
    public String state;
    public String natural;
    public String layer;
    public String whitesea;
    
    
    // for cyclo nodetrack
    public String rcn;
    public String rcn_ref;
    public String note;
    
    //for hiking
    public String osmcsymbol;
    public String osmc; // store information for mapsforge, boolean whearher tags has values osmcsymbol
    //public ArrayList<String> osmcSymbolElements;
    public String osmc_color;
    public String osmc_background;
    public String osmc_foreground;
    public String kct_barva;
    public String kct_green;
    
    public String colour;
    
    public long parentRelId;
    
    // list of possibles tags for cycloroute
    // http://wiki.openstreetmap.org/wiki/Cycle_routes
    
    
    public void setValue(Tag tag){
        if (tag.key != null && tag.val != null){
            if (tag.key.equals("rcn_ref")){
                rcn_ref = tag.val;
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
//            if (tag.key.equals("note")){
//                note = tag.val;
//            }
            
        }
        
    }
    
    public boolean isRegularBycicle(){
            return (route != null && route.equals("bicycle"));
    }
    
    public boolean  isMtb() {
        return (route != null && route.equals("mtb"));
    }
    
    public boolean isHiking() {
        return (route != null && (route.equals("hiking") ||
                            route.toLowerCase().equals("foot")));
    }
    
    public void parseOsmcSymbol () {
        //test if has tag osmc:symbol
        //osmcSymbolElements = new ArrayList<String>();
        if (osmcsymbol == null || osmcsymbol.isEmpty()){
            return;
        }
        int position = osmcsymbol.indexOf(":");
        if (position == -1){
            // osmcsymbol has no colon : for this reason try to validate whole value of osmcsymbol string
            if(isOsmcColorValid(osmcsymbol)){
                osmc_color = osmcsymbol;
            }
            return;
        }
        int counter = 0;
        //StringTokenizer tokens = new StringTokenizer(osmcsymbol, ":");
        String[] tokens = osmcsymbol.split(":");
        for (int i = 0; i < tokens.length; i++) {
           switch (i){
                case 0: 
                    osmc_color = tokens[i];                  
                    break;
                case 1: 
                    osmc_background = tokens[i];
                    break;
                case 2: 
                    osmc_foreground = tokens[i];
                    break;
            }
        }
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
            
            //test if has tag osmc:symbol
            if (osmcsymbol == null || osmcsymbol.isEmpty()){
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
                      
            // test if osmcsymbol has valid values
            if (osmc_color == null){
                Main.LOG.warning("[warn_hike_003] Relation with id="+this.parentRelId+" has non valid tag osmc:symbol. v=\""+osmcsymbol+"\"");
                return false;
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
            
            //color is corect set mapsforge attribute
            this.osmc = "yes";
            
            //System.out.println("Relation "+parentRelId+" ma network: "+network);
            return true;
        }
        return false;
    }
    
        
    /**
     * 
     * @param refsLength this the length of offset for ref caption. for example 
     *                   if we have two ways overlayd then text will have offset 
     *                  using white space. This is terrible solution - i know
     * @return 
     */
    public String toXml(int refsLength){
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
            String freeSpace = "";
            for (int i = 0; i < 3*refsLength; i++ ){
                freeSpace += " "; 
            }
            str += "\n   <tag k=\"ref\" v=\""+StringEscapeUtils.escapeXml(freeSpace + ref)+"\"/>";
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

        // for cyclo nodetrack
        if (rcn != null){
            str += "\n   <tag k=\"rcn\" v=\""+StringEscapeUtils.escapeXml(rcn)+"\"/>";
        }
        if (rcn_ref != null){
            str += "\n   <tag k=\"name\" v=\""+StringEscapeUtils.escapeXml(rcn_ref)+"\"/>";
        }
        if (state != null){
            str += "\n   <tag k=\"state\" v=\""+state+"\"/>";
        }
//        if (note != null){
//            str += "\n   <tag k=\"note\" v=\""+note+"\"/>";
//        }
        
        //for hiking routes
        if (osmc != null){
            str += "\n   <tag k=\"osmc\" v=\""+osmc+"\"/>";
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
        if (kct_barva != null){
            str += "\n   <tag k=\"kct_barva\" v=\""+StringEscapeUtils.escapeXml(kct_barva)+"\"/>";
        }
        if (kct_green != null){
            str += "\n   <tag k=\"kct_green\" v=\""+StringEscapeUtils.escapeXml(kct_green)+"\"/>";
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
            // value has no colon : for this reason try to validate whole value of osmcsymbol string
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
    
    private boolean  isOsmcColorValid(String color) {
        return Parameters.hikingColourType.contains(color);
    }
        
}
