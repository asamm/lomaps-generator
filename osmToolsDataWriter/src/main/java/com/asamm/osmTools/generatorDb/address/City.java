package com.asamm.osmTools.generatorDb.address;

import com.asamm.osmTools.generatorDb.utils.GeomUtils;
import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.geom.Point;
import gnu.trove.map.hash.THashMap;

import java.util.Map;

/**
 * Created by voldapet on 2015-08-14 .
 */
public class City {



    public enum CityType {
        // types and radius
        CITY(10000, 1), TOWN(4000, 2), VILLAGE(1750, 3), HAMLET(750, 4), SUBURB(750, 5), DISTRICT(400, 6);

        /** predefined radius in meters for city type*/
        private double radius;

        /** Num interpretation of type of a city. Lower type is bigger city */
        private int typeCode;


        private CityType(double radius, int typeCode) {
            this.radius = radius;
            this.typeCode = typeCode;
        }

        public double getRadius() {
            return radius;
        }

        public int getTypeCode() {
            return typeCode;
        }

        public static String valueToString(CityType cityType) {
            return cityType.toString().toLowerCase();
        }


        public static CityType createFromPlaceValue(String place) {
            if (place == null) {
                return null;
            }

            for (CityType cityType : CityType.values()) {
                if (cityType.name().equalsIgnoreCase(place)) {
                    return cityType;
                }
            }
            return null;
        }

        public static CityType createFromTypeCodeValue(int typeCode) {
            if (typeCode == 0) {
                return null;
            }

            for (CityType cityType : CityType.values()) {
                if (cityType.getTypeCode() == typeCode) {
                    return cityType;
                }
            }
            return null;
        }
    }
    // -------- END OF CITY TYPE ENUM ----------


    /** Same as OSM Node or id of relation/way */
    private long osmId;

    /** Name of the city in local lang*/
    private String name;

    /** Multilang codes*/
    protected Map<String, String> names;

    /** Center of the city*/
    private Point center;

    /** Size of city*/
    private CityType type;

    private String isIn;

    /** All possible international languages of cities, <langCode|name> */
    private THashMap<String, String> namesInternational;

    /** Administrative place for villages */
    private City parentCity;

    /** Admin region where city is in*/
    private Region region;

    /** is capital city */
    private String capital;

    /** The number of citizens in a given city   (used for city importance)  */
    private int population;

    /** URL of city   (used for city importance)*/
    private String website;

    /** part of URL for wiki page about place   (used for city importance)*/
    private String wikipedia;


    /** Boundary of city - can be null*/
    private MultiPolygon geom;

    public City () {

    }

    public City(CityType type) {
        reset();
        this.type = type;
    }


    private void reset () {
        name = "";
        isIn = "";
        capital = "";
        website = "";
        wikipedia = "";
        this.namesInternational = new THashMap<>();
    }

    public boolean isValid () {
        if (name == null || name.length() == 0){
            return false;
        }
        if (center == null){
            return false;
        }
        if (type == null){
            return false;
        }
        return true;
    }

    /**
     * Add values as geometry, population from boundary to city object.
     * @param boundary boundary to assign to city
     */
    public void combineWithBoundary(Boundary boundary){
        // set city geometry from boundary
        this.setGeom(boundary.getGeom());

        if (population == 0){
            population = boundary.getPopulation();
        }
        if (wikipedia.length() == 0){
            setWikipedia(boundary.getWikipedia());
        }
        if (website.length() == 0){
            setWebsite(boundary.getWebsite());
        }
        if (boundary.getNameLangs().size() > 0){
            this.namesInternational.putAll(boundary.getNameLangs());
        }
    }


    /**************************************************/
    /*             GETTERS & SETTERS
    /**************************************************/

    public long getOsmId() {
        return osmId;
    }

    public void setOsmId(long osmId) {
        this.osmId = osmId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        if (name != null){
            this.name = name;
        }
    }

    public Point getCenter() {
        return center;
    }

    public void setCenter(Point center) {
        this.center = center;
    }

    public CityType getType() {
        return type;
    }

    public void setType(CityType type) {
        this.type = type;
    }

    public String getIsIn() {
        return isIn;
    }

    public String getIsInValue() {
        return isIn;
    }

    public void setIsIn(String isIn) {
        if (isIn != null){
            this.isIn = isIn;
        }
    }

    /**
     * Add city name in lang mutation
     * @param langCode code of language
     * @param name name of the city in specified language
     */
    public void addNameInternational (String langCode, String name){
        this.namesInternational.put(langCode, name);

    }

    public THashMap<String, String> getNamesInternational() {
        return namesInternational;
    }

    public void setNamesInternational(THashMap<String, String> namesInternational) {
        this.namesInternational = namesInternational;
    }

    public City getParentCity() {
        return parentCity;
    }

    public void setParentCity(City parentCity) {
        this.parentCity = parentCity;
    }

    public Region getRegion() {
        return region;
    }

    public void setRegion(Region region) {
        this.region = region;
    }

    public String getCapital() {
        return capital;
    }

    public void setCapital(String capital) {
        if (capital != null){
            this.capital = capital;
        }
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

    public MultiPolygon getGeom() {
        return geom;
    }

    public void setGeom(MultiPolygon geom) {
        this.geom = geom;
    }

//    @Override
//    public int hashCode() {
//        int hash = 1;
//        hash = hash * 31 + Long.valueOf(id).hashCode();
//        return hash;
//    }

    @Override
    public String toString() {
        return "City{" +
                "id=" + osmId +
                ", name='" + name + '\'' +
                ", coordinate=" + center +
                ", type=" + type +
                ", isIn='" + isIn + '\'' +
                ", population=" + population +
                ", namesInternational size=" + namesInternational.size()  +
                ", geom=" + GeomUtils.geomToGeoJson(geom) +
                '}';
    }
}
