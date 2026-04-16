package com.asamm.osmTools.config


import com.asamm.store.LocusStoreEnv
import com.charleskorn.kaml.AnchorsAndAliases
import com.charleskorn.kaml.Yaml
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.io.File
import java.net.URI
import java.net.URL
import java.nio.file.Path
import java.nio.file.Paths


object AppConfig {

    // create variable with path to config yaml file
    val configFilePath = "config/app_config.yaml"

    lateinit var config: Config

    fun loadConfig() {
        if (!::config.isInitialized) {
            config = loadYamlConfig(configFilePath)
        }
        ConfigUtils.loadAwsCredentialsFromEnv()
    }

    fun loadYamlConfig(configFilePath: String = "config.yml"): Config {
        val yaml =
            Yaml(configuration = Yaml.default.configuration.copy(anchorsAndAliases = AnchorsAndAliases.Permitted()))
        val configFile = File(configFilePath)
        return yaml.decodeFromString(Config.serializer(), configFile.readText())
    }
}


@Serializable
data class Config(

    @Transient
    var version: String = "",

    var overwrite: Boolean = false,
    var actions: MutableList<Action> = mutableListOf<Action>(),
    var locusStoreEnv: LocusStoreEnv = LocusStoreEnv.PROD,
    var verbose: Boolean = false,

    @Serializable(with = PathSerializer::class)
    var temporaryDir: Path = Path.of("_temp"),

    @Serializable(with = PathSerializer::class)
    var mapsForgeDir: Path = Path.of("./_mapsforge"),

    @Serializable(with = PathSerializer::class)
    var mbtilesDir: Path = Path.of("./_mbtiles"),

    @Serializable(with = PathSerializer::class)
    var planetDir: Path = Path.of("./_planet"),

    @Serializable(with = PathSerializer::class)
    var storeUploaderPath: Path,

    @Transient @Serializable(with = PathSerializer::class)
    var storeUploadDefinitionJson: Path = Path.of("storeUploadeDefinition.json"),

    @Serializable(with = PathSerializer::class)
    var defaultStoreItemDefinitionPath: Path = Path.of("config/default_store_item_definition.json"),


    var touristConfig: TouristConfig,
    var contourConfig: ContourConfig,
    var planetConfig: PlanetConfig,
    var cmdConfig: CmdConfig,
    var maptilerCloudConfig: MaptilerCloudConfig,
    var onlineLoMapsConfig: OnlineLoMapsConfig,
    var mbtilesConfig: MbtilesConfig,
    var mapsforgeConfig: MapsforgeConfig,
    var coastlineConfig: CoastlineConfig,
    var poiAddressConfig: PoiAddressConfig,
    var terrainRgbConfig: TerrainRgbConfig,
    var overviewMapConfig: OverviewMapConfig

    ) {
    fun toYaml(): String {
        val yaml = Yaml.default
        return yaml.encodeToString(serializer(), this)
    }
}

@Serializable
data class TouristConfig(
    var nodeId: Long,
    var wayId: Long,

    //var loDmapsToolsPy: Path =  Paths.get("lomapsTools", "lomaps_tools.py")
    @Serializable(with = PathSerializer::class)
    var lomapsToolsPy: Path
)

@Serializable
data class ContourConfig(
    @Serializable(with = PathSerializer::class)
    var hgtDir: Path,

    var nodeIdMeter: Long,
    var nodeIdFeet: Long,
    var wayIdMeter: Long,
    var wayIdFeet: Long,

    var stepMeter: Int,
    var stepFeet: Int,

    // major and minor contours
    var stepCategoryMeter: String,
    var stepCategoryFeet: String,
    var
    source: String,

    //var polyCoverageMeter: Path = Path.of("polygons/_contours/planet_contours_meters.poly"),
    //var polyCoverageFeet: Path = Path.of("polygons/_contours/planet_contours_feet.poly"),
    @Serializable(with = PathSerializer::class)
    var polyCoverageMeter: Path,

    @Serializable(with = PathSerializer::class)
    var polyCoverageFeet: Path,

    @Serializable(with = PathSerializer::class)
    var tempMetersFile: Path = Path.of("_contours/planet_meter.osm.pbf"), // temporary file for generated contours in meter

    @Serializable(with = PathSerializer::class)
    var tempFeetFile: Path = Path.of("_contours/planet_feet.osm.pbf"), // temporary file for generated contours in feet

)

@Serializable
class CoastlineConfig(
    var nodeBorderId: Long,

    @Serializable(with = PathSerializer::class)
    @SerialName("landPolygonShp")
    val _landPolygonShp: Path = Path.of("coastlines/land_polygons/land_polygons.shp"),

    val landPolygonUrl: String = "https://osmdata.openstreetmap.de/download/land-polygons-complete-4326.zip"
) {
    @Serializable(with = PathSerializer::class)
    val landPolygonShp: Path
        get() = AppConfig.config.mapsForgeDir.resolve(_landPolygonShp)
}


@Serializable
class PlanetConfig(
    @Serializable(with = PathSerializer::class)
    var planetLatestPath: Path, // path to the original planet file
    @Serializable(with = URLSerializer::class)
    var planetLatestURL: URL, // URL where to download the latest planet file

    @Serializable(with = PathSerializer::class)
    var planetilerDownloadDir: Path, // path to the planetiler data folder

    var planetExtendedId: String, // id of the extended planet file (extended is planet file with contours and tourit)

    var lomapsOutdoorsLayers: MutableSet<String> // set of planetiler layers that are used for LoMaps outdoor tile scheme
)

@Serializable
class MaptilerCloudConfig(

    var tilesetTitleLm: String = "LoMaps_Outdoor",
    var tilesetAttributionLm: String,
    var tilesetDescLm: String,
)

@Serializable
class OnlineLoMapsConfig(
    var s3region: String,
    var s3bucket: String,
    var s3endpoint: String,
    var s3pmtilesPath: String,
    var s3pmtilesPathDev: String, // for testing with dev path
    @Transient var s3accessKey: String = "", // Set via environment variable S3_ACCESS_KEY
    @Transient var s3secretKey: String = "", // Set via environment variable S3_SECRET_KEY
    var s3terrainRgbPath: String, // Path to upload terrain rgb planet file
    var s3terrainRgbPathDev: String,
)

@Serializable
data class PoiAddressConfig(
    @Transient
    var dbPoiVersion: Int = 2,

    @Transient
    var dbAddressVersion: Int = 2,

    @Serializable(with = PathSerializer::class)
    var poiDbXml: Path,

    @Serializable(with = PathSerializer::class)
    var addressDbXml: Path
)

@Serializable
class MbtilesConfig(
    var mapDescription: String,

    var mapAttribution: String
)

@Serializable
class MapsforgeConfig(

    @Serializable(with = PathSerializer::class)
    var mapConfigXml: Path = Path.of(""),

    @Serializable(with = PathSerializer::class)
    var tagMapping: Path,

    var mapDescription: String,

    var mapMetaDataDescription: String,
)

@Serializable
class TerrainRgbConfig(

    /** LoMaps-ready terrain RGB PMTiles - path to local folder where is */
    @Serializable(with = PathSerializer::class)
    val planetFile: Path,

    val maxZoom: Int = 11,

    /** URL of the Mapterhorn download index JSON used to resolve the planet file download URL and MD5. */
    val mapterhornIndexUrl: String = "https://download.mapterhorn.com/download_urls.json",

    val mapterhornPlanetEntryName: String = "6-30-21.pmtiles",  // for production planet.pmtiles

    /** Raw Mapterhorn planet file as downloaded (before zoom-level filtering). */
    @Serializable(with = PathSerializer::class)
    val mapterhornRawFile: Path = Path.of("_planet/terrain_rgb/mapterhorn_raw.pmtiles"),

    /** Output directory for generated HGT elevation files. */
    @Serializable(with = PathSerializer::class)
    val hgtOutputDir: Path = Path.of("_planet/terrain_rgb/hgt"),

    /** Resampling method for terrain-RGB to HGT conversion. */
    val hgtResampling: HgtResampling = HgtResampling.BILINEAR,
)

/** Resampling method used when converting terrain-RGB tiles to HGT grid. */
@Serializable
enum class HgtResampling {
    /** 2×2 pixel neighborhood, smooth interpolation. Standard for DEM data. */
    BILINEAR,
    /** 4×4 pixel neighborhood (Keys cubic, a=-0.5). Sharper but may overshoot at edges. */
    BICUBIC,
}

@Serializable
class OverviewMapConfig(

    /** URL to the Natural Earth 6.0 https://shadedrelief.com data Shapefiles */
    val baseMapShpUrl: String = "https://www.shadedrelief.com/ne-draft/World-Base-Map-Shapefiles.zip",

    val ecoregionsShpUrl: String = "download/overview_map/ecoregions2017.zip", // TODO path to github or any asamm storage with this SHP

    /** URL to the Natural Earth GeoPackage ZIP */
    val gpkgUrl: String = "https://naciscdn.org/naturalearth/packages/natural_earth_vector.gpkg.zip",

    /** Directory for downloaded/extracted data for overview map */
    @Serializable(with = PathSerializer::class)
    val dataDir: Path = Path.of("download/overview_map"),

    /** Output PBF file with all NE data converted to OSM format */
    @Serializable(with = PathSerializer::class)
    val outputPbf: Path = Path.of("_planet/overview/planet_overview.osm.pbf"),

    /** Start node ID for NE features (must not conflict with existing ranges) */
    val startNodeId: Long,

    /** Start way ID for NE features */
    val startWayId: Long,

    /** Start relation ID for NE features */
    val startRelationId: Long,
)

@Serializable
class CmdConfig(
    @Serializable(with = PathSerializer::class)
    val planetiler: Path,

    val planetilerRamXmx: String = "16G",

    val planetilerRamXmn: String = "8G",

    @Serializable(with = PathSerializer::class)
    var osmosis: Path,

    // POI V2
    @Serializable(with = PathSerializer::class)
    val poiDbV2Init: Path,

    @Serializable(with = PathSerializer::class)
    val poiDbV2Generator: Path
) {
    val pyghtmap: String by lazy { ConfigUtils.getCheckPyhgtmapPath() }

    val osmium: String by lazy { ConfigUtils.getCheckOsmiumPath() }

    val rioRgbify: String by lazy { ConfigUtils.getCheckRioRgbifyPath() }

    val gdalbuildvrt: String by lazy { ConfigUtils.getCheckGdalPath("gdalbuildvrt") }

    val gdalwarp: String by lazy { ConfigUtils.getCheckGdalPath("gdalwarp") }
}


// SERIALIZER FOR PATH

object PathSerializer : KSerializer<Path> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Path", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Path) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): Path {
        return Paths.get(decoder.decodeString())
    }
}

// CUSTOM SERIALIZER FOR URL

object URLSerializer : KSerializer<URL> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("URL", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: URL) {
        encoder.encodeString(value.toString()) // Serialize the URL as a string
    }

    override fun deserialize(decoder: Decoder): URL {
        return URI.create(decoder.decodeString()).toURL()
    }

}