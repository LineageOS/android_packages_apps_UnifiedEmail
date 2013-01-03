/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.mail.photomanager;

import android.content.ComponentCallbacks2;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.net.Uri;
import android.os.Handler;
import android.os.Handler.Callback;
import android.os.HandlerThread;
import android.os.Message;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Contacts.Photo;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.Directory;
import android.text.TextUtils;
import android.util.LruCache;
import com.android.mail.ui.DividedImageCanvas;
import com.android.mail.utils.LogUtils;

import com.google.common.base.Objects;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import java.lang.ref.Reference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Asynchronously loads contact photos and maintains a cache of photos.
 */
public abstract class ContactPhotoManager implements ComponentCallbacks2 {
    static final String TAG = "ContactPhotoManager";
    static final boolean DEBUG = false; // Don't submit with true
    static final boolean DEBUG_SIZES = false; // Don't submit with true

    public static final String CONTACT_PHOTO_SERVICE = "contactPhotos";

    public static abstract class DefaultImageProvider {
        /**
         * Applies the default avatar to the DividedImageView. Extent is an
         * indicator for the size (width or height). If darkTheme is set, the
         * avatar is one that looks better on dark background
         * @param id
         */
        public abstract void applyDefaultImage(String name, String emailAddress,
                DividedImageCanvas view, int extent);
    }

    public static final DefaultImageProvider DEFAULT_AVATAR = new LetterTileProvider();

    /**
     * Requests the singleton instance of {@link AccountTypeManager} with data bound from
     * the available authenticators. This method can safely be called from the UI thread.
     */
    public static ContactPhotoManager getInstance(Context context) {
        Context applicationContext = context.getApplicationContext();
        ContactPhotoManager service =
                (ContactPhotoManager) applicationContext.getSystemService(CONTACT_PHOTO_SERVICE);
        if (service == null) {
            service = createContactPhotoManager(applicationContext);
            LogUtils.e(TAG, "No contact photo service in context: " + applicationContext);
        }
        return service;
    }

    public static synchronized ContactPhotoManager createContactPhotoManager(Context context) {
        return new ContactPhotoManagerImpl(context);
    }

    /**
     * Calls {@link #loadThumbnail(DividedImageCanvas, long, boolean, DefaultImageProvider)} with
     * {@link #DEFAULT_AVATAR}.
     */
    public final void loadThumbnail(DividedImageCanvas view, String name, String emailAddress) {
        loadThumbnail(view, name, emailAddress, DEFAULT_AVATAR);
    }

    public abstract void loadThumbnail(DividedImageCanvas view, String name, String emailAddress,
            DefaultImageProvider defaultProvider);

    /**
     * Remove photo from the supplied image view. This also cancels current pending load request
     * inside this photo manager.
     */
    public abstract void removePhoto(DividedImageCanvas view);

    /**
     * Temporarily stops loading photos from the database.
     */
    public abstract void pause();

    /**
     * Resumes loading photos from the database.
     */
    public abstract void resume();

    /**
     * Marks all cached photos for reloading.  We can continue using cache but should
     * also make sure the photos haven't changed in the background and notify the views
     * if so.
     */
    public abstract void refreshCache();

    /**
     * Initiates a background process that over time will fill up cache with
     * preload photos.
     */
    public abstract void preloadPhotosInBackground();

    // ComponentCallbacks2
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
    }

    // ComponentCallbacks2
    @Override
    public void onLowMemory() {
    }

    // ComponentCallbacks2
    @Override
    public void onTrimMemory(int level) {
    }
}

class ContactPhotoManagerImpl extends ContactPhotoManager implements Callback {
    private static final String LOADER_THREAD_NAME = "ContactPhotoLoader";

    /**
     * Type of message sent by the UI thread to itself to indicate that some photos
     * need to be loaded.
     */
    private static final int MESSAGE_REQUEST_LOADING = 1;

    /**
     * Type of message sent by the loader thread to indicate that some photos have
     * been loaded.
     */
    private static final int MESSAGE_PHOTOS_LOADED = 2;

    private static final String[] EMPTY_STRING_ARRAY = new String[0];
    private static final String[] COLUMNS = new String[] { Photo._ID, Photo.PHOTO };

    /**
     * Maintains the state of a particular photo.
     */
    private static class BitmapHolder {
        byte[] bytes;

        volatile boolean fresh;

        public BitmapHolder(byte[] bytes, int originalSmallerExtent) {
            this.bytes = bytes;
            this.fresh = true;
        }
    }

    private final Context mContext;

    /**
     * An LRU cache for bitmap holders. The cache contains bytes for photos just
     * as they come from the database. Each holder has a soft reference to the
     * actual bitmap.
     */
    private final LruCache<Object, BitmapHolder> mBitmapHolderCache;

    /**
     * An LRU cache for photo ids mapped to contact addresses.
     */
    private final LruCache<String, Long> mPhotoIdCache;

    /**
     * {@code true} if ALL entries in {@link #mBitmapHolderCache} are NOT fresh.
     */
    private volatile boolean mBitmapHolderCacheAllUnfresh = true;

    /**
     * Cache size threshold at which bitmaps will not be preloaded.
     */
    private final int mBitmapHolderCacheRedZoneBytes;

    /**
     * Level 2 LRU cache for bitmaps. This is a smaller cache that holds
     * the most recently used bitmaps to save time on decoding
     * them from bytes (the bytes are stored in {@link #mBitmapHolderCache}.
     */
    private final LruCache<Object, Bitmap> mBitmapCache;

    /**
     * A map from DividedImageView to the corresponding photo ID or uri, encapsulated in a request.
     * The request may swapped out before the photo loading request is started.
     */
    private final ConcurrentHashMap<DividedImageCanvas, Request> mPendingRequests =
            new ConcurrentHashMap<DividedImageCanvas, Request>();

    /**
     * Handler for messages sent to the UI thread.
     */
    private final Handler mMainThreadHandler = new Handler(this);

    /**
     * Thread responsible for loading photos from the database. Created upon
     * the first request.
     */
    private LoaderThread mLoaderThread;

    /**
     * A gate to make sure we only send one instance of MESSAGE_PHOTOS_NEEDED at a time.
     */
    private boolean mLoadingRequested;

    /**
     * Flag indicating if the image loading is paused.
     */
    private boolean mPaused;

    /** Cache size for {@link #mBitmapHolderCache} for devices with "large" RAM. */
    private static final int HOLDER_CACHE_SIZE = 2000000;

    /** Cache size for {@link #mBitmapCache} for devices with "large" RAM. */
    private static final int BITMAP_CACHE_SIZE = 36864 * 48; // 1728K

    /** Cache size for {@link #mPhotoIdCache}. Starting with 500 entries. */
    private static final int PHOTO_ID_CACHE_SIZE = 500;

    /** For debug: How many times we had to reload cached photo for a stale entry */
    private final AtomicInteger mStaleCacheOverwrite = new AtomicInteger();

    /** For debug: How many times we had to reload cached photo for a fresh entry.  Should be 0. */
    private final AtomicInteger mFreshCacheOverwrite = new AtomicInteger();

    public ContactPhotoManagerImpl(Context context) {
        mContext = context;

        final float cacheSizeAdjustment =
                (MemoryUtils.getTotalMemorySize() >= MemoryUtils.LARGE_RAM_THRESHOLD) ?
                        1.0f : 0.5f;
        final int bitmapCacheSize = (int) (cacheSizeAdjustment * BITMAP_CACHE_SIZE);
        mBitmapCache = new LruCache<Object, Bitmap>(bitmapCacheSize) {
            @Override protected int sizeOf(Object key, Bitmap value) {
                return value.getByteCount();
            }

            @Override protected void entryRemoved(
                    boolean evicted, Object key, Bitmap oldValue, Bitmap newValue) {
                if (DEBUG) dumpStats();
            }
        };
        final int holderCacheSize = (int) (cacheSizeAdjustment * HOLDER_CACHE_SIZE);
        mBitmapHolderCache = new LruCache<Object, BitmapHolder>(holderCacheSize) {
            @Override protected int sizeOf(Object key, BitmapHolder value) {
                return value.bytes != null ? value.bytes.length : 0;
            }

            @Override protected void entryRemoved(
                    boolean evicted, Object key, BitmapHolder oldValue, BitmapHolder newValue) {
                if (DEBUG) dumpStats();
            }
        };
        mBitmapHolderCacheRedZoneBytes = (int) (holderCacheSize * 0.75);
        LogUtils.i(TAG, "Cache adj: " + cacheSizeAdjustment);
        if (DEBUG) {
            LogUtils.d(TAG, "Cache size: " + btk(mBitmapHolderCache.maxSize())
                    + " + " + btk(mBitmapCache.maxSize()));
        }
        mPhotoIdCache = new LruCache<String, Long>(PHOTO_ID_CACHE_SIZE);
    }

    /** Converts bytes to K bytes, rounding up.  Used only for debug log. */
    private static String btk(int bytes) {
        return ((bytes + 1023) / 1024) + "K";
    }

    private static final int safeDiv(int dividend, int divisor) {
        return (divisor  == 0) ? 0 : (dividend / divisor);
    }

    /**
     * Dump cache stats on logcat.
     */
    private void dumpStats() {
        if (!DEBUG) {
            return;
        }
        int numHolders = 0;
        int rawBytes = 0;
        int bitmapBytes = 0;
        int numBitmaps = 0;
        for (BitmapHolder h : mBitmapHolderCache.snapshot().values()) {
            numHolders++;
            if (h.bytes != null) {
                rawBytes += h.bytes.length;
            }
        }
        LogUtils.d(TAG,
                "L1: " + btk(rawBytes) + " + " + btk(bitmapBytes) + " = "
                        + btk(rawBytes + bitmapBytes) + ", " + numHolders + " holders, "
                        + numBitmaps + " bitmaps, avg: " + btk(safeDiv(rawBytes, numHolders)) + ","
                        + btk(safeDiv(bitmapBytes, numBitmaps)));
        LogUtils.d(TAG, "L1 Stats: " + mBitmapHolderCache.toString() + ", overwrite: fresh="
                + mFreshCacheOverwrite.get() + " stale=" + mStaleCacheOverwrite.get());

        numBitmaps = 0;
        bitmapBytes = 0;
        for (Bitmap b : mBitmapCache.snapshot().values()) {
            numBitmaps++;
            bitmapBytes += b.getByteCount();
        }
        LogUtils.d(TAG, "L2: " + btk(bitmapBytes) + ", " + numBitmaps + " bitmaps" + ", avg: "
                + btk(safeDiv(bitmapBytes, numBitmaps)));
        // We don't get from L2 cache, so L2 stats is meaningless.
    }

    @Override
    public void onTrimMemory(int level) {
        if (DEBUG) LogUtils.d(TAG, "onTrimMemory: " + level);
        if (level >= ComponentCallbacks2.TRIM_MEMORY_MODERATE) {
            // Clear the caches.  Note all pending requests will be removed too.
            clear();
        }
    }

    @Override
    public void preloadPhotosInBackground() {
        ensureLoaderThread();
        mLoaderThread.requestPreloading();
    }

    @Override
    public void loadThumbnail(DividedImageCanvas view, String name, String emailAddress,
            DefaultImageProvider defaultProvider) {
        if (TextUtils.isEmpty(name)) {
            // No photo is needed
            defaultProvider.applyDefaultImage(name, emailAddress, view, -1);
            mPendingRequests.remove(view);
        } else {
            if (DEBUG)
                LogUtils.d(TAG, "loadPhoto request: " + name);
            loadPhotoByIdOrUri(view,
                    Request.createFromEmailAddress(name, emailAddress, defaultProvider));
        }
    }

    private void loadPhotoByIdOrUri(DividedImageCanvas view, Request request) {
        boolean loaded = loadCachedPhoto(view, request, false);
        if (loaded) {
            mPendingRequests.remove(view);
        } else {
            mPendingRequests.put(view, request);
            if (!mPaused) {
                // Send a request to start loading photos
                requestLoading();
            }
        }
    }

    @Override
    public void removePhoto(DividedImageCanvas view) {
        view.reset();
        mPendingRequests.remove(view);
    }

    @Override
    public void refreshCache() {
        if (mBitmapHolderCacheAllUnfresh) {
            if (DEBUG) LogUtils.d(TAG, "refreshCache -- no fresh entries.");
            return;
        }
        if (DEBUG) LogUtils.d(TAG, "refreshCache");
        mBitmapHolderCacheAllUnfresh = true;
        for (BitmapHolder holder : mBitmapHolderCache.snapshot().values()) {
            holder.fresh = false;
        }
    }

    /**
     * Checks if the photo is present in cache.  If so, sets the photo on the view.
     *
     * @return false if the photo needs to be (re)loaded from the provider.
     */
    private boolean loadCachedPhoto(DividedImageCanvas view, Request request, boolean fadeIn) {
        BitmapHolder holder = mBitmapHolderCache.get(request.getKey());
        if (holder == null) {
            // The bitmap has not been loaded ==> show default avatar
            request.applyDefaultImage(view);
            return false;
        }

        if (holder.bytes == null) {
            request.applyDefaultImage(view);
            return holder.fresh;
        }

        Bitmap cachedBitmap = view.addDivisionImage(holder.bytes, request.getEmailAddress());

        // Put the bitmap in the LRU cache. But only do this for images that are small enough
        // (we require that at least six of those can be cached at the same time)
        if (cachedBitmap != null && cachedBitmap.getByteCount() < mBitmapCache.maxSize() / 6) {
            mBitmapCache.put(request.getKey(), cachedBitmap);
        }

        return holder.fresh;
    }

    public void clear() {
        if (DEBUG) LogUtils.d(TAG, "clear");
        mPendingRequests.clear();
        mBitmapHolderCache.evictAll();
        mBitmapCache.evictAll();
        mPhotoIdCache.evictAll();
    }

    @Override
    public void pause() {
        mPaused = true;
    }

    @Override
    public void resume() {
        mPaused = false;
        if (DEBUG) dumpStats();
        if (!mPendingRequests.isEmpty()) {
            requestLoading();
        }
    }

    /**
     * Sends a message to this thread itself to start loading images.  If the current
     * view contains multiple image views, all of those image views will get a chance
     * to request their respective photos before any of those requests are executed.
     * This allows us to load images in bulk.
     */
    private void requestLoading() {
        if (!mLoadingRequested) {
            mLoadingRequested = true;
            mMainThreadHandler.sendEmptyMessage(MESSAGE_REQUEST_LOADING);
        }
    }

    /**
     * Processes requests on the main thread.
     */
    @Override
    public boolean handleMessage(Message msg) {
        switch (msg.what) {
            case MESSAGE_REQUEST_LOADING: {
                mLoadingRequested = false;
                if (!mPaused) {
                    ensureLoaderThread();
                    mLoaderThread.requestLoading();
                }
                return true;
            }

            case MESSAGE_PHOTOS_LOADED: {
                if (!mPaused) {
                    processLoadedImages();
                }
                if (DEBUG) dumpStats();
                return true;
            }
        }
        return false;
    }

    public void ensureLoaderThread() {
        if (mLoaderThread == null) {
            mLoaderThread = new LoaderThread(mContext.getContentResolver());
            mLoaderThread.start();
        }
    }

    /**
     * Goes over pending loading requests and displays loaded photos.  If some of the
     * photos still haven't been loaded, sends another request for image loading.
     */
    private void processLoadedImages() {
        Iterator<DividedImageCanvas> iterator = mPendingRequests.keySet().iterator();
        while (iterator.hasNext()) {
            DividedImageCanvas view = iterator.next();
            Request key = mPendingRequests.get(view);
            boolean loaded = loadCachedPhoto(view, key, true);
            if (loaded) {
                iterator.remove();
            }
        }

        // TODO: this already seems to happen when calling loadCachedPhoto
        //softenCache();

        if (!mPendingRequests.isEmpty()) {
            requestLoading();
        }
    }

    /**
     * Store the supplied photo id to contact address mapping so that we don't
     * have to lookup the contact again.
     * @param id Id of the photo matching the contact
     * @param contactAddress Email address of the contact
     */
    private void cachePhotoId(Long id, String contactAddress) {
        mPhotoIdCache.put(contactAddress, id);
    }

    /**
     * Stores the supplied bitmap in cache.
     */
    private void cacheBitmap(Object key, byte[] bytes, int requestedExtent) {
        if (DEBUG) {
            BitmapHolder prev = mBitmapHolderCache.get(key);
            if (prev != null && prev.bytes != null) {
                LogUtils.d(TAG, "Overwriting cache: key=" + key
                        + (prev.fresh ? " FRESH" : " stale"));
                if (prev.fresh) {
                    mFreshCacheOverwrite.incrementAndGet();
                } else {
                    mStaleCacheOverwrite.incrementAndGet();
                }
            }
            LogUtils.d(TAG, "Caching data: key=" + key + ", "
                    + (bytes == null ? "<null>" : btk(bytes.length)));
        }
        BitmapHolder holder = new BitmapHolder(bytes, bytes == null ? -1
                : BitmapUtil.getSmallerExtentFromBytes(bytes));

        mBitmapHolderCache.put(key, holder);
        mBitmapHolderCacheAllUnfresh = false;
    }

    /**
     * Populates an array of photo IDs that need to be loaded. Also decodes bitmaps that we have
     * already loaded
     */
    private void obtainPhotoIdsAndUrisToLoad(Set<Long> photoIds,
            Set<String> photoIdsAsStrings, Set<Request> uris, Set<Request> names) {
        photoIds.clear();
        photoIdsAsStrings.clear();
        uris.clear();
        names.clear();

        /*
         * Since the call is made from the loader thread, the map could be
         * changing during the iteration. That's not really a problem:
         * ConcurrentHashMap will allow those changes to happen without throwing
         * exceptions. Since we may miss some requests in the situation of
         * concurrent change, we will need to check the map again once loading
         * is complete.
         */
        Iterator<Request> iterator = mPendingRequests.values().iterator();
        while (iterator.hasNext()) {
            Request request = iterator.next();
            final BitmapHolder holder = mBitmapHolderCache.get(request.getKey());
            if (holder == null || holder.bytes == null || !holder.fresh) {
                names.add(request);
            }
        }
    }

    /**
     * The thread that performs loading of photos from the database.
     */
    private class LoaderThread extends HandlerThread implements Callback {
        private static final int MESSAGE_PRELOAD_PHOTOS = 0;
        private static final int MESSAGE_LOAD_PHOTOS = 1;

        /**
         * A pause between preload batches that yields to the UI thread.
         */
        private static final int PHOTO_PRELOAD_DELAY = 1000;

        /**
         * Number of photos to preload per batch.
         */
        private static final int PRELOAD_BATCH = 25;

        /**
         * Maximum number of photos to preload.  If the cache size is 2Mb and
         * the expected average size of a photo is 4kb, then this number should be 2Mb/4kb = 500.
         */
        private static final int MAX_PHOTOS_TO_PRELOAD = 100;

        private final ContentResolver mResolver;
        private final Set<Long> mPhotoIds = Sets.newHashSet();
        private final Set<String> mPhotoIdsAsStrings = Sets.newHashSet();
        private final Set<Request> mPhotoUris = Sets.newHashSet();
        private final Set<Request> mNames = Sets.newHashSet();
        private final List<Long> mPreloadPhotoIds = Lists.newArrayList();

        private Handler mLoaderThreadHandler;

        private static final int PRELOAD_STATUS_NOT_STARTED = 0;
        private static final int PRELOAD_STATUS_IN_PROGRESS = 1;
        private static final int PRELOAD_STATUS_DONE = 2;

        private int mPreloadStatus = PRELOAD_STATUS_NOT_STARTED;

        public LoaderThread(ContentResolver resolver) {
            super(LOADER_THREAD_NAME);
            mResolver = resolver;
        }

        public void ensureHandler() {
            if (mLoaderThreadHandler == null) {
                mLoaderThreadHandler = new Handler(getLooper(), this);
            }
        }

        /**
         * Kicks off preloading of the next batch of photos on the background thread.
         * Preloading will happen after a delay: we want to yield to the UI thread
         * as much as possible.
         * <p>
         * If preloading is already complete, does nothing.
         */
        public void requestPreloading() {
            if (mPreloadStatus == PRELOAD_STATUS_DONE) {
                return;
            }

            ensureHandler();
            if (mLoaderThreadHandler.hasMessages(MESSAGE_LOAD_PHOTOS)) {
                return;
            }

            mLoaderThreadHandler.sendEmptyMessageDelayed(
                    MESSAGE_PRELOAD_PHOTOS, PHOTO_PRELOAD_DELAY);
        }

        /**
         * Sends a message to this thread to load requested photos.  Cancels a preloading
         * request, if any: we don't want preloading to impede loading of the photos
         * we need to display now.
         */
        public void requestLoading() {
            ensureHandler();
            mLoaderThreadHandler.removeMessages(MESSAGE_PRELOAD_PHOTOS);
            mLoaderThreadHandler.sendEmptyMessage(MESSAGE_LOAD_PHOTOS);
        }

        /**
         * Receives the above message, loads photos and then sends a message
         * to the main thread to process them.
         */
        @Override
        public boolean handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_PRELOAD_PHOTOS:
                    preloadPhotosInBackground();
                    break;
                case MESSAGE_LOAD_PHOTOS:
                    loadPhotosInBackground();
                    break;
            }
            return true;
        }

        /**
         * The first time it is called, figures out which photos need to be preloaded.
         * Each subsequent call preloads the next batch of photos and requests
         * another cycle of preloading after a delay.  The whole process ends when
         * we either run out of photos to preload or fill up cache.
         */
        private void preloadPhotosInBackground() {
            if (mPreloadStatus == PRELOAD_STATUS_DONE) {
                return;
            }

            if (mPreloadStatus == PRELOAD_STATUS_NOT_STARTED) {
                queryPhotosForPreload();
                if (mPreloadPhotoIds.isEmpty()) {
                    mPreloadStatus = PRELOAD_STATUS_DONE;
                } else {
                    mPreloadStatus = PRELOAD_STATUS_IN_PROGRESS;
                }
                requestPreloading();
                return;
            }

            if (mBitmapHolderCache.size() > mBitmapHolderCacheRedZoneBytes) {
                mPreloadStatus = PRELOAD_STATUS_DONE;
                return;
            }

            mPhotoIds.clear();
            mPhotoIdsAsStrings.clear();

            int count = 0;
            int preloadSize = mPreloadPhotoIds.size();
            while(preloadSize > 0 && mPhotoIds.size() < PRELOAD_BATCH) {
                preloadSize--;
                count++;
                Long photoId = mPreloadPhotoIds.get(preloadSize);
                mPhotoIds.add(photoId);
                mPhotoIdsAsStrings.add(photoId.toString());
                mPreloadPhotoIds.remove(preloadSize);
            }

            loadThumbnails(true);

            if (preloadSize == 0) {
                mPreloadStatus = PRELOAD_STATUS_DONE;
            }

            LogUtils.v(TAG, "Preloaded " + count + " photos.  Cached bytes: "
                    + mBitmapHolderCache.size());

            requestPreloading();
        }

        private void queryPhotosForPreload() {
            Cursor cursor = null;
            try {
                Uri uri = Contacts.CONTENT_URI.buildUpon().appendQueryParameter(
                        ContactsContract.DIRECTORY_PARAM_KEY, String.valueOf(Directory.DEFAULT))
                        .appendQueryParameter(ContactsContract.LIMIT_PARAM_KEY,
                                String.valueOf(MAX_PHOTOS_TO_PRELOAD))
                        .build();
                cursor = mResolver.query(uri, new String[] { Contacts.PHOTO_ID },
                        Contacts.PHOTO_ID + " NOT NULL AND " + Contacts.PHOTO_ID + "!=0",
                        null,
                        Contacts.STARRED + " DESC, " + Contacts.LAST_TIME_CONTACTED + " DESC");

                if (cursor != null) {
                    while (cursor.moveToNext()) {
                        // Insert them in reverse order, because we will be taking
                        // them from the end of the list for loading.
                        mPreloadPhotoIds.add(0, cursor.getLong(0));
                    }
                }
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }

        private void loadPhotosInBackground() {
            obtainPhotoIdsAndUrisToLoad(mPhotoIds, mPhotoIdsAsStrings, mPhotoUris, mNames);
            loadThumbnails(false);
            loadEmailAddressBasedPhotos(false);
            requestPreloading();
        }

        /** Loads thumbnail photos with ids */
        private void loadThumbnails(boolean preloading) {
            Cursor cursor = null;
            try {
                cursor = loadThumbnails(preloading, mPhotoIdsAsStrings, mPhotoIds);

                if (cursor != null) {
                    while (cursor.moveToNext()) {
                        Long id = cursor.getLong(0);
                        byte[] bytes = cursor.getBlob(1);
                        cacheBitmap(id, bytes, -1);
                        mPhotoIds.remove(id);
                    }
                }
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
            loadProfileThumbnails(preloading, mPhotoIds);
            mMainThreadHandler.sendEmptyMessage(MESSAGE_PHOTOS_LOADED);
        }

        private Cursor loadThumbnails(boolean preloading, Set<String> photoIdsAsString,
                Set<Long> photoIds) {
            if (photoIds.isEmpty()) {
                return null;
            }

            // Remove loaded photos from the preload queue: we don't want
            // the preloading process to load them again.
            if (!preloading && mPreloadStatus == PRELOAD_STATUS_IN_PROGRESS) {
                for (Long id : photoIds) {
                    mPreloadPhotoIds.remove(id);
                }
                if (mPreloadPhotoIds.isEmpty()) {
                    mPreloadStatus = PRELOAD_STATUS_DONE;
                }
            }

            return mResolver.query(Data.CONTENT_URI,
                        COLUMNS,
                        createInQuery(Photo._ID, photoIds.size()),
                        photoIdsAsString.toArray(EMPTY_STRING_ARRAY),
                        null);
        }

        private void loadProfileThumbnails(boolean preloading, Set<Long> photoIds) {
            // Remaining photos were not found in the contacts database (but might be in profile).
            for (Long id : photoIds) {
                if (ContactsContract.isProfileId(id)) {
                    Cursor profileCursor = null;
                    try {
                        profileCursor = mResolver.query(
                                ContentUris.withAppendedId(Data.CONTENT_URI, id),
                                COLUMNS, null, null, null);
                        if (profileCursor != null && profileCursor.moveToFirst()) {
                            cacheBitmap(profileCursor.getLong(0), profileCursor.getBlob(1), -1);
                        } else {
                            // Couldn't load a photo this way either.
                            cacheBitmap(id, null, -1);
                        }
                    } finally {
                        if (profileCursor != null) {
                            profileCursor.close();
                        }
                    }
                } else {
                    // Not a profile photo and not found - mark the cache accordingly
                    cacheBitmap(id, null, -1);
                }
            }
        }

        private String createInQuery(String value, int itemCount) {
            // Build first query
            StringBuilder query = new StringBuilder().append(value + " IN (");
            appendQuestionMarks(query, itemCount);
            query.append(')');
            return query.toString();
        }

        void appendQuestionMarks(StringBuilder query, int itemCount) {
            boolean first = true;
            for (int i = 0; i < itemCount; i++) {
                if (first) {
                    first = false;
                } else {
                    query.append(',');
                }
                query.append('?');
            }
        }

        private final String[] DATA_COLS = new String[] {
            Email.DATA,                 // 0
            Email.PHOTO_ID,             // 1
        };

        private static final int DATA_EMAIL_COLUMN = 0;
        private static final int DATA_PHOTO_ID_COLUMN = 1;

        private void loadEmailAddressBasedPhotos(boolean preloading) {
            HashSet<String> addresses = new HashSet<String>();
            Set<Long> photoIds = new HashSet<Long>();
            Set<String> photoIdsAsString = new HashSet<String>();
            HashMap<Long, String> photoIdMap = new HashMap<Long, String>();
            Long match;
            String emailAddress;
            for (Request request : mNames) {
                emailAddress = request.getEmailAddress();
                match = mPhotoIdCache.get(emailAddress);
                if (match != null) {
                    photoIds.add(match);
                    photoIdsAsString.add(match + "");
                    photoIdMap.put(match, emailAddress);
                } else {
                    addresses.add(emailAddress);
                }
            }

            if (addresses.size() > 0) {
                String[] selectionArgs = new String[addresses.size()];
                addresses.toArray(selectionArgs);
                Cursor photoIdsCursor = null;
                try {
                    StringBuilder query = new StringBuilder().append(Data.MIMETYPE).append("='")
                            .append(Email.CONTENT_ITEM_TYPE).append("' AND ").append(Email.DATA)
                            .append(" IN (");
                    appendQuestionMarks(query, addresses.size());
                    query.append(')');
                    photoIdsCursor = mResolver.query(Data.CONTENT_URI, DATA_COLS,
                            query.toString(), selectionArgs, null /* sortOrder */);
                    Long id;
                    String contactAddress;
                    if (photoIdsCursor != null) {
                        while (photoIdsCursor.moveToNext()) {
                            id = photoIdsCursor.getLong(DATA_PHOTO_ID_COLUMN);
                            // In case there are multiple contacts for this
                            // contact, try to always pick the one that actually
                            // has a photo.
                            if (id != 0) {
                                contactAddress = photoIdsCursor.getString(DATA_EMAIL_COLUMN);
                                photoIds.add(id);
                                photoIdsAsString.add(id + "");
                                photoIdMap.put(id, contactAddress);
                                cachePhotoId(id, contactAddress);
                            }
                        }
                    }
                } finally {
                    if (photoIdsCursor != null) {
                        photoIdsCursor.close();
                    }
                }
            }
            if (photoIds != null && photoIds.size() > 0) {
                Cursor photosCursor = null;
                try {
                    photosCursor = loadThumbnails(preloading, photoIdsAsString, photoIds);

                    if (photosCursor != null) {
                        while (photosCursor.moveToNext()) {
                            Long id = photosCursor.getLong(0);
                            byte[] bytes = photosCursor.getBlob(1);
                            cacheBitmap(photoIdMap.get(id), bytes, -1);
                            photoIds.remove(id);
                        }
                    }
                } finally {
                    if (photosCursor != null) {
                        photosCursor.close();
                    }
                }
            }
            String matchingAddress;
            // Remaining photos were not found in the contacts database (but might be in profile).
            for (Long id : photoIds) {
                matchingAddress = photoIdMap.get(id);
                if (ContactsContract.isProfileId(id)) {
                    Cursor profileCursor = null;
                    try {
                        profileCursor = mResolver.query(
                                ContentUris.withAppendedId(Data.CONTENT_URI, id),
                                COLUMNS, null, null, null);
                        if (profileCursor != null && profileCursor.moveToFirst()) {
                            cacheBitmap(matchingAddress, profileCursor.getBlob(1), -1);
                        } else {
                            // Couldn't load a photo this way either.
                            cacheBitmap(matchingAddress, null, -1);
                        }
                    } finally {
                        if (profileCursor != null) {
                            profileCursor.close();
                        }
                    }
                } else {
                    // Not a profile photo and not found - mark the cache accordingly
                    cacheBitmap(matchingAddress, null, -1);
                }
            }
            // TODO(mindyp): this optimization assumes that contact photos don't
            // change/ update that often, and if you didn't have a matching id
            // for a contact before, you probably won't be getting it any time soon.
            for (String a : addresses) {
                if (!photoIdMap.containsValue(a)) {
                    // We couldn't find a matching photo id at all, so just
                    // cache this as needing a default image.
                    cacheBitmap(a, null, -1);
                }
            }
            mMainThreadHandler.sendEmptyMessage(MESSAGE_PHOTOS_LOADED);
        }
    }

    /**
     * A holder for a contact photo request.
     */
    private static final class Request {
        private final int mRequestedExtent;
        private final DefaultImageProvider mDefaultProvider;
        private final String mDisplayName;
        private final String mEmailAddress;

        private Request(String name, String emailAddress, int requestedExtent,
                DefaultImageProvider defaultProvider) {
            mRequestedExtent = requestedExtent;
            mDefaultProvider = defaultProvider;
            mDisplayName = name;
            mEmailAddress = emailAddress;
        }

        public String getDisplayName() {
            return mDisplayName;
        }

        public String getEmailAddress() {
            return mEmailAddress;
        }

        public static Request createFromEmailAddress(String displayName, String emailAddress,
                DefaultImageProvider defaultProvider) {
            return new Request(displayName, emailAddress, -1, defaultProvider);
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + mRequestedExtent;
            result = prime * result + ((mDisplayName == null) ? 0 : mDisplayName.hashCode());
            result = prime * result + ((mEmailAddress == null) ? 0 : mEmailAddress.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null) return false;
            if (getClass() != obj.getClass()) return false;
            final Request that = (Request) obj;
            if (mRequestedExtent != that.mRequestedExtent) return false;
            if (!Objects.equal(mDisplayName, that.mDisplayName)) return false;
            if (!Objects.equal(mEmailAddress, that.mEmailAddress)) return false;
            // Don't compare equality of mDarkTheme because it is only used in the default contact
            // photo case. When the contact does have a photo, the contact photo is the same
            // regardless of mDarkTheme, so we shouldn't need to put the photo request on the queue
            // twice.
            return true;
        }

        public Object getKey() {
            return mEmailAddress;
        }

        public void applyDefaultImage(DividedImageCanvas view) {
            mDefaultProvider.applyDefaultImage(getDisplayName(), getEmailAddress(), view,
                    mRequestedExtent);
        }
    }
}
