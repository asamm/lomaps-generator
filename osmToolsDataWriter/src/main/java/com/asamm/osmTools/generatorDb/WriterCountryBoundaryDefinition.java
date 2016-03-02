package com.asamm.osmTools.generatorDb;

import com.asamm.osmTools.generatorDb.data.OsmConst;
import org.openstreetmap.osmosis.core.domain.v0_6.Entity;
import org.openstreetmap.osmosis.core.domain.v0_6.EntityType;
import org.openstreetmap.osmosis.core.domain.v0_6.Tag;

import java.util.Collection;

/**
 * Created by voldapet on 2016-02-22 .
 */
public class WriterCountryBoundaryDefinition extends  AWriterDefinition {

    /**
     * admin level for country boundary
     */
    private int adminLevel;



    @Override
    public boolean isValidEntity(Entity entity) {
        if (entity == null || entity.getTags() == null) {
            return false;
        }

        // save all nodes into cache
        if (entity.getType() == EntityType.Node){
            return true;
        }

        // save all ways because should be limited by filters
        else if (entity.getType() == EntityType.Way){
            return true;
        }

        // save only boundaries ways and region into cache
        else if (isValidRelation(entity)){
            return true;
        }

        return false;
    }

    /**
     * Test tags of relation. Only boundary relation can be used
     *
     * @param entity entity to test
     * @return true if relation can be used for processing
     */
    public boolean isValidRelation (Entity entity){


        Collection<Tag> tags = entity.getTags();
        for (Tag tag : tags) {
            if (tag.getKey().equals(OsmConst.OSMTagKey.BOUNDARY.getValue()) ||
                    tag.getKey().equals(OsmConst.OSMTagKey.ADMIN_LEVEL)) {
                return true;
            }
        }
        return false;
    }

    public int getAdminLevel() {
        return adminLevel;
    }

    public void setAdminLevel(int level) {
        adminLevel = level;
    }
}
