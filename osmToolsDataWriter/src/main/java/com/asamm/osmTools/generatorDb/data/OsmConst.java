package com.asamm.osmTools.generatorDb.data;

/**
 * Created by voldapet on 2015-08-18 .
 */
public class OsmConst {

    public enum OSMTagKey {

        NAME("name"),
        SHORT_NAME("short_name"),

        ADMIN_LEVEL("admin_level"),
        BOUNDARY("boundary"),
        POSTAL_CODE("postal_code"),

        //relation types
        STREET("street"),
        ASSOCIATED_STREET("associatedStreet"),
        MULTIPOLYGON("multipolygon"),

        // address
        PLACE("place"),
        ADDR_HOUSE_NUMBER("addr:housenumber"),
        ADDR_HOUSE_NAME("addr:housename"),
        ADDR_STREET("addr:street"),
        ADDR_STREET2("addr:street2"),
        ADDR_CITY("addr:city"),
        ADDR_PLACE("addr:place"),
        ADDR_POSTCODE("addr:postcode"),
        ADDR_INTERPOLATION("addr:interpolation"),
        ADDRESS_TYPE("address:type"),
        ADDRESS_HOUSE("address:house"),
        TYPE("type"),
        IS_IN("is_in"),
        LOCALITY("locality"),


        INTERNET_ACCESS("internet_access"),
        CONTACT_WEBSITE("contact:website"),
        CONTACT_PHONE("contact:phone"),
        OPENING_HOURS("opening_hours"),
        PHONE("phone"),

        CAPITAL("capital"),
        DESCRIPTION("description"),
        POPULATION("population"),
        WEBSITE("website"),
        WIKIPEDIA("wikipedia"),
        URL("url"),

        HIGHWAY("highway"),
        BUILDING("building"),

        // residetials
        LANDUSE("landuse")

        ;


        private final String value;

        private OSMTagKey(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;

        }
    }
}