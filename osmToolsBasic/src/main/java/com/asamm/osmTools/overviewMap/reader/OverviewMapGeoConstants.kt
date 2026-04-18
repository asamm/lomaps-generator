package com.asamm.osmTools.overviewMap.reader

import com.asamm.osmTools.utils.MercatorUtils
import org.locationtech.jts.geom.Envelope
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.GeometryFactory


internal const val TAG_NE_MIN_ZOOM = "ne_min_zoom"
internal const val TAG_NE_MAX_ZOOM = "ne_max_zoom"

// Web Mercator valid latitude range ≈ ±85.051129°; geometries outside this cause
// projection overflow and must be clipped before writing to PBF.
internal val MERCATOR_BOUNDS_GEOM: Geometry = GeometryFactory().toGeometry(
    Envelope(-180.0, 180.0, -MercatorUtils.WEB_MERCATOR_MAX_LAT, MercatorUtils.WEB_MERCATOR_MAX_LAT)
)