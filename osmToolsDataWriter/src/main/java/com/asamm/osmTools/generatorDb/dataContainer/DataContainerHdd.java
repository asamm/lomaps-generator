package com.asamm.osmTools.generatorDb.dataContainer;

import com.asamm.osmTools.generatorDb.AWriterDefinition;
import com.asamm.osmTools.generatorDb.address.Street;
import com.asamm.osmTools.generatorDb.db.DatabaseDataTmp;
import com.asamm.osmTools.utils.Logger;
import gnu.trove.set.hash.THashSet;
import org.openstreetmap.osmosis.core.domain.v0_6.Node;
import org.openstreetmap.osmosis.core.domain.v0_6.Relation;
import org.openstreetmap.osmosis.core.domain.v0_6.Way;

import java.io.*;
import java.util.List;

public class DataContainerHdd extends ADataContainer {


    private static final String TAG = DataContainerHdd.class.getSimpleName();

	private DatabaseDataTmp dbData;

    private File tmpDbFile;


	
	public DataContainerHdd(AWriterDefinition writerDefinition, File tmpDbFile) throws Exception {
		super(writerDefinition);
        this.tmpDbFile = tmpDbFile;
        dbData = new DatabaseDataTmp(tmpDbFile,true);

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

    @Override
    public List<Node> getNodesFromCache(long[] ids) {
        return dbData.selectNodes(ids);
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
        dbData.createWayStreetIndex();
    }

    @Override
    public void clearWayStreetCache() {
        streetHashSet = new THashSet<>();
        dbData.deleteWayStreetData();
        //dbData.dropWayStreetIndex();

    }

    @Override
    public void insertWayStreetToCache(int hash, Street street) {
        dbData.insertWayStreet(hash, street);
    }

    @Override
    public List<Street> getWayStreetsFromCache(int hash) {
        return dbData.selectWayStreets(hash);
    }

    @Override
    protected void insertWayStreetByOsmIdToCache(Street street) {
        dbData.insertWayStreetByOsmId(street);
    }

    @Override
    public List<Street> getWayStreetsByOsmIdFromCache(List<Long> osmIds) {
        return dbData.selectWayStreetsByOsmIds(osmIds);
    }


    @Override
	public void destroy() {
        if (tmpDbFile.exists()) {
            tmpDbFile.delete();
        }

		super.destroy();
		try {
            dbData.destroy();
		} catch (Exception e) {}
	}

}
