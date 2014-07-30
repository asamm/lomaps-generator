/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.asamm.osmTools.tourist;

import org.kxml2.io.KXmlParser;

/**
 *
 * @author volda
 */
public class Member {
    
    public String type;
    public long ref;
    public String role;
    
      
    public void fillAttributes(KXmlParser parser){
        if (parser.getAttributeValue(null, "type") != null){
            type = parser.getAttributeValue(null, "type");
        }        
        if (parser.getAttributeValue(null, "ref") != null){
            String str  = parser.getAttributeValue(null, "ref");
            ref = Long.valueOf(str);
        }
        if (parser.getAttributeValue(null, "role") != null){
            role = parser.getAttributeValue(null, "role");
        }
    }
    
    
}
