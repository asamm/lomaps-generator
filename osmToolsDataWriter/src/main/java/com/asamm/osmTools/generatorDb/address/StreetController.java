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
import com.vividsolutions.jts.geom.prep.PreparedGeometry;
import com.vividsolutions.jts.geom.prep.PreparedGeometryFactory;
import com.vividsolutions.jts.index.quadtree.Quadtree;
import com.vividsolutions.jts.operation.linemerge.LineMerger;
import gnu.trove.iterator.TLongIterator;
import gnu.trove.list.TLongList;
import gnu.trove.set.hash.THashSet;
import gnu.trove.set.hash.TLongHashSet;
import org.openstreetmap.osmosis.core.domain.v0_6.*;

import java.util.ArrayList;
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

    private ADataContainer dc;

    private GeneratorAddress ga;

    private DatabaseAddress databaseAddress;

    private HouseController houseFactory;

    private GeometryFactory geometryFactory;

    //TODO for production remove the time measures

    public long timeFindStreetCities = 0;
    public long timeLoadNereastCities = 0;
    public long timeFindCityTestByGeom = 0;

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
            //Logger.i(TAG, "Create street for relation id: " + relationId);

            Relation relation = dc.getRelationFromCache(relationId);
            if (relation == null) {
                continue;
            }

            // get osm tag type
            String type = OsmUtils.getTagValue(relation, OSMTagKey.TYPE);
            if (type == null){
                continue;
            }

            if ( !(type.equals(OSMTagKey.STREET.getValue())
                    || type.equals(OSMTagKey.ASSOCIATED_STREET.getValue())
                    || type.equals((OSMTagKey.MULTIPOLYGON.getValue())))){
                continue;
            }

            // remember that this relation can be street (later use it for generation og houses)
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
            MultiLineString mls = createStreetGeom(relation);

            if (mls == null) {
                // probably associtate Address relation that does not contain any street member > skip it
                //Logger.i(TAG, "can not create street geom from relation id: " + relationId);
                continue;
            }

            Street wayStreet = new Street (name, isInList, mls);
            wayStreet.setOsmId(relationId);
            wayStreet.setPath(isPath(relation));

            // find all cities where street can be in it or is close to city
            List<City> cities = findCitiesForStreet(wayStreet);
            wayStreet.addCityIds(cities);


            if (name == null || name.length() == 0) {
                IndexController.getInstance().insertStreetUnnamed(wayStreet.getGeometry().getEnvelopeInternal(), wayStreet);
                dc.addWayStreetUnnamed(wayStreet);
            }
            else {
                dc.addWayStreet(wayStreet);
            }
        }
    }


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
                // probably associtate Address relation that does not contain any street member > skip it
                continue;
            }

            String name = OsmUtils.getStreetName(way);
            Street wayStreet = new Street (name, isInList, mls);
            wayStreet.setOsmId(wayId);
            wayStreet.setPath(isPath(way));

            // find all cities where street can be in it or is close to city
            long start = System.currentTimeMillis();
            List<City> cities = findCitiesForStreet(wayStreet);
            timeFindStreetCities += System.currentTimeMillis() - start;
            wayStreet.addCityIds(cities);

            if (name == null || name.length() == 0) {
                dc.addWayStreetUnnamed(wayStreet);
                IndexController.getInstance().insertStreetUnnamed(wayStreet.getGeometry().getEnvelopeInternal(), wayStreet);
            }
            else {
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
                        if (wayStreet.getName().equals("Lipec")) {
                            Logger.i(TAG, "test street: " + wayStreetToJoin.toString());
                            Logger.i(TAG, "Num of houses for Lipec: " + houses.size());
                        }

                        if (isFromTheSameCities(cityIds, wayStreetToJoin.getCityIds())){
                            // it street from the same cities prepare them for join
//                            if (wayStreet.getName().equals("Friedhofstraße")) {
//                                Logger.i(TAG, "Add geometry: " + Utils.geomToGeoJson(wayStreetToJoin.getGeometry()));
//                            }
                            //lineMerger.add(wayStreetToJoin.getGeometry());
                            waysGeomsToJoin.add(wayStreetToJoin.getGeometry());
                            cityIds.addAll(wayStreetToJoin.getCityIds());
                            houses.addAll(wayStreetToJoin.getHouses());
                            isPath = (isPath) ? true : wayStreet.isPath();

                            // remove this street way
                            wayStreets.remove(j);
                            j--;
                            i--;
                            sameStreetFounded = true;
                        }
                    }
                } while (sameStreetFounded);

                // CREATE STREET GEOM
                lineMerger.add(waysGeomsToJoin);
                List<LineString> lineStrings =  new ArrayList<LineString>(lineMerger.getMergedLineStrings());
                MultiLineString mls = GeomUtils.mergeLinesToMultiLine(lineStrings);
                if (mls == null) {
                    Logger.w(TAG, "joinWayStreets(): Can not create geometry for street:  " + wayStreet.getOsmId());
                    continue;
                }

                // CREATE STREET
                // now we joined all wayStreet with the same name and that are in the same city.

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
     * Joined street can lay in more then one city. But some times it's not one street but
     * two different street with the same name. This method identify if geometry of street
     * is only one street or if it is two or more different citiyes
     * @param street street to test if it is one or more different streets
     * @return list of separated street
     */
    public List<Street> splitToCityParts(Street street) {

        List<Street> separatedStreets = new ArrayList<>();
        MultiLineString mls = street.getGeometry();
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
            separatedStreets.add(street);
            return separatedStreets;
        }

        // for every geometry create new street object with the same name but find the street is in again
        for (MultiLineString mlsSep : mlsSeparated){

            Street streetSep = new Street();
            streetSep.setName(street.getName());
            streetSep.setGeometry(mlsSep);
            List<City> cities = findCitiesForStreet(streetSep);
            streetSep.addCityIds(cities);
            streetSep.setHouses(street.getHouses());
            streetSep.setPath(street.isPath());

            separatedStreets.add(streetSep);

            //Logger.i(TAG, "splitToCityParts(): New separated street: " + streetSep.toString());
        }

        return separatedStreets;
    }

    /**
     * Due to system of joining way streets can be joined two or more different streets
     * with the same name into one street. This method identify geometries that
     * are far away from each other and create saparate geometries for
     * every particular street
     * @param elements list of linestring, this is result of joining ways with the same name in city
     * @return geometries for new separate streets
     */
    private List<MultiLineString> splitGeomDifferentStreets(List<LineString> elements){

        // max distance between elements where we expect that are in the same street
        double maxDistance = 150;
        double[] distanceDeg = Utils.metersToDlatDlong(elements.get(0).getCoordinate(), maxDistance/2);

        // list geoms for separated streets
        List<MultiLineString> splittedMls = new ArrayList<>();

        List<LineString> lsList = new ArrayList<>();

        // create temp index of line strings
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
        Point streetCentroid = street.getGeometry().getCentroid();

        if ( !streetCentroid.isValid()){
            // centroid for street with same points is NaN. This is workaround
            streetCentroid = geometryFactory.createPoint(street.getGeometry().getCoordinate());
        }

        // decide if cities are iterate from memory or based on DB results
        long start = System.currentTimeMillis();
//        List<City> cities = ga.getCities(); //all cities for map
//        if (cities.size() > 100){
//            cities = databaseAddress.loadNearestCities(streetCentroid, 30);
//        }

        List<City> cities = IndexController.getInstance().getClosestCities(streetCentroid, 30);
        timeLoadNereastCities += System.currentTimeMillis() - start;

        //RECOGNIZE BY IS IN TAG

        List<String> isInNames = street.getIsIn();
        if (isInNames.size() > 0){
            for (int i = cities.size() - 1; i >= 0; i--){
                City city = cities.get(i);
                if (isInNames.contains(city.getName())){
                    streetCities.add(city);
                    cities.remove(i);
                }
            }
        }

        // RECOGNIZE BY CONTAINS GEOM

        // create index from rest of cities if boundary exist
        List<City> citiesWithoutBound = new ArrayList<>();
        start = System.currentTimeMillis();

        // TODO there are two ways how to check if city contains the street > test it for speed
//        STRtree cityBoundsIndex = new STRtree();
//        for (City city : cities){
//            MultiPolygon mp = city.getGeom();
//            if (mp == null){
//                // this city does not have defined the bound geometry
//                citiesWithoutBound.add(city);
//                continue;
//            }
//            cityBoundsIndex.insert(city.getGeom().getEnvelopeInternal(), city);
//        }
//        // search all cities that intersect or contain street
//        List<City> result = cityBoundsIndex.query(street.getGeometry().getEnvelopeInternal());
//        streetCities.addAll(result);

        PreparedGeometry streetGeomPrepared = PreparedGeometryFactory.prepare(street.getGeometry());
        for (City city : cities){
            MultiPolygon mp = city.getGeom();
            if (mp == null){
                // this city does not have defined the bound geometry
                citiesWithoutBound.add(city);
                continue;
            }

            // TODO decide if ti test intersect or contains
            if (streetGeomPrepared.coveredBy(city.getGeom())){
                streetCities.add(city);
            }
        }

        timeFindCityTestByGeom += System.currentTimeMillis() - start;

        // RECOGNIZE BY DISTANCE

        // for rest of cities that does not have defined the bounds check distance
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

        if (streetCities.isEmpty()) {
            // iterate again the cities and try to find the closest
            City city = getClosestCity(street.getGeometry().getCentroid());

            if (city != null){
                streetCities.add(city);
            }
            else {
                Logger.e(TAG, "Can not find city for street: " + street.getName()
                        + " geometry: " + Utils.geomToGeoJson(street.getGeometry()));
            }
        }
        return streetCities;
    }

    /**
     * Try to find the closest city to specific point
     * @param point Center for search
     * @return the closest city or null if no cities was found
     */
    private City getClosestCity (Point point) {

        List<City> cities = ga.getCities(); //all cities for map

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

        MultiLineString mlsJoined;
        if (joinedGeom instanceof MultiLineString){
            mlsJoined = (MultiLineString) joinedGeom;
        }
        else {
            LineString ls = (LineString) joinedGeom;
            mlsJoined = geometryFactory.createMultiLineString(new LineString[]{ls});
        }

        return mlsJoined;
    }





    /**************************************************/
    /*                  OTHER UTILS
    /**************************************************/


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
            MultiLineString mls = null;

            for (RelationMember rm : relation.getMembers()){
                MultiLineString mlsNext = null;

                if (rm.getMemberType() == EntityType.Way){

                    Way way = dc.getWayFromCache(rm.getMemberId());
                    if (way == null){
                        continue;
                    }

                    if (rm.getMemberRole() == null || !rm.getMemberRole().equals("street")) {
                        //some relation does not have defined the member > try to guess if it is street
                        if ( !isStreetWay(way)){
                            continue;
                        }
                    }

                    mlsNext = createStreetGeom(way);
                }
                else if (rm.getMemberType() == EntityType.Relation){
                    Relation re = dc.getRelationFromCache(rm.getMemberId());
                    mlsNext = createStreetGeom(re);
                }

                // combine linestrings
                if (mls == null ){
                    mls = mlsNext;
                }
                else if (mlsNext != null){
                    Geometry joinedGeom = mls.union(mlsNext);
                    mls = GeomUtils.geometryToMultilineString(joinedGeom);
                }
            }
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

