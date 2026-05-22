package com.asamm.osmTools.mapConfig

import com.asamm.osmTools.config.Action
import com.asamm.osmTools.config.AppConfig
import com.asamm.osmTools.utils.Logger
import com.asamm.osmTools.utils.PolyUtils
import com.asamm.osmTools.utils.Utils
import net.minidev.json.JSONObject
import net.minidev.json.parser.JSONParser
import net.minidev.json.parser.ParseException
import org.apache.commons.io.FileUtils
import org.kxml2.io.KXmlParser
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.IOException
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Path

class ItemMap(parent: ItemMapPack?) : AItemMap(parent) {

    companion object {
        private val TAG = ItemMap::class.simpleName
    }

    /** Unique ID of this item */
    var id: String? = null
        private set

    /** Map file name (from the {@code file} XML attribute) */
    var fileName: String = ""
        private set

    /** Human-readable name shown in the store */
    var nameReadable: String = ""
        private set

    /** Alternative file name used during generating */
    private var nameGen: String = ""

    private val pathResolver = PathResolver(this)

    /** Bounds of this map, populated from the polygon file */
    var boundary: Boundaries? = null
        private set

    /** Returns true when this item represents the full planet extract */
    val isPlanet: Boolean
        get() = id != null && id == AppConfig.config.planetConfig.planetExtendedId

    /**
     * Country name with fallback: if not set on this item or its parent pack,
     * returns [nameReadable] so generators always have a usable country label.
     */
    override var countryName: String
        get() = super.countryName.ifEmpty { nameReadable }
        set(value) { super.countryName = value }

    // --- Validation ---

    override fun validate() {
        super.validate()
        require(fileName.isNotEmpty()) { "Input XML is not valid. Invalid argument file: $fileName" }
        if (hasAction(Action.ADDRESS_POI_DB) || hasAction(Action.GENERATE_MAPSFORGE)) {
            require(countryName.isNotEmpty()) {
                "Input XML is not valid. Country name not defined for map or its parent - name: $fileName"
            }
        }
    }

    // --- Parsing ---

    override fun fillAttributes(parser: KXmlParser) {
        super.fillAttributes(parser)
        parser.attr("id")?.let { id = it }
        parser.attr("file")?.let { fileName = Utils.changeSlash(it) }
        parser.attr("name")?.let {
            nameReadable = Utils.changeSlash(it)
            if (nameReadable.isEmpty()) {
                Logger.w(TAG, "Config.xml not valid: Missing attribute name on line: ${parser.lineNumber}")
            }
        }
        parser.attr("fileGen")?.let { nameGen = Utils.changeSlash(it) }
        validate()
    }

    // --- Paths ---

    /** Effective file name: uses [nameGen] when set, otherwise [fileName] */
    private val effectiveName: String get() = nameGen.ifEmpty { fileName }

    val pathSource: Path get() = pathResolver.getPath(PathType.EXTRACT, "$fileName.osm.pbf")
    val pathMapsforgeGenerate: Path get() = pathResolver.getPath(PathType.MAPSFORGE_GENERATE, "$effectiveName.osm.map")
    val pathMbtiles: Path get() = pathResolver.getPath(PathType.MBTILES_GENERATE, "$effectiveName.mbtiles")
    val pathAddressDb: Path get() = pathResolver.getPath(PathType.ADDRESS_DB, "$fileName.osm.db")

    /** Path to the legacy address POI database for LM Classic */
    val pathAddressPoiDb: Path get() = pathResolver.getPath(PathType.ADDRESS_POI_DB_CLASSIC, "$fileName.osm.db")

    val pathPoiV2Db: Path get() = pathResolver.getPath(PathType.POI_V2_DB, "$fileName.poiv2.db")
    val pathPolygon: Path get() = pathResolver.getPath(PathType.POLYGON, "$fileName.poly")
    val pathJsonPolygon: Path get() = Utils.changeFileExtension(pathPolygon, ".json")
    val pathCountryBoundaryGeoJson: Path get() = Utils.changeFileExtension(pathPolygon, "_country.geojson")
    val pathContour: Path get() = pathResolver.getPath(PathType.CONTOUR, "$fileName.osm.pbf")
    val pathResultMapsforge: Path get() = pathResolver.getPath(PathType.MAPSFORGE_RESULT, "$effectiveName.zip")
    val pathResultMapsforgeClassic: Path get() = Path.of(pathResultMapsforge.toString().replace(".zip", "_lmclassic.zip"))
    val pathResultMbtiles: Path get() = Path.of(pathResultMapsforge.toString().replace(".zip", "_mbtiles.zip"))
    val pathTourist: Path get() = pathResolver.getPath(PathType.TOURIST, "$fileName.osm.pbf")
    val pathResidential: Path get() = pathResolver.getPath(PathType.RESIDENTIAL, "$fileName.osm.pbf")
    val pathGenMlOutdoor: Path get() = pathResolver.getPath(
        PathType.MBTILES_ONLINE_OUTDOOR,
        "${if (isPlanet) "planet" else fileName}_lm_outdoor.mbtiles"
    )
    val pathGenPmtilesOnline: Path get() = pathResolver.getPath(
        PathType.PMTILES_ONLINE,
        "${if (isPlanet) "planet" else fileName}.pmtiles"
    )

    // --- Tools ---

    @Throws(IOException::class)
    fun setBoundsFromPolygon() {
        val polyFile = pathPolygon.toFile()
        if (!polyFile.exists()) {
            boundary = null
            return
        }

        var maxLat = -90.0;  var maxLon = -180.0
        var minLat = 90.0;   var minLon = 180.0

        BufferedReader(FileReader(polyFile)).use { br ->
            br.forEachLine { line ->
                val cols = line.trim().split("\\s+".toRegex())
                if (cols.size == 2 && Utils.isNumeric(cols[0]) && Utils.isNumeric(cols[1])) {
                    val lon = cols[0].toDouble()
                    val lat = cols[1].toDouble()
                    maxLon = maxOf(lon, maxLon); maxLat = maxOf(lat, maxLat)
                    minLon = minOf(lon, minLon); minLat = minOf(lat, minLat)
                }
            }
        }
        boundary = Boundaries(minLon, maxLon, minLat, maxLat)
    }

    /** Read the map polygon definition from GeoJSON, converting from .poly if needed */
    fun getItemAreaGeoJson(): JSONObject {
        val jsonFile = pathJsonPolygon.toFile()
        if (!jsonFile.exists()) {
            PolyUtils.polyFileToGeoJson(pathPolygon, jsonFile.toPath())
        }

        val jsonText = try {
            FileUtils.readFileToString(jsonFile, UTF_8)
        } catch (e: IOException) {
            Logger.e(TAG, "Can not read JSON polygon file ${jsonFile.absolutePath}", e)
            throw IllegalArgumentException("Can not read JSON polygon file ${jsonFile.absolutePath}")
        }

        return try {
            JSONParser(JSONParser.DEFAULT_PERMISSIVE_MODE).parse(jsonText) as JSONObject
        } catch (e: ParseException) {
            Logger.e(TAG, "Can not parse JSON polygon file ${jsonFile.absolutePath}", e)
            throw IllegalArgumentException("Can not parse JSON polygon file ${jsonFile.absolutePath}")
        }
    }

    override fun toString() =
        "ItemMap(id=$id, fileName=$fileName, nameGen=$nameGen, boundary=$boundary)"
}