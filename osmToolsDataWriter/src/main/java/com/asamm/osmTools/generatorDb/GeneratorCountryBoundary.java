package com.asamm.osmTools.generatorDb;

import com.asamm.osmTools.generatorDb.address.Boundary;
import com.asamm.osmTools.generatorDb.address.CityController;
import com.asamm.osmTools.generatorDb.dataContainer.ADataContainer;
import com.asamm.osmTools.generatorDb.db.ADatabaseHandler;
import com.asamm.osmTools.generatorDb.utils.GeomUtils;
import com.asamm.osmTools.generatorDb.utils.Utils;
import com.asamm.osmTools.utils.Logger;
import com.vividsolutions.jts.geom.MultiPolygon;
import gnu.trove.list.TLongList;
import gnu.trove.map.hash.THashMap;
import org.openstreetmap.osmosis.core.domain.v0_6.Relation;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    Map<CountryConf, Boundary> countryBoundaryMap;


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
        createBoundaries(dc);
        Logger.i(TAG, "= Write data to geojson files=");
        writeBoundaries();
        testNotCreatedBoundaries ();
    }

    

    /**
     * Create boundaries that has name as required countries
     */
    private void createBoundaries (ADataContainer dc){

        List<CountryConf> countriesConf = wcbDefinition.getConfigurationCountry().getCountriesConf();
        CityController boundaryController = CityController.createForCountryBound(dc);
        TLongList relationIds = dc.getRelationIds();
        Boundary foundedBoundary = null;

        for (int i=0, size = relationIds.size(); i < size; i++) {
            long relationId = relationIds.get(i);

            Relation relation = dc.getRelationFromCache(relationId);
            if (relation == null) {
                continue;
            }

            Boundary boundary = boundaryController.create(relation, true);

            if (boundary == null || boundary.getAdminLevel() <= 0 || !boundary.isValid()){
                if (boundary != null) {
                    Logger.i(TAG, "Boundary is not valid: " + boundary.getName());
                }
                continue;

            }

            //if (boundary.getAdminLevel() == wcbDefinition.getConfigurationCountry().getAdminLevel()){
            // use only boundaries that are same or smaller level then needed level for region boundaries
            Logger.i(TAG, "Process country boundary for: " + boundary.getName());

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
                        // join geom of previous boundary with new one
                        MultiPolygon unionMp = GeomUtils.unionMultiPolygon(boundaryOld.getGeom(), boundary.getGeom());
                        boundaryOld.setGeom(unionMp);
                    }
                }
            }

        }
    }

    /**
     * Store created boundaries into GeoJson file
     */
    private void writeBoundaries () {

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

            throw new IllegalArgumentException("Some boundaries for countries was not founded - see the log above. " +
                    "Probably wrong country name");
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
        THashMap<String, String> nameInternational = boundary.getNamesInternational();
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

        return false;
    }

}
