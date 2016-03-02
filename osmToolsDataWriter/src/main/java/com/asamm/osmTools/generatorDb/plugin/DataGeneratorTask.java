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

import static com.asamm.osmTools.generatorDb.plugin.AConfiguration.DataContainerType;
import static com.asamm.osmTools.generatorDb.plugin.AConfiguration.GenerateType;

/**
 * An Osmosis plugin that reads OpenStreetMap data
 * 
 * @author bross
 */
public class DataGeneratorTask implements Sink {

    private static final String TAG = DataGeneratorTask.class.getSimpleName();

	// container for all data
	private ADataContainer dc = null;
	// generator for result database
    private AGenerator generator = null;

	DataGeneratorTask(AConfiguration config) {
        Logger.d(TAG, "DataGeneratorTask(), start");


		// read all data
		try {

            // POI GENERATOR

			if (config.getGenerateType() == GenerateType.POI) {

                ConfigurationPoi confPoi = (ConfigurationPoi) config;
                WriterPoiDefinition nodeHandler = new WriterPoiDefinition(confPoi.getFileConfig());

				// prepare data container
				int size = (int) (confPoi.getFileDatabase().length() / 1024L / 1024L);
                Logger.i(TAG, "Source size:" + size + ", max:" + 600);
				if (size <= 500) {
                    Logger.d(TAG, "creating data container: RAM");
					dc = new DataContainerRam(nodeHandler);
				} else {
                    Logger.d(TAG, "creating data container: HDD");
					dc = new DataContainerHdd(nodeHandler,
							new File(confPoi.getFileDatabase().getAbsolutePath()+ ".temp"));
				}
				generator = new GeneratorPoi(confPoi.getFileDatabase(), nodeHandler);
			}

            // ADDRESS GENERATOR

            else if (config.getGenerateType() == GenerateType.ADDRESS) {
                Logger.i(TAG, "Start address generator");

                ConfigurationAddress confAddress = (ConfigurationAddress) config;
                WriterAddressDefinition addressDefinition = new WriterAddressDefinition(confAddress);

                // crate RAM or HDD storage for all entities
                if (confAddress.getDataContainerType() == DataContainerType.RAM) {
                    Logger.i(TAG, "creating data container: RAM");
                    dc = new DataContainerRam(addressDefinition);
                } else {
                    Logger.i(TAG, "creating data container: HDD");
                    dc = new DataContainerHdd(addressDefinition,
                            new File(confAddress.getFileDatabase().getAbsolutePath()+ ".temp"));
                }

                generator = new GeneratorAddress(addressDefinition);
			}

            // COUNTRY BOUNDARY GENERATOR

            else if (config.getGenerateType() == GenerateType.COUNTRY_BOUNDARY){
                Logger.i(TAG, "Start country boundary generator");
                ConfigurationCountry confCountry = (ConfigurationCountry) config;

                WriterCountryBoundaryDefinition wcbDefinition = new WriterCountryBoundaryDefinition();
                wcbDefinition.setAdminLevel(confCountry.getAdminLevel());

                Logger.i(TAG, "creating data container: RAM");
                dc = new DataContainerRam(wcbDefinition);

                generator = new GeneratorCountryBoundary(
                        wcbDefinition,
                        new File(confCountry.getFileGeom().getAbsolutePath()));
            }

		} catch (Exception e) {
            Logger.e(TAG, "DataGeneratorTask(), problem with preparations", e);
			generator = null;
		}

		// check generator
		if (dc == null || generator == null) {
			throw new IllegalArgumentException("DataContainer (" + dc + "), or Generator (" + generator + "), " +
					"cannot be initialized for type:" + config.getGenerateType());
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
		Logger.i(TAG, "complete reading, start generating");

		// GENERATE DATA
		generator.proceedData(dc);

		// destroy data
		dc.destroy();
		generator.destroy();
	}

	public final void release() {
		// nothing to do

	}
}
