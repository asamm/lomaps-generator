package com.asamm.osmTools.generatorDb.input.definition;

import org.openstreetmap.osmosis.core.domain.v0_6.Entity;
import org.openstreetmap.osmosis.core.domain.v0_6.EntityType;

/**
 * Created by voldapet on 16/1/2017.
 */
public class WriterTransformDefinition extends AWriterDefinition {


    @Override
    public boolean isValidEntity(Entity entity) {

        if (entity == null || entity.getTags() == null) {
            return false;
        }

        // save all nodes into cache
        if (entity.getType() == EntityType.Node){
            return true;
        }

        // save all ways because filtration is done by Osmbasic program
        else if (entity.getType() == EntityType.Way){
            return true;
        }

        // relations are REJECTED for geneation city areas
        else if ( entity.getType() == EntityType.Relation){
            return false;
        }

        return false;

    }

}
