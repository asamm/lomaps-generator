package com.asamm.osmTools.generatorDb.address;

import com.asamm.osmTools.generatorDb.WriterAddressDefinition;
import com.asamm.osmTools.generatorDb.data.OsmConst;
import com.asamm.osmTools.generatorDb.data.WayEx;
import com.asamm.osmTools.generatorDb.dataContainer.ADataContainer;
import com.asamm.osmTools.generatorDb.db.DatabaseAddress;
import com.asamm.osmTools.generatorDb.index.IndexController;
import com.asamm.osmTools.generatorDb.utils.*;
import com.asamm.osmTools.utils.Logger;
import com.vividsolutions.jts.geom.*;
import com.vividsolutions.jts.geom.prep.PreparedGeometry;
import com.vividsolutions.jts.geom.prep.PreparedGeometryFactory;
import com.vividsolutions.jts.index.strtree.STRtree;
import com.vividsolutions.jts.linearref.LengthIndexedLine;
import com.vividsolutions.jts.operation.distance.DistanceOp;
import com.vividsolutions.jts.operation.linemerge.LineMerger;
import com.vividsolutions.jts.operation.union.UnaryUnionOp;
import gnu.trove.iterator.TLongIterator;
import gnu.trove.list.TLongList;
import gnu.trove.map.hash.THashMap;
import gnu.trove.set.hash.THashSet;
import gnu.trove.set.hash.TLongHashSet;
import org.apache.commons.lang3.StringUtils;
import org.openstreetmap.osmosis.core.domain.v0_6.*;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by voldapet on 2015-11-05 .
 *
 * Set of methods that create house object. It consist in parsing raw OSM data, finding proper street for house
 *
 */
public class HouseController extends AaddressController {


    private static final String TAG = HouseController.class.getSimpleName();

    // number of houses where we was not able to find proper street
    public int removedHousesWithDefinedPlace = 0;
    public int removedHousesWithDefinedStreetName = 0;
    public int numOfStreetForHousesUsingSqlSelect = 0;


    public long timeProcessHouseWithoutStreet = 0;
    public long timeBufferHousesGeoms = 0;
    public long timeCreateUnamedStreetGeom = 0;
    public long timeGroupByCity = 0;
    public long timeCutWayStreetByHouses = 0;
    public long timeCutWaysConvexHull = 0;
    public long timeCutWaysIntersection = 0;

    public long timeFindNearestForGroup = 0;
    public long timeFindNearestForGroupedHouse = 0;

    /**
     * The list of osm ids of houses that were created from relation and should not be created again from nodes
     * */
    private TLongHashSet houseIdsFromRelations;


    public HouseController(ADataContainer dc, DatabaseAddress databaseAddress, WriterAddressDefinition wad) {

        super(dc, databaseAddress, wad);

        this.houseIdsFromRelations = new TLongHashSet();
    }

    /**
     * Test if geometry can be used as center of city or as boundary. It also check if geom is located in country area
     *
     * @param center center point of house check
     * @return true if geometry can be used for address object
     */
    private boolean isValidGeometry (Point center){
        return (center != null && wad.isInDatabaseArea(center));
    }

    /**
     * Iterate through @link{#streetRelations} and create houses from members
     */
    public void createHousesFromRelations (){

        TLongIterator iterator = dc.getStreetRelations().iterator();
        while (iterator.hasNext()) {

            Relation relation = dc.getRelationFromCache(iterator.next());
            if (relation == null) {
                continue;
            }
            // name from relation tags if not success than try to obtain it from way members or from houses
            String streetName = OsmUtils.getStreetName(relation);

            List<House> houses = new ArrayList<>();
            for (RelationMember rm : relation.getMembers()){

                String role = rm.getMemberRole();

                if (streetName == null && rm.getMemberType() == EntityType.Way && role.equals("street")) {
                    Way way = dc.getWayFromCache(rm.getMemberId());
                    if (way != null) {
                        // if name was not obtained from relation try it from the relation member ways
                        streetName = OsmUtils.getStreetName(way);
                    }
                }

                if (role.equals("house") || role.equals("address")){
                    Entity entityH = null;
                    if (rm.getMemberType() == EntityType.Way) {
                        entityH = dc.getWayFromCache(rm.getMemberId());
                    }
                    else if (rm.getMemberType() == EntityType.Node){
                        entityH = dc.getNodeFromCache(rm.getMemberId());
                    }
                    // create house from way or node
                    houses.addAll(createHouse(entityH));
                }
            }

            // try to create house from relation itself, eq: https://www.openstreetmap.org/relation/1857530
            houses.addAll(createHouse(relation));

            // test if new houses has defined the street name
            for (House house : houses) {

                if (house.getStreetName() == null || house.getStreetName().length() == 0){
                    // set name from entity
                    if (streetName != null && streetName.length() > 0) {
                        house.setStreetName(streetName);
                    }
                }

                Street street = findStreetForHouse(house);
                if (street != null) {
                    //Logger.i(TAG, "Insert house with id: " + house.getOsmId());
                    houseIdsFromRelations.add(house.getOsmId());
                    databaseAddress.insertHouse(street, house);
                }
            }

        }
    }

    public void createHousesFromWays () {

        TLongList wayIds = dc.getWayIds();
        for (int i=0, size = wayIds.size(); i < size; i++) {
            long wayId = wayIds.get(i);
            if (houseIdsFromRelations.contains(wayId)){
                //Logger.i(TAG, "createHousesFromWays(): skip way " + wayId+ " house already created from relations");
                continue;
            }

            WayEx way = dc.getWay(wayId);
            long start = System.currentTimeMillis();
            List<House> houses = createHouse(way);

            for(House house : houses) {

                start = System.currentTimeMillis();
                Street street = findStreetForHouse(house);
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

            if (houseIdsFromRelations.contains(nodeId)){
                //Logger.i(TAG, "createHousesFromNodes(): skip node " + nodeId+ " house already created from relations");
                continue;
            }

            long start = System.currentTimeMillis();
            List<House> houses = createHouse(node);

            for(House house : houses) {
                start = System.currentTimeMillis();
                Street street = findStreetForHouse(house);

                if (street == null){
                    continue;
                }

                // insert house into db
                databaseAddress.insertHouse(street, house);
            }
        }
    }


    /**
     * Crate house object from entity
     * @param entity way, node or relation that contain information about address
     * @return house or null if some tag is not valid or is not possible to obtain center point
     */
    private List<House> createHouse (Entity entity) {

        List<House> houses = new ArrayList<>();
        if (entity == null){
            return houses;
        }

        String addrInterpolationValue = OsmUtils.getTagValue(entity, OsmConst.OSMTagKey.ADDR_INTERPOLATION);
        if (addrInterpolationValue != null){
            //some type of interpolation is defined > parse it
            houses.addAll(parseInterpolation(entity, addrInterpolationValue));
            return houses;
        }
        // NOW CREATE HOUSE FROM ENTITY
        House house = parseHouseData (entity);
        if (house == null){
            // for some reason is not able to parse entity and create house obj > return empty array
            return houses;
        }

        // SOME RUSSIAN ADR HAS TWO STREETS IN ONE BUILDING CREATE TWO HOUSES
        // http://wiki.openstreetmap.org/wiki/Proposed_features/AddrN
        String streetName2 = OsmUtils.getTagValue(entity, OsmConst.OSMTagKey.ADDR_STREET2);
        if (streetName2 != null){
            houses.addAll(parseRussianStyle(house, streetName2));
        }
        else {
            houses.add(house);
        }

        return houses;
    }


    /**
     * Parse entity tags and create house object
     * @param entity to read house information
     * @return house or null if entity does not contain needed data
     */
    private House parseHouseData(Entity entity) {

        String houseNum = OsmUtils.getTagValue(entity, OsmConst.OSMTagKey.ADDR_HOUSE_NUMBER);
        String houseName = OsmUtils.getTagValue(entity, OsmConst.OSMTagKey.ADDR_HOUSE_NAME);
        String streetName = OsmUtils.getTagValue(entity, OsmConst.OSMTagKey.ADDR_STREET);

        String placeName = OsmUtils.getTagValue(entity, OsmConst.OSMTagKey.ADDR_PLACE);
        String cityName = OsmUtils.getTagValue(entity, OsmConst.OSMTagKey.ADDR_CITY);


        if (houseNum == null){
            houseNum = houseName;
        }
        if (houseNum == null){
            // can not obtain house number nor house name > skip it
//            Logger.w(TAG, "createHouse() Can not create house due to not number nor name is definer, OSM id: "
//                   + entity.getId());
            return null;
        }

        if (houseName == null || houseName.length() == 0){
            // for house where is not defined the addr:housename try to get simple name. Useful for public buildings
            houseName = OsmUtils.getTagValue(entity, OsmConst.OSMTagKey.NAME);
        }

        String postCode = parsePostCode(entity);
        int postCodeId = databaseAddress.getPostCodeId(postCode);
        Point center = getCenter(entity, dc);

        if ( !isValidGeometry(center)){
            //Logger.w(TAG, "createHouse() CAn not create center for house, OSM id: " + entity.getId());
            //can not create center for this house
            return null;
        }

        // prepare house from entity for next customization
        House house = new House(entity.getId(), houseNum, houseName, postCodeId, center);
        house.setStreetName(streetName);
        house.setCityName(cityName);
        house.setPlace(placeName);
        house.addIsIn(cityName);
        house.addIsIn(placeName);

        return house;
    }

    /**************************************************/
    /*               STREET FOR HOUSE
    /**************************************************/

    /**
     * Try to find appropriate street for House
     *
     * @param house for that we want to find street
     * @return the best way for house or null if no street was found
     */
    private Street findStreetForHouse (House house){

        String addrStreetName = house.getStreetName();
        String addrPlaceName = house.getPlace();
        String addrCity = house.getCityName();

        Street streetFound;
        streetFound = null;

        // FIND BY STREET NAME TAG
        if (addrStreetName.length() > 0){

            streetFound = findNamedStreet(addrStreetName, Const.MAX_DISTANCE_NAMED_STREET, house);
            if (streetFound == null){
                //databaseAddress.insertRemovedHouse(house);
                dc.addHouseWithoutStreet (house);
            }
        }
        // FIND BY PLACENAME
        else  if ( addrPlaceName.length() > 0 ){
            // street name is not defined but we have place name. This is common for villages > check if there is street with
            // same or similar name
            streetFound = findNamedStreet(addrPlaceName, Const.MAX_DISTANCE_PLACENAME_STREET, house);
            if (streetFound == null){
                dc.addHouseWithoutStreet(house);
            }
        }
        else {
            // GET THE NEAREST NAMED STREET FOR HOUSE WITHOUT STREET NAME OR PLACE NAME
            List<Street> streetsAround = IndexController.getInstance().getStreetsAround(house.getCenter(), 15);
            streetFound = getNearestStreetFromAround (house.getCenter(), streetsAround);

            if (streetFound == null || Utils.getDistanceNearest(streetFound.getGeometry(), house.getCenter()) > 75){
                // founded named street is too fare and the house does not have defined the placename nor streetname
                City city = findNearestCityIsIn(house);
                if (city != null){
                    //Logger.i(TAG, "findStreetForHouse(): Set placename " + city.getName() + " for house: " + house.toString());
                    house.setPlace(city.getName());
                    house.addIsIn(city.getName());
                }
                // process this house again during houses without street. It can be assigned to unnamed street around house
                dc.addHouseWithoutStreet(house);
                return null;
            }
        }

        return streetFound;
    }

    /**
     * Find the corresponding street for house based on the name. Only street with same or similar name
     * that are in defined distance is returned
     *
     * @param nameToCompare streetName or placeName
     * @param house house to find street for
     * @param maxDistance maximal possible distance between house and street.
     * @return best street for house or null if not possible to find the street
     *
     */
    private Street findNamedStreet(String nameToCompare, double maxDistance, House house) {

        // Test the street around by the streetname
        Street namedStreet = null;
        List<Street> streetsAround = IndexController.getInstance().getStreetsAround(house.getCenter(), 15);
        for (Street street : streetsAround) {
            if (street.getName().equalsIgnoreCase(nameToCompare)){
                // in some situation can streetsaround contains two street of the same name choose the best by distance
                if (namedStreet != null){
                    double distancePrevious = Utils.getDistanceNearest(namedStreet.getGeometry(), house.getCenter());
                    double distanceNew = Utils.getDistanceNearest(street.getGeometry(), house.getCenter());
                    // other street from streets around can be closer > if yes use it as named street for house
                    if (distanceNew < distancePrevious){
                        namedStreet = street;
                    }
                }
                else {
                    namedStreet = street;
                }
            }
        }

        // was not able to find the nereast street with the same name > try to select by name from addressdb
        if (namedStreet == null && house.getCityName().length() > 0 ){
            long start = System.currentTimeMillis();
            List<Street> streets = databaseAddress.selectStreetByNames(house.getCityName(), nameToCompare);
            for (Street street : streets){

                double distance = Utils.getDistanceNearest(street.getGeometry(), house.getCenter());
                if (distance < maxDistance){
                    numOfStreetForHousesUsingSqlSelect++;
                    namedStreet = street;
                }
            }
        }

        // house has defined the name but can not find the street with the same name > check the similarity name
        if (namedStreet == null){
            long startSim = System.currentTimeMillis();
            Street maxSimilarStreet = null;
            double maxSimilarityScore = 0;
            for (Street street : streetsAround) {
                if (maxSimilarStreet == null){
                    maxSimilarStreet =  street;
                }
                double similarity = StringUtils.getJaroWinklerDistance(nameToCompare, street.getName());
                if (similarity > maxSimilarityScore){
                    maxSimilarityScore = similarity;
                    maxSimilarStreet = street;
                }
            }

            if (maxSimilarityScore > 0.9){
//                Logger.i(TAG, "Sim: "+maxSimilarityScore+" For house "+house.getOsmId() +" with streetname: " + addrStreetName +
//                " was, found street:  " + maxSimilarStreet.toString());
                namedStreet = maxSimilarStreet;
            }
        }

        if (namedStreet != null){

            double distance = Utils.getDistanceNearest(namedStreet.getGeometry(), house.getCenter());
            if (distance > maxDistance){
                return null;
            }
        }
        return namedStreet;
    }


//    /**
//     * For house find the city where house is inside. In case that there is more then one city then return the
//     * smallest type (village or suburbs)
//     * @param house house to find city
//     * @return city where house is in bound
//     */
//    private City findSmallestCityIsIn(House house){
//
//        City cityForHouse = null;
//
//        List<City> cities = IndexController.getInstance().getClosestCities(house.getCenter(), 30);
//        PreparedGeometry pgCenter = PreparedGeometryFactory.prepare(house.getCenter());
//
//        for (City city : cities){
//            MultiPolygon cityArea = city.getGeom();
//            if (cityArea == null){
//                continue;
//            }
//
//            if (pgCenter.intersects(cityArea)){
//                // house is inside this city > compare the type of city
//                if (cityForHouse == null){
//                    cityForHouse = city;
//                }
//                else if (city.getType().getTypeCode() > cityForHouse.getType().getTypeCode()){
//                    // higher type code represent smaller city
//                    cityForHouse = city;
//                }
//            }
//        }
//        return cityForHouse;
//    }

    /**
     * For house find the cities where house is in then return the nearest one
     * @param house house to find city
     * @return city where house is in or null if there is no city around
     */
    private City findNearestCityIsIn(House house){
        List<City> cities = StreetController.findCitiesForPlace(house.getCenter(),house.getIsIn() );
        return StreetController.getNearestCity(house.getCenter(), cities);
    }



    /**************************************************/
    /*       METHODS THAT HANDLE HOUSES WITHOUT STREET
    /**************************************************/

    /**
     * When no street was found for house then check the  streets without name
     */
    public void processHouseWithoutStreet (final StreetController sc){

        long start = System.currentTimeMillis();

        BiDiHashMap<City, Boundary>  centerCityBoundaryMap = dc.getCenterCityBoundaryMap();

        THashMap<String, List<House>> housesWithoutStreet = dc.getHousesWithoutStreet();
        for (Map.Entry<String, List<House>> entry  : housesWithoutStreet.entrySet()){

            String streetPlaceName = entry.getKey();
            if (streetPlaceName.startsWith("FIXME")){
                continue; // some places has defined name as 'FIX ME' grrrr
            }
            //Logger.i(TAG, " processHouseWithoutStreet() - process place or street name: " + streetPlaceName );

            // this houses has the same streetName or the same placeName
            List<House> houses = entry.getValue();
            int sizeHouses = houses.size();
            if (sizeHouses < 2 ){
                // at least two houses of the same name are required
                if (sizeHouses == 1){
                    databaseAddress.insertRemovedHouse(houses.get(0), "Only one house with placename: " + streetPlaceName);
                }
                continue;
            }
            // GROUP HOUSES BY BOUNDARY
            THashMap<City, List<House>> groupedHousesMap = groupHousesByCitiesNew(houses);

            for (Map.Entry<City, List<House>> entryG : groupedHousesMap.entrySet()){
                City city = entryG.getKey();

                List<House> housesGrouped = entryG.getValue();

                int sizeGroupedHouses = housesGrouped.size();
                if (sizeGroupedHouses < 2){
                    // at least two houses with the same placename in every boundary are required
                    if (sizeGroupedHouses == 1){
                        databaseAddress.insertRemovedHouse(houses.get(0), "Only one house in city: " + city.getName());
                    }
                    continue;
                }

                // make buffer around every house
                long start2 = System.currentTimeMillis();
                MultiPolygon mpBuffer = bufferHouses(housesGrouped, 100);
                timeBufferHousesGeoms += System.currentTimeMillis() - start2;

                // for every house in boundary grouped houses find the nearest unnamed way street
                List<Street> unNamedWayStreets = findNearestWayStreetsForGroupedHouses(mpBuffer, Const.MAX_DISTANCE_UNNAMED_STREET);

                if (unNamedWayStreets.size() == 0){
//                    Logger.i(TAG, " processHouseWithoutStreet(): Can not find any unnamed street for grouped houses: " +
//                                    streetPlaceName + ", and boundary: " + city.toString());
                    for (House houseToRemove : housesGrouped){
                        databaseAddress.insertRemovedHouse(houseToRemove, "" +
                                "Not found street for grouped houses with placeName: " +
                                streetPlaceName + ", and city: " + city.getName());
                    }
                    continue;
                }

                // create one street for group of houses. You can not create street right now because
                // there can be houses with same place from parent / child boundary
                Street wayStreet = new Street();
                wayStreet.setHouses(new THashSet<House>(housesGrouped));
                wayStreet.setName(streetPlaceName);
                // cut the geometry only around the houses

                long start3 = System.currentTimeMillis();
                MultiLineString mlsCutted = createGeomUnnamedWayStreets(city, mpBuffer, unNamedWayStreets);

                if (mlsCutted == null || mlsCutted.isEmpty()){
                    Logger.w(TAG, "processHouseWithoutStreet(): Not able create geometry for street of grouped houses: " +
                            "\n " + GeomUtils.geomToGeoJson(mpBuffer));
                    for (House houseToRemove : housesGrouped){
                        databaseAddress.insertRemovedHouse(houseToRemove, "" +
                                "Not able to create cut multilinestring : " +
                                streetPlaceName + ", and city: " + city.getName());
                    }
                    continue;
                }
                wayStreet.setGeometry(mlsCutted);
                timeCreateUnamedStreetGeom += System.currentTimeMillis() - start3;

                // find cities for way
                List<City> cities = sc.findCitiesForPlace(wayStreet.getGeometry(), wayStreet.getIsIn());
                wayStreet.addCities(cities);
                //wayStreet.addCityId(city.getOsmId());


                dc.addWayStreetHashName(wayStreet);
            }
        }

        timeProcessHouseWithoutStreet += System.currentTimeMillis() - start;

        // now join created wayStreet for houses
        sc.joinWayStreets(new StreetController.OnJoinStreetListener() {
            @Override
            public void onJoin(Street joinedStreet) {

// for belorusian was needed to split street maybe is not needed
//                // split streets from different places (due to belorusia)
//                List<Street> streetsToInsert = sc.splitToCityParts(joinedStreet);
//
//                // write to DB
//                for (Street street : streetsToInsert){
//
//                    THashSet<House> houses = street.getHouses();
//                    // insert street into DB
//                    databaseAddress.insertStreet(street);
//                    // insert houses into DB
//                    for (House house : houses) {
//                        databaseAddress.insertHouse(street, house);
//                    }
//                }

                    THashSet<House> houses = joinedStreet.getHouses();
                    // insert street into DB
                    databaseAddress.insertStreet(joinedStreet);
                    // insert houses into DB
                    for (House house : houses) {
                        databaseAddress.insertHouse(joinedStreet, house);
                    }

            }
        });
    }

    /**
     * Takes all houses that has defined same name street or the same place name.
     * Split list on part of houses that belong to the one city
     *
     * @param housesToGroup list of houses that has defined the same street name or place name
     * @return list of houses grouped by boundary area
     */
    private THashMap<City, List<House>> groupHousesByCities(List<House> housesToGroup) {

        long start = System.currentTimeMillis();

        THashMap<City, List<House>> groupedHousesMap = new THashMap<>();

        // prepare temporary index for houses of the same name
        STRtree index = new STRtree();
        //Logger.i(TAG, "groupHousesByCities() Index num of houses: " + housesToGroup.size());
        for (House house : housesToGroup){

            index.insert(house.getCenter().getEnvelopeInternal(), house);
        }

        BiDiHashMap<City, Boundary> centerCityBoundaryMap = dc.getCenterCityBoundaryMap();
        Collection<City> cities = dc.getCities();

        for (City city : cities){
            Boundary boundary = centerCityBoundaryMap.getValue(city);

            if (boundary == null){
                continue;
            }

            PreparedGeometry pgBoundary = PreparedGeometryFactory.prepare(boundary.getGeom());
            List<House> housesFromIndex = index.query(boundary.getGeom().getEnvelopeInternal());
            for (int i = housesFromIndex.size() - 1; i >= 0; i-- ){
                // remove houses that are in envelope but not are inside real boundary geom
                House houseToCheck = housesFromIndex.get(i);
                if ( !pgBoundary.intersects(houseToCheck.getCenter())){
                    housesFromIndex.remove(i);
                }
            }
            if (housesFromIndex.size() > 0){
                groupedHousesMap.put(city, housesFromIndex);
            }
        }
        timeGroupByCity += System.currentTimeMillis() - start;
        return groupedHousesMap;
    }

    /**
     * Takes all houses that has defined same name street or the same place name.
     * Split list on part of houses that belong to the one city
     *
     * @param housesToGroup list of houses that has defined the same street name or place name
     * @return list of houses grouped by boundary area
     */
    private THashMap<City, List<House>> groupHousesByCitiesNew(List<House> housesToGroup) {

        long start = System.currentTimeMillis();

        THashMap<City, List<House>> groupedHousesMap = new THashMap<>();

        for (House house : housesToGroup){

            // for every house find cities is in
            List<City> cities = StreetController.findCitiesForPlace(house.getCenter(), house.getIsIn());
            if (cities.size() == 0){
                databaseAddress.insertRemovedHouse(house, "Can not find any city around fo house");
                continue;
            }
            for (City city : cities){

                List<House> housesInCity = groupedHousesMap.get(city);
                if (housesInCity == null){
                    housesInCity = new ArrayList<>();
                    groupedHousesMap.put(city, housesInCity);
                }
                housesInCity.add(house);
            }
        }

        timeGroupByCity += System.currentTimeMillis() - start;
        return groupedHousesMap;
    }


    /**
     * For every house find the nearest waystreet. It combine unnamed waystreet and also named street
     *
     * @param mpBuffer multipolygon from polygon around every house
     * @return list of waystreets that are nearest for every polygon in multipoly
     */
    private List<Street> findNearestWayStreetsForGroupedHouses (MultiPolygon mpBuffer, double maxDistance){

        long start = System.currentTimeMillis();

        THashSet nearestStreets = new THashSet();
        List<Street> namedWayStreets = IndexController.getInstance().getNamedWayStreets(dc, mpBuffer);
        List<Street> unnamedWayStreets = IndexController.getInstance().getUnnamedWayStreets(dc, mpBuffer);

        // it can happen that more then one street is selected. It's needed to find only the closest one for every house
        // crate index from ways that are around grouped houses
        STRtree index = new STRtree();
        for (Street street : unnamedWayStreets){
            index.insert(street.getGeometry().getEnvelopeInternal(), street);
        }
        for (Street street : namedWayStreets){
            index.insert(street.getGeometry().getEnvelopeInternal(), street);
        }

        // for every house (every buffer around house) find the closest way
        int size = mpBuffer.getNumGeometries();
        for (int i=0; i < size; i++){
            Geometry geom = mpBuffer.getGeometryN(i);
            List<Street> watStreetsToTest = index.query(geom.getEnvelopeInternal());

            // now find the nearest street from index
            long start2 = System.currentTimeMillis();
            Street nearestWayStreet = getNearestStreetFromAround(geom.getCentroid(), watStreetsToTest);
            timeFindNearestForGroupedHouse += System.currentTimeMillis() - start2;

            if (nearestWayStreet == null || Utils.getDistanceNearest(nearestWayStreet.getGeometry(), geom) > maxDistance ){
                continue;
            }
            nearestStreets.add(nearestWayStreet);
        }

        timeFindNearestForGroup += System.currentTimeMillis() - start;
        return new ArrayList<Street>(nearestStreets);
    }

    /**
     * Prepare geometry for waystreets that are around grouped hauses. Join way's geoms into one multiline
     * and this multiline is cut by the multipolygon of buffered houses
     * @param city city for whih are houses grouped
     * @param bufferedHouses multipolygon define geoms that cut the multiline of ways
     * @param unNamedWayStreets wayStreets from this ways will be create the final geom
     * @return joined geometry that is cut by houses buffer
     */
    public MultiLineString createGeomUnnamedWayStreets (
            City city, MultiPolygon bufferedHouses, List<Street> unNamedWayStreets){

        // join the ways into one line
        LineMerger lineMerger = new LineMerger();
        for (Street wayStreet : unNamedWayStreets){
            lineMerger.add(wayStreet.getGeometry());
        }
        List<LineString> lineStrings =  new ArrayList<LineString>(lineMerger.getMergedLineStrings());
        MultiLineString mls = GeomUtils.mergeLinesToMultiLine(lineStrings);

        // prepare list of polygon that are around every house
        List<Geometry> geomsToJoins = new ArrayList<>();
        int size = bufferedHouses.getNumGeometries();
        for (int i=0; i < size; i++) {
            geomsToJoins.add(bufferedHouses.getGeometryN(i));
        }
        // join polygon into one geometry
        UnaryUnionOp unaryUnionOp = new UnaryUnionOp(geomsToJoins);
        Geometry joinedBuffers = unaryUnionOp.union();

        // cut the line by the joined geometry > the result is multiline around the houses
        mls = cutMlsByBoundary(mls, joinedBuffers);
        // cut it again to cut part of streets that are outside of city boundary
        if (city.getGeom() != null && city.getGeom().isValid()) {
            mls = cutMlsByBoundary(mls, city.getGeom());
        }

        return mls;
    }

    /**
     * Create intersection of street geometry with houses that related into this street
     *
     * @param mls original street geom create from raw OSM ways
     * @param cutGeom border area to cut the linestring
     * @return cut linestring that are only inside the boundary geom
     */
    public MultiLineString cutMlsByBoundary(MultiLineString mls, Geometry cutGeom){
        // intersect the street geom with area of houses
        long start = System.currentTimeMillis();
        if (mls.intersects(cutGeom)){
            long start3 = System.currentTimeMillis();
            Geometry cuttedStreet = mls.intersection(cutGeom);
            mls = GeomUtils.geometryToMultilineString(cuttedStreet);
            timeCutWaysIntersection += System.currentTimeMillis() - start3;
//            Logger.i(TAG, "Multipoint: " + Utils.geomToGeoJson(mp));
//            Logger.i(TAG, "Cutted geometry: " + Utils.geomToGeoJson(mls));
        }
        timeCutWayStreetByHouses += System.currentTimeMillis() - start;
        return mls;
    }

    /**
     * Create boundary polygon around set of houses
     *
     * @param groupedHouses list of houses in one boundary
     * @return geometry that is around the houses. It is convex hull not the concave hull that is better
     */
    private Geometry createConvexHullOfHouses (THashSet<House> groupedHouses){
        long start = System.currentTimeMillis();

        // create area around all houses
        MultiPoint mp = GeomUtils.housesToMultiPoint(groupedHouses);
        double bufferDeg = Utils.distanceToDeg(mp.getCoordinate(),50);
        Geometry boundary = mp.convexHull().buffer(bufferDeg);

        timeCutWaysConvexHull += System.currentTimeMillis() - start;
        return boundary;
    }

    /**
     * Create polygon around every house of specified size.
     * We use it for selecting nearest streets for group of houses
     *
     * @param housesToBuffer size of buffer in meters
     * @return buffered polygons around every house
     */
    private MultiPolygon  bufferHouses (List<House> housesToBuffer, double buffer){

        int size = housesToBuffer.size();
        Polygon[] polygons = new Polygon[size];
        for (int i=0; i < size ; i++){
            polygons[i] = GeomUtils.createRectangle(housesToBuffer.get(i).getCenter().getCoordinate(), buffer);
        }
        return geometryFactory.createMultiPolygon(polygons);
    }


    /**************************************************/
    /*                  INTERPOLATION UTILS
    /**************************************************/


    /**
     * Create houses if interpolation is defined.
     * @param entity way or node that has define the interpolation
     * @param addrInterpolationValue value of tag with key {@link OsmConst.OSMTagKey#ADDR_INTERPOLATION}
     * @return list of created houses or empty list if is not possible to create any house from interpolation
     */
    private List<House> parseInterpolation(Entity entity, String addrInterpolationValue) {

        List<House> houses = new ArrayList<>();
        EntityType entityType = entity.getType();

        if (addrInterpolationValue.length() <= 0 ){
            return houses;
        }

        AddrInterpolationType ait = AddrInterpolationType.fromValue(addrInterpolationValue);
        if (entityType == EntityType.Way){
            Way way = (Way) entity;

            if (way.isClosed()){
                // probably building don't interpolate
                House house = parseHouseData(way);
                //Logger.i(TAG, "parseInterpolation(): way is closed, building? wayId: " + way.getId());
                if (house !=  null){
                    houses.add(house);
                }

                return houses;
            }

            // Prepare for interpolation
            WayEx wayEx = dc.getWay(way);
            List<Node> nodes = wayEx.getNodes();
            if (nodes == null){
                return  houses;
            }
            // apriori is street name defined in house nodes but in some cases is defined in interpolation street
            String streetNameFallback = OsmUtils.getStreetName(way);

            // PARSE EVERY SEGMENT  (segment is part on between nodes that has defined the house number)

            House startHouse = null;
            House endHouse = null;
            List<Coordinate> segmentCoordinates = new ArrayList<>();

            for (int i=0, nodesSize = nodes.size(); i < nodesSize; i++ ){
                Node node = nodes.get(i);
                boolean isLastNode = (i == nodesSize - 1);
                segmentCoordinates.add(new Coordinate(node.getLongitude(), node.getLatitude()));

                //Logger.i(TAG, "parseInterpolation(): Parse nodes for : " + way.getId() + ", node: " + node.getId());
                House house = parseHouseData(node);
                if (house == null){
                    continue; // probably simple way node without house number
                }

                if (house.getStreetName() == null || house.getStreetName().length() == 0){
                    house.setStreetName(streetNameFallback);
                }

                if (canBeUsedForInterpolation(house.getNumber(), ait)){
                    // house number is letter or end with letter or is number

                    if (startHouse == null){
                        startHouse = house;
                    }
                    else if (endHouse == null) {
                        endHouse = house;
                        List<House> housesInterpolated =
                                interpolateHousesOnLineSegment(startHouse, endHouse, segmentCoordinates, ait, addrInterpolationValue, isLastNode);

                        houses.addAll(housesInterpolated);

                        // prepare for next segment
                        if (!isLastNode) {
                            startHouse = endHouse;
                            endHouse = null;
                            segmentCoordinates = new ArrayList<>();
                            segmentCoordinates.add(new Coordinate(node.getLongitude(), node.getLatitude()));
                        }
                    }
                }
                else {
                    // it's valid house but it's number can no be used for interpolation
                    houses.add(house);
                }
            }

            if (startHouse != null && endHouse == null ){
                // the end house is not defined at all > save at least the first one
                houses.add(startHouse);
            }
        }
        return houses;
    }

    /**
     * Create non existing house nodes along the interpolation line. Segment is part of OSM way
     * between two houses. Interpolation line can contains more then two houses. For example
     * when interpolation is not linear or there are some gaps. This is reason why is interpolation
     * prepared for segments not for whole ways. However in most cases is segment the same as way
     *
     * @param startHouse house from which starts the interpolation segment
     * @param endHouse house where interpolation segment ends
     * @param segmentCoordinates coordinates of line segment along are houses interpolated
     * @param ait type of interpolation
     * @param interpolationValue tag value of interpolation osm key
     * @param isLastNode set true if it's last segment of whole way
     * @return list of created house. List always contains at least start house
     * if isLastNode = true also the end house is added into returned list of interpolated houses.
     */
    private List<House> interpolateHousesOnLineSegment(
            House startHouse,
            House endHouse,
            List<Coordinate> segmentCoordinates,
            AddrInterpolationType ait,
            String interpolationValue,
            boolean isLastNode) {

        List<House> houses = new ArrayList<>();
        houses.add(startHouse);

        String startHouseNum = startHouse.getNumber();

        int startValue = 0;
        int endValue = 0;
        int step = ait.getStep();

        if (AddrInterpolationType.ALPHABETIC == ait){
            startValue = getLastChar(startHouseNum);
            endValue = getLastChar(endHouse.getNumber());
        }
        else {

            try {
                startValue = Integer.parseInt(startHouseNum);
                endValue = Integer.parseInt(endHouse.getNumber());
            }
            catch (NumberFormatException e) {
                Logger.w(TAG, "interpolateHousesOnLineSegment() - Can not parse start or end value for houses " +
                        "\n start house: " + startHouse.toString() +
                        "\n end house: " + endHouse.toString());
                return houses;
            }
        }

        if (AddrInterpolationType.INTEGER == ait){
            // obtain step for this specific type interpolation
            step = Integer.valueOf(interpolationValue);
        }

        int diff = endValue - startValue;

        if (diff == 0){
            // there is no difference between house number > nothing to do
            if (isLastNode){
                houses.add(endHouse);
            }
            return houses;
        }

        if (diff < 0){
            // negative interpolation > reverse step to be negative
            step = step * (-1);
        }

        // PREPARE INDEXED LINE FOR INTERPOLATION ALONG THE LINE

        Coordinate[] lineCoords = segmentCoordinates.toArray(new Coordinate[0]);
        LineString ls = geometryFactory.createLineString(lineCoords);
        LengthIndexedLine indexedLine = new LengthIndexedLine(ls);
        double endIndex = indexedLine.getEndIndex();
        double startIndex = indexedLine.getStartIndex();
        double stepIndex = ((endIndex - startIndex) / diff) * step;

        for (int i=1; i < diff/step; i++) {

            // interpolate the coordinates of new house
            Coordinate centerCoord = indexedLine.extractPoint(startIndex + i * stepIndex);
            Point centerPoint = geometryFactory.createPoint(centerCoord);

            // create new house number based on type of interpolation
            int houseNum = startValue + i * step;
            String houseNumStr = "";
            if ( ait == AddrInterpolationType.ALPHABETIC){
                // convert integer to ascii char and replace the last char in start house number
                houseNumStr = startHouseNum.substring(0, startHouseNum.length()-1) + (char)houseNum;
            }
            else {
                houseNumStr = String.valueOf(houseNum);
            }

            House house = new House(
                    0,
                    houseNumStr,
                    "",
                    startHouse.getPostCodeId(),
                    centerPoint);
            house.setStreetName(startHouse.getStreetName());
            house.setCityName(startHouse.getCityName());

            houses.add(house);
        }

        if (isLastNode){
            houses.add(endHouse);
        }
        return houses;
    }

    /**
     * Test if house with house number can be used as start or end interval
     * based on type of interpolation type
     * @param houseNumber value of houseNumber tag
     * @param ait type of interpolation
     * @return true house number can be interpolated
     */
    public boolean canBeUsedForInterpolation (String houseNumber, AddrInterpolationType ait){
        if (ait == AddrInterpolationType.NONE){
            return false;
        }
        else if ( ait == AddrInterpolationType.ALPHABETIC){
            Pattern pAlphabet = Pattern.compile("[a-zA-Z]$");
            Matcher matcher = pAlphabet.matcher(houseNumber);
            if ( houseNumber.length() == 1 && matcher.find() ){
                return true;
            }
            else if ( houseNumber.length() > 1 && matcher.find() ){
                // string ends with letter what about the previous char
                matcher = pAlphabet.matcher(houseNumber.substring(0, houseNumber.length()-2));
                if (matcher.find()){
                    return false;
                }
                return true;
            }
            else {
                return false;
            }
        }
        else {
            // all other types of interpolation has to be numeric
            if (Utils.isNumeric(houseNumber)){
                return true;
            }
        }

        return false;
    }



    /**************************************************/
    /*                  RUSSIAN STANDARD
    /**************************************************/


    /**
     * Russian has specific style for tagging of corner houses, when house can belong into two streets.
     * Method try to create new second house for second streetName
     * @param house1 standard house
     * @param streetName2 name of second street
     * @return List of houses that contains at least house1 or two houses if separation into different houses
     * were successful
     * */
    private List<House> parseRussianStyle(House house1, String streetName2){

        String houseNum1 = house1.getNumber();
        List<House> houses = new ArrayList<>();

        if (streetName2 != null && streetName2.length() > 0) {
            int slashPos = houseNum1.indexOf('/');

            if (slashPos > 0 && slashPos < houseNum1.length() - 1) {

                // there is another housenum > remove second number from the house1
                house1.setNumber(houseNum1.substring(0, slashPos));

                // create second house;
                String houseNum2 = houseNum1.substring(slashPos + 1);
                House house2 = new House(
                        house1.getOsmId(),
                        houseNum2,house1.getName(),
                        house1.getPostCodeId(),
                        house1.getCenter());
                house2.setStreetName(streetName2);

                houses.add(house1);
                houses.add(house2);
            }
        }
        else {
            // it was not possible to create second house add at least the first one
            houses.add(house1);
        }
        return houses;
    }


    /**************************************************/
    /*                  OTHER UTILS
    /**************************************************/


    /**
     * Prepare center point for house
     * @param entity way or node that represent the address
     * @param dc datacontainer cache for nodes and ways
     * @return point of identifies location for house or null if it is not possible to parse location
     */
    private Point getCenter(Entity entity, ADataContainer dc) {

        if (entity.getType() == EntityType.Node){

            Node node = (Node) entity;
            Point point = geometryFactory.createPoint(
                    new Coordinate(node.getLongitude(), node.getLatitude()));

            return point;
        }
        else if (entity.getType() == EntityType.Way){

            Way way = (Way) entity;
            WayEx wayEx = dc.getWay( way);
            Coordinate[] coordinates = wayEx.getCoordinates();
            if (coordinates.length == 0){
                //can not create house
//                if (entity.getId() == 115665727) {
//                    Logger.i(TAG, "Can not create center - no coordinates");
//                }
                return null;
            }
            else if (coordinates.length == 1){
                // we know only one node of way > use it as center
//                if (entity.getId() == 115665727){
//                    Logger.i(TAG, "Create center from one coordinate");
//                    Logger.i(TAG, "Center: " + Utils.geomToGeoJson(geometryFactory.createPoint(coordinates[0])) );
//                }
                return geometryFactory.createPoint(coordinates[0]);
            }

            LineString ls = geometryFactory.createLineString(coordinates);

            Point center = ls.getCentroid();
            if (!center.isValid()){
                center = geometryFactory.createPoint(ls.getEnvelopeInternal().centre());
            }
            return center;
        }
        else if (entity.getType() == EntityType.Relation){
            Relation relation = (Relation) entity;
            List<RelationMember> members = relation.getMembers();
            for (RelationMember rm : members){

                Entity entityMember = null;
                if (rm.getMemberType() == EntityType.Way) {
                    entityMember = dc.getWayFromCache(rm.getMemberId());
                    if (entityMember == null){
                        Logger.w(TAG, "getCenter() Can not load relation member from cache, WAY id: " + rm.getMemberId());
                    }

                }
                else if (rm.getMemberType() == EntityType.Node){
                    entityMember = dc.getNodeFromCache(rm.getMemberId());
                    if (entityMember == null){
                        Logger.w(TAG, "getCenter() Can not load relation member from cache, NODE id: " + rm.getMemberId());
                    }
                }

                if (entityMember == null){
                    continue;
                }
                return getCenter(entityMember, dc);
            }
        }

        Logger.w(TAG, "getCenter() Can not create center point for house, OSM id: " + entity.getId());
        return null;
    }


    /**
     * Obtain postal code for house
     * @param entity node or way that represent the house
     * @return tag value ADDR_POSTCODE or POSTAL_CODE or null if postal code is not defined
     */
    private String parsePostCode(Entity entity) {

        String postCode = OsmUtils.getTagValue(entity, OsmConst.OSMTagKey.ADDR_POSTCODE);
        if (postCode == null){
            postCode = OsmUtils.getTagValue(entity, OsmConst.OSMTagKey.POSTAL_CODE);
        }
        return postCode;
    }

    /**
     * Get last character in string and convert it to char
     * @param houseNumber string to parse
     * @return
     */
    private char getLastChar (String houseNumber) {
        String lastChar = houseNumber.substring(houseNumber.length()-1);
        return lastChar.charAt(0);
    }



    /**
     * Select the nearest street from specified point
     *
     * @param center center point to find the nearest street
     * @param streetsAround list of streets that are around the house
     * @return nearest street or null if list is empty
     */
    private Street getNearestStreetFromAround(Point center, List<Street> streetsAround) {
        Street nearestStreet = null;
        double minDistance = Float.MAX_VALUE;
        for (Street street : streetsAround) {
            if (nearestStreet == null){
                nearestStreet =  street;
            }

            double distance = DistanceOp.distance(center, street.getGeometry());
            if (distance < minDistance){
                minDistance = distance;
                nearestStreet = street;
            }
        }
        return nearestStreet;
    }
}
