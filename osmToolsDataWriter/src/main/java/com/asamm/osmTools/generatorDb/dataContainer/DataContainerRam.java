package com.asamm.osmTools.generatorDb.dataContainer;

import java.util.*;

import com.asamm.osmTools.generatorDb.AWriterDefinition;
import com.asamm.osmTools.generatorDb.address.Street;
import com.asamm.osmTools.utils.Logger;
import org.openstreetmap.osmosis.core.domain.v0_6.Node;
import org.openstreetmap.osmosis.core.domain.v0_6.Relation;
import org.openstreetmap.osmosis.core.domain.v0_6.Way;

public class DataContainerRam extends ADataContainer {

	private Hashtable<Long, Node> nodes;
	private Hashtable<Long, Way> ways;
    private Hashtable<Long, Relation> relations;
    private Hashtable<Integer, List<Street>> wayStreets;

	public DataContainerRam(AWriterDefinition writerDefinition) throws Exception {
		super(writerDefinition);
		nodes = new Hashtable<Long, Node>();
		ways = new Hashtable<Long, Way>();
        relations = new Hashtable<Long, Relation>();
        wayStreets = new Hashtable<>();
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
    public void insertRelationToCache(Relation relation) {
        relations.put(relation.getId(), relation);
    }

    @Override
    public void finalizeWayStreetCaching() {
        // nothing to do
    }

    @Override
	public Node getNodeFromCache(long id) {
		return nodes.get(id);
	}

    @Override
    public List<Node> getNodesFromCache(long[] ids) {
        List<Node> nodes = new ArrayList<>(ids.length);
        for (int i=0; i < ids.length; i++){
            Node node = getNodeFromCache(ids[i]);
            if (node != null){
                //Logger.i("DataContainerRam", "Can not load node with id from cache, id: " + ids[i]);
                nodes.add(node);
            }
        }
        return nodes;
    }

    @Override
	public Way getWayFromCache(long id) {
		return ways.get(id);
	}

    @Override
    public Relation getRelationFromCache(long id) {
        return relations.get(id);
    }

    @Override
    public void finalizeCaching() {
        // nothing to do > index is created only in hdd cache implementation
    }

    public void addWayStreet(Street street){
        super.amountOfWayStreetUsed++;

        int hash = street.hashCode();
        // put street into tmp cache
        insertWayStreetToCache(hash, street);
    }

    @Override
    public void insertWayStreetToCache(int hash, Street street) {
        List<Street> streets = wayStreets.get(hash);
        if (streets == null){
            streets = new ArrayList<>();
        }
        streets.add(street);
        wayStreets.put(hash, streets);
    }

    @Override
    public List<Street> getWayStreetsFromCache(int hash) {
        return wayStreets.get(hash);
    }

    @Override
    public Set<Integer> getStreetHashSet() {
        return wayStreets.keySet();
    }


}
