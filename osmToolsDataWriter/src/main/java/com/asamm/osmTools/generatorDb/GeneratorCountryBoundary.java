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
import java.util.Map;

/**
 * Created by voldapet on 2016-02-22 .
 */
public class GeneratorCountryBoundary extends AGenerator{

    private static final String TAG = GeneratorCountryBoundary.class.getSimpleName();

    /**
     * File to write country boundary geometry
     */
    private File outputGeomFile;


    /**
     * Definition for generator
     */
    private WriterCountryBoundaryDefinition wcbDefinition;


    public GeneratorCountryBoundary (WriterCountryBoundaryDefinition wcbDefinition, File outputGeomFile){

        this.wcbDefinition = wcbDefinition;
        this.outputGeomFile = outputGeomFile;

    }


    @Override
    protected ADatabaseHandler prepareDatabase() throws Exception {

        // nothing to do for this generator
        return null;
    }

    @Override
    public void proceedData(ADataContainer dc) {

        Logger.i(TAG, "=== Start country boundary process data ===");

        //
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
                continue;
            }
            if (boundary.getAdminLevel() == wcbDefinition.getConfigurationCountry().getAdminLevel()){
                // use only boundaries that are same or smaller level then needed level for region boundaries
                Logger.i(TAG, "Process country boundary for: " + boundary.getName());

                if (hasBoundaryNameForCountry(boundary, wcbDefinition.getConfigurationCountry().getCountryName())){
                    // this boundary has name as requested country > before use it test it
                    if (foundedBoundary == null){
                        // no boundary was found yet > use this one
                        foundedBoundary = boundary;
                    }
                    else if (boundary.getGeom().getArea() > foundedBoundary.getGeom().getArea()){
                        // in previous step was any boundary found. test the actual with previous by area. the bigger win
                        foundedBoundary = boundary;
                    }
                }
            }
        }

        if (foundedBoundary == null){
            throw  new IllegalArgumentException("No boundary was founded for map: " +
                    wcbDefinition.getConfigurationCountry().getCountryName());
        }
        else {
            // simplify boundary to write
            MultiPolygon mpSimpl = GeomUtils.simplifyMultiPolygon(foundedBoundary.getGeom(), 200);

            //buffer simplified geom because after simplification exits passage between orig country border and simplified boundary
            MultiPolygon mpBuf = GeomUtils.bufferGeom(mpSimpl, 100);
            mpSimpl = GeomUtils.simplifyMultiPolygon(mpBuf, 100);

            // write founded boundary to file
            File geoJsonFile = wcbDefinition.getConfigurationCountry().getFileGeom();
            String geoJsonStr = GeomUtils.geomToGeoJson(mpSimpl);
            com.asamm.osmTools.utils.Utils.writeStringToFile(geoJsonFile, geoJsonStr, false);

            Logger.i(TAG, "Write geometry geojson file into boundary file: " + geoJsonFile.getAbsolutePath());
        }
    }

    /**
     * Test of boundary name is same to needed country
     *
     * @param boundary boundary to test it's name
     * @param countryName country for which are generated boundaries
     * @return true if boundary has name as country
     */
    private boolean hasBoundaryNameForCountry (Boundary boundary, String countryName ){

        // normalize names
        String bNameNorm = Utils.normalizeString(boundary.getName());
        String cNameNorm = Utils.normalizeString(countryName);

        // test local name
        if (bNameNorm.equalsIgnoreCase(cNameNorm)){
            return true;
        }

        // test en name of boundary
        THashMap<String, String> nameInternational = boundary.getNamesInternational();
        String enName = nameInternational.get("en");
        if (enName != null && Utils.normalizeString(enName).equalsIgnoreCase(cNameNorm)){
            return true;
        }

        // test any language
        for (Map.Entry<String, String> entry : nameInternational.entrySet()){
            if (Utils.normalizeString(entry.getValue()).equalsIgnoreCase(cNameNorm)){
                return true;
            }
        }

        return false;
    }





}
