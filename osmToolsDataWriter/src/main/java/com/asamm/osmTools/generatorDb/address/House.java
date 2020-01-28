package com.asamm.osmTools.generatorDb.address;

import com.asamm.osmTools.generatorDb.utils.GeomUtils;
import com.asamm.osmTools.utils.Logger;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.io.ParseException;
import com.vividsolutions.jts.io.WKBReader;
import com.vividsolutions.jts.io.WKBWriter;
import locus.api.objects.Storable;
import locus.api.utils.DataReaderBigEndian;
import locus.api.utils.DataWriterBigEndian;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Address Place named as house
 * Created by voldapet on 2015-11-04 .
 */
public class House extends Storable{

    private static final String TAG = House.class.getSimpleName();

    /** OSM id of entity from which was house created. It's not unique */
    private long osmId = 0;

    /** The house number (may contain letters, dashes or other characters). */
    private String number = "";

    /** The name of a house.
     This is sometimes used in some countries like England instead of (or in addition to) a house number.*/
    private String name = "";

    /** For value addr:street*/
    private String streetName = "";

    /** For value addr:city*/
    private String cityName = "";

    private String place = "";

    /** Value of is_in tag, Used only for houses without sreeet*/
    private List<String> isIn = new ArrayList<>();

    /**  Serialized house does not contain whole postCode but only reference to table of postcodes*/
    private int postCodeId;

    /** Position of house */
    private Point center;

    public House() {
        super();
    }

    public House(long osmId, String number, String name, int postCodeId, Point center) {

        this.osmId = osmId;
        setNumber(number);
        setName(name);
        this.postCodeId = postCodeId;
        this.center = center;
    }

    public boolean isValid() {
        if (number.length() == 0 && name.length() == 0){
            return false;
        }
        if (center == null || !center.isValid()){
            return false;
        }
        return true;
    }

    /**
     * Test if street name for house is defined
     * @return true if house has street name
     */
    public boolean hasStreetName () {
        return streetName.length() > 0;
    }

    /**
     * Test if place name for house is defined
     * @return true if place name is defined
     */
    public boolean hasPlaceName () {
        return streetName.length() > 0;
    }

    /**************************************************/
    /*             STORABLE PART
    /**************************************************/



    @Override
    protected int getVersion() {
        return 0;
    }

    @Override
    protected void readObject(int version, DataReaderBigEndian dr) throws IOException {

        this.osmId = dr.readLong();
        this.number = dr.readString();
        this.name = dr.readString();
        this.postCodeId = dr.readInt();

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
        dw.writeInt(postCodeId);

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

    /**
     * This value is filled only for some houses that are processed as house without street
     * @return
     */
    public List<String> getIsIn() {
        return isIn;
    }

    public void setIsIn(List<String> isIn) {
        if (isIn != null) {
            this.isIn = isIn;
        }
    }

    public void addIsIn(String isInName) {
        if (isInName != null){
            this.isIn.add(isInName);
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
                ", postCodeId='" + postCodeId + '\'' +
                ", center=" + GeomUtils.geomToGeoJson(center) +
                '}';
    }



    @Override
    public int hashCode() {
        int hash = 17;
        hash = hash * 37 + center.hashCode();
        hash = hash * 37 + number.hashCode();
        //hash = hash * 37 + streetName.hashCode();
        //hash = hash * 37 + place.hashCode();
        return hash;
    }


}
