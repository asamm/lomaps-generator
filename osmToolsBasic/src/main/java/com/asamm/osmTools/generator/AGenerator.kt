package com.asamm.osmTools.generator

import com.asamm.osmTools.Main
import com.asamm.osmTools.cmdCommands.CmdCountryBorders
import com.asamm.osmTools.cmdCommands.CmdExtractOsmium
import com.asamm.osmTools.config.Action
import com.asamm.osmTools.config.AppConfig
import com.asamm.osmTools.generatorDb.plugin.ConfigurationCountry
import com.asamm.osmTools.mapConfig.ItemMap
import com.asamm.osmTools.mapConfig.ItemMapPack
import com.asamm.osmTools.mapConfig.MapSource
import com.asamm.osmTools.utils.Logger
import com.asamm.osmTools.utils.TimeWatch
import org.apache.commons.io.IOUtils
import org.kxml2.io.KXmlParser
import org.xmlpull.v1.XmlPullParserException
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.*

/**
 * Created by voldapet on 2016-09-16 .
 */
abstract class AGenerator {

     // ACTION EXTRACT
    @Throws(IOException::class, InterruptedException::class)
    fun actionExtract(mp: ItemMapPack, ms: MapSource) {
        Logger.i(TAG, "actionExtract(" + mp.name + ", " + ms.hasData() + ")")
        // create hashTable where identificator is sourceId of map and values is an list of
        // all map with same sourceId
        val mapTableBySourceId: MutableMap<String, MutableList<ItemMap>> = Hashtable()

        // fill hash table with values
        run {
            var i = 0
            val m = mp.mapsCount
            while (i < m) {
                val actualMap = mp.getMap(i)

                if (actualMap.hasAction(Action.EXTRACT)) {
                    var itemsToExtract = mapTableBySourceId[actualMap.sourceId]
                    if (itemsToExtract == null) {
                        itemsToExtract = ArrayList()
                        mapTableBySourceId[actualMap.sourceId] = itemsToExtract
                    }

                    // test if file for extract exist. If yes don't add it into ar
                    val writeFileLocation = actualMap.pathSource
                    if (!writeFileLocation.toFile().exists()) {
                        Logger.i(
                            TAG,
                            "Add map for extraction: $writeFileLocation"
                        )
                        itemsToExtract.add(actualMap)
                    } else {
                        Logger.i(
                            TAG,
                            "Map for extraction: $writeFileLocation already exist. No action performed"
                        )
                    }
                }
                i++
            }
        }

        // get valid sources and sort them by availability
        val keys: Iterator<String> = mapTableBySourceId.keys.iterator()
        val sources: MutableList<String> = ArrayList()
        while (keys.hasNext()) {
            // get content and check if we need to process any maps
            val key = keys.next()
            val ar: List<ItemMap> = mapTableBySourceId[key]!!
            if (ar.isEmpty()) {
                Logger.i(
                    TAG,
                    "Skip for source: $key"
                )
                continue
            }

            // add to list
            sources.add(key)
        }

        // sort by availability
        sources.sortWith(Comparator<String> { source1: String, source2: String ->
            // get required parameters
            val ex1 = ms.getMapById(source1).pathSource.toFile().exists()
            val ex2 = ms.getMapById(source2).pathSource.toFile().exists()

            // compare data

            when {
                ex1 -> -1
                ex2 -> 1
                else -> 0
            }
        })

        // write to log and start stop watch
        val time = TimeWatch()

        // finally handle data
        run {
            var i = 0
            val m = sources.size
            while (i < m) {
                val sourceId = sources[i]

                Logger.i(
                    TAG,
                    "Extracting maps from source: $sourceId"
                )
                Main.mySimpleLog.print("\nExtract Maps from: $sourceId ...")

                val ar: List<ItemMap> = mapTableBySourceId[sourceId]!!

                //val sourceSize = ms.getMapById(sourceId).pathSource.toFile().length()
                //val exportSize: Long = 0

                var ceo = CmdExtractOsmium(ms, sourceId)
                var completeRelations = false
                var j = 0
                val size = ar.size
                while (j < size) {
                    val map = ar[j]
                    Logger.i(TAG, "Add map for extraction: " + map.name)
                    ceo.addExtractMap(map)

                    if (map.hasAction(Action.GENERATE_MAPSFORGE)) {
                        completeRelations = true
                    }

                    // export only 7 maps in one step due to memory limitation
                    if (j != 0 && j % 7 == 0) {
                        ceo.createCmd(completeRelations)

                        Logger.i(TAG, ceo.getCmdLine())
                        ceo.execute()

                        ceo = CmdExtractOsmium(ms, sourceId)
                    }
                    j++
                }
                if (ceo.hasMapForExtraction()) {
                    // process the rest of map
                    ceo.createCmd(completeRelations)
                    Logger.i(TAG, ceo.getCmdLine())
                    ceo.execute()
                }

                Logger.i(TAG, "\t\t\tdone " + time.elapsedTimeSec + " sec")
                i++
            }
        }

        // execute extract also on sub-packs
        var i = 0
        val m = mp.mapPackCount
        while (i < m) {
            actionExtract(mp.getMapPack(i), ms)
            i++
        }
    }

    // COUNTRY BOUNDARY
    /**
     * Create country boundaries for map items in mappack
     *
     * @param mp map pack to create countries for it's items
     */
    @Throws(IOException::class, InterruptedException::class)
    protected fun actionCountryBorder(
        mp: ItemMapPack, mapSource: MapSource, storageType: ConfigurationCountry.StorageType
    ) {
        Logger.i(TAG, "actionCountryBorder, source: " + mp.name)

        // map where key is mappack id and value is list of map to generate boundaries from source
        val mapTableBySourceId = prepareMapsForSource(mp, storageType)


        // for every source run generation of country borders
        for ((key, mapToCreate) in mapTableBySourceId) {
            val sourceMap = mapSource.getMapById(key)

            if (mapToCreate.size == 0) {
                continue
            }

            // filter only boundary values from source
            val cmdCBfilter = CmdCountryBorders(sourceMap, storageType)
            if (!cmdCBfilter.filteredTempMap.exists()) {
                cmdCBfilter.addTaskFilter()
                Logger.i(TAG, "Filter for generation country bound, command: " + cmdCBfilter.getCmdLine())
                cmdCBfilter.execute()
            }

            val cmdBorders = CmdCountryBorders(sourceMap, storageType)
            cmdBorders.addGeneratorCountryBoundary()
            cmdBorders.addCountries(mapToCreate)
            Logger.i(TAG, "Generate country boundary, command: " + cmdBorders.getCmdLine())
            cmdBorders.execute()

            // delete tmp file
            //cmdBorders.deleteTmpFile();
        }
    }

    /**
     * For every source prepare list of maps that can be generated from source
     *
     * @param mp source mappack that can be used as source for generated counties boundaries
     * @return
     */
    protected fun prepareMapsForSource(
        mp: ItemMapPack, storageType: ConfigurationCountry.StorageType
    ): Map<String, MutableList<ItemMap>> {
        // map where key is mappack id and value is list of map to generate boundaries from source

        val mapTableBySourceId: MutableMap<String, MutableList<ItemMap>> = LinkedHashMap()

        // fill hash table with values in first step proccess map in mappack
        for (map in mp.maps) {
            if (!map.hasAction(Action.GENERATE_MAPSFORGE)
                && !map.hasAction(Action.ADDRESS_POI_DB)
                && !map.hasAction(Action.STORE_GEO_DB)
            ) {
                //Logger.i(TAG, "prepareCountriesForSource, skip map: " + map.getNameReadable());
                continue
            }

            if (storageType == ConfigurationCountry.StorageType.GEOJSON && map.pathCountryBoundaryGeoJson.toFile()
                    .exists()
            ) {
                // boundary geom for this map exists > skip it
                Logger.i(TAG, "Country boundaries exits for map: " + map.nameReadable)
                continue
            }

            // get the source item for map
            val sourceId = map.sourceId
            // get the list of countries that will be created from source
            var mapsFromSource = mapTableBySourceId[sourceId]
            if (mapsFromSource == null) {
                mapsFromSource = ArrayList()
            }
            mapsFromSource.add(map)
            mapTableBySourceId[sourceId] = mapsFromSource
        }

        // now get submaps for mappack and their mappack...
        for (mapPack in mp.mapPacks) {
            // for every map pack get country boundaries that will be generated

            val sourcesSubMap = prepareMapsForSource(mapPack, storageType)

            // combine result with parent source map
            for ((key, value) in sourcesSubMap) {
                var subMaps = mapTableBySourceId[key]
                if (subMaps == null) {
                    subMaps = ArrayList()
                }
                subMaps.addAll(value)
                mapTableBySourceId[key] = subMaps
            }
        }

        return mapTableBySourceId
    }

    companion object {
        private val TAG: String = AGenerator::class.java.simpleName
    }
}
