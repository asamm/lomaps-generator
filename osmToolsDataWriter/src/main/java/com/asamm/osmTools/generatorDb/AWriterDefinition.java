package com.asamm.osmTools.generatorDb;

import com.asamm.locus.features.loMaps.LoMapsDbConst;
import org.openstreetmap.osmosis.core.domain.v0_6.Entity;
import org.openstreetmap.osmosis.core.domain.v0_6.Node;
import org.openstreetmap.osmosis.core.domain.v0_6.Relation;
import org.openstreetmap.osmosis.core.domain.v0_6.Way;

/**
 * Created by menion on 8/5/15.
 */
public abstract class AWriterDefinition {


    protected LoMapsDbConst.EntityType getTypeFromEntity(Entity entity) {
        if (entity instanceof Node) {
            return LoMapsDbConst.EntityType.POIS;
        }
        else if (entity instanceof Way) {
            return LoMapsDbConst.EntityType.WAYS;
        }
        else if (entity instanceof Relation){
            return LoMapsDbConst.EntityType.RELATION;
        }
        else {
            return LoMapsDbConst.EntityType.UNKNOWN;
        }
    }

    public abstract  boolean isValidEntity(Entity entity);
}
