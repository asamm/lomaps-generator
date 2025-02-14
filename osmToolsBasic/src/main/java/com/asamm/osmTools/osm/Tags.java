/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.asamm.osmTools.osm;

import org.apache.commons.lang3.StringEscapeUtils;

/**
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
     *
     * @param tags
     */
    public Tags(Tags tags) {
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

    public Tags() {

    }

    public void setValue(Tag tag) {
        if (tag.key != null && tag.val != null) {
            if (tag.key.equals("rcn_ref")) {
                rcn_ref = tag.val;
                return;
            }
            if (tag.key.equals("rwn_ref")) {
                rwn_ref = tag.val;
                return;
            }
            if (tag.key.equals("type")) {
                type = tag.val;
                return;
            }
            if (tag.key.equals("route")) {
                route = tag.val.toLowerCase();
                return;
            }
            if (tag.key.equals("network")) {
                network = tag.val.toLowerCase();
                return;
            }
            if (tag.key.equals("ref")) {
                ref = tag.val;
                return;
            }
            if (tag.key.equals("name")) {
                name = tag.val;
                return;
            }
            if (tag.key.equals("rcn")) {
                rcn = tag.val;
                return;
            }

            if (tag.key.equals("tunnel")) {
                tunnel = tag.val;
                return;
            }
            if (tag.key.equals("bridge")) {
                bridge = tag.val;
                return;
            }

            if (tag.key.equals("highway")) {
                highway = tag.val;
                return;
            }
            if (tag.key.equals("tracktype")) {
                tracktype = tag.val;
                return;
            }

            if (tag.key.equals("osmc:symbol")) {
                osmcsymbol = tag.val;
            }
            if (tag.key.equals("state")) {
                state = tag.val;
            }
            if (tag.key.equals("kct_barva")) {
                kct_barva = tag.val;
            }
            if (tag.key.equals("kct_green")) {
                kct_green = tag.val;
            }
            if (tag.key.equals("layer")) {
                layer = tag.val;
            }
            if (tag.key.equals("colour")) {
                colour = tag.val.toLowerCase();
            }
            if (tag.key.equals("piste:type")) {
                pisteType = tag.val.toLowerCase();
            }
            if (tag.key.equals("piste:grooming")) {
                pisteGrooming = tag.val.toLowerCase();
            }
            if (tag.key.equals("piste:difficulty")) {
                pisteDifficulty = tag.val.toLowerCase();
            }
            if (tag.key.equals("sac_scale")) {
                sac_scale = tag.val.toLowerCase();
            }
        }
    }

    /**
     * @return
     */
    public String toXml() {
        String str = "";

        if (type != null) {
            str += "\n   <tag k=\"type\" v=\"" + type + "\"/>";
        }
        if (route != null) {
            str += "\n   <tag k=\"route\" v=\"" + StringEscapeUtils.escapeXml(route) + "\"/>";
        }
        if (natural != null) {
            str += "\n   <tag k=\"natural\" v=\"" + StringEscapeUtils.escapeXml(natural) + "\"/>";
        }
        if (network != null) {
            str += "\n   <tag k=\"network\" v=\"" + StringEscapeUtils.escapeXml(network) + "\"/>";
        }
        if (ref != null) {
            str += "\n   <tag k=\"ref\" v=\"" + StringEscapeUtils.escapeXml(ref) + "\"/>";
        }
        if (name != null) {
            str += "\n   <tag k=\"name\" v=\"" + StringEscapeUtils.escapeXml(name) + "\"/>";
        }

        if (layer != null) {
            str += "\n   <tag k=\"layer\" v=\"" + StringEscapeUtils.escapeXml(layer) + "\"/>";
        }
        if (whitesea != null) {
            str += "\n   <tag k=\"whitesea\" v=\"" + StringEscapeUtils.escapeXml(whitesea) + "\"/>";
        }

        if (tunnel != null) {
            str += "\n   <tag k=\"tunnel\" v=\"" + StringEscapeUtils.escapeXml(tunnel) + "\"/>";
        }
        if (bridge != null) {
            str += "\n   <tag k=\"bridge\" v=\"" + StringEscapeUtils.escapeXml(bridge) + "\"/>";
        }

        if (highway != null) {
            str += "\n   <tag k=\"osmc_highway\" v=\"" + StringEscapeUtils.escapeXml(highway) + "\"/>";
            str += "\n   <tag k=\"lm_highway\" v=\"" + StringEscapeUtils.escapeXml(highway) + "\"/>";
        }
        if (tracktype != null) {
            str += "\n   <tag k=\"tracktype\" v=\"" + StringEscapeUtils.escapeXml(tracktype) + "\"/>";
        }

        // for cyclo nodetrack
        if (rcn != null) {
            str += "\n   <tag k=\"rcn\" v=\"" + StringEscapeUtils.escapeXml(rcn) + "\"/>";
        }
        if (rcn_ref != null) {
            str += "\n   <tag k=\"name\" v=\"" + StringEscapeUtils.escapeXml(rcn_ref) + "\"/>";
        }
        if (rwn_ref != null) {
            str += "\n   <tag k=\"name\" v=\"" + StringEscapeUtils.escapeXml(rwn_ref) + "\"/>";
        }
        if (state != null) {
            str += "\n   <tag k=\"state\" v=\"" + state + "\"/>";
        }
        if (osmc != null) {
            str += "\n   <tag k=\"osmc\" v=\"" + osmc + "\"/>";
        }
        if (osmc_order > 0) {
            str += "\n   <tag k=\"osmc_order\" v=\"" + StringEscapeUtils.escapeXml(String.valueOf(osmc_order)) + "\"/>";
        }
        if (osmc_symbol_order > 0) {
            str += "\n   <tag k=\"osmc_symbol_order\" v=\"" + StringEscapeUtils.escapeXml(String.valueOf(osmc_symbol_order)) + "\"/>";
        }
        if (osmc_color != null) {
            str += "\n   <tag k=\"osmc_color\" v=\"" + StringEscapeUtils.escapeXml(osmc_color) + "\"/>";
        }

        if (osmc_background != null) {
            str += "\n   <tag k=\"osmc_background\" v=\"" + StringEscapeUtils.escapeXml(osmc_background) + "\"/>";
        }
        if (osmc_foreground != null) {
            str += "\n   <tag k=\"osmc_foreground\" v=\"" + StringEscapeUtils.escapeXml(osmc_foreground) + "\"/>";
        }
        if (osmc_text != null) {
            str += "\n   <tag k=\"osmc_text\" v=\"" + StringEscapeUtils.escapeXml(osmc_text) + "\"/>";
        }
        if (osmc_text_length != null) {
            str += "\n   <tag k=\"osmc_text_length\" v=\"" + StringEscapeUtils.escapeXml(osmc_text_length) + "\"/>";
        }
        if (osmc_text_color != null) {
            str += "\n   <tag k=\"osmc_text_color\" v=\"" + StringEscapeUtils.escapeXml(osmc_text_color) + "\"/>";
        }
        if (sac_scale != null) {
            str += "\n   <tag k=\"sac_scale\" v=\"" + StringEscapeUtils.escapeXml(sac_scale) + "\"/>";
        }
        if (kct_barva != null) {
            str += "\n   <tag k=\"kct_barva\" v=\"" + StringEscapeUtils.escapeXml(kct_barva) + "\"/>";
        }
        if (kct_green != null) {
            str += "\n   <tag k=\"kct_green\" v=\"" + StringEscapeUtils.escapeXml(kct_green) + "\"/>";
        }

        if (pisteType != null) {
            str += "\n   <tag k=\"piste:type\" v=\"" + StringEscapeUtils.escapeXml(pisteType) + "\"/>";
        }
        if (pisteGrooming != null) {
            str += "\n   <tag k=\"piste:grooming\" v=\"" + StringEscapeUtils.escapeXml(pisteGrooming) + "\"/>";
        }
        if (pisteDifficulty != null) {
            str += "\n   <tag k=\"piste:difficulty\" v=\"" + StringEscapeUtils.escapeXml(pisteDifficulty) + "\"/>";
        }
        return str;
    }

    /**
     * Function set id of parent relation
     * Tags come from one relation value parentRelId store
     *
     * @param id Id of relation
     */
    public void setParentRelationId(long id) {
        parentRelId = id;
    }


    private boolean isTagEmpty(String tag) {
        return (tag == null || tag.isEmpty());
    }

    public boolean isOsmSymbolDefined() {
        return (osmc != null && osmc.length() > 0);
    }

}
