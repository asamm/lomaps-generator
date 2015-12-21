package com.asamm.osmTools.generatorDb.dataContainer;

import java.util.*;

import com.asamm.osmTools.generatorDb.AWriterDefinition;
import com.asamm.osmTools.generatorDb.address.Street;
import com.asamm.osmTools.utils.Logger;
import gnu.trove.map.hash.THashMap;
import gnu.trove.set.hash.THashSet;
import org.openstreetmap.osmosis.core.domain.v0_6.Node;
import org.openstreetmap.osmosis.core.domain.v0_6.Relation;
import org.openstreetmap.osmosis.core.domain.v0_6.Way;

public class DataContainerRam extends ADataContainer {

	private THashMap<Long, Node> nodes;
	private THashMap<Long, Way> ways;
    private THashMap<Long, Relation> relations;
    private THashMap<Integer, List<Street>> wayStreets;
    private THashMap<Long, Street> wayStreetsUnnamed;

	public DataContainerRam(AWriterDefinition writerDefinition) throws Exception {
		super(writerDefinition);
		nodes = new THashMap<Long, Node>();
		ways = new THashMap<Long, Way>();
        relations = new THashMap<Long, Relation>();
        wayStreets = new THashMap<>();
        wayStreetsUnnamed = new THashMap<>();
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
    public void clearWayStreetCache() {
        streetHashSet = new THashSet<>();
        wayStreets = new THashMap<>();
    }

    @Override
    public List<Street> getWayStreetsFromCache(int hash) {
        return wayStreets.get(hash);
    }

    @Override
    public Set<Integer> getStreetHashSet() {
        return wayStreets.keySet();
    }


    @Override
    protected void insertWayStreetUnnamedToCache(Street street) {
        wayStreetsUnnamed.put(street.getOsmId(), street);
    }

    @Override
    public List<Street> getWayStreetsUnnamedFromCache(List<Long> osmIds) {
        List<Street> ids = new ArrayList<>();
        for (Long id : osmIds){
            ids.add(wayStreetsUnnamed.get(id));
        }
        return ids;
    }


}
