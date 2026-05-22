package com.asamm.osmTools.cmdCommands

import com.asamm.osmTools.config.AppConfig
import com.asamm.osmTools.mapConfig.ContourUnit
import com.asamm.osmTools.mapConfig.ItemMap
import com.asamm.osmTools.utils.Utils
import java.nio.file.Path
import kotlin.io.path.absolute

class CmdContour(val map: ItemMap) : Cmd(ExternalApp.PYHGTMAP) {

    private val TAG: String = CmdContour::class.java.simpleName

    private val tempMeter: Path = AppConfig.config.temporaryDir.resolve(AppConfig.config.contourConfig.tempMetersFile)
    private val tempFeet: Path = AppConfig.config.temporaryDir.resolve(AppConfig.config.contourConfig.tempFeetFile)

    init {
        require(map.isPlanet) {
            "Only planet map (id=planet) is supported for generation of contour lines"
        }
        require(AppConfig.config.contourConfig.polyCoverageMeter.toFile().exists()) {
            "Polygon file for meter contours: ${AppConfig.config.contourConfig.polyCoverageMeter.absolute()} does not exist"
        }
        require(AppConfig.config.contourConfig.polyCoverageFeet.toFile().exists()) {
            "Polygon file for feet contours: ${AppConfig.config.contourConfig.polyCoverageFeet.absolute()} does not exist"
        }
    }

    fun generate() {
        Utils.createParentDirs(tempMeter.toString())
        Utils.createParentDirs(tempFeet.toString())

        if (!tempFeet.toFile().exists()) {
            buildContoursCmd(ContourUnit.FEET, tempFeet).execute()
            rename(tempFeet)
        }

        if (!tempMeter.toFile().exists()) {
            buildContoursCmd(ContourUnit.METER, tempMeter).execute()
            rename(tempMeter)
        }

        Utils.createParentDirs(map.pathContour)
        CmdOsmium().merge(mutableListOf(tempMeter, tempFeet), map.pathContour)

        Utils.deleteFileQuietly(tempMeter)
        Utils.deleteFileQuietly(tempFeet)
    }

    private fun buildContoursCmd(unit: ContourUnit, outputPrefix: Path): ProcessCommand {
        val cores = Runtime.getRuntime().availableProcessors()
        return builder()
            .apply {
                when (unit) {
                    ContourUnit.METER -> {
                        add("--polygon=${AppConfig.config.contourConfig.polyCoverageMeter}")
                        add("--step=${AppConfig.config.contourConfig.stepMeter}")
                        add("--line-cat=${AppConfig.config.contourConfig.stepCategoryMeter}")
                        add("--start-node-id=${AppConfig.config.contourConfig.nodeIdMeter}")
                        add("--start-way-id=${AppConfig.config.contourConfig.wayIdMeter}")
                        add("--output-prefix=$outputPrefix")
                    }

                    ContourUnit.FEET -> {
                        add("--polygon=${AppConfig.config.contourConfig.polyCoverageFeet}")
                        add("--step=${AppConfig.config.contourConfig.stepFeet}")
                        add("--line-cat=${AppConfig.config.contourConfig.stepCategoryFeet}")
                        add("--start-node-id=${AppConfig.config.contourConfig.nodeIdFeet}")
                        add("--start-way-id=${AppConfig.config.contourConfig.wayIdFeet}")
                        add("--output-prefix=$outputPrefix")
                        add("--feet")
                    }

                    else -> throw IllegalArgumentException("Unsupported contour unit: $unit")
                }
            }
            .add("--no-zero-contour")
            .add("--source=${AppConfig.config.contourConfig.source}")
            .add("--write-timestamp")
            .add("--simplifyContoursEpsilon=0.00007")
            .add("--max-nodes-per-tile=0")
            .add("--hgtdir=${AppConfig.config.contourConfig.hgtDir}")
            .add("-j $cores")
            .add("--pbf")
            .build()
    }

    fun rename(tempPath: Path) {
        val ccDir = tempPath.parent.toFile()
        require(ccDir.exists()) {
            "Trying to rename contour $tempPath but parent directory does not exist"
        }
        val requiredFileName = tempPath.fileName.toString()
        val files = ccDir.listFiles() ?: return
        for (file in files) {
            if (file.isFile && file.name.startsWith(requiredFileName)) {
                file.renameTo(tempPath.toFile())
                break
            }
        }
    }
}
