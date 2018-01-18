package com.asamm.osmTools.generatorDb;

import com.asamm.osmTools.generatorDb.address.Boundary;
import com.asamm.osmTools.generatorDb.address.CityController;
import com.asamm.osmTools.generatorDb.address.Region;
import com.asamm.osmTools.generatorDb.data.OsmConst;
import com.asamm.osmTools.generatorDb.dataContainer.ADataContainer;
import com.asamm.osmTools.generatorDb.db.ADatabaseHandler;
import com.asamm.osmTools.generatorDb.db.DatabaseStoreMysql;
import com.asamm.osmTools.generatorDb.input.definition.WriterCountryBoundaryDefinition;
import com.asamm.osmTools.generatorDb.plugin.ConfigurationCountry;
import com.asamm.osmTools.generatorDb.utils.GeomUtils;
import com.asamm.osmTools.generatorDb.utils.Language;
import com.asamm.osmTools.generatorDb.utils.OsmUtils;
import com.asamm.osmTools.generatorDb.utils.Utils;
import com.asamm.osmTools.utils.Logger;
import com.vividsolutions.jts.geom.*;
import com.vividsolutions.jts.io.ParseException;
import com.vividsolutions.jts.io.WKTReader;
import com.vividsolutions.jts.simplify.DouglasPeuckerSimplifier;
import gnu.trove.list.TLongList;
import gnu.trove.map.hash.THashMap;
import org.openstreetmap.osmosis.core.domain.v0_6.*;

import java.io.File;
import java.nio.charset.Charset;
import java.util.*;

import static com.asamm.osmTools.generatorDb.plugin.ConfigurationCountry.CountryConf;

/**
 * Created by voldapet on 2016-02-22 .
 */
public class GeneratorCountryBoundary extends AGenerator{

    private static final String TAG = GeneratorCountryBoundary.class.getSimpleName();

    /**
     * File to write country boundary geometry
     */
   // private File outputGeomFile;

    private Map<CountryConf, Boundary> countryBoundaryMap;

    private Map<CountryConf, Region> continentsMap;


    /**
     * Definition for generator
     */
    private WriterCountryBoundaryDefinition wcbDefinition;


    public GeneratorCountryBoundary (WriterCountryBoundaryDefinition wcbDefinition){

        this.wcbDefinition = wcbDefinition;
        countryBoundaryMap = new HashMap<>();
    }


    @Override
    protected ADatabaseHandler prepareDatabase() throws Exception {

        // nothing to do for this generator
        return null;
    }

    @Override
    public void proceedData(ADataContainer dc) {
        Logger.i(TAG, "=== Start country boundary process data ===");

        // ccreate continents
        createContinents(dc);

        createBoundaries(dc);

        // SAVE TO GEOJSON

        if (wcbDefinition.getConfigurationCountry().getStorageType() == ConfigurationCountry.StorageType.GEOJSON){
            Logger.i(TAG, "= Write data to geojson files=");
            writeBoundariesToGeoJson();
        }

        // SAVE TO DATABASE
        else if (wcbDefinition.getConfigurationCountry().getStorageType() == ConfigurationCountry.StorageType.STORE_REGION_DB){
            Logger.i(TAG, "= Write data to Locus store database=");
            DatabaseStoreMysql db = new DatabaseStoreMysql();
            //db.cleanTables();

            insertContinentsToGeoDatabase(db);

            insertBoundariesToGeoDatabase (db);

            db.destroy();
        }
        testNotCreatedBoundaries ();
    }



    /**
     * Create boundaries that has name as required countries
     */
    private void createBoundaries (ADataContainer dc){

        List<CountryConf> countriesConf = wcbDefinition.getConfigurationCountry().getCountriesConf();
        CityController cityController = CityController.createForCountryBound(dc);
        TLongList relationIds = dc.getRelationIds();
        Boundary foundedBoundary = null;

        for (int i=0, size = relationIds.size(); i < size; i++) {
            long relationId = relationIds.get(i);

            Relation relation = dc.getRelationFromCache(relationId);
            if (relation == null) {
                continue;
            }

            Boundary boundary = cityController.createBoundary(relation, true);

            if (boundary == null || boundary.getAdminLevel() <= 0 || !boundary.isValid()){
                if (boundary != null) {
                    Logger.i(TAG, "Boundary is not valid: " + boundary.getName());
                }
                continue;

            }

            //if (boundary.getAdminLevel() == wcbDefinition.getConfigurationCountry().getAdminLevel()){
            // use only boundaries that are same or smaller level then needed level for region boundaries
           // Logger.i(TAG, "Process country boundary for: " + boundary.getName());

            // test if any country configuration has such name
            for (CountryConf countryConf : countriesConf){

                if (hasBoundaryNameForCountry(boundary, countryConf.getCountryName())){
                    // this boundary has name as requested country; test if exist any boundary of same name from previous
                    Boundary boundaryOld = countryBoundaryMap.get(countryConf);

                    if (boundaryOld == null){
                        // no boundary was found yet > use this one
                        countryBoundaryMap.put(countryConf, boundary);
                    }
                    else {

                        if (boundaryOld.getAdminLevel() > boundary.getAdminLevel()){
                            // previous boundary is lower admin level (replace with new one)
                            countryBoundaryMap.put(countryConf, boundary);
                        }
                        else if (boundaryOld.getAdminLevel() == boundary.getAdminLevel()){
                            // join geom of previous boundary with new one
                            MultiPolygon unionMp = GeomUtils.unionMultiPolygon(boundaryOld.getGeom(), boundary.getGeom());
                            boundaryOld.setGeom(unionMp);
                        }
                    }
                }
            }

        }
    }

    /**
     * Store created boundaries into GeoJson file
     */
    private void writeBoundariesToGeoJson() {

        for (Map.Entry<CountryConf, Boundary> entry : countryBoundaryMap.entrySet()){
            // simplify boundary to write
            MultiPolygon mpSimpl = GeomUtils.simplifyMultiPolygon(entry.getValue().getGeom(), 200);

            //buffer simplified geom because after simplification exits passage between orig country border and simplified boundary
            MultiPolygon mpBuf = GeomUtils.bufferGeom(mpSimpl, 100);
            mpSimpl = GeomUtils.simplifyMultiPolygon(mpBuf, 100);

            // write founded boundary to file
            File geoJsonFile = entry.getKey().getFileGeom();
            String geoJsonStr = GeomUtils.geomToGeoJson(mpSimpl);
            com.asamm.osmTools.utils.Utils.writeStringToFile(geoJsonFile, geoJsonStr, false);

            Logger.i(TAG, "Write geometry geojson file into boundary file: " + geoJsonFile.getAbsolutePath());
        }
    }

    /**
     * Insert create continent regions into mysql database
     */
    private void insertContinentsToGeoDatabase(DatabaseStoreMysql db) {

        db = new DatabaseStoreMysql();

        if (continentsMap == null) {
            return;
        }

        for (Map.Entry<CountryConf, Region> entry : continentsMap.entrySet()) {

            db.insertRegion(entry.getValue(), entry.getKey());
        }

        // close db connection
        db.destroy();
    }

    /**
     * Store created boundaries into Locus Store geo database
     */
    private void insertBoundariesToGeoDatabase(DatabaseStoreMysql db)  {

        db = new DatabaseStoreMysql();

        for (Map.Entry<CountryConf, Boundary> entry : countryBoundaryMap.entrySet()) {

            Boundary boundary = entry.getValue();

            CountryConf countryConf = entry.getKey();

            // simplify boundary to write
            MultiPolygon mpSimpl = GeomUtils.simplifyMultiPolygon(boundary.getGeom(), 200);

            //buffer simplified geom because after simplification exits passage between orig country border and simplified boundary
            MultiPolygon mpBuf = GeomUtils.bufferGeom(mpSimpl, 100);
            mpSimpl = GeomUtils.simplifyMultiPolygon(mpBuf, 100);

            // hack to have english name in multilangual names names
            if (boundary.getNameLangs().get(Language.ENGLISH.getCode()) == null){
                boundary.getNameLangs().put(Language.ENGLISH.getCode(), countryConf.getCountryName());
            }


            Region region = new Region(
                    boundary.getId(),
                    boundary.getEntityType(),
                    countryConf.getRegionCode(),
                    boundary.getName(),
                    boundary.getNameLangs(),
                    mpSimpl);

            region.setAdminLevel(boundary.getAdminLevel());

            db.insertRegion(region, entry.getKey());
        }
    }

    private void createContinents (ADataContainer dc) {

        continentsMap = new LinkedHashMap<>();

        // create world wide "continent"
        createWorldRegion();

        // Definition for possible continents
        String[][] continentDefinitions = new String[][] {
                {"Africa", "36966057", "wo.af", "WO-AF", "africa.json"},
                {"Asia", "36966065", "wo.as", "WO-AS","asia.json"},
                {"Europe", "25871341", "wo.eu", "WO-EU","europe.json"},
                {"South America", "36966069", "wo.am", "WO-AM","america_south.json"},
                {"North America", "36966063", "wo.an", "WO-AN","america_north.json"},
                {"Oceania", "249399679", "wo.oc", "WO-OC","oceania.json"}
        };

        // iterate over the nodes and try to find continent polygon
        TLongList nodeIds = dc.getNodeIds();
        for (int i=0, size = nodeIds.size(); i < size; i++) {
            Node node = dc.getNodeFromCache(nodeIds.get(i));
            if (node == null || !isValidContinentNode(node)) {
                continue;
            }

            WKTReader wkt = new WKTReader();

            Logger.i(TAG, "createContinents: Process node as continent, node id: " + node.getId());
            // find continent definition for node
            CountryConf countryConf = null;
            String path = "";
            for (String[] staticDefinition : continentDefinitions) {
                if (node.getId() == Long.valueOf(staticDefinition[1])) {

                    countryConf = ConfigurationCountry.CountryConf.createStoreRegionDbConf(
                            staticDefinition[0], "wo", staticDefinition[2], staticDefinition[3]);
                    path = "../polygons/_world/" + staticDefinition[4];
                }
            }
            if (countryConf == null) {
                throw new RuntimeException("Definition for continent does not exist, Continent OSM node: " + node.getId());
            }

            File wktFile = new File(path);
            Logger.i(TAG, wktFile.getAbsolutePath());

            MultiPolygon multiPolygon = null;
            Geometry geom = GeomUtils.geoJsonToGeom(com.asamm.osmTools.utils.Utils.readFileToString(path, Charset.forName("utf-8")));
            if (geom.getGeometryType().equalsIgnoreCase("MultiPolygon")){
                multiPolygon = (MultiPolygon) geom;
            }
            else {
                multiPolygon = GeomUtils.polygonToMultiPolygon((Polygon) geom);
            }


            THashMap<String, String> names = OsmUtils.getNamesLangMutation(node, "name", countryConf.getCountryName());

            // hack to have english name in multilangual names names
            if (names.get(Language.ENGLISH.getCode()) == null){
                names.put(Language.ENGLISH.getCode(), countryConf.getCountryName());
            }

            Region regionContinents = new Region(
                    node.getId(),
                    EntityType.Node,
                    countryConf.getRegionCode(),
                    countryConf.getCountryName(),
                    names,
                    multiPolygon);
            regionContinents.setAdminLevel(1);

            Logger.i(TAG, "createContinents: created continent: " + regionContinents.toString());
            continentsMap.put(countryConf, regionContinents);
        }
    }

    /** ******************************************************************
     *                          HELPERS
     * ******************************************************************/

    /**
     * Create definition for special worldwide continent
     */
    private void createWorldRegion (){

        CountryConf countryConf = ConfigurationCountry.CountryConf.createStoreRegionDbConf(
                "Worldwide","wo", "wo", "WO" );

        THashMap<String, String> names = new THashMap<>();
        names.put("en", countryConf.getCountryName());

        WKTReader wkt = new WKTReader();
        Region regionContinent = null;
        try {
            regionContinent = new Region(
                    9999999999L,
                    EntityType.Node,
                    countryConf.getRegionCode(),
                    countryConf.getCountryName(),
                    names,
                    (MultiPolygon) wkt.read("MULTIPOLYGON (((-179.9 85, 179.9 85,179.9 -85,-179.9 -85,-179.9 85)))"));
        } catch (ParseException e) {
            e.printStackTrace();
        }
        regionContinent.setAdminLevel(0);
        continentsMap.put(countryConf,regionContinent);
    }

        /**
         * Test of OSM entity is continent definition
         */
    private boolean isValidContinentNode (Entity entity){

        if (entity == null || entity.getTags() == null) {
            return false;
        }

        if (entity.getType() != EntityType.Node){
            return false;
        }

        Collection<Tag> tags = entity.getTags();

        for (Tag tag : tags) {
            if (tag.getKey().equals(OsmConst.OSMTagKey.PLACE.getValue())) {
                if (tag.getValue().equalsIgnoreCase("continent")) {
                    return true;
                }
            }
        }
        return false;
    }


    /**
     * Check if all requested boundaries were created
     */
    private void testNotCreatedBoundaries() {

        List<CountryConf> countriesConf = wcbDefinition.getConfigurationCountry().getCountriesConf();

        boolean missingCountry = false;

        for (CountryConf countryConf : countriesConf){
            if ( !countryBoundaryMap.containsKey(countryConf)){
                //boundaries for this map was not created
                Logger.w(TAG, "Boundaries was not created for country: " + countryConf.getCountryName());
                missingCountry = true;
            }
        }
        if (missingCountry){

            Logger.w(TAG, "Some boundaries for countries was not founded - see the log above. " +
                    "Probably wrong country name");

            System.exit(1);
        }
    }

    /**
     * Test of boundary name is same to needed country
     *
     * @param boundary boundary to test it's name
     * @param countryName country for which are generated boundaries
     * @return true if boundary has name as country
     */
    private boolean hasBoundaryNameForCountry (Boundary boundary, String countryName){

        // normalize names
        String bNameNorm = Utils.normalizeNames(boundary.getName());
        String cNameNorm = Utils.normalizeNames(countryName);

        // test local name
        if (bNameNorm.equalsIgnoreCase(cNameNorm)){
            return true;
        }

        // test en name of boundary
        THashMap<String, String> nameInternational = boundary.getNameLangs();
        String enName = nameInternational.get("en");
        if (enName != null && Utils.normalizeNames(enName).equalsIgnoreCase(cNameNorm)){
            return true;
        }
        THashMap<String, String> officialNameInternational = boundary.getOfficialNamesInternational();
        String enOfficialName = officialNameInternational.get("en");
        if (enOfficialName != null && Utils.normalizeNames(enOfficialName).equalsIgnoreCase(cNameNorm)){
            return true;
        }

        // test any language
        for (Map.Entry<String, String> entry : nameInternational.entrySet()){
            if (Utils.normalizeNames(entry.getValue()).equalsIgnoreCase(cNameNorm)){
                return true;
            }
        }

        // test official languages
        for (Map.Entry<String, String> entry : officialNameInternational.entrySet()){
            if (Utils.normalizeNames(entry.getValue()).equalsIgnoreCase(cNameNorm)){
                return true;
            }
        }

        // test other alternative names
        if (boundary.getNamesAlternative() != null){
            for (String nameAlt : boundary.getNamesAlternative()){
                if (Utils.normalizeNames(nameAlt).equalsIgnoreCase(cNameNorm)){
                    return true;
                }
            }
        }


        return false;
    }


    /**
     * Temporary menthod that writes created sub region into geoJson not into the database
     */
    @Deprecated
    private void writeRegionToGeoJson() {

        for (Map.Entry<CountryConf, Boundary> entry : countryBoundaryMap.entrySet()) {

            Boundary boundary = entry.getValue();

            // simplify boundary to write
            Geometry mpBuffer = GeomUtils.bufferGeom(boundary.getGeom(), 12000);

            // simplify boundary to write
            double distanceDeg = Utils.distanceToDeg(mpBuffer.getCoordinate(), 12000);
            Geometry geometry = DouglasPeuckerSimplifier.simplify(mpBuffer, distanceDeg);

            // write founded boundary to file
            String fileName = entry.getKey().getCountryName().toLowerCase().replace(" ", "_");
            fileName = "../polygons/" +fileName + ".geojson";
            String geoJsonStr = GeomUtils.geomToGeoJson(geometry);
            com.asamm.osmTools.utils.Utils.writeStringToFile(new File(fileName), geoJsonStr, false);
        }
    }
}
