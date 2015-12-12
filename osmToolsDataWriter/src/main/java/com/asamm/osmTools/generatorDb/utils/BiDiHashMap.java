package com.asamm.osmTools.generatorDb.utils;

import gnu.trove.map.hash.THashMap;

import java.util.Collection;
import java.util.List;

/**
 * Created by voldapet on 2015-12-02
 * Customized hash map that map the key -> values and also values to key.
 */
public class BiDiHashMap <K extends Object, V extends Object> {

    private THashMap<K,V> forward = new THashMap<K, V>();
    private THashMap<V,K> backward = new THashMap<V, K>();

    public synchronized void put(K key, V value) {
        forward.put(key, value);
        backward.put(value, key);
    }

    public synchronized V getValue(K key) {
        return forward.get(key);
    }

    public synchronized K getKey(V value) {
        return backward.get(value);
    }

    public synchronized Collection<K> getKeys (){
        return backward.values();
    }

    public synchronized Collection<V> getValues (){
        return forward.values();
    }
}