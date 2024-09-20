package com.asamm.osmTools.generatorDb.data;

import com.asamm.locus.features.loMaps.LoMapsDbConst;
import com.asamm.osmTools.generatorDb.input.definition.WriterPoiDefinition;
import com.asamm.osmTools.utils.Logger;
import org.openstreetmap.osmosis.core.domain.v0_6.Entity;
import org.openstreetmap.osmosis.core.domain.v0_6.Node;

import java.util.ArrayList;
import java.util.List;

public class OsmPoi extends AOsmObject {

    public static class Tag {

        public String key;
        public String value;

        public Tag(String key, String value) {
            this.key = key;
            this.value = value;
        }
    }

    public static OsmPoi create(Entity entity, WriterPoiDefinition nodeHandler) {

        if (entity.getId() == 248801135) {
            Logger.i("Create", "Start POI from entity: " + entity.toString());
        }

        // check type
        if (!(entity instanceof Node || entity instanceof WayEx)) {
            return null;
        }

        // process nodes
        OsmPoi poi = new OsmPoi(entity, nodeHandler);
        if (poi.isValid()) {
            return poi;
        } else {
            return null;
        }
    }

    // location part
    private double mLon = 0.0;
    private double mLat = 0.0;

    // flag if it's a wanted poi
    private List<WriterPoiDefinition.DbRootSubContainer> rootSubContainer;
    private List<Tag> tags;

    // handler for nodes (generated from XML)
    private WriterPoiDefinition nodeHandler;

    private OsmPoi(Entity entity, WriterPoiDefinition nodeHandler) {
        super(entity);
        this.nodeHandler = nodeHandler;
        this.rootSubContainer = new ArrayList<>();
        this.tags = new ArrayList<>();

        // finally extract data
        handleTags();

        // handle entity itself
        if (getEntityType() == LoMapsDbConst.EntityType.POIS) {
            handleNode((Node) entity);
        } else if (getEntityType() == LoMapsDbConst.EntityType.WAYS) {
            handleWay((WayEx) entity);
        }
    }

    private void handleNode(Node node) {
        mLon = node.getLongitude();
        mLat = node.getLatitude();
    }

    private void handleWay(WayEx way) {

        int size = way.getNodes().size();
        if (size <= 0) {
            return;
        }
        Node node = way.getNodes().get(0);
        double longitudeMin = node.getLongitude();
        double longitudeMax = node.getLongitude();
        double latitudeMax = node.getLatitude();
        double latitudeMin = node.getLatitude();

        for (int i = 1; i < size; i++) {
            node = way.getNodes().get(i);
            if (node.getLongitude() < longitudeMin) {
                longitudeMin = node.getLongitude();
            } else if (node.getLongitude() > longitudeMax) {
                longitudeMax = node.getLongitude();
            }

            if (node.getLatitude() < latitudeMin) {
                latitudeMin = node.getLatitude();
            } else if (node.getLatitude() > latitudeMax) {
                latitudeMax = node.getLatitude();
            }
        }

        mLon = (longitudeMin + longitudeMax) / 2;
        mLat = (latitudeMax + latitudeMin) / 2;
    }

    public double getLon() {
        return mLon;
    }

    public double getLat() {
        return mLat;
    }

    public List<WriterPoiDefinition.DbRootSubContainer> getRootSubContainers() {
        return rootSubContainer;
    }

    public List<Tag> getTags() {
        return tags;
    }

    /**************************************************/
    /*                  PARSE DATA                    */

    /**************************************************/

    @Override
    protected boolean isValidPrivate() {

        // check type
        if (rootSubContainer == null || rootSubContainer.size() == 0) {
            return false;
        }

        // check coordinates
        return !(mLon == 0.0 && mLat == 0.0);
    }

    @Override
    protected boolean handleTag(String key, String value) {
        WriterPoiDefinition.DbRootSubContainer node =
                nodeHandler.getNodeContainer(getEntityType(), key, value);
        if (node != null) {
            rootSubContainer.add(node);
            tags.add(new Tag(key, value));
            return true;
        }

        // check also if key should be added as separate value
        String newKey = nodeHandler.isKeySupportedSingle(key);
        if (newKey != null) {
            tags.add(new Tag(key, value));
            return true;
        }

        // check if key is in details
        newKey = nodeHandler.isKeySupportedMulti(key);
        if (newKey != null) {
            // check if "key" already exists
            for (int i = 0, m = tags.size(); i < m; i++) {
                Tag tag = tags.get(i);
                if (tag.key.equals(newKey)) {
                    tag.value = tag.value + LoMapsDbConst.DATA_SEPARATOR + value;
                    return true;
                }
            }

            // add as a new tag
            tags.add(new Tag(newKey, value));
            return true;
        }
        return false;
    }

//	if (preferredLanguage != null
//////		&& !foundPreferredLanguageName) {
//////	Matcher matcher = NAME_LANGUAGE_PATTERN.matcher(key);
//////	if (matcher.matches()) {
//////		String language = matcher.group(3);
//////		if (language.equalsIgnoreCase(preferredLanguage)) {
//////			name = tag.getValue();
//////			foundPreferredLanguageName = true;
//////		}
//////	}
}
