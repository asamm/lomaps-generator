package com.asamm.osmTools.generatorDb.address;

import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;
import gnu.trove.map.hash.THashMap;
import org.openstreetmap.osmosis.core.domain.v0_6.EntityType;
import org.wololo.geojson.GeoJSON;
import org.wololo.jts2geojson.GeoJSONWriter;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by voldapet on 2015-8-14 .
 */
public class Boundary {

    /** Can OSM relation ID or OSM simple way id*/
    private long id;

    /** If boudnary is created only from WAY or from RELATION*/
    private EntityType entityType;

    private String name;

    /** Common abbreviation, useful for example for nominatim searching*/
    private String shortName;

    private int adminLevel;

    /** It's not null if relation/ways has tag place. So boundary is exactly the city*/
    private City.CityType cityType;

    /** OSM Id of entity that is set as center of boundary (should be a place)*/
    private long adminCenterId;

    /** All possible international languages  <langCode|name> */
    private THashMap<String, String> nameLangs;

    /** All possible official names, <langCode|name> This is used for country borders*/
    private THashMap<String, String> officialNamesInternational;

    /*
     * Other possible name mutation for boudnary, like int_name, loc_name,..
     */
    private List<String> namesAlternative;


    /** The number of citizens in a given city   (used for city importance)  */
    private int population;

    /** URL of city   (used for city importance)*/
    private String website;

    /** part of URL for wiki page about place   (used for city importance)*/
    private String wikipedia;

    private MultiPolygon geom;

    /**
     *
     * @param id entity id
     */
    public Boundary (long id, EntityType entityType){
        reset();

        this.id = id;
        this.entityType = entityType;
    }

    public boolean isValid() {
        if (name == null || name.length() == 0){
            return false;
        }
        if (getCenterPoint().getCoordinate() == null){
            return false;
        }

        return true;
    }

    public boolean hasAdminCenterId(){
        return adminCenterId != 0;
    }

    /**
     * Test if CityType is defined for this boundary
     * @return true if city type is defined
     */
    public boolean hasCityType() {
        return cityType != null;
    }

    public boolean hasAdminLevel () {
        return adminLevel > 0;
    }

    public Point getCenterPoint() {
        return geom.getCentroid();
    }

    private void reset () {

        name = "";
        shortName = "";
        website = "";
        wikipedia = "";
    }

    /**
     * Print geometry of bounds as geojson string
     * @return
     */
    public String toGeoJsonString () {

        if (geom == null){
            return "";
        }
        GeoJSONWriter writer = new GeoJSONWriter();
        GeoJSON json = writer.write(geom);
        return json.toString();
    }

    /**************************************************/
    /*             GETTERS & SETTERS
    /**************************************************/

    public long getId() {
        return id;
    }

    public EntityType getEntityType() {
        return entityType;
    }

    // DEFAULT LOCAL NAME

    public String getName() {
        return name;
    }

    public void setName(String name) {
        if (name != null){
            this.name = name;
        }
    }

    // SHORT NAME

    public String getShortName (){
        return this.shortName;
    }

    public void setShortName(String shortName) {
        if (shortName != null){
            this.shortName = shortName;
        }
    }

    // CITY TYPE

    public City.CityType getCityType() {
        return cityType;
    }

    public void setCityType(City.CityType cityType) {
        this.cityType = cityType;
    }

    // ADMIN LEVEL

    public int getAdminLevel() {
        return adminLevel;
    }

    public void setAdminLevel(int adminLevel) {
        this.adminLevel = adminLevel;
    }

    // ADMIN CENTER

    public long getAdminCenterId() {
        return adminCenterId;
    }

    public void setAdminCenterId(long adminCenterId) {
        this.adminCenterId = adminCenterId;
    }

    // NAMES MULTILANG

    public THashMap<String, String> getNameLangs() {
        return nameLangs;
    }

    public void setNameLangs(THashMap<String, String> nameLangs) {
        this.nameLangs = nameLangs;
    }

    // OFICIAL NAMES MULTILANG

    public THashMap<String, String> getOfficialNamesInternational() {
        return officialNamesInternational;
    }

    public void setOfficialNamesInternational(THashMap<String, String> officialNamesInternational) {
        this.officialNamesInternational = officialNamesInternational;
    }

    // ALTERNATIVE NAMES INTERNATIONAL

    public List<String> getNamesAlternative() {
        return namesAlternative;
    }

    public void setNamesAlternative(List<String> namesAlternative) {
        this.namesAlternative = namesAlternative;
    }

    public void addNameAlternative (String nameOther){
        if (nameOther == null){
            return;
        }
        if (this.namesAlternative == null){
            this.namesAlternative =  new ArrayList<>();
        }
        this.namesAlternative.add(nameOther);
    }

    // GEOMETRY

    public MultiPolygon getGeom() {
        return geom;
    }


    public int getPopulation() {
        return population;
    }

    public void setPopulation(int population) {
        this.population = population;
    }

    public String getWebsite() {
        return website;
    }

    public void setWebsite(String website) {
        if (website != null){
            this.website = website;
        }
    }

    public String getWikipedia() {
        return wikipedia;
    }

    public void setWikipedia(String wikipedia) {
        if (wikipedia != null){
            this.wikipedia = wikipedia;
        }
    }

    public void setGeom(MultiPolygon geom) {
        this.geom = geom;
    }


    @Override
    public String toString() {
        return "Boundary{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", shortName='" + shortName + '\'' +
                ", adminLevel=" + adminLevel +
                ", cityType=" + cityType +
                ", adminCenterId=" + adminCenterId +
                ", bounds=" + toGeoJsonString() +
                '}';
    }


}
