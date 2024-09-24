package com.asamm.osmTools.config

import com.asamm.osmTools.LocusStoreEnv
import java.nio.file.Path


object AppConfig {

    lateinit var config: Config

    fun loadConfig(configFilePath: String = "config.yml") {
        if (!::config.isInitialized) {
            config = Config()
            // TODO load config from file
        }
    }

//    fun loadYamlConfig(configFilePath: String = "config.yml"): Config {
//        val yaml = Yaml()
//        val configFile = File(configFilePath)
//        return yaml.loadAs(configFile.inputStream(), Config::class.java)
//    }
}

data class Config(

    var version: String = "1975.01.01",
    var rewrite: Boolean = false,
    var actions: MutableSet<Action> = mutableSetOf<Action>(),
    var locusStoreEnv: LocusStoreEnv = LocusStoreEnv.PROD,
    var verbose: Boolean = false,

    var tempotaryDir: Path = Path.of("_temp"),

    var mapConfigXml: Path = Path.of(""),

    var touristConfig: TouristConfig = TouristConfig(),
    var contourConfig: ContourConfig = ContourConfig(),

    var mapDescription: String = """
        <div><h4>Vector maps for <a href="http://www.locusmap.app">Locus</a> application</h4>
        Created by <a href="http://code.google.com/p/mapsforge/">Mapsforge</a> Map-Writer
        <br />
        Map data source OpenStreetMap community
        <br />
        Contour lines source <a href="http://srtm.usgs.gov">SRTM</a> and 
        <a href="http://www.viewfinderpanoramas.org">Viewfinder Panoramas</a>
        <br /><br />
        </div>""".trimIndent()

)

data class TouristConfig(
    var nodeId: Long = 15000000000000L,
    var wayId: Long = 16000000000000L,
)

data class ContourConfig(
    var nodeId: Long = 17000000000000L,
    var wayId: Long = 18000000000000L,
    var stepMeter: Int = 20, // 20 meters
    var tepFeet: Int = 50,
    var source: String = "view3,view1",

    var pyghtmapFile: Path = Path.of("pyghtmap"),

)