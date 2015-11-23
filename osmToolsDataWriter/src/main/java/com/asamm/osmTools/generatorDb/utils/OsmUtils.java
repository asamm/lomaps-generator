package com.asamm.osmTools.generatorDb.utils;

import com.asamm.osmTools.generatorDb.data.OsmConst;
import com.asamm.osmTools.utils.Logger;
import gnu.trove.impl.hash.THash;
import gnu.trove.map.hash.THashMap;
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


    /**
     * Parse entity tags and find value possible street name
     * The priority of tags is: 1. Name, 2. Street, 3. Addr:street
     * Important! don't use it for getting street name when parse houses
     * For houses has higher priority the addr:street
     * @param entity entity to obtain name
     * @return street name or null if is not possible to parse street name
     */
    public static String getStreetName (Entity entity){
        String name = null;
        if (entity != null){
            name = OsmUtils.getTagValue(entity, OsmConst.OSMTagKey.NAME);
        }
        if (name == null || name.length() == 0){
            name = OsmUtils.getTagValue(entity, OsmConst.OSMTagKey.STREET);
        }
        if (name == null || name.length() == 0){
            name = OsmUtils.getTagValue(entity, OsmConst.OSMTagKey.ADDR_STREET);
        }
        return name;
    }

    public static THashMap<String, String> getNamesInternational (Entity entity){
        THashMap<String, String> names = new THashMap<>();

        if (entity == null){
            return names;
        }
        Collection<Tag> tags =  entity.getTags();
        if(tags == null){
            return names;
        }
        for (Tag tag : tags){
            String key = tag.getKey();
            if (key.startsWith("name:")) {
                String name = tag.getValue();
                String langCode = key.split(":")[1];
                if (langCode.length() == 2 && name != null && name.length() > 0){

                    names.put(langCode, name);
                    //Logger.i("OSm UTILS"," lang Code: " + langCode + " name: " + name );
                }
            }
        }
        return names;
    }
}
