package com.asamm.osmTools.generatorDb.input.definition;

import com.asamm.osmTools.generatorDb.data.OsmConst;
import com.asamm.osmTools.generatorDb.plugin.ConfigurationAddress;
import com.asamm.osmTools.generatorDb.utils.GeomUtils;
import com.asamm.osmTools.generatorDb.utils.Utils;
import com.asamm.osmTools.utils.Logger;
import com.asamm.osmTools.utils.XmlParser;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Point;
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

/**
 * Definition which Nodes, Ways, Relation are vital for storing in data container
 */
public class WriterAddressDefinition extends AWriterDefinition {


    private static final String TAG = WriterAddressDefinition.class.getSimpleName();

    // only this values for osm tag place can be used for cities
    protected static final String[] VALID_PLACE_NODES =  {"city", "town" , "village", "hamlet", "suburb", "district" };

    // id of "default" map > it's definition for map that are not defined in xml
    private static final String DEFAULT_MAP_ID = "default_definition";

    /** In adress definition XML are defined boundary phrases are separated by pipe */
    private static final String BOUNDARY_PHRASES_SEPARATOR = "|";


    private ConfigurationAddress confAddress;

    /**
     * Admin level of region boundary
     */
    private int regionAdminLevel;

    /**
     * Admin level that can be used for city boundary
     */
    private int[] cityAdminLevels;

    /**
     * Custom settings that map specific city to specific boundary
     * Map is created like <osmBoundaryId | osmCityId>
     */
    private TLongLongMap bundaryCityMapper;


    /**
     * List of boundary general names that can be removed from boundary name to find city for boundary
     */
    private THashSet<String> boundaryPhrases;

    /**
     * Geom prepared for intersection queries (better performance then standard geometry)
     */
    protected PreparedGeometry preparedDbGeom;

    /**
     * Defines area for which will be generated the geocoding db. It is combination / intersection of country border and map area
     * for standard addresses.
     */
    protected Geometry databaseGeom;

    /** CONSTRUCTOR */
    public WriterAddressDefinition(ConfigurationAddress configurationAddress) throws Exception {

        reset ();

        this.confAddress = configurationAddress;

        // parse XML
        parseConfigXml();

        // this is little bit fake > the geometry is the country geom.
        databaseGeom = createDbGeom(
                configurationAddress.getFileCountryGeom().getAbsolutePath(),
                configurationAddress.getFileCountryGeom().getAbsolutePath());

        preparedDbGeom = PreparedGeometryFactory.prepare(databaseGeom);
    }


    private void reset() {
        regionAdminLevel = 0;
        cityAdminLevels = new int[0];
        bundaryCityMapper = new TLongLongHashMap();
        boundaryPhrases = new THashSet<>();
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
                if (tag.getKey().equals(OsmConst.OSMTagKey.HIGHWAY.getValue())) {
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

            if (tag.getKey().equals(OsmConst.OSMTagKey.BOUNDARY.getValue()) || tag.getKey().equals(OsmConst.OSMTagKey.PLACE.getValue())) {
                return true;
            }

            if (tag.getValue().equals(OsmConst.OSMTagKey.STREET.getValue()) || tag.getValue().equals(OsmConst.OSMTagKey.ASSOCIATED_STREET.getValue())) {
                //Logger.i(TAG, "Street relation id: " + entity.getId());
                return true;
            }

            if (tag.getKey().equals(OsmConst.OSMTagKey.HIGHWAY.getValue()) && tag.getValue().equals("pedestrian")) {
                // because the squares
                //Logger.i(TAG, "Pedestrian relation id: " + entity.getId());
                return true;
            }

            if (tag.getKey().equals(OsmConst.OSMTagKey.BUILDING.getValue())) {
                return true;
            }
        }
        return false;
    }


    /**
     * Cities are created only for nodes and from custom list of type of places
     * @param entity osm entity to test if is valid place node for creation the city
     * @return <code>true</code> if entity can be parsed as center of any place/city
     */
    public boolean isValidPlaceNode (Entity entity){

        if (entity == null || entity.getTags() == null) {
            return false;
        }

        if (entity.getType() != EntityType.Node){
            return false;
        }

        Collection<Tag> tags = entity.getTags();

        for (Tag tag : tags) {
            if (tag.getKey().equals(OsmConst.OSMTagKey.PLACE.getValue())) {
                for (String placeType : VALID_PLACE_NODES) {
                    if (placeType.equalsIgnoreCase(tag.getValue())) {
                        return true;
                    }
                }
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

    // DATABASE AREA

    /**
     * Read geoJson areas from files and create JTS geom as intersection of these polygons
     *
     * @param dataGeomPath path to geojson file that define map area
     * @param countryGeomPath path to geojson file that define country borders
     * @return intersection geometry of both areas
     */
    public static Geometry createDbGeom(String dataGeomPath, String countryGeomPath ) {

        // read country boundaries
        String geoJsonCountry = com.asamm.osmTools.utils.Utils.readFileToString(
                countryGeomPath, StandardCharsets.UTF_8);

        String geoJsonData = com.asamm.osmTools.utils.Utils.readFileToString(
                dataGeomPath, StandardCharsets.UTF_8);

        Geometry countryPoly = GeomUtils.geoJsonToGeom(geoJsonCountry);
        Geometry dataPoly = GeomUtils.geoJsonToGeom(geoJsonData);

        // intersect both geometries
        return countryPoly.intersection(dataPoly);
    }

    /**
     * Tests if center point of geometry lies in country boundaries
     *
     * @param geometry geometry to test
     * @return true if center point is inside the database borders
     */
    public boolean isInDatabaseArea (Geometry geometry){

        Point centroid = geometry.getCentroid();
        if ( !centroid.isValid()){
            // centroid for street with same points is NaN. This is workaround
            centroid = new GeometryFactory().createPoint(geometry.getCoordinate());
        }

        return preparedDbGeom.intersects(centroid);
    }


    // CITY BOUNDARY MAPPER

    /**
     * Test if exist any boundary mapper definition for cityid
     *
     * @param cityId id of city to test
     * @return true if exist specified boundary for such city
     */
    public boolean isMappedCityId (long cityId){
        return bundaryCityMapper.containsValue(cityId);
    }

    /**
     * Test if exist any custom mapper definition that say that this boundary has to be set for specific city
     *
     * @param boundaryId id of boundary to test
     * @return true if this boundary has to be assigned to custom city
     */
    public boolean isMappedBoundaryId (long boundaryId){
        return bundaryCityMapper.containsKey(boundaryId);
    }

    /**
     *
     * @param boundaryId id of boundary to get mapped city for it
     * @return osm id of city that has to be used as center for boundary with defined id
     */
    public long getMappedCityIdForBoundary(long boundaryId) {
        return bundaryCityMapper.get(boundaryId);
    }


    // PARSE DEF XML

    /**
     * Parse address configuration xml to get possible admin levels for city and region boundaries
     *
     * @throws Exception
     */
    private void parseConfigXml() throws Exception {

        // if of map for which is address defined and for which we search the level definition
        final String mapId = confAddress.getMapId();

        XmlParser parser = new XmlParser(confAddress.getFileConfigXml()) {

            boolean isMapId = false;

            @Override
            public boolean tagStart(XmlPullParser parser, String tagName) throws Exception {

                if (tagName.equals("map")) {

                    String id = parser.getAttributeValue(null, "id");
                    isMapId = id.equals(mapId);

                    if (id.equals(DEFAULT_MAP_ID) || isMapId){
                        // save definition for default or needed map by mapId
                        String strCityLevels = parser.getAttributeValue(null, "cityLevels");
                        String strRegionLevel = parser.getAttributeValue(null, "regionLevel");
                        cityAdminLevels = parseCityRange(strCityLevels, parser);
                        regionAdminLevel = parseRegionLevel(strRegionLevel, parser);
                    }
                }

                if (isMapId && tagName.equals("boundaryMapper")){
                    parseCityBoundaryMapper (parser);
                }
                if (isMapId && tagName.equals("boundaryPhrases")){
                    parseBoundaryPhrases(parser);
                }

                return true;
            }

            @Override
            public boolean tagEnd(XmlPullParser parser, String tagName) throws Exception {

                return true;
            }

            @Override
            public void parsingFinished(boolean success) {}
        };

        parser.parse();
    }


    /**
     * Parse definition which city id has some boundary id
     *
     * @param parser xml definition parser
     */
    private void parseCityBoundaryMapper(XmlPullParser parser) {
        String cityid = parser.getAttributeValue(null, "cityid");
        String boundaryid = parser.getAttributeValue(null, "boundaryid");
        String cityname = parser.getAttributeValue(null, "cityname");
        String boundaryname = parser.getAttributeValue(null, "boundaryname");

        if ( !Utils.isNumeric(cityid) || !Utils.isNumeric(boundaryid)){
            throw new IllegalArgumentException("Address definition XML not valid. " +
                    "Wrong cityid or boundaryid on line: " + parser.getLineNumber()  + ", cityid: " + cityid +
                    ", boundaryid: " + boundaryid);
        }

        bundaryCityMapper.put(Long.valueOf(boundaryid), Long.valueOf(cityid));
    }

    /**
     * Parse individual words / phrases for string of boundary phrases
     * @param parser
     */
    private void parseBoundaryPhrases(XmlPullParser parser) {
        String value = parser.getAttributeValue(null, "phrases");

        if (value == null || value.length() == 0){
            return;
        }
        if (value.contains(BOUNDARY_PHRASES_SEPARATOR)){
            String [] phrases = value.split(BOUNDARY_PHRASES_SEPARATOR);
            for (String word : phrases){
                boundaryPhrases.add(word.trim());
            }
        }
        else {
            boundaryPhrases.add(value.trim());
        }

        for (String word : boundaryPhrases) {
            Logger.i(TAG, " boudary phrases: " + word);
        }
    }


    /**
     * Convert string of admin levels value into array of integer that define supported admin levels for cities
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



    public THashSet<String> getBoundaryPhrases() {
        return boundaryPhrases;
    }

    /**
     * Define area for which will be created addresses
     * @return
     */
    public Geometry getDatabaseGeom() {
        return databaseGeom;
    }

    public ConfigurationAddress getConfAddress() {
        return confAddress;
    }

}

