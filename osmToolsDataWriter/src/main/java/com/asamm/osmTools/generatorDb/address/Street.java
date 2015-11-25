package com.asamm.osmTools.generatorDb.address;

import com.asamm.osmTools.generatorDb.utils.Utils;
import com.asamm.osmTools.utils.Logger;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.MultiLineString;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.io.ParseException;
import com.vividsolutions.jts.io.WKBReader;
import com.vividsolutions.jts.io.WKBWriter;
import gnu.trove.iterator.TLongIterator;
import gnu.trove.set.hash.THashSet;
import gnu.trove.set.hash.TLongHashSet;
import locus.api.objects.Storable;
import locus.api.utils.DataReaderBigEndian;
import locus.api.utils.DataWriterBigEndian;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.openstreetmap.osmosis.core.domain.v0_6.Tag;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by voldapet on 2015-08-22 .
 */
public class Street extends Storable {

    private static final String TAG = Street.class.getSimpleName();

    /** It's not OSM entity id > id for database */
    private long id;

    /** Name of the street */
    private String name;

    /** OSM id of place in which is street located. basicaly is used only for wayStreet when crate hash */
    private long cityId;

    /** IDs of cities in which can be this way*/
    private TLongHashSet cityIds;

    /** Splitted is_in tag*/
    private List<String> isIn;

    private List<House> houses;

    /** JTS multiline geometry of the street*/
    private MultiLineString geometry;

    public Street (){

    }


    public Street(String name, List<String> isInList, MultiLineString mls) {

        setName(name);
        this.isIn = isInList;
        this.geometry = mls;

    }

    public Street (byte[] data) throws IOException {
        super(data);
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
        this.cityIds = new TLongHashSet();
        this.isIn = new ArrayList<>();
        this.houses = new ArrayList<>();
        this.geometry = new GeometryFactory().createMultiLineString(null);

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

        cityId = dr.readLong();
        name = dr.readString();
        //read list of cityIds
        int size = dr.readInt();
        cityIds = new TLongHashSet();
        for (int i=0; i < size; i++){
            cityIds.add(dr.readLong());
        }

        size = dr.readInt();
        houses = new ArrayList<>();
        for (int i=0; i < size; i++){
            houses.add(new House(dr));
        }

        WKBReader wkbReader = new WKBReader();
        int count = dr.readInt();
        try {
            geometry = ((MultiLineString) wkbReader.read(dr.readBytes(count)));
        } catch (ParseException e) {
            Logger.e(TAG, "Can not read street", e);
        }
    }

    @Override
    protected void writeObject(DataWriterBigEndian dw) throws IOException {

        dw.writeLong(cityId);
        dw.writeString(name);
        // write list of city ids
        dw.writeInt(cityIds.size());
        TLongIterator iterator = cityIds.iterator();
        while (iterator.hasNext()){
            dw.writeLong(iterator.next());
        }
        dw.writeInt(houses.size());
        for (House house : houses){
            dw.write(house.getAsBytes());
        }
        WKBWriter wkbWriter = new WKBWriter();
        byte[] geomData = wkbWriter.write(geometry);
        dw.writeInt(geomData.length);
        dw.write(geomData);
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

    public TLongHashSet getCityIds() {
        return cityIds;
    }

    public void setCityIds(TLongHashSet cityIds) {
        if (cityIds != null){
            this.cityIds = cityIds;
        }
    }

    public void addCityIds (List<City> cities) {

        for (City city : cities) {
            cityIds.add(city.getId());
        }
    }

    public void addCityId(long cityId) {
        if (cityIds == null){
            cityIds = new TLongHashSet();
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

    public void addHouse (House house){
        houses.add(house);
    }

    public List<House> getHouses() {
        return houses;
    }

    public void setHouses(List<House> houses) {
        this.houses = houses;
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
                ", houses size=" + houses.size() +
                ", geometry=" + Utils.geomToGeoJson(geometry) +
                '}';
    }
}
