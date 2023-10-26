/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.asamm.osmTools.tourist;

import com.asamm.osmTools.Main;
import com.asamm.osmTools.utils.Logger;
import com.asamm.osmTools.utils.SparseArray;
import lombok.Getter;
import lombok.Setter;
import org.kxml2.io.KXmlParser;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Setter
@Getter
/**
 *
 * @author volda
 */
public class Relation {

    private static final String TAG = Relation.class.getSimpleName();

    private long id;

    private String visible;
    
    private int counter = 0;
    
    private ArrayList<Member> members = new ArrayList<Member>();;

    private Tags tags = new Tags();

    private Tags parentTags;

    private List<Relation> childRelations;
    public Relation () {
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
     * Obtains ways from relations' members. These ways obtain tags from this relation
     * @param relations list of parsed relation
     * @param wl list of way ids that should obtain new tags from tourist relations
     * @param parentId
     */
    public void membersToWayList(SparseArray<Relation> relations, WayList wl, long parentId){

//        if (id == 22602){
//            Logger.i(TAG, "Relation ID: " + id);
//        }

        if (!members.isEmpty()) {

            for (Member mbr : members ){
                // test if member is not relations itselfs. if yes continue to next member
                if (mbr.ref == this.id){
                    Main.LOG.warning("Relation "+ this.id +" has itself a member");
                    continue;
                }
                
                // now test whether member is way or relation, nodes dont check
                if (mbr.type.equals("way")){

                    // test if way is member of any other valid children relation
                    if (isWayMemberOfValidChildrenRelation(mbr.ref)){
                        continue;
                    }

                    wl.addWay(mbr.ref, this.parentTags);
                } else if (mbr.type.equals("relation")){

                    if (mbr.role != null && mbr.role.equals("link")){
                        // skip children relation that are type link
                        // Connection is used for routes linking two different routes or linking a route
                        // with for example a village centre.
                        continue;
                    }

                    Relation rel = relations.get(mbr.ref);
                    if (rel == null) {
                        continue; // member linked with relation which wasn't parsed from source data
                    }

                    // test if child relation is not the parent relation > it'd cause closed loop
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
        }
    }

    /**
     * Iterates relation members and find relation that is parent of specified way.
     * If such relation is found and if has valid tags (defined osmc symbol or network) then @code{true} is returned
     * @param wayId id of way that is tested
     */
    private boolean isWayMemberOfValidChildrenRelation(long wayId){

        if (childRelations == null){
            return false;
        }
        for (Relation relation : childRelations){
            // find any member of relation with ref equal to wayId
            if (relation.getMembers().stream().anyMatch(mbr -> mbr.ref == wayId)) {
                // if relation has valid tags then return true
                return (tags.osmc_color != null && tags.osmc_color.length() > 0) || this.tags.isIwnNwnRwnLwn();
            }
        }
        return false;
    }

    /**
     * Iterate member of this relation, find all children relations and add them to list of children relations
     * @param allRelations all relations from source data
     */
    public void initChildRelation(SparseArray<Relation> allRelations) {
        if (childRelations == null){
            childRelations = new ArrayList<>();
        }
        for (Member mbr : members ){
            if (mbr.type.equals("relation")){
                Relation rel = allRelations.get(mbr.ref);
                if (rel == null) {
                    continue; // member linked with relation which wasn't parsed from source data
                }
                childRelations.add(rel);
            }
        }
    }
}
