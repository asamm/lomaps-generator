package com.asamm.osmTools.generatorDb.address;

import com.asamm.osmTools.utils.Logger;
import com.vividsolutions.jts.geom.Point;
import locus.api.utils.DataWriterBigEndian;

import java.io.IOException;

/**
 * Created by voldapet on 2015-11-26 .
 * Custom object used for serialization of {@link com.asamm.osmTools.generatorDb.address.House} object
 *
 */
public class HouseSer {

    private static final String TAG = HouseSer.class.getSimpleName();

    private static final int COORDINATE_POW = 100000;

    /** House number */
    private String number;

    /** The name of a house..*/
    private String name;

    /** Reference to table of postcodes*/
    private int postCodeId;

    /** Position of house */
    private int lon;

    private int lat;

    public HouseSer(String number, String name, int postCodeId, Point center) {

        reset();

        setNumber(number);
        setName(name);
        this.postCodeId = postCodeId;

        this.lon = (int) Math.round(center.getX() * COORDINATE_POW);
        this.lat = (int) Math.round(center.getY() * COORDINATE_POW);
    }

    /**************************************************/
    /*             SERIALIZATION PART
    /**************************************************/

    private void reset() {
        this.number = "";
        this.name = "";
        this.postCodeId = -1;
    }

    private boolean isHouseNumberDefined (byte header){
        if (((header >> 0) & 1) == 1){
            return true;
        }
        return false;
    }

    private boolean isHouseNameDefined (byte header){
        if (((header >> 1) & 1) == 1){
            return true;
        }
        return false;
    }

    private boolean isPostcodeIdDefined(byte header){
        if (((header >> 2) & 1) == 1){
            return true;
        }
        return false;
    }

    public byte[] getAsBytes() {

        DataWriterBigEndian dr = new DataWriterBigEndian();

        try {

            byte header = createHeader();
            if (isHouseNumberDefined(header)){
                dr.writeString(number);
            }
            if (isHouseNameDefined(header)){
                dr.writeString(name);
            }
            if (isPostcodeIdDefined(header)){
                dr.writeInt(postCodeId);
            }
            dr.writeInt(lon);
            dr.writeInt(lat);

        } catch (IOException e) {
            Logger.e(TAG, "getAsBytes() - Can not serialize house: " + this.toString(), e );
            e.printStackTrace();
        }
        return dr.toByteArray();
    }


    private byte createHeader () {

        byte b = (byte) 0;

        if (number.length() > 0){
            b |= (1 << 0);
        }
        if (name.length() > 0){
            b |= (1 << 1);
        }
        if (postCodeId > 0){
            b |= (1 << 2);
        }

        return b;
    }

    public int getBit(byte b, int position)    {
        return (b >> position) & 1;
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

    public int getPostCodeId() {
        return postCodeId;
    }

    public void setPostCodeId(int postCodeId) {
        this.postCodeId = postCodeId;
    }

    public int getLon() {
        return lon;
    }

    public void setLon(int lon) {
        this.lon = lon;
    }

    public int getLat() {
        return lat;
    }

    public void setLat(int lat) {
        this.lat = lat;
    }

    @Override
    public String toString() {
        return "HouseSer{" +
                "number='" + number + '\'' +
                ", name='" + name + '\'' +
                ", postCodeId=" + postCodeId +
                ", lon=" + lon +
                ", lat=" + lat +
                '}';
    }


}
