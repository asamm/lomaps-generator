package com.asamm.osmTools.generatorDb.address;

import com.asamm.osmTools.generatorDb.dataContainer.ADataContainer;
import com.asamm.osmTools.generatorDb.db.DatabaseAddress;
import org.locationtech.jts.geom.GeometryFactory;

/**
 * Created by voldapet on 2016-03-10 .
 */
public abstract class AaddressController {

    protected ADataContainer dc;

    protected DatabaseAddress databaseAddress;

    protected GeometryFactory geometryFactory;

    public AaddressController (ADataContainer dc, DatabaseAddress databaseAddress) {

        this.dc = dc;
        this.databaseAddress = databaseAddress;

        this.geometryFactory = new GeometryFactory();
    }

}
