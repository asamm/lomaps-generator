package com.asamm.osmTools.generatorDb.plugin;

import com.asamm.osmTools.generatorDb.*;
import com.asamm.osmTools.generatorDb.dataContainer.ADataContainer;
import com.asamm.osmTools.generatorDb.dataContainer.DataContainerHdd;
import com.asamm.osmTools.generatorDb.dataContainer.DataContainerRam;
import com.asamm.osmTools.utils.Logger;
import org.openstreetmap.osmosis.core.container.v0_6.EntityContainer;
import org.openstreetmap.osmosis.core.domain.v0_6.Entity;
import org.openstreetmap.osmosis.core.domain.v0_6.Node;
import org.openstreetmap.osmosis.core.domain.v0_6.Relation;
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
	private ADataContainer dc = null;
	// generator for result database
    private AGenerator generator = null;

	DataGeneratorTask(Configuration config) {
        Logger.d(TAG, "DataGeneratorTask(), start");

		// store configuration
		this.conf = config;

		// read all data
		try {
			if (conf.getGenerateType() == Configuration.GenerateType.POI) {
				WriterPoiDefinition nodeHandler = new WriterPoiDefinition(config.getFileConfig());

				// prepare data container
				int size = (int) (conf.getFileDatabase().length() / 1024L / 1024L);
                Logger.i(TAG, "Source size:" + size + ", max:" + 600);
				if (size <= 500) {
                    Logger.d(TAG, "creating data container: RAM");
					dc = new DataContainerRam(nodeHandler);
				} else {
                    Logger.d(TAG, "creating data container: HDD");
					dc = new DataContainerHdd(nodeHandler,
							new File(config.getFileDatabase().getAbsolutePath()+ ".temp"));
				}
				generator = new GeneratorPoi(conf.getFileDatabase(), nodeHandler);
			}
            else if (conf.getGenerateType() == Configuration.GenerateType.ADDRESS) {
                Logger.i(TAG, "Start address generator");
                WriterAddressDefinition addressDefinition = new WriterAddressDefinition();

                if (conf.getDataContainerType() == Configuration.DataContainerType.RAM) {

                    Logger.i(TAG, "creating data container: RAM");
                    dc = new DataContainerRam(addressDefinition);

                    Logger.i(TAG, "creating data container: HDD");
                    dc = new DataContainerHdd(addressDefinition,
                            new File(config.getFileDatabase().getAbsolutePath()+ ".temp"));
                } else {
                    Logger.i(TAG, "creating data container: HDD");
                    dc = new DataContainerHdd(addressDefinition,
                            new File(config.getFileDatabase().getAbsolutePath()+ ".temp"));
                }

                generator = new GeneratorAddress(
                        addressDefinition,
                        new File(config.getFileDatabase().getAbsolutePath()));
			}
		} catch (Exception e) {
            Logger.e(TAG, "DataGeneratorTask(), problem with preparations", e);
			generator = null;
		}

		// check generator
		if (dc == null || generator == null) {
			throw new IllegalArgumentException("DataContainer (" + dc + "), or Generator (" + generator + "), " +
					"cannot be initialized for type:" + conf.getGenerateType());
		}
	}

    @Override
	public void initialize(Map<String, Object> metadata) {
		// nothing to do
	}

    @Override
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
                Relation relation = (Relation) entity;
                dc.addRelation(relation);
                break;
        }
    }

    @Override
	public final void complete() {

        dc.finalizeCaching();
		Logger.d(TAG, "complete reading, start generating");

		// generate database
		generator.proceedData(dc);

		// destroy data
		dc.destroy();
		generator.destroy();
	}

	public final void release() {
		// nothing to do

	}
}
