package com.asamm.osmTools.generatorDb.db;

import java.util.Hashtable;

import com.asamm.osmTools.generatorDb.DataWriterDefinition;
import org.openstreetmap.osmosis.core.domain.v0_6.Node;
import org.openstreetmap.osmosis.core.domain.v0_6.Way;

public class DataContainerRam extends ADataContainer {

	private Hashtable<Long, Node> nodes;
	private Hashtable<Long, Way> ways;
	
	public DataContainerRam(DataWriterDefinition nodeHandler) throws Exception {
		super(nodeHandler);
		nodes = new Hashtable<Long, Node>();
		ways = new Hashtable<Long, Way>();
	}

	@Override
	public void insertNodeToCache(Node node) {
		nodes.put(node.getId(), node);
	}

	@Override
	public void insertWayToCache(Way way) {
		ways.put(way.getId(), way);
	}

	@Override
	public Node getNodeFromCache(long id) {
		return nodes.get(id);
	}

	@Override
	public Way getWayFromCache(long id) {
		return ways.get(id);
	}

}
