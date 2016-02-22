package com.asamm.osmTools.generatorDb.address;

import com.asamm.osmTools.generatorDb.utils.GeomUtils;
import com.asamm.osmTools.generatorDb.utils.Utils;
import com.asamm.osmTools.utils.Logger;
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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by voldapet on 2015-08-22 .
 */
public class Street extends Storable {

    private static final String TAG = Street.class.getSimpleName();

    /** It's not OSM entity id > id for database */
    private int id;

    /** This value is set only for custom wayStreet before joined street is created
     * CAn be id of OSM way or OSM relation*/
    private long osmId;

    /** Name of the street */
    private String name;

    /** OSM id of place in which is street located. basicaly is used only for wayStreet when create hash */
    private long cityId;

    /** IDs of cities in which can be this way*/
    private TLongHashSet cityIds;

    /** Value of is_in tag */
    private List<String> isIn;

    /** keep information if street were created from track or path */
    private boolean isPath;

    private THashSet<House> houses;

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
        return true;
    }



    public void reset() {
        this.id = -1;
        this.osmId = -1;
        this.name = "";
        this.cityId = -1;
        this.cityIds = new TLongHashSet();
        this.isIn = new ArrayList<>();
        this.isPath = false;
        this.houses = new THashSet<>();
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

        osmId = dr.readLong();
        cityId = dr.readLong();
        name = dr.readString();
        //read list of cityIds
        int size = dr.readInt();
        cityIds = new TLongHashSet();
        for (int i=0; i < size; i++){
            cityIds.add(dr.readLong());
        }
        isPath = dr.readBoolean();

        size = dr.readInt();
        houses = new THashSet<>();
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

        dw.writeLong(osmId);
        dw.writeLong(cityId);
        dw.writeString(name);
        // write list of city ids
        dw.writeInt(cityIds.size());
        TLongIterator iterator = cityIds.iterator();
        while (iterator.hasNext()){
            dw.writeLong(iterator.next());
        }
        dw.writeBoolean(isPath);

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


    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public long getOsmId() {
        return osmId;
    }

    public void setOsmId(long osmId) {
        this.osmId = osmId;
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

    public void setCities (List<City> cities) {
        cityIds.clear();
        for (City city : cities) {
            cityIds.add(city.getOsmId());
        }
    }

    public void addCities(List<City> cities) {

        for (City city : cities) {
            cityIds.add(city.getOsmId());
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

    public boolean isPath() {
        return isPath;
    }

    public void setPath(boolean isPath) {
        this.isPath = isPath;
    }

    public THashSet<House> getHouses() {
        return houses;
    }

    public void setHouses(THashSet<House> houses) {
        this.houses = houses;
    }

    public MultiLineString getGeometry() {
        return geometry;
    }

    public void setGeometry(MultiLineString geometry) {
        this.geometry = geometry;
    }

    /**
     * Get centroid of street geom and convert it into integer coordinates
     * @return coordinates of centroid as integer array [lon, lat]
     */
    public int[] getOriginForHouseDTO(){
        Point centroid = geometry.getEnvelope().getCentroid();
        return GeomUtils.pointToIntegerValues(centroid);
    }

    @Override
    public String toString() {
        String str =  "Street{" +
                "id=" + id +
                ", osmId=" + osmId +
                ", name='" + name + '\'' +
                ", isIn=" + isIn +
                ", cityIds=[";

        TLongIterator iterator = cityIds.iterator();
        while (iterator.hasNext()){
            str += iterator.next() + ", " ;
        }
        str +="], isPath=" + isPath +
                ", houses size=" + houses.size() +
                ", geometry=" + Utils.geomToGeoJson(geometry) +
                '}';
        return str;
    }


}
