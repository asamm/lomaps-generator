package com.asamm.osmTools.generatorDb;

import com.asamm.osmTools.generatorDb.address.*;
import com.asamm.osmTools.generatorDb.data.AOsmObject;
import com.asamm.osmTools.generatorDb.data.OsmAddress;
import com.asamm.osmTools.generatorDb.data.OsmConst;
import com.asamm.osmTools.generatorDb.data.WayEx;
import com.asamm.osmTools.generatorDb.dataContainer.ADataContainer;
import com.asamm.osmTools.generatorDb.db.ADatabaseHandler;
import com.asamm.osmTools.generatorDb.db.DatabaseAddress;
import com.asamm.osmTools.generatorDb.utils.BiDiHashMap;
import com.asamm.osmTools.generatorDb.utils.GeomUtils;
import com.asamm.osmTools.generatorDb.utils.OsmUtils;
import com.asamm.osmTools.generatorDb.utils.Utils;
import com.asamm.osmTools.utils.Logger;
import com.vividsolutions.jts.geom.*;
import com.vividsolutions.jts.index.strtree.STRtree;
import com.vividsolutions.jts.operation.union.UnaryUnionOp;
import com.vividsolutions.jts.simplify.DouglasPeuckerSimplifier;
import gnu.trove.list.TLongList;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.list.array.TLongArrayList;
import gnu.trove.map.hash.THashMap;
import org.openstreetmap.osmosis.core.domain.v0_6.Node;
import org.openstreetmap.osmosis.core.domain.v0_6.Relation;
import org.openstreetmap.osmosis.core.domain.v0_6.Way;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class GeneratorAddress extends AGenerator {
	
	private static final String TAG = GeneratorAddress.class.getSimpleName();



    /**
     * All places created from nodes or from relations
     * */
    private List<City> cities;

    private List<Boundary> boundaries;

    /** Center city and the best boundary for it*/
    private BiDiHashMap<City, Boundary> centerCityBoundaryMap;

    /** List of cities that are in the boundary*/
    private THashMap<Boundary, List<City>> citiesInBoundaryMap;

    /** Ids of relation that contains information about street and houses*/
    private TLongList streetRelations;

    /** JTS in memory index of center geometries for cities*/
    private STRtree cityCenterIndex;

    /** Set of method that help to create boundary objects */
    BoundaryController boundaryFactory;
    StreetController sc;
    HouseController hc;

    // output DB file
    private File outputDb;

    // handler for tags
    private WriterAddressDefinition addressDefinition;
	
	public GeneratorAddress(WriterAddressDefinition addressDefinition, File outputDb) throws Exception {

        Logger.d(TAG, "Prepared GeneratorAddress");

        this.outputDb = outputDb;
        this.addressDefinition = addressDefinition;

        this.boundaryFactory = new BoundaryController(this);
        this.cities = new ArrayList<>();
        this.boundaries = new ArrayList<>();
        this.centerCityBoundaryMap = new BiDiHashMap<>();
        this.citiesInBoundaryMap = new THashMap<>();
        this.streetRelations = new TLongArrayList();

        cityCenterIndex = new STRtree();

        initialize();
    }

	@Override
	protected ADatabaseHandler prepareDatabase()   throws Exception{
		return new DatabaseAddress(outputDb);
	}

    @Override
    public void proceedData(ADataContainer dc) {

        this.sc = new StreetController(dc, this);
        this.hc = new HouseController(dc, this);

        Logger.i(TAG, "=== Step 0 - testinf residential ===");
        //createResidentialAreas(dc);

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

        Logger.i(TAG, "=== Step 4B - find parent cities ===");
        findParentCitiesForVillages();

        // ---- step 5 write cities to DB ----
        Logger.i(TAG, "=== Step 5 - write cities to db ===");
        insertCitiesToDB();



        // ----- Step 6 create streets from relations streets -----
        Logger.i(TAG, "=== Step 6 - create streets from relations ===");
        sc.createWayStreetFromRelations();

        // ----- step 7 create streets from ways ------
        Logger.i(TAG, "=== Step 7 - create streets from ways ===");
        sc.createWayStreetFromWays();
        Logger.i(TAG, "Create dummy streets for cities without street ===");
        //TODO create dummy street from place=locality
        ((DatabaseAddress) db).createDummyStreets();
        ((DatabaseAddress) db).buildStreetNameIndex();

        // ----- step 8 create houses ------
        Logger.i(TAG, "=== Step 8 - create houses ===");
        Logger.i(TAG, "Create houses from relations");
        hc.createHousesFromRelations(streetRelations);
        Logger.i(TAG, "Create houses from ways");
        hc.createHousesFromWays();
        Logger.i(TAG, "Create houses from nodes");
        hc.createHousesFromNodes();
        Logger.i(TAG, "Clear duplicated houses ");
        ((DatabaseAddress) db).deleteDuplicatedHouses();
        ((DatabaseAddress) db).buildHouseIndexes();

        // ----- step 9 simplify geoms ------
        Logger.i(TAG, "=== Step 9 - simplify street and city geoms ===");
        simplifyGeoms ();

        // ----- step 10 set houses as blob to streets ------
        Logger.i(TAG, "=== Step 10 - update streets, write houses data ===");
        updateStreetsWriteHouses();

        Logger.i(TAG, "Finding cities for every street way takes: " + sc.timeFindStreetCities/1000.0 + " sec" );
        Logger.i(TAG, "Finding cities only loading cities fromk DB takes: " + sc.timeLoadNereastCities /1000.0 + " sec" );
        Logger.i(TAG, "Finding cities only compare the boundaries takes: " + sc.timeFindCityTestByGeom /1000.0 + " sec" );

        Logger.i(TAG, "Joining ways and preparation for insert: " + sc.timeJoinWaysToStreets /1000.0 + " sec" );
        Logger.i(TAG, "Insert streets: " + sc.timeInsertStreetSql /1000.0 + " sec" );

        Logger.i(TAG, "Houses" );
        Logger.i(TAG, "Create parse houses: " + hc.timeCreateParseHouses /1000.0 + " sec" );
        Logger.i(TAG, "Find street for house: " + hc.timeFindStreetForHouse /1000.0 + " sec" );
        Logger.i(TAG, "Find street for house using name from DB: " + hc.timeFindStreetSelectFromDB /1000.0 + " sec" );
        Logger.i(TAG, "Find street for house using similar name: " + hc.timeFindStreetSimilarName /1000.0 + " sec" );
        Logger.i(TAG, "Find street for house using the nearest: " + hc.timeFindStreetNearest /1000.0 + " sec" );

        Logger.i(TAG, "NUm of loaded houses as byte[] to update streets: " + ((DatabaseAddress) db).housesPreparedAsBlobForStreets );

        Logger.i(TAG, "Number of found streets for houses using sql select, : " + hc.numOfStreetForHousesUsingSqlSelect);

        Logger.i(TAG, "Number of removed houses - not able to find street, : " + hc.removedHousesWithDefinedPlace);
        Logger.i(TAG, "Number of removed houses with defined addr:street name, : " + hc.removedHousesWithDefinedStreetName);
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

            String name = OsmUtils.getTagValue(node, OsmConst.OSMTagKey.NAME);

            City city = new City(cityType);
            city.setOsmId(node.getId());
            city.setName(name);
            city.setNamesInternational(OsmUtils.getNamesLangMutation(node, name));
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
        for (int i=0, size = wayIds.size(); i < size; i++) {
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
     * Try to find center city for every boundary. It also create the region places from boundaries of higher levels
     * @param dc base osm data container
     */
    private void findCenterCityForBoundary(ADataContainer dc) {

        for (Boundary boundary :  boundaries){

            String boundaryName = boundary.getName().toLowerCase();
            String altBoundaryName = boundary.getShortName().toLowerCase();

            City cityFound = null;
            if(boundary.hasAdminCenterId()) {
                for (City city : cities) {
                    if (city.getOsmId() == boundary.getAdminCenterId()) {
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
                    if (boundaryFactory.hasSimilarName(boundary, city)) {
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
                    boundary.setAdminCenterId(cityFound.getOsmId());
                    cities.add(cityFound);
                    cityCenterIndex.insert(cityFound.getCenter().getEnvelopeInternal(), cityFound);
                }
            }

            if (cityFound != null){
                // OK we have center city for boundary > put them into cache and compare priority
                registerBoundaryForCity (boundary, cityFound);
            }
            else {
               // Logger.i(TAG, "Not found any center city for boundary: "  + boundary.toString());
            }
        }
    }

    /**
     * City can be center for more boundaries. Register the best boundary for city
     * Method compare priority of previous boundary (if exist).
     * @param boundary new boundary that should registered for center city
     * @param city center city
     */
    private void registerBoundaryForCity(Boundary boundary, City city) {

        // try to obtain previous registered boundary for city
        Boundary oldBoundary = this.centerCityBoundaryMap.getValue(city);
        if (oldBoundary == null){
            //there is no registered boundary for this city > simple register it
            centerCityBoundaryMap.put(city, boundary);
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
            int oldBoundaryPriority = boundaryFactory.getCityBoundaryPriority(oldBoundary, city);
            int newBoundaryPriority = boundaryFactory.getCityBoundaryPriority(boundary, city);

            if (newBoundaryPriority < oldBoundaryPriority){
                centerCityBoundaryMap.put(city, boundary);
            }
        }
    }

    private City createMissingCity (Boundary boundary){

        City city = new City(boundary.getCityType());
        city.setOsmId(boundary.getId());
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

    /**
     * For villages neighbour or
     */
    private void findParentCitiesForVillages() {

        for (City city : cities){
            City parentCity = boundaryFactory.findParentCityForVillages(city);
            // avoid the parent that are the same as city itself

            if (parentCity != null && parentCity.getOsmId() != city.getOsmId()){
                city.setParentCity(parentCity);
            }
        }
    }

    /**************************************************/
    /*  STEP 5 - write cities into DB
    /**************************************************/

    private void insertCitiesToDB() {

        City city;
        for (int i = 0, size = cities.size() ; i < size;  i++){
            city = cities.get(i);
            Boundary boundary = centerCityBoundaryMap.getValue(city);
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
            Boundary boundary = centerCityBoundaryMap.getValue(city);
            if (boundary != null){
                databaseAddress.simplifyCityGeom(city, boundary);
            }
        }

        ((DatabaseAddress) db).buildCityBoundaryIndex();
        long time = System.currentTimeMillis() - start;
        Logger.i(TAG, "SimplifyGeoms takes: " + time/1000.0 + " sec" );
    }

    /**************************************************/
    /*  STEP 10 - Set house blob data into streets
    /**************************************************/

    private void updateStreetsWriteHouses() {
        DatabaseAddress databaseAddress = getDatabaseAddress();
        TIntArrayList pathStreetIds = databaseAddress.getPathStreetIds();
        int lastStreetId = databaseAddress.getStreetIdSequence();

        for (int id = 0; id <= lastStreetId; id++ ){

            byte[] data = databaseAddress.selectHousesInStreet(id);

            if (data == null || data.length == 0){
                if (pathStreetIds.contains(id)){
                    // it path or track without any house > remove this street
                    // TODO improve compare distance form the city

                    databaseAddress.deleteStreet(id);
                }
                continue;
            }

            databaseAddress.updateStreetSetHouseBlob(id, data);
        }
    }


    /**************************************************/
    /*  TEMPORARY
    /**************************************************/


    private void createResidentialAreas(ADataContainer dc) {

        List<Geometry> polygons = new ArrayList<>();

        List<Geometry> polytmp = new ArrayList<>();

        TLongList wayIds = dc.getWayIds();
        for (int i=0, size = wayIds.size(); i < size; i++) {
            Way way = dc.getWayFromCache(wayIds.get(i));
            Polygon poly = parseForResidential(dc, way);
            if (poly == null || !poly.isValid()){
                continue;
            }

            polytmp.add(poly);

            if (polytmp.size() > 10000){
                //UnaryUnionOp unaryUnionOp = new UnaryUnionOp(polytmp);
                //Geometry geomUnion = unaryUnionOp.union();
                //polygons.add(DouglasPeuckerSimplifier.simplify(geomUnion, 0.0001));
                //polytmp.clear();
            }
        }

        // add rest of building into joined polygons
        polygons.addAll(polytmp);
        long start = System.currentTimeMillis();
        UnaryUnionOp unaryUnionOp = new UnaryUnionOp(polygons);
        Geometry finalGeom = unaryUnionOp.union();
        Logger.i(TAG, "Union takes: " + (System.currentTimeMillis() - start) / 1000.0);

        int size = finalGeom.getNumGeometries();



        double minArea =  Utils.metersToDeg(300) *  Utils.metersToDeg(300);
        List<Polygon> residentialPolygons = new ArrayList<>();

        for (int i=0; i < size; i++){
            Polygon polygon = (Polygon) finalGeom.getGeometryN(i);

            if (polygon.getArea() > minArea ){
                residentialPolygons.add(polygon);
            }
        }

        GeometryFactory geometryFactory = new GeometryFactory();
        MultiPolygon multiPoly = geometryFactory.createMultiPolygon(residentialPolygons.toArray(new Polygon[0]));

        Geometry geom = DouglasPeuckerSimplifier.simplify(multiPoly, 0.0001);

        com.asamm.osmTools.utils.Utils.writeStringToFile(new File("residential.geojson"),Utils.geomToGeoJson(geom) ,false);

    }


    private Polygon parseForResidential (ADataContainer dc, Way way){

        float bufferM = 100;
        Polygon polygon = null;

        String landuse = OsmUtils.getTagValue(way, OsmConst.OSMTagKey.LANDUSE);
        String building = OsmUtils.getTagValue(way, OsmConst.OSMTagKey.BUILDING);
        if (Utils.objectEquals(landuse, "residential")){
            WayEx wayEx = dc.getWay(way.getId());
            if (wayEx == null || !wayEx.isValid()){
                return null;
            }
            if (wayEx.isClosed()){
                polygon =  GeomUtils.createPolyFromOuterWay(wayEx);
            }
        }

        if (building != null) {
            WayEx wayEx = dc.getWay(way.getId());
            if (wayEx == null || !wayEx.isValid()) {
                return null;
            }
            if (wayEx.isClosed()){
                polygon =  GeomUtils.createPolyFromOuterWay(wayEx);
            }
            double bufferD = Utils.metersToDeg(bufferM);

            polygon =  GeomUtils.createPolyFromOuterWay(wayEx);
            if (polygon != null){
                polygon = (Polygon) polygon.buffer(bufferD);
            }
        }
        return polygon;
    }




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
                //Logger.i(TAG, "MAx num of resize reached");
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

    public BiDiHashMap<City, Boundary> getCenterCityBoundaryMap() {
        return centerCityBoundaryMap;
    }

    public THashMap<Boundary, List<City>> getCitiesInBoundaryMap() {
        return citiesInBoundaryMap;
    }

    public DatabaseAddress getDatabaseAddress (){
        return (DatabaseAddress) db;
    }

    public TLongList getStreetRelations() {
        return streetRelations;
    }
}
