package com.asamm.osmTools.generatorDb;

import com.asamm.locus.features.dbPoi.DbPoiConst;
import com.asamm.osmTools.utils.Logger;
import com.asamm.osmTools.utils.XmlParser;
import org.openstreetmap.osmosis.core.domain.v0_6.Entity;
import org.openstreetmap.osmosis.core.domain.v0_6.Node;
import org.openstreetmap.osmosis.core.domain.v0_6.Tag;
import org.openstreetmap.osmosis.core.domain.v0_6.Way;
import org.xmlpull.v1.XmlPullParser;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class WriterPoiDefinition extends  AWriterDefinition{

    private static final String TAG = WriterPoiDefinition.class.getSimpleName();

	private File file;
	private List<DbRootSubContainer> nodes;

	// these arrays are just list of values contained in nodes
	public List<String> foldersRoot;
	public List<String> foldersSub;
	public List<String> tagKeysMain;
	public List<String> tagValuesMain;
	
	private List<String> tagKeysExtra;
	private List<String> tagKeysEmail;
	private List<String> tagKeysPhone;
	private List<String> tagKeysUrl;
	
	public class DbRootSubContainer {

		public String folRoot;
		public String folSub;
		
		public String key;
		public String value;
		
		boolean entityPoi;
		boolean entityWay;
		
		private boolean isValidEntity(DbPoiConst.EntityType type, Collection<Tag> tags) {
			if (entityPoi && type == DbPoiConst.EntityType.POIS ||
					entityWay && type == DbPoiConst.EntityType.WAYS) {
				return checkTags(tags);
			}
			return false;
		}
		
		public boolean isValidType(DbPoiConst.EntityType type) {
			if (entityPoi && type == DbPoiConst.EntityType.POIS ||
					entityWay && type == DbPoiConst.EntityType.WAYS) {
				return true;
			}
			return false;
		}
		
		private boolean isValidEntity(DbPoiConst.EntityType type, String key, String value) {
			if (!isValidType(type)) {
				return false;
			}
			return this.key.equals(key) && 
					this.value.equals(value);
		}
		
		private boolean checkTags(Collection<Tag> tags) {
			for (Tag tag : tags) {
				if (key.equals(tag.getKey()) && value.equals(tag.getValue())) {
					return true;
				}
			}
			return false;
		}
	}

	public WriterPoiDefinition(File file) throws Exception {
		Logger.d(TAG, "NodeHandler(" + file.getAbsolutePath() + ")");
		this.file = file;
		this.nodes = new ArrayList<>();
		
		
		// data containers
		this.foldersRoot = new ArrayList<>();
		this.foldersSub = new ArrayList<>();
		this.tagKeysMain = new ArrayList<>();
		this.tagValuesMain = new ArrayList<>();
		
		this.tagKeysExtra = new ArrayList<>();
		this.tagKeysEmail = new ArrayList<>();
		this.tagKeysPhone = new ArrayList<>();
		this.tagKeysUrl = new ArrayList<>();
		parseData();
	}

    @Override
	public boolean isValidEntity(Entity entity) {
		// quick check
		if (entity == null || entity.getTags() == null) {
			return false;
		}

		DbPoiConst.EntityType type = getTypeFromEntity(entity);
		if (type == DbPoiConst.EntityType.UNKNOWN) {
			return false;
		}
		
		// check all tags
		for (DbRootSubContainer nodeContainer : nodes) {
			if (nodeContainer.isValidEntity(type, entity.getTags())) {
				return true;
			}
		}
		return false;
	}
	
	public List<DbRootSubContainer> getRootSubContainers() {
		return nodes;
	}
	

    /**
     * Check if key is included in main keys or detail keys
     * @param key that should be tested
     * @return key itself if valid, otherwise return null
     */
	public String isKeySupportedSingle(String key) {
        // check root keys
		if (tagKeysMain.contains(key)) {
			return key;
		}

        // check if key is in POI details
		if (tagKeysExtra.contains(key)) {
			return key;
		}

        // return "null"
		return null;
	}

    /**
     * Check if key is included in emails, phones or urls
     * @param key that should be tested
     * @return key itself if valid, otherwise return null
     */
    public String isKeySupportedMulti(String key) {
        // test emails
		if (tagKeysEmail.contains(key)) {
			return DbPoiConst.COL_EMAIL;
		}

        // test phones
		if (tagKeysPhone.contains(key)) {
			return DbPoiConst.COL_PHONE;
		}

        // test urls
		if (tagKeysUrl.contains(key)) {
			return DbPoiConst.COL_URL;
		}

        // return "null"
		return null;
	}
	
	public List<String> getSupportedKeysForDb() {
        List<String> extra = new ArrayList<>();
		
		// add all root keys
		for (String key : tagKeysMain) {
			extra.add(key);
		}
		
		// add all details
		for (String key : tagKeysExtra) {
			extra.add(key);
		}
		
		// add extra columns
		extra.add(DbPoiConst.COL_EMAIL);
		extra.add(DbPoiConst.COL_PHONE);
		extra.add(DbPoiConst.COL_URL);
		return extra;
	}

	public DbRootSubContainer getNodeContainer(DbPoiConst.EntityType type,
			String key, String value) {
		// check source params
		if (type == DbPoiConst.EntityType.UNKNOWN || key == null || value == null) {
			return null;
		}
		
		// iterate over all data
		for (int i = 0, m = nodes.size(); i < m; i++) {
            DbRootSubContainer locusNode = nodes.get(i);
			if (locusNode.isValidEntity(type, key, value)) {
				return locusNode;
			}
		}
		return null;
	}

	// PARSE PART
	
	private void parseData() throws Exception {
		XmlParser parser = new XmlParser(file) {
			
			// define current type
			boolean isInPois = false;
			boolean isInOptDetails = false;			
			boolean isInOptEmail = false;
			boolean isInOptPhone = false;
			boolean isInOptUrl = false;
			
			String lastFolderRoot;
			String lastFolderSub;
			
			@Override
			public boolean tagStart(XmlPullParser parser, String tagName)
					throws Exception {
				if (tagName.equals("pois")) {
					isInPois = true;
				} else if (tagName.equals("poi-details")) {
					isInOptDetails = true;
				} else if (tagName.equals("poi-email")) {
					isInOptEmail = true;
				} else if (tagName.equals("poi-phone")) {
					isInOptPhone = true;
				} else if (tagName.equals("poi-url")) {
					isInOptUrl = true;
				} else if (isInPois) {
					if (tagName.equals("folder")) {
						String folderName = parser.getAttributeValue(null, "name");
//System.out.println("parseData(), tag:" + tagName + ", folder:" + folderName);
						lastFolderRoot = folderName;
						if (!foldersRoot.contains(folderName)) {
							foldersRoot.add(folderName);
						}
					} else if (tagName.equals("sub-folder")) {
						String folderName = parser.getAttributeValue(null, "name");
//System.out.println("parseData(), tag:" + tagName + ", folder:" + folderName);
						lastFolderSub = folderName;
						if (!foldersSub.contains(folderName)) {
							foldersSub.add(folderName);
						}
					} else if (tagName.equals("tag")) {
						String key = parser.getAttributeValue(null, "key");
						String value = parser.getAttributeValue(null, "value");
						String type = parser.getAttributeValue(null, "type");
						DbRootSubContainer node = new DbRootSubContainer();
						node.folRoot = lastFolderRoot;
						node.folSub = lastFolderSub;
						node.key = key;
						node.value = value;
						node.entityPoi = type.contains("poi");
						node.entityWay = type.contains("way");
						nodes.add(node);
						if (!tagKeysMain.contains(key)) {
							tagKeysMain.add(key);
						}
						if (!tagValuesMain.contains(value)) {
							tagValuesMain.add(value);
						}
					}
				} else if (isInOptDetails) {
					if (tagName.equals("tag")) {
						tagKeysExtra.add(parser.getAttributeValue(null, "key"));
					}
				} else if (isInOptEmail) {
					if (tagName.equals("tag")) {
						tagKeysEmail.add(parser.getAttributeValue(null, "key"));
					}
				} else if (isInOptPhone) {
					if (tagName.equals("tag")) {
						tagKeysPhone.add(parser.getAttributeValue(null, "key"));
					}
				} else if (isInOptUrl) {
					if (tagName.equals("tag")) {
						tagKeysUrl.add(parser.getAttributeValue(null, "key"));
					}
				}
				return true;
			}
			
			@Override
			public boolean tagEnd(XmlPullParser parser, String tagName)
					throws Exception {
				if (tagName.equals("pois")) {
					isInPois = false;
				} else if (tagName.equals("poi-details")) {
					isInOptDetails = false;
				} else if (tagName.equals("poi-email")) {
					isInOptEmail = false;
				} else if (tagName.equals("poi-phone")) {
					isInOptPhone = false;
				} else if (tagName.equals("poi-url")) {
					isInOptUrl = false;
				} else if (tagName.equals("folder")) {
					lastFolderRoot = null;
				} else if (tagName.equals("sub-folder")) {
					lastFolderSub = null;
				}
				return true;
			}
			
			@Override
			public void parsingFinished(boolean success) {}
		};
		parser.parse();
	}
}
