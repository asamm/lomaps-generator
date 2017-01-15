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

    private static final String PARAM_STORAGE_TYPE = "-storageType";
    private static final String PARAM_STORAGE_TYPE_GEO_DATABASE = "geodatabase";
    private static final String PARAM_STORAGE_TYPE_GEOJSON = "geojson";

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

            String atrStorageType = getStringArgument(taskConfig, PARAM_STORAGE_TYPE, "").trim();
            String cmdCountriesAtr = getStringArgument(taskConfig, PARAM_DATA_COUNTRIES, "").trim();

            // parse definition what for which countries will be borders generated
            List<ConfigurationCountry.CountryConf> countriesConf = parseCountryDefinition(atrStorageType, cmdCountriesAtr);
            confCountryBoundary.setCountriesConf(countriesConf);

            // set the type of storage
            if (atrStorageType.equals(PARAM_STORAGE_TYPE_GEOJSON)){
                confCountryBoundary.setStorageType(ConfigurationCountry.StorageType.GEOJSON);
            }
            else if (atrStorageType.equals(PARAM_STORAGE_TYPE_GEO_DATABASE)){
                confCountryBoundary.setStorageType(ConfigurationCountry.StorageType.GEO_DATABASE);
            }

            return confCountryBoundary;
        }

        else if (genType == GenerateType.STORE_GEOCODE){
            ConfigurationGeoCoding confGeoCoding = new ConfigurationGeoCoding();

            // if will be used RAM or HDD CONTAINER
            confGeoCoding.setDataContainerType(getStringArgument(taskConfig, PARAM_DATA_CONTAINER_TYPE, "").trim());

            // mapId to link with admin level definition from address configuration XML file
            confGeoCoding.setMapId(getStringArgument(taskConfig, PARAM_DATA_MAP_ID).trim());
            // mapId to link with admin level definition from address configuration XML file
            confGeoCoding.setFileConfigXml(getStringArgument(taskConfig, PARAM_FILE_CONFIG, "").trim());

            //path to file with geojson with country border
            confGeoCoding.setFileCountryGeom(getStringArgument(taskConfig, PARAM_FILE_COUNTRY_GEOM, "").trim());

            return  confGeoCoding;
        }

        else {
            throw new IllegalArgumentException("invalid generator type, ");
        }
    }

    /**
     * Parse cmd argument countries and create definitions that contains name of country, store region id and
     * path to GeoJson if output is geojson
     * @param atrStorageType cmd attribute that define if generated country borders will be stored in Store Geo databse
     *                       or in local geoJson file
     * @param atrCountries list of definition and country names to generate
     * @return
     */
    private List<ConfigurationCountry.CountryConf> parseCountryDefinition(String atrStorageType, String atrCountries){

        List<ConfigurationCountry.CountryConf> countriesConf = new ArrayList<>();

        if (atrCountries == null || atrCountries.length() == 0 || atrStorageType == null || atrStorageType.length() == 0){
            return countriesConf;
        }

        if (atrStorageType.equals(PARAM_STORAGE_TYPE_GEOJSON)){
            String[] cmdAtrSplit = atrCountries.split(",");

            if (cmdAtrSplit.length % 3 != 0) {
                throw  new IllegalArgumentException("Wrong number of countries elements cmd attribute : " + atrCountries);
            }

            for (int i=0; i < cmdAtrSplit.length; i++){
                countriesConf.add(new ConfigurationCountry.CountryConf(cmdAtrSplit[i], cmdAtrSplit[++i], cmdAtrSplit[++i], cmdAtrSplit[++i]));
            }
        }
        else if (atrStorageType.equals(PARAM_STORAGE_TYPE_GEO_DATABASE)){

            // for geo database is not needed to define path to geojson file

            String[] cmdAtrSplit = atrCountries.split(",");

            if (cmdAtrSplit.length % 3 != 0) {
                throw  new IllegalArgumentException("Wrong number of countries elements cmd attribute : " + atrCountries);
            }

            for (int i=0; i < cmdAtrSplit.length; i++){
                countriesConf.add(new ConfigurationCountry.CountryConf(cmdAtrSplit[i],cmdAtrSplit[++i], cmdAtrSplit[++i]));
            }
        }
        else {
            throw  new IllegalArgumentException("Wrong storage type "   + atrStorageType);
        }

        return countriesConf;
    }

}
