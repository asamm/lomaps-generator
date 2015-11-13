package com.asamm.osmTools.generatorDb.address;

import com.asamm.osmTools.generatorDb.GeneratorAddress;
import com.asamm.osmTools.generatorDb.data.OsmConst;
import com.asamm.osmTools.generatorDb.data.WayEx;
import com.asamm.osmTools.generatorDb.dataContainer.ADataContainer;
import com.asamm.osmTools.generatorDb.db.DatabaseAddress;
import com.asamm.osmTools.generatorDb.utils.OsmUtils;
import com.asamm.osmTools.utils.Logger;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.Point;
import org.openstreetmap.osmosis.core.domain.v0_6.Entity;
import org.openstreetmap.osmosis.core.domain.v0_6.EntityType;
import org.openstreetmap.osmosis.core.domain.v0_6.Node;
import org.openstreetmap.osmosis.core.domain.v0_6.Way;

import java.util.ArrayList;
import java.util.List;

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

        String houseNum = OsmUtils.getTagValue(entity, OsmConst.OSMTagKey.ADDR_HOUSE_NUMBER);
        String houseName = OsmUtils.getTagValue(entity, OsmConst.OSMTagKey.ADDR_HOUSE_NAME);
        String streetName = OsmUtils.getTagValue(entity, OsmConst.OSMTagKey.ADDR_STREET);
        String streetName2 = OsmUtils.getTagValue(entity, OsmConst.OSMTagKey.ADDR_STREET2);
        String placeName = OsmUtils.getTagValue(entity, OsmConst.OSMTagKey.ADDR_PLACE);
        String cityName = OsmUtils.getTagValue(entity, OsmConst.OSMTagKey.ADDR_CITY);


        if (houseNum == null){
            houseNum = houseName;
        }
        if (houseNum == null){
            // can not obtain house number nor house name > skip it
            //Logger.w(TAG, "createHouse() Can not create house due to not number nor name is definer, OSM id: "
            //       + entity.getId());
            return houses;
        }

        //sometime is used addr:place but not the addr:city
        if (cityName == null && placeName != null){
            cityName = placeName;
        }

        String postCode = parsePostCode(entity);
        Point center = getCenter(entity, dc);

        if (center == null){
            //can not create center for this house
            return houses;
        }

        String addrInterpolationValue = OsmUtils.getTagValue(entity, OsmConst.OSMTagKey.ADDR_INTERPOLATION);
        House.AddrInterpolationType ait = House.AddrInterpolationType.fromValue(addrInterpolationValue);


        // prepare house from entity for next customization
        House house = new House(entity.getId(), houseNum, houseName, postCode, center, ait );
        house.setStreetName(streetName);
        house.setCityName(cityName);


        // SOME RUSSIAN ADR HAS TWO STREETS IN ONE BUILDING CREATE TWO HOUSES
        // http://wiki.openstreetmap.org/wiki/Proposed_features/AddrN
        if (streetName2 != null){
            houses.addAll(parseCreateRussianStyle(house, streetName2));
        }
        else if (addrInterpolationValue != null){
            // TODO prepare interpolated houses
        }
        else {

            houses.add(house);

        }




        return houses;
    }


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
                        house1.getCenter(),
                        house1.getHouseInterpolation());
                house2.setStreetName(house1.getStreetName());

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

        Logger.w(TAG, "getCenter() Can not create center point for house, OSM id: " + entity.getId());
        return null;
    }


    /**
     * Obtain postal code for house
     * @param entity node or way that represent the house
     * @return tag value ADDR_POSTCODE or POSTAL_CODE or null if postal code is not defined
     */
    private static String parsePostCode(Entity entity) {

        String postCode = OsmUtils.getTagValue(entity, OsmConst.OSMTagKey.ADDR_POSTCODE);
        if (postCode == null){
            postCode = OsmUtils.getTagValue(entity, OsmConst.OSMTagKey.POSTAL_CODE);
        }
        return postCode;
    }
}
