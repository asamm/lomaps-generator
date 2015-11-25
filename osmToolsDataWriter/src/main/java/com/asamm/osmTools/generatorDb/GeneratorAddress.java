package com.asamm.osmTools.generatorDb;

import com.asamm.osmTools.generatorDb.address.Boundary;
import com.asamm.osmTools.generatorDb.address.BoundaryCreator;
import com.asamm.osmTools.generatorDb.address.City;
import com.asamm.osmTools.generatorDb.address.StreetCreator;
import com.asamm.osmTools.generatorDb.data.AOsmObject;
import com.asamm.osmTools.generatorDb.data.OsmAddress;
import com.asamm.osmTools.generatorDb.data.OsmConst;
import com.asamm.osmTools.generatorDb.data.WayEx;
import com.asamm.osmTools.generatorDb.dataContainer.ADataContainer;
import com.asamm.osmTools.generatorDb.db.ADatabaseHandler;
import com.asamm.osmTools.generatorDb.db.DatabaseAddress;
import com.asamm.osmTools.generatorDb.utils.OsmUtils;
import com.asamm.osmTools.generatorDb.utils.Utils;
import com.asamm.osmTools.utils.Logger;
import com.vividsolutions.jts.geom.*;
import com.vividsolutions.jts.index.strtree.STRtree;
import gnu.trove.list.TLongList;
import org.openstreetmap.osmosis.core.domain.v0_6.Node;
import org.openstreetmap.osmosis.core.domain.v0_6.Relation;
import org.openstreetmap.osmosis.core.domain.v0_6.Way;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GeneratorAddress extends AGenerator {
	
	private static final String TAG = GeneratorAddress.class.getSimpleName();



    /** All places created from node or from relations*/
    private List<City> cities;

    /** JTS in memory index of center geometries for cities*/
    private STRtree cityCenterIndex;

    private List<Boundary> boundaries;

    /** Center city and the best boundary for it*/
    private Map<Long, Boundary> centerCityBoundaryMap;

    /** List of cities that are in the boundary*/
    private Map<Boundary, List<City>> citiesInBoundaryMap;

    // output DB file
    private File outputDb;

    // handler for tags
    private WriterAddressDefinition addressDefinition;
	
	public GeneratorAddress(WriterAddressDefinition addressDefinition, File outputDb) throws Exception {

        Logger.d(TAG, "Prepared GeneratorAddress");

        this.outputDb = outputDb;
        this.addressDefinition = addressDefinition;

        this.cities = new ArrayList<>();
        this.boundaries = new ArrayList<>();
        this.centerCityBoundaryMap = new HashMap<>();
        this.citiesInBoundaryMap = new HashMap<>();

        initialize();


	}

	@Override
	protected ADatabaseHandler prepareDatabase()   throws Exception{
		return new DatabaseAddress(outputDb);
	}

    @Override
    public void proceedData(ADataContainer dc) {


        // ---- step 1 find all city places -----
        Logger.i(TAG, "=== Step 1 - load city places ===");
        loadCityPlaces(dc);

        // ---- step 2 create boundaries -----
        Logger.i(TAG, "=== Step 2 - create boundaries ===");
        loadBoundaries(dc);

        // ---- step 3 find center city for boundary -----
        Logger.i(TAG, "=== Step 3 - find center city for boundary ===");
        findCenterCityForBoundary(dc);

        // ---- step 4 create list of cities that are in boundary ----
        Logger.i(TAG, "=== Step 4 - find all cities inside boundaries ===");
        findAllCitiesForBoundary();

        // ---- step 5 write cities to DB ----
        Logger.i(TAG, "=== Step 5 - write cities to db ===");
        insertCitiesToDB();


        StreetCreator sc = new StreetCreator(dc, this);
        // ----- Step 6 create streets from relations streets -----
        Logger.i(TAG, "=== Step 6 - create streets from relations ===");
        sc.createStreetFromRelations();

        // ----- step 7 create streets from ways ------
        Logger.i(TAG, "=== Step 7 - create streets from ways ===");
        sc.createStreetFromWays();
        Logger.i(TAG, "Create dummy streets for cities without street ===");
        ((DatabaseAddress) db).createDummyStreets();
        ((DatabaseAddress) db).buildStreetNameIndex();

        Logger.i(TAG, "=== Step 8 - create houses ===");
        Logger.i(TAG, "Create houses from relations");
        sc.createHousesFromRelations();
        Logger.i(TAG, "Create houses from ways");
        sc.createHousesFromWays();
        Logger.i(TAG, "Create houses from nodes");
        sc.createHousesFromNodes();
        Logger.i(TAG, "Clear duplicated houses ");
        ((DatabaseAddress) db).deleteDuplicatedHouses();


        Logger.i(TAG, "=== Step 9 - simplify street and city geoms ===");
        simplifyGeoms ();

        Logger.i(TAG, "Finding cities for every street way takes: " + sc.timeFindStreetCities/1000.0 + " sec" );
        Logger.i(TAG, "Finding cities only loading cities fromk DB takes: " + sc.timeLoadNereastCities /1000.0 + " sec" );
        Logger.i(TAG, "Finding cities only compare the boundaries takes: " + sc.timeFindCityTestByGeom /1000.0 + " sec" );

        Logger.i(TAG, "Joining ways and preparation for insert: " + sc.timeJoinWaysToStreets /1000.0 + " sec" );
        Logger.i(TAG, "Insert streets: " + sc.timeInsertStreetSql /1000.0 + " sec" );

        Logger.i(TAG, "Houses" );
        Logger.i(TAG, "Create parse houses: " + sc.timeCreateParseHouses /1000.0 + " sec" );
        Logger.i(TAG, "Find street for house: " + sc.timeFindStreetForHouse /1000.0 + " sec" );
        Logger.i(TAG, "Find street for house using name from DB: " + sc.timeFindStreetSelectFromDB /1000.0 + " sec" );
        Logger.i(TAG, "Find street for house using similar name: " + sc.timeFindStreetSimilarName /1000.0 + " sec" );
        Logger.i(TAG, "Find street for house using the nearest: " + sc.timeFindStreetNearest /1000.0 + " sec" );

        Logger.i(TAG, "Number of found streets for houses using sql select, : " + sc.numOfStreetForHousesUsingSqlSelect);

        Logger.i(TAG, "Number of removed houses - not able to find street, : " + sc.removedHousesWithDefinedPlace);
        Logger.i(TAG, "Number of removed houses with defined addr:street name, : " + sc.removedHousesWithDefinedStreetName);


    }


    /**************************************************/
    /*  STEP 1 - Create cities
    /**************************************************/

    void loadCityPlaces(ADataContainer dc) {
        cityCenterIndex = new STRtree();
        TLongList nodeIds = dc.getNodeIds();

        for (int i=0, size = nodeIds.size(); i < size; i++) {
            Node node = dc.getNodeFromCache(nodeIds.get(i));
            if (node == null || !addressDefinition.isValidPlaceNode(node)) {
                continue;
            }

            //Logger.i(TAG,"Has place node: " + node.toString());
            String place = OsmUtils.getTagValue(node, OsmConst.OSMTagKey.PLACE);
            City.CityType cityType = City.CityType.createFromPlaceValue(place);
            if (cityType == null){
                Logger.d(TAG, "Can not create CityType from place tag. " + node.toString());
                continue;
            }

            City city = new City(cityType);
            city.setId(node.getId());
            city.setName(OsmUtils.getTagValue(node, OsmConst.OSMTagKey.NAME));
            city.setNamesInternational(OsmUtils.getNamesInternational(node));
            city.setCenter(new GeometryFactory().createPoint(new Coordinate(node.getLongitude(), node.getLatitude())));
            city.setIsIn(OsmUtils.getTagValue(node, OsmConst.OSMTagKey.IS_IN));

            if (!city.isValid()){
                Logger.d(TAG, "City is not valid. Do not add into city cache. City: " + city.toString());
                continue;
            }
            // add crated city into list and also into index
            cities.add(city);
            cityCenterIndex.insert(city.getCenter().getEnvelopeInternal(), city);
        }

        Logger.i(TAG, "loadCityPlaces: " + cities.size() + " cities were created and loaded into cache");
    }

    /**************************************************/
    /*  STEP 2 - Create boundaries
    /**************************************************/

    void loadBoundaries(ADataContainer dc) {

        // create boundaries from relation
        TLongList relationIds = dc.getRelationIds();
        BoundaryCreator boundaryFactory = new BoundaryCreator(relationIds.size());

        for (int i=0, size = relationIds.size(); i < size; i++) {
            Relation relation = dc.getRelationFromCache(relationIds.get(i));
            if (relation == null) {
                continue;
            }

            Boundary boundary = boundaryFactory.create(dc, relation);

            if (boundary == null){
                //Logger.i(TAG, "Relation was not proceeds. Creation boundary failed. Relation id: " + relation.getId());
                continue;
            }

            if (boundary.isValid()){
                //Logger.i(TAG, "loadBoundaries: Add boundary from relation to cache: " + boundary.toString());
                boundaries.add(boundary);
            }
        }

        TLongList wayIds = dc.getWayIds();
        for (int i=0, size = relationIds.size(); i < size; i++) {
            Way way = dc.getWayFromCache(wayIds.get(i));
            Boundary boundary = boundaryFactory.create(dc, way);

            if (boundary == null){
                //Logger.i(TAG, "Relation was not proceeds. Creation boundary failed. Relation id: " + relation.getId());
                continue;
            }

            if (boundary.isValid()){
                //Logger.i(TAG, "loadBoundaries: Add boundary from way to cache: " + boundary.toString());
                boundaries.add(boundary);
            }
        }

        Logger.i(TAG, "loadBoundaries: " + boundaries.size() + " boundaries were created and loaded into cache");
    }

    /**************************************************/
    /*  STEP 3 - find center place for boundary
    /**************************************************/

    /**
     * Try to find center city for every boundary
     * @param dc base osm data container
     */
    private void findCenterCityForBoundary(ADataContainer dc) {

        for (Boundary boundary :  boundaries){

            String boundaryName = boundary.getName().toLowerCase();
            String altBoundaryName = boundary.getShortName().toLowerCase();

            City cityFound = null;
            if(boundary.hasAdminCenterId()) {
                for (City city : cities) {
                    if (city.getId() == boundary.getAdminCenterId()) {
                        cityFound = city;
                        //Logger.i(TAG, "City were founded by admin center for boundary: "+boundary.getId()+ " city: " + city.toString());
                        break;
                    }
                }
            }

            if(cityFound == null) {
                for (City city : cities) {
                    if (boundaryName.equalsIgnoreCase(city.getName()) || altBoundaryName.equalsIgnoreCase(city.getName())){
                        if (boundary.getGeom().contains(city.getCenter())) {
                            //Logger.i(TAG, "City were founded by name and contains for boundary: "+boundary.getId()+ " city: " + city.toString());
                            cityFound = city;
                            break;
                        }
                    }
                }
            }

            // Try to find city that has similar name as boundary
            if (cityFound == null) {
                for (City city : cities) {
                    if (hasSimilarName(boundary, city)) {
                        if (boundary.getGeom().contains(city.getCenter())) {
                            // city has similar name boundary and is in bounds > use it as center
                            cityFound = city;
                            break;
                        }
                    }
                }
            }

            // there is no city for this boundary > try to guess and create new one from boundary informations
            if (cityFound == null && boundary.hasCityType()){

                cityFound = createMissingCity(boundary);
                if (cityFound.isValid()){
                    boundary.setAdminCenterId(cityFound.getId());
                    cities.add(cityFound);
                }
            }

            if (cityFound != null){
                // OK we have center city for boundary > put them into cache and compare priority
                registerBoundaryForCity (boundary, cityFound);
            }
            else {
                //Logger.i(TAG, "Not found any center city for boundary: "  + boundary.toString());
            }
        }
    }

    /**
     * City can be center for more boundaries. Register the best boundary for city
     * Mothod compare priority of previous boundary (if exist).
     * @param boundary new boundary that should registered for center city
     * @param city center city
     */
    private void registerBoundaryForCity(Boundary boundary, City city) {

        // try to obtain previous registered boundary for city
        Boundary oldBoundary = this.centerCityBoundaryMap.get(city.getId());
        if (oldBoundary == null){
            //there is no registered boundary for this city > simple register it
            centerCityBoundaryMap.put(city.getId(), boundary);
        }
        else if (oldBoundary.getAdminLevel() == boundary.getAdminLevel()
                && oldBoundary != boundary
                && oldBoundary.getName().equalsIgnoreCase(boundary.getName())){
            // this condition is inspiration from OSMand probably can happen that there
            // are to boundaries for the same city
            MultiPolygon newBounds = (MultiPolygon) oldBoundary.getGeom().union(boundary.getGeom());
            oldBoundary.setGeom(newBounds);

            Logger.i(TAG, "Boundaries are similar -> union boundaries for =  " +  oldBoundary.getId() + " and " + boundary.getId());
            Logger.i(TAG, "Union polygon:  " + boundary.toGeoJsonString());
        }

        else {
            int oldBoundaryPriority = getCityBoundaryPriority (oldBoundary, city);
            int newBoundaryPriority = getCityBoundaryPriority(boundary, city);

            if (newBoundaryPriority < oldBoundaryPriority){
//                Logger.i(TAG, "Boundary: " + oldBoundary.toString()
//                        + " \n were replaced with boundary: " + boundary.toString());
                centerCityBoundaryMap.put(city.getId(), boundary);
            }
        }
    }

    /**
     * Compare how ideal is boundary for city. The priority is evaluated as integer value from 0 - 36
     * @param boundary boundary to compare
     * @param city city to compare with boundary
     * @return priority the lower is better
     */
    private int getCityBoundaryPriority(Boundary boundary, City city) {

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
            else if(city.getId() == boundary.getAdminCenterId()){
                return adminLevelPriority;  // return 1 - 6
            }
            return 10 + adminLevelPriority; // return 11 - 16, priority was made based on name
        }
        else {
            // boundary and city has different name
            if(city.getId() == boundary.getAdminCenterId()) {
                return 20 + adminLevelPriority;
            }
            else {
                return 30  + adminLevelPriority;
            }
        }
    }

    /**
     * Says how priority agains other admin_level has boundary. The lower priority is better. OsmAnd ispiration
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


    /**
     * Compare name of boundary with name of city and try find city with similar name as boundary
     * @param boundary
     * @param city
     * @return true if city has similar name as boundary
     */
    private boolean hasSimilarName (Boundary boundary, City city){

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
        return false;
    }

    private City createMissingCity (Boundary boundary){

        City city = new City(boundary.getCityType());
        city.setId(boundary.getId());
        city.setName(boundary.getName());
        city.setNamesInternational(boundary.getNamesInternational());
        city.setCenter(boundary.getCenterPoint());

        return city;
    }

    /**************************************************/
    /*  STEP 4 - create list of cities inside boundary
    /**************************************************/

    /**
     * For every boundary make list of cities that are in the boundary poly
     */
    private void findAllCitiesForBoundary() {

        Boundary boundary = null;
        City city = null;
        for (int i=0, size = boundaries.size(); i < size; i++){

            boundary = boundaries.get(i);
            List<City> citiesInBoundary = new ArrayList<>();
            List cityFromIndex = cityCenterIndex.query(boundary.getGeom().getEnvelopeInternal());

            for (int c=0, sizeC = cityFromIndex.size();  c < sizeC; c++){
                city = cities.get(c);

                if (boundary.getGeom().contains(city.getCenter())){
                    citiesInBoundary.add(city);
                }
            }
            citiesInBoundaryMap.put(boundary, citiesInBoundary);
        }
    }

    /**************************************************/
    /*  STEP 5 - write cities into DB
    /**************************************************/

    private void insertCitiesToDB() {

        City city;
        for (int i = 0, size = cities.size() ; i < size;  i++){
            city = cities.get(i);
            Boundary boundary = centerCityBoundaryMap.get(city.getId());
            if (boundary != null){
                city.setGeom(boundary.getGeom());
            }

            ((DatabaseAddress) db).insertCity(city, boundary);
        }

        ((DatabaseAddress) db).buildCityIndexes();
    }

    /**************************************************/
    /*  STEP 9 - simplify geoms
    /**************************************************/

    private void simplifyGeoms() {
        DatabaseAddress databaseAddress = getDatabaseAddress();
        long start = System.currentTimeMillis();

        databaseAddress.simplifyStreetGeoms();

        City city;
        for (int i = 0, size = cities.size() ; i < size;  i++){
            city = cities.get(i);
            Boundary boundary = centerCityBoundaryMap.get(city.getId());
            if (boundary != null){
                databaseAddress.simplifyCityGeom(city, boundary);
            }
        }

        ((DatabaseAddress) db).buildCityBoundaryIndex();
        long time = System.currentTimeMillis() - start;
        Logger.i(TAG, "SimplifyGeoms takes: " + time/1000.0 + " sec" );
    }


    /**************************************************/
    /*             Other tools
    /**************************************************/

    public List<City> getClosestCities (Point centerPoint, int minNumber){

        double distance = 5000;

        List cityFromIndex = new ArrayList();

        int numOfResize = 0;
        while (cityFromIndex.size() < minNumber) {
            //Logger.i(TAG,"Extends bounding box");
            Polygon searchBound = Utils.createRectangle(centerPoint.getCoordinate(), distance);
            cityFromIndex = cityCenterIndex.query(searchBound.getEnvelopeInternal());
            distance = distance * 2;
            numOfResize++;
            if (numOfResize == 4){
                Logger.i(TAG, "MAx num of resize reached");
                break;
            }
        }

        return cityFromIndex;
    }



    /**************************************************/
    /*             Inherited methods
    /**************************************************/

	protected AOsmObject addNodeImpl(Node node, ADatabaseHandler db) {
 		// generate OSM poi object
		OsmAddress addr = OsmAddress.create(node);
		if (addr == null) {
			return null;
		}

		// add to database
		if (db != null) {
//			db.insertObject(addr);
		} 
		return addr;
	}

	@Override
	protected AOsmObject addWayImp(WayEx way, ADatabaseHandler db) {
		return null;
	}


    /**************************************************/
    /*              Getters
    /**************************************************/


    public List<City> getCities() {
        return cities;
    }

    public Map<Long, Boundary> getCenterCityBoundaryMap() {
        return centerCityBoundaryMap;
    }

    public Map<Boundary, List<City>> getCitiesInBoundaryMap() {
        return citiesInBoundaryMap;
    }

    public DatabaseAddress getDatabaseAddress (){
        return (DatabaseAddress) db;
    }
}
