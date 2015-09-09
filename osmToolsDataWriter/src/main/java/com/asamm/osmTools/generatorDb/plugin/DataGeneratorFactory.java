package com.asamm.osmTools.generatorDb.plugin;

import org.openstreetmap.osmosis.core.pipeline.common.TaskConfiguration;
import org.openstreetmap.osmosis.core.pipeline.common.TaskManager;
import org.openstreetmap.osmosis.core.pipeline.common.TaskManagerFactory;
import org.openstreetmap.osmosis.core.pipeline.v0_6.SinkManager;

class DataGeneratorFactory extends TaskManagerFactory {

	private static final String PARAM_TYPE = "-type";
	private static final String PARAM_FILE_DB = "-fileDb";
    private static final String PARAM_FILE_CONFIG = "-fileConfig";
	private static final String PARAM_TESTING = "-testing";
	private static final String PARAM_TESTING_REPORT_FROM = "-testingFrom";
	private static final String PARAM_TESTING_REPORT_COUNT = "-testingCount";
    private static final String PARAM_DATA_CONTAINER_TYPE = "-dataContainerType";

	@Override
	protected TaskManager createTaskManagerImpl(TaskConfiguration taskConfig) {
		// load configuration
		Configuration conf = new Configuration();

		// basics (required)
		conf.setGenerateType(getStringArgument(
				taskConfig, PARAM_TYPE, "").trim());
		conf.setFileDatabase(getStringArgument(
				taskConfig, PARAM_FILE_DB, "").trim());
		conf.setFileConfig(getStringArgument(
				taskConfig, PARAM_FILE_CONFIG, "").trim());

		// testing part (optional)
		conf.testing = getBooleanArgument(
				taskConfig, PARAM_TESTING, false);
		conf.reportFrom = getIntegerArgument(
				taskConfig, PARAM_TESTING_REPORT_FROM, 0);
		conf.reportCount = getIntegerArgument(
				taskConfig, PARAM_TESTING_REPORT_COUNT, 0);

        conf.setDataContainerType(getStringArgument(
                taskConfig, PARAM_DATA_CONTAINER_TYPE, "").trim());

		// validate configuration
		conf.validate();

		// define task
		DataGeneratorTask task = new DataGeneratorTask(conf);

		// return prepared configuration
		return new SinkManager(taskConfig.getId(), task, taskConfig.getPipeArgs());
	}
}
