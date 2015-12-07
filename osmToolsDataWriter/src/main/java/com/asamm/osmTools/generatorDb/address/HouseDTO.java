package com.asamm.osmTools.generatorDb.address;

import com.asamm.osmTools.generatorDb.utils.OsmUtils;
import com.asamm.osmTools.generatorDb.utils.Utils;
import com.asamm.osmTools.utils.Logger;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.MultiLineString;
import com.vividsolutions.jts.geom.Point;
import locus.api.utils.DataWriterBigEndian;

import java.io.IOException;

/**
 * Created by voldapet on 2015-11-26 .
 * Custom object used for serialization of {@link com.asamm.osmTools.generatorDb.address.House} object
 *
 */
public class HouseDTO {

    private static final String TAG = HouseDTO.class.getSimpleName();

    private static final int COORDINATE_POW = 100000;

    /** House number */
    private String number;

    /** The name of a house..*/
    private String name;

    /** Reference to table of postcodes*/
    private int postCodeId;

    /** Position of house. it's difference from the first note of parent street*/
    private short lon;

    private short lat;

    public HouseDTO(String number, String name, int postCodeId, Point center, Street street) {

        reset();

        setNumber(number);
        setName(name);
        this.postCodeId = postCodeId;

        Logger.i(TAG, "Convert house to DTO : " +
                "\n HouseCenter: " + Utils.geomToGeoJson(center) +
                "\n Street:  " + street.toString());

            MultiLineString mls = street.getGeometry();
            Coordinate[] coordinates = mls.getCoordinates();

            //Coordinate streetFirstNode = street.getGeometry().getCoordinates()[0];
            Coordinate streetFirstNode = coordinates[0];

            int dLon = (int) Math.round((streetFirstNode.x - center.getX()) * COORDINATE_POW);
            int dLat = (int) Math.round((streetFirstNode.y - center.getY()) * COORDINATE_POW);


            this.lon = Utils.intToShort(dLon);
            this.lat = Utils.intToShort(dLat);


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
            dr.writeShort(lon);
            dr.writeShort(lat);

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

    public void setLon(short lon) {
        this.lon = lon;
    }

    public int getLat() {
        return lat;
    }

    public void setLat(short lat) {
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
