package com.asamm.osmTools.generatorDb.address;

import com.asamm.osmTools.generatorDb.data.OsmConst;
import com.asamm.osmTools.generatorDb.data.WayEx;
import com.asamm.osmTools.generatorDb.dataContainer.ADataContainer;
import com.asamm.osmTools.generatorDb.index.IndexController;
import com.asamm.osmTools.generatorDb.utils.GeomUtils;
import com.asamm.osmTools.generatorDb.utils.OsmUtils;
import com.asamm.osmTools.generatorDb.utils.Utils;
import com.asamm.osmTools.utils.Logger;
import com.vividsolutions.jts.geom.*;
import com.vividsolutions.jts.index.strtree.STRtree;
import com.vividsolutions.jts.operation.union.UnaryUnionOp;
import com.vividsolutions.jts.simplify.DouglasPeuckerSimplifier;
import gnu.trove.list.TLongList;
import org.openstreetmap.osmosis.core.domain.v0_6.Entity;
import org.openstreetmap.osmosis.core.domain.v0_6.EntityType;
import org.openstreetmap.osmosis.core.domain.v0_6.Node;
import org.openstreetmap.osmosis.core.domain.v0_6.Way;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Created by voldapet on 12/1/2017.
 */
public class ResidentialAreaCreator {

    private static final String TAG = IndexController.class.getSimpleName();


    private static final int BUILDING_RESIDENTIAL_BUFFER_SIZE = 50; // meters

    /*
     * JTS index of areas that are taged as residential in defautl OSM data
     */
    private STRtree residentialAreasIndex;

    /*
     * List of regions that can be residential
     */
    private List<Polygon> residentPolygons;

    private List<Polygon> buildings;

    ADataContainer dc;

    GeometryFactory geometryFactory;

    public ResidentialAreaCreator(ADataContainer dc) {

        this.dc = dc;

        this.residentialAreasIndex = new STRtree();

        this.residentPolygons = new ArrayList<>();

        this.buildings = new ArrayList<>();

        this.geometryFactory = new GeometryFactory();
    }

    /**
     * Generate residential areas
     */
    public void generate() {

        Logger.i(TAG, "Create polygons from default OSM ways");
        createPolygonsFromDefaultAreas();

        residentialAreasIndex.build();

        Logger.i(TAG, "Create polygons from buildings");
        createPolygonsFromBuilding();


        // Union all possible residential polygons into one geom
        long start = System.currentTimeMillis();
        UnaryUnionOp unaryUnionOp = new UnaryUnionOp(residentPolygons);
        Geometry unionGeom = unaryUnionOp.union();
        Logger.i(TAG, "Union takes: " + (System.currentTimeMillis() - start) / 1000.0);

        if (unionGeom == null){
            Logger.w(TAG, "No residental region or building detect");
            return;
        }

        Geometry residentialGeom = simplifyResidentialGeom(unionGeom);

        com.asamm.osmTools.utils.Utils.writeStringToFile(
                new File("residential.geojson"), GeomUtils.geomToGeoJson(residentialGeom) ,false);
    }

    /*
     *  Read only ways that are landuse = residential and create polygons from it
     */
    private void createPolygonsFromDefaultAreas (){

        TLongList wayIds = dc.getWayIds();
        for (int i=0, size = wayIds.size(); i < size; i++) {
            Way way = dc.getWayFromCache(wayIds.get(i));

            if (way == null){
                continue;
            }

            String landuse = OsmUtils.getTagValue(way, OsmConst.OSMTagKey.LANDUSE);

            // Create polygon from residential areas
            if (Utils.objectEquals(landuse, "residential") || Utils.objectEquals(landuse, "industrial") || Utils.objectEquals(landuse, "railway") ) {
                WayEx wayEx = dc.getWay(way.getId());

                // create polygon of residential area from this way and add it into index and list of residential poly
                Polygon residentialPoly = GeomUtils.createPolyFromOuterWay(wayEx, true);
                if (residentialPoly != null && residentialPoly.isValid()){
                    residentPolygons.add(residentialPoly);
                    residentialAreasIndex.insert(residentialPoly.getEnvelopeInternal(), residentialPoly);
                }
            }
        }
    }

    /**
     * Find building, test if are outside any residential area. If yes then create simple rectangle around building
     * These buffered building polygon will be later used for union
     */
    private void createPolygonsFromBuilding(){

        double bufferD = Utils.metersToDeg(BUILDING_RESIDENTIAL_BUFFER_SIZE);

        //FROM WAYS
        TLongList wayIds = dc.getWayIds();
        for (int i=0, size = wayIds.size(); i < size; i++) {
            Way way = dc.getWayFromCache(wayIds.get(i));

            if (way == null){
                continue;
            }
            String buildingValue = OsmUtils.getTagValue(way, OsmConst.OSMTagKey.BUILDING);

            if (buildingValue == null) {
                // way is not building skip it
                continue;
            }

            WayEx wayEx = dc.getWay(way.getId());
            if (wayEx == null || intersectWithResidentialAreas(wayEx.getCenterLongitude(), wayEx.getCenterLatitude())) {
                // this building intersect with any default OSM residential area > do not create poly around building
                continue;
            }

            Coordinate coordinate = new Coordinate(wayEx.getCenterLongitude(), wayEx.getCenterLatitude());
            Polygon polygon = GeomUtils.createRectangle(coordinate, BUILDING_RESIDENTIAL_BUFFER_SIZE);
            residentPolygons.add(polygon);
        }

        // FROM NODES

        TLongList nodeIds = dc.getNodeIds();
        for (int i=0, size = wayIds.size(); i < size; i++) {
            Node node = dc.getNodeFromCache(nodeIds.get(i));
            if (node == null){
                continue;
            }
            String buildingKey = OsmUtils.getTagValue(node, OsmConst.OSMTagKey.BUILDING);

            if (buildingKey == null) {
                // node is not building skip it
                continue;
            }

            if ( intersectWithResidentialAreas(node.getLongitude(), node.getLatitude())) {
                // this building intersect with any default OSM residential area > do not create poly around building
                continue;
            }
            Coordinate coordinate = new Coordinate(node.getLongitude(), node.getLatitude());
            Polygon polygon = GeomUtils.createRectangle(coordinate, BUILDING_RESIDENTIAL_BUFFER_SIZE);
            residentPolygons.add(polygon);
        }
    }


    /**
     * Test if given coordinates intersect with any default OSM residential areas
     * @param lon
     * @param lat
     * @return <code>true</code> when coordinates lies in OSM default residential area
     */
    private boolean intersectWithResidentialAreas (double lon, double lat){

        // use index to get possible residential areas that could intersecty with specified coordinates
        Point point = geometryFactory.createPoint(new Coordinate(lon, lat));
        List<Polygon>  intersectPoly = residentialAreasIndex.query(point.getEnvelopeInternal());

        if (intersectPoly.size() == 0){
            return false;
        }

        for (Polygon polygon : intersectPoly){

            if (polygon.intersects(point)){
                return true;
            }
            //Logger.i(TAG, GeomUtils.geomToGeoJson(polygon));
        }

        // TODO query test only bbox it would be more precise to exactly test the result from index if really intersect
        // but for our need is bbox enough
        return false;
    }

    /**
     * Do some logic to simplify geometry, remove holes, create areas more smooth
     * @param geom geometry to simplify
     * @return
     */
    private Geometry simplifyResidentialGeom (Geometry geom){

        // simplify joined geometry
        double distanceDeg = Utils.distanceToDeg(geom.getCoordinate(), 25);
        geom = DouglasPeuckerSimplifier.simplify(geom, distanceDeg);

        // remove too small geometries
        double minArea =  Utils.metersToDeg(200) *  Utils.metersToDeg(200);
        List<Polygon> polygons = new ArrayList<>();
        for (int i=0, size = geom.getNumGeometries(); i < size; i++){
            Polygon polygon = (Polygon) geom.getGeometryN(i);
            if (polygon.getArea() > minArea && polygon.getNumPoints() > 4 ){
                polygons.add(polygon);
            }
        }

        // union rest of polygons again
        UnaryUnionOp unaryUnionOp = new UnaryUnionOp(polygons);
        Geometry residentialGeom = unaryUnionOp.union();
        // buffer and create nagative buffer to create polygons little bit smooth
        residentialGeom = residentialGeom.buffer(Utils.distanceToDeg(residentialGeom.getCoordinate(), 50));
        residentialGeom = residentialGeom.buffer(-Utils.distanceToDeg(residentialGeom.getCoordinate(), 60));

        // simplify again
        residentialGeom = DouglasPeuckerSimplifier.simplify(residentialGeom, distanceDeg);
        residentialGeom = removeHoles(residentialGeom);

        // remove triangles
        polygons = new ArrayList<>();
        for (int i = 0, size = residentialGeom.getNumGeometries(); i < size; i++){
            Polygon polygon = (Polygon) residentialGeom.getGeometryN(i);
            if (polygon.getNumPoints() > 4 ){
                polygons.add(polygon);
            }
        }

        unaryUnionOp = new UnaryUnionOp(polygons);
        residentialGeom = unaryUnionOp.union();
        return residentialGeom;
    }

    /**
     * Test if geometry contains any holes. If are of hole is bigger than limit > hole is removed
     * @param geom geometry to remove small holes
     * @return geometry without small holes
     */
    private Geometry removeHoles (Geometry geom){

        // holes smaller than this area will be removed
        double minHoleArea =  Utils.metersToDeg(500) *  Utils.metersToDeg(500);

        int numGeometries = geom.getNumGeometries();

        List<Polygon> reCreatedPolygons = new ArrayList<>();
        for (int i=0; i < numGeometries; i++){
            Polygon polygon = (Polygon) geom.getGeometryN(i);
            LinearRing lrExterior =  (LinearRing) polygon.getExteriorRing();

            int numHoles = polygon.getNumInteriorRing();
            if (numHoles == 0){
                reCreatedPolygons.add(polygon);
                continue;
            }

            // get geometry for all holes
            ArrayList bigHoles = new ArrayList();
            for (int t = 0; t < numHoles;t++) {
                LinearRing lr_hole =  (LinearRing) polygon.getInteriorRingN(t);
                // create temporary polygon and test area
                Polygon p = lr_hole.getFactory().createPolygon(lr_hole,null);

                if (p.getArea() > minHoleArea) {
                    // do not remove this hole
                    bigHoles.add(lr_hole);
                }
            }
            // create new polygon only with big holes
            polygon = polygon.getFactory().createPolygon(lrExterior,(LinearRing[]) bigHoles.toArray(new LinearRing[0]));
            Logger.i(TAG, "Polygon after hole cleaning " + GeomUtils.geomToGeoJson(polygon));

            reCreatedPolygons.add(polygon);
        }

        // Merge re-created polygons back into one geom
        UnaryUnionOp unaryUnionOp = new UnaryUnionOp(reCreatedPolygons);

        return unaryUnionOp.union();
    }




}
