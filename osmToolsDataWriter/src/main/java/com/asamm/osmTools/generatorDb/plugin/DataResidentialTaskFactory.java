package com.asamm.osmTools.generatorDb.plugin;

import org.openstreetmap.osmosis.core.pipeline.common.TaskConfiguration;
import org.openstreetmap.osmosis.core.pipeline.common.TaskManager;
import org.openstreetmap.osmosis.core.pipeline.common.TaskManagerFactory;
import org.openstreetmap.osmosis.core.pipeline.v0_6.SinkSourceManager;

public class DataResidentialTaskFactory extends TaskManagerFactory {

    private static final String PARAM_FILE_CONFIG = "-fileConfig";
    private static final String PARAM_FILE_DATA_GEOM = "-fileDataGeom";

    @Override
    protected TaskManager createTaskManagerImpl(TaskConfiguration taskConfig) {
        ConfigurationResidential config = new ConfigurationResidential();
        DataResidentialTask task = new DataResidentialTask(config);
        return new SinkSourceManager(taskConfig.getId(), task, taskConfig.getPipeArgs());
    }

    private ConfigurationResidential createConfiguration(TaskConfiguration taskConfig) {
        ConfigurationResidential config = new ConfigurationResidential();
        config.setFileConfigXml(getStringArgument(taskConfig, PARAM_FILE_CONFIG, "").trim());
        config.setFileDataGeom(getStringArgument(taskConfig, PARAM_FILE_DATA_GEOM, "").trim());
        return config;
    }
}