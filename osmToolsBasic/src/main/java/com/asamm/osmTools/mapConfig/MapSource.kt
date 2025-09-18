/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.asamm.osmTools.mapConfig

import com.asamm.osmTools.utils.Logger

/**
 *
 * @author volda
 */
class MapSource {
    // define xml file 
    val mapPackList: MutableList<ItemMapPack> = ArrayList()

    fun hasData(): Boolean {
        return mapPackList.size > 0
    }

    fun addMapPack(mapPack: ItemMapPack) {
        mapPack.validate()
        mapPackList.add(mapPack)
    }

    val mapPacksIterator: Iterator<ItemMapPack>
        get() = mapPackList.iterator()


    val getAllMaps: List<ItemMap>
        get() = mapPackList.flatMap { it.getAllMaps() }

    fun getMapPackByDir(dirName: String): ItemMapPack? {
        var i = 0
        val m = mapPackList.size
        while (i < m) {
            val mp = mapPackList[i]
            if (mp.dirGen.contains(dirName) || mp.dir.contains(dirName)) {
                return mp
            }

            val mpSub = mp.getMapPackByDir(dirName)
            if (mpSub != null) {
                return mpSub
            }
            i++
        }
        return null
    }

    /**
     * Search in definitions for map specified by it's ID
     * @param mapId ID of map we search for
     * @return found item
     */
    public fun getMapById(mapId: String): ItemMap {
        // search for map
        var i = 0
        val m = mapPackList.size
        while (i < m) {
            val mp = mapPackList[i]
            val map = mp.getMapById(mapId)
            if (map != null) {
                return map
            }
            i++
        }

        Logger.w(TAG, "Can't find map with id: $mapId Check the config XML file.")
        throw IllegalStateException("Map '$mapId', not found" )
    }

    companion object {
        private val TAG: String = MapSource::class.java.simpleName
    }
}



