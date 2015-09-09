package com.asamm.osmTools.generatorDb.dataContainer;

import com.asamm.osmTools.generatorDb.AWriterDefinition;
import com.asamm.osmTools.generatorDb.address.Street;
import com.asamm.osmTools.generatorDb.db.DatabaseDataTmp;
import com.asamm.osmTools.utils.Logger;
import org.openstreetmap.osmosis.core.domain.v0_6.Node;
import org.openstreetmap.osmosis.core.domain.v0_6.Relation;
import org.openstreetmap.osmosis.core.domain.v0_6.Way;

import java.io.*;
import java.util.HashSet;
import java.util.List;

public class DataContainerHdd extends ADataContainer {

    private static final String TAG = DataContainerHdd.class.getSimpleName();

	private DatabaseDataTmp dbData;



	
	public DataContainerHdd(AWriterDefinition writerDefinition, File tempFile) throws Exception {
		super(writerDefinition);
        dbData = new DatabaseDataTmp(tempFile,true);

        Logger.i(TAG, "HDD container created");
    }

	@Override
	public void insertNodeToCache(Node node) {
		// check data
		if (node == null) {
			return;
		}
        dbData.insertNode(node.getId(), node);
	}

	@Override
	public void insertWayToCache(Way way) {
		//ways.put(way.getId(), way);
        if (way == null){
            return;
        }
        dbData.insertWay(way.getId(), way);
	}

    @Override
    public void insertRelationToCache (Relation relation){
        if (relation == null){
            return;
        }
        dbData.insertRelation(relation.getId(), relation);
    }

	@Override
	public Node getNodeFromCache(long id) {
		return dbData.selectNode(id);
	}

    /**
     * Get osm way from cache
     * @param id osm id of way
     * @return
     */
	@Override
	public Way getWayFromCache(long id) {
        return dbData.selectWay(id);
	}

    @Override
    public Relation getRelationFromCache(long id) {
        return dbData.selectRelation(id);
    }

    /**
     * Flush not executed batch statement
     */
    @Override
    public void finalizeCaching() {
        dbData.finalizeBatchStatement();
    }

    public void finalizeWayStreetCaching () {
        dbData.createStreetIndex();
    }


    @Override
    public void insertWayStreetToCache(int hash, Street street) {
        dbData.insertStreet(hash, street);
    }

    @Override
    public List<Street> getWayStreetsFromCache(int hash) {
        return dbData.selectStreets (hash);
    }


    @Override
	public void destroy() {
		super.destroy();
		try {
            dbData.destroy();
		} catch (Exception e) {}
	}
}
