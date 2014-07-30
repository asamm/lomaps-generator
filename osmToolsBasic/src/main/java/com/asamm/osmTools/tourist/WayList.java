/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.asamm.osmTools.tourist;

import java.util.ArrayList;
import java.util.Hashtable;

/**
 *
 * @author volda
 */
public class WayList {
    
    Hashtable<Long, ArrayList<Tags>> wayList;
   
    public WayList () {
        //tags = new Hashtable<String, String> (); 
        wayList = new Hashtable<Long, ArrayList<Tags>>();
    }
    
    public void addWay (long wayId, Tags newTags) {
        if (isWayInList(wayId)){
            // get old tags according wayID
            ArrayList<Tags> tagsArray;
            tagsArray = wayList.get(wayId);
        
            if (tagsArray != null){
                if(!isTagsInArray(newTags, tagsArray)) {
                    
                    tagsArray.add(newTags);
                }
               
            }
            
            
        } else {
            // way is not in list add way to list
            ArrayList<Tags> tagsArray = new ArrayList<Tags>();
            tagsArray.add(newTags);
            wayList.put(wayId, tagsArray);
            
        }
        
    }   
    
    public boolean isWayInList (long id){
        return wayList.containsKey(id);
    }
    
    /**
     * Function test if there is array of tag with same tag/value
     * Because inherit it could happen than same tagArray can be add  again
     * @param newTags new tag for add 
     * @param tagsArray arraylist of Tags for specific way
     */
    public boolean isTagsInArray (Tags newTags, ArrayList<Tags> tagsArray){
        
        boolean isInArray = false;
        //first test 
        for (Tags tags : tagsArray ){
            //first test ref tag
            if (tags.ref != null && newTags.ref != null) {
                isInArray = (tags.ref.equals(newTags.ref));
            } 
            // if member relation does not have ref tag , decide based on name
            else if (tags.name != null && newTags.name != null){
                // compare name tag (upperCase of tag)
                isInArray = (tags.name.toUpperCase().equals(newTags.name.toUpperCase()));
            }
        }
        
        return isInArray;
    
    }
    
}
