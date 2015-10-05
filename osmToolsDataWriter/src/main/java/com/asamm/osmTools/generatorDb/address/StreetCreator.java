package com.asamm.osmTools.generatorDb.address;

import com.asamm.osmTools.generatorDb.GeneratorAddress;
import com.asamm.osmTools.generatorDb.data.OsmConst.OSMTagKey;
import com.asamm.osmTools.generatorDb.data.WayEx;
import com.asamm.osmTools.generatorDb.dataContainer.ADataContainer;
import com.asamm.osmTools.generatorDb.dataContainer.DataContainerHdd;
import com.asamm.osmTools.generatorDb.db.DatabaseAddress;
import com.asamm.osmTools.generatorDb.utils.OsmUtils;
import com.asamm.osmTools.generatorDb.utils.Utils;
import com.asamm.osmTools.utils.Logger;
import com.vividsolutions.jts.geom.*;
import com.vividsolutions.jts.operation.union.UnaryUnionOp;
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

    GeometryFactory geometryFactory;

    public long timeFindStreetCities = 0;
    public long timeInsertStreetTmpTime = 0;
    public long timeLoadCities = 0;

    public StreetCreator (ADataContainer dc, GeneratorAddress ga){

        this.dc = dc;
        this.ga = ga;
        this.databaseAddress = ga.getDatabaseAddress();

        this.geometryFactory = new GeometryFactory();
    }

    /**
     * Iterate relations > find that are street or associated street and create streets from members
     * Create streets are saved into database
     */
    public void createStreetFromRelations () {

        for (long relationId : dc.getRelationIds()) {

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

            if ( !(type.equals(OSMTagKey.STREET.getValue()) || type.equals(OSMTagKey.ASSOCIATED_STREET.getValue()))){
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
            // create street geom
            MultiLineString mls = createStreetGeom(relation);

            if (mls == null) {
                // probably associtate Address relation that does not contain any street member > skip it
                continue;
            }

            Street street = new Street (name, isInList, mls);

            // find all cities where street can be in it or is close to city
            List<City> cities = findStreetCities(street, relationId);

            List<Street> streets = createWayStreetsForCities(street, cities);

            for (Street streetToInsert : streets){
                long streetId = insertOrUpdateStreet(streetToInsert);
            }
        }
    }




    public void createStreetFromWays() {

        for (long wayId : dc.getWayIds()){

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
            List<Street> streets = createWayStreetsForCities(street, cities);
            Street streetTmp;
            for (int i=0, size = streets.size(); i < size; i++) {
                dc.addStreet(streets.get(i));

            }

//            if (wayId == 7980116){
//                for (City city : cities){
//                    Logger.i(TAG, "Way id  7980116 is in cities: " + city.toString());
//                }
//            }

            timeInsertStreetTmpTime += System.currentTimeMillis() - start;
        }

        // combine all tmp street ways into streets
        joinWayStreets();
    }

    /**
     * iterate through hashes of created streets from osm ways and join ways into one Street
     */
    private void joinWayStreets() {

        Logger.i(TAG, "Start to join ways into streets and insert them into DB");

        if (dc instanceof DataContainerHdd){
            ((DataContainerHdd) dc).finalizeWayStreetCaching();
        }

        Iterator<Integer> iterator = dc.getStreetHashSet().iterator();
        List<MultiLineString> geoms = new ArrayList<>();

        int counter = 0;
        while (iterator.hasNext()){
            counter ++;
            List<Long> cityIds = new ArrayList<>();
            int hash = iterator.next();
            //load all ways with the same hash from cache
            List<Street> wayStreets = dc.getWayStreetsFromCache(hash);
            if (wayStreets.size() ==0){
                continue;
            }

            // join possible cityIds
            geoms = new ArrayList<>();
            for (int i=0, size = wayStreets.size(); i < size; i++){
                Street wayStreet = wayStreets.get(i);
                geoms.add(wayStreet.getGeometry());
                //combine cityIds from every streetWay into on list of cities
                for (Long cityId : wayStreet.getCityIds()){
                    if ( !cityIds.contains(cityId)){
                        cityIds.add(cityId);
                    }
                }
            }

            // union all ways into one street
            MultiLineString mls;
            Geometry geomUnion = UnaryUnionOp.union(geoms, geometryFactory);
            if (geomUnion instanceof LineString){
                mls = geometryFactory.createMultiLineString(new LineString[]{(LineString) geomUnion});
            }
            else {
                mls = (MultiLineString) UnaryUnionOp.union(geoms, geometryFactory);
            }

            // get first street and update the geom (all way streets should have save city and name
            Street street = wayStreets.get(0);
            street.setGeometry(mls);
            street.setCityIds(cityIds);

            insertOrUpdateStreet(street);
        }
    }


    /**
     * Create list of way street for every city that can contain this street.
     * @param street streets to fill with city
     * @param streetCities list of cities in which can be street placed
     * @return
     */
    private List<Street> createWayStreetsForCities(Street street, List<City> streetCities){

        List<Long> cityIds = new ArrayList<>();
        List<Street> streets = new ArrayList<>();

        // create list of city ids in which is this way (street)
        for (City city : streetCities){
            cityIds.add(city.getId());
        }

        // add cityIds into street so every copy know other streets
        street.setCityIds(cityIds);

        for (City city : streetCities){
            // create copy of street
            Street streetCreated = new Street(street);
            streetCreated.setCityId(city.getId());

            streets.add(streetCreated);
        }
        return streets;
    }


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
        List<City> cities = ga.getCities(); //all cities for map
        if (cities.size() > 100){
            cities = databaseAddress.loadNearestCities(streetCentroid, 40);
        }
        timeLoadCities += System.currentTimeMillis() - start;

        City city;
        for (int i=0, size = cities.size(); i < size; i++){

            city = cities.get(i);
//            if (wayId == 7980116){
//                Logger.i(TAG, "test city:  " + city.getId() + ", name: " + city.getName()
//                        + " distance: " + Utils.getDistance(streetCentroid, city.getCenter()));
//            }
            // recognize city by the name and isIn tag
            if (street.getIsIn().contains(city.getName())){
//                if (wayId == 7980116){
//                    Logger.i(TAG, "Way id  7980116 has city ISIN, city: " + city.toString());
//                }
                streetCities.add(city);
                continue;
            }

            // try check if is in boundary
            Boundary boundary = ga.getCenterCityBoundaryMap().get(city.getId());


            if (boundary != null) {
                if (boundary.getGeom().contains(street.getGeometry()) || (street.getGeometry().crosses(boundary.getGeom()))) {
//                    if (wayId == 7980116){
//                        Logger.i(TAG, "Way id  7980116 is in boundary" + boundary.toString());
//                    }

                    streetCities.add(city);
                    continue;
                }
            }

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
            city = getClosestCity(street.getGeometry().getCentroid());

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

    /**
     * Insert street with name, city and subcity into database. If the same street alrady exist
     * then join old geometry with new one and update old record
     * @param street street to insert
     * @return id of inserted street
     */
    private long insertOrUpdateStreet(Street street) {

        if ( !street.isValid()) {
            //Logger.i(TAG, "Street is not valid can not insert or update db:  " + street.toString());
            return 0;
        }

        long id = 0;

        // try to load same street from database
        List<Street> loadedStreets = databaseAddress.selectStreetInCities(street);
        int size = loadedStreets.size();
        if (size == 0) {
            // database does not contain such street > simple insert new record
            id = databaseAddress.insertStreet(street);
        }

        if (size == 1){

            //join loaded street with new one and update the loaded street
            // already exist street with this name for different with any same city
            Street loadedStreet = loadedStreets.get(0);

            List<Long> cityIdsNotSaved = new ArrayList<>();
            for (Long cityId : street.getCityIds()){
                if ( !loadedStreet.getCityIds().contains(cityId)){
                    cityIdsNotSaved.add(cityId);
                }
            }

            loadedStreet.setGeometry(unionStreetGeom(loadedStreet, street));
            loadedStreet.setCityIds(cityIdsNotSaved);
            //Logger.w(TAG, "Joined geom: ." + loadedStreet.toString());
            // update street geom
            id = databaseAddress.updateStreet(loadedStreet);
        }

        if (size > 1){
            // there more streets in the DB > join them into the new one and delete the previous records in db
            List<Long> cityIds = new ArrayList<>();
            for (int i=0; i < size; i++ ){
                Street loadedStreet = loadedStreets.get(i);

                for (Long cityId : street.getCityIds()){
                    if ( !street.getCityIds().contains(cityId)){
                        street.addCityId(cityId);
                    }
                }

                street.setGeometry(unionStreetGeom(street, loadedStreet));
                // delete old record
                databaseAddress.deleteStreet(loadedStreet.getId());
            }

            // insert new joined street into db
            id = databaseAddress.insertStreet(street);
        }

        return id;
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
    /*                UTILS
    /**************************************************/


    private boolean isStreetWay (Way way) {

        String highwayVal = OsmUtils.getTagValue(way, OSMTagKey.HIGHWAY);
        if (highwayVal == null){
            return false;
        }

        if (highwayVal.equals("track")){
            return false;
        }

        if (highwayVal.equals("path")){
            return false;
        }

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



    private String getStreetName (Entity entity){
        String name = null;
        if (entity != null){
            name = OsmUtils.getTagValue(entity, OSMTagKey.NAME);
        }
        return name;
    }

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

                if (rm.getMemberRole() == null || !rm.getMemberRole().equals("street")) {
                    //create geometry only from members that has role street
                    continue;
                }
                else if (rm.getMemberType() == EntityType.Way){
                    Way way = dc.getWayFromCache(rm.getMemberId());
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
                     mls = (MultiLineString) mls.union(mlsNext);
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

