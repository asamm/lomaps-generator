package com.asamm.osmTools.generatorDb.plugin;

import org.openstreetmap.osmosis.core.pipeline.common.TaskConfiguration;
import org.openstreetmap.osmosis.core.pipeline.common.TaskManager;
import org.openstreetmap.osmosis.core.pipeline.common.TaskManagerFactory;
import org.openstreetmap.osmosis.core.pipeline.v0_6.SinkManager;

import static com.asamm.osmTools.generatorDb.plugin.AConfiguration.GenerateType;

class DataGeneratorFactory extends TaskManagerFactory {

	private static final String PARAM_TYPE = "-type";
	private static final String PARAM_FILE_DB = "-fileDb";
    private static final String PARAM_FILE_CONFIG = "-fileConfig";
	private static final String PARAM_TESTING = "-testing";
	private static final String PARAM_TESTING_REPORT_FROM = "-testingFrom";
	private static final String PARAM_TESTING_REPORT_COUNT = "-testingCount";
    private static final String PARAM_DATA_CONTAINER_TYPE = "-dataContainerType";
    private static final String PARAM_DATA_MAP_ID = "-mapId";

    private static final String PARAM_FILE_COUNTRY_GEOM = "-fileGeom";
    private static final String PARAM_DATA_COUNTRY_ADMIN_LEVEL = "-countryAdminLevel";

	@Override
	protected TaskManager createTaskManagerImpl(TaskConfiguration taskConfig) {

        // create configuration from command line plugin params
        AConfiguration conf = createConfiguration(taskConfig);

		// validate configuration
		conf.validate();

		// define task
		DataGeneratorTask task = new DataGeneratorTask(conf);

		// return prepared configuration
		return new SinkManager(taskConfig.getId(), task, taskConfig.getPipeArgs());
	}

    /**
     * Create custom configuration based on type of generator
     *
     * @param taskConfig needed information for generation from command line
     * @return configuration for specified type
     */
    private AConfiguration createConfiguration (TaskConfiguration taskConfig){


        GenerateType genType = GenerateType.createFromValue(
                getStringArgument(taskConfig, PARAM_TYPE, "").trim());

        if (genType == GenerateType.POI) {
            ConfigurationPoi confPoi = new ConfigurationPoi();
            confPoi.setFileDatabase(getStringArgument(
                    taskConfig, PARAM_FILE_DB, "").trim());
            confPoi.setFileConfig(getStringArgument(
                    taskConfig, PARAM_FILE_CONFIG, "").trim());

            // testing part (optional)
            confPoi.testing = getBooleanArgument(
                    taskConfig, PARAM_TESTING, false);
            confPoi.reportFrom = getIntegerArgument(
                    taskConfig, PARAM_TESTING_REPORT_FROM, 0);
            confPoi.reportCount = getIntegerArgument(
                    taskConfig, PARAM_TESTING_REPORT_COUNT, 0);

            return confPoi;
        }

        else if (genType == GenerateType.ADDRESS){

            ConfigurationAddress confAddress = new ConfigurationAddress();

            confAddress.setFileDatabase(getStringArgument(
                    taskConfig, PARAM_FILE_DB, "").trim());
            confAddress.setFileConfig(getStringArgument(
                    taskConfig, PARAM_FILE_CONFIG, "").trim());

            confAddress.setDataContainerType(getStringArgument(taskConfig, PARAM_DATA_CONTAINER_TYPE, "").trim());

            if (doesArgumentExist(taskConfig, PARAM_DATA_MAP_ID)){
                confAddress.setMapId(getStringArgument(taskConfig, PARAM_DATA_MAP_ID).trim());
            }

            return confAddress;
        }

        else if (genType ==  GenerateType.COUNTRY_BOUNDARY){
            ConfigurationCountry confCountryBoundary = new ConfigurationCountry();
            confCountryBoundary.setFileGeom(getStringArgument(taskConfig, PARAM_FILE_COUNTRY_GEOM, "").trim());

            if (doesArgumentExist(taskConfig, PARAM_DATA_COUNTRY_ADMIN_LEVEL)){
                confCountryBoundary.setAdminLevel(
                        getStringArgument(taskConfig, PARAM_DATA_COUNTRY_ADMIN_LEVEL).trim());
            }
            return confCountryBoundary;
        }
        else {
            throw new IllegalArgumentException("invalid generator type, ");
        }
    }
}
