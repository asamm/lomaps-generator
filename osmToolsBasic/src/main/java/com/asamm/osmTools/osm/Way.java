/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.asamm.osmTools.osm;

import com.asamm.osmTools.utils.Logger;
import org.apache.commons.lang3.StringEscapeUtils;
import org.kxml2.io.KXmlParser;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

/**
 * @author volda
 */
public class Way {

    private static final String TAG = Way.class.getSimpleName();

    long id;
    String visible;
    ArrayList<Node> nodes;
    ArrayList<Tags> tagsArray;
    private Tags originalTags;

    public Way() {
        //define array of nodes
        nodes = new ArrayList<Node>();

        originalTags = new Tags();
    }

//    /**
//     * The international trails are often organized into so called super route relation
//     * https://wiki.openstreetmap.org/wiki/Relation:superroute
//     * This super route may have different color then national routes. For example international E8 route has blue
//     * color but local national routes use RED marked trails for it. If super route isn't removed the Blue a Red color
//     * is printed in the map
//     */
//    private void removeSuperRoute() {
//        if (tagsArray != null) {
//            for (int i = tagsArray.size() - 1; i >= 0; --i) {
//                Tags tags = tagsArray.get(i);
//                if (tags.type != null && tags.type.equalsIgnoreCase("superroute")
//                        && !tags.isOsmSymbolDefined() && !tags.isIwnNwnRwnLwn()) {
//                    // remove this superroute because it doesn't have defined osmc symbol and it isn't any hiking route
//                    tagsArray.remove(i);
//                    //Logger.i(TAG, "Remove superroute, ID: " + tags.parentRelId);
//                }
//            }
//        }
//    }

    public void addNode(Node nd) {
        nodes.add(nd);
    }

    private void fillAttributes(KXmlParser parser) {
        if (parser.getAttributeValue(null, "id") != null) {
            String str = parser.getAttributeValue(null, "id");
            id = Long.valueOf(str);
        }
        if (parser.getAttributeValue(null, "visible") != null) {
            visible = parser.getAttributeValue(null, "visible");
        }
    }

    /**
     * In Germany are often background color the same as foreground
     * The goal is to keep foreground color empty if same as background
     * The osmc symbol with missing foreground will not be displayed
     */
    private void removeSameOsmcForegroundColor() {

        for (Tags tags : tagsArray) {
            if (tags.osmc_background != null && !tags.osmc_background.isEmpty()
                    && tags.osmc_foreground != null && !tags.osmc_foreground.isEmpty()) {

                if (tags.osmc_foreground.startsWith(tags.osmc_background)) {
                    Logger.i(TAG, "Remove osmc foreground for way, ID: " + this.id);
                    tags.osmc_foreground = "";
                }
            }
        }
    }

    /**
     * Combine the ref value and name into combination "name,  ref"
     */
    private void mergeRefAndName() {

        for (Tags tags : this.tagsArray) {
            String ref = (tags.ref == null) ? "" : tags.ref;
            String name = (tags.name == null) ? "" : tags.name;

            if (name.length() > 0) {
                if (ref.length() > 0 && !name.contains(ref)) {
                    // append ref to the existing name
                    tags.name = name + ", " + ref;
                }
            } else if (ref.length() > 0 && !name.contains(ref)) {
                // name is empty replace it by ref value
                tags.name = ref;
            }
        }
    }

    private void copyOriginalTagsToNewWays() {

        for (Tags tags : tagsArray) {

            // try to obtain the original highway tag and set it also into new hiking way
            tags.highway = originalTags.highway;
            tags.sac_scale = originalTags.sac_scale;

            tags.tunnel = originalTags.tunnel;
            tags.bridge = originalTags.bridge;
            tags.layer = originalTags.layer;

            // copy ferry route tag to highway tag
            ferryRouteToHighwayTag(tags, originalTags);
        }
    }

    /**
     * If the original way is ferry route, set the highway=ferry tag
     * The goal is to avoid the situation when ferry route is printed as hiking route. For this reason it is necessary
     * to create a fake 'highway=ferry' tag
     *
     * @param tags         new tags for tourist route
     * @param originalTags original tags from OSM way
     */
    private void ferryRouteToHighwayTag(Tags tags, Tags originalTags) {
        if (originalTags.highway == null || originalTags.highway.isEmpty()) {
            if (originalTags.route != null && originalTags.route.equalsIgnoreCase("ferry")) {
                tags.highway = "ferry";
            }
        }
    }


    /**
     * function write way with attributes nodes and specified tags to string
     *
     * @param tags
     * @return XML string
     */
    public String toXmlString(Tags tags, long wayId) {
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        Date date = new Date();

        String str = "";
        // print way heading
        str += "\n"
                + "  <way id=\"" + wayId + "\" user=\"AsammSW\" ";
        if (visible != null) {
            str += "visible=\"" + StringEscapeUtils.escapeXml(this.visible) + "\"";
        }
        str += "version=\"1\" "
                + "timestamp=\"" + df.format(date) + "\">";
        // print nodes
        for (Node node : nodes) {
            str += "\n   <nd ref=\"" + node.id + "\"/>";
        }
        // print tags string
        str += tags.toXml();

        //close the way
        str += "\n  </way>";
        return str;
    }

    public Tags getOriginalTags() {
        return originalTags;
    }

    public void addOriginalTag(Tag t) {
        this.originalTags.setValue(t);
    }
}