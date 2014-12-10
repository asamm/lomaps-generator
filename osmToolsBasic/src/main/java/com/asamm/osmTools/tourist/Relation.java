/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.asamm.osmTools.tourist;

import java.util.ArrayList;

import com.asamm.osmTools.utils.Logger;
import org.kxml2.io.KXmlParser;
import com.asamm.osmTools.Main;
import com.asamm.osmTools.utils.SparseArray;

/**
 *
 * @author volda
 */
public class Relation {

    private static final String TAG = Relation.class.getSimpleName();

    public long id;
    public String visible;
    
    private int counter = 0;
    
    ArrayList<Member> members;
    Tags tags;
    Tags parentTags;
    
    public Relation () {
        members = new ArrayList<Member>();
        tags = new Tags();
    }
    
            
    public void fillAttributes(KXmlParser parser){
        if (parser.getAttributeValue(null, "id") != null){
            String str = parser.getAttributeValue(null, "id");
            id = Long.valueOf(str);
        }
        
        if (parser.getAttributeValue(null, "visible") != null){
            visible = parser.getAttributeValue(null, "visible");
        }
    }
    
//    public boolean isCycloRoute (){
//        if (tags.route != null){
//            return (tags.route.equals("bicycle") || tags.route.equals("mtb"));
//        }
//        return false;
//    }
    
    public void membersToList (SparseArray<Relation> relations, WayList wl, long parentId){
       //counter ++;  
        if (!members.isEmpty()) {
          //  System.out.println("Pocet spusteni membersToList: " + counter);
          //  System.out.println("relation id: " + this.id);

            for (Member mbr : members ){
                // test if member is not relations itselfs. if yes continue to next member
                if (mbr.ref == this.id){
                    Main.LOG.warning("Relation "+ this.id +" has itself a member");
                    continue;
                }
                
                // now test whether member is way or relation, nodes dont check
                if (mbr.type.equals("way")){
                    wl.addWay(mbr.ref, this.parentTags);
                }
                else if (mbr.type.equals("relation")){
                    
                    Relation rel = relations.get(mbr.ref);
                    if (rel == null) {
                        continue; // member linked with relation which is not in xml file
                    }

                    // test if child relation is not the parent relation > it cause cycling
                    if (rel.id == parentId){
                        //skip this relation
                        Logger.w(TAG, "Relation id: " + this.id + " has child relation same as parent relation, parent relation id: " + parentId);
                        continue;
                    }

                    //copy parentTag from parent to children relation
                    rel.parentTags = this.parentTags;
                  
                    rel.membersToList(relations, wl,this.id);
                }
            }
            //System.out.println(wl.wayList.size());
        }
    }
}
