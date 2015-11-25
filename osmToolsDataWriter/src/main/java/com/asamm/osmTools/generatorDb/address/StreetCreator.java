package com.asamm.osmTools.generatorDb.address;

import com.asamm.osmTools.generatorDb.GeneratorAddress;
import com.asamm.osmTools.generatorDb.data.OsmConst.OSMTagKey;
import com.asamm.osmTools.generatorDb.data.WayEx;
import com.asamm.osmTools.generatorDb.dataContainer.ADataContainer;
import com.asamm.osmTools.generatorDb.db.DatabaseAddress;
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
import gnu.trove.list.array.TLongArrayList;
import gnu.trove.set.hash.TLongHashSet;
import org.apache.commons.lang3.StringUtils;
import org.openstreetmap.osmosis.core.domain.v0_6.*;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Created by voldapet on 2015-08-21 .
 */
public class StreetCreator {

    private static final String TAG = StreetCreator.class.getSimpleName();

    ADataContainer dc;

    GeneratorAddress ga;

    DatabaseAddress databaseAddress;

    HouseFactory houseFactory;

    GeometryFactory geometryFactory;



    /** Cached last street because often goes houses from the same street*/
    Street lastHouseStreet = new Street();

    /** Ids of relation that contains information about street and houses*/
    TLongList streetRelations;

    //TODO for production remove the time measures

    public long timeFindStreetCities = 0;
    public long timeLoadNereastCities = 0;
    public long timeFindCityTestByGeom = 0;

    public long timeJoinWaysToStreets = 0;
    public long timeInsertStreetSql = 0;

    public long timeFindStreetForHouse = 0;
    public long timeFindStreetAroundByName = 0;
    public long timeFindStreetSelectFromDB = 0;
    public long timeFindStreetSimilarName = 0;
    public long timeFindStreetAroundDummy = 0;
    public long timeFindStreetNearest = 0;
    public long timeCreateParseHouses = 0;

    // number of houses where we was not able to find proper street
    public int removedHousesWithDefinedPlace = 0;
    public int removedHousesWithDefinedStreetName = 0;
    public int numOfStreetForHousesUsingSqlSelect = 0;

    public StreetCreator (ADataContainer dc, GeneratorAddress ga){

        this.dc = dc;
        this.ga = ga;
        this.databaseAddress = ga.getDatabaseAddress();

        this.geometryFactory = new GeometryFactory();

        this.houseFactory = new HouseFactory(dc, ga);

        streetRelations = new TLongArrayList();
    }

    /**
     * Iterate relations > find that are street or associated street and create streets from members
     * Create streets are saved into database
     */
    public void createStreetFromRelations () {

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

            streetRelations.add(relationId);

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

            if (name == null) {
                continue;
            }
            // create street geom
            MultiLineString mls = createStreetGeom(relation);

            if (mls == null) {
                // probably associtate Address relation that does not contain any street member > skip it
                //Logger.i(TAG, "can not create street geom from relation id: " + relationId);
                continue;
            }

            Street street = new Street (name, isInList, mls);

            // find all cities where street can be in it or is close to city
            List<City> cities = findCitiesForStreet(street);
            street.addCityIds(cities);

            dc.addWayStreet(street);
        }
    }



    public void createStreetFromWays() {

        TLongList wayIds = dc.getWayIds();
        for (int i=0, size = wayIds.size(); i < size; i++) {

            long wayId = wayIds.get(i);

            Way way = dc.getWayFromCache(wayId);
            if (way == null){
                continue;
            }

            String name = OsmUtils.getStreetName(way);
            if ( !isStreetWay(way) || name == null){
                continue;
            }

            List<String> isInList = getIsInList(way);

            // create street geom
            MultiLineString mls = createStreetGeomFromWay(way);
            if (mls == null) {
                // probably associtate Address relation that does not contain any street member > skip it
                continue;
            }

            Street street = new Street (name, isInList, mls);

            // find all cities where street can be in it or is close to city
            long start = System.currentTimeMillis();
            List<City> cities = findCitiesForStreet(street);
            timeFindStreetCities += System.currentTimeMillis() - start;

            street.addCityIds(cities);
            dc.addWayStreet(street);
        }

        // combine all tmp street ways into streets
        joinWayStreets();
    }

    /**
     * Iterate thourh @link{#streetRelations} and create houses from members
     */
    public void createHousesFromRelations (){

        for (int i=0, size = streetRelations.size(); i < size; i++) {

            long relationId = streetRelations.get(i);

            Relation relation = dc.getRelationFromCache(relationId);
            if (relation == null) {
                continue;
            }
            // name from relation tags if not success than try to obtain it from way members or from houses
            String name = OsmUtils.getStreetName(relation);

            List<House> houses = new ArrayList<>();
            for (RelationMember rm : relation.getMembers()){

                if (name == null && rm.getMemberType() == EntityType.Way ) {
                    Way way = dc.getWayFromCache(rm.getMemberId());
                    if (name != null) {
                        // if name was not obtained from relation try it from the realtion member ways
                        name = OsmUtils.getStreetName(way);
                    }
                }

                String role = rm.getMemberRole();
                if (role.equals("house") || role.equals("address")){
                    Entity entityH = null;
                    if (rm.getMemberType() == EntityType.Way) {
                        entityH = dc.getWayFromCache(rm.getMemberId());
                    }
                    else if (rm.getMemberType() == EntityType.Node){
                        entityH = dc.getNodeFromCache(rm.getMemberId());
                    }

                    if (entityH == null){
                        Logger.i(TAG, "Can not get address relation member from cache. Relation" + relationId +
                                " Relation member: " + rm.getMemberId());
                        continue;
                    }

                    long start = System.currentTimeMillis();
                    houses.addAll(houseFactory.createHouse(entityH));
                    timeCreateParseHouses += System.currentTimeMillis() - start;
                }
            }

            // try to create house from relation itself, eq: https://www.openstreetmap.org/relation/1857530

            long start = System.currentTimeMillis();
            houses.addAll(houseFactory.createHouse(relation));
            timeCreateParseHouses += System.currentTimeMillis() - start;



            // test if new houses has defined the street name
            for (House house : houses) {

                start = System.currentTimeMillis();
                Street street = findStreetForHouse(house);
                timeFindStreetForHouse += System.currentTimeMillis() - start;
                if (street == null){

                    // try to search street again but set the name parse from relation
                    if (name != null && name.length() > 0) {
                        house.setStreetName(name);
                        start = System.currentTimeMillis();
                        street = findStreetForHouse(house);
                        timeFindStreetForHouse += System.currentTimeMillis() - start;
                        if (street == null){
                            //Logger.i(TAG, "Can not find street for house: " + house.toString());
                            continue;
                        }
                    }
                }
                databaseAddress.insertHouse(street, house);
            }
        }
    }

    public void createHousesFromWays () {

        TLongList wayIds = dc.getWayIds();
        for (int i=0, size = wayIds.size(); i < size; i++) {
            long wayId = wayIds.get(i);
            WayEx way = dc.getWay(wayId);
            long start = System.currentTimeMillis();
            List<House> houses = houseFactory.createHouse(way);
            timeCreateParseHouses += System.currentTimeMillis() - start;;

            for(House house : houses) {

                start = System.currentTimeMillis();
                Street street = findStreetForHouse(house);
                timeFindStreetForHouse += System.currentTimeMillis() - start;
                if (street == null){
                    //Logger.w(TAG, "createHousesFromWays(): Can not find street for house: " + house.toString());
                    continue;
                }

                // insert house into db
                databaseAddress.insertHouse(street, house);
            }
        }
    }

    public void createHousesFromNodes () {
        TLongList nodeIds = dc.getNodeIds();
        for (int i=0, size = nodeIds.size(); i < size; i++) {
            long nodeId = nodeIds.get(i);
            Node node = dc.getNode(nodeId);

            long start = System.currentTimeMillis();
            List<House> houses = houseFactory.createHouse(node);
            timeCreateParseHouses += System.currentTimeMillis() - start;

            for(House house : houses) {

                start = System.currentTimeMillis();
                Street street = findStreetForHouse(house);
                timeFindStreetForHouse += System.currentTimeMillis() - start;
                if (street == null){
                    //Logger.w(TAG, "createHousesFromNodes(): Can not find street for house: " + house.toString());
                    continue;
                }

                // insert house into db
                databaseAddress.insertHouse(street, house);
            }
        }
    }

    /**
     * Iterate through wayStreets (osm ways) and join ways into one Street. In first step
     * are joined all wayStreet of the same name that are in the same city. BUT there can
     * be more then one street in the city. For this reason we fix it by splitting the joined
     * street into independent streets
     */
    private void joinWayStreets() {

        Logger.i(TAG, "Start to join ways into streets and insert them into DB");
        long start = System.currentTimeMillis();

        dc.finalizeWayStreetCaching();

        Iterator<Integer> iterator = dc.getStreetHashSet().iterator();
        TLongHashSet cityIds = new TLongHashSet();
        List<House> houses = new ArrayList<>();

        while (iterator.hasNext()){

            cityIds = new TLongHashSet();
            int hash = iterator.next();
            //load all ways with the same hash from cache. These all ways has the same name but different city
            List<Street> wayStreets = dc.getWayStreetsFromCache(hash);
            if (wayStreets.size() ==0){
                continue;
            }

            LineMerger lineMerger = new LineMerger();

            // how it works: takes the last wayStreet then subiterate all waystreet with the same name and try
            // to find other wayStreet from that share at least on cityId. If found it join it with the waystreet from
            // parent loop. then delete it.
            // Becease different wayStreet can have different cityIds it's needed to iterate several times.

            for (int i = wayStreets.size() -1; i >=0; i--){
                lineMerger = new LineMerger();
                Street wayStreet = wayStreets.get(i);
                wayStreets.remove(i);

                cityIds = wayStreet.getCityIds();
                houses = wayStreet.getHouses();
                lineMerger.add(wayStreet.getGeometry());
                boolean sameStreetFounded = false;

                do {
                    sameStreetFounded = false;
                    for (int j = i-1; j >= 0; j--){
                        Street wayStreetToJoin = wayStreets.get(j);
//                        if (wayStreet.getName().equals("Friedhofstraße")) {
//                            Logger.i(TAG, "test streeet: " + wayStreetToJoin.toString());
//                        }

                        if (isFromTheSameCities(cityIds, wayStreetToJoin.getCityIds())){
                            // it street from the same cities prepare them for join
//                            if (wayStreet.getName().equals("Friedhofstraße")) {
//                                Logger.i(TAG, "Add geometry: " + Utils.geomToGeoJson(wayStreetToJoin.getGeometry()));
//                            }
                            lineMerger.add(wayStreetToJoin.getGeometry());
                            cityIds.addAll(wayStreetToJoin.getCityIds());
                            houses.addAll(wayStreetToJoin.getHouses());

                            // remove this street way
                            wayStreets.remove(j);
                            j--;
                            i--;
                            sameStreetFounded = true;
                        }
                    }
                } while (sameStreetFounded);

                // CREATE STREET GEOM

                MultiLineString mls;
                List<LineString> lineStrings =  new ArrayList<LineString>(lineMerger.getMergedLineStrings());

                int linesSize = lineStrings.size();
                if (linesSize == 1){
                    mls = geometryFactory.createMultiLineString(new LineString[]{lineStrings.get(0)});
                }
                else if (linesSize > 1) {
                    mls = (MultiLineString)  geometryFactory.buildGeometry(lineStrings);
                }
                else {
                    Logger.w(TAG, "joinWayStreets(): Can not create geometry for street:  " + wayStreet.getId());
                    continue;
                }

                // CREATE STREET
                // now we joined all wayStreet with the same name and that are in the same city.

                Street street = new Street();
                street.setName(wayStreet.getName());
                street.setGeometry(mls);
                street.setCityIds(cityIds);
                street.setHouses(houses);

                // SEPARATE PART (city can have more streets with the same name)

                if (linesSize == 1){
                    // street has simple line geom insert it into DB
                    long start3 = System.currentTimeMillis();
                    long id = databaseAddress.insertStreet(street, false);
                    timeInsertStreetSql += System.currentTimeMillis() - start3;
                }
                else {
                    // street geom has more parts. Maybe it is two different street > try to separate it
                    List<Street> streets = splitToCityParts (street);
                    for (Street streetToInsert : streets){
                        long start3 = System.currentTimeMillis();
                        long id = databaseAddress.insertStreet(streetToInsert, false);
                        timeInsertStreetSql += System.currentTimeMillis() - start3;
                    }
                }
            }
            timeJoinWaysToStreets += System.currentTimeMillis() - start;
        }
    }

    private List<Street> splitToCityParts(Street street) {

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
    private List<City> findCitiesForStreet(Street street) {
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

        List<City> cities = ga.getClosestCities(streetCentroid, 30);
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
            if (streetGeomPrepared.intersects(city.getGeom())){
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
    /*                  HOUSE UTILS
    /**************************************************/

    /**
     * Try to find appropriate street for House
     * @param house for that we want to find street
     * @return the best way for house or null if no street was found
     */
    private Street findStreetForHouse (House house){

        String addrStreetName = house.getStreetName();
        String addrPlace = house.getPlace();
        String addrCity = house.getCityName();

        if (addrStreetName.length() > 0){

            // test the last founded street as first
            if (lastHouseStreet.getName().equalsIgnoreCase(addrStreetName)){
                return lastHouseStreet;
            }

            List<Street> streetsAround = databaseAddress.getStreetsAround(house.getCenter(), 25);

            for (Street street : streetsAround) {
                //Logger.i(TAG, "Street to check" + street.toString());
                if (street.getName().equalsIgnoreCase(addrStreetName)){
                    lastHouseStreet = street;
                    return street;
                }
            }
            // was not able to find the nereast street with the same name > try to select by name from addressdb
            if (house.getCityName().length() > 0 ){

                long start = System.currentTimeMillis();
                Street street = databaseAddress.selectStreetByNames(house.getCityName(), addrStreetName);
                timeFindStreetSelectFromDB += System.currentTimeMillis() - start;
                if (street != null){
                    numOfStreetForHousesUsingSqlSelect++;
                    lastHouseStreet = street;
                    return street;
                }
            }

            // house has defined the name but can not find the street with the same name > check the similar name
            long startSim = System.currentTimeMillis();
            Street maxSimilarStreet = null;
            double maxSimilarityScore = 0;
            for (Street street : streetsAround) {
                if (maxSimilarStreet == null){
                    maxSimilarStreet =  street;
                }

                double similarity = StringUtils.getJaroWinklerDistance(addrStreetName, street.getName());
                if (similarity > maxSimilarityScore){
                    maxSimilarityScore = similarity;
                    maxSimilarStreet = street;
                }
            }

            if (maxSimilarityScore > 0.9){
//                Logger.i(TAG, "Sim: "+maxSimilarityScore+" For house "+house.getOsmId() +" with streetname: " + addrStreetName +
//                " was, found street:  " + maxSimilarStreet.toString());
                timeFindStreetSimilarName += System.currentTimeMillis() - startSim;
                lastHouseStreet = maxSimilarStreet;
                return maxSimilarStreet;
            }
        }
        else  if ( addrPlace.length() > 0 ){
            // street name is not defined but we have place name. This is common for villages

            // test the last founded street as first
            if (lastHouseStreet.getName().equalsIgnoreCase(addrPlace)){
                return lastHouseStreet;
            }

            List<Street> dummyStreetsAround = databaseAddress.getDummyStreetsAround(house.getCenter(), 20);
            for (Street street : dummyStreetsAround) {
                //Logger.i(TAG, "Street to check" + street.toString());
                if (street.getName().equalsIgnoreCase(addrPlace)){
//                    Logger.i(TAG, "findStreetForHouse() - Use the dummy street with the same name as place" +
//                            "\n place: " + addrPlace + " , city: " + addrCity + ", street: " + street.toString());
                    lastHouseStreet = street;
                    return street;
                }
            }

            //no dummy streets fits our needs try to find the closest city
            List<City> closestCities = ga.getClosestCities(house.getCenter(), 30);
            for (City city :  closestCities) {
                if (city.getName().equalsIgnoreCase(addrPlace)){

                    Street streetToInsert = databaseAddress.createDummyStreet(city.getName(), city.getId(), city.getCenter());
                    long start3 = System.currentTimeMillis();
                    long id = databaseAddress.insertStreet(streetToInsert, true);
                    timeInsertStreetSql += System.currentTimeMillis() - start3;

//                    Logger.i(TAG, "findStreetForHouse(): Create new dummy street for found city: " + city.getName() +
//                            " new street: " + street.toString());
                    lastHouseStreet = streetToInsert;
                    return streetToInsert;
                }
            }
        }

        // was not able to find street based on name > try the nereast one and
        //Logger.i(TAG, "Looking for nearest street for house: " + house.toString());
        long start = System.currentTimeMillis();
        List<Street> streetsAround = databaseAddress.getStreetsAround(house.getCenter(), 7);
        streetsAround.addAll(databaseAddress.getDummyStreetsAround(house.getCenter(), 7));

        Street nearestStreet = null;
        double minDistance = Float.MAX_VALUE;
        Point houseCenter = house.getCenter();
        for (Street street : streetsAround) {
            if (nearestStreet == null){
                nearestStreet =  street;
            }

            double distance = houseCenter.distance(street.getGeometry());
            if (distance < minDistance){
                minDistance = distance;
                nearestStreet = street;
            }
        }

        timeFindStreetNearest += System.currentTimeMillis() - start;


        if (addrStreetName.length() > 0 && Utils.toMeters(minDistance) > 150){
//            Logger.i(TAG, "findStreetForHouse(): HOuse has name street but the nereast street is far away. Distance: " + Utils.toMeters(minDistance) +
//                    "\n House: " + house.toString() + " \n Nereast street: " + nearestStreet.toString());
            databaseAddress.insertRemovedHouse(house);
            removedHousesWithDefinedStreetName++;
            return null;
        }

//        Logger.i(TAG, "### Nearest street: " +
//                "\n House: " + house.toString() + " \n Street: " + nearestStreet.toString());
        lastHouseStreet = nearestStreet;
        return nearestStreet;
    }


    /**************************************************/
    /*                  OTHER UTILS
    /**************************************************/


    /**
     * Test if way is type Highway and of proper type. For example track or platform are not
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
     * Try to find smaller or lower city part (subburb etc) in city for street
     * @param street
     * @param topCity
     * @return lower city part or null if does not exist any lower sub city
     */
    public City getSubCity (Street street, City topCity) {

        // try to get boundary for city
        Boundary topBoundary = ga.getCenterCityBoundaryMap().get(topCity.getId());
        if (topBoundary == null){
            return null;
        }
        // load all cities that are in topBoundary
        List<City> subCities = ga.getCitiesInBoundaryMap().get(topBoundary);
        if (subCities == null){
            return null;
        }

        for (City subCity : subCities){
            if (subCity == topCity){
                continue;
            }
            // load boundary for sub city and find the lowest according to the admin level
            Boundary subBoundary = ga.getCenterCityBoundaryMap().get(subCity.getId());
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

                    if (joinedGeom instanceof MultiLineString){
                        mls = (MultiLineString) joinedGeom;
                    }
                    else {
                        LineString ls = (LineString) joinedGeom;
                        mls = geometryFactory.createMultiLineString(new LineString[]{ls});
                    }
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

        // get full way with nodes
        WayEx wayEx = dc.getWay(way);
        if (wayEx == null || !wayEx.isValid()){
            return null;
        }
        LineString ls = geometryFactory.createLineString(wayEx.getCoordinates());
        return geometryFactory.createMultiLineString(new LineString[]{ls});
    }

}

