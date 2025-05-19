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

    MBTILES_GENERATE,
    MBTILES_RESULT,
    POI_V2_DB,

    MBTILES_ONLINE_OUTDOOR,

}

class PathResolver(val map: ItemMap) {

    // Path to current working directory
    private val workingDirectory = Path.of(".")

    // Path to mapsforge directory
    private val mapsForgeDir = AppConfig.config.mapsForgeDir.toAbsolutePath()

    // Path to mbtiles directory
    private val mbtilesDir = AppConfig.config.mbtilesDir.toAbsolutePath()

    // Path to planet directory
    private val planetDir = AppConfig.config.planetDir.toAbsolutePath()



    // Version name (date e.q. 2021.01.01/europe/....) as syubfolder
    val versionPath: Path = Path.of(AppConfig.config.version, map.dir)

    fun getPath(type: PathType,fileName: String): Path {

        return when (type) {
            // completely static located in working directory
            PathType.POLYGON -> workingDirectory.resolve("polygons").resolve(map.dir).resolve(fileName)

            // located in data directory not generated with every version
            PathType.TRANSFORM -> mapsForgeDir.resolve("transform").resolve(map.dir).resolve(fileName)
            PathType.COASTLINE -> mapsForgeDir.resolve("coastlines").resolve("_pbf").resolve(map.dir).resolve(fileName)
            PathType.SHP -> mapsForgeDir.resolve("coastlines").resolve("_shp").resolve(map.dir).resolve(fileName)
            PathType.CONTOUR -> mapsForgeDir.resolve("contours").resolve(map.dir).resolve(fileName)
            // temporary data generated with every version located in data directory
            PathType.MERGE -> mapsForgeDir.resolve("_merge").resolve(versionPath).resolve(fileName).toAbsolutePath()
            PathType.ADDRESS_POI_DB -> mapsForgeDir.resolve("_address_poi_db").resolve(versionPath).resolve(fileName)
            PathType.MAPSFORGE_GENERATE -> mapsForgeDir.resolve("_generate").resolve(versionPath).resolve(fileName).toAbsolutePath()
            PathType.MAPSFORGE_RESULT -> mapsForgeDir.resolve("_result").resolve(versionPath).resolve(fileName)

            // temporary planet data generated with every version located in data directory
            PathType.TOURIST -> {
                if (isPlanet(map)) {
                    planetDir.resolve("_tourist").resolve(versionPath).resolve(fileName)
                }
                    else {
                    mapsForgeDir.resolve("_tourist").resolve(versionPath).resolve(fileName)
                }
            }
            PathType.EXTRACT -> {
                if (isPlanet(map)) {
                    planetDir.resolve("_extract").resolve(versionPath).resolve(fileName)
                } else {
                    mapsForgeDir.resolve("_extract").resolve(versionPath).resolve(fileName)
                }
            }

            PathType.MBTILES_GENERATE -> {
                if (isPlanet(map)) {
                    planetDir.resolve("mbtiles_lomaps_openmaptiles").resolve(versionPath).resolve(fileName)
                } else {
                    mbtilesDir.resolve("mbtiles").resolve(versionPath).resolve(fileName)
                }
            }
            PathType.MBTILES_RESULT -> mapsForgeDir.resolve("_result_mbtiles").resolve(versionPath).resolve(fileName)

            // online lomaps outdoor
            PathType.MBTILES_ONLINE_OUTDOOR -> planetDir.resolve("mbtiles_online_outdoor").resolve(versionPath).resolve(fileName)

            // POI V2
            PathType.POI_V2_DB -> mbtilesDir.resolve("_poi_v2_db").resolve(versionPath).resolve(fileName)
        }
    }

    private fun isPlanet(map: ItemMap): Boolean {
        return map.id?.let{it.equals(AppConfig.config.planetConfig.planetExtendedId)} ?: false
    }
}