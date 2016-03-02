package com.asamm.osmTools.generatorDb;

import com.asamm.osmTools.generatorDb.data.OsmConst.OSMTagKey;
import com.asamm.osmTools.generatorDb.plugin.ConfigurationAddress;
import com.asamm.osmTools.generatorDb.utils.Utils;
import com.asamm.osmTools.utils.XmlParser;
import org.apache.commons.lang3.Range;
import org.openstreetmap.osmosis.core.domain.v0_6.Entity;
import org.openstreetmap.osmosis.core.domain.v0_6.EntityType;
import org.openstreetmap.osmosis.core.domain.v0_6.Tag;
import org.xmlpull.v1.XmlPullParser;

import java.util.Collection;

/**
 * Definition which Nodes, Ways, Relation are vital for storing in data container
 */
public class WriterAddressDefinition extends AWriterDefinition{


    private static final String TAG = WriterAddressDefinition.class.getSimpleName();

    // only this values for osm tag place can be used for cities
    private static final String[] VALID_PLACE_NODES =  {"city", "town" , "village", "hamlet", "suburb", "district" };

    // id of "default" map > it's definition for map that are not defined in xml
    private static final String DEFAULT_MAP_ID = "default_definition";


    // configuration parameters from plugin command lines
    private ConfigurationAddress confAddress;

    private int regionAdminLevel;

    private int[] cityAdminLevels;

    public WriterAddressDefinition (ConfigurationAddress confAddress) throws Exception {

        this.confAddress = confAddress;

        // parse XML
        parseConfigXml();
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
                for (String placeType : VALID_PLACE_NODES) {
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

    // LEVEL DEFINITION

    /**
     * Get boundary level to use for generation of region boundaries
     * @return admin level. Default value admin_level = 6;
     */
    public int getRegionAdminLevel () {
        return regionAdminLevel;
    }

    /**
     * Array of admin level from which is possible to create boundary for city
     * @return
     */
    public int[] getCityAdminLevels (){
        return cityAdminLevels;
    }

    /**
     * Test if boundary with specified admin level can be used as geometry for city
     *
     * @param adminLevel level to test
     * @return true if boundary can be used as geom for city
     */
    public boolean isCityAdminLevel (int adminLevel) {
        if (cityAdminLevels == null){
            return false;
        }

        for (int i=0; i < cityAdminLevels.length; i++){
            if (cityAdminLevels[i] == adminLevel){
                return true;
            }
        }
        return false;
    }

    public ConfigurationAddress getConfAddress() {
        return confAddress;
    }


    // PARSE DEF XML

    private void parseConfigXml() throws Exception {

        // if of map for which is address defined and for which we search the level definition
        final String mapId = confAddress.getMapId();

        XmlParser parser = new XmlParser(confAddress.getFileConfig()) {

            boolean isInMaps = false;

            @Override
            public boolean tagStart(XmlPullParser parser, String tagName) throws Exception {

                if (tagName.equals("maps")) {
                    isInMaps = true;
                }
                else if (isInMaps) {
                    if (tagName.equals("map")) {

                        String id = parser.getAttributeValue(null, "id");

                        if (id.equals(DEFAULT_MAP_ID) || id.equals(mapId) ){
                            // save definition for default or needed map by mapId
                            String strCityLevels = parser.getAttributeValue(null, "cityLevels");
                            String strRegionLevel = parser.getAttributeValue(null, "regionLevel");
                            cityAdminLevels = parseCityRange(strCityLevels, parser);
                            regionAdminLevel = parseRegionLevel(strRegionLevel, parser);
                        }

                        if (id.equals(mapId)){
                            // stop parsing because we find definition for map wih spec id
                            return false;
                        }
                    }
                }
                return true;
            }

            @Override
            public boolean tagEnd(XmlPullParser parser, String tagName) throws Exception {
                if (tagName.equals("maps")) {
                    isInMaps = false;
                }
                return true;
            }

            @Override
            public void parsingFinished(boolean success) {}
        };

        parser.parse();
    }


    /**
     * Convert string cml value into array of integer that define supported admin levels for cities
     *
     * @param strCityLevels
     * @param parser
     * @return
     */
    private int[] parseCityRange (String strCityLevels, XmlPullParser parser){

        if (strCityLevels == null || strCityLevels.length() == 0){
            throw new IllegalArgumentException("Address definition XML not valid. " +
                    "Wrong cityLevels parameter on line: " + parser.getLineNumber());
        }

        // split levels
        String[] parts = strCityLevels.split(",",-1);
        int[] cityAdminLevels = new int[parts.length];

        for (int i=0; i < parts.length; i++){
            if (!Utils.isNumeric(parts[i])){
                throw new IllegalArgumentException("Address definition XML not valid. " +
                        "Wrong cityLevels parameter. Only integers are allowed. Line num: " + parser.getLineNumber());
            }
            cityAdminLevels[i] = Integer.parseInt(parts[i]);
        }
        return cityAdminLevels;
    }

    private int parseRegionLevel (String strRegionLevel, XmlPullParser parser){

        if (strRegionLevel == null || strRegionLevel.length() == 0){
            throw new IllegalArgumentException("Address definition XML not valid. " +
                    "Wrong regionLevel parameter on line: " + parser.getLineNumber());
        }
        if ( !Utils.isNumeric(strRegionLevel)){
            throw new IllegalArgumentException("Address definition XML not valid. " +
                    "Wrong region parameter (has to be integer) on line: " + parser.getLineNumber());
        }
        return Integer.parseInt(strRegionLevel);
    }
}
