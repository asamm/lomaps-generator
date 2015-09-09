package com.asamm.osmTools.generatorDb.dataContainer;

import com.asamm.osmTools.generatorDb.AWriterDefinition;
import com.asamm.osmTools.generatorDb.address.Street;
import com.asamm.osmTools.generatorDb.data.RelationEx;
import com.asamm.osmTools.generatorDb.data.WayEx;
import com.asamm.osmTools.utils.Logger;
import org.openstreetmap.osmosis.core.domain.v0_6.Node;
import org.openstreetmap.osmosis.core.domain.v0_6.Relation;
import org.openstreetmap.osmosis.core.domain.v0_6.Way;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public abstract class ADataContainer {

	private static final String TAG = ADataContainer.class.getSimpleName();
	
	// handle for tags
	private AWriterDefinition writerDefinition;


    // ids of elements stored in cache
	private List<Long> nodeIds;
	private List<Long> wayIds;
    private List<Long> relationIds;
    private Set<Integer> streetHashSet;
	
	// counters
	private int amountOfNodesTested = 0;
	private int amountOfNodesUsed = 0;
	private int amountOfWaysTested = 0;
	private int amountOfWaysUsed = 0;
	private int amountOfRelationsTested = 0;
	private int amountOfRelationsUsed = 0;
    protected int amountOfWayStreetUsed = 0;
	
	public ADataContainer(AWriterDefinition writerDefinition) throws Exception {
		this.writerDefinition = writerDefinition;
		this.nodeIds = new ArrayList<Long>();
		this.wayIds = new ArrayList<Long>();
        this.relationIds = new ArrayList<>();
        this.streetHashSet = new HashSet<>();
	}
	
	public abstract void insertNodeToCache(Node node);
	
	public abstract void insertWayToCache(Way way);

    public abstract void insertRelationToCache (Relation relation);

    public abstract Node getNodeFromCache(long id);
	
	public abstract Way getWayFromCache(long id);

    public abstract Relation getRelationFromCache (long id);

    public abstract void finalizeCaching();

    protected abstract void insertWayStreetToCache(int hash, Street street);

    public abstract List<Street> getWayStreetsFromCache(int hash);



    public void addNode(Node node) {
		amountOfNodesTested++;
		
		// insert into database
		insertNodeToCache(node);
		
		// store valid ID's for later
		if (writerDefinition.isValidEntity(node)) {
			nodeIds.add(node.getId());
		}
	}
	
	public void addWay(Way way) {
		amountOfWaysTested++;

		// store way for later if valid
		if (writerDefinition.isValidEntity(way)) {
			insertWayToCache(way);
			wayIds.add(way.getId());
		}
	}

    public void addRelation (Relation relation){
        if (writerDefinition.isValidEntity(relation)){
            insertRelationToCache(relation);
            relationIds.add(relation.getId());
        }
    }

    public void addStreet (Street street){
        amountOfWayStreetUsed++;

        int hash = street.hashCode();
        // put street into tmp cache
        insertWayStreetToCache(hash, street);
        streetHashSet.add(hash);
    }

	public void destroy() {
		// print results
        Logger.i(TAG, "finished...");
		Logger.i(TAG, "total processed nodes: " + amountOfNodesUsed + " / " + amountOfNodesTested);
		Logger.i(TAG, "total processed ways: " + amountOfWaysUsed + " / " + amountOfWaysTested);
		Logger.i(TAG, "total processed relations: " + amountOfRelationsUsed + " / " + amountOfRelationsTested);
        Logger.i(TAG, "total processed ways to streets: " + amountOfWayStreetUsed );
	}

    public Node getNode (long nodeId){
        return getNodeFromCache(nodeId);
    }

	public List<Node> getNodes() {
        List<Node> validNodes = new ArrayList<Node>();
		for (long nodeId : nodeIds) {
            Node node = getNode(nodeId);
            if (node != null && writerDefinition.isValidEntity(node)) {
                amountOfNodesUsed++;
                validNodes.add(node);
            }
		}
		return validNodes;
	}

    public WayEx getWay (long wayId){

        Way way = getWayFromCache(wayId);
        return getWay(way);
    }

    public WayEx getWay (Way way){

        if (way == null){
            //Logger.e(TAG, "getWays(), cannot load way from cache. Id:" + wayId);
            return null;
        }

        WayEx we = new WayEx(way);
        if (we.fillNodes(this)) {
            amountOfWaysUsed++;
            return we;
        }
        return null;
    }

	public List<WayEx> getWays() {
        List<WayEx> validWays = new ArrayList<WayEx>();
		for (long wayId : wayIds) {
			WayEx we = getWay(wayId);
            if (we != null){
                validWays.add(we);
            }
		}
		return validWays;
	}


    public RelationEx getRelation (long relationId){

        Relation relation = getRelationFromCache(relationId);
        if (relation == null){
            return null;
        }
        RelationEx re = new RelationEx(relation);
        re.fillMembers(this);
        return re;
    }

    public List<RelationEx> getRelations (){
        List<RelationEx> relations = new ArrayList<>();
        for (long relationId : relationIds){
            RelationEx re = getRelation(relationId);
            if (re != null){
                relations.add(re);
            }
        }
        return relations;
    }



    public List<Long> getNodeIds() {
        return nodeIds;
    }

    public void setNodeIds(List<Long> nodeIds) {
        this.nodeIds = nodeIds;
    }

    public List<Long> getWayIds() {
        return wayIds;
    }

    public void setWayIds(List<Long> wayIds) {
        this.wayIds = wayIds;
    }

    public List<Long> getRelationIds() {
        return relationIds;
    }

    public void setRelationIds(List<Long> relationIds) {
        this.relationIds = relationIds;
    }

    public Set<Integer> getStreetHashSet() {
        return streetHashSet;
    }

}
