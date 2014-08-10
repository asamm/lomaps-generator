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
import gnu.trove.list.array.TLongArrayList;
import gnu.trove.map.hash.TLongObjectHashMap;
import gnu.trove.set.hash.TLongHashSet;
import org.mapsforge.core.model.BoundingBox;
import org.mapsforge.map.writer.OSMTagMapping;
import org.mapsforge.map.writer.model.*;
import org.mapsforge.map.writer.util.GeoUtils;
import org.openstreetmap.osmosis.core.domain.v0_6.Node;
import org.openstreetmap.osmosis.core.domain.v0_6.Relation;
import org.openstreetmap.osmosis.core.domain.v0_6.Way;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * A TileBasedDataStore that uses the RAM as storage device for temporary data structures.
 */
public final class RAMTileBasedDataProcessor extends BaseTileBasedDataProcessor {

    private static final String TAG = RAMTileBasedDataProcessor.class.getSimpleName();

	/**
	 * Creates a new instance of a {@link RAMTileBasedDataProcessor}.
	 * 
	 * @param configuration
	 *            the configuration
	 * @return a new instance of a {@link RAMTileBasedDataProcessor}
	 */
	public static RAMTileBasedDataProcessor newInstance(MapWriterConfiguration configuration) {
		return new RAMTileBasedDataProcessor(configuration);
	}

    // container for nodes
	private final TLongObjectHashMap<TDNode> nodes;
    // container for ways
    private final TLongObjectHashMap<TDWay> ways;
    // container for polygons
    private final TLongObjectHashMap<TDRelation> multipolygons;

    // container for separated tiles
	private final RAMTileData[][][] tileData;

	private RAMTileBasedDataProcessor(MapWriterConfiguration configuration) {
		super(configuration);

        // prepare containers
		this.nodes = new TLongObjectHashMap<>();
		this.ways = new TLongObjectHashMap<>();
		this.multipolygons = new TLongObjectHashMap<>();

		// compute number of tiles needed on each base zoom level
        this.tileData = new RAMTileData[this.zoomIntervalConfiguration.getNumberOfZoomIntervals()][][];
		for (int i = 0; i < this.zoomIntervalConfiguration.getNumberOfZoomIntervals(); i++) {
			this.tileData[i] = new RAMTileData
                    [this.tileGridLayouts[i].getAmountTilesHorizontal()]
                    [this.tileGridLayouts[i].getAmountTilesVertical()];
		}
	}

	@Override
	public void addNode(Node node) {
        // create and check node
		TDNode tdNode = TDNode.fromNode(node, this.preferredLanguage);
        if (tdNode == null) {
            return;
        }

        // add node to storage
        this.nodes.put(tdNode.getId(), tdNode);
		addPOI(tdNode);
	}

	@Override
	public void addWay(Way way) {
        // create and check ways
		TDWay tdWay = TDWay.fromWay(way, this, this.preferredLanguage);
		if (tdWay == null) {
			return;
		}

        // add ways to storage
		this.ways.put(tdWay.getId(), tdWay);
		this.maxWayID = Math.max(this.maxWayID, way.getId());

		if (tdWay.isCoastline()) {
			// find matching tiles on zoom level 12
			Set<TileCoordinate> coastLineTiles = GeoUtils.mapWayToTiles(tdWay, TileInfo.TILE_INFO_ZOOMLEVEL, 0);
			for (TileCoordinate tileCoordinate : coastLineTiles) {
				TLongHashSet coastlines = this.tilesToCoastlines.get(tileCoordinate);
				if (coastlines == null) {
					coastlines = new TLongHashSet();
					this.tilesToCoastlines.put(tileCoordinate, coastlines);
				}
				coastlines.add(tdWay.getId());
			}
		}
	}

    @Override
    public void addRelation(Relation relation) {
        TDRelation tdRelation = TDRelation.fromRelation(relation, this, this.preferredLanguage);
        if (tdRelation != null) {
            this.multipolygons.put(relation.getId(), tdRelation);
        }
    }

	@Override
	public void complete() {
		// Polygonize multipolygon
        Logger.d(TAG, "complete(), handle relations...");
		this.multipolygons.forEachValue(new RelationHandler());

        Logger.d(TAG, "complete(), handle ways...");
		this.ways.forEachValue(new WayHandler());

        Logger.d(TAG, "complete(), optimize poi and ways");
		OSMTagMapping.getInstance().optimizePoiOrdering(this.histogramPoiTags);
		OSMTagMapping.getInstance().optimizeWayOrdering(this.histogramWayTags);
	}

	@Override
	public List<TDWay> getInnerWaysOfMultipolygon(long outerWayID) {
		TLongArrayList innerwayIDs = this.outerToInnerMapping.get(outerWayID);
		if (innerwayIDs == null) {
			return null;
		}
		return getInnerWaysOfMultipolygon(innerwayIDs.toArray());
	}

	@Override
	public TDNode getNode(long id) {
		return this.nodes.get(id);
	}

	@Override
	public TileData getTile(int zoom, int tileX, int tileY) {
		return getTileImpl(zoom, tileX, tileY);
	}

	@Override
	public TDWay getWay(long id) {
		return this.ways.get(id);
	}

	@Override
	public ZoomIntervalConfiguration getZoomIntervalConfiguration() {
		return this.zoomIntervalConfiguration;
	}

	@Override
	public void release() {
		// nothing to do here
	}

	@Override
	protected RAMTileData getTileImpl(int zoom, int tileX, int tileY) {
		int tileCoordinateXIndex = tileX - this.tileGridLayouts[zoom].getUpperLeft().getX();
		int tileCoordinateYIndex = tileY - this.tileGridLayouts[zoom].getUpperLeft().getY();
		// check for valid range
		if (tileCoordinateXIndex < 0 || tileCoordinateYIndex < 0 || this.tileData[zoom].length <= tileCoordinateXIndex
				|| this.tileData[zoom][tileCoordinateXIndex].length <= tileCoordinateYIndex) {
			return null;
		}

		RAMTileData td = this.tileData[zoom][tileCoordinateXIndex][tileCoordinateYIndex];
		if (td == null) {
			td = new RAMTileData();
			this.tileData[zoom][tileCoordinateXIndex][tileCoordinateYIndex] = td;
		}

		return td;
	}

	@Override
	protected void handleAdditionalRelationTags(TDWay virtualWay, TDRelation relation) {
		// nothing to do here
	}

	@Override
	protected void handleVirtualInnerWay(TDWay virtualWay) {
		this.ways.put(virtualWay.getId(), virtualWay);
	}

	@Override
	protected void handleVirtualOuterWay(TDWay virtualWay) {
		// nothing to do here
	}

	private List<TDWay> getInnerWaysOfMultipolygon(long[] innerWayIDs) {
		if (innerWayIDs == null) {
			return Collections.emptyList();
		}
		List<TDWay> res = new ArrayList<>();
		for (long id : innerWayIDs) {
			TDWay current = this.ways.get(id);
			if (current == null) {
				continue;
			}
			res.add(current);
		}

		return res;
	}
}
