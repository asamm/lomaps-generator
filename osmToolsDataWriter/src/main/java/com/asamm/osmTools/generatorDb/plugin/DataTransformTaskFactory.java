package com.asamm.osmTools.generatorDb.plugin;

import org.openstreetmap.osmosis.core.pipeline.common.TaskConfiguration;
import org.openstreetmap.osmosis.core.pipeline.common.TaskManager;
import org.openstreetmap.osmosis.core.pipeline.common.TaskManagerFactory;
import org.openstreetmap.osmosis.core.pipeline.v0_6.SinkSourceManager;

/**
 * Created by voldapet on 15/1/2017.
 */
public class DataTransformTaskFactory extends TaskManagerFactory {
    @Override
    protected TaskManager createTaskManagerImpl(TaskConfiguration taskConfig) {

        DataTransformTask task = new DataTransformTask();

        return new SinkSourceManager(taskConfig.getId(),task,taskConfig.getPipeArgs());
    }
}
