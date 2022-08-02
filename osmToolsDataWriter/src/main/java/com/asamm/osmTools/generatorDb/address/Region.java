package com.asamm.osmTools.generatorDb.address;

import com.asamm.osmTools.generatorDb.utils.GeomUtils;
import org.locationtech.jts.geom.MultiPolygon;
import gnu.trove.map.hash.THashMap;
import org.openstreetmap.osmosis.core.domain.v0_6.EntityType;

/**
 * Created by voldapet on 2015-08-14 .
 */
public class Region {


    /*
     * Same as OSM Node or id of relation/way
     */
    private long osmId;

    /**
     * If region boundary is created only from WAY or from RELATION
     */
    private EntityType entityType;

    /*
     * Name of the region in local lang
     */
    private String name;

    /*
     * The OSM admin level value
     */
    private int adminLevel;

    /*
         * ISO Alpha country code
         */
    private String regionCode;

    /*
     * All possible international languages of cities, <langCode|name>
     */
    private THashMap<String, String> namesInternational;

    /*
     * Boundary of city - can be null
     */
    private MultiPolygon geom;

    public Region() {
        reset();
    }

    public Region(long osmId, EntityType entityType, String regionCode, String name,
                  THashMap<String, String> namesInternational, MultiPolygon geom) {

        reset();

        this.osmId = osmId;
        this.entityType = entityType;
        this.regionCode = regionCode;
        this.name = name;
        this.namesInternational = namesInternational;
        this.geom = geom;
    }

    private void reset () {
        name = "";
        regionCode = "";
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

    public EntityType getEntityType() {
        return entityType;
    }

    public void setEntityType(EntityType entityType) {
        this.entityType = entityType;
    }


    public String getRegionCode() {
        return regionCode;
    }

    public void setRegionCode(String regionCode) {
        if (regionCode != null){
            this.regionCode = regionCode;
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

    /**
     * Try to get English name for region if not defined fallback and get native name
     * @return name of region
     */
    public String getEnName () {
        String nameEn = namesInternational.get("en");
        return (nameEn == null) ? this.name : nameEn;
    }

    public int getAdminLevel() {
        return adminLevel;
    }

    public void setAdminLevel(int adminLevel) {
        this.adminLevel = adminLevel;
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
                ", geom=" + GeomUtils.geomToGeoJson(geom) +
                '}';
    }



}
