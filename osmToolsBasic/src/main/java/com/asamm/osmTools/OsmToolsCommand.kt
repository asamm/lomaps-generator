package com.asamm.osmTools

import com.asamm.osmTools.LoMapsCommand.Companion.TAG
import com.asamm.osmTools.cleanup.OldMapsCleaner
import com.asamm.osmTools.generator.lomaps.MapsforgeTilerRunner
import com.asamm.osmTools.config.Action
import com.asamm.osmTools.config.AppConfig
import com.asamm.osmTools.config.ConfigUtils
import com.asamm.osmTools.config.LoMapsMode
import com.asamm.osmTools.elevation.ElevationPlanetBuilder
import com.asamm.osmTools.overviewMap.OverviewMapBuilder
import com.asamm.osmTools.generator.lomaps.GenLoMaps
import com.asamm.osmTools.generator.GenStoreRegionDB
import com.asamm.osmTools.generator.PlanetUpdater
import com.asamm.osmTools.utils.Logger
import com.asamm.slack.SlackUtils
import com.asamm.store.LocusStoreEnv
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.clikt.parameters.types.file
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.*


class OsmToolsCommand : CliktCommand(
    name = "OsmToolsBasic",
    help = "Tool for processing OSM data and generating maps"
) {

    init {
        versionOption(loadVersionFromProperties())
    }

    // verbose mode
    val verbose by option("-d", "--debug", help = "Prints more detailed information").flag()

    // replace files if exist
    val overwrite by option("-ow", "--overwrite", help = "Overwrite output file if exists").flag()

    // set Locus Store environment (where to upload maps)
    val locusStoreEnv by option(
        "-e", "--ls_environment", help = "Set Locus Store environment - where to upload maps.  " +
                "Possible values: ${LocusStoreEnv.entries.joinToString(", ")}"
    )
        .enum<LocusStoreEnv>()
        .default(LocusStoreEnv.PROD)


    override fun run() {

        // Load the app configuration (initialize it once)
        AppConfig.loadConfig()

        // Configure file logging (archives previous latest.log, opens fresh latest.log)
        AppConfig.config.loggerConfig.verbose = verbose
        Logger.configure(
            AppConfig.config.loggerConfig.logDir,
            AppConfig.config.loggerConfig.maxFiles,
            AppConfig.config.loggerConfig.verbose,
        )

        // Set Locus Store environment
        AppConfig.config.locusStoreEnv = locusStoreEnv

        // Set overwrite mode
        AppConfig.config.overwrite = overwrite
    }

    /**
     * Load version from properties file
     */
    private fun loadVersionFromProperties(): String {
        return try {
            val props = Properties()
            javaClass.classLoader.getResourceAsStream("version.properties")?.use { props.load(it) }
            props.getProperty("version") ?: "unknown"
        } catch (e: Exception) {
            "unknown error" + e.message
        }
    }

}

// UPDATE PLANET SUBCOMMAND

class UpdatePlanetCommand : CliktCommand(
    name = "update_planet",
    help = "Subcommand to update OSM planet file"
) {

    override fun run() {
        val planetUpdater = PlanetUpdater()
        planetUpdater.update()
    }
}


// DELETE OLD MAPS SUBCOMMAND

class CleanOldGenerationCommand : CliktCommand(
    name = "clean_old",
    help = "Subcommand to clean old maps and support files from data folders"
) {

    // path to a configuration file where are defined maps for generation
    val configFile: File by option("-cf", "--config_file", help = "Path to configuration file").file(mustExist = true)
        .defaultLazy {
            val defaultConfigFile = File("config.xml")
            require(defaultConfigFile.exists()) {
                "Default config file '$defaultConfigFile' doesn't exist. Please specify path to config file"
            }
            defaultConfigFile
        }


    override fun run() {

        // Set path to the configuration file
        AppConfig.config.mapsforgeConfig.mapConfigXml = configFile.toPath()

        val oldMapsCleaner = OldMapsCleaner()
        oldMapsCleaner.purgePreviousMapGeneration()

        Logger.i(TAG, "== Purge previous LoMaps generation finished ==")
    }
}

// GENERATE TERRAIN RGB SUBCOMMAND

class TerrainRgbCommand : CliktCommand(
    name = "terrain_rgb",
    help = "Prepare RGB terrain tiles with planet coverage (from Mapternhorn and GEBCO data)"
) {

    val terrain: Boolean by option(
        "-t",
        "--terrain",
        help = "Download Mapternhorn data and generate terrain RGB tiles for land elevation (without ocean floor)"
    ).flag()

    val bathymetry: Boolean by option(
        "-b",
        "--bathymetry",
        help = "Download GEBCO data and generate bathymetry (ocean floor) terrain-RGB tiles"
    ).flag()

    val uploadToS3: Boolean by option(
        "-u",
        "--upload_to_s3",
        help = "Upload resulting MBTiles/PMTiles to S3 after preparation"
    ).flag()

    val generateHgt: Boolean by option(
        "--generate_hgt",
        help = "Convert prepared terrain RGB elevation data to HGT files (only for land)"
    ).flag()

    override fun run() {
        val elevationPlanetBuilder = ElevationPlanetBuilder()

        // Step Download, extract, simplify terrain RGB planet
        if (terrain) {
            elevationPlanetBuilder.prepareTerrainRgbMapternhorn()
        }

        // Step 4 (optional): Convert terrain RGB tiles to HGT elevation files
        if (generateHgt) {
            elevationPlanetBuilder.generateHgtForLand()
        }

        // Optional: Download GEBCO data and generate bathymetry terrain-RGB tiles
        if (bathymetry) {
            elevationPlanetBuilder.prepareBathymetry()
        }

        // Upload terrain RGB planet PMTiles to S3
        if (uploadToS3) {
            if (terrain) {
                elevationPlanetBuilder.uploadTerrainRgbToS3()
            }
            if (bathymetry) {
                elevationPlanetBuilder.uploadBathymetryToS3()
            }
            if (!terrain && !bathymetry) {
                Logger.w(TAG, "Upload requested but neither --terrain nor --bathymetry was specified, nothing to upload.")
            }
        }
    }

    companion object {
        private const val TAG = "TerrainRgbCommand"
    }
}


// Overview SUBCOMMAND

class OverviewMapCommand : CliktCommand(
    name = "overview_map",
    help = "Download non OSM data (Natural Earth, Shadedrelief data and convert to OSM PBF " +
            "for simplified global map (zoom 0-9)"
) {
    override fun run() {
        OverviewMapBuilder().buildOverviewOsmPbf()

        MapsforgeTilerRunner.generateOverviewMap()
    }
}


// LOMAPS SUBCOMMAND

class LoMapsCommand : CliktCommand(
    name = "lomaps",
    help = "Subcommand to generate maps for Locus Store"
) {
    companion object {
        val TAG: String = LoMapsCommand::class.java.simpleName
    }

    // version of the map
    val version: String by option(
        "-v",
        "--version",
        help = "Name of map version. This is used for versioning of map in Locus Store"
    ).default("")
        .validate {
            require(validateDate(it)) {
                "Invalid date format. Use yyyy.MM.dd"
            }
        }

    // path to a configuration file where are defined maps for generation
    val configFile: File by option("-cf", "--config_file", help = "Path to configuration file").file(mustExist = true)
        .defaultLazy {
            val defaultConfigFile = File("config.xml")
            require(defaultConfigFile.exists()) {
                "Default config file '$defaultConfigFile' doesn't exist. Please specify path to config file"
            }
            defaultConfigFile
        }

    val mode: LoMapsMode by option(
        "-m", "--mode",
        help = "Generation mode. '${LoMapsMode.OFFLINE.label}' generates offline vector maps for Locus Store. " +
               "'${LoMapsMode.ONLINE.label}' generates online planet-level tile maps."
    )
        .convert { LoMapsMode.fromLabel(it) }
        .required()

    val release: Boolean by option(
        "-r", "--release",
        help = "Release the generated output. For '${LoMapsMode.OFFLINE.label}': uploads maps to Locus Store. " +
               "For '${LoMapsMode.ONLINE.label}': publishes PMTiles to S3 and the tile server. Default: false."
    ).flag(default = false)

    val hgtDir: File by option("-hgt", "--hgt_dir", help = "Path to elevation hgt file").file(mustExist = true)
        .defaultLazy {
            val defaultConfigFile = File("hgt")
            require(defaultConfigFile.exists()) {
                "Default folder with elevation data: '$defaultConfigFile' doesn't exist. Please specify path to config file"
            }
            defaultConfigFile
        }

    val mapsforgeDir: File by option(
        "-mf", "--mapsforge_dir",
        help = "Path to folder where data for generation of MapsForge maps are stored"
    ).file()
        .defaultLazy { AppConfig.config.mapsForgeDir.toFile() }

    val mbtilesDir: File by option(
        "-mb", "--mbtiles_dir",
        help = "Path to folder where data for generation of Mbtiles maps are stored"
    ).file()
        .defaultLazy { AppConfig.config.mbtilesDir.toFile() }

    val planetDir: File by option(
        "-pd", "--planet_dir",
        help = "Path to folder where data for generation of planet data are stored"
    ).file()
        .defaultLazy { AppConfig.config.planetDir.toFile() }

    // path to a locus store uploader — required only when --release is set with mode=offline
    val storeUploaderFile: File? by option(
        "-su",
        "--store_uploader",
        help = "Path to java .jar file script for LoMaps store uploader. " +
               "Required when --release is set with mode '${LoMapsMode.OFFLINE.label}'."
    )
        .file(mustExist = true)

    override fun run() {

        // Resolve the full action list for the selected mode (base actions + injected dependencies)
        val extraActions = if (release) listOf(Action.UPLOAD) else emptyList()
        val actions = ConfigUtils.resolveActions(mode, extraActions)

        // Set actions to the configuration
        AppConfig.config.actions = actions
        AppConfig.config.version = version
        AppConfig.config.mapsForgeDir = mapsforgeDir.toPath()
        AppConfig.config.mbtilesDir = mbtilesDir.toPath()
        AppConfig.config.planetDir = planetDir.toPath()


        // Set path to the configuration file
        AppConfig.config.mapsforgeConfig.mapConfigXml = configFile.toPath()

        // Store uploader is required when releasing offline maps
        if (release && mode == LoMapsMode.OFFLINE) {
            require(storeUploaderFile != null) {
                "Option --store_uploader is required when --release is set with mode '${LoMapsMode.OFFLINE.label}'"
            }
        }
        storeUploaderFile?.let { AppConfig.config.storeUploaderPath = it.toPath() }

        // Set path to the hgt directory
        AppConfig.config.contourConfig.hgtDir = hgtDir.toPath()

        Logger.i(TAG, "Configuration : ${AppConfig.config.toYaml()}")

        val genLoMaps = GenLoMaps()
        genLoMaps.process()

        SlackUtils.sendMessage("[OsmTools] LoMaps generation finished successfully for version ${AppConfig.config.version}")
        Logger.i(TAG, "== Map generation finished ==")
    }

    /**
     * Validate date format
     */
    private fun validateDate(dateStr: String): Boolean {
        try {
            val formatter = DateTimeFormatter.ofPattern("yyyy.MM.dd")
            LocalDate.parse(dateStr, formatter)
            return true
        } catch (_: DateTimeParseException) {
            Logger.e("Command", "Invalid date format. Use yyyy.MM.dd")
            return false
        }
    }
}

// GENERATE COUNTRY BORDERS

class StoreGeoCommand : CliktCommand(
    name = "storegeo",
    help = "Subcommand to generate map borders for regions in Locus Store"
) {

    // path to a configuration file where are defined maps for generation
    val configFile: File by option(
        "-cf",
        "--config_file",
        help = "Path to configuration XML for generation of country boundaries"
    ).file(mustExist = true)
        .defaultLazy {
            val defaultConfigFile = File("config/config_store_geodb.xml")
            require(defaultConfigFile.exists()) {
                "Default config file '$defaultConfigFile' doesn't exist. Please specify path to config file"
            }
            defaultConfigFile
        }


    override fun run() {
        val genStoreGeo = GenStoreRegionDB(configFile.toPath())
        genStoreGeo.process()
    }
}

// MAIN FUNCTION

fun main(args: Array<String>) {
    // Global uncaught exception handler
    Thread.setDefaultUncaughtExceptionHandler { thread, e ->
        try {
            Logger.e("OsmTools", "Uncaught exception in thread ${thread.name}", e)
            SlackUtils.sendMessage("[OsmTools] LoMaps generation process ends abnormally with exception: ${e.message}")
        } catch (_: Exception) {
        }
        // Rethrow the exception to let the program terminate
        throw e
    }

    // start the command line interface and catch any exception to send it to Slack
    try {

        // CMD entry point
        OsmToolsCommand()
            .subcommands(
                LoMapsCommand(),
                UpdatePlanetCommand(),
                CleanOldGenerationCommand(),
                StoreGeoCommand(),
                TerrainRgbCommand(),
                OverviewMapCommand()
            )
            .main(args)

    } catch (e: Exception) {
        try {
            Logger.e("OsmTools", "Exception occurred while running OsmTools", e)
            SlackUtils.sendMessage("[OsmTools] LoMaps generation process ends abnormally with exception: ${e.message}")
        } catch (_: Exception) {
        }
    }
}