package com.asamm.osmTools.generatorDb.data;

import org.openstreetmap.osmosis.core.domain.v0_6.Node;

public class OsmAddress extends AOsmObject {

	public static OsmAddress create(Node node) {
		// immediately eliminate nodes without tags
		if (node.getTags() == null || node.getTags().size() == 0) {
			return null;
		}
		
		// process nodes
		OsmAddress poi = new OsmAddress(node);
		if (poi.isValid()) {
			return poi;
		} else {
			return null;
		}
	}

	private String city;
	private String conscriptionNumber;
	private String country;
	private String houseName;
	private String houseNumber;
	private String place;
	private String postCode;
	private String street;
	private String streetNumber;
	
	public OsmAddress(Node node) {
		super(node);
	}

	@Override
	protected boolean isValidPrivate() {
		return houseNumber != null && houseNumber.length() > 0;
	}

	@Override
	protected boolean handleTag(String key, String value) {
		if (key.startsWith("addr:")) {
			if ("addr:city".equals(key)) {
				this.city = value;
			} else if ("addr:conscriptionnumber".equals(key)) {		// číslo popisné
				this.conscriptionNumber = value;
			} else if ("addr:country".equals(key)) {				// alt. - country
				this.country = value;
			} else if ("addr:full".equals(key)) {
				// handle separately
			} else if ("addr:housename".equals(key)) {
				this.houseName = value;
			} else if ("addr:housenumber".equals(key)) {
				this.houseNumber = value;
			} else if ("addr:place".equals(key)) {
				this.place = value;
			} else if ("addr:postcode".equals(key)) {
				this.postCode = value;
			} else if ("addr:street".equals(key)) {					// alt. - "postal_code"
				this.street = value;
			} else if ("addr:streetnumber".equals(key)) {			// číslo orientační
				this.streetNumber = value;
			}
			return true;
		}
		return false;
	}
}
