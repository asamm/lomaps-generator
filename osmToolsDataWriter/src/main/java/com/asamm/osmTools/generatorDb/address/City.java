package com.asamm.osmTools.generatorDb.address;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Point;

/**
 * Created by voldapet on 2015-08-14 .
 */
public class City {

    public enum CityType {
        // types and radius
        CITY(10000), TOWN(5000), VILLAGE(1300), HAMLET(1000), SUBURB(400), DISTRICT(400);

        /** predefined redius in meters for city type*/
        private double radius;

        private CityType(double radius) {
            this.radius = radius;
        }

        public double getRadius() {
            return radius;
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
    }
    // -------- END OF CITY TYPE ENUM ----------

    /** Same as OSM Node id but some City are not created from Node place and for this reason is id zero*/
    private long id;

    /** Name of the city*/
    private String name;

    /** Center of the city*/
    private Point center;

    /** Size of city*/
    private CityType type;

    //TODO LIST OF STREETS
    //private List<Street> listOfStreets = new ArrayList<Street>();

    private String isIn;

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
                '}';
    }
}
