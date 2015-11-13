package com.asamm.osmTools.generatorDb.address;

import com.asamm.osmTools.generatorDb.utils.Utils;
import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.geom.Point;

import java.util.Map;

/**
 * Created by voldapet on 2015-08-14 .
 */
public class City {

    public enum CityType {
        // types and radius
        CITY(10000, 1), TOWN(5000, 2), VILLAGE(1300, 3), HAMLET(1000, 4), SUBURB(400, 5), DISTRICT(400, 6);

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
    private long id;

    /** Name of the city*/
    private String name;

    /** Multilang codes*/
    protected Map<String, String> names;

    /** Center of the city*/
    private Point center;

    /** Size of city*/
    private CityType type;

    private String isIn;

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


    /**************************************************/
    /*             GETTERS & SETTERS
    /**************************************************/

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
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

    public MultiPolygon getGeom() {
        return geom;
    }

    public void setGeom(MultiPolygon geom) {
        this.geom = geom;
    }

    @Override
    public int hashCode() {
        int hash = 1;
        hash = hash * 31 + Long.valueOf(id).hashCode();
        return hash;
    }

    @Override
    public String toString() {
        return "City{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", coordinate=" + center +
                ", type=" + type +
                ", isIn='" + isIn + '\'' +
                ", geom=" + Utils.geomToGeoJson(geom) +
                '}';
    }
}
