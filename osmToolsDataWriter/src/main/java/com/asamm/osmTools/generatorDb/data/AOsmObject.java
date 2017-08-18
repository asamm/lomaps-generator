package com.asamm.osmTools.generatorDb.data;

import com.asamm.locus.features.loMaps.LoMapsDbConst;
import com.asamm.osmTools.generatorDb.utils.OsmTagUsage;
import com.asamm.osmTools.utils.Logger;
import org.openstreetmap.osmosis.core.domain.v0_6.Entity;
import org.openstreetmap.osmosis.core.domain.v0_6.Node;
import org.openstreetmap.osmosis.core.domain.v0_6.Tag;
import org.openstreetmap.osmosis.core.domain.v0_6.Way;

import java.util.Locale;

public abstract class AOsmObject {

	private static final String TAG = AOsmObject.class.getSimpleName();
	
	// current node
    private Entity entity;
	// type of entity
    private LoMapsDbConst.EntityType mEntityType;

    // object ID
    private long mId;
	// name of POI
	private String mName;

	public AOsmObject(Entity entity) {
		this.entity = entity;

        // store id
        this.mId = entity.getId();

		// define entity type
		if (entity instanceof Node) {
			mEntityType = LoMapsDbConst.EntityType.POIS;
		} else if (entity instanceof Way) {
			mEntityType = LoMapsDbConst.EntityType.WAYS;
		}
	}

    public LoMapsDbConst.EntityType getEntityType() {
        return mEntityType;
    }

    public long getId() {
        return mId;
    }

	public String getName() {
		return mName;
	}
	
	protected void setName(String name) {
		if (name != null && name.length() > 0) {
			this.mName = name;
		}
	}
	
	protected boolean isValid() {
		return isValidPrivate();
	}
	
	protected abstract boolean isValidPrivate();
	
	protected void handleTags() {
		// parse tags - http://wiki.openstreetmap.org/wiki/Key:KEY
        if (entity.getTags() == null){
            Logger.i(TAG, "handleTags(): Entity tags are null, entity: " + entity.getId());
            return;
        }

        String nameEn = null; // only for fallback purposes when name in locale lng is not defined
		for (Tag tag : entity.getTags()) {
			String key = tag.getKey().toLowerCase(Locale.ENGLISH);
			String value = tag.getValue();
			
			// check values
			if (key.length() == 0 || value == null || value.length() == 0) {
				// invalid tag
				continue;
			}

            // handle specific tags
			if (key.startsWith("name")) {

				if ("name".equals(key)) {
					setName(value);	
				}
				else if("name:en".equals(key))  {
					// temporary remember alternative name:en
					nameEn = value;
				}
			} else if (handleTag(key, value)) {
				// tag consumed
			} else {
				OsmTagUsage.getPoiTagUsage().recordTag(tag, false);
				continue;
			}
			OsmTagUsage.getPoiTagUsage().recordTag(tag, true);
		}

		// fallback when name is not defined > try name in EN langugage
		if (mName == null || mName.trim().length() == 0){
			setName(nameEn); // set method check null or empty string
		}
	}
	
	protected abstract boolean handleTag(String key, String value);
	
//	protected short parseElevation(Tag tag) {
//		double testElevation = 0.0;
//		try {
//			// parse elevation value
//			String strElevation = tag.getValue().toLowerCase();
//			strElevation = strElevation.replace(",", ".");
//			strElevation = strElevation.replace(";", "");
//			strElevation = strElevation.replace("?", "");
//			strElevation = strElevation.replace("meter", "");
//			strElevation = strElevation.replace("m.n.m", "");
//			strElevation = strElevation.replace("m (msl)", "");
//			strElevation = strElevation.trim();
//
//			if (strElevation.endsWith("'")) {
//				strElevation = strElevation.replace("'", "");
//				testElevation = Double.parseDouble(strElevation) * 3.2808;
//			} else {
//				if (strElevation.endsWith("m")) {
//					strElevation = strElevation.replace("m", "");
//				}
//				testElevation = Double.parseDouble(strElevation);
//			}
//
//			if (testElevation < Consts.MAX_ELEVATION) {
//				return (short) testElevation;
//			}
//		} catch (NumberFormatException e) {
//            Logger.w(TAG, "could not parse elevation information to double value: '" + tag.getValue() +
//                    "', entity-id: '" + entity.getId() +
//                    "', entity-type: '" + entity.getType().name() + "'");
//		}
//		return Short.MIN_VALUE;
//	}
}
