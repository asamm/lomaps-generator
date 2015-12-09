package com.asamm.osmTools.generatorDb.address;

import com.asamm.osmTools.generatorDb.utils.Utils;
import com.asamm.osmTools.utils.Logger;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.io.ParseException;
import com.vividsolutions.jts.io.WKBReader;
import com.vividsolutions.jts.io.WKBWriter;
import locus.api.objects.Storable;
import locus.api.utils.DataReaderBigEndian;
import locus.api.utils.DataWriterBigEndian;

import java.io.IOException;

/**
 * Address Place named as house
 * Created by voldapet on 2015-11-04 .
 */
public class House extends Storable{

    private static final String TAG = House.class.getSimpleName();


    /** OSM id of entity from which was house created. It's not unique */
    private long osmId;

    /** The house number (may contain letters, dashes or other characters). */
    private String number;

    /** The name of a house.
     This is sometimes used in some countries like England instead of (or in addition to) a house number.*/
    private String name;

    /** For value addr:street*/
    private String streetName;

    /** For value addr:city*/
    private String cityName;

    private String place;

     /** Postal code */
    private String postCode;

    /**  Serialized house does not contain whole postCode but only reference to table of postcodes*/
    private int postCodeId;

    /** Position of house */
    private Point center;

    public House(DataReaderBigEndian dr) throws IOException {
        super(dr);
    }

    public House(long osmId, String number, String name, String postCode, Point center) {

        this.osmId = osmId;
        setNumber(number);
        setName(name);
        setPostCode(postCode);
        this.center = center;
    }

//    public House (HouseDTO houseDTO, Coordinate streetFirstNode){
//
//        double lon = (streetFirstNode.x + houseDTO.getLon() * HouseDTO.COORDINATE_POW);
//        double lat = (streetFirstNode.y + houseDTO.getLat() * HouseDTO.COORDINATE_POW);
//
//        setNumber(houseDTO.getNumber());
//        setName(houseDTO.getName());
//        setPostCode(houseDTO.getPostCodeId());
//
//
//
//    }


    /**************************************************/
    /*             STORABLE PART
    /**************************************************/

    @Override
    protected int getVersion() {
        return 0;
    }

    @Override
    public void reset() {

        this.osmId = 0;
        this.number = "";
        this.name = "";
        this.postCode = "";
        this.streetName = "";
        this.cityName = "";
        this.place = "";
    }

    @Override
    protected void readObject(int version, DataReaderBigEndian dr) throws IOException {

        this.osmId = dr.readLong();
        this.number = dr.readString();
        this.name = dr.readString();
        this.postCode = dr.readString();

        WKBReader wkbReader = new WKBReader();
        int count = dr.readInt();
        try {
            this.center = ((Point) wkbReader.read(dr.readBytes(count)));
        } catch (ParseException e) {
            Logger.e(TAG, "Can not de-serialize center point", e);
        }
    }

    @Override
    protected void writeObject(DataWriterBigEndian dw) throws IOException {
        dw.writeLong(osmId);
        dw.writeString(number);
        dw.writeString(name);
        dw.writeString(postCode);

        WKBWriter wkbWriter = new WKBWriter();
        byte[] geomData = wkbWriter.write(center);
        dw.writeInt(geomData.length);
        dw.write(geomData);

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

    public String getNumber() {
        return number;
    }

    public void setNumber(String number) {
        if (number != null){
            this.number = number;
        }
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

    public String getStreetName() {
        return streetName;
    }

    public void setStreetName(String streetName) {
        if (streetName != null){
            this.streetName = streetName;
        }
    }

    public String getCityName() {
        return cityName;
    }

    public void setCityName(String cityName) {
        if (cityName != null) {
            this.cityName = cityName;
        }
    }

    public String getPlace() {
        return place;
    }

    public void setPlace(String place) {
        if (place != null){
            this.place = place;
        }
    }

    public String getPostCode() {
        return postCode;
    }

    public void setPostCode(String postCode) {
        if (postCode != null){
            this.postCode = postCode;
        }
    }


    public int getPostCodeId() {
        return postCodeId;
    }

    public void setPostCodeId(int postCodeId) {
        this.postCodeId = postCodeId;
    }

    @Override
    public String toString() {
        return "House{" +
                "osmId=" + osmId +
                ", number='" + number + '\'' +
                ", name='" + name + '\'' +
                ", streetName='" + streetName + '\'' +
                ", cityName='" + cityName + '\'' +
                ", place='" + place + '\'' +
                ", postCode='" + postCode + '\'' +
                ", postCodeId='" + postCodeId + '\'' +
                ", center=" + Utils.geomToGeoJson(center) +
                '}';
    }



//    @Override
//    public int hashCode() {
//        int hash = 1;
//        hash = hash * 17 + Long.valueOf(osmId).hashCode();
//        hash = hash * 31 + number.hashCode();
//        return hash;
//    }
}
