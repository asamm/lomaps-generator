package com.asamm.osmTools.generatorDb;

import com.asamm.locus.features.dbPoi.DbPoiConst;
import org.openstreetmap.osmosis.core.domain.v0_6.Entity;
import org.openstreetmap.osmosis.core.domain.v0_6.Node;
import org.openstreetmap.osmosis.core.domain.v0_6.Relation;
import org.openstreetmap.osmosis.core.domain.v0_6.Way;

/**
 * Created by menion on 8/5/15.
 */
public abstract class AWriterDefinition {


    protected DbPoiConst.EntityType getTypeFromEntity(Entity entity) {
        if (entity instanceof Node) {
            return DbPoiConst.EntityType.POIS;
        }
        else if (entity instanceof Way) {
            return DbPoiConst.EntityType.WAYS;
        }
        else if (entity instanceof Relation){
            return DbPoiConst.EntityType.RELATION;
        }
        else {
            return DbPoiConst.EntityType.UNKNOWN;
        }
    }

    public abstract  boolean isValidEntity(Entity entity);
}
