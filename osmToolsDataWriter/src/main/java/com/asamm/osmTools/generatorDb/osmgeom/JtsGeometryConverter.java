package com.asamm.osmTools.generatorDb.osmgeom;

import org.locationtech.jts.geom.*;
import gnu.trove.map.hash.THashMap;
import org.openstreetmap.osmosis.core.container.v0_6.*;
import org.openstreetmap.osmosis.core.domain.v0_6.*;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Created by voldapet on 17/1/2017.
 */
public class JtsGeometryConverter {

    /*
     * Every created OSM entity must have set the user info who created it
     */
    private static final int VIRTUAL_OSM_USER_ID = 1;
    private static final String VIRTUAL_OSM_USER_NAME = "asammsw";
    /*
     * Common entity data. Sets of metadata that are assigned to all created OSM entities
     */
    OsmUser osmUser;

    /*
     * Initial start IDs for created OSM entities
     */
    private long osmNodeId = (long) 1.001e15;
    private long osmWayId = (long) 1.002e15;
    private long osmRelationId = (long) 1.003e15;


    /*
         * Storage for created OSM nodes
         */
    private THashMap<Long, NodeContainer> nodes;

    /*
     * Storage for created OSM ways
     */
    private THashMap<Long, WayContainer> ways;

    /*
     * Storage for created relations (multipolygons)
     */
    private THashMap<Long, RelationContainer> relations;

    /*
     * Factories
     */
    NodeContainerFactory nodeContainerFactory;
    WayContainerFactory wayContainerFactory;
    RelationContainerFactory relationContainerFactory;


    /**
     * It works as converter and also storage. Every added JTS geometry is converted into OSM and save into
     * Node/way/relation container storage
     */
    public JtsGeometryConverter () {

        // initialize needed containers
        nodes = new THashMap<>();
        ways = new THashMap<>();
        relations = new THashMap<>();

        //initialize object factories
        nodeContainerFactory = new NodeContainerFactory();
        wayContainerFactory = new WayContainerFactory();
        relationContainerFactory = new RelationContainerFactory();

        // initialize virtual OSM user that is set to all created entities
        osmUser = new OsmUser( VIRTUAL_OSM_USER_ID, VIRTUAL_OSM_USER_NAME);

    }


    public Entity addJtsGeometry (Geometry geometry, List<Tag> tags){

        if (geometry instanceof Point){
            // create empty multipoly
            return addPoint((Point) geometry, tags);
        }
        else if (geometry instanceof LineString){
            return  addLineString ((LineString) geometry, tags);
        }
        else if (geometry instanceof Polygon){
            return  addPolygon ((Polygon) geometry, tags);
        }


        else {
            throw new IllegalArgumentException("addJtsGeometry: Not implemented yet " +
                    "- converter can not procces JTS geom - " + geometry.getGeometryType());
        }


    }

    /**
     * Convert JTS point geometry into osm node
     * @param point point to convert
     * @param tags osm tags for created osm node
     * @return created OSM node
     */
    public Node addPoint(Point point, List<Tag> tags){
        if (tags == null){
            tags = new ArrayList<>();
        }
        CommonEntityData ced = getCommonEntityData(osmNodeId++, tags);
        Node node = new Node(ced, point.getY(), point.getX());

        // add node into storage
        addNode(node);

        return node;
    }

    /**
     * Convert JTS polygon geometry into closed OSM way. In case that
     * polygon has holes than multipolygon relation is also created to reflect the hole.
     * Converted polygon is added into Node/Way/Relation storage
     * @param polygon polygon to convert
     * @param tags tags to assign to converted object
     * @return return created OSM way for simple polygon or OSM relation for complicated polygon with holes
     */
    public Entity addPolygon (Polygon polygon, List<Tag> tags){


        // outer poly
        int numInteriors = polygon.getNumInteriorRing();

        if (numInteriors == 0){
            // create closed way from simple polygon without hole
            return addLineString (polygon.getExteriorRing(), tags);
        }

        // POLYGON WITH HOLES
        //
        // it's created as several closed way with different member role (outer/inner)
        // see http://wiki.openstreetmap.org/wiki/Relation:multipolygon

        List<RelationMember> relationMembers = new ArrayList<>();

        // create outer way
        LineString exteriorLineStrig = polygon.getExteriorRing();
        Way exteriorWay = addLineString(exteriorLineStrig, new ArrayList<Tag>());
        RelationMember rm = new RelationMember(exteriorWay.getId(), exteriorWay.getType(), "outer");
        relationMembers.add(rm);

        // create inner ways (hole polygons)
        for (int i=0; i < numInteriors; i++){
            LineString lsInterior = polygon.getInteriorRingN(i);
            // create way from interior line strings no tags are added
            Way interiorWay = addLineString(lsInterior, new ArrayList<Tag>());

            rm = new RelationMember(interiorWay.getId(),interiorWay.getType(),"inner");
            relationMembers.add(rm);
        }

        // create relation
        tags = new ArrayList<>(tags);
        Tag tag = new Tag("type", "multipolygon");
        tags.add(tag);
        CommonEntityData ced = getCommonEntityData(osmRelationId++, tags);
        Relation relation = new Relation(ced, relationMembers);
        addRelation(relation);

        return relation;
    }

    /**
     * Basic method to add new ways into storage of converted OSM entities
     * It convert JTS Linestring and save nodes and ways in entity storage
     * @param lineString line to convert into OSM entity
     * @param tags tags to assing to converted OSM entity
     * @return created OSM way
     */
    private Way addLineString(LineString lineString, List<Tag> tags) {

        boolean isClosed = lineString.isClosed();
        long firstNodeId = -1;
        Coordinate[] coordinates = lineString.getCoordinates();

        List<WayNode> wayNodes = new ArrayList<>();

        // create and insert way nodes
        for (int i=0; i < coordinates.length; i++){
            if (isClosed && i == coordinates.length -1){
                // for the last coordinate CLOSED way do not create point again but use the first one
                WayNode wayNode = new WayNode(firstNodeId);
                wayNodes.add(wayNode);
                continue;
            }

            long nodeId = addNode(coordinates[i].y, coordinates[i].x, new ArrayList<Tag>());

            // remember id of first added node (only for closed linestring
            if (isClosed && i == 0){
                firstNodeId = nodeId;
            }
            WayNode wayNode = new WayNode(nodeId);
            wayNodes.add(wayNode);
        }

        //create way itself
        CommonEntityData ced = getCommonEntityData(osmWayId++, tags);
        Way way = new Way(ced, wayNodes);

        // add way into storage
        addWay(way);

        return way;
    }

    /****************************************************
     *                  OSM ENTITIES
     * **************************************************/

    /**
     * Create OSM node and add it into entity storage
     * @param lat node latitude
     * @param lon node longitude
     * @param tags tags to assing to OSM node entity
     * @return id of created OSM node
     */
    private long addNode (double lat, double lon, List<Tag> tags){
        CommonEntityData ced = getCommonEntityData(osmNodeId++, tags);

        Node node = new Node(ced, lat, lon);

        return addNode(node);
    }

    /**
     * Add node into entity storage
     * @param node node to add into storage
     * @return id of added node
     */
    private long addNode(Node node){

        NodeContainer nodeContainer = (NodeContainer) nodeContainerFactory.createContainer(node);

        this.nodes.put(node.getId(), nodeContainer);

        return node.getId();
    }

    /**
     * Add way into storege. To speed up the procces, the entity doesn't check if way nodes exist in storage.
     * We expect that all geom are covnerted properly
     * @param way way to add into storage
     * @return id added way
     */
    private long addWay(Way way){

        WayContainer wayContainer = (WayContainer) wayContainerFactory.createContainer(way);

//        List<WayNode> wayNodes = way.getWayNodes();
//
//        // test if way node exist in node storage
//        for (int i= wayNodes.size() -1; i >= 0; i--){
//            long wayNodeId = wayNodes.get(i).getNodeId();
//
//            if ( !nodes.containsKey(wayNodeId)){
//                //strategy if some node does not exist
//                wayNodes.remove(i);
//            }
//        }

        this.ways.put(way.getId(), wayContainer);

        return way.getId();
    }


    private long addRelation (Relation relation){
        RelationContainer relationContainer = (RelationContainer) relationContainerFactory.createContainer(relation);

        this.relations.put(relation.getId(), relationContainer);
        return relation.getId();
    }
    /**
     * Create sets of metadata that are assigned to entity. OsmUser and version of data is the same for all created
     * entities
     * @param entityId id of entity to create
     * @param tags list of tags that are assigned to entity
     * @return entity data
     */
    private CommonEntityData getCommonEntityData(long entityId, List<Tag> tags) {

        return new CommonEntityData(entityId,1,new Date(),this.osmUser,1,tags);
    }


    /****************************************************
     *                  GETTERS
     * **************************************************/

    public THashMap<Long, NodeContainer> getNodes() {
        return nodes;
    }

    public THashMap<Long, WayContainer> getWays() {
        return ways;
    }

    public THashMap<Long, RelationContainer> getRelations() {
        return relations;
    }

}
