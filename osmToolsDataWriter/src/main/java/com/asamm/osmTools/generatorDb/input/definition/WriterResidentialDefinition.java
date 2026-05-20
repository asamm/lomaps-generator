package com.asamm.osmTools.generatorDb.input.definition;

import com.asamm.osmTools.generatorDb.plugin.ConfigurationResidential;
import com.asamm.osmTools.utils.XmlParser;
import gnu.trove.map.hash.THashMap;
import org.openstreetmap.osmosis.core.domain.v0_6.Entity;
import org.openstreetmap.osmosis.core.domain.v0_6.EntityType;
import org.openstreetmap.osmosis.core.domain.v0_6.Tag;
import org.xmlpull.v1.XmlPullParser;

import java.util.ArrayList;
import java.util.List;

public class WriterResidentialDefinition extends AWriterDefinition {

    private ConfigurationResidential configResidential;

    public final List<Tag> residentialAreaTags = new ArrayList<>();

    public final List<Tag> natureAreaTags = new ArrayList<>();

    public final THashMap lakeTags = new THashMap();

    public WriterResidentialDefinition(ConfigurationResidential configResidential) throws Exception {

        super();

        this.configResidential = configResidential;
    }

    @Override
    public boolean isValidEntity(Entity entity) {

        if (entity == null || entity.getTags() == null) {
            return false;
        }

        if (entity.getType() == EntityType.Node) {
            return true;
        } else if (entity.getType() == EntityType.Way) {
            return true;
        } else if (entity.getType() == EntityType.Relation) {
            return false;
        }

        return false;
    }

    private void parseConfigXml() throws Exception {

        XmlParser parser = new XmlParser(configResidential.getFileConfigXml()) {

            boolean isResidentialSection = false;
            boolean isNatureSection = false;

            @Override
            public boolean tagStart(XmlPullParser parser, String tagName) throws Exception {

                if (tagName.equals("residential")) {
                    isResidentialSection = true;
                }
                if (tagName.equals("nature")) {
                    isNatureSection = true;
                }
                if (tagName.equals("tag")) {
                    String key = parser.getAttributeValue(null, "key");
                    String value = parser.getAttributeValue(null, "value");
                    if (isResidentialSection) {
                        residentialAreaTags.add(new Tag(key, value));
                    }
                    if (isNatureSection) {
                        natureAreaTags.add(new Tag(key, value));
                    }
                }
                return true;
            }

            @Override
            public boolean tagEnd(XmlPullParser parser, String tagName) throws Exception {
                if (tagName.equals("residential")) {
                    isResidentialSection = false;
                }
                if (tagName.equals("nature")) {
                    isNatureSection = false;
                }
                return true;
            }

            @Override
            public void parsingFinished(boolean success) {}
        };

        parser.parse();
    }
}