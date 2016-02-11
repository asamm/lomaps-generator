package com.asamm.osmTools.generatorDb.address;

import com.asamm.osmTools.generatorDb.utils.Utils;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.geom.Point;
import gnu.trove.map.hash.THashMap;
import org.wololo.geojson.GeoJSON;
import org.wololo.jts2geojson.GeoJSONWriter;

/**
 * Created by voldapet on 2015-8-14 .
 */
public class Boundary {

    /** Can OSM relation ID or OSM simple way id*/
    private long id;

    private String name;

    /** Common abbreviation, useful for example for nominatim searching*/
    private String shortName;

    private int adminLevel;

    /** It's not null if relation/ways has tag place. So boundary is exactly the city*/
    private City.CityType cityType;

    /** OSM Id of entity that is set as center of boundary (should be a place)*/
    private long adminCenterId;

    /** All possible international languages of cities, <langCode|name> */
    private THashMap<String, String> namesInternational;

    private MultiPolygon geom;

    /**
     *
     * @param id entity id
     */
    public Boundary (long id){
        reset();

        this.id = id;
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        if (name != null){
            this.name = name;
        }
    }

    public String getShortName() {
        return shortName;
    }

    public void setShortName(String shortName) {
        if (shortName != null){
            this.shortName = shortName;
        }
    }


    public City.CityType getCityType() {
        return cityType;
    }

    public void setCityType(City.CityType cityType) {
        this.cityType = cityType;
    }

    public int getAdminLevel() {
        return adminLevel;
    }

    public void setAdminLevel(int adminLevel) {
        this.adminLevel = adminLevel;
    }

    public long getAdminCenterId() {
        return adminCenterId;
    }

    public void setAdminCenterId(long adminCenterId) {
        this.adminCenterId = adminCenterId;
    }


    public THashMap<String, String> getNamesInternational() {
        return namesInternational;
    }

    public void setNamesInternational(THashMap<String, String> namesInternational) {
        this.namesInternational = namesInternational;
    }

    public MultiPolygon getGeom() {
        return geom;
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
