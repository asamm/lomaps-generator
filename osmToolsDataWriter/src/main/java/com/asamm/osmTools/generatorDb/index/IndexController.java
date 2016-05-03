package com.asamm.osmTools.generatorDb.index;

import com.asamm.osmTools.generatorDb.address.Boundary;
import com.asamm.osmTools.generatorDb.address.City;
import com.asamm.osmTools.generatorDb.address.Region;
import com.asamm.osmTools.generatorDb.address.Street;
import com.asamm.osmTools.generatorDb.dataContainer.ADataContainer;
import com.asamm.osmTools.generatorDb.utils.GeomUtils;
import com.vividsolutions.jts.geom.*;
import com.vividsolutions.jts.geom.prep.PreparedGeometry;
import com.vividsolutions.jts.geom.prep.PreparedGeometryFactory;
import com.vividsolutions.jts.index.strtree.STRtree;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by voldapet on 2015-12-09
 * Container for all JTS in memory geom indexes.
 */
public class IndexController {

    private static IndexController instance = null;

    /** JTS index of regions geometries (their) envelopes*/
    private STRtree regionGeomIndex;

    /** JTS in memory index of center geometries for cities*/
    private STRtree cityCenterIndex;

    /** JTS in memory index of boundaries or cities center if city has not boundary*/
    private STRtree cityGeomIndex;

    /** JTS index of boundary geometries (their) envelopes*/
    private STRtree boundaryGeomIndex;

    /** Index of geometries of ways that has defined the name*/
    private STRtree wayStreetNamedGeomIndex;

    /** Index for geometries of all ways that can be street but does not have defined the name*/
    private STRtree wayStreetUnnamedGeomIndex;

    /** JTS in memory index of geometries of joined streets*/
    private STRtree joinedStreetGeomIndex;


    private IndexController() {
        regionGeomIndex = new STRtree();
        cityCenterIndex = new STRtree();
        cityGeomIndex = new STRtree();
        boundaryGeomIndex = new STRtree();
        wayStreetNamedGeomIndex = new STRtree();
        wayStreetUnnamedGeomIndex = new STRtree();
        joinedStreetGeomIndex = new STRtree();
    }

    public static IndexController getInstance() {
        if (instance == null){
            instance = new IndexController();
        }
        return instance;
    }


    /**
     * Use only if know what it does.
     * It clear all indexes
     */
    public void clearAll() {

        regionGeomIndex = null;
        cityCenterIndex = null;
        boundaryGeomIndex = null;
        wayStreetNamedGeomIndex = null;
        wayStreetUnnamedGeomIndex = null;
        joinedStreetGeomIndex = null;
    }


    // INDEX OF REGION GEOMETRIES

    public void insertRegion(Region region) {
        regionGeomIndex.insert(region.getGeom().getEnvelopeInternal() , region);
    }

    // CITY CENTER GEOM INDEX

    /**
     * Add city center into geom index. This index works only with center of city. This index is vital for finding
     * boundaries when geometry for city is not know
     * @param envelope
     * @param city
     */
    public void insertCityCenter(Envelope envelope, City city) {
        cityCenterIndex.insert(envelope, city);
    }

    /**
     * Add city into index that works with city boundary. If boundary is not defined then city center is used for index
     * @param city city to add into index
     */
    public void insertCityGeom(City city) {

        Envelope envelope = null;

        if (city.getGeom() != null && city.getGeom().isValid()){
            envelope = city.getGeom().getEnvelopeInternal();
        }
        else {
            envelope = city.getCenter().getEnvelopeInternal();
        }

        cityGeomIndex.insert(envelope, city);
    }


    public List<City> getClosestCities (Point centerPoint, int minNumber){

        double distance = 5000;

        List cityFromIndex = new ArrayList();
        if ( !centerPoint.isValid()){
            return cityFromIndex;
        }

        int numOfResize = 0;
        while (cityFromIndex.size() < minNumber) {
            //Logger.i(TAG,"Extends bounding box");
            Polygon searchBound = GeomUtils.createRectangle(centerPoint.getCoordinate(), distance);
            cityFromIndex = cityGeomIndex.query(searchBound.getEnvelopeInternal());
            distance = distance * 2;
            numOfResize++;
            if (numOfResize == 4){
                //Logger.i(TAG, "MAx num of resize reached");
                break;
            }
        }

        return cityFromIndex;
    }
    // INDEX OF BOUNDARIES GEOMETRIES

    public void insertBoundary(Envelope envelope, Boundary boundary) {
        boundaryGeomIndex.insert(envelope, boundary);
    }

    // STREET GEOM

    public void clearStreetGeomIndex() {
        joinedStreetGeomIndex = new STRtree();
    }

    public void insertStreet(Envelope envelope, Street street) {
        joinedStreetGeomIndex.insert(envelope, street);
    }

    public List<Street> getStreetsAround(Point centerPoint, int minNumber) {

        double distance = 200;

        List<Street> streetsFromIndex = new ArrayList();
        if ( !centerPoint.isValid()){
            // center point is not valid not able to find street
            return streetsFromIndex;
        }

        int numOfResize = 0;
        Polygon searchBound = GeomUtils.createRectangle(centerPoint.getCoordinate(), distance);
        while (streetsFromIndex.size() < minNumber) {
            //Logger.i(TAG,"getStreetsAround(): bounding box: " +Utils.geomToGeoJson(searchBound));
            streetsFromIndex = joinedStreetGeomIndex.query(searchBound.getEnvelopeInternal());
            if (numOfResize == 4) {
                //Logger.i(TAG, "getStreetsAround(): Max num of resize reached for center point: " + Utils.geomToGeoJson(centerPoint));
                break;
            }
            numOfResize++;
            distance = distance * 2;
            searchBound = GeomUtils.createRectangle(centerPoint.getCoordinate(), distance);
        }
        return streetsFromIndex;
    }

    // WAYSTREET NAMED

    public void insertWayStreetNamed(Envelope envelope, Street street) {
        wayStreetNamedGeomIndex.insert(envelope, street.getOsmId());
    }


    /**
     * Select named waystreet that intersect with ENVELOPER if given multipolygon
     *
     * @param multiPolygon region to select named way streets
     * @return list of streets that intersect or lay inside of envelope of given area
     */
    public List<Street> getNamedWayStreets(ADataContainer dc, MultiPolygon multiPolygon) {

        List<Long> streetIds = wayStreetNamedGeomIndex.query(multiPolygon.getEnvelopeInternal());
        List<Street> wayStreets = dc.getWayStreetsByOsmIdFromCache(streetIds);

        return wayStreets;
    }


    // WAYSTREET UNNAMED


    public void insertWayStreetUnnamed(Envelope envelope, Street street) {
        wayStreetUnnamedGeomIndex.insert(envelope, street.getOsmId());
    }


    /**
     * Select unnamed street that intersect with given multipolygon
     * @param multiPolygon region to select streets
     * @return list of streets that intersect or lay inside of given area
     */
    public List<Street> getUnnamedWayStreets(ADataContainer dc, MultiPolygon multiPolygon) {

        PreparedGeometry pg = PreparedGeometryFactory.prepare(multiPolygon);
        List<Long> streetIds = wayStreetUnnamedGeomIndex.query(multiPolygon.getEnvelopeInternal());
        List<Street> wayStreets = dc.getWayStreetsByOsmIdFromCache(streetIds);


//        // TODO is it really needed to filter queried ways if intersect with multipolygon??
//
//        for (int i = wayStreets.size() -1; i >= 0; i--){
//            Street streetToFilter = wayStreets.get(i);
//            if ( !pg.intersects(streetToFilter.getGeometry())){
//                wayStreets.remove(i);
//            }
//        }


        return wayStreets;
    }



//
//    /**
//     * Select nearest streets
//     * @param centerPoint
//     * @param minNumber
//     * @return
//     */
//    public List<Street> getDummyStreetsAround(Point centerPoint, int minNumber) {
//
//        double distance = 200;
//
//        List<Street> streetsFromIndex = new ArrayList();
//        Polygon searchBound = Utils.createRectangle(centerPoint.getCoordinate(), distance);
//        int numOfResize = 0;
//        while (streetsFromIndex.size() < minNumber) {
//
//            //Logger.i(TAG,"getDummyStreetsAround(): bounding box: " +Utils.geomToGeoJson(searchBound));
//            streetsFromIndex = dummyStreetGeomIndex.query(searchBound.getEnvelopeInternal());
//            if (numOfResize == 4) {
//                //Logger.i(TAG, "getDummyStreetsAround(): Max num of resize reached for center point: " + Utils.geomToGeoJson(centerPoint));
//                break;
//            }
//            numOfResize++;
//            distance = distance * 2;
//            searchBound = Utils.createRectangle(centerPoint.getCoordinate(), distance);
//        }
//
//        // FILTER - Quadtree return only items that MAY intersect > it's needed to check it
//
//        PreparedGeometry pg = PreparedGeometryFactory.prepare(searchBound);
//        for (int i = streetsFromIndex.size() -1; i >= 0; i--){
//            Street street = streetsFromIndex.get(i);
//            if ( !pg.intersects(street.getGeometry().getEnvelope())){
//                //Logger.i(TAG, "getDummyStreetsAround(): remove street because not intersect: " + street.toString());
//                streetsFromIndex.remove(i);
//            }
//        }
//
//        return streetsFromIndex;
//    }


    /**
     * **********************************************
     */
    /*                     GETTERS
    /**************************************************/
    public STRtree getCityCenterIndex() {
        return cityCenterIndex;
    }

    public STRtree getJoinedStreetGeomIndex() {
        return joinedStreetGeomIndex;
    }

//    public Quadtree getDummyStreetGeomIndex() {
//        return dummyStreetGeomIndex;
//    }

    public STRtree getBoundaryGeomIndex() {
        return boundaryGeomIndex;
    }


    public STRtree getRegionGeomIndex() {
        return regionGeomIndex;
    }

}
