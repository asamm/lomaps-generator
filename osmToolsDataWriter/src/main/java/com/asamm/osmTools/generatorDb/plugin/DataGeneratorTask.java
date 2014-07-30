package com.asamm.osmTools.generatorDb.plugin;

import com.asamm.osmTools.generatorDb.AGenerator;
import com.asamm.osmTools.generatorDb.GeneratorAddress;
import com.asamm.osmTools.generatorDb.GeneratorPoi;
import com.asamm.osmTools.generatorDb.DataWriterDefinition;
import com.asamm.osmTools.generatorDb.db.ADataContainer;
import com.asamm.osmTools.generatorDb.db.DataContainerHdd;
import com.asamm.osmTools.generatorDb.db.DataContainerRam;
import com.asamm.osmTools.utils.Logger;
import org.openstreetmap.osmosis.core.container.v0_6.EntityContainer;
import org.openstreetmap.osmosis.core.domain.v0_6.Entity;
import org.openstreetmap.osmosis.core.domain.v0_6.Node;
import org.openstreetmap.osmosis.core.domain.v0_6.Way;
import org.openstreetmap.osmosis.core.task.v0_6.Sink;

import java.io.File;
import java.util.Map;

/**
 * An Osmosis plugin that reads OpenStreetMap data
 * 
 * @author bross
 */
public class DataGeneratorTask implements Sink {

    private static final String TAG = DataGeneratorTask.class.getSimpleName();

	// task configuration
	private Configuration conf;
	
	// container for all data
	ADataContainer dc = null;
	// generator for result database
	AGenerator generator = null;

	DataGeneratorTask(Configuration config) {
        Logger.d(TAG, "DataGeneratorTask(), start");

		// store configuration
		this.conf = config;

		// read all data
		try {
			if (conf.getGenerateType() == Configuration.GenerateType.POI) {
				DataWriterDefinition nodeHandler = new DataWriterDefinition(config.getFileConfig());

				// prepare data container
				int size = (int) (conf.getFileDatabase().length() / 1024L / 1024L);
                Logger.d(TAG, "Source size:" + size + ", max:" + 500);
				if (size <= 500) {
                    Logger.d(TAG, "creating data container: RAM");
					dc = new DataContainerRam(nodeHandler);
				} else {
                    Logger.d(TAG, "creating data container: HDD");
					dc = new DataContainerHdd(nodeHandler,
							new File(config.getFileDatabase().getAbsolutePath()+ ".temp"));
				}
				generator = new GeneratorPoi(conf.getFileDatabase(), nodeHandler);
			} else if (conf.getGenerateType() == Configuration.GenerateType.ADDRESS) {
				generator = new GeneratorAddress();
			}
		} catch (Exception e) {
            Logger.e(TAG, "DataGeneratorTask(), problem with preparations", e);
			generator = null;
		}

		// check generator
		if (dc == null || generator == null) {
			throw new IllegalArgumentException("DataContainer and Generator " +
					"cannot be initialized for type:" + conf.getGenerateType());
		}
	}

	public void initialize(Map<String, Object> metadata) {
		// nothing to do
	}

	public final void complete() {
		Logger.d(TAG, "complete reading, start generating");

		// finish loading of data container
		dc.finishLoading();

		// generate database
		generator.proceedData(dc);

		// destroy data
		dc.destroy();
		generator.destroy();
	}

	public final void release() {
		// nothing to do
	}

	public final void process(EntityContainer entityContainer) {
		Entity entity = entityContainer.getEntity();

		switch (entity.getType()) {
			case Bound:
				break;
			case Node:
				Node node = (Node) entity;
				dc.addNode(node);
				break;
			case Way:
				Way way = (Way) entity;
				dc.addWay(way);
				break;
			case Relation:
				break;
		}
	}
}
