package com.asamm.osmTools.overviewMap

import org.locationtech.jts.geom.Geometry

/**
 * Intermediate representation of a geo feature (from any source) with JTS geometry
 * and OSM tags ready for PBF writing.
 */
data class OverviewMapFeature(
    val geometry: Geometry,
    val osmTags: Map<String, String>,
    val sourceLayer: String,
)