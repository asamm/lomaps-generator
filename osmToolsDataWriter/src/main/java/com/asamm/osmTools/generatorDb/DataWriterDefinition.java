package com.asamm.osmTools.generatorDb;

import com.asamm.locus.data.spatialite.DbPoiConst;
import com.asamm.osmTools.utils.Consts;
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

public class DataWriterDefinition {

    private static final String TAG = DataWriterDefinition.class.getSimpleName();

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
		
		private boolean isValidEntity(Consts.EntityType type, Collection<Tag> tags) {
			if (entityPoi && type == Consts.EntityType.POIS ||
					entityWay && type == Consts.EntityType.WAYS) {
				return checkTags(tags);
			}
			return false;
		}
		
		public boolean isValidType(Consts.EntityType type) {
			if (entityPoi && type == Consts.EntityType.POIS ||
					entityWay && type == Consts.EntityType.WAYS) {
				return true;
			}
			return false;
		}
		
		private boolean isValidEntity(Consts.EntityType type, String key, String value) {
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
	
	public class DbColumnDefinition {
		
		public String name;
		public String type;
		
		private DbColumnDefinition(String name, String type) {
			this.name = name;
			this.type = type;
		}
	}
	
	public DataWriterDefinition(File file) throws Exception {
		Logger.d(TAG, "NodeHandler(" + file.getAbsolutePath() + ")");
		this.file = file;
		this.nodes = new ArrayList<DbRootSubContainer>();
		
		
		// data containers
		this.foldersRoot = new ArrayList<String>();
		this.foldersSub = new ArrayList<String>();
		this.tagKeysMain = new ArrayList<String>();
		this.tagValuesMain = new ArrayList<String>();
		
		this.tagKeysExtra = new ArrayList<String>();
		this.tagKeysEmail = new ArrayList<String>();
		this.tagKeysPhone = new ArrayList<String>();
		this.tagKeysUrl = new ArrayList<String>();
		parseData();
	}
	
	public boolean isValidEntity(Entity entity) {
		// quick check
		if (entity == null || entity.getTags() == null) {
			return false;
		}

		Consts.EntityType type = getTypeFromEntity(entity);
		if (type == Consts.EntityType.UNKNOWN) {
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
	
	private Consts.EntityType getTypeFromEntity(Entity entity) {
		if (entity instanceof Node) {
			return Consts.EntityType.POIS;
		} else if (entity instanceof Way) {
			return Consts.EntityType.WAYS;
		} else {
			return Consts.EntityType.UNKNOWN;
		}
	}
	
	public String isKeySupportedSingle(String key) {
		if (tagKeysMain.contains(key)) {
			return key;
		}
		if (tagKeysExtra.contains(key)) {
			return key;
		}
		return null;
	}
	
	public String isKeySupportedMulti(String key) {
		if (tagKeysEmail.contains(key)) {
			return DbPoiConst.COL_EMAIL;
		}
		if (tagKeysPhone.contains(key)) {
			return DbPoiConst.COL_PHONE;
		}
		if (tagKeysUrl.contains(key)) {
			return DbPoiConst.COL_URL;
		}
		return null;
	}
	
	public List<String> getSupportedKeysForDb() {
        List<String> extra = new ArrayList<String>();
		
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
	
//	public ArrayList<String> getSupportedKeysForExtract() {
//		ArrayList<String> extra = new ArrayList<String>();
//		
//		// add all root keys
//		for (String key : tagKeysMain) {
//			extra.add(key);
//		}
//		
//		// add all details
//		for (String key : tagKeysExtra) {
//			extra.add(key);
//		}
//		
//		// add extra columns
//		for (String key : tagKeysEmail) {
//			extra.add(key);
//		}
//		for (String key : tagKeysPhone) {
//			extra.add(key);
//		}
//		for (String key : tagKeysUrl) {
//			extra.add(key);
//		}
//		return extra;
//	}
	
//	public ArrayList<String> getBaseColumns() {
//		ArrayList<String> extra = new ArrayList<String>();
//			
//		// add all root keys
//		for (DbNodeContainer locusNode : nodes) {
//			if (!extra.contains(locusNode.key)) {
//				extra.add(locusNode.key);
//			}
//		}
//		return extra;
//	}

	
//	public ArrayList<DbColumnDefinition> getDetailColumns() {
//		ArrayList<DbColumnDefinition> extra = new ArrayList<DbColumnDefinition>();
//		
//		// add all details
//		for (String key : tagExtraKeys) {
//			extra.add(new DbColumnDefinition(key, "TEXT"));
//		}
//		
//		// add extra columns
//		extra.add(new DbColumnDefinition(DbPoiParameters.COL_EMAIL, "TEXT"));
//		extra.add(new DbColumnDefinition(DbPoiParameters.COL_PHONE, "TEXT"));
//		extra.add(new DbColumnDefinition(DbPoiParameters.COL_URL, "TEXT"));
//		return extra;
//	}

	public DbRootSubContainer getNodeContainer(Consts.EntityType type,
			String key, String value) {
		// check source params
		if (type == Consts.EntityType.UNKNOWN || key == null || value == null) {
			return null;
		}
		
		// iterate over all data
		for (DbRootSubContainer locusNode : nodes) {
			if (locusNode.isValidEntity(type, key, value)) {
				return locusNode;
			}
		}
		return null;
	}
	
//	public String getDetailsKey(String key) {
//		if (key == null || key.length() == 0) {
//			return null;
//		}
//		if (tagExtraKeys.contains(key)) {
//			return key;
//		}
//		if (tagEmail.contains(key)) {
//			return "email";
//		}
//		if (tagPhone.contains(key)) {
//			return "phone";
//		}
//		if (tagUrl.contains(key)) {
//			return "url";
//		}
//		return null;
//	}
	
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
