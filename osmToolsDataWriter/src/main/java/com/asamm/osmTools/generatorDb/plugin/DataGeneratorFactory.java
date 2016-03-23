package com.asamm.osmTools.generatorDb.plugin;

import org.openstreetmap.osmosis.core.pipeline.common.TaskConfiguration;
import org.openstreetmap.osmosis.core.pipeline.common.TaskManager;
import org.openstreetmap.osmosis.core.pipeline.common.TaskManagerFactory;
import org.openstreetmap.osmosis.core.pipeline.v0_6.SinkManager;

import java.util.ArrayList;
import java.util.List;

import static com.asamm.osmTools.generatorDb.plugin.AConfiguration.GenerateType;

class DataGeneratorFactory extends TaskManagerFactory {

	private static final String PARAM_TYPE = "-type";
	private static final String PARAM_TESTING = "-testing";
	private static final String PARAM_TESTING_REPORT_FROM = "-testingFrom";
	private static final String PARAM_TESTING_REPORT_COUNT = "-testingCount";
    private static final String PARAM_DATA_CONTAINER_TYPE = "-dataContainerType";
    private static final String PARAM_DATA_MAP_ID = "-mapId";
    private static final String PARAM_DATA_NAME = "-name";
    private static final String PARAM_DATA_COUNTRIES = "-countries";

    private static final String PARAM_FILE_DB = "-fileDb";
    private static final String PARAM_FILE_CONFIG = "-fileConfig";
    private static final String PARAM_FILE_COUNTRY_GEOM = "-fileCountryGeom";
    private static final String PARAM_FILE_DATA_GEOM = "-fileDataGeom";
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

            // if will be used RAM or HDD CONTAINER
            confAddress.setDataContainerType(getStringArgument(taskConfig, PARAM_DATA_CONTAINER_TYPE, "").trim());
            // mapId to link with admin level definition from address configuration XML file
            confAddress.setMapId(getStringArgument(taskConfig, PARAM_DATA_MAP_ID).trim());
            // path to data of address database
            confAddress.setFileDatabase(getStringArgument(taskConfig, PARAM_FILE_DB, "").trim());
            // path to address configuration XML file
            confAddress.setFileConfigXml(getStringArgument(taskConfig, PARAM_FILE_CONFIG, "").trim());
            // path to file with geojson with bounds of map area
            confAddress.setFileDataGeom(getStringArgument(taskConfig, PARAM_FILE_DATA_GEOM, "").trim());
            //path to file with geojson with country border
            confAddress.setFileCountryGeom(getStringArgument(taskConfig, PARAM_FILE_COUNTRY_GEOM, "").trim());

            return confAddress;
        }

        else if (genType ==  GenerateType.COUNTRY_BOUNDARY){


            ConfigurationCountry confCountryBoundary = new ConfigurationCountry();

            if (doesArgumentExist(taskConfig, PARAM_DATA_COUNTRY_ADMIN_LEVEL)){
                confCountryBoundary.setAdminLevel(getStringArgument(taskConfig, PARAM_DATA_COUNTRY_ADMIN_LEVEL).trim());
            }

            String cmdCountriesAtr = getStringArgument(taskConfig, PARAM_DATA_COUNTRIES, "").trim();
            List<ConfigurationCountry.CountryConf> countriesConf = parseCountryDefinition(cmdCountriesAtr);
            confCountryBoundary.setCountriesConf(countriesConf);

            return confCountryBoundary;
        }
        else {
            throw new IllegalArgumentException("invalid generator type, ");
        }
    }

    /**
     * Parse cmd argument countries and create definitions that continas name of country and path to geojson
     *
     * @param cmdAtr cmd parameter "countries"
     * @return
     */
    private List<ConfigurationCountry.CountryConf> parseCountryDefinition (String cmdAtr){


        List<ConfigurationCountry.CountryConf> countriesConf = new ArrayList<>();
        if (cmdAtr == null || cmdAtr.length() == 0){
            return countriesConf;
        }

        String[] cmdAtrSplit = cmdAtr.split(",");

        if (cmdAtrSplit.length % 2 != 0) {
            throw  new IllegalArgumentException("Wrong number of countries elements cmd attribute : " + cmdAtr);
        }

        for (int i=0; i < cmdAtrSplit.length; i++){
            countriesConf.add(new ConfigurationCountry.CountryConf(cmdAtrSplit[i], cmdAtrSplit[++i]));
        }

        return countriesConf;
    }

}
