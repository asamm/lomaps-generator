package com.asamm.osmTools.generatorDb.utils;

import com.asamm.osmTools.generatorDb.data.OsmConst;
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
     *
     * @param entity entity to get tag
     * @param key    name of tag
     * @return value of tag or null if tag is not defined
     */
    public static String getTagValue(Entity entity, OsmConst.OSMTagKey key) {

        Collection<Tag> tags = entity.getTags();
        if (tags == null) {
            return null;
        }
        for (Tag tag : tags) {
            if (tag.getKey().equals(key.getValue())) {
                return tag.getValue();
            }
        }
        return null;
    }

    /**
     * Prapare string with values of entity tags
     *
     * @param entity
     * @return
     */
    public static String printTags(Entity entity) {

        String str = "Entity: " + entity.getType().toString() + ", id:  " + entity.getId();
        Collection<Tag> tags = entity.getTags();
        if (tags == null) {
            return str;
        }
        for (Tag tag : tags) {
            str += "\n " + tag.getKey() + "=" + tag.getValue() + " , ";
        }
        return str;
    }


    /**
     * Parse entity tags and find value possible street name
     * The priority of tags is: 1. Name, 2. Street, 3. Addr:street
     * Important! don't use it for getting street name when parse houses
     * For houses has higher priority the addr:street
     *
     * @param entity entity to obtain name
     * @return street name or null if is not possible to parse street name
     */
    public static String getStreetName(Entity entity) {

        if (entity == null) {
            return null;
        }

        String name = null;
        if (entity != null) {
            name = OsmUtils.getTagValue(entity, OsmConst.OSMTagKey.NAME);
        }
        if (name == null || name.length() == 0) {
            name = OsmUtils.getTagValue(entity, OsmConst.OSMTagKey.STREET);
        }
        if (name == null || name.length() == 0) {
            name = OsmUtils.getTagValue(entity, OsmConst.OSMTagKey.ADDR_STREET);
        }
        return name;
    }

    /**
     * Prepare map of lang mutation of OSM tag. Lang mutation is not in map if this lang mutation
     * is the same as local default name
     *
     * @param entity  entity to get names
     * @param tagName name of OSM tag that can have multilangual values. like name or official_name
     * @param nameDef default local name
     * @return
     */
    public static THashMap<String, String> getNamesLangMutation(Entity entity, String tagName, String nameDef) {
        THashMap<String, String> names = new THashMap<>();

        if (entity == null) {
            return names;
        }
        Collection<Tag> tags = entity.getTags();
        if (tags == null) {
            return names;
        }

        tagName = tagName + ":";
        for (Tag tag : tags) {
            String key = tag.getKey();
            if (key.startsWith(tagName)) {
                String nameInternational = tag.getValue();
                String[] keyParts = key.split(":");
                if (keyParts.length < 2) {
                    // it looks like tag with key name: > but can not parse lang code> skip it
                    continue;
                }
                String langCode = keyParts[1];

                if (langCode.length() == 2 && nameInternational != null && nameInternational.length() > 0) {
                    if (!Utils.objectEquals(nameInternational, nameDef)) {
                        // only languages that does not have same name as default name are added into map
                        names.put(langCode, nameInternational);
                    }
                }
            }
        }
        return names;
    }
}
