package com.asamm.osmTools.generatorDb.plugin;

import org.openstreetmap.osmosis.core.pipeline.common.TaskManagerFactory;
import org.openstreetmap.osmosis.core.plugin.PluginLoader;

import java.util.HashMap;
import java.util.Map;

/**
 * The Osmosis PluginLoader for the data-generator Locus plugin
 * 
 * @author menion
 */
public class DataPluginLoader implements PluginLoader {

    public static final String PLUGIN_LOMAPS_DB = "loMapsDb";

	public static final String PLUGIN_DATA_TRANSFORM = "dataTransform";

	public Map<String, TaskManagerFactory> loadTaskFactories() {
    	// create factory that will handle request for generation LoMaps db
		DataLoMapsDbGeneratorFactory factory = new DataLoMapsDbGeneratorFactory();
		HashMap<String, TaskManagerFactory> map = new HashMap<String, TaskManagerFactory>();
		map.put(PLUGIN_LOMAPS_DB, factory);
		map.put("gDb", factory);

		DataTransformTaskFactory dataTransformTaskFactory = new DataTransformTaskFactory();
		map.put(PLUGIN_DATA_TRANSFORM,dataTransformTaskFactory);

        // return filled container
		return map;
	}
}
