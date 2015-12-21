package com.asamm.osmTools.generatorDb.index;

import com.asamm.osmTools.generatorDb.address.Boundary;
import com.asamm.osmTools.generatorDb.address.City;
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


    /** JTS in memory index of center geometries for cities*/
    private STRtree cityCenterIndex;

    /** JTS index of boundary geometries (their) envelopes*/
    private STRtree boundaryGeomIndex;

    /** Index for geometries of all ways that can be street but does not have defined the name*/
    private STRtree streetUnnamedGeomIndex;

    /** JTS in memory index of geometries of joined streets*/
    private STRtree streetGeomIndex;

    /** Custom geom index only for dummy streets created from cities or places*/
    //private Quadtree dummyStreetGeomIndex;




    private IndexController() {
        cityCenterIndex = new STRtree();
        boundaryGeomIndex = new STRtree();
        streetUnnamedGeomIndex = new STRtree();
        streetGeomIndex = new STRtree();
 //       dummyStreetGeomIndex = new Quadtree();

    }

    public static IndexController getInstance() {
        if (instance == null){
            instance = new IndexController();
        }
        return instance;
    }
    // CITY CENTER GEOM INDEX

    public void insertCity(Envelope envelope, City city) {
        cityCenterIndex.insert(envelope, city);
    }

    public List<City> getClosestCities (Point centerPoint, int minNumber){

        double distance = 5000;

        List cityFromIndex = new ArrayList();

        int numOfResize = 0;
        while (cityFromIndex.size() < minNumber) {
            //Logger.i(TAG,"Extends bounding box");
            Polygon searchBound = GeomUtils.createRectangle(centerPoint.getCoordinate(), distance);
            cityFromIndex = cityCenterIndex.query(searchBound.getEnvelopeInternal());
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
        streetGeomIndex = new STRtree();
    }

    public void insertStreet(Envelope envelope, Street street) {
        streetGeomIndex.insert(envelope, street);
    }

    public List<Street> getStreetsAround(Point centerPoint, int minNumber) {

        double distance = 200;

        List<Street> streetsFromIndex = new ArrayList();

        int numOfResize = 0;
        Polygon searchBound = GeomUtils.createRectangle(centerPoint.getCoordinate(), distance);
        while (streetsFromIndex.size() < minNumber) {
            //Logger.i(TAG,"getStreetsAround(): bounding box: " +Utils.geomToGeoJson(searchBound));
            streetsFromIndex = streetGeomIndex.query(searchBound.getEnvelopeInternal());
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


    // STREET UNNAMED


    public void insertStreetUnnamed(Envelope envelope, Street street) {
        streetUnnamedGeomIndex.insert(envelope, street.getOsmId());
    }


    /**
     * Select unnamed street that intersect with given multipolygon
     * @param multiPolygon region to select streets
     * @return list of streets that intersect or lay inside of geiven area
     */
    public List<Street> getUnnamedWayStreets(ADataContainer dc, MultiPolygon multiPolygon) {

        PreparedGeometry pg = PreparedGeometryFactory.prepare(multiPolygon);
        List<Long> streetIds = streetUnnamedGeomIndex.query(multiPolygon.getEnvelopeInternal());
        List<Street> wayStreets = dc.getWayStreetsUnnamedFromCache(streetIds);

        for (int i = wayStreets.size() -1; i >= 0; i--){
            Street streetToFilter = wayStreets.get(i);
            if ( !pg.intersects(streetToFilter.getGeometry())){
                wayStreets.remove(i);
            }
        }
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

    public STRtree getStreetGeomIndex() {
        return streetGeomIndex;
    }

//    public Quadtree getDummyStreetGeomIndex() {
//        return dummyStreetGeomIndex;
//    }

    public STRtree getBoundaryGeomIndex() {
        return boundaryGeomIndex;
    }



}
