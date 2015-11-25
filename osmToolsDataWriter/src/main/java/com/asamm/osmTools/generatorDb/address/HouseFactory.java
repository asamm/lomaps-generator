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
import org.openstreetmap.osmosis.core.domain.v0_6.*;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by voldapet on 2015-11-05 .
 * Set of features that
 */
public class HouseFactory {


    private static final String TAG = HouseFactory.class.getSimpleName();


    ADataContainer dc;

    GeneratorAddress ga;

    DatabaseAddress databaseAddress;

    GeometryFactory geometryFactory;


    public  HouseFactory (ADataContainer dc, GeneratorAddress ga) {
        this.dc = dc;
        this.ga = ga;
        this.databaseAddress = ga.getDatabaseAddress();

        this.geometryFactory = new GeometryFactory();
    }

    /**
     * Crate house object from entity
     * @param entity way, node or relation that contain information about address
     * @return house or null if some tag is not valid or is not possible to obtain center point
     */
    public List<House> createHouse (Entity entity) {

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

        String postCode = parsePostCode(entity);
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

        return house;
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
            startValue = Integer.parseInt(startHouseNum);
            endValue = Integer.parseInt(endHouse.getNumber());
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
