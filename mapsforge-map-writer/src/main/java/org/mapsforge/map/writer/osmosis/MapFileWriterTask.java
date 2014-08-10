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
package org.mapsforge.map.writer.osmosis;

import java.io.IOException;
import java.text.NumberFormat;
import java.util.Map;
import java.util.Properties;

import com.asamm.osmTools.utils.Logger;
import org.mapsforge.core.model.BoundingBox;
import org.mapsforge.map.writer.dataProcessor.HDTileBasedDataProcessor;
import org.mapsforge.map.writer.MapFileWriter;
import org.mapsforge.map.writer.dataProcessor.RAMTileBasedDataProcessor;
import org.mapsforge.map.writer.model.MapWriterConfiguration;
import org.mapsforge.map.writer.dataProcessor.TileBasedDataProcessor;
import org.mapsforge.map.writer.util.Constants;
import org.openstreetmap.osmosis.core.container.v0_6.EntityContainer;
import org.openstreetmap.osmosis.core.domain.v0_6.Bound;
import org.openstreetmap.osmosis.core.domain.v0_6.Entity;
import org.openstreetmap.osmosis.core.domain.v0_6.Node;
import org.openstreetmap.osmosis.core.domain.v0_6.Relation;
import org.openstreetmap.osmosis.core.domain.v0_6.Way;
import org.openstreetmap.osmosis.core.task.v0_6.Sink;

/**
 * An Osmosis plugin that reads OpenStreetMap data and converts it to a mapsforge binary file.
 */
public class MapFileWriterTask implements Sink {

    private static final String TAG = MapFileWriterTask.class.getSimpleName();

	// counting
	private int mNodesProcessed = 0;
    private int mWaysProcessed = 0;
    private int mRelationsProcessed = 0;

    // plugin configuration
	private final MapWriterConfiguration mConfig;
    // data processor
	private TileBasedDataProcessor mDataProcessor;

	MapFileWriterTask(MapWriterConfiguration configuration) {
		this.mConfig = configuration;

		Properties properties = new Properties();
		try {
			properties.load(MapFileWriterTask.class.getClassLoader().getResourceAsStream("default.properties"));
			configuration.setWriterVersion(Constants.CREATOR_NAME + "-"
					+ properties.getProperty(Constants.PROPERTY_NAME_WRITER_VERSION));
			configuration.setFileSpecificationVersion(Integer.parseInt(properties
					.getProperty(Constants.PROPERTY_NAME_FILE_SPECIFICATION_VERSION)));

			Logger.d(TAG, "mapfile-writer version: " + configuration.getWriterVersion());
			Logger.d(TAG, "mapfile format specification version: " + configuration.getFileSpecificationVersion());
		} catch (IOException e) {
			throw new RuntimeException("could not find default properties", e);
		} catch (NumberFormatException e) {
			throw new RuntimeException("map file specification version is not an integer", e);
		}

		// create datastore if BBox is defined
		if (this.mConfig.getBboxConfiguration() != null) {
            createDataProcessor();
		}
	}

    @Override
    public void initialize(Map<String, Object> metadata) {
        // nothing to do here
    }

    @Override
    public final void process(EntityContainer entityContainer) {
        Entity entity = entityContainer.getEntity();
        switch (entity.getType()) {

            // handle BOUND
            case Bound:
                Bound bound = (Bound) entity;
                if (this.mConfig.getBboxConfiguration() == null) {
                    BoundingBox bbox = new BoundingBox(bound.getBottom(), bound.getLeft(),
                            bound.getTop(), bound.getRight());
                    this.mConfig.setBboxConfiguration(bbox);
                    this.mConfig.validate();
                    createDataProcessor();
                }
                Logger.d(TAG, "start reading data...");
                break;

            // handle NODE
            case Node:
                checkState();
                this.mDataProcessor.addNode((Node) entity);
                this.mNodesProcessed++;
                break;

            // handle WAY
            case Way:
                checkState();
                this.mDataProcessor.addWay((Way) entity);
                this.mWaysProcessed++;
                break;

            // handle RELATION
            case Relation:
                checkState();
                this.mDataProcessor.addRelation((Relation) entity);
                this.mRelationsProcessed++;
                break;
        }
    }

	@Override
	public final void complete() {
		NumberFormat nfMegabyte = NumberFormat.getInstance();
		NumberFormat nfCounts = NumberFormat.getInstance();
		nfCounts.setGroupingUsed(true);
		nfMegabyte.setMaximumFractionDigits(2);

		Logger.i(TAG, "completing read...");
		this.mDataProcessor.complete();

		Logger.i(TAG, "start writing file...");
		try {
			if (this.mConfig.getOutputFile().exists()) {
				this.mConfig.getOutputFile().delete();
			}
			MapFileWriter.writeFile(this.mConfig, this.mDataProcessor);
		} catch (IOException e) {
			Logger.e(TAG, "error while writing file", e);
		}

        // print results
		Logger.i(TAG, "Finished, nodes:" + nfCounts.format(this.mNodesProcessed) +
                ", ways:" + nfCounts.format(this.mWaysProcessed) +
                ", relations: " + nfCounts.format(this.mRelationsProcessed));
		Logger.i(TAG, "Estimated memory consumption: " +
                nfMegabyte.format(+((Runtime.getRuntime().totalMemory() -
                        Runtime.getRuntime().freeMemory()) / Math.pow(1024, 2))) + "MB");
	}

	@Override
	public final void release() {
		if (this.mDataProcessor != null) {
			this.mDataProcessor.release();
            this.mDataProcessor = null;
		}
	}

    // TOOLS

    private void createDataProcessor() {
        if ("ram".equalsIgnoreCase(mConfig.getDataProcessorType())) {
            this.mDataProcessor = RAMTileBasedDataProcessor.newInstance(mConfig);
        } else {
            this.mDataProcessor = HDTileBasedDataProcessor.newInstance(mConfig);
        }
    }

    private void checkState() {
        if (this.mDataProcessor == null) {
            Logger.e(TAG, "No valid bounding box found in input data.\n"
                    + "Please provide valid bounding box via command "
                    + "line parameter 'bbox=minLat,minLon,maxLat,maxLon'.\n"
                    + "Tile based data store not initialized. Aborting...");
            throw new IllegalStateException("tile based data store not initialized, missing bounding "
                    + "box information in input data");
        }
    }
}
