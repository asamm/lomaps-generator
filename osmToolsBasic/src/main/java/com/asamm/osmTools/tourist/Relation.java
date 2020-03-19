/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.asamm.osmTools.tourist;

import com.asamm.osmTools.Main;
import com.asamm.osmTools.utils.Logger;
import com.asamm.osmTools.utils.SparseArray;
import org.kxml2.io.KXmlParser;

import java.util.ArrayList;

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
    
    /**
     * Obtains ways from relations members. If way remember new tourist tags that ways should obtain from relation
     * @param relations list of parsed relation
     * @param wl list of way ids that should obtain new tags from tourist relations
     * @param parentId
     */
    public void membersToWayList(SparseArray<Relation> relations, WayList wl, long parentId){
       //counter ++;  
        if (!members.isEmpty()) {
            //  System.out.println("Pocet spusteni membersToWayList: " + counter);
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
                  
                    rel.membersToWayList(relations, wl,this.id);
                }
            }
            //System.out.println(wl.wayList.size());
        }
    }
}
