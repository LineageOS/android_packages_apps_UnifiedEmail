package com.android.bitmap;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;


/**
 * An alternative implementation of a pool+cache. This implementation only counts
 * unreferenced objects in its size calculation. Internally, it never evicts from
 * its cache, and instead {@link #poll()} is allowed to return unreferenced cache
 * entries.
 * <p>
 * You would only use this kind of cache if your objects are interchangeable and
 * have significant allocation cost, and if your memory footprint is somewhat
 * flexible.
 * <p>
 * Because this class only counts unreferenced objects toward targetSize,
 * it will have a total memory footprint of:
 * <code>(targetSize) + (# of threads concurrently writing to cache) +
 * (total size of still-referenced entries)</code>
 *
 */
public class AltPooledCache<K, V extends RefCountable> implements PooledCache<K, V> {

    private final LinkedHashMap<K, V> mCache;
    private final LinkedBlockingQueue<V> mPool;
    private final int mTargetSize;

    private final boolean DEBUG = DecodeTask.DEBUG;

    /**
     * @param targetSize not exactly a max size in practice
     */
    public AltPooledCache(int targetSize) {
        mCache = new LinkedHashMap<K, V>(0, 0.75f, true);
        mPool = new LinkedBlockingQueue<V>();
        mTargetSize = targetSize;
    }

    @Override
    public V get(K key) {
        synchronized (mCache) {
            return mCache.get(key);
        }
    }

    @Override
    public V put(K key, V value) {
        synchronized (mCache) {
            return mCache.put(key, value);
        }
    }

    @Override
    public void offer(V value) {
        if (value.getRefCount() != 0) {
            throw new IllegalArgumentException("unexpected offer of a referenced object: " + value);
        }
        mPool.offer(value);
    }

    @Override
    public V poll() {
        final V pooled = mPool.poll();
        if (pooled != null) {
            return pooled;
        }

        synchronized (mCache) {
            int unrefSize = 0;
            Map.Entry<K, V> eldestUnref = null;
            for (Map.Entry<K, V> entry : mCache.entrySet()) {
                final V value = entry.getValue();
                if (value.getRefCount() > 0) {
                    continue;
                }
                if (eldestUnref == null) {
                    eldestUnref = entry;
                }
                unrefSize += sizeOf(value);
                if (unrefSize > mTargetSize) {
                    break;
                }
            }
            // only return a scavenged cache entry if the cache has enough
            // eligible (unreferenced) items
            if (unrefSize <= mTargetSize) {
                if (DEBUG) System.err.println(
                        "POOL SCAVENGE FAILED, cache not fully warm yet. szDelta="
                        + (mTargetSize-unrefSize));
                return null;
            } else {
                mCache.remove(eldestUnref.getKey());
                if (DEBUG) System.err.println("POOL SCAVENGE SUCCESS, oldKey="
                        + eldestUnref.getKey());
                return eldestUnref.getValue();
            }
        }
    }

    protected int sizeOf(V value) {
        return 1;
    }

    @Override
    public String toDebugString() {
        if (DEBUG) {
            final StringBuilder sb = new StringBuilder("[");
            sb.append(super.toString());
            int size = 0;
            synchronized (mCache) {
                sb.append(" poolCount=");
                sb.append(mPool.size());
                sb.append(" cacheSize=");
                sb.append(mCache.size());
                sb.append("\n---------------------");
                for (V val : mPool) {
                    size += sizeOf(val);
                    sb.append("\n\tpool item: ");
                    sb.append(val);
                }
                sb.append("\n---------------------");
                for (Map.Entry<K, V> item : mCache.entrySet()) {
                    final V val = item.getValue();
                    sb.append("\n\tcache key=");
                    sb.append(item.getKey());
                    sb.append(" val=");
                    sb.append(val);
                    size += sizeOf(val);
                }
                sb.append("\n---------------------");
                sb.append("\nTOTAL SIZE=" + size);
            }
            sb.append("]");
            return sb.toString();
        } else {
            return null;
        }
    }

}
