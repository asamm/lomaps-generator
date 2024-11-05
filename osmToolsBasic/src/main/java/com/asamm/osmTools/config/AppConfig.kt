package com.asamm.osmTools.config

import com.asamm.osmTools.LocusStoreEnv
import java.net.URL
import java.nio.file.Path
import java.nio.file.Paths


object AppConfig {

    lateinit var config: Config

    fun loadConfig(configFilePath: String = "config.yml") {
        if (!::config.isInitialized) {
            this@AppConfig.config = Config()
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
    var overwrite: Boolean = false,
    var actions: MutableSet<Action> = mutableSetOf<Action>(),
    var locusStoreEnv: LocusStoreEnv = LocusStoreEnv.PROD,
    var verbose: Boolean = false,

    var tempotaryDir: Path = Path.of("_temp"),

    var mapConfigXml: Path = Path.of(""),

    var touristConfig: TouristConfig = TouristConfig(),
    var contourConfig: ContourConfig = ContourConfig(),
    var planetConfig: PlanetConfig = PlanetConfig(),
    var cmdConfig: CmdConfig = CmdConfig(),

    var mapDescription: String = """
        <div><h4>Vector maps for <a href="http://www.locusmap.app">Locus</a> application</h4>
        Created by <a href="http://code.google.com/p/mapsforge/">Mapsforge</a> Map-Writer
        <br />
        Map data source OpenStreetMap community
        <br />
        Contour lines source <a href="http://srtm.usgs.gov">SRTM</a> and 
        <a href="http://www.viewfinderpanoramas.org">Viewfinder Panoramas</a>
        <br /><br />
        </div>""".trimIndent(),

)

data class TouristConfig(
    var nodeId: Long = 14000000000000L,
    var wayId: Long = 14500000000000L,

    //var loDmapsToolsPy: Path =  Paths.get("lomapsTools", "lomaps_tools.py")
    var lomapsToolsPy: Path = Paths.get("d:\\asamm\\projects\\lomaps-tools\\lomaps_tools\\", "lomaps_tools.py")
)

data class ContourConfig(

    var hgtDir: Path = Path.of("hgt"),
//    var nodeIdMeter: Long = 15_000_000_000_000L, // nodes for meter contour lines
//    var wayIdMeter: Long = 16_000_000_000_000L, // ways for meter contour lines
//    var nodeIdFeet: Long = 17_000_000_000_000L, // nodes for feet contour lines
//    var wayIdFeet: Long = 18_000_000_000_000L, // ways for feet contour lines
                                //10_000_000_000


    var nodeIdMeter: Long = 100_000_000_000L, // nodes for meter contour lines
    var wayIdMeter: Long =  130_000_000_000L, // ways for meter contour lines
    var nodeIdFeet: Long =  150_000_000_000L, // nodes for feet contour lines
    var wayIdFeet: Long =   180_000_000_000L, // ways for feet contour lines


    var stepMeter: Int = 20, // 20 meters
    var stepFeet: Int = 50, // 50 feet

    // major and minor contours
    var stepCategoryMeter: String = "100,50",
    var stepCategoryFeet: String = "400,200",
    var source: String = "view3,view1",

    //var polyCoverageMeter: Path = Path.of("polygons/_contours/planet_contours_meters.poly"),
    //var polyCoverageFeet: Path = Path.of("polygons/_contours/planet_contours_feet.poly"),
    var polyCoverageMeter: Path = Path.of("polygons/_contours/monaco_contours_meter.poly"),
    var polyCoverageFeet: Path = Path.of("polygons/_contours/monaco_contours_feet.poly"),

    var tempMetersFile: Path = Path.of("_contours/planet_meter.osm.pbf"), // temporary file for generated contours in meter
    var tempFeetFile: Path = Path.of("_contours/planet_feet.osm.pbf"), // temporary file for generated contours in feet

)

class PlanetConfig(
    var planetLatestPath: Path = Path.of("_planet", "orig", "planet-latest.osm.pbf"), // path to the original planet file
    var planetLatestURL: URL = URL("https://download.geofabrik.de/europe/monaco-latest.osm.pbf"), // URL to the latest planet file
    var planetExtendedId: String = "planet-extended"
)

class CmdConfig() {
    val pythonPath: String by lazy { ConfigUtils.findPythonPath() }

    val pyghtmap: String by lazy { ConfigUtils.getCheckPyhgtmapPath() }

    val osmium: String by lazy { ConfigUtils.getCheckOsmiumPath()}

    val planetiler: String by lazy { "D:\\asamm\\projects\\planetiler-openmaptiles\\target\\planetiler-openmaptiles-3.15.1-SNAPSHOT-with-deps.jar" }
}