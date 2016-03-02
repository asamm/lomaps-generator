package com.asamm.osmTools.generatorDb.address;

import com.asamm.osmTools.generatorDb.GeneratorAddress;
import com.asamm.osmTools.generatorDb.data.OsmConst.OSMTagKey;
import com.asamm.osmTools.generatorDb.data.WayEx;
import com.asamm.osmTools.generatorDb.dataContainer.ADataContainer;
import com.asamm.osmTools.generatorDb.db.DatabaseAddress;
import com.asamm.osmTools.generatorDb.index.IndexController;
import com.asamm.osmTools.generatorDb.utils.GeomUtils;
import com.asamm.osmTools.generatorDb.utils.OsmUtils;
import com.asamm.osmTools.generatorDb.utils.Utils;
import com.asamm.osmTools.utils.Logger;
import com.vividsolutions.jts.geom.*;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.geom.prep.PreparedGeometry;
import com.vividsolutions.jts.geom.prep.PreparedGeometryFactory;
import com.vividsolutions.jts.index.quadtree.Quadtree;
import com.vividsolutions.jts.operation.linemerge.LineMerger;
import gnu.trove.iterator.TLongIterator;
import gnu.trove.list.TLongList;
import gnu.trove.set.hash.THashSet;
import gnu.trove.set.hash.TLongHashSet;
import org.apache.commons.lang3.StringUtils;
import org.openstreetmap.osmosis.core.domain.v0_6.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

/**
 * Created by voldapet on 2015-08-21 .
 */
public class StreetController {

    public interface OnJoinStreetListener {
        public void onJoin(Street street);
    }

    private static final String TAG = StreetController.class.getSimpleName();

    // maximal distance between linestring (it's envelope) that is accepted that is part of the same street
    private static final int MAX_DISTANCE_BETWEEN_STREET_SEGMENTS = 300;



    private ADataContainer dc;

    private GeneratorAddress ga;

    private DatabaseAddress databaseAddress;

    private HouseController houseFactory;

    private GeometryFactory geometryFactory;

    //TODO for production remove the time measures

    public long timeFindStreetCities = 0;
    public long timeLoadNereastCities = 0;
    public long timeFindCityTestByGeom = 0;
    public long timeFindCityTestByDistance = 0;
    public long timeFindCityFindNearest = 0;

    public long timeJoinWaysToStreets = 0;

    public StreetController(ADataContainer dc, GeneratorAddress ga){

        this.dc = dc;
        this.ga = ga;
        this.databaseAddress = ga.getDatabaseAddress();

        this.geometryFactory = new GeometryFactory();

        this.houseFactory = new HouseController(dc, ga);
    }

    /**
     * Iterate relations > find that are street or associated street and create streets from members
     * Create streets are saved into database
     */
    public void createWayStreetFromRelations() {

        TLongList relationIds = dc.getRelationIds();
        for (int i=0, size = relationIds.size(); i < size; i++) {

            long relationId = relationIds.get(i);
            //Logger.i(TAG, " createWayStreetFromRelations() Create street for relation id: " + relationId);

            Relation relation = dc.getRelationFromCache(relationId);
            if (relation == null) {
                continue;
            }

            // get osm tag type
            String type = OsmUtils.getTagValue(relation, OSMTagKey.TYPE);
            if (type == null){
                continue;
            }

            if ( !( type.equals(OSMTagKey.STREET.getValue())
                    || type.equals(OSMTagKey.ASSOCIATED_STREET.getValue())
                    || type.equals((OSMTagKey.MULTIPOLYGON.getValue())))){
                //Logger.i(TAG, "createWayStreetFromRelations() Skip relation id: " + relationId);
                continue;
            }

            // remember that this relation can be street (later use it for generation of houses)
            dc.getStreetRelations().add(relationId);

            List<String> isInList = new ArrayList<>();
            String name = null;

            // in first step try to obtain name from "first" street member
            for (RelationMember re : relation.getMembers()){
                if (re.getMemberType() == EntityType.Way){
                    Way way = dc.getWayFromCache(re.getMemberId());
                    if (way == null){
                        continue;
                    }
                    name = OsmUtils.getStreetName(way);
                    isInList = getIsInList(way);
                    break;
                }

            }

            if (name == null || name.length() == 0){
                name = OsmUtils.getStreetName(relation);
                isInList = getIsInList(relation);
            }

            // create street geom
            //Logger.i(TAG, "Create street geom from relation id: " + relationId);
            MultiLineString mls = createStreetGeom(relation);

            if (mls == null || mls.getCoordinates().length == 0) {
                // probably associtate Address relation that does not contain any street member > try to create geom
                // from unnamed streets if relation has some houses
                //Logger.i(TAG, "can not create street geom from relation id: " + relationId);
                continue;
            }

            Street wayStreet = new Street (name, isInList, mls);
            wayStreet.setOsmId(relationId);
            wayStreet.setPath(isPath(relation));

            // find all cities where street can be in it or is close to city
            List<City> cities = findCitiesForStreet(wayStreet);
            wayStreet.addCities(cities);


            if (name == null || name.length() == 0) {
                IndexController.getInstance().insertStreetUnnamed(wayStreet.getGeometry().getEnvelopeInternal(), wayStreet);
                dc.addWayStreetUnnamed(wayStreet);
            }
            else {
                dc.addWayStreet(wayStreet);
            }
        }
    }

//    private List<RelationMember> getHouseMembersCenters (Relation relation){
//        List<RelationMember> houseMembers = new ArrayList<>();
//
//        for (RelationMember rm : relation.getMembers()){
//            String role = rm.getMemberRole();
//            if (role.equals("house") || role.equals("address")){
//                Entity entityH = null;
//                if (rm.getMemberType() == EntityType.Way) {
//                    entityH = dc.getWayFromCache(rm.getMemberId());
//                }
//                else if (rm.getMemberType() == EntityType.Node){
//                    entityH = dc.getNodeFromCache(rm.getMemberId());
//                }
//
//                if (entityH != null){
//                    Point center = HouseController.ge
//                }
//            }
//
//        }
//
//    }

    /**
     *
     */
    public void createWayStreetFromWays() {

        TLongList wayIds = dc.getWayIds();
        for (int i=0, size = wayIds.size(); i < size; i++) {

            long wayId = wayIds.get(i);

            Way way = dc.getWayFromCache(wayId);
            if (way == null){
                continue;
            }

            if ( !isStreetWay(way)){
                continue;
            }

            List<String> isInList = getIsInList(way);

            // create street geom
            MultiLineString mls = createStreetGeom(way);
            if (mls == null) {
                // probably associate Address relation that does not contain any street member > skip it
                continue;
            }

            String name = OsmUtils.getStreetName(way);
            Street wayStreet = new Street (name, isInList, mls);
            wayStreet.setOsmId(wayId);
            wayStreet.setPath(isPath(way));

            if (name == null || name.length() == 0) {
                dc.addWayStreetUnnamed(wayStreet);
                IndexController.getInstance().insertStreetUnnamed(wayStreet.getGeometry().getEnvelopeInternal(), wayStreet);
            }
            else {

                // find all cities where street can be in it or is close to city
                long start = System.currentTimeMillis();
                List<City> cities = findCitiesForStreet(wayStreet);
                timeFindStreetCities += System.currentTimeMillis() - start;
                wayStreet.addCities(cities);
                if (wayId == 34967495 ){
                    Logger.i(TAG, "Add way street into datacontainer: " + wayStreet.toString());
                }

                dc.addWayStreet(wayStreet);
            }
        }
    }


    /**
     * Iterate through wayStreets (osm ways) and join ways into one Street. In first step
     * are joined all wayStreet of the same name that are in the same city. BUT there can
     * be more then one street in the city. For this reason we fix it by splitting the joined
     * street into independent streets
     */
    public void joinWayStreets(OnJoinStreetListener onJoinStreetListener) {

        Logger.i(TAG, "Start to join ways into streets and insert them into DB");
        long start = System.currentTimeMillis();

        dc.finalizeWayStreetCaching();
        IndexController.getInstance().clearStreetGeomIndex();

        Iterator<Integer> iterator = dc.getStreetHashSet().iterator();
        TLongHashSet cityIds = new TLongHashSet();
        THashSet<House> houses = new THashSet<>();
        THashSet<Geometry> waysGeomsToJoin = new THashSet<>();
        LineMerger lineMerger = new LineMerger();

        while (iterator.hasNext()){

            cityIds = new TLongHashSet();
            int hash = iterator.next();
            //load all ways with the same hash from cache. These all ways has the same name but different city
            List<Street> wayStreets = dc.getWayStreetsFromCache(hash);

            if (wayStreets.size() == 0){
                continue;
            }

            //Logger.i(TAG, "Num of streets for hash: " + hash + " streets: " + wayStreets.size() + " name: " + wayStreets.get(0).getName());

            // how it works: takes the last wayStreet then subiterate all waystreet with the same name and try
            // to find other wayStreet from that share at least on cityId. If found it join it with the waystreet from
            // parent loop. then delete it.
            // Because different wayStreet can have different cityIds it's needed to iterate several times.

            for (int i = wayStreets.size() -1; i >= 0; i--){

                lineMerger = new LineMerger();
                waysGeomsToJoin = new THashSet<>();
                Street wayStreet = wayStreets.get(i);
                wayStreets.remove(i);

                cityIds = wayStreet.getCityIds();
                boolean isPath = wayStreet.isPath();
                houses = wayStreet.getHouses();
                //lineMerger.add(wayStreet.getGeometry());
                waysGeomsToJoin.add(wayStreet.getGeometry());
                boolean sameStreetFounded = false;

                do {
                    sameStreetFounded = false;
                    for (int j = i-1; j >= 0; j--){
                        Street wayStreetToJoin = wayStreets.get(j);
                        if (wayStreet.getName().equals("Vestermarksvej")) {
                            Logger.i(TAG, "test way street: " + wayStreetToJoin.toString());
                        }

                        if (isFromTheSameCities(cityIds, wayStreetToJoin.getCityIds())){
                            // it street from the same cities prepare them for join
//                            if (wayStreet.getName().equals("Via Verdi")) {
//                                Logger.i(TAG, "Add geometry: " + Utils.geomToGeoJson(wayStreetToJoin.getGeometry()));
//                            }
                            waysGeomsToJoin.add(wayStreetToJoin.getGeometry());
                            cityIds.addAll(wayStreetToJoin.getCityIds());
                            houses.addAll(wayStreetToJoin.getHouses());
                            isPath = (isPath) ? true : wayStreet.isPath();

                            // remove this street way because was proccessed
                            wayStreets.remove(j);
                            j--;
                            i--;
                            sameStreetFounded = true;
                        }
                    }
                } while (sameStreetFounded);

                // CREATE STREET GEOM
                lineMerger.add(waysGeomsToJoin);
                Collection<LineString> lineStrings = lineMerger.getMergedLineStrings();
                MultiLineString mls = GeomUtils.mergeLinesToMultiLine(lineStrings);
                if (mls == null) {
                    //Logger.w(TAG, "joinWayStreets(): Can not create geometry for street:  " + wayStreet.getOsmId());
                    continue;
                }

                // CREATE STREET
                // now we joined all wayStreet with the same name that are in the same city.

                Street street = new Street();
                street.setName(wayStreet.getName());
                street.setGeometry(mls);
                street.setCityIds(cityIds);
                street.setHouses(houses);
                street.setPath(isPath);

                // street is create now call for other actions
                onJoinStreetListener.onJoin(street);
            }
        }
        timeJoinWaysToStreets += System.currentTimeMillis() - start;
    }

    /**
     * Try to find cities where street is in it. And then split street into stand alone streets based on cities geom
     * IMPORTANT - method does not handle the houses. So use it only when crate streets
     *
     * @param streetToSplit street to split into several streets
     * @return list of street and their geoms are cutted by city geoms
     */
    public List<Street> splitGeomByParentCities (Street streetToSplit) {

        List<Street> streets = new ArrayList<>();
           // PREPARE LIST OF TOP CITIES
        List<City> topCities = getTopLevelCities(streetToSplit, City.CityType.VILLAGE);

        if (topCities.size() <= 1){
            // streets contains only one city
//            if (streetToSplit.getName().equals("Via Appia")){
//                Logger.i(TAG, "There is only one top city >  nothing to cut");
//            }
            streets.add(streetToSplit);
            return streets;
        }

        // SPLIT STREETS BY CITIES GEOM
        // Logger.i(TAG, "****Num of cities " +topCities.size() + " ; split line: " + streetToSplit.toString());

        for (City city : topCities){

            Street street = new Street();
            street.setName(streetToSplit.getName());
            street.setHouses(streetToSplit.getHouses());
            street.setPath(streetToSplit.isPath());

            //Logger.i(TAG, "Line for cut: " + Utils.geomToGeoJson(streetToSplit.getGeometry()));
            Geometry geomIntersection = streetToSplit.getGeometry().intersection(city.getGeom());
            // the result can be GeomCollection try to convert it into Lines
            MultiLineString mls = GeomUtils.geometryToMultilineString(geomIntersection);

            // test if there is any intersection
            if (mls == null || !mls.isValid() || mls.isEmpty()){
                continue;
            }

            Coordinate[] coordinates = mls.getCoordinates();
            double lengthM = Utils.getDistance(coordinates[0], coordinates[coordinates.length-1]);

            //Logger.i(TAG, "Intersection line: " + Utils.geomToGeoJson(mls));
            if (lengthM > 10) {
                // set geometry from intersection to new street
                street.setGeometry(mls);
                street.addCities(findCitiesForStreet(street));
                streets.add(street);
            }

            // remove intersection part from source street
            Geometry geomDifference = streetToSplit.getGeometry().difference(city.getGeom());
            streetToSplit.setGeometry(GeomUtils.geometryToMultilineString(geomDifference));
            //Logger.i(TAG, "Difference line: " +geomDifference.getLength() + "; " + Utils.geomToGeoJson(geomDifference));
        }

        // TEST IF SOURCE STREET CAN BE STILL USED AS PROPER STREET
        // remove short segments from rest of the street
        streetToSplit.setGeometry(removeShortSegments(streetToSplit.getGeometry(), 10));
        if ( streetToSplit.getGeometry() != null && !streetToSplit.getGeometry().isEmpty()){
            // compute distance between first and the last point of line
            Coordinate[] coordinates = streetToSplit.getGeometry().getCoordinates();
            double lengthM = Utils.getDistance(coordinates[0], coordinates[coordinates.length - 1]);
            if (lengthM > 10){
                // this street is longer then 10 meters use it as street > need to update list of cities
                streetToSplit.setCities(findCitiesForStreet(streetToSplit));
                streets.add(streetToSplit);
            }
        }

        return streets;
    }

    /**
     * Compare cities and get the most top level cities
     * @param street street to get the cities
     * @param typeLevel only cities on the same or higher level will loaded
     * @return list of the most parent top level cities.
     */
    private List<City> getTopLevelCities (Street street, City.CityType typeLevel){



        List<City> topCities = new ArrayList<>();

        TLongHashSet cityIds = street.getCityIds();
        TLongIterator iterator = cityIds.iterator();

        int topCityTypeCode = typeLevel.getTypeCode(); // the lower code means bigger city
        while (iterator.hasNext()){
            City city = dc.getCity(iterator.next());
            if (city == null || city.getGeom() == null){
                continue;
            }

            int cityType = city.getType().getTypeCode();
            if (cityType <= topCityTypeCode){

                // test if new city can be parent of any any current city in topLevel list
                for (int i = topCities.size() -1; i >= 0; i-- ){
                    City cityInList = topCities.get(i);
                    City parentCity = cityInList.getParentCity();
                    if (parentCity != null && parentCity.getOsmId() == city.getOsmId()){
                        // this is parent city for this one > replace it
                        topCities.remove(i);
                    }
                }
                topCities.add(city);
            }
        }
        return topCities;
    }

    /**
     * Joined street can lay in more then one city. But some times it's not one street but
     * two different street with the same name. This method identify if geometry of street
     * is only one street or if it is two or more different cities
     *
     * @param streetToSplit street to test if it is one or more different streets
     * @return list of separated street
     */
    public List<Street> splitToCityParts(Street streetToSplit) {

        List<Street> separatedStreets = new ArrayList<>();
        MultiLineString mls = streetToSplit.getGeometry();
        int numGeomElements = mls.getNumGeometries();

        //Logger.i(TAG, "splitToCityParts(): Num of multilinestreet: " + numGeomElements + " street: " + street.toString());

        // from parent street get list of linestring from which is created
        List<LineString> elements = new ArrayList<>();
        for (int i=0; i < numGeomElements; i++){
            LineString ls = (LineString) mls.getGeometryN(i);
            elements.add(ls);
        }

        // find that if there geometries for more than one street
        List<MultiLineString> mlsSeparated = splitGeomDifferentStreets(elements);

        if (mlsSeparated.size() <= 1){
            // all linestring are from the same street
            separatedStreets.add(streetToSplit);
            return separatedStreets;
        }

        // for every geometry create new street object with the same name but find the cities again
        for (MultiLineString mlsSep : mlsSeparated){

            Street streetSep = new Street();
            streetSep.setName(streetToSplit.getName());
            streetSep.setGeometry(mlsSep);
            List<City> cities = findCitiesForStreet(streetSep);
            streetSep.addCities(cities);
            streetSep.setPath(streetToSplit.isPath());

            separatedStreets.add(streetSep);

            //Logger.i(TAG, "splitToCityParts(): New separated street: " + streetSep.toString());
        }

        // test if street for splitting has any house
        if (streetToSplit.hasHouses()){
            for (House house : streetToSplit.getHouses()){
                // for every house find the nearest street from splitted streets
                Street nearestStreet = getNearestStreet(house.getCenter(), separatedStreets);
                if (nearestStreet != null){
                    nearestStreet.addHouse(house);
                }
            }
        }

        return separatedStreets;
    }

    /**
     * Due to system of joining way streets can be joined two or more different streets
     * with the same name into one street. This method identify geometries that
     * are far away from each other and create separate geometries for
     * every particular street
     * @param elements list of linestring, this is result of joining ways with the same name in city
     * @return geometries for new separate streets
     */
    private List<MultiLineString> splitGeomDifferentStreets(List<LineString> elements){

        // max distance between elements where we expect that are in the same street
        double[] distanceDeg = Utils.metersToDlatDlong(elements.get(0).getCoordinate(), MAX_DISTANCE_BETWEEN_STREET_SEGMENTS);

        // list geoms for separated streets
        List<MultiLineString> splittedMls = new ArrayList<>();

        List<LineString> lsList = new ArrayList<>();

        // create temp index of line strings of source geom
        Quadtree index = new Quadtree();
        for (int i=0; i < elements.size(); i++){
            index.insert(elements.get(i).getEnvelopeInternal(), i);
        }

        // prepare envelope for first query from the first element
        MultiLineString mlsForEnvelope = geometryFactory.createMultiLineString(new LineString[]{elements.get(0)});
        Envelope envelope = mlsForEnvelope.getEnvelopeInternal();
        envelope.expandBy(distanceDeg[0], distanceDeg[1]);

        boolean hasOtherElements = true;
        while (hasOtherElements){
            // get ids of line string around the first linestring
            List<Integer> result = index.query(envelope);
            int resultSize = result.size();
            if (resultSize == 0){
                // not able to get other lines around
                break;
            }
            else {
                // there are some linestring around the firs one (can be first itself)
                for (Integer i : result){
                    LineString ls = elements.get(i);
                    lsList.add(ls);
                    // remove this linestring from index
                    index.remove(ls.getEnvelopeInternal(), i);
                }

                // create new envelope from result. The reason is to get elements near to result
                LineString[] linesForEnvelope = lsList.toArray(new LineString[0]);
                mlsForEnvelope = geometryFactory.createMultiLineString(linesForEnvelope);
                envelope = mlsForEnvelope.getEnvelopeInternal();
                envelope.expandBy(distanceDeg[0], distanceDeg[1]);
            }
        }

        // remove all lines from queries from elements
        elements.removeAll(lsList);

        // create new geom for new street from list of lines
        LineString[] linesNewGeom = lsList.toArray(new LineString[0]);
        MultiLineString mls = geometryFactory.createMultiLineString(linesNewGeom);
        splittedMls.add(mls);
        //Logger.i(TAG, "splitGeomDifferentStreets()  split street: " + Utils.geomToGeoJson(mls));

        if (elements.size() > 0){
            //  it seems that there is other street with the same name but on different place
            //Logger.i(TAG, "splitGeomDifferentStreets: Street has more street do next iteration, street: " + street.getName() );
            splittedMls.addAll(splitGeomDifferentStreets(elements));
        }

        return splittedMls;
    }

    /**
     * Compare two list of city ids.
     * @param c1 List of city ids for the first wayStreet
     * @param c2 List of city ids for the second wayStreet (wayStreet for join with the first one)
     * @return true of both list (streets) are in one city
     */
    private boolean isFromTheSameCities (TLongHashSet c1, TLongHashSet c2){
        TLongIterator iterator = c1.iterator();
        while (iterator.hasNext()){
            if (c2.contains(iterator.next())){
                return true;
            }
        }
        return false;
    }

    /**
     * Find cities where street is in it.
     * @param street street to find in which cities is
     * @return cities that contain tested street
     */
    public List<City> findCitiesForStreet(Street street) {
        //Logger.i(TAG, " findCitiesForStreet() - looking for cities for street: " + street.toString() );

        List<City> streetCities = new ArrayList<>(); // cities where street is in it
        if (street.getGeometry().isEmpty()){
            // street does not contains any geometry
            return streetCities;
        }

        Point streetCentroid = street.getGeometry().getCentroid();
        if ( !streetCentroid.isValid()){
            // centroid for street with same points is NaN. This is workaround
            streetCentroid = geometryFactory.createPoint(street.getGeometry().getCoordinate());
        }
        long start = System.currentTimeMillis();
        List<City> citiesAround = IndexController.getInstance().getClosestCities(streetCentroid, 30);
        timeLoadNereastCities += System.currentTimeMillis() - start;

        //RECOGNIZE BY IS IN TAG

        List<String> isInNames = street.getIsIn();
        if (isInNames.size() > 0){
            for (int i = citiesAround.size() - 1; i >= 0; i--){
                City city = citiesAround.get(i);
                if (isInNames.contains(city.getName())){
                    streetCities.add(city);
                    citiesAround.remove(i);
                }
            }
        }

        // RECOGNIZE BY CONTAINS GEOM

        // create index from rest of cities if boundary exist
        List<City> citiesWithoutBound = new ArrayList<>();

        start = System.currentTimeMillis();
        PreparedGeometry streetGeomPrepared = PreparedGeometryFactory.prepare(street.getGeometry());
        //PreparedGeometry streetGeomPrepared = PreparedGeometryFactory.prepare(street.getGeometry().getEnvelope().getCentroid());
        for (City city : citiesAround){
            MultiPolygon mp = city.getGeom();
            if (mp == null){
                // this city does not have defined the bound geometry
                citiesWithoutBound.add(city);
                continue;
            }
            if (streetGeomPrepared.intersects(city.getGeom())){
                streetCities.add(city);
            }
        }

        timeFindCityTestByGeom += System.currentTimeMillis() - start;


        // RECOGNIZE BY DISTANCE

        // for rest of cities that does not have defined the bounds check distance
        if (streetCities.size() == 0){
            start = System.currentTimeMillis();
            for (City city : citiesWithoutBound){

                // boundary is not defined > if relative distance is lower 0.2
                double distance = Utils.getDistance(streetCentroid, city.getCenter());
                if (distance / city.getType().getRadius() < 0.2){
//                if (wayId == 7980116) {
//                    Logger.i(TAG, "Add city because is close, city:  " + city.getId() + ", name: " + city.getName());
//                }
                    streetCities.add(city);
                }
            }
            timeFindCityTestByDistance += System.currentTimeMillis() - start;
        }

        // GET CLOSEST FROM LOADED

        start = System.currentTimeMillis();
        if (streetCities.size() == 0) {
            // iterate again the cities and try to find the closest
            City city = getNearestCity(street.getGeometry().getCentroid(), citiesAround);

            if (city != null){
                // test how fare is the nearest city
                double distance = Utils.getDistance(streetCentroid, city.getCenter());
                if (distance / city.getType().getRadius() < 0.5){
                    streetCities.add(city);
                }
                else {
                    //TODO WRITE THESE STREETS INTO CUSTOM TABLE AND CHECK WHICH STREET ARE REMOVED
//                    Logger.e(TAG, "Can not find city for street: " + street.getName()
//                            + " geometry: " + Utils.geomToGeoJson(street.getGeometry()));
                }
            }
        }
        timeFindCityFindNearest += System.currentTimeMillis() - start;

        return streetCities;
    }

    /**
     * Select the closest city from the list
     * @param point Center for search
     * @return the closest city or null if no cities was found
     */
    private City getNearestCity(Point point, List<City> cities) {

        if (point == null || cities.size() == 0){
            return null;
        }

        City closestC = cities.get(0);
        double minDist = Utils.getDistance(point, closestC.getCenter());

        for (int i=1, size = cities.size(); i < size; i++){

            City city = cities.get(i);
            double distance = Utils.getDistance(point, city.getCenter());

            if (distance < minDist){
                minDist = distance;
                closestC = city;
            }
        }
        return closestC;
    }


    private MultiLineString unionStreetGeom (Street s1, Street s2){
        Geometry joinedGeom = s1.getGeometry().union(s2.getGeometry());
        return GeomUtils.geometryToMultilineString(joinedGeom);
    }

    /**
     * Find nearest street from defined list and point
     *
     * @param center point from which is searched nearest street
     * @param streets list of streets from which want to get nearest one
     * @return return nearest street
     */
    private Street getNearestStreet(Point center, List<Street> streets){

        Street nearestStreet = null;
        double minDistance = 0;
        for (Street street : streets) {
            if (nearestStreet == null){
                nearestStreet = street;
                minDistance = Utils.getDistanceNearest(center,street.getGeometry());
                continue;
            }
            double distance = Utils.getDistanceNearest(center, street.getGeometry());
            if (distance < minDistance){
                minDistance = distance;
                nearestStreet = street;
            }
        }
        return nearestStreet;
    }



    /**************************************************/
    /*                  OTHER UTILS
    /**************************************************/

    /**
     * Check if multilinestring contains some short lines. Such short segments are removed from geometry
     * If MultiLineString contains only one geom is automatically returned
     * @param mls geom to check
     * @param minLength minimal length that is accepted
     * @return
     */
    private MultiLineString removeShortSegments (MultiLineString mls, double minLength){

        int numSegments = mls.getNumGeometries();
        if (numSegments <= 1 ){
            return mls;
        }
        LineMerger lm = new LineMerger();

        for (int i=0; i < numSegments; i++){
            Geometry segment = mls.getGeometryN(i);
            Coordinate[] coordinates = segment.getCoordinates();
            double lengthM = Utils.getDistance(coordinates[0], coordinates[coordinates.length - 1]);
            if (lengthM > minLength){
                // this line is longer then 10 meters use it for street
                lm.add(segment);
            }
        }

        lm.getMergedLineStrings();

        List<LineString> lineStrings =  new ArrayList<LineString>(lm.getMergedLineStrings());
        return GeomUtils.mergeLinesToMultiLine(lineStrings);
    }

    /**
     * Test if way is type Highway and of proper type. Platform is not
     * valid ways for creation street
     * @param way way to test
     * @return true if it is way that is suiteble for creation street
     */
    private boolean isStreetWay (Way way) {

        String highwayVal = OsmUtils.getTagValue(way, OSMTagKey.HIGHWAY);
        if (highwayVal == null){
            return false;
        }

//        if (highwayVal.equals("track")){
//            return false;
//        }
//
//        if (highwayVal.equals("path")){
//            return false;
//        }

        if (highwayVal.equals("platform")){
            return false;
        }

        return true;
    }

    /**
     * Test tags of the way and check if it's track ot path way
     * @param entity osm entity to test
     * @return return true for track and path
     */
    private boolean isPath (Entity entity){

        String highwayVal = OsmUtils.getTagValue(entity, OSMTagKey.HIGHWAY);
        if (highwayVal == null){
            return false;
        }

        if (highwayVal.equals("track")){
            return true;
        }
        if (highwayVal.equals("path")){
            return true;
        }
        if (highwayVal.equals("cycleway")){
            return true;
        }
        return false;
    }

    /**
     * Try to find smaller or lower city part (suburb etc) in city for street
     * @param street
     * @param topCity
     * @return lower city part or null if does not exist any lower sub city
     */
    public City getSubCity (Street street, City topCity) {

        // try to get boundary for city
        Boundary topBoundary = dc.getCenterCityBoundaryMap().getValue(topCity);
        if (topBoundary == null){
            return null;
        }
        // load all cities that are in topBoundary
        List<City> subCities = dc.getCitiesInBoundaryMap().get(topBoundary);
        if (subCities == null){
            return null;
        }

        for (City subCity : subCities){
            if (subCity == topCity){
                continue;
            }
            // load boundary for sub city and find the lowest according to the admin level
            Boundary subBoundary = dc.getCenterCityBoundaryMap().getValue(subCity);
            if (subBoundary == null || !subBoundary.hasAdminLevel()){
                continue;
            }
            if (subBoundary.getAdminLevel() > topBoundary.getAdminLevel()){
                // boundary for this city is lower then top boundary > now check if is street in it
                if (subBoundary.getGeom().contains(street.getGeometry())){
                    return subCity;
                }
            }
        }
        return null;
    }


    /**
     * Get value of isIn tag as
     * @param entity OSM entity to get value of isIn tag
     * @return value as List of String or empty list of tag is not defined
     */
    private List<String> getIsInList(Entity entity) {

        String isIn = OsmUtils.getTagValue(entity, OSMTagKey.IS_IN);
        if (isIn == null) {
            return new ArrayList<>(0);
        }

        if (isIn.contains(";")){
            String[] splitted = isIn.split(";");

            List<String> isInList = new ArrayList<>(splitted.length);
            for (int i = 0; i < splitted.length; i++) {
                isInList.add(splitted[i].trim());
            }
            return isInList;
        }

        return new ArrayList<>(0);
    }

    /**
     * Create Multiline string that represent geometry of street
     * @param entity relation or way of the street
     * @return street geometry or null if entity does not contain street data (for example associatedStreet relations
     * can contain only building but not street geometry)
     */
    private MultiLineString createStreetGeom (Entity entity){

        if (entity == null){
            return null;
        }

        if (entity.getType() == EntityType.Node){
            return null;
        }
        else if (entity.getType() == EntityType.Way){
            Way way = (Way) entity;
            return createStreetGeomFromWay(way);
        }

        else if (entity.getType() == EntityType.Relation){

            Relation relation = (Relation) entity;
            LineMerger lineMerger = new LineMerger();
            MultiLineString mlsNext = null;

            for (RelationMember rm : relation.getMembers()){
                if (rm.getMemberType() == EntityType.Node){
                    continue;
                }
                else if (rm.getMemberType() == EntityType.Way){

                    Way way = dc.getWayFromCache(rm.getMemberId());
                    if (way == null){
                        continue;
                    }

                    if (rm.getMemberRole() == null || !rm.getMemberRole().equals("street")) {
                        //some relation does not have defined the member > try to guess if it is street
                        if ( isStreetWay(way)){
                            mlsNext = createStreetGeom(way);
                        }
                    }
                }
                else if (rm.getMemberType() == EntityType.Relation){
                    if (rm.getMemberId() == entity.getId()){
                        Logger.w(TAG, "The child member is the same relation as parent. Parent id: " + entity.getId());
                        continue;
                    }
                    Relation re = dc.getRelationFromCache(rm.getMemberId());
                    mlsNext = createStreetGeom(re);
                }

                if (mlsNext != null){
                    lineMerger.add(mlsNext);
                }
            }
            List<LineString> lineStrings =  new ArrayList<LineString>(lineMerger.getMergedLineStrings());
            MultiLineString mls = GeomUtils.mergeLinesToMultiLine(lineStrings);
            return mls;
        }
        return null;
    }


    /**
     * Crate LineString geometry from single way
     * @param way to create geometry from it
     * @return
     */
    private MultiLineString createStreetGeomFromWay (Way way){
        GeometryFactory geometryFactory = new GeometryFactory();
        // get full way with nodes
        WayEx wayEx = dc.getWay(way);
        if (wayEx == null || !wayEx.isValid()){
            return null;
        }
        LineString ls = geometryFactory.createLineString(wayEx.getCoordinates());
        return geometryFactory.createMultiLineString(new LineString[]{ls});
    }
}

