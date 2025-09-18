package com.asamm.osmTools.mapConfig

import com.asamm.osmTools.config.AppConfig
import java.nio.file.Path

enum class PathType(val baseDir: String) {
    // static files
    POLYGON("polygons"),

    // data files
    TRANSFORM("transform"),
    COASTLINE("coastlines/_pbf"),
    SHP("coastlines/_shp"),
    CONTOUR("contours"),

    TOURIST("_tourist"),
    MERGE("_merge"),
    EXTRACT("_extract"),
    ADDRESS_DB("_address_db"),
    POI_V2_DB_MBTILES("_poi_v2_db_mbtiles"),
    POI_V2_DB_MAPSFORGE("_poi_v2_db_mapsforge"),
    MAPSFORGE_GENERATE("_generate"),
    MBTILES_GENERATE("_mbtiles"),
    MAPSFORGE_RESULT("_result"),
    ADDRESS_POI_DB_CLASSIC("_address_poi_db"),
    MBTILES_ONLINE_OUTDOOR("_mbtiles_online_outdoor");
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

    fun getPath(type: PathType, fileName: String): Path {

        return when (type) {
            // completely static located in working directory
            PathType.POLYGON -> getBaseDir(PathType.POLYGON).resolve(map.dir).resolve(fileName)

            // located in data directory not generated with every version
            PathType.TRANSFORM -> getBaseDir(PathType.TRANSFORM).resolve(map.dir).resolve(fileName)
            PathType.COASTLINE -> getBaseDir(PathType.COASTLINE).resolve(map.dir).resolve(fileName)
            PathType.SHP -> getBaseDir(PathType.SHP).resolve(map.dir).resolve(fileName)
            PathType.CONTOUR -> getBaseDir(PathType.CONTOUR).resolve(map.dir).resolve(fileName)

            // temporary planet data generated with every version located in data directory
            PathType.TOURIST -> getBaseDir(PathType.TOURIST).resolve(versionPath).resolve(fileName)
            PathType.EXTRACT -> getBaseDir(PathType.EXTRACT).resolve(versionPath).resolve(fileName)

            // temporary data generated with every version located in data directory
            PathType.MERGE -> getBaseDir(PathType.MERGE).resolve(versionPath).resolve(fileName).toAbsolutePath()
            PathType.ADDRESS_DB -> getBaseDir(PathType.ADDRESS_DB).resolve(versionPath).resolve(fileName)
            // POI V2
            PathType.POI_V2_DB_MBTILES -> getBaseDir(PathType.POI_V2_DB_MBTILES).resolve(versionPath).resolve(fileName)
            PathType.POI_V2_DB_MAPSFORGE -> getBaseDir(PathType.POI_V2_DB_MAPSFORGE).resolve(versionPath).resolve(fileName)

            // Offline map files
            PathType.MAPSFORGE_GENERATE -> getBaseDir(PathType.MAPSFORGE_GENERATE).resolve(versionPath).resolve(fileName).toAbsolutePath()
            PathType.MBTILES_GENERATE -> getBaseDir(PathType.MBTILES_GENERATE).resolve(versionPath).resolve(fileName)

            PathType.MAPSFORGE_RESULT -> getBaseDir(PathType.MAPSFORGE_RESULT).resolve(versionPath).resolve(fileName)

            // custom result for classic locus with old POI DB
            PathType.ADDRESS_POI_DB_CLASSIC -> getBaseDir(PathType.ADDRESS_POI_DB_CLASSIC).resolve(versionPath).resolve(fileName)

            // online lomaps outdoor
            PathType.MBTILES_ONLINE_OUTDOOR -> getBaseDir(PathType.MBTILES_ONLINE_OUTDOOR).resolve(versionPath).resolve(fileName)
        }
    }

    /**
     * Returns the absolute path to the base directory for the given PathType.
     * This does NOT include versionPath  just the root directory for the PathType.
     */
    fun getBaseDir(type: PathType): Path {
        return when (type) {
            PathType.POLYGON -> workingDirectory.resolve(type.baseDir)

            PathType.TRANSFORM,
            PathType.COASTLINE,
            PathType.SHP,
            PathType.CONTOUR,

            PathType.MERGE,
            PathType.ADDRESS_DB,
            PathType.MAPSFORGE_GENERATE,
            PathType.MAPSFORGE_RESULT,
            PathType.ADDRESS_POI_DB_CLASSIC -> mapsForgeDir.resolve(type.baseDir)

            PathType.TOURIST,
            PathType.EXTRACT -> if (map.isPlanet) planetDir.resolve(type.baseDir) else mapsForgeDir.resolve(type.baseDir)

            PathType.POI_V2_DB_MBTILES,
            PathType.POI_V2_DB_MAPSFORGE,
            PathType.MBTILES_GENERATE,
            PathType.MBTILES_ONLINE_OUTDOOR -> mbtilesDir.resolve(type.baseDir)

        }
    }
}