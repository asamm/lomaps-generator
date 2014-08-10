package com.asamm.osmTools.generatorDb.dataContainer;

import com.asamm.osmTools.generatorDb.DataWriterDefinition;
import com.asamm.osmTools.generatorDb.data.WayEx;
import com.asamm.osmTools.utils.Logger;
import org.openstreetmap.osmosis.core.domain.v0_6.Node;
import org.openstreetmap.osmosis.core.domain.v0_6.Way;

import java.util.ArrayList;
import java.util.List;

public abstract class ADataContainer {

	private static final String TAG = ADataContainer.class.getSimpleName();
	
	// handle for tags
	private DataWriterDefinition nodeHandler;
	
	// base cache on data
	private List<Long> nodes;
	private List<Long> ways;
	
	// counters
	private int amountOfNodesTested = 0;
	private int amountOfNodesUsed = 0;
	private int amountOfWaysTested = 0;
	private int amountOfWaysUsed = 0;
	private int amountOfRelationsTested = 0;
	private int amountOfRelationsUsed = 0;
	
	public ADataContainer(DataWriterDefinition nodeHandler) throws Exception {
		this.nodeHandler = nodeHandler;
		this.nodes = new ArrayList<Long>();
		this.ways = new ArrayList<Long>();
	}
	
	public abstract void insertNodeToCache(Node node);
	
	public abstract void insertWayToCache(Way way);
	
	public abstract Node getNodeFromCache(long id);
	
	public abstract Way getWayFromCache(long id);
	
	public void addNode(Node node) {
		amountOfNodesTested++;
		
		// insert into database
		insertNodeToCache(node);
		
		// store valid ID's for later
		if (nodeHandler.isValidEntity(node)) {
			nodes.add(node.getId());
		}
	}
	
	public void addWay(Way way) {
		amountOfWaysTested++;
		
		// store way for later if valid
		if (nodeHandler.isValidEntity(way)) {
			insertWayToCache(way);
			ways.add(way.getId());
		}
	}

	public void destroy() {
		// print results
        Logger.i(TAG, "finished...");
		Logger.i(TAG, "total processed nodes: " + amountOfNodesUsed + " / " + amountOfNodesTested);
		Logger.i(TAG, "total processed ways: " + amountOfWaysUsed + " / " + amountOfWaysTested);
		Logger.i(TAG, "total processed relations: " + amountOfRelationsUsed + " / " + amountOfRelationsTested);
	}
	
	public List<Node> getNodes() {
        List<Node> validNodes = new ArrayList<Node>();
		for (long nodeId : nodes) {
			Node node = getNodeFromCache(nodeId);
			if (node != null && nodeHandler.isValidEntity(node)) {
				amountOfNodesUsed++;
				validNodes.add(node);
			}
		}
		return validNodes;
	}
	
	public List<WayEx> getWays() {
        List<WayEx> validWays = new ArrayList<WayEx>();
		for (long wayId : ways) {
			Way way = getWayFromCache(wayId);
			WayEx we = new WayEx(way);
			if (we.fillNodes(this)) {
				amountOfWaysUsed++;
				validWays.add(we);
			} else {
                Logger.e(TAG, "getWays(), cannot fill nodes for way:" + we.getId());
			}
		}
		return validWays;
	}

}
