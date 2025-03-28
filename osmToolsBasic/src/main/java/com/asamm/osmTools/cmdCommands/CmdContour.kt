/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.asamm.osmTools.cmdCommands

import com.asamm.osmTools.config.AppConfig
import com.asamm.osmTools.generator.GenLoMaps
import com.asamm.osmTools.mapConfig.ContourUnit
import com.asamm.osmTools.mapConfig.ItemMap
import com.asamm.osmTools.utils.Logger
import com.asamm.osmTools.utils.Utils
import java.io.File
import java.nio.file.Path
import kotlin.io.path.absolute

/**
 *
 * @author volda
 */
class CmdContour(val map: ItemMap) : Cmd(ExternalApp.PYHGTMAP) {

    private val TAG: String = GenLoMaps::class.java.simpleName

    private val tempMeter: Path = AppConfig.config.temporaryDir.resolve(AppConfig.config.contourConfig.tempMetersFile)

    private val tempFeet: Path = AppConfig.config.temporaryDir.resolve(AppConfig.config.contourConfig.tempFeetFile)

    init {

        // check the id of map only "planet" is supported at this moment for generation of contour lines
        require(map.getId() == AppConfig.config.planetConfig.planetExtendedId) {
            "Only planet map (id=planet) is supported for generation of contour lines" }

        // check the location of polygon for contour lines
        require(AppConfig.config.contourConfig.polyCoverageMeter.toFile().exists()) {
            "Polygon file for generation contours meters: ${AppConfig.config.contourConfig.polyCoverageMeter.absolute()} does not exist" }

        require(AppConfig.config.contourConfig.polyCoverageFeet.toFile().exists()) {
            "Polygon file for generation contours feet: ${AppConfig.config.contourConfig.polyCoverageFeet.absolute()} does not exist" }
    }

    fun generate() {

        Utils.createParentDirs(tempMeter.toString())
        Utils.createParentDirs(tempFeet.toString())

        // generate feet contours only for areas where are feets
        if ( !tempFeet.toFile().exists()){
            // generate feet contours
            generateContours(ContourUnit.FEET, tempFeet)
            Logger.i(TAG, "Command: " + getCmdLine())
            execute()
            rename(tempFeet)
            //renumber(tempFeet, AppConfig.config.contourConfig.nodeIdFeet, AppConfig.config.contourConfig.wayIdFeet)
            reset()
        }

        // generate meters contours (contains also areas where are feet contours)
        if ( !tempMeter.toFile().exists()) {
            generateContours(ContourUnit.METER, tempMeter)
            Logger.i(TAG, "Command: " + getCmdLine())
            execute()
            rename(tempMeter)
            //renumber(tempMeterWithFeetAreas, AppConfig.config.contourConfig.nodeIdMeter, AppConfig.config.contourConfig.wayIdMeter)
            reset()
        }

        val cmdOsmium:CmdOsmium = CmdOsmium()

        // merge meters and feets contours to single contour world file
        Utils.createParentDirs(map.getPathContour())
        cmdOsmium.merge(mutableListOf(tempMeter, tempFeet), map.getPathContour())

        // delete tmp files
        Utils.deleteFileQuietly(tempMeter)
        Utils.deleteFileQuietly(tempFeet)
    }

    /**
     * Pyhgtmap generates contour lines with node and way only in integer range and it may collide with existing data.
     * This method renumbers nodes and ways to avoid collisions.
     */
    private fun renumber(generatedContourFile: Path, startNodeId: Long, startWayId: Long) {
        val cmdOsmium:CmdOsmium = CmdOsmium()
        // rename the temp meter fiel to add _renumbered before extension
        val renumberedFile = generatedContourFile.resolveSibling(generatedContourFile.fileName.toString() + ".renumbered.pbf")
        cmdOsmium.renumber(generatedContourFile, renumberedFile, startNodeId, startWayId)
        Utils.renameFileQuitly(renumberedFile, generatedContourFile, true)
    }

    private fun generateContours(unit: ContourUnit, prefix: Path) {

        // create commands based on specified unit
        when(unit) {
            ContourUnit.METER -> {
                addCommand("--polygon="+ AppConfig.config.contourConfig.polyCoverageMeter.toString())
                addCommand("--step=" + AppConfig.config.contourConfig.stepMeter.toString())
                addCommand("--line-cat=" + AppConfig.config.contourConfig.stepCategoryMeter)
                // higher ids are ommited on windows > use default pyhgtmap  values > generated contour are renumbered later
                addCommand("--start-node-id=" + AppConfig.config.contourConfig.nodeIdMeter.toString())
                addCommand("--start-way-id=" + AppConfig.config.contourConfig.wayIdMeter.toString())
                addCommand("--output-prefix=" + prefix)
            }
            ContourUnit.FEET -> {
                addCommand("--polygon="+  AppConfig.config.contourConfig.polyCoverageFeet.toString())
                addCommand("--step=" + AppConfig.config.contourConfig.stepFeet.toString())
                addCommand("--line-cat=" + AppConfig.config.contourConfig.stepCategoryFeet)
                addCommand("--start-node-id=" + AppConfig.config.contourConfig.nodeIdFeet.toString())
                addCommand("--start-way-id=" + AppConfig.config.contourConfig.wayIdFeet.toString())
                addCommand("--output-prefix=" + prefix)
                addCommand("--feet")
            }
            else -> {
                throw IllegalArgumentException("Unsupported contour unit: $unit")
            }
        }

        addCommand("--no-zero-contour")
        addCommand("--source=" + AppConfig.config.contourConfig.source) // addCommand("--source=view3,view1");
        addCommand("--write-timestamp")
        // add simplification of contour lines for SRTM1 data, some value between 0.00003 and 0.00008 seems reasonable
        addCommand("--simplifyContoursEpsilon=0.00007")
        addCommand("--max-nodes-per-tile=0")
        addCommand("--hgtdir=" + AppConfig.config.contourConfig.hgtDir)

        val cores = Runtime.getRuntime().availableProcessors()
        addCommand("-j " + cores.toString()) //number of paralel jobs (POSIX only)
        addCommand("--pbf")
    }

    fun rename(tempPath: Path) {
        val ccDir = tempPath.parent.toFile()
        var files: Array<File>?
        require( ccDir.exists()) {
            "Try to rename contour " + tempPath + ".But folder with contour data does not exist"
        }
        files = ccDir.listFiles()
        val requiredFileName = tempPath.fileName.toString()
        for (file in files){
            if (file.isFile && file.name.startsWith(requiredFileName)){
                file.renameTo(tempPath.toFile())
                break
            }
        }
    }
}
