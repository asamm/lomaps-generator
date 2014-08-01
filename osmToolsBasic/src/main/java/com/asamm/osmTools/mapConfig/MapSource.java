/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.asamm.osmTools.mapConfig;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 *
 * @author volda
 */
public class MapSource {

    // define xml file 
    private List<ItemMapPack> mMapPackList;

    public MapSource() {
        mMapPackList = new ArrayList<ItemMapPack>();
    }

    public boolean hasData() {
        return mMapPackList.size() > 0;
    }

    public void addMapPack(ItemMapPack mapPack) {
        mapPack.validate();
        this.mMapPackList.add(mapPack);
    }

    public Iterator<ItemMapPack> getMapPacksIterator() {
        return mMapPackList.iterator();
    }

    public ItemMapPack getMapPackByDir(String dirName){
        for (int i = 0, m = mMapPackList.size(); i < m; i++){
            ItemMapPack mp = mMapPackList.get(i);
            if (mp.getDirGen().contains(dirName) || mp.getDir().contains(dirName)){
                return mp;
            }

            ItemMapPack mpSub = mp.getMapPackByDir(dirName);
            if (mpSub != null){
                return mpSub;
            }
        }
        return null;
    }

    /**
     * Search in definitions for map specified by it's ID
     * @param mapId ID of map we search for
     * @return found item
     */
    public ItemMap getMapById(String mapId) {
        // search for map
        for (int i = 0, m = mMapPackList.size(); i < m; i++){
            ItemMapPack mp = mMapPackList.get(i);
            ItemMap map = mp.getMapById(mapId);
            if (map != null) {
                return map;
            }
        }

        // throw exception if maps wasn't found
        throw new IllegalArgumentException("Map '" + mapId + "', not found");
    }
}
    
    
   
