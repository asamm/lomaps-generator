package com.asamm.osmTools.generatorDb.address;

import com.asamm.osmTools.generatorDb.utils.Utils;
import com.asamm.osmTools.utils.Logger;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.io.ParseException;
import com.vividsolutions.jts.io.WKBReader;
import com.vividsolutions.jts.io.WKBWriter;
import locus.api.objects.Storable;
import locus.api.utils.DataReaderBigEndian;
import locus.api.utils.DataWriterBigEndian;

import java.io.IOException;

/**
 * Created by voldapet on 2015-11-04 .
 */
public class House extends Storable{

    private static final String TAG = House.class.getSimpleName();




    public enum AddrInterpolationType {
        ALL(1), EVEN(2), ODD(2), ALPHABETIC(1), INTEGER(1), NONE(1);

        /** Step in interpolation */
        private int step;

        private AddrInterpolationType (int step){
            this.step = step;
        }

        /**
         * Find proper enum based on value of tag addr:interpolation
         * @param value content of tag addr:interpolation
         * @return type of interpolation or None if interpolation value is unknown or not defined
         */
        public static AddrInterpolationType fromValue(String value){

            if (value == null){
                return NONE;
            }

            if (Utils.isInteger(value)){
                return INTEGER;
            }
            for(AddrInterpolationType ait : values()) {
                if (ait.name().equalsIgnoreCase(value)) {
                    return ait;
                }
            }
            return NONE;
        }

        public int getStep (){
            return step;
        }
    }


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

     /** Postal code */
    String postCode;

    /** Position of house */
    Point center;

    /** The type of interpolation*/
    AddrInterpolationType addrInterpolationType;

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
        this.addrInterpolationType = AddrInterpolationType.NONE;
        this.postCode = "";
        this.streetName = "";
        this.cityName = "";


    }

    @Override
    protected void readObject(int version, DataReaderBigEndian dr) throws IOException {

        this.osmId = dr.readLong();
        this.number = dr.readString();
        this.name = dr.readString();
        this.postCode = dr.readString();
        this.addrInterpolationType = AddrInterpolationType.fromValue(dr.readString());

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
        dw.writeString(addrInterpolationType.name());

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

    public AddrInterpolationType getHouseInterpolation() {
        return addrInterpolationType;
    }

    public void setHouseInterpolation(AddrInterpolationType addrInterpolationType) {
        this.addrInterpolationType = addrInterpolationType;
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

    public String getPostCode() {
        return postCode;
    }

    public void setPostCode(String postCode) {
        if (postCode != null){
            this.postCode = postCode;
        }
    }

    @Override
    public String toString() {
        return "House{" +
                "osmId=" + osmId +
                ", number='" + number + '\'' +
                ", name='" + name + '\'' +
                ", streetName='" + streetName + '\'' +
                ", cityName='" + cityName + '\'' +
                ", postCode='" + postCode + '\'' +
                ", addrInterpolationType=" + addrInterpolationType +
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
