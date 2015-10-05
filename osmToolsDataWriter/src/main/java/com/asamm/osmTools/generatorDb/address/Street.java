package com.asamm.osmTools.generatorDb.address;

import com.asamm.osmTools.generatorDb.utils.Utils;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.MultiLineString;
import com.vividsolutions.jts.geom.Point;
import locus.api.objects.Storable;
import locus.api.utils.DataReaderBigEndian;
import locus.api.utils.DataWriterBigEndian;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by voldapet on 2015-08-22 .
 */
public class Street  {

    /** It's not OSM entity id > id for database */
    private long id;

    /** Name of the street */
    private String name;

    /** OSM id of place in which is street located. basicaly is used only for wayStreet when crate hash */
    private long cityId;

    /** IDs of cities in which can be this way*/
    private List<Long> cityIds;

    /** Splitted is_in tag*/
    private List<String> isIn;

    /** JTS multiline geometry of the street*/
    private MultiLineString geometry;

    public Street (){

    }


    public Street(String name, List<String> isInList, MultiLineString mls) {

        this.name = name;
        this.isIn = isInList;
        this.geometry = mls;

    }

    /** Constructor for copy of object */
    public Street(Street street) {
        this.id = street.id;
        this.name = street.name;
        this.cityId = street.cityId;
        this.cityIds = street.cityIds;
        this.isIn = street.isIn;
        this.geometry = street.geometry;
    }

    public boolean isValid () {

        if (name == null || name.length() == 0){
            return false;
        }
        if (geometry == null){
            return false;
        }
        if (cityIds.size() <= 0){
            return false;
        }
//        if (cityId == 0) {
//            return false;
//        }
        return true;
    }



    public void reset() {
        this.id = -1;
        this.name = "";
        this.cityId = -1;
        this.cityIds = new ArrayList<>();
        this.isIn = new ArrayList<>();
        this.geometry = new GeometryFactory().createMultiLineString(null);

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

    public long getCityId() {
        return cityId;
    }

    public void setCityId(long cityId) {
        this.cityId = cityId;
    }

    public List<Long> getCityIds() {
        return cityIds;
    }

    public void setCityIds(List<Long> cityIds) {
        if (cityIds != null){
            this.cityIds = cityIds;
        }
    }

    public void addCityId(long cityId) {
        if (cityIds == null){
            cityIds = new ArrayList<>();
        }
        cityIds.add(cityId);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<String> getIsIn() {
        return isIn;
    }

    public void setIsIn(List<String> isIn) {
        this.isIn = isIn;
    }

    public MultiLineString getGeometry() {
        return geometry;
    }

    public void setGeometry(MultiLineString geometry) {
        this.geometry = geometry;
    }

    @Override
    public String toString() {
        return "Street{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", isIn=" + isIn +
                ", geometry=" + Utils.geomToGeoJson(geometry) +
                '}';
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
                .append(this.cityId)
                .append(this.name)
                .toHashCode();
    }


    
}