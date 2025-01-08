package com.asamm.osmTools.cmdCommands

import com.asamm.locus.features.loMaps.LoMapsDbConst
import com.asamm.osmTools.Parameters
import com.asamm.osmTools.config.AppConfig
import com.asamm.osmTools.generatorDb.input.definition.WriterPoiDefinition
import com.asamm.osmTools.generatorDb.plugin.DataPluginLoader
import com.asamm.osmTools.mapConfig.ItemMap
import com.asamm.osmTools.utils.Consts
import com.asamm.osmTools.utils.Logger
import org.apache.commons.io.FileUtils
import java.io.File
import java.io.IOException
import java.util.*

/**
 * Created by menion on 28.7.14.
 */
class CmdLoMapsDbPlugin(val map: ItemMap) : Cmd(ExternalApp.OSMOSIS), CmdOsmosis {
    // set parameters
    private val mFileTempMap = AppConfig.config.temporaryDir.resolve("temp_map_simple.osm.pbf").toFile()

    /**
     * Definition where will be poiDb created
     */
    //mFileTempDb = new File(Consts.DIR_TMP, map.getName() + ".osm.db");
    val fileTempDb: File = map.pathAddressPoiDb.toFile()


    /**
     * Used for repeated run of the command when is needed to delete file create in previous run
     */
    private val mFileToCreate = fileTempDb


    @Throws(IOException::class, InterruptedException::class)
    fun execute(numRepeat: Int, deleteFile: Boolean): String? {
        var numRepeat = numRepeat
        try {
            return execute()
        } catch (e: Exception) {
            if (numRepeat > 0) {
                numRepeat--

                if (deleteFile) {
                    Logger.w(TAG, "Delete file from previous not success execution: " + mFileToCreate.absolutePath)
                    mFileToCreate.delete()
                }
                Logger.w(TAG, "Re-execute data plugin run: " + getCmdLine())
                execute(numRepeat, deleteFile)
            } else {
                throw e
            }
        }
        return null
    }

    /**
     * Delete tmop file where are store result of filtering for pois or address
     */
    fun deleteTmpFile() {
        mFileTempMap.delete()
    }

    @Throws(IOException::class)
    fun addTaskSimplifyForPoi(definition: WriterPoiDefinition) {
        //FileUtils.deleteQuietly(mFilePoiDb);

        addReadSource(map.pathSource)
        addCommand("--tf")
        addCommand("reject-relations")
        addCommand("--tf")
        addCommand("accept-nodes")
        addListOfTags(definition, LoMapsDbConst.EntityType.POIS)
        addCommand("--tf")
        addCommand("reject-ways")
        addCommand("outPipe.0=Nodes")

        // add second task
        addReadSource(map.pathSource)
        addCommand("--tf")
        addCommand("reject-relations")
        addCommand("--tf")
        addCommand("accept-ways")
        addListOfTags(definition, LoMapsDbConst.EntityType.WAYS)
        addCommand("--used-node")
        //        addCommand("idTrackerType=Dynamic");
        addCommand("outPipe.0=Ways")

        // add merge task
        addCommand("--merge")
        addCommand("inPipe.0=Nodes")
        addCommand("inPipe.1=Ways")

        // add export path
        addWritePbf(mFileTempMap.absolutePath, true)
    }

    @Throws(IOException::class)
    fun addTaskSimplifyForAddress() {
        addReadSource(map.pathSource)
        addCommand("--tf")
        addCommand("reject-relations")
        addCommand("--tf")
        addCommand("reject-ways")
        addCommand("--tf")
        addCommand("accept-nodes")
        addCommand("place=*")
        addCommand("addr:housenumber=*")
        addCommand("addr:housename=*")
        addCommand("addr:street=*")
        addCommand("addr:street2=*")
        addCommand("address:house=*")

        addCommand("outPipe.0=Nodes")

        // add second task
        addReadSource(map.pathSource)
        addCommand("--tf")
        addCommand("reject-relations")
        addCommand("--tf")
        addCommand("accept-ways")
        addCommand("highway=*")
        addCommand("boundary=*")
        addCommand("place=*")
        addCommand("*=street")
        addCommand("*=associatedStreet")
        addCommand("addr:housenumber=*")
        addCommand("addr:housename=*")
        addCommand("addr:street=*")
        addCommand("addr:street2=*")
        addCommand("address:house=*")
        addCommand("addr:interpolation=*")
        addCommand("address:type=*")


        //        addCommand("landuse=residential");
//        addCommand("building=*");
        addCommand("--used-node")

        // addCommand("idTrackerType=Dynamic");
        addCommand("outPipe.0=Ways")

        // add third task
        addReadSource(map.pathSource)
        addCommand("--tf")
        addCommand("accept-relations")
        addCommand("highway=*")
        addCommand("boundary=*")
        addCommand("place=*")
        addCommand("type=street")
        addCommand("type=associatedStreet")
        addCommand("addr:housenumber=*")
        addCommand("addr:housename=*")
        addCommand("addr:street=*")
        addCommand("addr:street2=*")
        addCommand("address:house=*")
        addCommand("addr:interpolation=*")
        addCommand("address:type=*")

        //        addCommand("landuse=residential");
//        addCommand("building=*");
        addCommand("--used-way")
        addCommand("--used-node")
        //        addCommand("idTrackerType=Dynamic");
        addCommand("outPipe.0=Relations")

        // add merge task
        addCommand("--merge")
        addCommand("inPipe.0=Nodes")
        addCommand("inPipe.1=Ways")
        addCommand("outPipe.0=NodesWays")
        addCommand("--merge")
        addCommand("inPipe.0=Relations")
        addCommand("inPipe.1=NodesWays")

        // add export path
        addWritePbf(mFileTempMap.absolutePath, true)
    }

    fun addGeneratorPoiDb() {
        FileUtils.deleteQuietly(fileTempDb)

        addReadPbf(mFileTempMap.absolutePath)
        addCommand("--" + DataPluginLoader.PLUGIN_LOMAPS_DB)
        addCommand("-type=poi")
        addCommand("-fileDb=" + fileTempDb)
        addCommand("-fileConfig=" + Parameters.getConfigApDbPath())
    }

    /**
     * Prepare cmd line to run osmosis for generation post address database
     */
    fun addGeneratorAddress() {
        //addReadPbf(getMap().getPathSource());

        addReadPbf(mFileTempMap.absolutePath)

        addCommand("--" + DataPluginLoader.PLUGIN_LOMAPS_DB)
        addCommand("-type=address")

        val size = (mFileTempMap.length() / 1024L / 1024L).toInt()
        if (size <= 250) {
            addCommand("-dataContainerType=ram")
        } else {
            addCommand("-dataContainerType=hdd")
        }

        // add map id if is not defined then use map name as id
        if (map.countryName != null) {
            var mapId = map.id
            if (mapId == null || mapId.length == 0) {
                mapId = map.name
            }
            addCommand("-mapId=$mapId")
        }
        //addCommand("-countryName=" + getMap().getCountryName());
        addCommand("-fileDb=" + fileTempDb)
        addCommand("-fileConfig=" + Parameters.getConfigAddressPath())
        addCommand("-fileDataGeom=" + map.pathJsonPolygon)
        addCommand("-fileCountryGeom=" + map.pathCountryBoundaryGeoJson)

        //        try {
//            addWriteXml("./exportplugin.osm", true);
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
    }

    private fun addListOfTags(definition: WriterPoiDefinition, type: LoMapsDbConst.EntityType) {
        // prepare list of tags
        val nodes = definition.rootSubContainers
        val nodesPrep = Hashtable<String, String>()
        for (dbDef in nodes) {
            // check type
            if (!dbDef.isValidType(type)) {
                continue
            }

            // add to data
            val value = nodesPrep[dbDef.key]
            if (value == null) {
                nodesPrep[dbDef.key] = dbDef.value
            } else {
                nodesPrep[dbDef.key] = value + "," + dbDef.value
            }
        }

        // finally add all key/value pairs
        val nodesKeys = nodesPrep.keys()
        while (nodesKeys.hasMoreElements()) {
            val key = nodesKeys.nextElement()
            val value = nodesPrep[key]
            addCommand("$key=$value")
        }
    }


    companion object {
        private val TAG: String = CmdLoMapsDbPlugin::class.java.simpleName
    }
}
