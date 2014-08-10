package com.asamm.osmTools.generatorDb.data;

import java.util.ArrayList;
import java.util.List;

import com.asamm.osmTools.generatorDb.dataContainer.ADataContainer;
import org.openstreetmap.osmosis.core.domain.v0_6.CommonEntityData;
import org.openstreetmap.osmosis.core.domain.v0_6.Node;
import org.openstreetmap.osmosis.core.domain.v0_6.Way;
import org.openstreetmap.osmosis.core.domain.v0_6.WayNode;

public class WayEx extends Way {

	private ArrayList<Node> nodes;
	
	public WayEx(Way way) {
		// create a copy of a way
		super(new CommonEntityData(way.getId(), way.getVersion(), 
				way.getTimestampContainer(), way.getUser(),
				way.getChangesetId(), way.getTags()), way.getWayNodes());
		
		// prepare nodes
		nodes = new ArrayList<Node>();
	}
	
	public boolean fillNodes(ADataContainer dc) {
		nodes.clear();
		for (int i = 0, n = getWayNodes().size(); i < n; i++) {
			WayNode wn = getWayNodes().get(i);
			Node node = dc.getNodeFromCache(wn.getNodeId());
			if (node != null) {
				nodes.add(node);
			} else {
				return false;
			}
		}
		return true;
	}
	
	public List<Node> getNodes() {
		return nodes;
	}
	
	public double getCenterLatitude() {
		double lat = 0.0;
		for (Node node : nodes) {
			lat += node.getLatitude();
		}
		return lat / nodes.size();
	}
	
	public double getCenterLongitude() {
		double lon = 0.0;
		for (Node node : nodes) {
			lon += node.getLongitude();
		}
		return lon / nodes.size();
	}
}
