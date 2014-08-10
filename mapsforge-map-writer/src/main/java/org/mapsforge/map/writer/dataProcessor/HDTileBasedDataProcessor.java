/*
 * Copyright 2010, 2011, 2012, 2013 mapsforge.org
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.mapsforge.map.writer.dataProcessor;

import com.asamm.osmTools.utils.Logger;
import gnu.trove.iterator.TLongIterator;
import gnu.trove.list.array.TLongArrayList;
import gnu.trove.map.TLongObjectMap;
import gnu.trove.map.hash.TLongObjectHashMap;
import org.mapsforge.map.writer.OSMTagMapping;
import org.mapsforge.map.writer.model.MapWriterConfiguration;
import org.mapsforge.map.writer.model.TDNode;
import org.mapsforge.map.writer.model.TDRelation;
import org.mapsforge.map.writer.model.TDWay;
import org.openstreetmap.osmosis.core.domain.v0_6.Node;
import org.openstreetmap.osmosis.core.domain.v0_6.Relation;
import org.openstreetmap.osmosis.core.domain.v0_6.Way;
import org.openstreetmap.osmosis.core.lifecycle.ReleasableIterator;
import org.openstreetmap.osmosis.core.store.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A TileBasedDataStore that uses the hard disk as storage device for temporary data structures.
 */
public final class HDTileBasedDataProcessor extends BaseTileBasedDataProcessor {

    private static final String TAG = HDTileBasedDataProcessor.class.getSimpleName();

	/**
	 * Creates a new {@link HDTileBasedDataProcessor}.
	 * 
	 * @param configuration
	 *            the configuration
	 * @return a new instance of a {@link HDTileBasedDataProcessor}
	 */
	public static HDTileBasedDataProcessor newInstance(MapWriterConfiguration configuration) {
		return new HDTileBasedDataProcessor(configuration);
	}

	final TLongObjectMap<List<TDRelation>> additionalRelationTags;
	final TLongObjectMap<TDWay> virtualWays;
    // container for raw nodes
	private final IndexedObjectStore<Node> indexedNodeStore;
    // container for raw ways
	private final IndexedObjectStore<Way> indexedWayStore;

	private IndexedObjectStoreReader<Node> nodeIndexReader;
	private final SimpleObjectStore<Relation> relationStore;

	private final HDTileData[][][] tileData;
	private IndexedObjectStoreReader<Way> wayIndexReader;

	private final SimpleObjectStore<Way> wayStore;

	private HDTileBasedDataProcessor(MapWriterConfiguration configuration) {
		super(configuration);

		this.indexedNodeStore = new IndexedObjectStore<>(
                new SingleClassObjectSerializationFactory(Node.class), "idxNodes");
		this.indexedWayStore = new IndexedObjectStore<>(
                new SingleClassObjectSerializationFactory(Way.class), "idxWays");

		this.wayStore = new SimpleObjectStore<>(
                new SingleClassObjectSerializationFactory(Way.class), "heapWays", true);
		this.relationStore = new SimpleObjectStore<>(
                new SingleClassObjectSerializationFactory(Relation.class), "heapRelations", true);

		this.tileData = new HDTileData[this.zoomIntervalConfiguration.getNumberOfZoomIntervals()][][];
		for (int i = 0; i < this.zoomIntervalConfiguration.getNumberOfZoomIntervals(); i++) {
			this.tileData[i] = new HDTileData
                    [this.tileGridLayouts[i].getAmountTilesHorizontal()]
                    [this.tileGridLayouts[i].getAmountTilesVertical()];
		}
		this.virtualWays = new TLongObjectHashMap<>();
		this.additionalRelationTags = new TLongObjectHashMap<>();
	}

	@Override
	public void addNode(Node node) {
		this.indexedNodeStore.add(node.getId(), node);
		TDNode tdNode = TDNode.fromNode(node, this.preferredLanguage);
		addPOI(tdNode);
	}

    @Override
    public void addWay(Way way) {
        this.wayStore.add(way);
        this.indexedWayStore.add(way.getId(), way);
        this.maxWayID = Math.max(way.getId(), this.maxWayID);
    }

	@Override
	public void addRelation(Relation relation) {
		this.relationStore.add(relation);
	}

	// TODO add accounting of average number of tiles per way
	@Override
	public void complete() {
        Logger.i(TAG, "complete(), step 1");
		this.indexedNodeStore.complete();
		this.nodeIndexReader = this.indexedNodeStore.createReader();

		this.indexedWayStore.complete();
		this.wayIndexReader = this.indexedWayStore.createReader();

		// handle relations
        Logger.i(TAG, "complete(), step 2");
		ReleasableIterator<Relation> relationReader = this.relationStore.iterate();
		RelationHandler relationHandler = new RelationHandler();
		while (relationReader.hasNext()) {
			Relation entry = relationReader.next();
			TDRelation tdRelation = TDRelation.fromRelation(entry, this, this.preferredLanguage);
			relationHandler.execute(tdRelation);
		}

		// handle ways
        Logger.i(TAG, "complete(), step 3");
		ReleasableIterator<Way> wayReader = this.wayStore.iterate();
		WayHandler wayHandler = new WayHandler();
		while (wayReader.hasNext()) {
			Way way = wayReader.next();
			TDWay tdWay = TDWay.fromWay(way, this, this.preferredLanguage);
			if (tdWay == null) {
				continue;
			}
			List<TDRelation> associatedRelations = this.additionalRelationTags.get(tdWay.getId());
			if (associatedRelations != null) {
				for (TDRelation tileDataRelation : associatedRelations) {
					tdWay.mergeRelationInformation(tileDataRelation);
				}
			}

			wayHandler.execute(tdWay);
		}

        Logger.i(TAG, "complete(), step 4");
		OSMTagMapping.getInstance().optimizePoiOrdering(this.histogramPoiTags);
		OSMTagMapping.getInstance().optimizeWayOrdering(this.histogramWayTags);
	}

	@Override
	public synchronized List<TDWay> getInnerWaysOfMultipolygon(long outerWayID) {
		TLongArrayList innerwayIDs = this.outerToInnerMapping.get(outerWayID);
		if (innerwayIDs == null) {
			return null;
		}
		return getInnerWaysOfMultipolygon(innerwayIDs.toArray());
	}

	@Override
	public TDNode getNode(long id) {
		if (this.nodeIndexReader == null) {
			throw new IllegalStateException("node store not accessible, call complete() first");
		}

		try {
			return TDNode.fromNode(this.nodeIndexReader.get(id), this.preferredLanguage);
		} catch (NoSuchIndexElementException e) {
			Logger.w(TAG, "getNode(" + id + "), node cannot be found in index");
			return null;
		}
	}

	@Override
	public TileData getTile(int baseZoomIndex, int tileCoordinateX, int tileCoordinateY) {
		HDTileData hdt = getTileImpl(baseZoomIndex, tileCoordinateX, tileCoordinateY);
		if (hdt == null) {
			return null;
		}

		return fromHDTileData(hdt);
	}

	@Override
	public TDWay getWay(long id) {
		if (this.wayIndexReader == null) {
			throw new IllegalStateException("way store not accessible, call complete() first");
		}

		try {
			return TDWay.fromWay(this.wayIndexReader.get(id), this, this.preferredLanguage);
		} catch (NoSuchIndexElementException e) {
			Logger.w(TAG, "getWay(" + id + "), way cannot be found in index");
			return null;
		}
	}

	@Override
	public void release() {
		this.indexedNodeStore.release();
		this.indexedWayStore.release();
		this.wayStore.release();
		this.relationStore.release();
	}

	@Override
	protected HDTileData getTileImpl(int zoom, int tileX, int tileY) {
		int tileCoordinateXIndex = tileX - this.tileGridLayouts[zoom].getUpperLeft().getX();
		int tileCoordinateYIndex = tileY - this.tileGridLayouts[zoom].getUpperLeft().getY();
		// check for valid range
		if (tileCoordinateXIndex < 0 || tileCoordinateYIndex < 0 || this.tileData[zoom].length <= tileCoordinateXIndex
				|| this.tileData[zoom][tileCoordinateXIndex].length <= tileCoordinateYIndex) {
			return null;
		}

		HDTileData td = this.tileData[zoom][tileCoordinateXIndex][tileCoordinateYIndex];
		if (td == null) {
			td = new HDTileData();
			this.tileData[zoom][tileCoordinateXIndex][tileCoordinateYIndex] = td;
		}

		return td;
	}

	@Override
	protected void handleAdditionalRelationTags(TDWay way, TDRelation relation) {
		List<TDRelation> associatedRelations = this.additionalRelationTags.get(way.getId());
		if (associatedRelations == null) {
			associatedRelations = new ArrayList<>();
			this.additionalRelationTags.put(way.getId(), associatedRelations);
		}
		associatedRelations.add(relation);
	}

	@Override
	protected void handleVirtualInnerWay(TDWay virtualWay) {
		this.virtualWays.put(virtualWay.getId(), virtualWay);
	}

	@Override
	protected void handleVirtualOuterWay(TDWay virtualWay) {
		this.virtualWays.put(virtualWay.getId(), virtualWay);
	}

	private RAMTileData fromHDTileData(HDTileData hdt) {
		final RAMTileData td = new RAMTileData();
		TLongIterator it = hdt.getPois().iterator();
		while (it.hasNext()) {
			td.addPOI(TDNode.fromNode(this.nodeIndexReader.get(it.next()), this.preferredLanguage));
		}

		it = hdt.getWays().iterator();
		while (it.hasNext()) {
			TDWay way;
			long id = it.next();
			try {
				way = TDWay.fromWay(this.wayIndexReader.get(id), this, this.preferredLanguage);
				td.addWay(way);
			} catch (NoSuchIndexElementException e) {
				// is it a virtual way?
				way = this.virtualWays.get(id);
				if (way != null) {
					td.addWay(way);
				} else {
					Logger.e(TAG,  "referenced way non-existing" + id);
				}
			}

			if (way != null) {
				if (this.outerToInnerMapping.contains(way.getId())) {
					way.setShape(TDWay.MULTI_POLYGON);
				}

				List<TDRelation> associatedRelations = this.additionalRelationTags.get(id);
				if (associatedRelations != null) {
					for (TDRelation tileDataRelation : associatedRelations) {
						way.mergeRelationInformation(tileDataRelation);
					}
				}
			}
		}

		return td;
	}

	private List<TDWay> getInnerWaysOfMultipolygon(long[] innerWayIDs) {
		if (innerWayIDs == null) {
			return Collections.emptyList();
		}
		List<TDWay> res = new ArrayList<>();
		for (long id : innerWayIDs) {
			TDWay current;
			try {
				current = TDWay.fromWay(this.wayIndexReader.get(id), this, this.preferredLanguage);
			} catch (NoSuchIndexElementException e) {
				current = this.virtualWays.get(id);
				if (current == null) {
                    Logger.w(TAG, "multipolygon with outer way id " + id + " references non-existing inner way " + id);
					continue;
				}
			}

			res.add(current);
		}

		return res;
	}
}
