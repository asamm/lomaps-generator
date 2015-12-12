package com.asamm.osmTools.generatorDb.address;

import com.asamm.osmTools.generatorDb.utils.OsmUtils;
import com.asamm.osmTools.generatorDb.utils.Utils;
import com.asamm.osmTools.utils.Logger;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.MultiLineString;
import com.vividsolutions.jts.geom.Point;
import locus.api.utils.DataReaderBigEndian;
import locus.api.utils.DataWriterBigEndian;

import java.io.IOException;

/**
 * Created by voldapet on 2015-11-26 .
 * Custom object used for serialization of {@link com.asamm.osmTools.generatorDb.address.House} object
 *
 */
public class HouseDTO {

    private static final String TAG = HouseDTO.class.getSimpleName();

    public static final int COORDINATE_POW = 100000;

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

        MultiLineString mls = street.getGeometry();
        Coordinate streetFirstNode = mls.getCoordinates()[0];
        int dLon = (int) Math.round((center.getX() - streetFirstNode.x) * COORDINATE_POW);
        int dLat = (int) Math.round((center.getY() - streetFirstNode.y) * COORDINATE_POW);

        this.lon = Utils.intToShort(dLon);
        this.lat = Utils.intToShort(dLat);
    }

    public HouseDTO (DataReaderBigEndian dr) throws IOException {
        if (dr.available() == 0){
            throw new IOException("Invalid size");
        }
        byte bHeader = dr.readBytes(1)[0];

        if (isHouseNumberDefined(bHeader)){
            this.number = dr.readString();
        }
        if (isHouseNameDefined(bHeader)){
            this.name = dr.readString();
        }
        if (isPostcodeIdDefined(bHeader)){
            this.postCodeId = dr.readInt();
        }

        this.lon = dr.readShort();
        this.lat = dr.readShort();
    }


    /**************************************************/
    /*             SERIALIZATION PART
    /**************************************************/

    private void reset() {
        this.number = "";
        this.name = "";
        this.postCodeId = -1;
    }


    /**
     * Serialize object to byte array
     * @return Serialized object
     */
    public byte[] getAsBytes() {

        DataWriterBigEndian dw = new DataWriterBigEndian();

        try {

            byte header = createHeader();
            dw.write(header);
            if (isHouseNumberDefined(header)){
                dw.writeString(number);
            }
            if (isHouseNameDefined(header)){
                dw.writeString(name);
            }
            if (isPostcodeIdDefined(header)){
                dw.writeInt(postCodeId);
            }
            dw.writeShort(lon);
            dw.writeShort(lat);

        } catch (IOException e) {
            Logger.e(TAG, "getAsBytes() - Can not serialize house: " + this.toString(), e );
            e.printStackTrace();
        }
        return dw.toByteArray();
    }

    /**
     * Test if serialized house data contains house number
     * @param bHeader first byte of serialized house
     * @return true is data contains information about house number
     */
    private boolean isHouseNumberDefined (byte bHeader){
        if (((bHeader >> 0) & 1) == 1){
            return true;
        }
        return false;
    }

    /**
     * Test if serialized house data contains house name
     * @param bHeader first byte of serialized house
     * @return true is data contains information about house name
     */
    private boolean isHouseNameDefined (byte bHeader){
        if (((bHeader >> 1) & 1) == 1){
            return true;
        }
        return false;
    }
    /**
     * Test if serialized house data contains post code for house
     * @param bHeader first byte of serialized house
     * @return true is data contains information about post code
     */
    private boolean isPostcodeIdDefined(byte bHeader){
        if (((bHeader >> 2) & 1) == 1){
            return true;
        }
        return false;
    }

    /**
     * Create first byte that holds information which values are serialized
      * @return byte inform if house number, name or postcode db id is can be serialized
     */
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
