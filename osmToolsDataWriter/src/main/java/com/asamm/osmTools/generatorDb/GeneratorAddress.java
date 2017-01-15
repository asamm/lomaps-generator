package com.asamm.osmTools.generatorDb;

import com.asamm.osmTools.generatorDb.address.*;
import com.asamm.osmTools.generatorDb.dataContainer.ADataContainer;
import com.asamm.osmTools.generatorDb.db.ADatabaseHandler;
import com.asamm.osmTools.generatorDb.db.DatabaseAddress;
import com.asamm.osmTools.generatorDb.index.IndexController;
import com.asamm.osmTools.generatorDb.utils.*;
import com.asamm.osmTools.utils.Logger;
import com.vividsolutions.jts.geom.*;
import gnu.trove.list.array.TIntArrayList;

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
        ResidentialAreaCreator residentialC = new ResidentialAreaCreator(dc);
        residentialC.generate();


        // TODO REMOVE
        if (1 == 1){
            return;
        }

        // ---- step 1 find all city places -----
        Logger.i(TAG, "=== Step 1 - load city places ===");
        cc.createCities();

        // ---- step 2 create boundaries -----
        Logger.i(TAG, "=== Step 2 - create boundaries ===");
        cc.createBoundariesRegions();

        // ---- step 3 find center city for boundary -----
        Logger.i(TAG, "=== Step 3 - find center city for boundary ===");
        cc.findCenterCityForBoundary();

        // ---- step 4 create list of cities that are in boundary ----
        Logger.i(TAG, "=== Step 4 - find all cities inside boundaries ===");
        cc.findAllCitiesForBoundary();

        Logger.i(TAG, "=== Step 4B - find parent cities ===");
        cc.findParentCitiesAndRegions();

        // ---- step 5 write cities to DB ----
        Logger.i(TAG, "=== Step 5 - write cities to db ===");
        insertRegionsToDB(dc);
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
    /*  STEP 5 - write cities into DB
    /**************************************************/

    private void insertRegionsToDB(ADataContainer dc) {

        List<Region> regions = dc.getRegions();
        for (Region region : regions){
            ((DatabaseAddress)db).insertRegion(region);
        }
    }

    private void insertCitiesToDB(ADataContainer dc) {
        BiDiHashMap<City, Boundary> centerCityBoundaryMap = dc.getCenterCityBoundaryMap();
        Collection<City> cities = dc.getCities();

        for (City city : cities){
            Boundary boundary = centerCityBoundaryMap.getValue(city);
            if (boundary != null){

                // Combine boundary values (geom, population, etc.) with the city
                city.combineWithBoundary(boundary);
            }

            // write city geometry or city center into memory index
            IndexController.getInstance().insertCityGeom(city);

            // insert city into database
            ((DatabaseAddress) db).insertCity(city);
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

                if (diagonalLength > Const.ADR_MAX_DIAGONAL_STREET_LENGTH){
                    // street is too long try to separate it based on villages, town or cities geom
                    streetsToInsert = sc.splitLongStreet(streetToInsert);
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
                    if (diagonalLength <= Const.ADR_MAX_DIAGONAL_STREET_LENGTH) {
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



    /**************************************************/
    /*              Getters
    /**************************************************/

    public DatabaseAddress getDatabaseAddress (){
        return (DatabaseAddress) db;
    }

}
