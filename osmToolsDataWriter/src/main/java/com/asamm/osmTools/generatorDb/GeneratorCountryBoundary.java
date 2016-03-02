package com.asamm.osmTools.generatorDb;

import com.asamm.osmTools.generatorDb.address.Boundary;
import com.asamm.osmTools.generatorDb.address.BoundaryController;
import com.asamm.osmTools.generatorDb.dataContainer.ADataContainer;
import com.asamm.osmTools.generatorDb.db.ADatabaseHandler;
import com.asamm.osmTools.utils.Logger;
import gnu.trove.list.TLongList;
import org.openstreetmap.osmosis.core.domain.v0_6.Relation;

import java.io.File;

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
        BoundaryController boundaryController = new BoundaryController();
        TLongList relationIds = dc.getRelationIds();

        for (int i=0, size = relationIds.size(); i < size; i++) {
            long relationId = relationIds.get(i);

            Relation relation = dc.getRelationFromCache(relationId);
            if (relation == null) {
                continue;
            }

            Boundary boundary = boundaryController.create(dc, relation);

            if (boundary == null || boundary.getAdminLevel() <= 0){
                continue;
            }

            if (boundary.getAdminLevel() == 8){
                // use only boundaries that are same or smaller level then needed level for region boundaries
                Logger.i(TAG, "Process country boudnary for: " + boundary.getName());
            }
        }


    }


}
