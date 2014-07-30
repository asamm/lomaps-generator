/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.asamm.osmTools.mapConfig;

import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author volda
 */
public class ItemMapPack extends AItemMap {

    private static final String TAG = ItemMapPack.class.getSimpleName();
    
    // list of all maps
    private List<ItemMap> mMaps;
    // list of child mapPacks
    private List<ItemMapPack> mMapPacks;

    public ItemMapPack() {
        super(null);
        initialize();
    }

    public ItemMapPack(ItemMapPack mpParent) {
        super(mpParent);
        initialize();
    }

    private void initialize() {
        mMaps = new ArrayList<ItemMap>();
        mMapPacks = new ArrayList<ItemMapPack>();
    }

    protected ItemMap getMapById(String mapId) {
        for (ItemMap actualMap : mMaps) {
            if (actualMap.getId() != null && actualMap.getId().equals(mapId)) {
                return actualMap;
            }
        }
        for (ItemMapPack mp : mMapPacks) {
            ItemMap map = mp.getMapById(mapId);
            if (map != null) {
                return map;
            }
        }
        return null;
    }
    
    protected ItemMapPack getMapPackByDir (String dirName){
        for (ItemMapPack mp : mMapPacks){
            if (mp.getDirGen().contains(dirName) || mp.getDir().contains(dirName)){
                return mp;
            }
            ItemMapPack mpSub = mp.getMapPackByDir(dirName);
            if (mpSub != null){
                return mp;
            }
        }
        return null;
    }

    /**************************************************/
    /*               GETTERS & SETTERS                */
    /**************************************************/

    public void addMap(ItemMap map) {
        this.mMaps.add(map);
    }

    public ItemMap getMap(int index) {
        return mMaps.get(index);
    }

    public int getMapsCount() {
        return mMaps.size();
    }

    public void addMapPack(ItemMapPack mpSub) {
        if (mpSub.isValid()) {
            mMapPacks.add(mpSub);
        }
    }

    public ItemMapPack getMapPack(int index) {
        return mMapPacks.get(index);
    }

    public int getMapPackCount() {
        return mMapPacks.size();
    }
}
