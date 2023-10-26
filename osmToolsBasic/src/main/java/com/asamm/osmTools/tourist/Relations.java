/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.asamm.osmTools.tourist;

import com.asamm.osmTools.utils.SparseArray;

/**
 * @author volda
 */
public class Relations {

    SparseArray<Relation> relations;

    public Relations() {
        relations = new SparseArray<Relation>();

    }

    /**
     * @param rel Relation to add
     */
    public void addRel(Relation rel) {
        this.relations.put(rel.getId(), rel);

        this.relations.put(rel.getId(), rel);
    }

    public Relation getRel(long id) {
        return relations.get(id);
    }


//    public Relations Enumeration<String> getKeys () {
//        return relations.
//    }    

}
