package com.asamm.osmTools.generatorDb.data;

import com.asamm.locus.data.spatialite.DbPoiConst;
import com.asamm.osmTools.generatorDb.DataWriterDefinition;
import com.asamm.osmTools.utils.Consts;
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
	
	public static OsmPoi create(Entity entity, DataWriterDefinition nodeHandler) {
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
	protected double lon = 0.0;
	protected double lat = 0.0;
	
	// flag if it's a wanted poi
	private List<DataWriterDefinition.DbRootSubContainer> rootSubContainer;
	private List<Tag> tags;
	
	// handler for nodes (generated from XML)
	private DataWriterDefinition nodeHandler;
		
	private OsmPoi(Entity entity, DataWriterDefinition nodeHandler) {
		super(entity);
		this.nodeHandler = nodeHandler;
		this.rootSubContainer = new ArrayList<>();
		this.tags = new ArrayList<>();
		
		// finally extract data
		handleTags();
		
		// handle entity itself
		if (entityType == Consts.EntityType.POIS) {
			handleNode((Node) entity);
		} else if (entityType == Consts.EntityType.WAYS) {
			handleWay((WayEx) entity);
		}
	}
	
	private void handleNode(Node node) {
		lon = node.getLongitude();
		lat = node.getLatitude();
	}
	
	private void handleWay(WayEx way) {
		lon = way.getCenterLongitude();
		lat = way.getCenterLatitude();
	}
	
	public double getLon() {
		return lon;
	}

	public double getLat() {
		return lat;
	}
	
	public List<DataWriterDefinition.DbRootSubContainer> getRootSubContainers() {
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
        return !(lon == 0.0 && lat == 0.0);
    }

	@Override
	protected boolean handleTag(String key, String value) {
		DataWriterDefinition.DbRootSubContainer node =
                nodeHandler.getNodeContainer(entityType, key, value);
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
					tag.value = tag.value + DbPoiConst.DATA_SEPARATOR + value;
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
