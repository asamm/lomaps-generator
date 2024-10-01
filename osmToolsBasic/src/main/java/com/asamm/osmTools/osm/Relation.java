/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.asamm.osmTools.osm;

import com.asamm.osmTools.utils.SparseArray;
import lombok.Getter;
import lombok.Setter;
import org.kxml2.io.KXmlParser;

import java.util.ArrayList;
import java.util.List;

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
