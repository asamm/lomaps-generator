package com.asamm.osmTools.generatorDb.address;

import com.asamm.osmTools.generatorDb.WriterAddressDefinition;
import com.asamm.osmTools.generatorDb.dataContainer.ADataContainer;
import com.asamm.osmTools.generatorDb.db.DatabaseAddress;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;

/**
 * Created by voldapet on 2016-03-10 .
 */
public abstract class AaddressController {

    protected ADataContainer dc;

    protected DatabaseAddress databaseAddress;

    protected WriterAddressDefinition wad;

    protected GeometryFactory geometryFactory;

    public AaddressController (ADataContainer dc, DatabaseAddress databaseAddress, WriterAddressDefinition wad ) {

        this.dc = dc;
        this.databaseAddress = databaseAddress;
        this.wad = wad;

        this.geometryFactory = new GeometryFactory();
    }

}
