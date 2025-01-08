package com.asamm.osmTools.mapConfig

import com.asamm.osmTools.config.AppConfig
import java.nio.file.Path

enum class PathType {
    TOURIST,
    CONTOUR,
    EXTRACT,
    COASTLINE,
    POLYGON,
    TRANSFORM,
    SHP,
    MERGE,
    ADDRESS_POI_DB,
    MAPSFORGE_GENERATE,
    MAPSFORGE_RESULT,
    MAPLIBRE_RESULT,
    MAPLIBRE_GENERATE,
    MAPLIBRE_ONLINE_OUTDOOR,
    MAPLIBRE_ONLINE_OPENMAPTILES,



}

class PathResolver(val map: ItemMap) {

    // Path to current working directory
    private val workingDirectory = Path.of(".")

    // Path to data directory
    private val dataDirectory = AppConfig.config.dataDir

    // Version name (date e.q. 2021.01.01/europe/....) as syubfolder
    val versionPath: Path = Path.of(AppConfig.config.version, map.dir)

    fun getPath(type: PathType,fileName: String): Path {

        return when (type) {
            // completely static located in working directory
            PathType.POLYGON -> workingDirectory.resolve("polygons").resolve(map.dir).resolve(fileName)

            // located in data directory not generated with every version
            PathType.TRANSFORM -> dataDirectory.resolve("transform").resolve(map.dir).resolve(fileName)
            PathType.COASTLINE -> dataDirectory.resolve("coastlines").resolve("_pbf").resolve(map.dir).resolve(fileName)
            PathType.SHP -> dataDirectory.resolve("coastlines").resolve("_shp").resolve(map.dir).resolve(fileName)
            PathType.CONTOUR -> dataDirectory.resolve("contours").resolve(map.dir).resolve(fileName)

            // temporary data generated with every version located in data directory
            PathType.TOURIST -> dataDirectory.resolve("_tourist").resolve(versionPath).resolve(fileName)
            PathType.EXTRACT -> dataDirectory.resolve("_extract").resolve(versionPath).resolve(fileName)
            PathType.MERGE -> dataDirectory.resolve("_merge").resolve(versionPath).resolve(fileName).toAbsolutePath()
            PathType.ADDRESS_POI_DB -> dataDirectory.resolve("_address_poi_db").resolve(versionPath).resolve(fileName)
            PathType.MAPSFORGE_RESULT -> dataDirectory.resolve("_result").resolve(versionPath).resolve(fileName)
            PathType.MAPSFORGE_GENERATE -> dataDirectory.resolve("_generate").resolve(versionPath).resolve(fileName).toAbsolutePath()
            PathType.MAPLIBRE_GENERATE -> dataDirectory.resolve("_gen_maplibre").resolve(versionPath).resolve(fileName)
            PathType.MAPLIBRE_RESULT -> dataDirectory.resolve("_result_maplibre").resolve(versionPath).resolve(fileName)

            // online maplibre tiles
            PathType.MAPLIBRE_ONLINE_OUTDOOR -> dataDirectory.resolve("_gen_online_outdoor").resolve(versionPath).resolve(fileName)
            PathType.MAPLIBRE_ONLINE_OPENMAPTILES -> dataDirectory.resolve("_gen_online_openmaptiles").resolve(versionPath).resolve(fileName)
        }
    }
}