package com.asamm.osmTools.generatorDb;

import com.asamm.locus.features.loMaps.LoMapsDbConst;
import com.asamm.osmTools.utils.Logger;
import com.asamm.osmTools.utils.XmlParser;
import gnu.trove.map.hash.THashMap;
import locus.api.utils.Utils;
import org.openstreetmap.osmosis.core.domain.v0_6.Entity;
import org.openstreetmap.osmosis.core.domain.v0_6.EntityType;
import org.openstreetmap.osmosis.core.domain.v0_6.Tag;
import org.xmlpull.v1.XmlPullParser;

import java.io.File;
import java.util.*;

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


    // SUB CLASS DbRootSubContainer for settings onle single node

    public class DbRootSubContainer {

        // filter matches entities if entity does not have a tag with the specified key.
        private static final String FILTER_TILDA = "~";

        // filter matched any value for the key
        private static final String FILTER_ASTERISK = "*";

		public String folRoot;
		public String folSub;
		
		public String key;
		public String value;
		
		boolean entityPoi;
		boolean entityWay;

        // Map <tag_key, tag_value> for better specification which tag entities can be load into database
        public THashMap<String, List<String>> filterTags;


		private boolean isValidEntity(LoMapsDbConst.EntityType type, Collection<Tag> tags) {
			if (tags == null){
                return false;
            }
            if (entityPoi && type == LoMapsDbConst.EntityType.POIS ||
					entityWay && type == LoMapsDbConst.EntityType.WAYS) {
				return checkTags(tags);
			}
			return false;
		}


        /**
         * Check if type of entity can be compared with this container
         *
         * @param type type of entity to test
         * @return
         */
		public boolean isValidType(LoMapsDbConst.EntityType type) {
			if (entityPoi && type == LoMapsDbConst.EntityType.POIS ||
                    entityWay && type == LoMapsDbConst.EntityType.WAYS) {
				return true;
			}
			return false;
		}

		
		private boolean checkTags(Collection<Tag> tags) {

            for (Tag tag : tags) {
				if (key.equals(tag.getKey()) && value.equals(tag.getValue())) {
					return checkFilterTags(tags);
				}
			}
			return false;
		}

        /**
         * Test if item fits detailed filters
         *
         * @param tags tags of item to compare
         * @return <code>true</code> if entity fits filter
         */
        private boolean checkFilterTags (Collection<Tag> tags){

            if (filterTags == null || filterTags.size() == 0){
                // this container doesn't have defined special filter tags
                return true;
            }

            for (Map.Entry<String, List<String>> entry : filterTags.entrySet()){

                // first find in entity's tags tag that is that same as filter key
                Tag tagForFilter = null;
                for (Tag tag : tags) {
                    if (entry.getKey().equals(tag.getKey())) {
                        tagForFilter = tag;
                    }
                }

                // possible values for filter key
                List<String> fValues = entry.getValue();
                if (fValues.contains(FILTER_TILDA) && tagForFilter == null){
                    // tilda says that any entity without such tag is valid
                    // because entity does not have tag as filter > return true
                    return true;
                }
                if (tagForFilter != null){
                    //entity contains tags that are defined in filter > check if tag value is the same as any filter value
                    if (fValues.contains(tagForFilter.getValue()) || fValues.contains(FILTER_ASTERISK)){
                        return true;
                    }
                }
            }
            return false;
        }

        /**
         * Test if this node container contains definition for specified type, and tag key/value combination
         *
         * @param type type of entity
         * @param key tag key to get
         * @param value value for key
         * @return true if this node container is liable for type, key value..
         */
        private boolean isNodeContainerFor(LoMapsDbConst.EntityType type, String key, String value) {
            if (!isValidType(type)) {
                return false;
            }
            return this.key.equals(key) && 	this.value.equals(value);
        }


        /**
         * Add precise definition of combination more key-values that are accepted
         * @param filter
         */
        public void addFilters (THashMap<String, List<String>> filter){
            if (this.filterTags == null){
                this.filterTags = new THashMap<>();
            }
            if (filter != null){
                this.filterTags.putAll(filter);
            }
        }

	}

    // CONSTRUCTOR

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
		if (entity == null) {
			return false;
		}

        if (entity.getType() == EntityType.Node){
            // save all nodes into cache because the node ways
            return true;
        }

		LoMapsDbConst.EntityType type = getTypeFromEntity(entity);
		if (type == LoMapsDbConst.EntityType.UNKNOWN) {
			return false;
		}

		// check all tags for ways
		for (DbRootSubContainer nodeContainer : nodes) {
			if (nodeContainer.isValidEntity(type, entity.getTags())) {
                return true;
			}
		}
		return false;
	}

    /**
     * Validate nodes separately during generation. Method tests if node has tags according to definition xml
     * @param entity node to test
     * @return true if node can be used for creation of POI
     */
    public boolean isValidNode(Entity entity) {
        // quick check
        if (entity == null) {
            return false;
        }

        if (entity.getType() == EntityType.Node){
            // save all nodes into cache because the node ways
            return true;
        }

        // check all tags
        LoMapsDbConst.EntityType type = getTypeFromEntity(entity);
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
			return LoMapsDbConst.COL_EMAIL;
		}

        // test phones
		if (tagKeysPhone.contains(key)) {
			return LoMapsDbConst.COL_PHONE;
		}

        // test urls
		if (tagKeysUrl.contains(key)) {
			return LoMapsDbConst.COL_URL;
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
		extra.add(LoMapsDbConst.COL_EMAIL);
		extra.add(LoMapsDbConst.COL_PHONE);
		extra.add(LoMapsDbConst.COL_URL);
		return extra;
	}

	public DbRootSubContainer getNodeContainer(LoMapsDbConst.EntityType type,
			String key, String value) {
		// check source params
		if (type == LoMapsDbConst.EntityType.UNKNOWN || key == null || value == null) {
			return null;
		}
		
		// iterate over all data
		for (int i = 0, m = nodes.size(); i < m; i++) {
            DbRootSubContainer locusNode = nodes.get(i);
			if (locusNode.isNodeContainerFor(type, key, value)) {
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
            DbRootSubContainer lastNode;
			
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
                        lastNode = node;
						if (!tagKeysMain.contains(key)) {
							tagKeysMain.add(key);
						}
						if (!tagValuesMain.contains(value)) {
							tagValuesMain.add(value);
						}
					}
                    else if (tagName.equals("filter")){
                        lastNode.addFilters(parseNodeFilters(parser));

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

    /**
     * Prepare list of possible values for of filter element
     *
     * @param parser XMLParser
     * @return Map with one key(as key of tag) and list of values that are accepted or empty map if not possible to parse node
     */
    private THashMap<String, List<String>> parseNodeFilters(XmlPullParser parser){

        THashMap<String, List<String>> map = new THashMap<>();

        String key = parser.getAttributeValue(null, "key");
        String valuesRaw = parser.getAttributeValue(null, "value");

        if (key == null || valuesRaw == null){
            return map;
        }

        String[] values = valuesRaw.split("|");
        map.put(key, Arrays.asList(values));

        return map;
    }
}
