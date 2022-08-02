package com.asamm.osmTools.generatorDb.address;

import com.asamm.osmTools.generatorDb.data.OsmConst;
import com.asamm.osmTools.generatorDb.data.OsmConst.OSMTagKey;
import com.asamm.osmTools.generatorDb.data.WayEx;
import com.asamm.osmTools.generatorDb.dataContainer.ADataContainer;
import com.asamm.osmTools.generatorDb.db.DatabaseAddress;
import com.asamm.osmTools.generatorDb.index.IndexController;
import com.asamm.osmTools.generatorDb.input.definition.WriterAddressDefinition;
import com.asamm.osmTools.generatorDb.utils.BiDiHashMap;
import com.asamm.osmTools.generatorDb.utils.GeomUtils;
import com.asamm.osmTools.generatorDb.utils.OsmUtils;
import com.asamm.osmTools.generatorDb.utils.Utils;
import com.asamm.osmTools.utils.Logger;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;
import org.locationtech.jts.index.strtree.STRtree;
import org.locationtech.jts.operation.linemerge.LineMerger;
import gnu.trove.list.TLongList;
import gnu.trove.list.array.TLongArrayList;
import gnu.trove.map.hash.THashMap;
import gnu.trove.set.hash.THashSet;
import org.apache.commons.lang3.StringUtils;
import org.openstreetmap.osmosis.core.domain.v0_6.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Created by voldapet on 2015-08-14 .
 */
public class CityController extends AaddressController {

    private static final String TAG = CityController.class.getSimpleName();

    private static final boolean isDebugEntity = true;

    private static final long debugEntityId = 62484;

    /**
     * Needed for generation boundaries from relation. Ways with this ids ware already use for generation of bounds
     */
    private TLongList processedWays;

    public static CityController createForCountryBound (ADataContainer dc){
        return new CityController (dc, null, null);
    }

    WriterAddressDefinition wgd;

    public CityController(ADataContainer dc, DatabaseAddress databaseAddress, WriterAddressDefinition wgd) {

        super(dc, databaseAddress);

        this.wgd = wgd;
        processedWays = new TLongArrayList();
    }


    /**
     * Test if geometry can be used as center of city or as boundary. It also check if geom is located in country area
     *
     * @param geom geometry yo check
     * @return true if geometry can be used for address object
     */
    private boolean isValidGeometry (Geometry geom){
        return (geom != null && wgd.isInDatabaseArea(geom));
    }


    // CITIES

    public void createCities() {
        TLongList nodeIds = dc.getNodeIds();

        for (int i=0, size = nodeIds.size(); i < size; i++) {
            Node node = dc.getNodeFromCache(nodeIds.get(i));
            if (node == null || !wgd.isValidPlaceNode(node)) {
                continue;
            }

            Point center = geometryFactory.createPoint(new Coordinate(node.getLongitude(), node.getLatitude()));
            if ( !isValidGeometry(center)){
                continue;
            }

            //Logger.i(TAG,"Has place node: " + node.toString());
            String place = OsmUtils.getTagValue(node, OsmConst.OSMTagKey.PLACE);
            City.CityType cityType = City.CityType.createFromPlaceValue(place);
            if (cityType == null){
                Logger.d(TAG, "Can not create CityType from place tag. " + node.toString());
                continue;
            }

            String name = OsmUtils.getTagValue(node, OsmConst.OSMTagKey.NAME);

            City city = new City(cityType);
            city.setOsmId(node.getId());
            city.setName(name);
            city.setNamesInternational(OsmUtils.getNamesLangMutation(node, "name", name));
            city.setCenter(center);
            city.setIsIn(OsmUtils.getTagValue(node, OsmConst.OSMTagKey.IS_IN));

            city.setCapital(OsmUtils.getTagValue(node, OSMTagKey.CAPITAL));
            city.setWebsite(OsmUtils.getTagValue(node, OSMTagKey.WEBSITE));
            city.setWikipedia(OsmUtils.getTagValue(node, OSMTagKey.WIKIPEDIA));
            String populStr = OsmUtils.getTagValue(node, OSMTagKey.POPULATION);
            if (populStr != null && Utils.isInteger(populStr)){
                city.setPopulation(Integer.valueOf(populStr));
            }

            if (!city.isValid()){
                Logger.d(TAG, "City is not valid. Do not add into city cache. City: " + city.toString());
                continue;
            }
            // add crated city into list and also into index Center index is used for finding boundaries
            dc.addCity(city);
            IndexController.getInstance().insertCityCenter(city.getCenter().getEnvelopeInternal(), city);
        }

        Logger.i(TAG, "loadCityPlaces: " + dc.getCitiesMap().size() + " cities were created and loaded into cache");
    }

    // BOUNDARY

    public void createBoundariesRegions() {

        // create boundaries from relation
        TLongList relationIds = dc.getRelationIds();

        for (int i=0, size = relationIds.size(); i < size; i++) {
            long relationId = relationIds.get(i);

//            if (relationId == 56106 || relationId == 2135916){
//               Logger.i(TAG, "Start process relation id: " + relationId);
//            }
            Relation relation = dc.getRelationFromCache(relationId);
            Boundary boundary = createBoundary(relation, false);
            checkRegisterBoundary(boundary);
        }

        TLongList wayIds = dc.getWayIds();
        for (int i=0, size = wayIds.size(); i < size; i++) {
            Way way = dc.getWayFromCache(wayIds.get(i));
            Boundary boundary = createBoundary(way, false);
            checkRegisterBoundary(boundary);
        }

        Logger.i(TAG, "loadBoundariesRegions: " + dc.getBoundaries().size() + " boundaries were created and loaded into cache");
    }


    /**
     * Create boundary object from relation or way. Only admin boundaries with all members and closed ways
     * are converted into boundary object
     *
     * @param entity OSM entity to crate boundary from it
     * @param isCloseRing set true when want to force close boundary geom to ring. It cause that all
     *                    boundaries also the boundaries from neighbour country will be add into data
     * @return boundary or null is is not possible to crate boundary from entity
     */
    public  Boundary createBoundary(Entity entity, boolean isCloseRing) {

        if (entity == null){
            return null;
        }

        EntityType entityType =  entity.getType();
        if (entityType == EntityType.Node){
            return null;
        }

        String place = OsmUtils.getTagValue(entity, OSMTagKey.PLACE);
        String boundaryTag = OsmUtils.getTagValue(entity, OSMTagKey.BOUNDARY);
        if ( !Utils.objectEquals("administrative", boundaryTag) && !Utils.objectEquals("territorial", boundaryTag) && place == null ){
            // is not administrative nor the place
            return null;
        }

        LineMerger outerMerger = new LineMerger();
        LineMerger innerMerger = new LineMerger();

        // get name of boundary for further checking
        String bName = OsmUtils.getTagValue(entity, OSMTagKey.NAME);

        Boundary boundary = new Boundary(entity.getId(), entityType);
        boundary.setName(bName);

        if (isDebugEntity && entity.getId() == debugEntityId){
            Logger.i(TAG, "---- Create boundary for entity " + entity.getId() + ", name: " + bName);
        }

        if (entity.getType() == EntityType.Way){

            Way way = (Way) entity;

            if (processedWays.contains(entity.getId())  || !way.isClosed()){
                // boundary from this way was already processed or way is not closed
                return  null;
            }

            WayEx wayEx = dc.getWay(entity.getId());
            if (wayEx == null || !wayEx.isValid()){
                return null;
            }

            MultiPolygon geom = GeomUtils.createMultiPolyFromOuterWay(wayEx, isCloseRing);
            if (geom == null){
                return null;
            }
            boundary.setGeom(geom);
        }
        else if (entity.getType() == EntityType.Relation){

            Relation re = (Relation) entity;
            List<RelationMember> members = re.getMembers();

            for (RelationMember reMember : members) {

                if (reMember.getMemberType() == EntityType.Way){

                    WayEx wayEx = dc.getWay(reMember.getMemberId());

                    if (wayEx == null || !wayEx.isValid()){
                        // member way was not downloaded from cache (probably is way in different map
                        // such boundary is not useful

                        //return null;
                        continue;
                    }

                    if (reMember.getMemberRole().equals("inner")){
                        LineString lineString = geometryFactory.createLineString(wayEx.getCoordinates());
                        innerMerger.add(lineString);
                    }

                    else {
                        // get name of child way
                        String wName = OsmUtils.getTagValue(wayEx, OSMTagKey.NAME);

                        // if name are not equal keep the way for further check (it could be different suburb)
                        if (Utils.objectEquals(wName, bName) || wName == null) {
                            processedWays.add(wayEx.getId());
                        }

                        LineString lineString = geometryFactory.createLineString(wayEx.getCoordinates());
                        outerMerger.add(lineString);
                    }
                }

                else if (reMember.getMemberType() == EntityType.Node){
                    String memberRole = reMember.getMemberRole();
                    if (memberRole.equals("admin_centre") || memberRole.equals("admin_center")){
                        // this point has define role as center of relation
                        boundary.setAdminCenterId(reMember.getMemberId());
                    }
                    else if (memberRole.equals("label")){
                        // label is node where to draw label Has lower priority then admin_center
                        boundary.setAdminCenterId(reMember.getMemberId());
                    }
                }
            }

            // for relation create multipolygon
            MultiPolygon boundaryGeom = GeomUtils.createMultiPolygon(entity.getId(), outerMerger, innerMerger, isCloseRing);
            boundary.setGeom(boundaryGeom);
        }

        // test if center node is in area of database
        if ( wgd != null && !isValidGeometry(boundary.getGeom())) {
            return null;
        }

        // try to recognize type of place (its only for place relation/ways
        City.CityType cityType = City.CityType.createFromPlaceValue(place);

        boundary.setAdminLevel(parseBoundaryAdminLevel(entity));
        boundary.setShortName(OsmUtils.getTagValue(entity, OSMTagKey.SHORT_NAME));
        boundary.setCityType(cityType);
        boundary.setNameLangs(OsmUtils.getNamesLangMutation(entity, "name", bName));
        boundary.setOfficialNamesInternational(OsmUtils.getNamesLangMutation(entity, "official_name", bName));

        // obtain alt_names
        boundary.setNamesAlternative(new ArrayList<String>(
                OsmUtils.getNamesLangMutation(entity, "alt_name", bName).values()));
        // add other names like int_name
        boundary.addNameAlternative(OsmUtils.getTagValue(entity, OSMTagKey.INT_NAME));


        boundary.setWebsite(OsmUtils.getTagValue(entity, OSMTagKey.WEBSITE));
        boundary.setWikipedia(OsmUtils.getTagValue(entity, OSMTagKey.WIKIPEDIA));
        String populStr = OsmUtils.getTagValue(entity, OSMTagKey.POPULATION);
        if (populStr != null && Utils.isInteger(populStr)){
            boundary.setPopulation(Integer.valueOf(populStr));
        }


        if (isDebugEntity && entity.getId() == debugEntityId){
            Logger.i(TAG, "Administrative/place entity created: " + boundary.toString());
        }

        IndexController.getInstance().insertBoundary(boundary.getGeom().getEnvelopeInternal(), boundary);
        return boundary;
    }


    /**
     * Check if boundary can be used as city area of region area. If yeas then save it for next process
     *
     * @param boundary boundary to test and store in datacontainer
     */
    private void checkRegisterBoundary (Boundary boundary) {
        if (boundary == null || !boundary.isValid()){
            //Logger.i(TAG, "Relation was not proceeds. Creation boundary failed. Relation id: " + relation.getId());
            return;
        }

        if (boundary.getAdminLevel() == wgd.getRegionAdminLevel()) {
            // boundary has corect admin level for regions > create new region
            Region region = new Region(
                    boundary.getId(),
                    boundary.getEntityType(),
                    "",
                    boundary.getName(),
                    boundary.getNameLangs(),
                    boundary.getGeom());

            IndexController.getInstance().insertRegion(region);
            //((DatabaseAddress)db).insertRegion(region);
            dc.addRegion(region);
        }

        if (wgd.isCityAdminLevel(boundary.getAdminLevel())
                || wgd.isMappedBoundaryId(boundary.getId())){
            // from this boundary can be created the city area
            dc.addBoundary(boundary);
        }
        else {
            if (isDebugEntity && boundary.getId() == debugEntityId){
                Logger.i(TAG, "checkRegisterBoundary: boundary does not have admin level for city: " + boundary.toString());
            }
        }
    }

    /**
     * Test if boundary can be region (by admin level). If yes that region is written into database address and
     * also into index for later when  look for region for cities
     * @param boundary to test and create region
     */
    private void createRegion(Boundary boundary){
        Region region = new Region(
                boundary.getId(), boundary.getEntityType(), "",
                boundary.getName(), boundary.getNameLangs(), boundary.getGeom());

        IndexController.getInstance().insertRegion(region);
        //((DatabaseAddress)db).insertRegion(region);
        dc.addRegion(region);
    }


    /**************************************************/
    /*  Find center place for boundary
    /**************************************************/

    /**
     * Try to find center city for every boundary.
     */
    public void findCenterCityForBoundary() {
        List<Boundary> boundaries = dc.getBoundaries();
        for (Boundary boundary :  boundaries){

            if (isDebugEntity && boundary.getId() == debugEntityId){
                Logger.i(TAG, "Search for center city for boundary: " + boundary.toString());
            }

            String boundaryName = boundary.getName().toLowerCase();
            String altBoundaryName = boundary.getShortName().toLowerCase();
            Collection<City> cities = dc.getCities();

            City cityFound = null;
            // Test city by custom mapper definition
            if (wgd.isMappedBoundaryId(boundary.getId())){
                long mappedCityId = wgd.getMappedCityIdForBoundary(boundary.getId());
                cityFound = dc.getCity(mappedCityId); // can be null

                if (cityFound != null){
                    Logger.i(TAG, "Founded city by custom mapper ; city " + cityFound.toString() +
                            "\n boundary:  " + boundary.getId());
                }
            }

            // try to load city based on admin center id (if defined)
            if(cityFound == null && boundary.hasAdminCenterId()) {
                cityFound = dc.getCity(boundary.getAdminCenterId()); // can be null

                if (cityFound != null && isDebugEntity && boundary.getId() == debugEntityId){
                    Logger.i(TAG, "City founded by admin Id. Boundary id: "+boundary.getId()+ " city: " + cityFound.toString());
                }
            }

            if(cityFound == null) {
                for (City city : cities) {
                    if (boundaryName.equalsIgnoreCase(city.getName()) || altBoundaryName.equalsIgnoreCase(city.getName())){
                        if (boundary.getGeom().contains(city.getCenter())) {
                            if (isDebugEntity && boundary.getId() == debugEntityId){
                                Logger.i(TAG, "City found by name. Boundary id: "+boundary.getId()+ " city: " + city.toString());
                            }
                            if (canBeSetAsCenterCity (boundary, city)){
                                cityFound = city;
                                break;
                            }
                        }
                    }
                }
            }

            // Try to find city that has similar name as boundary
            if (cityFound == null) {
                for (City city : cities) {
                    if (hasSimilarName(boundary, city)) {
                        if (boundary.getGeom().contains(city.getCenter())) {
                            // test if city is some small village > in this case do not use it
                            if (canBeSetAsCenterCity (boundary, city)){
                                cityFound = city;
                                break;
                            }
                        }
                    }
                }
            }

            // there is no city for this boundary > try to guess and create new one from boundary informations
            if (cityFound == null && boundary.hasCityType()){
                if (isDebugEntity && boundary.getId() == debugEntityId){
                    Logger.i(TAG, "Create center city for boundary from boundary infomation: "  + boundary.getId());
                }
                cityFound = createMissingCity(boundary);
                if (cityFound.isValid()){
                    boundary.setAdminCenterId(cityFound.getOsmId());
                    dc.addCity(cityFound);
                    IndexController.getInstance().insertCityCenter(cityFound.getCenter().getEnvelopeInternal(), cityFound);
                }
            }

            if (cityFound != null){
                if (isDebugEntity && boundary.getId() == debugEntityId){
                    Logger.i(TAG, "Found center city for boundary: "  + boundary.getId() +
                        " Founded city: " + cityFound.toString());
                }

                // OK we have center city for boundary > put them into cache and compare priority
                checkPriorityBoundaryForCity(dc, boundary, cityFound);
            }
            else {
                if (isDebugEntity && boundary.getId() == debugEntityId){
                    Logger.i(TAG, "Not found any center city for boundary: "  + boundary.toString());
                }
            }
        }
    }

    /**
     * City can be center for more boundaries. createRegion the best boundary for city
     * Method compare priority of previous boundary (if exist).
     * @param boundary new boundary that should registered for center city
     * @param city center city
     */
    private void checkPriorityBoundaryForCity(ADataContainer dc, Boundary boundary, City city) {

        // try to obtain previous registered boundary for city
        BiDiHashMap<City, Boundary> centerCityBoundaryMap = dc.getCenterCityBoundaryMap();
        Boundary oldBoundary = centerCityBoundaryMap.getValue(city);
        if (oldBoundary == null){
            //there is no registered boundary for this city > simple register it
            if (isDebugEntity && boundary.getId() == debugEntityId){
                Logger.i(TAG, "Put city and boundary into map. Boundary id: "+boundary.getId()+ " city: " + city.toString());
            }

            centerCityBoundaryMap.put(city, boundary);
        }
        else if (oldBoundary.getAdminLevel() == boundary.getAdminLevel()
                && oldBoundary != boundary
                && oldBoundary.getName().equalsIgnoreCase(boundary.getName())){

            MultiPolygon newBounds = GeomUtils.fixInvalidGeom(oldBoundary.getGeom().union(boundary.getGeom()));
            oldBoundary.setGeom(newBounds);

            if (isDebugEntity && oldBoundary.getId() == debugEntityId || boundary.getId() == debugEntityId){
                Logger.i(TAG, "Combine geometries of boundaries. Old boundary id: "+oldBoundary.getId()
                        + " new boundary id: " + boundary.getId());
            }
        }

        else {
            int oldBoundaryPriority = getCityBoundaryPriority(oldBoundary, city);
            int newBoundaryPriority = getCityBoundaryPriority(boundary, city);

            if (newBoundaryPriority < oldBoundaryPriority){
                centerCityBoundaryMap.put(city, boundary);

                if (isDebugEntity && oldBoundary.getId() == debugEntityId || boundary.getId() == debugEntityId){
                    Logger.i(TAG, "Replace the old boundary with better priority. Old boundary id: "+oldBoundary.getId()
                            + " new boundary id: " + boundary.getId());
                }
            }
            else {

                if (isDebugEntity && oldBoundary.getId() == debugEntityId || boundary.getId() == debugEntityId){
                    Logger.i(TAG, "Boundary priority is not enought to replace the previous. Old boundary id: "+oldBoundary.getId()
                            + " new boundary id: " + boundary.getId());
                }
            }
        }
    }

    /**
     * Create city from boundary definition.
     * @param boundary boundary to create city from it
     * @return created city
     */
    private City createMissingCity (Boundary boundary){

        //Logger.i(TAG, "Create missing city for boundary: "  + boundary.getName());

        City city = new City(boundary.getCityType());
        city.setOsmId(boundary.getId());
        city.setName(boundary.getName());
        city.setNamesInternational(boundary.getNameLangs());
        city.setCenter(boundary.getCenterPoint());

        return city;
    }

    /**
     * It can happen that some small village or hamlet can have similar city as boundary for big area. It's
     * needed to limit such villages and do not allow to assign such boundaries for small city even of they
     * have similar name. Boundary with admin level > 5 can be automatically used for every city level

     * @param boundary boundary that should be assigned to the city
     * @param city city that we found as possible center city
     * @return true if city level can be used for boundary
     */
    public boolean canBeSetAsCenterCity(Boundary boundary, City city) {

        if (boundary.getAdminLevel() > 5){
            // this is not so big boundary automatically use it
            return true;
        }

        if (city.getType().getTypeCode() <= City.CityType.VILLAGE.getTypeCode()){
            return true;
        }
        // this city is SUBURB, HAMLET OR DISTRICT and boundary is something on admin <= 5 do not allow to use it as center
        if (isDebugEntity && boundary.getId() == debugEntityId){
            Logger.i(TAG, "City can not be used as center for boundary: "+boundary.getId()+ " city: " + city.toString());
        }

        return false;
    }


    /**************************************************/
    /*  Create list of cities inside boundary
    /**************************************************/

    /**
     * For every boundary make list of cities that are in the boundary poly
     */
    public void findAllCitiesForBoundary() {

        List<Boundary> boundaries = dc.getBoundaries();
        THashMap<Boundary, List<City>> citiesInBoundaryMap = dc.getCitiesInBoundaryMap();

        Boundary boundary = null;
        STRtree cityCenterIndex = IndexController.getInstance().getCityCenterIndex();
        for (int i=0, size = boundaries.size(); i < size; i++){

            boundary = boundaries.get(i);
            List<City> citiesInBoundary = new ArrayList<>();
            List<City> cityFromIndex = cityCenterIndex.query(boundary.getGeom().getEnvelopeInternal());

            for (City city : cityFromIndex){
                if (boundary.getGeom().contains(city.getCenter())){
                    citiesInBoundary.add(city);
                }
            }
            citiesInBoundaryMap.put(boundary, citiesInBoundary);
        }
    }


    /**************************************************/
    /*  Find parent city and parent region
    /**************************************************/

    /**
     * For all cities find parent city (if any) and region is in
     */
    public void findParentCitiesAndRegions() {
        Collection<City> cities = dc.getCities();
        for (City city : cities){
            City parentCity = findParentCity(dc, city);
            city.setParentCity(parentCity); // can return null

            Region parentRegion = findParentRegion(city);
            if (parentRegion != null){
                city.setRegion(parentRegion);
            }
        }
    }

    /**
     * Look for parent city for city.
     *
     * @param dc temporary data container
     * @param city city to find parent
     * @return parent city or null if was not possible to find parent city
     */
    private City findParentCity(ADataContainer dc, City city) {

        City parentCity = null;

        City.CityType type = city.getType();
        // find parent city only for villages subburs etc.
        if (type == City.CityType.CITY || type == City.CityType.TOWN){
            return null;
        }

        PreparedGeometry pgCenter = PreparedGeometryFactory.prepare(city.getCenter());

        List<Boundary> parentBoundaries = IndexController.getInstance().getBoundaryGeomIndex()
                .query(pgCenter.getGeometry().getEnvelopeInternal());

        // from the list of boundaries from query find such that cover the center of city and find the one with the best admin level
        Boundary parentBoundary = null;
        int parentBoundaryPriority = 6;

        for (Boundary boundary : parentBoundaries){
            if (pgCenter.intersects(boundary.getGeom())){
                int priority = getAdminLevelPriority(boundary);

                if (parentBoundary == null){
                    parentBoundary = boundary;
                    parentBoundaryPriority = priority;
                }
                else if (priority < parentBoundaryPriority) {
                    parentBoundary = boundary;
                    parentBoundaryPriority = priority;
                }
            }
        }

        // get the center city for the parent boundary
        if (parentBoundary != null){
            parentCity = dc.getCenterCityBoundaryMap().getKey(parentBoundary);

            if (parentCity != null && parentCity.getOsmId() == city.getOsmId()){
                // find the same city return null
                return null;
            }
        }
        return parentCity;
    }

    /**
     * Find admin region in which is city
     *
     * @param city to find region for
     * @return parent region in which city is in or null if there is no region for city
     */
    private Region findParentRegion (City city){

        PreparedGeometry pgCenter = PreparedGeometryFactory.prepare(city.getCenter());

        List<Region> parentRegions = IndexController.getInstance().getRegionGeomIndex()
                .query(pgCenter.getGeometry().getEnvelopeInternal());
        Region parentRegion = null;
        for (Region region : parentRegions){

            if (pgCenter.intersects(region.getGeom())){
                if (parentRegion != null){
                    // there is other region where city is in. compare it with previous by name and by area
                    if (city.getIsIn().toLowerCase().contains(region.getName().toLowerCase())){
                        parentRegion = region;
                    }
                    else if (region.getGeom().getArea() > parentRegion.getGeom().getArea()){
                        parentRegion = region;
                    }
                }
                else {
                    parentRegion = region;
                }
            }
        }

        return parentRegion;
    }



    /**
     * Compare name of boundary with name of city and try find city with similar name as boundary
     *
     * @param boundary boundary to check the name with city
     * @param city city to check its name with boundary name
     * @return true if city has similar name as boundary
     */
    private boolean hasSimilarName(Boundary boundary, City city){

        String bName = boundary.getName().toLowerCase();
        String bShortName = boundary.getShortName().toLowerCase();
        String cName = city.getName().toLowerCase();

        if (bName.length() == 0 || cName.length() == 0){
            return false;
        }

        if (bName.startsWith(cName+" ") || bName.endsWith(" "+ cName) || bName.contains( " " + cName + " ")){
            return true;
        }

        if (bShortName.startsWith(cName+" ") || bShortName.endsWith(" "+ cName) || bShortName.contains( " " + cName + " ")){
            return true;
        }

        if (cName.startsWith(bName+" ") || cName.endsWith(" "+ bName) || cName.contains( " " + bName + " ")){
            return true;
        }

        //compare using similarity
        double similarity = StringUtils.getJaroWinklerDistance(bName, cName);
        if (similarity > 0.89){
            return true;
        }

        similarity = StringUtils.getJaroWinklerDistance(bShortName, cName);
        if (similarity > 0.89){
            return true;
        }

        // try to remove general boundary names like, obec, gemainde, land..
        THashSet<String> boundaryPhrases = wgd.getBoundaryPhrases();
        for (String word : boundaryPhrases){
            if (bName.contains(word)){
                bName.replace(word,"");
                if (bName.startsWith(cName+" ") || bName.endsWith(" "+ cName) || bName.contains( " " + cName + " ")){
                    return true;
                }
            }
        }

        return false;
    }



    // PRIORITY

    /**
     * Compute the how the city is important based on num of population, number of international names or wiki links
     * @param city city to compute importance
     * @return value from 1 - 9 where higher is higher priority
     */
    public static double computeCityPriority (City city){

        double priority = 1;

        // population
        int population = city.getPopulation();
        // number of languages
        int langNames = city.getNamesInternational().size();

        if (population > 500000){
            if (population < 1000000){
                priority += 1;
            }
            else if (population >= 1000000){
                priority += 1.75;
            }
        }
        else if (langNames > 5){
            if (langNames < 12){
                priority += 0.75;
            }
            else if (langNames <20){
                priority += 1;
            }
            else if (langNames >=30){
                priority += 1.5;
            }
        }

        // website or wiki
//        if (city.getWebsite().length() > 0 || city.getWikipedia().length() > 0){
//            priority += 1;
//        }
        return priority;
    }


    /**************************************************/
    /*  City and Boundary utils
    /**************************************************/


    /**
     * Parse the tag that contains information about admin level
     *
     * @param entity entity to get admin level tag value
     * @return admin level value or -1 if admin level is not defined or value is not integer
     */
    private int parseBoundaryAdminLevel(Entity entity) {

        int adminLevel = -1;
        try {
            String tag = OsmUtils.getTagValue(entity, OSMTagKey.ADMIN_LEVEL);
            if (tag == null) {
                return adminLevel;
            }
            return Integer.parseInt(tag);
        } catch (NumberFormatException ex) {
            return adminLevel;
        }
    }


    /**
     * Compare how ideal is boundary for city. The priority is evaluated as integer value from 0 - 36
     * @param boundary boundary to compare
     * @param city city to compare with boundary
     * @return priority the lower is better
     */
    public int getCityBoundaryPriority(Boundary boundary, City city) {

        if (city.getOsmId() != 0 && boundary.getId() != 0 &&
                wgd.getMappedCityIdForBoundary(boundary.getId()) == city.getOsmId()){
            // this is custom mapped combination for city and boundary > use it as the best priority
            return 0;
        }

        boolean hasSameName = boundary.getName().equalsIgnoreCase(city.getName());

        if (!hasSameName && boundary.getShortName().length() > 0){
            // try to compare short name with city name
            hasSameName = boundary.getShortName().equalsIgnoreCase(city.getName());
        }

        int adminLevelPriority = getAdminLevelPriority(boundary); // can be maximal 6
        if(hasSameName) {
            if(boundary.getCityType() != null) {
                //boundary was created from relation that has tag place. So it's the best boundary for city
                return 0;
            }

            // IMPORTANT comparison based on admin center id was limited because bigger region can have defined the admin but smaller not
            else if(city.getOsmId() == boundary.getAdminCenterId()){
                return adminLevelPriority;  // return 1 - 6
            }
            return 10 + adminLevelPriority; // return 11 - 16,
        }
        else {
            //boundary and city has different name
            if(city.getOsmId() == boundary.getAdminCenterId()) {
                return 20 + adminLevelPriority;
            }
            else {
                return 30  + adminLevelPriority;
            }
        }
    }

    /**
     * Says how priority has admin_level for boundary. The lower priority is better. OA inspiration
     *
     * @param boundary boundary to check
     * @return return number from 1 - 6 as mark that says how "good" is admin level for boundary.
     * The lower value has better priority
     */
    private int getAdminLevelPriority(Boundary boundary) {

        int adminLevelPriority = 5;

        if(boundary.getAdminLevel() > 0) {

            int adminLevel = boundary.getAdminLevel();

            if(adminLevel == 8) { //the best level city boundary
                adminLevelPriority = 1;
            }
            else if(adminLevel == 7) {
                adminLevelPriority = 2;
            }
            else if(adminLevel == 6) {
                adminLevelPriority = 3;
            }
            else if(adminLevel == 9) {
                adminLevelPriority = 4;
            }
            else if(adminLevel == 10) {
                adminLevelPriority = 5;
            }
            else {
                adminLevelPriority = 6;
            }
        }
        return adminLevelPriority;
    }



}
