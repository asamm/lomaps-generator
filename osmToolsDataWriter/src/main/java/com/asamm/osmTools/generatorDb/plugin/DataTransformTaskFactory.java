package com.asamm.osmTools.generatorDb.plugin;

import org.openstreetmap.osmosis.core.pipeline.common.TaskConfiguration;
import org.openstreetmap.osmosis.core.pipeline.common.TaskManager;
import org.openstreetmap.osmosis.core.pipeline.common.TaskManagerFactory;
import org.openstreetmap.osmosis.core.pipeline.v0_6.SinkSourceManager;

/**
 * Created by voldapet on 15/1/2017.
 */
public class DataTransformTaskFactory extends TaskManagerFactory {

    private static final String PARAM_FILE_CONFIG = "-fileConfig";

    private static final String PARAM_FILE_DATA_GEOM = "-fileDataGeom";


    @Override
    protected TaskManager createTaskManagerImpl(TaskConfiguration taskConfig) {

        // Obtain cmd configuration parameters for plugin
        //ConfigurationTransform confTransform = createConfiguration(taskConfig);
        ConfigurationTransform confTransform = new ConfigurationTransform();

        DataTransformTask task = new DataTransformTask(confTransform);

        return new SinkSourceManager(taskConfig.getId(),task,taskConfig.getPipeArgs());
    }

    /**
     * Read location of config file and map boundary file
     * @param taskConfig Information required to instantiate and configure the task.
     * @return Parameters for transform task
     */
    private ConfigurationTransform createConfiguration(TaskConfiguration taskConfig) {

        ConfigurationTransform confTransform = new ConfigurationTransform();

        // path to transform configuration XML file
        confTransform.setFileConfigXml(getStringArgument(taskConfig, PARAM_FILE_CONFIG, "").trim());

        // path to file with geojson with bounds of map area
        confTransform.setFileDataGeom(getStringArgument(taskConfig, PARAM_FILE_DATA_GEOM, "").trim());

        return confTransform;
    }
}
