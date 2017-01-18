package com.asamm.osmTools.generatorDb.address;

import com.asamm.osmTools.generatorDb.input.definition.WriterAddressDefinition;
import com.asamm.osmTools.generatorDb.data.OsmConst.OSMTagKey;
import com.asamm.osmTools.generatorDb.data.WayEx;
import com.asamm.osmTools.generatorDb.dataContainer.ADataContainer;
import com.asamm.osmTools.generatorDb.db.DatabaseAddress;
import com.asamm.osmTools.generatorDb.index.IndexController;
import com.asamm.osmTools.generatorDb.utils.Const;
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
import gnu.trove.map.hash.THashMap;
import gnu.trove.set.hash.THashSet;
import gnu.trove.set.hash.TLongHashSet;
import org.openstreetmap.osmosis.core.domain.v0_6.*;

import java.util.*;

import static com.asamm.osmTools.generatorDb.address.City.CityType.*;

/**
 * Created by voldapet on 2015-08-21 .
 */
public class StreetController extends AaddressController {

    public interface OnJoinStreetListener {
        public void onJoin(Street street);
    }

    private static final String TAG = StreetController.class.getSimpleName();

    //TODO for production remove the time measures

    public static long timeFindStreetCities = 0;
    public static long timeLoadNereastCities = 0;
    public static long timeFindCityTestByGeom = 0;
    public static long timeFindCityTestByDistance = 0;
    public static long timeFindCityFindNearest = 0;

    public long timeJoinWaysToStreets = 0;

    private WriterAddressDefinition wad;

    public StreetController(ADataContainer dc, DatabaseAddress databaseAddress, WriterAddressDefinition wad){
        super(dc, databaseAddress);

        this.wad = wad;
    }

    /**
     * Test if geometry can be used as way for street
     *
     * @param mls geometry to check
     * @return true if geometry can be used for address object
     */
    private boolean isValidGeometry (MultiLineString mls){

        if (mls == null || mls.getCoordinates().length == 0 ){
            return false;
        }
        if ( !wad.isInDatabaseArea(mls)){
            return false;
        }
        return true;
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
            if ( !isValidGeometry(mls)) {
                // probably associtate Address relation that does not contain any street member > try to create geom
                // from unnamed streets if relation has some houses
                //Logger.i(TAG, "can not create street geom from relation id: " + relationId);
                continue;
            }


            Street wayStreet = new Street (name, isInList, mls);
            wayStreet.setOsmId(relationId);
            wayStreet.setPath(isPath(relation));

            // find all cities where street can be in it or is close to city
            List<City> cities = findCitiesForPlace(wayStreet.getGeometry(), wayStreet.getIsIn(), wayStreet.getOsmId());
            wayStreet.addCities(cities);


            if (name == null || name.length() == 0) {
                IndexController.getInstance().insertWayStreetUnnamed(wayStreet.getGeometry().getEnvelopeInternal(), wayStreet);
                dc.addWayStreetByOsmId(wayStreet);
            }
            else {
                IndexController.getInstance().insertWayStreetNamed(wayStreet.getGeometry().getEnvelopeInternal(), wayStreet);
                dc.addWayStreetHashName(wayStreet);
                dc.addWayStreetByOsmId(wayStreet);
            }
        }
    }

    /**
     * For every street check if can be street and create waystreet from it
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
            if ( !isValidGeometry(mls)) {
                // probably associate Address relation that does not contain any street member > skip it
                continue;
            }

            String name = OsmUtils.getStreetName(way);
            Street wayStreet = new Street (name, isInList, mls);
            wayStreet.setOsmId(wayId);
            wayStreet.setPath(isPath(way));

            if (name == null || name.length() == 0) {
                dc.addWayStreetByOsmId(wayStreet);
                IndexController.getInstance().insertWayStreetUnnamed(wayStreet.getGeometry().getEnvelopeInternal(), wayStreet);
            }
            else {
                // find all cities where street can be in it or is close to city
                long start = System.currentTimeMillis();
                List<City> cities = findCitiesForPlace(wayStreet.getGeometry(), wayStreet.getIsIn(), wayStreet.getOsmId());
                timeFindStreetCities += System.currentTimeMillis() - start;
                wayStreet.addCities(cities);

                dc.addWayStreetHashName(wayStreet);
                dc.addWayStreetByOsmId(wayStreet);
                IndexController.getInstance().insertWayStreetNamed(wayStreet.getGeometry().getEnvelopeInternal(), wayStreet);
            }

            if (wayId == 24346977){
                Logger.i(TAG, "createWayStreetFromWays(): Creates wayStreet: " + wayStreet.toString() );
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


    /** ************************************************
     *                  SPLIT STREET
     ************************************************* */

    /**
     * Try to split street into several segment.
     * @param streetToSplit street that has long geom and should be splitted into several parts
     * @return
     */
    public List<Street> splitLongStreet(Street streetToSplit) {
        // in first step try to split to street using cities is in
        List<Street> streets = splitGeomByParentCities (streetToSplit);

        if (streets.size() <= 1){
            // was not possible to split street by cities, simply cut it in half
            streets = splitStreetIntoTwoHalf(streetToSplit);
        }
        return streets;
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
            // create copy from street to split
            Street street = new Street(streetToSplit);

            // PREPARE NEW GEOMETRY

            Geometry geomIntersection = null;
            //Logger.i(TAG, "Line for cut: " + GeomUtils.geomToGeoJson(streetToSplit.getGeometry()));
            //Logger.i(TAG, "City for cut: " + GeomUtils.geomToGeoJson(city.getGeom()));
            try {
                geomIntersection = streetToSplit.getGeometry().intersection(city.getGeom());
            }
            catch (TopologyException e){
                // in some rare situation can intersection throw topology exception because no-node intersection
                continue;
            }
            // the result can be GeomCollection try to convert it into Lines
            MultiLineString mls = GeomUtils.geometryToMultilineString(geomIntersection);

            // test if there is any intersection
            if (mls == null || !mls.isValid() || mls.isEmpty()){
                continue;
            }

            //compute diagonal length
            Coordinate[] coordinates = mls.getCoordinates();
            double lengthM = Utils.getDistance(coordinates[0], coordinates[coordinates.length-1]);
            if (lengthM < 10){
                continue;
            }

            //Logger.i(TAG, "Intersection line: " + Utils.geomToGeoJson(mls));

            // set geometry from intersection to new street
            street.setGeometry(mls);
            street.addCities(findCitiesForPlace(street.getGeometry(), street.getIsIn(), street.getOsmId()));

            // SPLIT HOUSES
            THashSet housesToRemove = new THashSet();
            for (House house : streetToSplit.getHouses()){
                if (city.getGeom().intersects(house.getCenter())){
                    street.addHouse(house);
                    housesToRemove.add(house);
                }
            }
            // remove the houses that were assigned to new street
            streetToSplit.getHouses().removeAll(housesToRemove);

            // new street is created
            streets.add(street);

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
                streetToSplit.setCities(findCitiesForPlace(streetToSplit.getGeometry(), streetToSplit.getIsIn(), streetToSplit.getOsmId()));
                streets.add(streetToSplit);
            }
        }

        return streets;
    }

    public List<Street> splitStreetIntoTwoHalf (Street streetToSplit){
        // split geometry of street envelope into two polygons
        if (streetToSplit.getGeometry() == null){
            List<Street> streets = new ArrayList<>();
            return streets;
        }

        Envelope envelope = streetToSplit.getGeometry().getEnvelopeInternal();
        double height = envelope.getHeight();
        double width =  envelope.getWidth();

        Envelope env1 = new Envelope();
        Envelope env2 = new Envelope();
        if (height > width){
            env1.init(envelope.getMinX(), envelope.getMaxX(), envelope.getMinY(), envelope.getMinY() + height / 2);
            env2.init(envelope.getMinX(), envelope.getMaxX(), envelope.getMinY() + height / 2, envelope.getMaxY() );
        }
        else {
            env1.init(envelope.getMinX(), envelope.getMinX() + width / 2, envelope.getMinY(), envelope.getMaxY());
            env2.init(envelope.getMinX() + width / 2, envelope.getMaxX() , envelope.getMinY(), envelope.getMaxY());
        }
        Geometry poly1 = geometryFactory.toGeometry(env1);
        Geometry poly2 = geometryFactory.toGeometry(env2);

        // create copy from street that should be splitted
        Street street1 = new Street(streetToSplit);
        Street street2 = new Street(streetToSplit);

        // prepare new cutted geometries for new street
        street1.setGeometry(GeomUtils.geometryToMultilineString(poly1.intersection(streetToSplit.getGeometry())));
        street2.setGeometry(GeomUtils.geometryToMultilineString(poly2.intersection(streetToSplit.getGeometry())));

        // split houses into half
        THashSet<House> houses1 = new THashSet<>();
        THashSet<House> houses2 = new THashSet<>();

        for (House house : streetToSplit.getHouses()){
            if (poly1.intersects(house.getCenter())){
                houses1.add(house);
            }
            else if (poly2.intersects(house.getCenter())) {
                houses2.add(house);
            }
        }
        street1.setHouses(houses1);
        street2.setHouses(houses2);

        List<Street> streets = new ArrayList<>();
        streets.add(street1);
        streets.add(street2);
        return streets;
    }

    public List<Street> splitStreetIntoGrid(Street streetToSplit) {
        // split geometry of street envelope into two polygons
        List<Street> streetsSplitted = new ArrayList<>();
        if (streetToSplit.getGeometry() == null) {
            return streetsSplitted;
        }

        double maxHeight = 0.15;
        double maxWidth = 0.15;

//        if (streetToSplit.getName().equals("Drykkjeåvegen")){
//            Logger.i(TAG, "Joined street to split: "  + streetToSplit.toString());
//        }

        Envelope envelope = streetToSplit.getGeometry().getEnvelopeInternal();
        double height = envelope.getHeight();
        double width = envelope.getWidth();

        if (maxHeight > height && maxWidth > width){
            streetsSplitted.add(streetToSplit);
            return streetsSplitted;
        }

        // size of grid
        int countH = (int) Math.ceil((height / maxHeight));
        int countW = (int) Math.ceil((width / maxWidth));

        // create grid as polygons
        List<Geometry> gridPolygons = new ArrayList<>();
        for (int i = 0; i < countH ; i++){
            for (int j = 0; j < countW ; j++){
                double polyMinX = envelope.getMinX() + j * maxWidth;
                double polyMaxX = envelope.getMinX() + (j+1) * maxWidth;
                double polyMinY = envelope.getMinY() + i * maxHeight;
                double polyMaxY = envelope.getMinY() + (i+1) * maxHeight;

                Envelope polyE = new Envelope();
                polyE.init(polyMinX, polyMaxX, polyMinY, polyMaxY);
                Geometry poly = geometryFactory.toGeometry(polyE);
                gridPolygons.add(poly);
            }
        }

        THashMap<Geometry, Street> gridStreetMap = new THashMap<>();
        THashMap<Geometry,THashSet<House>> gridHouseMap = new THashMap<>();

        // for every grid create new street, create street only with valid geom
        for (Geometry gridPoly : gridPolygons){
            Street street = new Street(streetToSplit);
            MultiLineString mls = (GeomUtils.geometryToMultilineString(gridPoly.intersection(streetToSplit.getGeometry())));
            if (mls != null && !mls.isEmpty() && mls.isValid()){
                street.setGeometry(mls);
                gridStreetMap.put(gridPoly, street);
            }
        }

        // find grid where house is in it
        for (House house :  streetToSplit.getHouses()){
            for (Geometry geometry : gridStreetMap.keySet()){
                if (geometry.intersects(house.getCenter())){
                    THashSet<House> houses = gridHouseMap.get(geometry);
                    if (houses == null){
                        houses = new THashSet<>();
                        gridHouseMap.put(geometry, houses);
                    }
                    houses.add(house);
                }
            }
        }

        // combine streets with houses
        for (Map.Entry<Geometry, Street> entry : gridStreetMap.entrySet()){
            Street street = entry.getValue();
            //check if exist any houses for this part of street
            THashSet<House> housesForStreet = gridHouseMap.get(entry.getValue());
            street.setHouses(housesForStreet);
            streetsSplitted.add(street);
        }

        return streetsSplitted;
    }

    /**
     * Compare cities where street is in and get the most top level cities
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

        if (streetToSplit.getName().equalsIgnoreCase("Schwarzer Weg")){
            Logger.i(TAG, "splitToCityParts(): Num of multilinestreet: " + numGeomElements + " street: " + streetToSplit.toString());
        }

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
            List<City> cities = findCitiesForPlace(streetSep.getGeometry(), streetSep.getIsIn() , streetSep.getOsmId());
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

        if (streetToSplit.getName().equalsIgnoreCase("Schwarzer Weg")){
            for (Street street : separatedStreets){
                Logger.i(TAG, "splitToCityParts(): separated street: " + street.toString());
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
        double[] distanceDeg = Utils.metersToDlatDlong(elements.get(0).getCoordinate(), Const.ADR_MAX_DISTANCE_BETWEEN_STREET_SEGMENTS);

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
     * Find cities where center of geometry can be in it
     *
     * @param geometry geometry to find the cities
     * @return cities that contain tested geom
     */
    public static List<City> findCitiesForPlace(Geometry geometry, List<String> isInNames, long osmId) {
        //Logger.i(TAG, " findCitiesForPlace() - looking for cities for geom: " + GeomUtils.geomToGeoJson(geometry));

        List<City> foundCities = new ArrayList<>(); // cities where object is in it
        // if all founded cities are suburbs search by more detailed parameters to get at least town, city or village
        boolean areFoundedCitiesSuburbs = false;

        if (geometry.isEmpty()){
            return foundCities;
        }
        Point centroid = geometry.getCentroid();
        if ( !centroid.isValid()){
            // centroid for street with same points is NaN. This is workaround
            centroid = new GeometryFactory().createPoint(geometry.getCoordinate());
        }

        long start = System.currentTimeMillis();
        List<City> citiesAround = IndexController.getInstance().getClosestCities(centroid, 40);
        //Logger.i(TAG, " findCitiesForPlace () num of cities around: " + citiesAround.toString());
        timeLoadNereastCities += System.currentTimeMillis() - start;

        //RECOGNIZE BY IS IN TAG

        if (isInNames.size() > 0){
            for (int i = citiesAround.size() - 1; i >= 0; i--){
                City city = citiesAround.get(i);
                if (isInNames.contains(city.getName())){
                    foundCities.add(city);
                    citiesAround.remove(i);
                    if (city.getType() == SUBURB || city.getType() == DISTRICT){
                        areFoundedCitiesSuburbs = true;
                    }
                }
            }
        }

        // RECOGNIZE BY CONTAINS GEOM

        // create index from rest of cities if boundary exist
        List<City> citiesWithoutBound = new ArrayList<>();

        start = System.currentTimeMillis();
        PreparedGeometry streetGeomPrepared = PreparedGeometryFactory.prepare(geometry);
        //PreparedGeometry streetGeomPrepared = PreparedGeometryFactory.prepare(street.getGeometry().getEnvelope().getCentroid());

        for (City city : citiesAround){
            //Logger.i(TAG, " findCitiesForPlace () prepare to test intersection with city: " + city.toString());
            MultiPolygon mp = city.getGeom();
            if (mp == null){
                // this city does not have defined the bound geometry
                citiesWithoutBound.add(city);
                continue;
            }
            if (streetGeomPrepared.intersects(city.getGeom())){
                double distance = Utils.getDistance(centroid, city.getCenter());
                if (distance / city.getType().getRadius() < Const.ADR_MAX_FOUNDED_CITY_DISTANCE_RADIUS_RATIO){
                    foundCities.add(city);
                    if (city.getType() == SUBURB || city.getType() == DISTRICT){
                        areFoundedCitiesSuburbs = true;
                    }
                }
            }
        }

        timeFindCityTestByGeom += System.currentTimeMillis() - start;


        // TEST THE MOST IMPORTANT CITY FROM CITIES WITHOUT BOUNDARIES

        // for rest of cities that does not have defined the bounds check relative importance
        if (foundCities.size() == 0 || areFoundedCitiesSuburbs){
            // sort cities without bounds by relative importance to place
            sortByRelativeImportance(centroid, citiesWithoutBound);

            start = System.currentTimeMillis();
            for (City city : citiesWithoutBound){
                double importance = relativeImportance(geometry, city);
                if (importance < 1) {
                    // geom is probably inside of radius of city or has some parameters that improve it priority
                    if (city.getType() == DISTRICT || city.getType() == SUBURB ){
                        // we found some near district or subburb without boundary
                        foundCities.add(city);
                    }
                    else if (!isCityTypeInList(city.getType(), foundCities)){
                        // place is near this city and city type is not in list of founded cities
                        foundCities.add(city);
                        areFoundedCitiesSuburbs = false;
                        break;
                    }
                }
            }
        }

        // TEST THE MOST IMPORTANT CITY, TOWN OR VILLAGE FROM ALL CITIES AROUND

        if (foundCities.size() == 0 || areFoundedCitiesSuburbs){
            // it seems that list of cities without bounds does not contain any useful city so try to obtain more cities
            citiesAround = IndexController.getInstance().getClosestCities(centroid, 80);
            sortByRelativeImportance(centroid, citiesAround);

            start = System.currentTimeMillis();
            for (City city : citiesAround){

                double importance = relativeImportance(geometry, city);
                if (importance < 1) {
                    // geom is probably inside of radius of city or has some parameters that improve it priority
                    if (city.getType() == DISTRICT || city.getType() == SUBURB ){
                        // all nearest suburbs was procesed earlier we're looking for city
                        continue;
                    }
                    else if (!isCityTypeInList(city.getType(), foundCities)){
                        // place is near this city and city type is not in list of founded cities
                        foundCities.add(city);
                        areFoundedCitiesSuburbs = false;
                    }
                }
            }
        }

        // NO CITY WAS FOUND GET AT LEAST THE NEAREST FROM LOADED

        start = System.currentTimeMillis();
        if (foundCities.size() == 0) {
            // iterate again the cities and try to find the closest
            City city = getNearestCity(geometry.getCentroid(), citiesAround);

            if (city != null){
                // test how fare is the nearest city
                double distance = Utils.getDistance(centroid, city.getCenter());
                if (distance / city.getType().getRadius() < Const.ADR_MAX_FOUNDED_CITY_DISTANCE_RADIUS_RATIO){
                    //add only cities that are far as their 3xradius
                    foundCities.add(city);
                }
                else {
                    //TODO WRITE THESE STREETS INTO CUSTOM TABLE AND CHECK WHICH STREET ARE REMOVED
//                    Logger.e(TAG, "Can not find city for street: " + street.getName()
//                            + " geometry: " + Utils.geomToGeoJson(street.getGeometry()));
                }
            }
        }
        timeFindCityFindNearest += System.currentTimeMillis() - start;

        return foundCities;
    }



    /**
     * Select the closest city from the list
     * @param point Center for search
     * @return the closest city or null if no cities was found
     */
    public static City getNearestCity(Point point, List<City> cities) {

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

    /**
     * Test if list of cities contain any city of defined city type
     *
     * @param cType type city to test if is in list
     * @param cities cities to test if contain defined type
     * @return true if list contain defined cityType
     */
    private static boolean isCityTypeInList (City.CityType cType, List<City> cities){

        for (City city : cities){
            if (city.getType() == cType){
                return true;
            }
        }
        return false;
    }

    /**
     * Compute relative distance between center point of geometry and center point of city
     * and the frac it by city priority.
     *
     * Relative because computed distance depends on type of city
     *
     * @param geom geometry from its centroid will be computed relative distance
     * @param city city to compute relative distance
     * @return value that say how important is city for place of given geometry. Lower is Better!
     */
    private static double relativeImportance(Geometry geom, City city) {
        Coordinate coordinate = geom.getEnvelope().getCentroid().getCoordinate();
        double relDistance = Utils.getDistance(coordinate, city.getCenter().getCoordinate()) / city.getType().getRadius();
        return relDistance / CityController.computeCityPriority(city);
    }

    /**
     * Sort cities in list by relative importance of city based on city and distance between city and center
     * point of geometry
     *
     * @param geom location to sort from it
     * @param cities cities to sort
     */
    private static void sortByRelativeImportance(final Geometry geom, List<City> cities){

        Collections.sort(cities, new Comparator<City>() {
            @Override
            public int compare(City c1, City c2) {
                double rd1 = relativeImportance(geom, c1);
                double rd2 = relativeImportance(geom, c2);
                return Double.compare(rd1, rd2);
            }
        });
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

        if (Utils.getDistanceNearest(center, nearestStreet.getGeometry()) > Const.ADR_MAX_DISTANCE_NAMED_STREET){
            return null;
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
        //Logger.i(TAG, "Create streetm geom grom entity: " + entity.getId() + " adn type: " + entity.getType().toString());
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

                // TODO REMOVE temporary solution because wrong relation in ukraine
                if (entity.getId() == 2275254 || entity.getId() == 2213799){
                    Logger.i(TAG, "Skip UKRAINE entity: " + entity.getId());
                    continue;
                }


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

