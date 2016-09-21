package com.asamm.osmTools.generatorDb;

import com.asamm.osmTools.generatorDb.data.OsmConst.OSMTagKey;
import com.asamm.osmTools.generatorDb.plugin.ConfigurationAddress;
import com.asamm.osmTools.generatorDb.utils.GeomUtils;
import com.asamm.osmTools.generatorDb.utils.Utils;
import com.asamm.osmTools.utils.Logger;
import com.asamm.osmTools.utils.XmlParser;
import com.vividsolutions.jts.geom.*;
import com.vividsolutions.jts.geom.prep.PreparedGeometry;
import com.vividsolutions.jts.geom.prep.PreparedGeometryFactory;
import gnu.trove.map.TLongLongMap;
import gnu.trove.map.hash.TLongLongHashMap;
import gnu.trove.set.hash.THashSet;
import org.openstreetmap.osmosis.core.domain.v0_6.Entity;
import org.openstreetmap.osmosis.core.domain.v0_6.EntityType;
import org.openstreetmap.osmosis.core.domain.v0_6.Tag;
import org.xmlpull.v1.XmlPullParser;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Definition which Nodes, Ways, Relation are vital for storing in data container
 */
public class WriterAddressDefinition extends WriterGeocodingDefinition{


    private static final String TAG = WriterAddressDefinition.class.getSimpleName();

    // only this values for osm tag place can be used for cities
    protected static final String[] VALID_PLACE_NODES =  {"city", "town" , "village", "hamlet", "suburb", "district" };


    /**
     *  Configuration parameters from plugin command lines
     */
    private ConfigurationAddress confAddress;




    private final GeometryFactory geometryFactory;

    public WriterAddressDefinition (ConfigurationAddress confAddress) throws Exception {

        super(confAddress);

        this.confAddress = confAddress;

        geometryFactory = new GeometryFactory();

    }


    public boolean isValidEntity(Entity entity) {
        if (entity == null || entity.getTags() == null) {
            return false;
        }

        // save all nodes into cache
        if (entity.getType() == EntityType.Node){
//            Node node = (Node) entity;
//            Point point = geometryFactory.createPoint(new Coordinate(node.getLongitude(), node.getLatitude()));
//            if (preparedDbGeom.intersects(point)){
//                return true;
//            }
//            return false;
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

    /**
     * Test if given relation can be used for generation of addresses. Specifically if it is street, place boundary
     * building
     * @param entity entity to test
     * @return <true>True</true> if entity can be used for generation of address
     */
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


    public ConfigurationAddress getConfAddress() {
        return confAddress;
    }



}
