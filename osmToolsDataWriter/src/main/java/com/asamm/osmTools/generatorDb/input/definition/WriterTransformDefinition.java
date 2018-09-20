package com.asamm.osmTools.generatorDb.input.definition;

import com.asamm.osmTools.generatorDb.plugin.ConfigurationTransform;
import com.asamm.osmTools.utils.XmlParser;
import gnu.trove.map.hash.THashMap;
import org.openstreetmap.osmosis.core.domain.v0_6.Entity;
import org.openstreetmap.osmosis.core.domain.v0_6.EntityType;
import org.openstreetmap.osmosis.core.domain.v0_6.Tag;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Created by voldapet on 16/1/2017.
 */
public class WriterTransformDefinition extends AWriterDefinition {

    private ConfigurationTransform configTransform;

    // set of tags (key value) of element that can be used for creation of residential areas
    public final List<Tag> residentialAreaTags = new ArrayList<>();

    public final List<Tag> natureAreaTags = new ArrayList<>();

    // set of tags <key value> of
    public final THashMap lakeTags = new THashMap();

    public WriterTransformDefinition(ConfigurationTransform configTransform) throws Exception {

        super();

        this.configTransform = configTransform;

        // parse XML
        // comment not needed
        //parseConfigXml();
    }



    @Override
    public boolean isValidEntity(Entity entity) {

        if (entity == null || entity.getTags() == null) {
            return false;
        }

        // save all nodes into cache
        if (entity.getType() == EntityType.Node){
            return true;
        }

        // save all ways because filtration is done by Osmbasic program
        else if (entity.getType() == EntityType.Way){
            return true;
        }

        // relations are REJECTED for geneation city areas
        else if ( entity.getType() == EntityType.Relation){
            return false;
        }

        return false;

    }


    private void parseConfigXml() throws Exception {


        XmlParser parser = new XmlParser(configTransform.getFileConfigXml()) {

            boolean isResidentialSection = false;
            boolean isNatureSection = false;

            @Override
            public boolean tagStart(XmlPullParser parser, String tagName) throws Exception {

                if (tagName.equals("residential")){
                    isResidentialSection = true;
                }

                if (tagName.equals("nature")){
                    isNatureSection = true;
                }


                if (tagName.equals("tag")) {

                    String key = parser.getAttributeValue(null, "key");
                    String value = parser.getAttributeValue(null, "value");

                    if (isResidentialSection){
                        residentialAreaTags.add(new Tag(key, value));
                    }
                    if (isNatureSection){
                        natureAreaTags.add(new Tag(key, value));
                    }
                }


                return true;
            }

            @Override
            public boolean tagEnd(XmlPullParser parser, String tagName) throws Exception {
                if (tagName.equals("residential")){
                    isResidentialSection = false;
                }

                if (tagName.equals("nature")){
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
