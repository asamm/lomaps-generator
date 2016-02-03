package com.asamm.osmTools.generatorDb.address;

import com.asamm.osmTools.generatorDb.utils.Utils;
import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.geom.Point;
import gnu.trove.map.hash.THashMap;

import java.util.Map;

/**
 * Created by voldapet on 2015-08-14 .
 */
public class Region {


    /** Same as OSM Node or id of relation/way */
    private long osmId;

    /** Name of the region in local lang*/
    private String name;

    /** All possible international languages of cities, <langCode|name> */
    private THashMap<String, String> namesInternational;

    /** Boundary of city - can be null*/
    private MultiPolygon geom;

    public Region() {

    }

    public Region(long osmId, String name, THashMap<String, String> namesInternational, MultiPolygon geom) {
        this.osmId = osmId;
        this.name = name;
        this.namesInternational = namesInternational;
        this.geom = geom;
    }

    private void reset () {
        name = "";
        this.namesInternational = new THashMap<>();
    }

    public boolean isValid () {
        if (name == null || name.length() == 0){
            return false;
        }
        if (geom == null){
            return false;
        }
        return true;
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        if (name != null){
            this.name = name;
        }
    }

    /**
     * Add city name in lang mutation
     * @param langCode code of language
     * @param name name of the city in specified language
     */
    public void addNameInternational (String langCode, String name){
        this.namesInternational.put(langCode, name);

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
        return "Region{" +
                "id=" + osmId +
                ", name='" + name + '\'' +
                ", geom=" + Utils.geomToGeoJson(geom) +
                '}';
    }
}
