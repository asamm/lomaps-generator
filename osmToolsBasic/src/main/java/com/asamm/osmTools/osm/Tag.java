/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.asamm.osmTools.osm;

import org.kxml2.io.KXmlParser;

/**
 *
 * @author volda
 */
public class Tag {
    public String key;
    public String val;
    
    
     public void fillAttributes(KXmlParser parser){
        if (parser.getAttributeValue(null, "k") != null){
            key = parser.getAttributeValue(null, "k");
        }        
        if (parser.getAttributeValue(null, "v") != null){
            val = parser.getAttributeValue(null, "v");
        }
     }
}
