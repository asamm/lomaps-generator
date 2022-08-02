package com.asamm.osmTools.generatorDb.data;

import com.asamm.osmTools.generatorDb.dataContainer.ADataContainer;
import org.locationtech.jts.geom.Coordinate;
import org.openstreetmap.osmosis.core.domain.v0_6.CommonEntityData;
import org.openstreetmap.osmosis.core.domain.v0_6.Node;
import org.openstreetmap.osmosis.core.domain.v0_6.Way;
import org.openstreetmap.osmosis.core.domain.v0_6.WayNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Custom definition of OSM way entity object
 */
public class WayEx extends Way {

	private List<Node> nodes;
	
	public WayEx(Way way) {
		// create a copy of a way
		super(new CommonEntityData(way.getId(), way.getVersion(), 
				way.getTimestampContainer(), way.getUser(),
				way.getChangesetId(), way.getTags()), way.getWayNodes());
		
		// prepare nodes
		nodes = new ArrayList<Node>();
	}

    public boolean isValid () {
        return nodes.size() > 1; // some border ways has incorrect num of nodes
    }

	public boolean fillNodes(ADataContainer dc) {
		nodes.clear();
        int wayNodesSize = getWayNodes().size();

        long[] ids = new long[wayNodesSize];
		for (int i = 0; i < wayNodesSize; i++) {
			WayNode wn = getWayNodes().get(i);
            ids[i] = wn.getNodeId();
		}

        nodes = dc.getNodesFromCache(ids);
        return true;
        //return (nodes.size() == wayNodesSize);
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

    public Coordinate[] getCoordinates () {

        int numNodes = nodes.size();
        Coordinate[] coordinates = new Coordinate[numNodes];
        for (int i=0; i < numNodes; i++){
            Node node = nodes.get(i);
            Coordinate coordinate = new Coordinate(node.getLongitude(), node.getLatitude());
            coordinates[i] = coordinate;
        }
        return coordinates;
    }
}
