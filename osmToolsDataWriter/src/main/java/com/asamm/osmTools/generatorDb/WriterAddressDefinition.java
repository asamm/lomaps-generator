package com.asamm.osmTools.generatorDb;

import com.asamm.osmTools.generatorDb.data.OsmConst.OSMTagKey;
import com.asamm.osmTools.generatorDb.utils.OsmUtils;
import com.asamm.osmTools.utils.Logger;
import org.openstreetmap.osmosis.core.domain.v0_6.Entity;
import org.openstreetmap.osmosis.core.domain.v0_6.EntityType;
import org.openstreetmap.osmosis.core.domain.v0_6.Tag;

import java.util.Collection;

/**
 * Definition which Nodes, Ways, Relation are vital for storing in data container
 */
public class WriterAddressDefinition extends AWriterDefinition{


    private static final String TAG = WriterAddressDefinition.class.getSimpleName();

    // only this values for osm tag place can be used for cities
    String[] validPlaceNodes =  {"city", "town" , "village", "hamlet", "suburb", "district" };

    // what level of admin boundaries will be used for admin areas
    private int regionAdminLevel = 6;


    public WriterAddressDefinition () {

    }

    @Override
    public boolean isValidEntity(Entity entity) {
        if (entity == null || entity.getTags() == null) {
            return false;
        }

        // save all nodes into cache
        if (entity.getType() == EntityType.Node){
            return true;
        }

        // save all ways because the streets
        else if (entity.getType() == EntityType.Way){
            //almost unused ways are limited by osmosis simplification
            Collection<Tag> tags = entity.getTags();
            for (Tag tag : tags) {
                if (tag.getKey().equals(OSMTagKey.HIGHWAY.getValue())) {
                    if (tag.getValue().equals("proposed")){
                        return false;
                    }
                }
            }
            return true;
        }

        // save only boundaries ways and region into cache
        else if (isValidRelation(entity)){
            return true;
        }

        return false;
    }


    public boolean isValidPlaceNode (Entity entity){

        if (entity == null || entity.getTags() == null) {
            return false;
        }

        if (entity.getType() != EntityType.Node){
            return false;
        }

        Collection<Tag> tags = entity.getTags();

        for (Tag tag : tags) {
            if (tag.getKey().equals(OSMTagKey.PLACE.getValue())) {
                for (String placeType : validPlaceNodes) {
                    if (placeType.equalsIgnoreCase(tag.getValue())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public boolean isValidRelation (Entity entity){


        Collection<Tag> tags = entity.getTags();
        for (Tag tag : tags) {

            if (tag.getKey().equals(OSMTagKey.BOUNDARY.getValue()) || tag.getKey().equals(OSMTagKey.PLACE.getValue())) {
                return true;
            }

            if (tag.getValue().equals(OSMTagKey.STREET.getValue()) || tag.getValue().equals(OSMTagKey.ASSOCIATED_STREET.getValue())) {
                //Logger.i(TAG, "Street relation id: " + entity.getId());
                return true;
            }

            if (tag.getKey().equals(OSMTagKey.HIGHWAY.getValue()) && tag.getValue().equals("pedestrian")) {
                // because the squares
                //Logger.i(TAG, "Pedestrian relation id: " + entity.getId());
                return true;
            }

            if (tag.getKey().equals(OSMTagKey.BUILDING.getValue())) {
                return true;
            }
        }
        return false;
    }

    public int getRegionAdminLevel() {
        return regionAdminLevel;
    }

    public void setRegionAdminLevel(int level) {
        regionAdminLevel = level;
    }
}
