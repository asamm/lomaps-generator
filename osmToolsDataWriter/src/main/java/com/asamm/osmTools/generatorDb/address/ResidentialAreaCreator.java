package com.asamm.osmTools.generatorDb.address;

import com.asamm.osmTools.generatorDb.data.OsmConst;
import com.asamm.osmTools.generatorDb.data.WayEx;
import com.asamm.osmTools.generatorDb.dataContainer.ADataContainer;
import com.asamm.osmTools.generatorDb.utils.GeomUtils;
import com.asamm.osmTools.generatorDb.utils.OsmUtils;
import com.asamm.osmTools.generatorDb.utils.Utils;
import com.asamm.osmTools.utils.Logger;
import com.vividsolutions.jts.geom.*;
import com.vividsolutions.jts.index.strtree.STRtree;
import com.vividsolutions.jts.operation.union.UnaryUnionOp;
import com.vividsolutions.jts.simplify.DouglasPeuckerSimplifier;
import gnu.trove.list.TLongList;
import org.openstreetmap.osmosis.core.domain.v0_6.Node;
import org.openstreetmap.osmosis.core.domain.v0_6.Way;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by voldapet on 12/1/2017.
 */
public class ResidentialAreaCreator {

    private static final String TAG = ResidentialAreaCreator.class.getSimpleName();

    private static final String[] VALID_LANDUSE_TAGS = new String[]{
            "residential", "industrial", "railway", "commercial", "hospital", "brownfield", "cemetery"};

    private static final int BUILDING_RESIDENTIAL_BUFFER_SIZE = 50; // meters

    private static final int MAX_BUILDING_POLY_COUNT_FOR_UNION = (int) 1e6;

    /*
     * JTS index of areas that are taged as residential in defautl OSM data
     */
    private STRtree residentialAreasIndex;

    /*
     * Final polygons that can be residential and are use for whole union and buffering
     */
    private List<Geometry> residentPolygons;

    /*
     * Polygons created from OSM landuse areas. Inside this area aren't created new building rectangles
     */
    private List<Geometry> landusePolygons;

    /*
     * List store building rectagles that are joined in specified size;
     */
    private List<Geometry> tmpBuildingPolygons;

    /*
     * Counter keeps actual size of #tmpBuildingPolygons for union when #MAX_BUILDING_POLY_COUNT_FOR_UNION is reached
     */
    private int tmpBuildingCounter = 0;

    /*
     * Global counter how many building rectangles was created during generation
     */
    private int residentalBuildingCounter = 0;


    ADataContainer dc;

    GeometryFactory geometryFactory;

    public ResidentialAreaCreator(ADataContainer dc) {

        this.dc = dc;

        this.residentialAreasIndex = new STRtree();

        this.residentPolygons = new ArrayList<>();
        this.landusePolygons = new ArrayList<>();
        this.tmpBuildingPolygons = new ArrayList<>();

        this.geometryFactory = new GeometryFactory();
    }

    /**
     * Generate residential areas
     */
    public List<Polygon> generate() {

        Logger.i(TAG, "Create polygons from default OSM ways");
        createPolygonsFromLanduseAreas();

        residentialAreasIndex.build();

        Logger.i(TAG, "Create polygons from buildings");
        createPolygonsFromBuilding();

        // Union all possible residential polygons into one geom
        Logger.i(TAG, "generate: Start final union of all geometries");
        long start = System.currentTimeMillis();
        UnaryUnionOp unaryUnionOp = new UnaryUnionOp(residentPolygons);
        Geometry unionGeom = unaryUnionOp.union();
        Logger.i(TAG, "generate: Union takes: " + (System.currentTimeMillis() - start) / 1000.0);

        if (unionGeom == null) {
            Logger.w(TAG, "generate: No residential region or building detect");
            return new ArrayList<>();
        }

        Logger.i(TAG, "generate: Simplify residential areas ");
        List<Polygon> polygons = simplifyResidentialGeom(unionGeom);

        //writeResultToGeoJson(polygons);

        return polygons;
    }


    /*
     *  Read only ways that are landuse = residential and create polygons from it
     */
    private void createPolygonsFromLanduseAreas() {



        TLongList wayIds = dc.getWayIds();
        for (int i = 0, size = wayIds.size(); i < size; i++) {
            Way way = dc.getWayFromCache(wayIds.get(i));

            if (way == null || !isValidLanduse(way)) {
                continue;
            }

            // this way seem to be valid landuse > create polygon from it
            WayEx wayEx = dc.getWay(way.getId());

            // create polygon of residential area from this way and add it into index and list of residential poly
            Geometry landusePoly = GeomUtils.createPolyFromOuterWay(wayEx, true);

            if (landusePoly != null && landusePoly.isValid()) {
                // due to non noded intersection use workaround with small buffer
                landusePoly = landusePoly.buffer(Utils.distanceToDeg(landusePoly.getCoordinate(), 1));

                landusePolygons.add(landusePoly);
                residentialAreasIndex.insert(landusePoly.getEnvelopeInternal(), landusePoly);
            }
        }

        // union osm landuse areas
        Logger.i(TAG, "createPolygonsFromLanduseAreas: Union landuse areas. Num of poly: " + landusePolygons.size());
        UnaryUnionOp unaryUnionOp = new UnaryUnionOp(landusePolygons);
        Geometry landuseGeom = unaryUnionOp.union();
        if (landuseGeom == null){
            // no land use geom was created from data
            return;
        }

        Logger.i(TAG, "createPolygonsFromLanduseAreas: simplify languse geoms" );
        // use ugly hack because some residentia areas in UK are close very close to aech other and cause topology exception
        double distanceDeg = Utils.distanceToDeg(landuseGeom.getEnvelope().getCoordinate(), 20);
        landuseGeom = DouglasPeuckerSimplifier.simplify(landuseGeom, distanceDeg).buffer(0.0);

        residentPolygons.add(landuseGeom);
    }

    /**
     * Find building, test if are outside any residential area. If yes then create simple rectangle around building
     * These buffered building polygon will be later used for union
     */
    private void createPolygonsFromBuilding() {

        double bufferD = Utils.metersToDeg(BUILDING_RESIDENTIAL_BUFFER_SIZE);

        //FROM WAYS
        TLongList wayIds = dc.getWayIds();
        for (int i = 0, size = wayIds.size(); i < size; i++) {
            Way way = dc.getWayFromCache(wayIds.get(i));

            if (way == null) {
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

            residentalBuildingCounter++;
            tmpBuildingCounter++;
            tmpBuildingPolygons.add(polygon);

            if (tmpBuildingCounter >= MAX_BUILDING_POLY_COUNT_FOR_UNION) {
                unionTempResidentPoly();
                Logger.i(TAG, "Num processed building poly: " + residentalBuildingCounter);
            }
        }

        // FROM NODES

        TLongList nodeIds = dc.getNodeIds();
        for (int i = 0, size = wayIds.size(); i < size; i++) {
            Node node = dc.getNodeFromCache(nodeIds.get(i));
            if (node == null) {
                continue;
            }
            String buildingKey = OsmUtils.getTagValue(node, OsmConst.OSMTagKey.BUILDING);

            if (buildingKey == null) {
                // node is not building skip it
                continue;
            }

            if (intersectWithResidentialAreas(node.getLongitude(), node.getLatitude())) {
                // this building intersect with any default OSM residential area > do not create poly around building
                continue;
            }
            Coordinate coordinate = new Coordinate(node.getLongitude(), node.getLatitude());
            Polygon polygon = GeomUtils.createRectangle(coordinate, BUILDING_RESIDENTIAL_BUFFER_SIZE);

            residentalBuildingCounter++;
            tmpBuildingCounter++;
            tmpBuildingPolygons.add(polygon);

            if (tmpBuildingCounter >= MAX_BUILDING_POLY_COUNT_FOR_UNION) {
                unionTempResidentPoly();
                Logger.i(TAG, "Num processed building poly: " + residentalBuildingCounter);
            }
        }

        // process rest of tmp polygons
        unionTempResidentPoly();
        Logger.i(TAG, "Num processed building poly: " + residentalBuildingCounter);
    }


    // UNION TEMPORARY BUILDING POLY
    private void unionTempResidentPoly() {

        int size = tmpBuildingPolygons.size();
        if (size == 0){
            // no building was created
            return;
        }

        long start = System.currentTimeMillis();
        UnaryUnionOp unaryUnionOp = new UnaryUnionOp(tmpBuildingPolygons);
        Geometry unionGeom = unaryUnionOp.union();
        Logger.i(TAG, "Union " + size + " polygons takes: " + (System.currentTimeMillis() - start) / 1000.0);



        // simplify merged geom
        double distanceDeg = Utils.distanceToDeg(unionGeom.getEnvelope().getCoordinate(), 20);
        unionGeom = DouglasPeuckerSimplifier.simplify(unionGeom, distanceDeg);
        unionGeom = unionGeom.buffer(0.0);

        residentPolygons.add(unionGeom);

        // clear temporary cachee
        tmpBuildingPolygons = new ArrayList<>();
        tmpBuildingCounter = 0;
    }


    /**
     * Test if given coordinates intersect with any default OSM residential areas
     *
     * @param lon
     * @param lat
     * @return <code>true</code> when coordinates lies in OSM default residential area
     */
    private boolean intersectWithResidentialAreas(double lon, double lat) {

        // use index to get possible residential areas that could intersecty with specified coordinates
        Point point = geometryFactory.createPoint(new Coordinate(lon, lat));
        List<Polygon> intersectPoly = residentialAreasIndex.query(point.getEnvelopeInternal());

        if (intersectPoly.size() == 0) {
            return false;
        }

        for (Polygon polygon : intersectPoly) {

            if (polygon.intersects(point)) {
                return true;
            }
            //Logger.i(TAG, GeomUtils.geomToGeoJson(polygon));
        }
        return false;
    }

    //  SIMPLIFY UTILS

    /**
     * Do some logic to simplify geometry, remove holes, create areas more smooth
     *
     * @param geom geometry to simplify
     * @return
     */
    private List<Polygon> simplifyResidentialGeom(Geometry geom) {

        // simplify joined geometry
        double distanceDeg = Utils.distanceToDeg(geom.getCoordinate(), 20);
        geom = DouglasPeuckerSimplifier.simplify(geom, distanceDeg);

        // remove too small geometries
        double minArea = Utils.metersToDeg(200) * Utils.metersToDeg(200);
        List<Polygon> polygons = new ArrayList<>();
        for (int i = 0, size = geom.getNumGeometries(); i < size; i++) {
            Polygon polygon = (Polygon) geom.getGeometryN(i);
            if (polygon.getArea() > minArea && polygon.getNumPoints() > 4) {
                polygons.add(polygon);
            }
        }

        if (polygons.size() == 0){
            // some residential polygons existed but was too small and removed > mas has no residential data
            return new ArrayList<>();
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
        for (int i = 0, size = residentialGeom.getNumGeometries(); i < size; i++) {
            Polygon polygon = (Polygon) residentialGeom.getGeometryN(i);
            if (polygon.getNumPoints() > 4) {
                polygons.add(polygon);
            }
        }

        return polygons;
    }

    /**
     * Test if geometry contains any holes. If are of hole is bigger than limit > hole is removed
     *
     * @param geom geometry to remove small holes
     * @return geometry without small holes
     */
    private Geometry removeHoles(Geometry geom) {

        // holes smaller than this area will be removed
        double minHoleArea = Utils.metersToDeg(500) * Utils.metersToDeg(500);

        int numGeometries = geom.getNumGeometries();

        List<Polygon> reCreatedPolygons = new ArrayList<>();
        for (int i = 0; i < numGeometries; i++) {
            Polygon polygon = (Polygon) geom.getGeometryN(i);
            LinearRing lrExterior = (LinearRing) polygon.getExteriorRing();

            int numHoles = polygon.getNumInteriorRing();
            if (numHoles == 0) {
                reCreatedPolygons.add(polygon);
                continue;
            }

            // get geometry for all holes
            ArrayList bigHoles = new ArrayList();
            for (int t = 0; t < numHoles; t++) {
                LinearRing lr_hole = (LinearRing) polygon.getInteriorRingN(t);
                // create temporary polygon and test area
                Polygon p = lr_hole.getFactory().createPolygon(lr_hole, null);

                if (p.getArea() > minHoleArea) {
                    // do not remove this hole
                    bigHoles.add(lr_hole);
                }
            }
            // create new polygon only with big holes
            polygon = polygon.getFactory().createPolygon(lrExterior, (LinearRing[]) bigHoles.toArray(new LinearRing[0]));

            reCreatedPolygons.add(polygon);
        }

        // Merge re-created polygons back into one geom
        UnaryUnionOp unaryUnionOp = new UnaryUnionOp(reCreatedPolygons);

        return unaryUnionOp.union();
    }

    // OTHER UTILS

    /**
     * Test if way is landuse and if is accpeted landuse for residential ares
     *
     * @param way way to test
     * @return <code>true</code> id way can be used for creation residential polygon
     */
    private boolean isValidLanduse(Way way) {

        String landuse = OsmUtils.getTagValue(way, OsmConst.OSMTagKey.LANDUSE);

        if (landuse == null) {
            return false;
        }

        for (String validLanduse : VALID_LANDUSE_TAGS) {
            if (validLanduse.equalsIgnoreCase(landuse)) {
                return true;
            }
        }
        return false;
    }


    /**
     * Write list of polygons as one geometry into geojson file. Only for testing purposes on local
     *
     * @param polygons created residential areas
     */
    private void writeResultToGeoJson(List<Polygon> polygons) {
        UnaryUnionOp unaryUnionOp = new UnaryUnionOp(polygons);
        Geometry unionGeom = unaryUnionOp.union();

        com.asamm.osmTools.utils.Utils.writeStringToFile(
                new File("residential.geojson"), GeomUtils.geomToGeoJson(unionGeom), false);
    }
}
