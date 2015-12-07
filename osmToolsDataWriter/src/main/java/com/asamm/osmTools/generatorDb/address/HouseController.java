package com.asamm.osmTools.generatorDb.address;

import com.asamm.osmTools.generatorDb.GeneratorAddress;
import com.asamm.osmTools.generatorDb.data.OsmConst;
import com.asamm.osmTools.generatorDb.data.WayEx;
import com.asamm.osmTools.generatorDb.dataContainer.ADataContainer;
import com.asamm.osmTools.generatorDb.db.DatabaseAddress;
import com.asamm.osmTools.generatorDb.utils.OsmUtils;
import com.asamm.osmTools.generatorDb.utils.Utils;
import com.asamm.osmTools.utils.Logger;
import com.vividsolutions.jts.geom.*;
import com.vividsolutions.jts.linearref.LengthIndexedLine;
import com.vividsolutions.jts.operation.distance.DistanceOp;
import gnu.trove.list.TLongList;
import org.apache.commons.lang3.StringUtils;
import org.openstreetmap.osmosis.core.domain.v0_6.*;

import javax.rmi.CORBA.Util;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by voldapet on 2015-11-05 .
 *
 * Set of methods that create house object. It consist in parsing raw OSM data, finding proper street for house
 *
 */
public class HouseController {


    private static final String TAG = HouseController.class.getSimpleName();

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



    ADataContainer dc;

    GeneratorAddress ga;

    DatabaseAddress databaseAddress;

    GeometryFactory geometryFactory;


    /**
     * Cached last street because often goes houses from the same street in sequence
     * */
    Street lastHouseStreet = new Street();

    public HouseController(ADataContainer dc, GeneratorAddress ga) {
        this.dc = dc;
        this.ga = ga;
        this.databaseAddress = ga.getDatabaseAddress();

        this.geometryFactory = new GeometryFactory();
    }

    /**
     * Iterate thourh @link{#streetRelations} and create houses from members
     * @param streetRelations
     */
    public void createHousesFromRelations (TLongList streetRelations){

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
                        Logger.i(TAG, "createHousesFromRelations() - Can not get address relation member from cache. " +
                                "Relation: " + relationId + ", relation member: " + rm.getMemberId());
                        continue;
                    }

                    long start = System.currentTimeMillis();
                    houses.addAll(createHouse(entityH));
                    timeCreateParseHouses += System.currentTimeMillis() - start;
                }
            }

            // try to create house from relation itself, eq: https://www.openstreetmap.org/relation/1857530

            long start = System.currentTimeMillis();
            houses.addAll(createHouse(relation));
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
                if (street != null){
                    databaseAddress.insertHouse(street, house);
                }
            }
        }
    }

    public void createHousesFromWays () {

        TLongList wayIds = dc.getWayIds();
        for (int i=0, size = wayIds.size(); i < size; i++) {
            long wayId = wayIds.get(i);
            WayEx way = dc.getWay(wayId);
            long start = System.currentTimeMillis();
            List<House> houses = createHouse(way);
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
            List<House> houses = createHouse(node);
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
     * Crate house object from entity
     * @param entity way, node or relation that contain information about address
     * @return house or null if some tag is not valid or is not possible to obtain center point
     */
    private List<House> createHouse (Entity entity) {

        List<House> houses = new ArrayList<>();


        String addrInterpolationValue = OsmUtils.getTagValue(entity, OsmConst.OSMTagKey.ADDR_INTERPOLATION);
        if (addrInterpolationValue != null){
            //some type of interpolation is defined > parse it
            houses.addAll(parseInterpolation(entity, addrInterpolationValue));
            return houses;
        }

        House house = parseHouseData (entity);
        if (house == null){
            // for some reason is not able to parse entity and create house obj > return empty array
            return houses;
        }

        // SOME RUSSIAN ADR HAS TWO STREETS IN ONE BUILDING CREATE TWO HOUSES
        // http://wiki.openstreetmap.org/wiki/Proposed_features/AddrN
        String streetName2 = OsmUtils.getTagValue(entity, OsmConst.OSMTagKey.ADDR_STREET2);
        if (streetName2 != null){
            houses.addAll(parseCreateRussianStyle(house, streetName2));
        }
        else {
            houses.add(house);
        }

        return houses;
    }

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

        if (center == null){
            //Logger.w(TAG, "createHouse() CAn not create center for house, OSM id: " + entity.getId());
            //can not create center for this house
            return null;
        }

        // prepare house from entity for next customization
        House house = new House(entity.getId(), houseNum, houseName, postCode, center);
        house.setStreetName(streetName);
        house.setCityName(cityName);
        house.setPlace(placeName);
        house.setPostCodeId(postCodeId);

        return house;
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

        Logger.i(TAG, "*Looking for best street for house: " + house.toString());

        String addrStreetName = house.getStreetName();
        String addrPlace = house.getPlace();
        String addrCity = house.getCityName();

        Street nearestStreet = null;

        if (addrStreetName.length() > 0){

            nearestStreet = findStreetByAddrStreetName(house);
        }
        else  if ( addrPlace.length() > 0 ){
            // street name is not defined but we have place name. This is common for villages
            nearestStreet = findStreetByAddrPlaceName (house);
        }
        else {
            // House have not defined the streetName nor the place name > find the closest street
            Logger.i(TAG, "Looking for nearest street for house: ");
            long start = System.currentTimeMillis();
            List<Street> streetsAround = databaseAddress.getStreetsAround(house.getCenter(), 7);
            streetsAround.addAll(databaseAddress.getDummyStreetsAround(house.getCenter(), 7));

            double minDistance = Float.MAX_VALUE;
            Point houseCenter = house.getCenter();
            for (Street street : streetsAround) {
                if (nearestStreet == null){
                    nearestStreet =  street;
                }

                double distance = DistanceOp.distance(houseCenter, street.getGeometry());
                if (distance < minDistance){
                    minDistance = distance;
                    nearestStreet = street;
                }
            }
            timeFindStreetNearest += System.currentTimeMillis() - start;
        }

        if (nearestStreet == null && addrStreetName.length() > 0 ){

//            Polygon circle = Utils.createCircle(house.getCenter().getCoordinate(), 100, 10);
//            if ( !circle.intersects(nearestStreet.getGeometry())) {
//                databaseAddress.insertRemovedHouse(house);
//                removedHousesWithDefinedStreetName++;
//                return null;
//            }

            databaseAddress.insertRemovedHouse(house);
            removedHousesWithDefinedStreetName++;

            // house has defined the street name but can not FIND such street

        }

//        Logger.i(TAG, "### Nearest street: " +
//                "\n House: " + house.toString() + " \n Street: " + nearestStreet.toString());
        // this street has not defined the
        if (nearestStreet != null) {
            lastHouseStreet = nearestStreet;
        }
        return nearestStreet;
    }

    private Street findStreetByAddrPlaceName(House house) {

        String addrPlaceName = house.getPlace();
        Logger.i(TAG, "Looking for best street for house by addrPlaceName: " + addrPlaceName);

//        // test the last founded street as first
//        if (lastHouseStreet != null && lastHouseStreet.getName().equalsIgnoreCase(addrPlaceName)){
//           return lastHouseStreet;
//        }

        // select streets around and compare their name with house placeName
        List<Street> streetsAround = databaseAddress.getStreetsAround(house.getCenter(), 20);
        for (Street street : streetsAround) {
            //Logger.i(TAG, "Street to check" + street.toString());
            if (street.getName().equalsIgnoreCase(addrPlaceName)){
                return street;
            }
        }

        List<Street> dummyStreetsAround = databaseAddress.getDummyStreetsAround(house.getCenter(), 20);
        for (Street street : dummyStreetsAround) {
            //Logger.i(TAG, "Street to check" + street.toString());
            if (street.getName().equalsIgnoreCase(addrPlaceName)){
//                    Logger.i(TAG, "findStreetForHouse() - Use the dummy street with the same name as place" +
//                            "\n place: " + addrPlace + " , city: " + addrCity + ", street: " + street.toString());
                return street;
            }
        }

        //no dummy streets fits our needs try to find the city with same name as house placename
        List<City> closestCities = ga.getClosestCities(house.getCenter(), 30);
        for (City city :  closestCities) {
            if (city.getName().equalsIgnoreCase(addrPlaceName)){
                // TODO test if any dummy street with name exist but it should be ensured using previous dummy streets around
                Street streetToInsert = databaseAddress.createDummyStreet(city.getName(), city.getOsmId(), city.getCenter());
                long id = databaseAddress.insertStreet(streetToInsert, true);

//                Logger.i(TAG, "findStreetForHouse(): Create new dummy street for found city: " + city.getName() +
//                        "\n new street: " + streetToInsert.toString() +
//                        "\n house: " + house.toString());
                return streetToInsert;
            }
        }

        return null;
    }

    /**
     * Find the corresponding street for house based on place name
     * @param house
     * @return
     */
    private Street findStreetByAddrStreetName(House house) {

        String addrStreetName = house.getStreetName();
        Logger.i(TAG, "Looking for best street for house by addrStreetName: " + addrStreetName);

//        // test the last founded street as first
//        if (lastHouseStreet != null && lastHouseStreet.getName().equalsIgnoreCase(addrStreetName)){
//            if
//            return lastHouseStreet;
//        }

        List<Street> streetsAround = databaseAddress.getStreetsAround(house.getCenter(), 15);

        for (Street street : streetsAround) {
            Logger.i(TAG, "Street to check" + street.toString());
            if (street.getName().equalsIgnoreCase(addrStreetName)){
                return street;
            }
        }
        // was not able to find the nereast street with the same name > try to select by name from addressdb
        if (house.getCityName().length() > 0 ){

            long start = System.currentTimeMillis();
            Street street = databaseAddress.selectStreetByNames(house.getCityName(), addrStreetName);
            timeFindStreetSelectFromDB += System.currentTimeMillis() - start;
            if (street != null){
                double distance = Utils.getDistance(house.getCenter(), street.getGeometry().getCentroid());
                if (distance < 2500){
                    numOfStreetForHousesUsingSqlSelect++;
                    return street;
                }
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
            return maxSimilarStreet;
        }

        return null;
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

        if ( ait == AddrInterpolationType.ALPHABETIC){
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

        if (ait == AddrInterpolationType.INTEGER){
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
                    startHouse.getPostCode(),
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
     * @param houseNumber
     * @param ait
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



    private List<House> parseCreateRussianStyle (House house1, String streetName2){

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
                        house1.getPostCode(),
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
}
