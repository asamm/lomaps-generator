/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.asamm.osmTools.mapConfig

/**
 *
 * @author volda
 */
class ItemMapPack(mpParent: ItemMapPack?) : AItemMap(mpParent) {

    // list of all maps in this mapPack
    var maps: MutableList<ItemMap> = ArrayList()

    // list of child mapPacks
    var mMapPacks: MutableList<ItemMapPack> = ArrayList()

    /**
     * Get all maps in this mapPack and all sub mapPacks
     */
    fun getAllMaps(): List<ItemMap> {
        val allMaps = mutableListOf<ItemMap>()
        allMaps.addAll(maps)

        for (mp in mMapPacks) {
            allMaps.addAll(mp.getAllMaps())
        }

        return allMaps
    }

    fun getMapById(mapId: String): ItemMap? {
        for (actualMap in maps) {
            if (actualMap.id != null && actualMap.id == mapId) {
                return actualMap
            }
        }
        for (mp in mMapPacks) {
            val map = mp.getMapById(mapId)
            if (map != null) {
                return map
            }
        }
        return null
    }

    /**
     * Search for mapPack by directory name
     * @param dirName directory name to search for
     * @return found mapPack or null if not found
     */
    fun getMapPackByDir(dirName: String): ItemMapPack? {
        for (mp in mMapPacks) {
            if (mp.dirGen.contains(dirName) || mp.dir.contains(dirName)) {
                return mp
            }
            val mpSub = mp.getMapPackByDir(dirName)
            if (mpSub != null) {
                return mp
            }
        }
        return null
    }

    fun addMap(map: ItemMap) {
        maps.add(map)
    }

    fun getMap(index: Int): ItemMap {
        return maps[index]
    }

    val mapsCount: Int
        get() = maps.size

    fun addMapPack(mpSub: ItemMapPack) {
        mpSub.validate()
        mMapPacks.add(mpSub)
    }

    fun getMapPack(index: Int): ItemMapPack {
        return mMapPacks[index]
    }

    val mapPackCount: Int
        get() = mMapPacks.size

    val mapPacks: List<ItemMapPack>
        get() = mMapPacks

}
