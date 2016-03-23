package com.asamm.osmTools.generatorDb;

import com.asamm.osmTools.generatorDb.address.*;
import com.asamm.osmTools.generatorDb.data.OsmConst;
import com.asamm.osmTools.generatorDb.data.WayEx;
import com.asamm.osmTools.generatorDb.dataContainer.ADataContainer;
import com.asamm.osmTools.generatorDb.db.ADatabaseHandler;
import com.asamm.osmTools.generatorDb.db.DatabaseAddress;
import com.asamm.osmTools.generatorDb.index.IndexController;
import com.asamm.osmTools.generatorDb.utils.*;
import com.asamm.osmTools.utils.Logger;
import com.vividsolutions.jts.geom.*;
import com.vividsolutions.jts.index.strtree.STRtree;
import com.vividsolutions.jts.operation.union.UnaryUnionOp;
import com.vividsolutions.jts.simplify.DouglasPeuckerSimplifier;
import gnu.trove.list.TLongList;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.hash.THashMap;
import org.openstreetmap.osmosis.core.domain.v0_6.Node;
import org.openstreetmap.osmosis.core.domain.v0_6.Relation;
import org.openstreetmap.osmosis.core.domain.v0_6.Way;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class GeneratorAddress extends AGenerator {
	
	private static final String TAG = GeneratorAddress.class.getSimpleName();


    private List<Boundary> boundaries;

    /** Set of method that help to create boundary objects */
    CityController cc;
    StreetController sc;
    HouseController hc;

    // output address DB file
    private File outputDb;

    // handler for tags
    private WriterAddressDefinition addressDefinition;
	
	public GeneratorAddress(WriterAddressDefinition addressDefinition) throws Exception {

        Logger.d(TAG, "Prepared GeneratorAddress");
        this.outputDb = addressDefinition.getConfAddress().getFileDatabase();
        this.addressDefinition = addressDefinition;

        this.boundaries = new ArrayList<>();

        initialize();
    }

	@Override
	protected ADatabaseHandler prepareDatabase()   throws Exception{
		return new DatabaseAddress(outputDb);
	}

    @Override
    public void proceedData(ADataContainer dc) {

        this.cc = new CityController(dc, this.getDatabaseAddress(), addressDefinition);
        this.sc = new StreetController(dc, this.getDatabaseAddress(), addressDefinition);
        this.hc = new HouseController(dc, this.getDatabaseAddress(), addressDefinition);

        Logger.i(TAG, "=== Step 0 - testing residential ===");
        //createResidentialAreas(dc);


        // ---- step 1 find all city places -----
        Logger.i(TAG, "=== Step 1 - load city places ===");
        loadCityPlaces(dc);
        Utils.printUsedMemory();

        // ---- step 2 create boundaries -----
        Logger.i(TAG, "=== Step 2 - create boundaries ===");
        loadBoundariesRegions(dc);
        Utils.printUsedMemory();

        // ---- step 3 find center city for boundary -----
        Logger.i(TAG, "=== Step 3 - find center city for boundary ===");
        findCenterCityForBoundary(dc);
        Utils.printUsedMemory();

        // ---- step 4 create list of cities that are in boundary ----
        Logger.i(TAG, "=== Step 4 - find all cities inside boundaries ===");
        findAllCitiesForBoundary(dc);
        Utils.printUsedMemory();

        Logger.i(TAG, "=== Step 4B - find parent cities ===");
        findParentCitiesAndRegions(dc);
        Utils.printUsedMemory();

        // ---- step 5 write cities to DB ----
        Logger.i(TAG, "=== Step 5 - write cities to db ===");
        insertCitiesToDB(dc);
        Utils.printUsedMemory();

        // ----- Step 6 - 7 create streets from relations streets -----
        createStreets(dc);
        Utils.printUsedMemory();

        Logger.i(TAG, "Joining ways and preparation for insert: " + sc.timeJoinWaysToStreets /1000.0 + " sec" );
        Logger.i(TAG, "Finding cities for street: " + sc.timeFindStreetCities /1000.0 + " sec" );
        Logger.i(TAG, "Finding cities loading first 30 cities: " + sc.timeLoadNereastCities /1000.0 + " sec" );
        Logger.i(TAG, "Finding cities: test by geom: " + sc.timeFindCityTestByGeom /1000.0 + " sec" );
        Logger.i(TAG, "Finding cities: test by distance: " + sc.timeFindCityTestByDistance /1000.0 + " sec" );
        Logger.i(TAG, "Finding cities: test by distance: " + sc.timeFindCityFindNearest /1000.0 + " sec" );


        // ----- step 8 create houses ------
        Logger.i(TAG, "=== Step 8 - create houses ===");
        Logger.i(TAG, "Create houses from relations");
        hc.createHousesFromRelations();
        Utils.printUsedMemory();
        Logger.i(TAG, "Create houses from ways");
        hc.createHousesFromWays();
        Utils.printUsedMemory();
        Logger.i(TAG, "Create houses from nodes");
        hc.createHousesFromNodes();
        Utils.printUsedMemory();

        Logger.i(TAG, "Create houses for unnamed streets");
        dc.clearWayStreetCache();
        hc.processHouseWithoutStreet(sc);


        Logger.i(TAG, "Clear duplicated houses ");
        ((DatabaseAddress) db).deleteDuplicatedHouses();
        ((DatabaseAddress) db).buildHouseIndexes(); // CREATE INDEX BECAUSE STREET ID

        // ----- step 10 simplify geoms ------
        Logger.i(TAG, "=== Step 10 - simplify street and city geoms ===");
        simplifyGeoms (dc);

        // ----- step 11 simplify geoms ------
        Logger.i(TAG, "=== Step 11 - clear unused data ===");
        clearUnusedData(dc);


        // ----- step 12 set houses as blob to streets ------
        Logger.i(TAG, "=== Step 12 - update streets, write houses data ===");
        updateStreetsWriteHouses();

        Logger.i(TAG, "Finding cities for every street way takes: " + sc.timeFindStreetCities/1000.0 + " sec" );
        Logger.i(TAG, "Finding cities only loading cities fromk DB takes: " + sc.timeLoadNereastCities /1000.0 + " sec" );
        Logger.i(TAG, "Finding cities only compare the boundaries takes: " + sc.timeFindCityTestByGeom /1000.0 + " sec" );

        Logger.i(TAG, "Joining ways and preparation for insert: " + sc.timeJoinWaysToStreets /1000.0 + " sec" );

        Logger.i(TAG, "Houses" );
        Logger.i(TAG, "Process Houses without street: " + hc.timeProcessHouseWithoutStreet /1000.0 + " sec" );
        Logger.i(TAG, "House without street group by boundary: " + hc.timeGroupByCity /1000.0 + " sec" );
        Logger.i(TAG, "House buffer geoms: " + hc.timeGroupByCity /1000.0 + " sec" );
        Logger.i(TAG, "House without street find cut street geom by hauses: " + hc.timeCutWayStreetByHouses /1000.0 + " sec" );
        Logger.i(TAG, "House without street find cut street CONVEX HULL: " + hc.timeCutWaysConvexHull /1000.0 + " sec" );
        Logger.i(TAG, "House without street find cut street INTERSECTION: " + hc.timeCutWaysIntersection /1000.0 + " sec" );
        Logger.i(TAG, "House without street nearest streets for grouped houses: " + hc.timeFindNearestForGroup /1000.0 + " sec" );
        Logger.i(TAG, "House without street find the nearest street for house: " + hc.timeFindNearestForGroupedHouse /1000.0 + " sec" );
        Logger.i(TAG, "House create unnamed waystreets: " + hc.timeCreateUnamedStreetGeom /1000.0 + " sec" );

        Logger.i(TAG, "NUm of loaded houses as byte[] to update streets: " + ((DatabaseAddress) db).housesPreparedAsBlobForStreets );

        Logger.i(TAG, "Number of found streets for houses using sql select, : " + hc.numOfStreetForHousesUsingSqlSelect);

        Logger.i(TAG, "Number of removed houses - not able to find street, : " + hc.removedHousesWithDefinedPlace);
        Logger.i(TAG, "Number of removed houses with defined addr:street name, : " + hc.removedHousesWithDefinedStreetName);
    }



    /**************************************************/
    /*  STEP 1 - Create cities
    /**************************************************/

    void loadCityPlaces(ADataContainer dc) {
        cc.createCities();
    }

    /**************************************************/
    /*  STEP 2 - Create boundaries and regions
    /**************************************************/

    void loadBoundariesRegions(ADataContainer dc) {

        // create boundaries from relation
        TLongList relationIds = dc.getRelationIds();

        for (int i=0, size = relationIds.size(); i < size; i++) {
            long relationId = relationIds.get(i);

//            if (relationId == 56106 || relationId == 2135916){
//                Logger.i(TAG, "Start process relation id: " + relationId);
//            }
            Relation relation = dc.getRelationFromCache(relationId);
            Boundary boundary = cc.create(relation, false);
            checkRegisterBoundary(boundary);
        }

        TLongList wayIds = dc.getWayIds();
        for (int i=0, size = wayIds.size(); i < size; i++) {
            Way way = dc.getWayFromCache(wayIds.get(i));
            Boundary boundary = cc.create(way, false);
            checkRegisterBoundary(boundary);
        }

        Logger.i(TAG, "loadBoundariesRegions: " + boundaries.size() + " boundaries were created and loaded into cache");
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

        if (boundary.getAdminLevel() == addressDefinition.getRegionAdminLevel()) {
            // boundary has corect admin level for regions
            createRegion(boundary);
        }

        if (addressDefinition.isCityAdminLevel(boundary.getAdminLevel())
                || addressDefinition.isMappedBoundaryId(boundary.getId())){
            // from this boundary can be created the city area
            boundaries.add(boundary);
        }
        else {
            //Logger.i(TAG, "checkRegisterBoundary: boundary does not have admin level for city: " + boundary.toString());
        }
    }

    /**
     * Test if boundary can be region (by admin level). If yes that region is written into database address and
     * also into index for later when  look for region for cities
     * @param boundary to test and create region
     */
    private void createRegion(Boundary boundary){
        Region region = new Region(boundary.getId(), boundary.getName(), boundary.getNamesInternational(), boundary.getGeom());

        IndexController.getInstance().insertRegion(region);
        ((DatabaseAddress)db).insertRegion(region);

    }



    /**************************************************/
    /*  STEP 3 - find center place for boundary
    /**************************************************/

    /**
     * Try to find center city for every boundary.
     * @param dc base osm data container
     */
    private void findCenterCityForBoundary(ADataContainer dc) {

        for (Boundary boundary :  boundaries){

            String boundaryName = boundary.getName().toLowerCase();
            String altBoundaryName = boundary.getShortName().toLowerCase();
            Collection<City> cities = dc.getCities();

            City cityFound = null;
            // Test city by custom mapper definition
            if (addressDefinition.isMappedBoundaryId(boundary.getId())){
                long mappedCityId = addressDefinition.getMappedCityIdForBoundary(boundary.getId());
                cityFound = dc.getCity(mappedCityId); // can be null

                if (cityFound != null){
                    Logger.i(TAG, "Founded city by custom mapper ; city " + cityFound.toString() +
                            "\n boundary:  " + boundary.toString());
                }
            }

            // try to load city based on admin center id (if defined)
            if(cityFound == null && boundary.hasAdminCenterId()) {
                cityFound = dc.getCity(boundary.getAdminCenterId()); // can be null
            }

            if(cityFound == null) {
                for (City city : cities) {
                    if (boundaryName.equalsIgnoreCase(city.getName()) || altBoundaryName.equalsIgnoreCase(city.getName())){
                        if (boundary.getGeom().contains(city.getCenter())) {
                            //Logger.i(TAG, "City were founded by name and contains for boundary: "+boundary.getId()+ " city: " + city.toString());
                            if (cc.canBeSetAsCenterCity (boundary, city)){
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
                    if (cc.hasSimilarName(boundary, city)) {
                        if (boundary.getGeom().contains(city.getCenter())) {
                            // test if city is some small village > in this case do not use it
                            if (cc.canBeSetAsCenterCity (boundary, city)){
                                cityFound = city;
                                break;
                            }
                        }
                    }
                }
            }

            // there is no city for this boundary > try to guess and create new one from boundary informations
            if (cityFound == null && boundary.hasCityType()){

                cityFound = createMissingCity(boundary);
                if (cityFound.isValid()){
                    boundary.setAdminCenterId(cityFound.getOsmId());
                    dc.addCity(cityFound);
                    IndexController.getInstance().insertCity(cityFound.getCenter().getEnvelopeInternal(), cityFound);
                }
            }

            if (cityFound != null){
                // OK we have center city for boundary > put them into cache and compare priority
                registerBoundaryForCity (dc, boundary, cityFound);
            }
            else {
               //Logger.i(TAG, "Not found any center city for boundary: "  + boundary.toString());
            }
        }
    }

    /**
     * City can be center for more boundaries. createRegion the best boundary for city
     * Method compare priority of previous boundary (if exist).
     * @param boundary new boundary that should registered for center city
     * @param city center city
     */
    private void registerBoundaryForCity(ADataContainer dc, Boundary boundary, City city) {

        // try to obtain previous registered boundary for city
        BiDiHashMap<City, Boundary> centerCityBoundaryMap = dc.getCenterCityBoundaryMap();
        Boundary oldBoundary = centerCityBoundaryMap.getValue(city);
        if (oldBoundary == null){
            //there is no registered boundary for this city > simple register it
//            if (city.getOsmId() == 60806241){
//                Logger.i(TAG, "Put A boundary " + boundary.toString());
//            }
            centerCityBoundaryMap.put(city, boundary);
        }
        else if (oldBoundary.getAdminLevel() == boundary.getAdminLevel()
                && oldBoundary != boundary
                && oldBoundary.getName().equalsIgnoreCase(boundary.getName())){
            // this condition is inspiration from OSMand probably can happen that there
            // are to boundaries for the same city
            MultiPolygon newBounds = GeomUtils.fixInvalidGeom(oldBoundary.getGeom().union(boundary.getGeom()));
            oldBoundary.setGeom(newBounds);
        }

        else {
            int oldBoundaryPriority = cc.getCityBoundaryPriority(oldBoundary, city);
            int newBoundaryPriority = cc.getCityBoundaryPriority(boundary, city);

            if (newBoundaryPriority < oldBoundaryPriority){
                centerCityBoundaryMap.put(city, boundary);
            }
        }
    }

    private City createMissingCity (Boundary boundary){

        //Logger.i(TAG, "Create missing city for boundary: "  + boundary.getName());

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
    private void findAllCitiesForBoundary(ADataContainer dc) {

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

    /**
     * For all cities find parent city (if any) and region is in
     */
    private void findParentCitiesAndRegions(ADataContainer dc) {
        Collection<City> cities = dc.getCities();
        for (City city : cities){
            City parentCity = cc.findParentCity(dc, city);
            city.setParentCity(parentCity); // can return null

            Region parentRegion = cc.findParentRegion(city);
            if (parentRegion != null){
                city.setRegion(parentRegion);
            }
        }
    }



    /**************************************************/
    /*  STEP 5 - write cities into DB
    /**************************************************/

    private void insertCitiesToDB(ADataContainer dc) {
        BiDiHashMap<City, Boundary> centerCityBoundaryMap = dc.getCenterCityBoundaryMap();
        Collection<City> cities = dc.getCities();

        for (City city : cities){
            Boundary boundary = centerCityBoundaryMap.getValue(city);
            if (boundary != null){
                city.setGeom(boundary.getGeom());
            }
            ((DatabaseAddress) db).insertCity(city, boundary);
        }


        // need to build index for names right now because can be used in processing of streets
        ((DatabaseAddress) db).buildCityNamesIndexes();
    }

    /**************************************************/
    /*  STEP 6 - 7 - Create streets
    /**************************************************/

    private void createStreets (ADataContainer dc){

        Logger.i(TAG, "=== Step 6 - create streets from relations ===");
        sc.createWayStreetFromRelations();

        // ----- step 7 create streets from ways ------
        Logger.i(TAG, "=== Step 7 - create streets from ways ===");
        sc.createWayStreetFromWays();


        sc.joinWayStreets(new StreetController.OnJoinStreetListener() {
            @Override
            public void onJoin(Street streetToInsert) {

                List<Street> streetsToInsert = new ArrayList<Street>();

                // SPLIT BASED ON TOP LEVEL CITIES GEOMS
                Envelope envelope = streetToInsert.getGeometry().getEnvelopeInternal();
                double diagonalLength = Utils.getDistance(
                        envelope.getMinY(), envelope.getMinX(), envelope.getMaxY(), envelope.getMaxX());

                if (diagonalLength > Const.MAX_DIAGONAL_STREET_LENGTH){
                    // street is too long try to separate it based on villages, town or cities geom
                    streetsToInsert = sc.splitGeomByParentCities(streetToInsert);
                }
                else if (streetToInsert.getGeometry().getNumGeometries() > 1){
                    // street geom has more parts. Maybe it is two different streets > try to separate them
                    streetsToInsert = sc.splitToCityParts (streetToInsert);
                }
                else {
                    streetsToInsert.add(streetToInsert);
                }

                // write to DB
                for (Street street : streetsToInsert){
                    //check again the diagonal length
                    envelope = street.getGeometry().getEnvelopeInternal();
                    diagonalLength = Utils.getDistance(
                            envelope.getMinY(), envelope.getMinX(), envelope.getMaxY(), envelope.getMaxX());
                    if (diagonalLength <= Const.MAX_DIAGONAL_STREET_LENGTH) {
                        ((DatabaseAddress) db).insertStreet(street);
                    }
                }
            }
        });


        //Logger.i(TAG, "Create dummy streets for cities without street ===");
        //((DatabaseAddress) db).createDummyStreets();
        ((DatabaseAddress) db).buildStreetNameIndex();
    }

    /**************************************************/
    /*  STEP 10 - simplify geoms
    /**************************************************/

    private void simplifyGeoms(ADataContainer dc) {


        DatabaseAddress databaseAddress = getDatabaseAddress();
        long start = System.currentTimeMillis();

        databaseAddress.simplifyStreetGeoms();

        long time = System.currentTimeMillis() - start;
        Logger.i(TAG, "SimplifyGeoms takes: " + time/1000.0 + " sec" );
    }

    /**
     * ************************************************/
    /*  STEP 11 - Clear unused object
    /**************************************************/

    /**
     * Clear some cached object that no more needed.
     * @param dc container of some objects
     */
    private void clearUnusedData(ADataContainer dc) {

        boundaries = null;
        IndexController.getInstance().clearAll ();
        dc.clearAll();

    }

    /**************************************************/
    /*  STEP 12 - Set house blob data into streets
    /**************************************************/

    private void updateStreetsWriteHouses() {
        DatabaseAddress databaseAddress = getDatabaseAddress();
        TIntArrayList pathStreetIds = databaseAddress.getPathStreetIds();
        int lastStreetId = databaseAddress.getStreetIdSequence();

        byte[] data = null;
        Street street = null;

        long timeWriteHousesSelectStreet = 0;
        long timeWriteHousesCreateHouseBlob = 0;
        long timeWriteHousesUpdateStreet = 0;
        long timeWriteHousesDeleteStreet = 0;


        for (int id = 0; id <= lastStreetId; id++ ){

            // load street from database
            long start = System.currentTimeMillis();
            street = databaseAddress.selectStreet(id);
            timeWriteHousesSelectStreet += System.currentTimeMillis() - start;

            if (street == null){
                continue;
            }

            if (street.getGeometry() == null){
                Logger.w(TAG, "updateStreetsWriteHouses: selected street from DB is not valid: "+
                    "\n street: " + street.toString());
                continue;
            }

            // load all houses for this street and create blob from them
            start = System.currentTimeMillis();
            data = databaseAddress.createHousesDTOblob(street);
            timeWriteHousesCreateHouseBlob += System.currentTimeMillis() - start;

            if (data == null || data.length == 0){
                start = System.currentTimeMillis();
                if (pathStreetIds.contains(id)){
                    // reduce named path and tracks. It street is path or track street without any house > remove it
                    // TODO improve compare distance form the city
                    databaseAddress.deleteStreet(id);
                }
                timeWriteHousesDeleteStreet += System.currentTimeMillis() - start;
                continue;
            }
            start = System.currentTimeMillis();
            databaseAddress.updateStreetHouseBlob(id, data);
            timeWriteHousesUpdateStreet += System.currentTimeMillis() - start;


        }
        databaseAddress.finalizeUpdateStreetHouseBlob();


        Logger.i(TAG, "Write house blobs, select streets : " + timeWriteHousesSelectStreet /1000.0 + " sec" );
        Logger.i(TAG, "Write house blobs, create blobs : " + timeWriteHousesCreateHouseBlob /1000.0 + " sec" );
        Logger.i(TAG, "Create house blobs, select houses : " + databaseAddress.timeDtoSelectHouses /1000.0 + " sec" );
        Logger.i(TAG, "Create house blobs, de serialize house : " + databaseAddress.timeDtoDeSerializeHouse /1000.0 + " sec" );
        Logger.i(TAG, "Create house blobs, Crate DTO: " + databaseAddress.timeDtoCreateDTO /1000.0 + " sec" );
        Logger.i(TAG, "Create house blobs, zip data: " + databaseAddress.timeDtoZipData /1000.0 + " sec" );
        Logger.i(TAG, "Write house blobs, delete empty path  streets : " + timeWriteHousesDeleteStreet /1000.0 + " sec" );
        Logger.i(TAG, "Write house blobs, insert write  streets : " + timeWriteHousesUpdateStreet /1000.0 + " sec" );
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

        com.asamm.osmTools.utils.Utils.writeStringToFile(new File("residential.geojson"), GeomUtils.geomToGeoJson(geom) ,false);

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
                polygon =  GeomUtils.createPolyFromOuterWay(wayEx, true);
            }
        }

        if (building != null) {
            WayEx wayEx = dc.getWay(way.getId());
            if (wayEx == null || !wayEx.isValid()) {
                return null;
            }
            if (wayEx.isClosed()){
                polygon =  GeomUtils.createPolyFromOuterWay(wayEx,true);
            }
            double bufferD = Utils.metersToDeg(bufferM);

            polygon =  GeomUtils.createPolyFromOuterWay(wayEx, true);
            if (polygon != null){
                polygon = (Polygon) polygon.buffer(bufferD);
            }
        }
        return polygon;
    }


    /**************************************************/
    /*              Getters
    /**************************************************/

    public DatabaseAddress getDatabaseAddress (){
        return (DatabaseAddress) db;
    }

}
