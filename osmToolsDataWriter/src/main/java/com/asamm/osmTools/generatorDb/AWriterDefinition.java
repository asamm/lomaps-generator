package com.asamm.osmTools.generatorDb;

import com.asamm.locus.features.dbAddressPoi.DbAddressPoiConst;
import org.openstreetmap.osmosis.core.domain.v0_6.Entity;
import org.openstreetmap.osmosis.core.domain.v0_6.Node;
import org.openstreetmap.osmosis.core.domain.v0_6.Relation;
import org.openstreetmap.osmosis.core.domain.v0_6.Way;

/**
 * Created by menion on 8/5/15.
 */
public abstract class AWriterDefinition {


    protected DbAddressPoiConst.EntityType getTypeFromEntity(Entity entity) {
        if (entity instanceof Node) {
            return DbAddressPoiConst.EntityType.POIS;
        }
        else if (entity instanceof Way) {
            return DbAddressPoiConst.EntityType.WAYS;
        }
        else if (entity instanceof Relation){
            return DbAddressPoiConst.EntityType.RELATION;
        }
        else {
            return DbAddressPoiConst.EntityType.UNKNOWN;
        }
    }

    public abstract  boolean isValidEntity(Entity entity);
}
