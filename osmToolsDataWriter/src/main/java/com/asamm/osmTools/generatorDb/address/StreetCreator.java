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
import com.vividsolutions.jts.index.strtree.STRtree;
import com.vividsolutions.jts.operation.union.UnaryUnionOp;
import gnu.trove.iterator.TLongIterator;
import gnu.trove.list.TLongList;
import gnu.trove.set.hash.THashSet;
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

    // number of houses where we was not able to find proper street
    int removedHousesWithoutStreet = 0;

    /** Cached last street becase often goes houses from the same street*/
    Street lastHouseStreet = null;

    public long timeFindStreetCities = 0;
    public long timeInsertStreetTmpTime = 0;
    public long timeLoadNereastCities = 0;
    public long timeFindCityTestBoundaries = 0;
    public long timeFindCityTestIsIn = 0;

    public long timeJoinWaysToStreets = 0;
    public long timeInsertOrUpdateStreetsWhole = 0;
    public long timeSelectPreviousStreetFromDB = 0;
    public long timeUpdateStreetSql = 0;
    public long timeInsertStreetSql = 0;


    public StreetCreator (ADataContainer dc, GeneratorAddress ga){

        this.dc = dc;
        this.ga = ga;
        this.databaseAddress = ga.getDatabaseAddress();

        this.geometryFactory = new GeometryFactory();

        this.houseFactory = new HouseFactory(dc, ga);
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

            List<String> isInList = new ArrayList<>();
            String name = null;

            // in first step try to obtain name from "first" street member
            for (RelationMember re : relation.getMembers()){
                if (re.getMemberType() == EntityType.Way){
                    Way way = dc.getWayFromCache(re.getMemberId());
                    if (way == null){
                        continue;
                    }
                    name = getStreetName(way);
                    isInList = getIsInList(way);
                    break;
                }
            }

            if (name == null || name.length() == 0){
                name = getStreetName(relation);
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

            // HOUSE PART

            List<Entity> addressEntities = new ArrayList<>();
            for (RelationMember rm : relation.getMembers()){

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

                    addressEntities.add(entityH);
                }
            }

            street = processAddressEntities(street, addressEntities);

            // find all cities where street can be in it or is close to city
            List<City> cities = findStreetCities(street, relationId);
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

            String name = getStreetName(way);
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
            List<City> cities = findStreetCities(street, wayId);
            timeFindStreetCities += System.currentTimeMillis() - start;

            start = System.currentTimeMillis();
            // for every possible city create copy of street
            street.addCityIds(cities);

            dc.addWayStreet(street);

            timeInsertStreetTmpTime += System.currentTimeMillis() - start;
        }

        // combine all tmp street ways into streets
        joinWayStreets();
    }

    public void createHousesFromWays () {
        TLongList wayIds = dc.getWayIds();
        for (int i=0, size = wayIds.size(); i < size; i++) {
            long wayId = wayIds.get(i);
            WayEx way = dc.getWay(wayId);
            List<House> houses = houseFactory.createHouse(way);

            for(House house : houses) {

                if (house.getCenter() == null){

                    Logger.w(TAG, "House does not have defined the center: " + house.toString());


                }



                Street street = findStreetForHouse(house);
                if (street == null){
                    Logger.w(TAG, "Can not find street for house: " + house.toString());
                    continue;
                }

                // insert house into db
                databaseAddress.insertHouse(street, house);
            }

        }
    }

    /**
     * iterate through hashes of created streets from osm ways and join ways into one Street
     */
    private void joinWayStreets() {

        Logger.i(TAG, "Start to join ways into streets and insert them into DB");

        dc.finalizeWayStreetCaching();

        Iterator<Integer> iterator = dc.getStreetHashSet().iterator();
        List<MultiLineString> geoms = new ArrayList<>();
        TLongHashSet cityIds = new TLongHashSet();
        THashSet<House> houses = new THashSet<>();


        while (iterator.hasNext()){
            long start = System.currentTimeMillis();
             cityIds = new TLongHashSet();
            int hash = iterator.next();
            //load all ways with the same hash from cache. These all ways has the same name but different city
            List<Street> wayStreets = dc.getWayStreetsFromCache(hash);
            if (wayStreets.size() ==0){
                continue;
            }

            // how it works: takes the last wayStreet then subiterate all waystreet with the same name and try
            // to find other wayStreet from that share at least on cityId. If found it join it with the waystreet from
            // parent loop. then delete it.
            // Becease different wayStreet can have different cityIds it's needed to iterate several times.

            for (int i = wayStreets.size() -1; i >=0; i--){

                geoms = new ArrayList<>(); // geometries for created street;
                Street wayStreet = wayStreets.get(i);
                wayStreets.remove(i);

                cityIds = wayStreet.getCityIds();
                houses = wayStreet.getHouses();
                geoms.add(wayStreet.getGeometry());
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
                            geoms.add(wayStreetToJoin.getGeometry());
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

                MultiLineString mls;
                Geometry geomUnion;
                try {
                    geomUnion = UnaryUnionOp.union(geoms, geometryFactory);
                }
                catch (TopologyException e){
                    Logger.e(TAG, "Topology exception when union way: " + wayStreet.getName() , e);
                    // TODO better fix for topology exception
                    geomUnion = geoms.get(0);
                }

                if (geomUnion instanceof LineString){
                    mls = geometryFactory.createMultiLineString(new LineString[]{(LineString) geomUnion});
                }
                else {
                    mls = (MultiLineString) geomUnion;
                }

                Street street = new Street();
                street.setName(wayStreet.getName());
                street.setGeometry(mls);
                street.setCityIds(cityIds);
                street.setHouses(houses);

                // insert new joined street into db
                long start3 = System.currentTimeMillis();

                long id = databaseAddress.insertStreet(street);

                timeInsertStreetSql += System.currentTimeMillis() - start3;

            }
            timeJoinWaysToStreets += System.currentTimeMillis() - start;
        }
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
     * Create list of way street for every city that can contain this street.
     * @param street streets to fill with city
     * @param streetCities list of cities in which can be street placed
     * @return
     */
//    private List<Street> createWayStreetsForCities(Street street, List<City> streetCities){
//
//        List<Long> cityIds = new ArrayList<>();
//        List<Street> streets = new ArrayList<>();
//
//        // create list of city ids in which is this way (street)
//        for (City city : streetCities){
//            cityIds.add(city.getId());
//        }
//
//        // add cityIds into street so every copy know other streets
//        street.setCityIds(cityIds);
//
//        for (City city : streetCities){
//            // create copy of street
//            Street streetCreated = new Street(street);
//            streetCreated.setCityId(city.getId());
//
//            streets.add(streetCreated);
//        }
//        return streets;
//    }

    /**
     * Find cities that street is in it.
     * @param street
     * @return
     */
    private List<City> findStreetCities (Street street, long wayId) {


        List<City> streetCities = new ArrayList<>(); // cities where street is in it
        Point streetCentroid = street.getGeometry().getCentroid();

        // decide if cities are iterate from memory or based on DB results
        long start = System.currentTimeMillis();
//        List<City> cities = ga.getCities(); //all cities for map
//        if (cities.size() > 100){
//            cities = databaseAddress.loadNearestCities(streetCentroid, 30);
//        }

        List<City> cities = ga.getClosestCities(streetCentroid, 30);
        timeLoadNereastCities += System.currentTimeMillis() - start;

        List<City> citiesWithoutBound = new ArrayList<>();


        // recognize city by the name and isIn tag
        for (int i = cities.size() - 1; i >= 0; i--){
            City city = cities.get(i);
            start = System.currentTimeMillis();
            if (street.getIsIn().contains(city.getName())){
                streetCities.add(city);
                cities.remove(i);
                timeFindCityTestIsIn += System.currentTimeMillis() - start;
            }
        }

        // create index from rest of cities if boundary exist
        start = System.currentTimeMillis();
        STRtree cityBoundsIndex = new STRtree();
        for (City city : cities){
            MultiPolygon mp = city.getGeom();
            if (mp == null){
                // this city does not have defined the bound geometry
                citiesWithoutBound.add(city);
                continue;
            }
            cityBoundsIndex.insert(city.getGeom().getEnvelopeInternal(), city);
        }
        timeFindCityTestBoundaries += System.currentTimeMillis() - start;

        // search all cities that intersect or contain street
        streetCities.addAll(cityBoundsIndex.query(street.getGeometry().getEnvelopeInternal()));

        // for rest of cities that does not have defined the bounds check distance etc

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

//    /**
//     * Insert street with name, city and subcity into database. If the same street already exist
//     * then join old geometry with new one and update old record
//     * @param street street to insert
//     * @return id of inserted street
//     */
//    private long insertOrUpdateStreet(Street street) {
//
//        if ( !street.isValid()) {
//            //Logger.i(TAG, "Street is not valid can not insert or update db:  " + street.toString());
//            return 0;
//        }
//
//        long id = 0;
//
//        // try to load same street from database
//
//        long start = System.currentTimeMillis();
//        List<Street> loadedStreets = databaseAddress.selectStreetInCities(street);
//        timeSelectPreviousStreetFromDB += System.currentTimeMillis() - start;
//
//        int size = loadedStreets.size();
//        if (size == 0) {
//            // database does not contain such street > simple insert new record
//            long start3 = System.currentTimeMillis();
//            id = databaseAddress.insertWayStreet(street);
//            timeInsertStreetSql += System.currentTimeMillis() - start3;
//
//        }
//
//        if (size == 1){
//
//            //join loaded street with new one and update the loaded street
//            // already exist street with this name for different with any same city
//            Street loadedStreet = loadedStreets.get(0);
//
//            List<Long> cityIdsNotSaved = new ArrayList<>();
//            for (Long cityId : street.getCityIds()){
//                if ( !loadedStreet.getCityIds().contains(cityId)){
//                    cityIdsNotSaved.add(cityId);
//                }
//            }
//
//            loadedStreet.setGeometry(unionStreetGeom(loadedStreet, street));
//            loadedStreet.setCityIds(cityIdsNotSaved);
//            //Logger.w(TAG, "Joined geom: ." + loadedStreet.toString());
//            // update street geom
//            long start2 = System.currentTimeMillis();
//            id = databaseAddress.updateStreet(loadedStreet);
//            timeUpdateStreetSql += System.currentTimeMillis() - start2;
//        }
//
//        if (size > 1){
//            // there more streets in the DB > join them into the new one and delete the previous records in db
//            List<Long> cityIds = new ArrayList<>();
//            for (int i=0; i < size; i++ ){
//                Street loadedStreet = loadedStreets.get(i);
//
//                for (Long cityId : street.getCityIds()){
//                    if ( !street.getCityIds().contains(cityId)){
//                        street.addCityId(cityId);
//                    }
//                }
//
//                street.setGeometry(unionStreetGeom(street, loadedStreet));
//                // delete old record
//                databaseAddress.deleteStreet(loadedStreet.getId());
//            }
//
//            // insert new joined street into db
//            long start3 = System.currentTimeMillis();
//            id = databaseAddress.insertWayStreet(street);
//            timeInsertStreetSql += System.currentTimeMillis() - start3;
//        }
//
//        return id;
//    }

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

    private Street processAddressEntities(Street street, List<Entity> entities) {

        for (Entity entityH : entities){

            List<House> houses = houseFactory.createHouse(entityH);
            //Logger.i(TAG, "Created house: " + house.toString());

            for (House house: houses){
                street.addHouse(house);
                databaseAddress.insertHouse(street, house);
            }
        }

        return street;
    }

    /**
     * Try to find appropriate street for House
     * @param house for that we want to find street
     * @return the best way for house or null if no street was found
     */
    private Street findStreetForHouse (House house){

        //Logger.i(TAG, "Look for street: House: "  + house.toString());

        List<Street> streetsAround = databaseAddress.getStreetsAround(house.getCenter(), 10);
        String addrStreetName = house.getStreetName();


        if (addrStreetName.length() > 0){
            for (Street street : streetsAround) {

                //Logger.i(TAG, "Street to check" + street.toString());
                if (street.getName().equals(addrStreetName)){
                    return street;
                }
            }
            // was not able to find the nereast street with the same name > try to select by name from addressdb
            if (house.getCityName().length() > 0 ){

                Street street = databaseAddress.selectStreetByNames(house.getCityName(), addrStreetName);
                if (street != null){
                    return street;
                }
            }

            // house has defined the name but can not find the street with the same name > check the similar name
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
                return maxSimilarStreet;
            }
        }

        // was not able to find street based on name > try the nereast one
        //Logger.i(TAG, "House does not have defined the addr:street, find the nearest: " + house.toString());
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

        if (addrStreetName.length() > 0 && Utils.toMeters(minDistance) > 100){
            Logger.i(TAG, "HOuse has name street but the nereast street is far away. Distance: " + Utils.toMeters(minDistance) +
                    "\n House: " + house.toString() + " \n Nereast street: " + nearestStreet.toString());
            removedHousesWithoutStreet++;
            return null;
        }

//        Logger.i(TAG, "### Nearest street: " +
//                "\n House: " + house.toString() + " \n Street: " + nearestStreet.toString());
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
     * Parse entity tags and find value of name tag
     * @param entity entity to obtain name
     * @return street name or null if tag name is not defined
     */
    private String getStreetName (Entity entity){
        String name = null;
        if (entity != null){
            name = OsmUtils.getTagValue(entity, OSMTagKey.NAME);
        }
        return name;
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

