package com.asamm.osmTools.overviewMap

/**
 * A minimal, library-agnostic interface for accessing feature attributes by name.
 *
 * Used by [LayerDefinition] filter and attribute-mapper functions so that they remain
 * independent of the specific reading library (GeoTools [SimpleFeature] for SHP files,
 * or [mil.nga.geopackage.features.user.FeatureRow] for GeoPackage files).
 */
fun interface FeatureAttributes {
    /** Returns the value of the named attribute, or `null` if it does not exist. */
    fun get(name: String): Any?
}

/**
 * Defines which layers to process and how to map their attributes to OSM tags.
 */

enum class DataSource {
    /** Curated World Base Map Shapefiles from shadedrelief.com */
    BASE_MAP_SHP,

    /** Standard Natural Earth GeoPackage */
    GPKG,

    /** RESOLVE Ecoregions 2017 Shapefiles */
    ECOREGIONS_SHP,
}

/**
 * A single layer definition with its OSM tag mapping.
 *
 * @param layerName table/file name (e.g. "ne_50m_ocean")
 * @param source which data source this layer comes from
 * @param staticTags tags always applied to every feature in this layer
 * @param minZoom minimum zoom level for this layer
 * @param maxZoom maximum zoom level for this layer
 * @param attributeMapper optional function to derive additional OSM tags from feature attributes
 * @param filter optional predicate to skip features
 * @param toCenterLine if true, convert polygon geometries to centerlines (for lakes, areals)
 */
data class LayerDefinition(
    val layerName: String,
    val source: DataSource,
    val staticTags: Map<String, String> = emptyMap(),
    val minZoom: Int,
    val maxZoom: Int,
    val attributeMapper: ((FeatureAttributes) -> Map<String, String>)? = null,
    val filter: ((FeatureAttributes) -> Boolean)? = null,
    val toCenterLine: Boolean = false,
)

/**
 * All layer definitions used for the simplified global map (zoom 0-9).
 */
object OverviewMapLayers {

    /** Helper to extract a trimmed, non-empty string attribute. */
    private fun FeatureAttributes.str(name: String): String? {
        val v = get(name) ?: return null
        val s = v.toString().trim()
        return s.ifEmpty { null }
    }

    // ---- OCEANS ----

    private val OCEAN_TAGS = mapOf("ne_natural" to "water", "ne_water" to "ocean")

    private val ne110mOcean = LayerDefinition(
        layerName = "ne_110m_ocean",
        source = DataSource.GPKG,
        staticTags = OCEAN_TAGS,
        minZoom = 0, maxZoom = 4,
    )

    private val ne50mOcean = LayerDefinition(
        layerName = "ne_50m_ocean",
        source = DataSource.GPKG,
        staticTags = OCEAN_TAGS,
        minZoom = 4, maxZoom = 9,
    )

    // OCEAN CENTER LINES

    private fun geographyCenterLineMapper(f: FeatureAttributes): Map<String, String> = buildMap {
        f.str("featurecla")?.let { put("type", it) }
        f.str("name")?.let { put("name", it) }
        f.str("name_en")?.let { put("name:en", it) }

        val scalerank = ((f.get("scalerank") as? Number)?.toInt() ?: 0) + 1
        put("rank", scalerank.toString())
    }

    private val ne110mOcenCenterLines = LayerDefinition(
        layerName = "ne_110m_geography_marine_polys",
        source = DataSource.GPKG,
        staticTags = mapOf("ne_geography_marine" to "yes"),
        minZoom = 0, maxZoom = 4,
        attributeMapper = ::geographyCenterLineMapper,
        toCenterLine = true
    )

    private val ne50mOceanCenterLines = LayerDefinition(
        layerName = "ne_50m_geography_marine_polys",
        source = DataSource.GPKG,
        staticTags = mapOf("ne_geography_marine" to "yes"),
        minZoom = 5, maxZoom = 9,
        attributeMapper = ::geographyCenterLineMapper,
        toCenterLine = true,
    )

//    private val ne10mOceanCenterLines = LayerDefinition(
//        layerName = "ne_10m_geography_marine_polys",
//        source = DataSource.GPKG,
//        staticTags = mapOf("geography_marine" to "yes"),
//        minZoom = 3, maxZoom = 9,
//        attributeMapper = ::geographyCenterLineMapper,
//        toCenterLine = true,
//    )

    // REGION CENTER NAMES

    private val ne110mRegionsCenterLines = LayerDefinition(
        layerName = "ne_110m_geography_regions_polys",
        source = DataSource.GPKG,
        staticTags = mapOf("ne_geography_regions" to "yes"),
        minZoom = 0, maxZoom = 4,
        attributeMapper = ::geographyCenterLineMapper,
        toCenterLine = true
    )

    private val ne50mRegionsCenterLines = LayerDefinition(
        layerName = "ne_50m_geography_regions_polys",
        source = DataSource.GPKG,
        staticTags = mapOf("ne_geography_regions" to "yes"),
        minZoom = 5, maxZoom = 9,
        attributeMapper = ::geographyCenterLineMapper,
        toCenterLine = true,
    )

//    private val ne10mRegionsCenterLines = LayerDefinition(
//        layerName = "ne_10m_geography_regions_polys",
//        source = DataSource.GPKG,
//        staticTags = mapOf("geography_regions" to "yes"),
//        minZoom = 4, maxZoom = 9,
//        attributeMapper = ::geographyCenterLineMapper,
//        toCenterLine = true,
//    )

    // ---- LAKES ----

    private val LAKE_TAGS = mapOf("ne_natural" to "water")

    private fun lakesMapper(f: FeatureAttributes): Map<String, String> = buildMap {
        f.str("name")?.let { put("name", it) }
    }

    private val ne50mLakes = LayerDefinition(
        layerName = "ne_50m_lakes",
        source = DataSource.GPKG,
        staticTags = LAKE_TAGS,
        minZoom = 2, maxZoom = 4,
        attributeMapper = ::lakesMapper,
    )

    private val ne10mLakes = LayerDefinition(
        layerName = "ne_10m_lakes",
        source = DataSource.GPKG,
        staticTags = LAKE_TAGS,
        minZoom = 5, maxZoom = 9,
        attributeMapper = ::lakesMapper,
    )

    // ---- COUNTRY BOUNDARIES ----

    private val COUNTRY_BOUNDARY_TAGS = mapOf(
        "boundary" to "ne_administrative",
        "admin_level" to "2",
    )

    private val ne110mBoundary = LayerDefinition(
        layerName = "ne_110m_admin_0_boundary_lines_land",
        source = DataSource.GPKG,
        staticTags = COUNTRY_BOUNDARY_TAGS,
        minZoom = 0, maxZoom = 1,
    )

    private val ne50mBoundary = LayerDefinition(
        layerName = "ne_50m_admin_0_boundary_lines_land",
        source = DataSource.GPKG,
        staticTags = COUNTRY_BOUNDARY_TAGS,
        minZoom = 2, maxZoom = 4,
    )

    private val ne10mBoundary = LayerDefinition(
        layerName = "ne_10m_admin_0_boundary_lines_land",
        source = DataSource.GPKG,
        staticTags = COUNTRY_BOUNDARY_TAGS,
        minZoom = 5, maxZoom = 9,
    )

    // ---- STATE/PROVINCE BOUNDARIES ----

    private val STATE_BOUNDARY_TAGS = mapOf(
        "boundary" to "ne_administrative",
        "admin_level" to "4",
    )

    private val ne10mStateBoundary = LayerDefinition(
        layerName = "ne_10m_admin_1_states_provinces_lines",
        source = DataSource.GPKG,
        staticTags = STATE_BOUNDARY_TAGS,
        minZoom = 3, maxZoom = 9,
    )

    // ---- RIVERS ----

    private val RIVER_TAGS = mapOf("ne_waterway" to "river")

    private fun riverMapper(f: FeatureAttributes): Map<String, String> = buildMap {
        f.str("name")?.let { put("name", it) }
    }

    private fun riverFilter(f: FeatureAttributes): Boolean {
        val featurecla = f.str("featurecla") ?: return true
        return featurecla == "River"
    }

    private val ne110mRivers = LayerDefinition(
        layerName = "ne_110m_rivers_lake_centerlines",
        source = DataSource.GPKG,
        staticTags = RIVER_TAGS,
        minZoom = 0, maxZoom = 3,
        attributeMapper = ::riverMapper,
        filter = ::riverFilter,
    )

    private val ne50mRivers = LayerDefinition(
        layerName = "ne_50m_rivers_lake_centerlines",
        source = DataSource.GPKG,
        staticTags = RIVER_TAGS,
        minZoom = 4, maxZoom = 9,
        attributeMapper = ::riverMapper,
        filter = ::riverFilter,
    )

    // ---- GLACIATED AREAS ----

    private val GLACIER_TAGS = mapOf("ne_natural" to "glacier")

    private val ne50mGlaciers = LayerDefinition(
        layerName = "ne_50m_glaciated_areas",
        source = DataSource.GPKG,
        staticTags = GLACIER_TAGS,
        minZoom = 2, maxZoom = 4,
    )

    private val ne10mGlaciers = LayerDefinition(
        layerName = "ne_10m_glaciated_areas",
        source = DataSource.GPKG,
        staticTags = GLACIER_TAGS,
        minZoom = 5, maxZoom = 9,
    )

    // ---- URBAN AREAS ----

    private val ne50mUrban = LayerDefinition(
        layerName = "ne_50m_urban_areas",
        source = DataSource.GPKG,
        staticTags = mapOf("ne_landuse" to "residential"),
        minZoom = 4, maxZoom = 9,
    )

    // ---- POPULATED PLACES ----

    private fun populatedPlaceMapper(f: FeatureAttributes): Map<String, String> = buildMap {
        f.str("NAME")?.let { put("name", it) }
        f.str("ADM0NAME")?.let { put("is_in:country", it) }

        // Determine place type based on scalerank and population
        val scalerank = (f.get("SCALERANK") as? Number)?.toInt() ?: 10
        val popMax = (f.get("POP_MAX") as? Number)?.toLong() ?: 0

        val placeType = when {
            scalerank <= 3 || popMax > 500_000 -> "city"
            popMax > 50_000 -> "town"
            else -> "village"
        }
        put("place", placeType)

        if (popMax > 0) {
            put("population", popMax.toString())
        }
    }

    private val ne10mPopulatedPlaces = LayerDefinition(
        layerName = "ne_10m_populated_places",
        source = DataSource.GPKG,
        staticTags = emptyMap(),
        minZoom = 2, maxZoom = 9,
        attributeMapper = ::populatedPlaceMapper,
    )

    // --- ECOREGIONS SHP ---

    private fun ecoregionsMapper(f: FeatureAttributes): Map<String, String> = buildMap {
        f.str("LANDTYPE")?.let { put("ne_landtype", it) }
        (f.get("BIOME") as? Number)?.toInt()?.let { put("biome", it.toString()) }
    }

    private val ecoregions2017 = LayerDefinition(
        layerName = "wwf_terr_ecos_dissolved",
        source = DataSource.ECOREGIONS_SHP,
        minZoom = 0, maxZoom = 9,
        attributeMapper = ::ecoregionsMapper,
    )

    // --- BASE MAP SHP ---

    // ROADS & FERRY

    private fun roadMapper(f: FeatureAttributes): Map<String, String> = buildMap {
        f.str("name")?.let { put("name", it) }
        f.str("featurecla")?.let {
            when (it) {
                "Ferry" -> put("ne_route", "ferry")
                "Road" -> {
                    f.str("type")?.let { type ->
                        when (type) {
                            "Expressway" -> put("ne_highway", "motorway")
                            "Road" -> put("ne_highway", "primary")
                            "Track", "Other Highway" -> put("ne_highway", "other")
                            else -> put("ne_highway", "other")
                        }
                    }
                }
            }
        }
    }

    private val bmRoadFerries = LayerDefinition(
        layerName = "Road_Ferries-beta2",
        source = DataSource.BASE_MAP_SHP,
        staticTags = emptyMap(),
        minZoom = 5, maxZoom = 9,
        attributeMapper = ::roadMapper,
    )


    // --- RAILROADS ---
    private val RAILS_TAGS = mapOf("ne_railway" to "rail")

    private fun railsMapper(f: FeatureAttributes): Map<String, String> = buildMap {
        val scalerank = (f.get("scalerank") as? Number)?.toInt()

        when (scalerank) {
            4, 5 -> put("rank", "1")
            6 -> put("rank", "2")
            7 -> put("rank", "3")
            8 -> put("rank", "4")
            9, 10 -> put("rank", "5")
            else -> put("rank", "4")
        }
    }

    private val bmRailroads = LayerDefinition(
        layerName = "Railroads-beta2",
        source = DataSource.BASE_MAP_SHP,
        staticTags = RAILS_TAGS,
        minZoom = 5, maxZoom = 9,
        attributeMapper = ::railsMapper,
    )

    // ---- ALL LAYERS ----

    val ALL: List<LayerDefinition> = listOf(
        // Ecoregions
        ecoregions2017,
        // Oceans
        ne110mOcean, ne50mOcean, //ne10mOcean,
        // Ocean center lines (polygon → centerline for labels)
        ne110mOcenCenterLines, ne50mOceanCenterLines, //ne10mOceanCenterLines,
        // Geography region center lines
        ne110mRegionsCenterLines, ne50mRegionsCenterLines, // ,ne10mRegionsCenterLines,
        // Lakes
//        ne110mLakes, ne50mLakes, ne10mLakes,
//        // Country boundaries
//        ne110mBoundary, ne50mBoundary, ne10mBoundary,
//        // State boundaries
//        ne10mStateBoundary,
//        // Rivers
//        ne110mRivers, ne50mRivers,
//        // Glaciated areas
//        ne50mGlaciers, ne10mGlaciers,
//        // Urban areas
//        ne50mUrban,
//        // Populated places
//        ne10mPopulatedPlaces,
//        // Base map SHP
//        // Roads
//        bmRoadFerries,
//        // Railroads
//        bmRailroads,
    )
}