package com.asamm.osmTools.generatorDb.utils;

import com.asamm.osmTools.utils.Logger;
import org.openstreetmap.osmosis.core.domain.v0_6.Tag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;

public class OsmTagUsage {

    private static final String TAG = OsmTagUsage.class.getSimpleName();

	private static OsmTagUsage poiTagUsage;
	public static OsmTagUsage getPoiTagUsage() {
		if (poiTagUsage == null) {
			poiTagUsage = new OsmTagUsage();
		}
		return poiTagUsage;
	}
	
	private HashMap<String, UsedTag> tags;
	
	private OsmTagUsage() {
		tags = new HashMap<String, OsmTagUsage.UsedTag>();
	}
	
	public void recordTag(Tag tag, boolean used) {
		UsedTag usedTag = tags.get(tag.getKey());
		if (usedTag == null) {
			usedTag = new UsedTag();
			usedTag.key = tag.getKey();
			tags.put(tag.getKey(), usedTag);
		}
		usedTag.count++;
		if (used) {
			usedTag.used = true;
		}
	}
	
	public void printTags() {
		// sort by name
		ArrayList<String> keys = new ArrayList<String>();
		Iterator<String> iterKeys = tags.keySet().iterator();
		while (iterKeys.hasNext()) {
			keys.add(iterKeys.next());
		}
		Collections.sort(keys);
		
		// finally print data
		for (String key : keys) {
			UsedTag ut = tags.get(key);
			if (ut.used) {
				continue;
			}
            Logger.i(TAG, ut.toString());
		}
	}
	
	private class UsedTag {
		
		private String key;
		private int count;
		private boolean used;
		
		public String toString() {
			return String.format("  %1$-30s %2$d", key, count);
		}
	}
}
