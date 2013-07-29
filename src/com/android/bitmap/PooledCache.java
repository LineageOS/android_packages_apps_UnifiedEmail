package com.android.bitmap;

public interface PooledCache<K, V> {

    V get(K key);
    V put(K key, V value);
    void offer(V scrapValue);
    V poll();
    String toDebugString();

}
