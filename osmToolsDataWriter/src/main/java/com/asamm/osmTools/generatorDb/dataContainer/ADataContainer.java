package com.asamm.osmTools.generatorDb.dataContainer;

import com.asamm.osmTools.generatorDb.AWriterDefinition;
import com.asamm.osmTools.generatorDb.address.Boundary;
import com.asamm.osmTools.generatorDb.address.City;
import com.asamm.osmTools.generatorDb.address.House;
import com.asamm.osmTools.generatorDb.address.Street;
import com.asamm.osmTools.generatorDb.data.RelationEx;
import com.asamm.osmTools.generatorDb.data.WayEx;
import com.asamm.osmTools.generatorDb.utils.BiDiHashMap;
import com.asamm.osmTools.utils.Logger;
import gnu.trove.list.TLongList;
import gnu.trove.list.array.TLongArrayList;
import gnu.trove.map.hash.THashMap;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.openstreetmap.osmosis.core.domain.v0_6.Node;
import org.openstreetmap.osmosis.core.domain.v0_6.Relation;
import org.openstreetmap.osmosis.core.domain.v0_6.Way;

import java.util.*;

public abstract class ADataContainer {

	private static final String TAG = ADataContainer.class.getSimpleName();
	
	// handle for tags
	private AWriterDefinition writerDefinition;


    // ids of elements stored in cache
	private TLongList nodeIds;
	private TLongList wayIds;
    private TLongList relationIds;
    protected Set<Integer> streetHashSet;


    /**
     * Map for houses that have defined the street name or place name but was not possible to find street for them
     * Key for map is the streetName or placeName
     * */
    private THashMap<String, List<House>> housesWithoutStreet;

    /** Center city and the best boundary for it*/
    private BiDiHashMap<City, Boundary> centerCityBoundaryMap;

    /** List of cities that are in the boundary*/
    private THashMap<Boundary, List<City>> citiesInBoundaryMap;

    /** Ids of relation that contains information about street and houses*/
    private TLongList streetRelations;


    // counters
	private int amountOfNodesTested = 0;
	private int amountOfNodesUsed = 0;
	private int amountOfWaysTested = 0;
	private int amountOfWaysUsed = 0;
	private int amountOfRelationsTested = 0;
	private int amountOfRelationsUsed = 0;
    protected int amountOfWayStreetUsed = 0;
    protected int amountOfWayStreetUnnamedUsed = 0;
	
	public ADataContainer(AWriterDefinition writerDefinition) throws Exception {
		this.writerDefinition = writerDefinition;

		this.nodeIds = new TLongArrayList();
		this.wayIds = new TLongArrayList();
        this.relationIds = new TLongArrayList();
        this.streetHashSet = new HashSet<>();
        this.housesWithoutStreet = new THashMap<>();
        this.centerCityBoundaryMap = new BiDiHashMap<>();
        this.citiesInBoundaryMap = new THashMap<>();
        this.streetRelations = new TLongArrayList();
	}


    /**
     * IMPORTANT delete all object be sure what you do
     */
    public void clearAll (){

        this.nodeIds = null;
        this.wayIds = null;
        this.relationIds = null;
        this.streetHashSet = null;
        this.housesWithoutStreet = null;
        this.centerCityBoundaryMap = null;
        this.citiesInBoundaryMap = null;
        this.streetRelations = null;
    }

	public abstract void insertNodeToCache(Node node);
	
	public abstract void insertWayToCache(Way way);

    public abstract void insertRelationToCache (Relation relation);

    public abstract void finalizeWayStreetCaching();

    public abstract void clearWayStreetCache();

    public abstract Node getNodeFromCache(long id);

    public abstract List<Node> getNodesFromCache(long[] ids);
	
	public abstract Way getWayFromCache(long id);

    public abstract Relation getRelationFromCache (long id);

    public abstract void finalizeCaching();

    protected abstract void insertWayStreetToCache(int hash, Street street);

    public abstract List<Street> getWayStreetsFromCache(int hash);

    protected abstract void insertWayStreetUnnamedToCache(Street street);

    public abstract List<Street> getWayStreetsUnnamedFromCache(List<Long> osmIds);


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

    public void addWayStreet(Street street){
        amountOfWayStreetUsed++;

        int hash = new HashCodeBuilder().append(street.getName()).toHashCode();
        // put street into tmp cache
        insertWayStreetToCache(hash, street);
        streetHashSet.add(hash);
    }

    public void addWayStreetUnnamed(Street street){
        amountOfWayStreetUnnamedUsed++;
        insertWayStreetUnnamedToCache(street);
    }

    public void addHouseWithoutStreet(House house) {

        //Logger.i(TAG, "addHouseWithoutStreet (): " + house.toString());

        String key = (house.getStreetName().length() > 0) ? house.getStreetName() : house.getPlace();
        if (key.length() <= 0){
            // no key no record
            return;
        }

        List<House> houses = housesWithoutStreet.get(key);
        if (houses == null){
            houses = new ArrayList<>();
        }
        houses.add(house);
        housesWithoutStreet.put(key, houses);

    }




    public void destroy() {
		// print results
        Logger.i(TAG, "finished...");
		Logger.i(TAG, "total processed nodes: " + amountOfNodesUsed + " / " + amountOfNodesTested);
		Logger.i(TAG, "total processed ways: " + amountOfWaysUsed + " / " + amountOfWaysTested);
		Logger.i(TAG, "total processed relations: " + amountOfRelationsUsed + " / " + amountOfRelationsTested);
        Logger.i(TAG, "total processed ways to streets: " + amountOfWayStreetUsed );
        Logger.i(TAG, "total processed ways for unnamed streets: " + amountOfWayStreetUnnamedUsed );
	}

    public Node getNode (long nodeId){
        return getNodeFromCache(nodeId);
    }

	public List<Node> getNodes() {
        List<Node> validNodes = new ArrayList<Node>();
		for (int i=0, size = nodeIds.size(); i < size; i++) {
            Node node = getNode(nodeIds.get(i));
            if (node != null && writerDefinition.isValidEntity(node)) {
                amountOfNodesUsed++;
                validNodes.add(node);
            }
		}
		return validNodes;
	}

 //   public abstract Iterator<Node> getNodes ();

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
        for (int i=0, size = wayIds.size(); i < size; i++) {
        	WayEx we = getWay(wayIds.get(i));
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
        for (int i=0, size = relationIds.size(); i < size; i++) {
            RelationEx re = getRelation(relationIds.get(i));
            if (re != null){
                relations.add(re);
            }
        }
        return relations;
    }



    public TLongList getNodeIds() {
        return nodeIds;
    }

    public void setNodeIds(TLongList nodeIds) {
        this.nodeIds = nodeIds;
    }

    public TLongList getWayIds() {
        return wayIds;
    }

    public void setWayIds(TLongList wayIds) {
        this.wayIds = wayIds;
    }

    public TLongList getRelationIds() {
        return relationIds;
    }

    public void setRelationIds(TLongList relationIds) {
        this.relationIds = relationIds;
    }

    public Set<Integer> getStreetHashSet() {
        return streetHashSet;
    }

    public THashMap<String, List<House>> getHousesWithoutStreet() {
        return housesWithoutStreet;
    }

    public BiDiHashMap<City, Boundary> getCenterCityBoundaryMap() {
        return centerCityBoundaryMap;
    }

    public THashMap<Boundary, List<City>> getCitiesInBoundaryMap() {
        return citiesInBoundaryMap;
    }

    public TLongList getStreetRelations() {
        return streetRelations;
    }
}
