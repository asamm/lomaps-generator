package com.asamm.osmTools.generatorDb.utils;

import com.asamm.osmTools.generatorDb.data.OsmConst;
import org.openstreetmap.osmosis.core.domain.v0_6.Entity;
import org.openstreetmap.osmosis.core.domain.v0_6.Tag;

import java.util.Collection;

/**
 * Created by voldapet on 2015-08-18 .
 */
public class OsmUtils {
    /**
     * Get value of tag for entity
     * @param entity enrity to get tag
     * @param key name of tag
     * @return value of tag or null if tag is not defined
     */
    public static String getTagValue (Entity entity, OsmConst.OSMTagKey key) {

        Collection<Tag> tags =  entity.getTags();
        if(tags == null){
            return null;
        }
        for (Tag tag : tags){
            if (tag.getKey().equals(key.getValue())) {
                return tag.getValue();
            }
        }
        return null;
    }
}
