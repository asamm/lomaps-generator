package com.asamm.osmTools.generatorDb.data;

import com.asamm.osmTools.generatorDb.dataContainer.ADataContainer;
import org.openstreetmap.osmosis.core.domain.v0_6.*;

import java.util.List;
import java.util.NoSuchElementException;

/**
 * Created by voldapet on 8/13/15.
 */
public class RelationEx extends Relation{


    private List<WayEx> membersEx;

    public RelationEx(Relation relation) {
        super(new CommonEntityData(
                relation.getId(),
                relation.getVersion(),
                relation.getTimestampContainer(),
                relation.getUser(),
                relation.getChangesetId(),
                relation.getTags()),
                relation.getMembers());


    }

    public void fillMembers (ADataContainer dc){

        membersEx.clear();
        for (int i = 0, size = getMembers().size(); i < size; i++){
            RelationMember rm = getMembers().get(i);
            if (rm.getMemberType() == EntityType.Way){
                Way way = dc.getWayFromCache(rm.getMemberId());

                if (way == null){
                    throw new NoSuchElementException("Way is not stored in cache, id: " + rm.getMemberId());
                }

                WayEx wayEx = new WayEx(way);
                wayEx.fillNodes(dc);
                membersEx.add(wayEx);
            }
            else if (rm.getMemberType() == EntityType.Node) {

                // TODO process nodes as relation members
            }
            else if (rm.getMemberType() == EntityType.Relation){

                Relation relation = dc.getRelationFromCache(rm.getMemberId());

                if (relation == null){
                    throw new NoSuchElementException("Relation is not stored in cache, id: " + rm.getMemberId());
                }

                RelationEx relationEx = new RelationEx(relation);
                relationEx.fillMembers(dc);
            }
        }
    }

    /**************************************************/
    /*             GETTERS & SETTERS
    /**************************************************/

    public List<WayEx> getMembersEx() {
        return membersEx;
    }

    public void setMembersEx(List<WayEx> membersEx) {
        this.membersEx = membersEx;
    }
}
