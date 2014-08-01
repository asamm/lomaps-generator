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

    public static final String PLUGIN_COMMAND = "generatorDb";

	public Map<String, TaskManagerFactory> loadTaskFactories() {
    	// create factory that will handle requests
		DataGeneratorFactory factory = new DataGeneratorFactory();
		HashMap<String, TaskManagerFactory> map = new HashMap<String, TaskManagerFactory>();
		map.put(PLUGIN_COMMAND, factory);
		map.put("gDb", factory);

        // return filled container
		return map;
	}
}
