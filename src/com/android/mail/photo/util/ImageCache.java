/*
 * Copyright (C) 2011 Google Inc.
 * Licensed to The Android Open Source Project.
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

package com.android.mail.photo.util;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.os.Handler.Callback;
import android.os.Looper;
import android.os.Message;
import android.support.v4.util.LruCache;

import com.android.mail.R;
import com.android.mail.photo.content.ImageRequest;
import com.android.mail.photo.content.LocalImageRequest;
import com.android.mail.photo.content.MediaImageRequest;

import java.lang.ref.SoftReference;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Asynchronously loads images and maintains a cache of those.
 */
public class ImageCache implements Callback {

    /**
     * The listener interface notifying of avatar changes.
     */
    public interface OnAvatarChangeListener {

        /**
         * Invoked if a new avatar has been loaded for the specified Gaia ID.
         */
        void onAvatarChanged(String gaiaId);
    }

    /**
     * The listener interface notifying of media image changes.
     */
    public interface OnMediaImageChangeListener {

        /**
         * Invoked if a new media image has been loaded for the specified URL.
         */
        void onMediaImageChanged(String url);
    }

    /**
     * The listener interface notifying of image changes.
     */
    public interface OnRemoteImageChangeListener {

        /**
         * Invoked if a new media image has been loaded for the specified URL.
         */
        void onRemoteImageChanged(ImageRequest request, Bitmap bitmap);
    }

    /**
     * The listener interface notifying of remote image changes.
     */
    public interface OnRemoteDrawableChangeListener extends OnRemoteImageChangeListener {

        /**
         * Invoked if a new image has been loaded for the specified URL.
         */
        void onRemoteImageChanged(ImageRequest request, Drawable drawable);
    }

    /**
     * The listener interface notifying a listener that an image load request has been completed.
     */
    public interface OnImageRequestCompleteListener {

        /**
         * Invoked when a request is complete.
         */
        void onImageRequestComplete(ImageRequest request);
    }

    /**
     * The callback interface that must be implemented by the views requesting images.
     */
    public interface ImageConsumer {

        /**
         * @param bitmap The bitmap
         * @param loading The flag indicating if the image is still loading (if
         *            true, bitmap will be null).
         */
        void setBitmap(Bitmap bitmap, boolean loading);
    }

    /**
     * The callback interface that can optionally be implemented by the views
     * requesting images if they want to support animated drawables.
     */
    public interface DrawableConsumer extends ImageConsumer {

        /**
         * @param drawable The image
         * @param loading The flag indicating if the image is still loading (if
         *            true, bitmap will be null).
         */
        void setDrawable(Drawable drawable, boolean loading);
    }

    // Logging.
    static final String TAG = "ImageCache";

    private static final String LOADER_THREAD_NAME = "ImageCache";

    /**
     * Type of message sent by the UI thread to itself to indicate that some photos
     * need to be loaded.
     */
    private static final int MESSAGE_REQUEST_LOADING = 1;

    /**
     * Type of message sent by the loader thread to indicate that some photos have
     * been loaded.
     */
    private static final int MESSAGE_IMAGES_LOADED = 2;

    /**
     * Type of message sent to indicate that an avatar has changed.
     */
    private static final int MESSAGE_AVATAR_CHANGED = 3;

    /**
     * Type of message sent to indicate that a media image has changed.
     */
    private static final int MESSAGE_MEDIA_IMAGE_CHANGED = 4;

    /**
     * Type of message sent to indicate that an image has changed.
     */
    private static final int MESSAGE_REMOTE_IMAGE_CHANGED = 5;

    private static final byte[] EMPTY_ARRAY = new byte[0];

    /**
     * Maintains the state of a particular photo.
     */
    private static class ImageHolder {
        final byte[] bytes;
        final boolean complete;

        volatile boolean fresh;

        /**
         * Either {@link Bitmap} or {@link Drawable}.
         */
        Object image;
        SoftReference<Object> imageRef;


        public ImageHolder(byte[] bytes, boolean complete) {
            this.bytes = bytes;
            this.fresh = true;
            this.complete = complete;
        }
    }

    private static class MediaImageChangeNotification {
        MediaImageRequest request;
        byte[] imageBytes;
    }

    private static class RemoteImageChangeNotification {
        ImageRequest request;
        byte[] imageBytes;
    }

    private static final float ESTIMATED_BYTES_PER_PIXEL = 0.3f;

    private static int sTinyAvatarEstimatedSize;
    private static int sSmallAvatarEstimatedSize;
    private static int sMediumAvatarEstimatedSize;

    private static boolean sUseSoftReferences;

    private final Context mContext;

    private static HashSet<OnAvatarChangeListener> mAvatarListeners =
            new HashSet<OnAvatarChangeListener>();

    private static HashSet<OnMediaImageChangeListener> mMediaImageListeners =
            new HashSet<OnMediaImageChangeListener>();

    private static HashSet<OnRemoteImageChangeListener> mRemoteImageListeners =
            new HashSet<OnRemoteImageChangeListener>();

    private static HashSet<OnImageRequestCompleteListener> mRequestCompleteListeners =
            new HashSet<OnImageRequestCompleteListener>();

    /**
     * An LRU cache for image holders. The cache contains bytes for images just
     * as they come from the database. Each holder has a soft reference to the
     * actual image.
     */
    private final LruCache<ImageRequest, ImageHolder> mImageHolderCache;

    /**
     * Cache size threshold at which images will not be preloaded.
     */
    private final int mImageHolderCacheRedZoneBytes;

    /**
     * Level 2 LRU cache for images. This is a smaller cache that holds
     * the most recently used images to save time on decoding
     * them from bytes (the bytes are stored in {@link #mImageHolderCache}.
     */
    private final LruCache<ImageRequest, Object> mImageCache;

    /**
     * A map from {@link ImageConsumer} to the corresponding {@link ImageRequest}. Please
     * note that this request may change before the photo loading request is
     * started.
     */
    private final ConcurrentHashMap<ImageConsumer, ImageRequest> mPendingRequests =
            new ConcurrentHashMap<ImageConsumer, ImageRequest>();

    /**
     * Handler for messages sent to the UI thread.
     */
    private final Handler mMainThreadHandler = new Handler(Looper.getMainLooper(), this);

//    /**
//     * Thread responsible for loading photos from the database. Created upon
//     * the first request.
//     */
//    private LoaderThread mLoaderThread;

    /**
     * A gate to make sure we only send one instance of MESSAGE_PHOTOS_NEEDED at a time.
     */
    private boolean mLoadingRequested;

    /**
     * Flag indicating if the image loading is paused.
     */
    private boolean mPaused;

    private static ImageCache sInstance;

    public static synchronized ImageCache getInstance(Context context) {

        // We can use one global instance provided that we bind to the
        // application context instead of the context that is passed in.
        // Otherwise this static instance would retain the supplied context and
        // cause a leak.
        if (sInstance == null) {
            sInstance = new ImageCache(context.getApplicationContext());
        }
        return sInstance;
    }

    private ImageCache(Context context) {
        mContext = context;

        Resources resources = context.getApplicationContext().getResources();
        mImageCache = new LruCache<ImageRequest, Object>(
                resources.getInteger(R.integer.config_image_cache_max_bitmaps));
        int maxBytes = resources.getInteger(R.integer.config_image_cache_max_bytes);
        mImageHolderCache = new LruCache<ImageRequest, ImageHolder>(maxBytes) {

            /**
             * {@inheritDoc}
             */
            @Override
            protected int sizeOf(ImageRequest request, ImageHolder value) {
                return value.bytes != null ? value.bytes.length : 0;
            }
        };

        mImageHolderCacheRedZoneBytes = (int) (maxBytes * 0.9);

        if (sTinyAvatarEstimatedSize == 0) {
//            sTinyAvatarEstimatedSize = (int) (ESTIMATED_BYTES_PER_PIXEL
//                    * EsAvatarData.getTinyAvatarSize(context)
//                    * EsAvatarData.getTinyAvatarSize(context));
//            sSmallAvatarEstimatedSize = (int) (ESTIMATED_BYTES_PER_PIXEL
//                    * EsAvatarData.getSmallAvatarSize(context)
//                    * EsAvatarData.getSmallAvatarSize(context));
//            sMediumAvatarEstimatedSize = (int) (ESTIMATED_BYTES_PER_PIXEL
//                    * EsAvatarData.getMediumAvatarSize(context)
//                    * EsAvatarData.getMediumAvatarSize(context));

            sUseSoftReferences = Build.VERSION.SDK_INT >= 11;
        }
    }

//    /**
//     * Returns an estimate of the avatar size in bytes.
//     */
//    private int getEstimatedSizeInBytes(AvatarRequest request) {
//        switch (request.getSize()) {
//            case AvatarRequest.TINY: return sTinyAvatarEstimatedSize;
//            case AvatarRequest.SMALL: return sSmallAvatarEstimatedSize;
//            case AvatarRequest.MEDIUM: return sMediumAvatarEstimatedSize;
//        }
//        return 0;
//    }

    /**
     * Clears cache.
     */
    public void clear() {
        mImageHolderCache.evictAll();
        mImageCache.evictAll();
        mPendingRequests.clear();
    }

//    /**
//     * Starts preloading photos in the background.
//     */
//    public void preloadAvatarsInBackground(List<AvatarRequest> requests) {
//        ensureLoaderThread();
//
//        boolean preloadingNeeded = touchRequestedEntries(requests);
//        int totalTinyAvatarSize = touchTinyAvatars();
//
//        if (!preloadingNeeded) {
//            return;
//        }
//
//        requests = trimCache(requests, totalTinyAvatarSize);
//
//        mLoaderThread.startPreloading(requests);
//    }

//    /**
//     * Adjust the LRU order of the requested images that are already cached to prevent them
//     * from being evicted by preloading.
//     */
//    private boolean touchRequestedEntries(List<AvatarRequest> requests) {
//        boolean cacheMissed = false;
//        for (int i = requests.size() - 1; i >= 0; i--) {
//            AvatarRequest request = requests.get(i);
//            ImageHolder holder = mImageHolderCache.get(request);
//            if (holder != null) {
//                mImageHolderCache.put(request, holder);
//            } else {
//                cacheMissed = true;
//            }
//        }
//
//        return cacheMissed;
//    }

//    /**
//     * Moves tiny avatars to the top of the LRU order to try and keep them from being evicted.
//     * Caching tiny avatars is highly cost-effective.
//     *
//     * @return The total size of all tiny avatars in cache.
//     */
//    private int touchTinyAvatars() {
//        int totalSize = 0;
//
//        Iterator<Entry<ImageRequest, ImageHolder>> iterator =
//                mImageHolderCache.snapshot().entrySet().iterator();
//        while (iterator.hasNext()) {
//            Entry<ImageRequest, ImageHolder> entry = iterator.next();
//            ImageRequest request = entry.getKey();
//            if ((request instanceof AvatarRequest)
//                    && ((AvatarRequest) request).getSize() == AvatarRequest.TINY) {
//                ImageHolder holder = entry.getValue();
//                if (holder.bytes != null) {
//                    totalSize += holder.bytes.length;
//                }
//
//                mImageHolderCache.put(request, holder);
//            }
//        }
//
//        return totalSize;
//    }

//    /**
//     * Reduces the size of cache before preloading.
//     */
//    private List<AvatarRequest> trimCache(List<AvatarRequest> requests, int totalTinyAvatarSize) {
//        int preferredCacheSize = mImageHolderCacheRedZoneBytes;
//        int estimatedMemoryUse = totalTinyAvatarSize;
//        for (int i = 0; i < requests.size(); i++) {
//            if (estimatedMemoryUse >= mImageHolderCacheRedZoneBytes) {
//                trimCache(preferredCacheSize);
//                return requests.subList(0, i);
//            }
//
//            AvatarRequest request = requests.get(i);
//            ImageHolder holder = mImageHolderCache.get(request);
//            if (holder != null && holder.bytes != null) {
//                estimatedMemoryUse += holder.bytes.length;
//            } else {
//                int bytes = getEstimatedSizeInBytes(request);
//                preferredCacheSize -= bytes;
//                estimatedMemoryUse += bytes;
//            }
//        }
//
//        trimCache(preferredCacheSize);
//        return requests;
//    }

    /**
     * Shrinks cache to the desired size.
     */
    private void trimCache(int size) {
        Iterator<Entry<ImageRequest, ImageHolder>> iterator =
                mImageHolderCache.snapshot().entrySet().iterator();
        while (mImageHolderCache.size() > size && iterator.hasNext()) {
            mImageHolderCache.remove(iterator.next().getKey());
        }
    }

    /**
     * Evicts avatars that were requested but never loaded.  This will force them to be
     * requested again if needed.
     */
    public void refresh() {
        Iterator<ImageHolder> iterator = mImageHolderCache.snapshot().values().iterator();
        while (iterator.hasNext()) {
            ImageHolder holder = iterator.next();
            if (!holder.complete) {
                holder.fresh = false;
            }
        }
    }

    /**
     * Requests asynchronous photo loading for the specified request.
     *
     * @param consumer Image consumer
     * @param request The combination of URL, type and size.
     */
    public void loadImage(ImageConsumer consumer, ImageRequest request) {
        loadImage(consumer, request, true);
    }

    /**
     * Requests an asynchronous refresh of the image for the specified request.
     *
     * @param consumer Image consumer
     * @param request The combination of URL, type and size.
     */
    public void refreshImage(ImageConsumer consumer, ImageRequest request) {
        loadImage(consumer, request, false);
    }

    /**
     * Evicts all local images from the cache.
     */
    public void evictAllLocalImages() {
        Set<ImageRequest> iterator = mImageHolderCache.snapshot().keySet();
        for (ImageRequest request : iterator) {
            if (request instanceof LocalImageRequest) {
                mImageCache.remove(request);
                mImageHolderCache.remove(request);
            }
        }
    }

    private void loadImage(ImageConsumer consumer, ImageRequest request,
            boolean clearIfNotCached) {
        if (request.isEmpty()) {
            // No photo is needed
            consumer.setBitmap(null, false);
            notifyRequestComplete(request);
            mPendingRequests.remove(consumer);
        } else {
            boolean loaded = loadCachedImage(consumer, request, clearIfNotCached);
            if (loaded) {
                mPendingRequests.remove(consumer);
            } else {
                mPendingRequests.put(consumer, request);
                if (!mPaused) {
                    // Send a request to start loading photos
                    requestLoading();
                }
            }
        }
    }

    /**
     * Registers an avatar change listener.
     */
    public void registerAvatarChangeListener(OnAvatarChangeListener listener) {
        mAvatarListeners.add(listener);
    }

    /**
     * Unregisters an avatar change listener.
     */
    public void unregisterAvatarChangeListener(OnAvatarChangeListener listener) {
        mAvatarListeners.remove(listener);
    }

//    /**
//     * Sends a notification to all registered listeners that the avatar for the
//     * specified Gaia ID has changed.
//     */
//    public void notifyAvatarChange(String gaiaId) {
//        if (gaiaId == null) {
//            return;
//        }
//
//        ensureLoaderThread();
//        mLoaderThread.notifyAvatarChange(gaiaId);
//    }

    /**
     * Registers a media image change listener.
     */
    public void registerMediaImageChangeListener(OnMediaImageChangeListener listener) {
        mMediaImageListeners.add(listener);
    }

    /**
     * Unregisters a media image change listener.
     */
    public void unregisterMediaImageChangeListener(OnMediaImageChangeListener listener) {
        mMediaImageListeners.remove(listener);
    }

//    /**
//     * Sends a notification to all registered listeners that the media image for the
//     * specified URL has changed.
//     */
//    public void notifyMediaImageChange(MediaImageRequest request, byte[] imageBytes) {
//        ensureLoaderThread();
//        MediaImageChangeNotification notification = new MediaImageChangeNotification();
//        notification.request = request;
//        notification.imageBytes = imageBytes;
//        mLoaderThread.notifyMediaImageChange(notification);
//    }

    /**
     * Registers a remote image change listener.
     */
    public void registerRemoteImageChangeListener(OnRemoteImageChangeListener listener) {
        mRemoteImageListeners.add(listener);
    }

    /**
     * Unregisters a remote image change listener.
     */
    public void unregisterRemoteImageChangeListener(OnRemoteImageChangeListener listener) {
        mRemoteImageListeners.remove(listener);
    }

//    /**
//     * Sends a notification to all registered listeners that the remote image for the
//     * specified URL has changed.
//     */
//    public void notifyRemoteImageChange(ImageRequest request, byte[] imageBytes) {
//        ensureLoaderThread();
//        RemoteImageChangeNotification notification = new RemoteImageChangeNotification();
//        notification.request = request;
//        notification.imageBytes = imageBytes;
//        mLoaderThread.notifyRemoteImageChange(notification);
//    }

    /**
     * Registers an image request completion listener.
     */
    public void registerRequestCompleteListener(OnImageRequestCompleteListener listener) {
        mRequestCompleteListeners.add(listener);
    }

    /**
     * Unregisters an image request completion listener.
     */
    public void unregisterRequestCompleteListener(OnImageRequestCompleteListener listener) {
        mRequestCompleteListeners.remove(listener);
    }

    private void notifyRequestComplete(ImageRequest request) {
        for (OnImageRequestCompleteListener listener : mRequestCompleteListeners) {
            listener.onImageRequestComplete(request);
        }
    }

    /**
     * Checks if the photo is present in cache.  If so, sets the photo on the view.
     *
     * @return false if the photo needs to be (re)loaded from the provider.
     */
    private boolean loadCachedImage(ImageConsumer consumer, ImageRequest request,
            boolean clearIfNotCached) {
        ImageHolder holder = mImageHolderCache.get(request);
        if (holder == null) {
            if (clearIfNotCached) {
                // The bitmap has not been loaded - should display the placeholder image.
                consumer.setBitmap(null, true);
            }
            return false;
        }

        // Put this holder on top of the LRU list
        mImageHolderCache.put(request, holder);

        if (holder.bytes == null) {
            if (holder.complete) {
                consumer.setBitmap(null, false);
                notifyRequestComplete(request);
            } else {
                // The bitmap has not been loaded from server - should display a placeholder.
                consumer.setBitmap(null, true);
            }
            return holder.fresh;
        }

        // Optionally decode bytes into a bitmap.
        inflateImage(request, holder);

        Object image = holder.image;
        if (image instanceof Bitmap) {
            consumer.setBitmap((Bitmap) image, false);
        } else if (consumer instanceof DrawableConsumer) {
            ((DrawableConsumer)consumer).setDrawable((Drawable) image, false);
        } else if (image instanceof GifDrawable) {
            consumer.setBitmap(((GifDrawable)image).getFirstFrame(), false);
        } else if (image != null) {
            throw new UnsupportedOperationException("Cannot handle drawables of type "
                    + image.getClass());
        }

        notifyRequestComplete(request);

        // Put the bitmap in the LRU cache
        if (image != null && holder.fresh) {
            mImageCache.put(request, image);
        }

        // Soften the reference
        holder.image = null;

        return holder.fresh;
    }

//    /**
//     * Returns a photo from cache or null if it is not cached.  Does not trigger a load.
//     * Returns an empty byte array if the photo is known to be missing.
//     */
//    public byte[] getCachedAvatar(AvatarRequest request) {
//        ImageHolder holder = mImageHolderCache.get(request);
//        if (holder == null || !holder.fresh) {
//            return null;
//        }
//
//        if (holder.bytes == null) {
//            return EMPTY_ARRAY;
//        }
//
//        return holder.bytes;
//    }

    /**
     * If necessary, decodes bytes stored in the holder to Bitmap.  As long as the
     * bitmap is held either by {@link #mImageCache} or by a soft reference in
     * the holder, it will not be necessary to decode the bitmap.
     */
    private void inflateImage(ImageRequest request, ImageHolder holder) {
        if (holder.image != null) {
            return;
        }

        byte[] bytes = holder.bytes;
        if (bytes == null || bytes.length == 0) {
            return;
        }

        holder.image = mImageCache.get(request);
        if (holder.image != null) {
            return;
        }

        // Check the soft reference.  If will be retained if the bitmap is also
        // in the LRU cache, so we don't need to check the LRU cache explicitly.
        if (holder.imageRef != null) {
            holder.image = holder.imageRef.get();
            if (holder.image != null) {
                return;
            }
        }

        try {
            holder.image = ImageUtils.decodeMedia(bytes);
            if (holder.image == null) {
                holder.imageRef = null;
            } else if (sUseSoftReferences) {
                holder.imageRef = new SoftReference<Object>(holder.image);
            }
        } catch (OutOfMemoryError e) {
            // Do nothing - the photo will appear to be missing
        }
    }

    public void pause() {
        mPaused = true;
    }

    public void resume() {
        mPaused = false;
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
//                    ensureLoaderThread();
//                    mLoaderThread.requestLoading();
                }
                return true;
            }

            case MESSAGE_IMAGES_LOADED: {
                if (!mPaused) {
                    processLoadedImages();
                }
                return true;
            }

//            case MESSAGE_AVATAR_CHANGED: {
//                String gaiaId = (String) msg.obj;
//
//                evictImage(new AvatarRequest(gaiaId, AvatarRequest.TINY));
//                evictImage(new AvatarRequest(gaiaId, AvatarRequest.SMALL));
//                evictImage(new AvatarRequest(gaiaId, AvatarRequest.MEDIUM));
//
//                for (OnAvatarChangeListener listener : mAvatarListeners) {
//                    listener.onAvatarChanged(gaiaId);
//                }
//                return true;
//            }

            case MESSAGE_MEDIA_IMAGE_CHANGED: {
                MediaImageChangeNotification notification = (MediaImageChangeNotification) msg.obj;
                String url = notification.request.getUrl();
                for (ImageRequest request : mImageHolderCache.snapshot().keySet()) {
                    if (!request.equals(notification.request)
                        && (request instanceof MediaImageRequest)
                        && url.equals(((MediaImageRequest) request).getUrl())) {
                        evictImage(request);
                    }
                }

                for (OnMediaImageChangeListener listener : mMediaImageListeners) {
                    listener.onMediaImageChanged(url);
                }
                return true;
            }

            case MESSAGE_REMOTE_IMAGE_CHANGED: {
                final RemoteImageChangeNotification notification =
                        (RemoteImageChangeNotification) msg.obj;
                final ImageRequest notificationRequest = notification.request;
                final ImageHolder holder = mImageHolderCache.get(notificationRequest);
                final Object image = (holder != null) ? holder.image : null;

                for (OnRemoteImageChangeListener listener : mRemoteImageListeners) {
                    if (image instanceof Bitmap || image == null) {
                        listener.onRemoteImageChanged(notificationRequest, (Bitmap) image);
                    } else if (image instanceof GifDrawable) {
                        GifDrawable drawable = (GifDrawable) image;
                        if (listener instanceof OnRemoteDrawableChangeListener) {
                            ((OnRemoteDrawableChangeListener) listener).onRemoteImageChanged(
                                    notificationRequest, drawable);
                        } else {
                            listener.onRemoteImageChanged(
                                    notificationRequest, drawable.getFirstFrame());
                        }
                    } else {
                        throw new UnsupportedOperationException("Unsupported remote image type "
                                + image.getClass());
                    }
                }
                return true;
            }
        }
        return false;
    }

    private void evictImage(ImageRequest request) {
        mImageCache.remove(request);
        ImageHolder holder = mImageHolderCache.get(request);
        if (holder != null) {
            holder.fresh = false;
        }
    }

//    public void ensureLoaderThread() {
//        if (mLoaderThread == null) {
//            mLoaderThread = new LoaderThread(mContext.getContentResolver());
//            mLoaderThread.start();
//        }
//    }

    /**
     * Goes over pending loading requests and displays loaded photos.  If some of the
     * photos still haven't been loaded, sends another request for image loading.
     */
    private void processLoadedImages() {
        Iterator<ImageConsumer> iterator = mPendingRequests.keySet().iterator();
        while (iterator.hasNext()) {
            ImageConsumer consumer = iterator.next();
            ImageRequest request = mPendingRequests.get(consumer);
            boolean loaded = loadCachedImage(consumer, request, false);
            if (loaded) {
                iterator.remove();
            }
        }

        softenCache();

        if (!mPendingRequests.isEmpty()) {
            requestLoading();
        }
    }

    /**
     * Removes strong references to loaded images to allow them to be garbage collected
     * if needed.  Some of the images will still be retained by {@link #mImageCache}.
     */
    private void softenCache() {
        for (ImageHolder holder : mImageHolderCache.snapshot().values()) {
            holder.image = null;
        }
    }

    /**
     * Stores the supplied image in cache.
     */
    private void deliverImage(ImageRequest request, byte[] bytes, boolean available,
            boolean preloading) {
        ImageHolder holder = new ImageHolder(bytes, available);
        holder.fresh = true;

        // Unless this image is being preloaded, decode it right away while
        // we are still on the background thread.
        if (available && !preloading) {
            inflateImage(request, holder);
        }

        mImageHolderCache.put(request, holder);
    }

    /**
     * Populates an array of photo IDs that need to be loaded.
     */
    private void obtainRequestsToLoad(HashSet<ImageRequest> requests) {
        requests.clear();

        /*
         * Since the call is made from the loader thread, the map could be
         * changing during the iteration. That's not really a problem:
         * ConcurrentHashMap will allow those changes to happen without throwing
         * exceptions. Since we may miss some requests in the situation of
         * concurrent change, we will need to check the map again once loading
         * is complete.
         */
        Iterator<ImageRequest> iterator = mPendingRequests.values().iterator();
        while (iterator.hasNext()) {
            ImageRequest key = iterator.next();
            ImageHolder holder = mImageHolderCache.get(key);
            if (holder == null || !holder.fresh) {
                requests.add(key);
            }
        }
    }

//    /**
//     * The thread that performs loading of photos from the database.
//     */
//    private class LoaderThread extends HandlerThread implements Callback {
//        private static final int MESSAGE_PRELOAD_AVATARS = 0;
//        private static final int MESSAGE_CONTINUE_PRELOAD = 1;
//        private static final int MESSAGE_LOAD_IMAGES = 2;
//        private static final int MESSAGE_NOTIFY_AVATAR_CHANGE = 3;
//        private static final int MESSAGE_NOTIFY_MEDIA_IMAGE_CHANGE = 4;
//        private static final int MESSAGE_NOTIFY_REMOTE_IMAGE_CHANGE = 5;
//
//        /**
//         * A pause between preload batches that yields to the UI thread.
//         */
//        private static final int AVATAR_PRELOAD_DELAY = 50;
//
//        /**
//         * Number of photos to preload per batch.
//         */
//        private static final int PRELOAD_BATCH = 25;
//
//        private final HashSet<ImageRequest> mRequests = new HashSet<ImageRequest>();
////        private List<AvatarRequest> mPreloadRequests = new ArrayList<AvatarRequest>();
//
//        private Handler mLoaderThreadHandler;
//
//        private static final int PRELOAD_STATUS_NOT_STARTED = 0;
//        private static final int PRELOAD_STATUS_IN_PROGRESS = 1;
//        private static final int PRELOAD_STATUS_DONE = 2;
//
//        private int mPreloadStatus = PRELOAD_STATUS_NOT_STARTED;
//
//        public LoaderThread(ContentResolver resolver) {
//            super(LOADER_THREAD_NAME);
//        }
//
//        public void ensureHandler() {
//            if (mLoaderThreadHandler == null) {
//                mLoaderThreadHandler = new Handler(getLooper(), this);
//            }
//        }
//
////        /**
////         * Kicks off preloading of the photos on the background thread.
////         * Preloading will happen after a delay: we want to yield to the UI thread
////         * as much as possible.
////         * <p>
////         * If preloading is already complete, does nothing.
////         */
////        public void startPreloading(List<AvatarRequest> requests) {
////            ensureHandler();
////
////            mLoaderThreadHandler.sendMessage(mLoaderThreadHandler.obtainMessage(
////                    MESSAGE_PRELOAD_AVATARS, requests));
////        }
//
//        /**
//         * Kicks off preloading of the next batch of photos on the background thread.
//         * Preloading will happen after a delay: we want to yield to the UI thread
//         * as much as possible.
//         * <p>
//         * If preloading is already complete, does nothing.
//         */
//        public void continuePreloading() {
//            if (mPreloadStatus == PRELOAD_STATUS_DONE) {
//                return;
//            }
//
//            ensureHandler();
//            if (mLoaderThreadHandler.hasMessages(MESSAGE_LOAD_IMAGES)) {
//                return;
//            }
//
//            mLoaderThreadHandler.sendEmptyMessageDelayed(MESSAGE_CONTINUE_PRELOAD,
//                    AVATAR_PRELOAD_DELAY);
//        }
//
//        /**
//         * Sends a message to this thread to load requested photos.
//         */
//        public void requestLoading() {
//            ensureHandler();
//            mLoaderThreadHandler.removeMessages(MESSAGE_CONTINUE_PRELOAD);
//            mLoaderThreadHandler.sendEmptyMessage(MESSAGE_LOAD_IMAGES);
//        }
//
//        /**
//         * Channels a change notification event through the loader thread to ensure
//         * proper concurrency.
//         */
//        public void notifyAvatarChange(String gaiaId) {
//            ensureHandler();
//            Message msg = mLoaderThreadHandler.obtainMessage(MESSAGE_NOTIFY_AVATAR_CHANGE, gaiaId);
//            mLoaderThreadHandler.sendMessage(msg);
//        }
//
//        /**
//         * Channels a change notification event through the loader thread to ensure
//         * proper concurrency.
//         */
//        public void notifyMediaImageChange(MediaImageChangeNotification notification) {
//            ensureHandler();
//            Message msg = mLoaderThreadHandler.obtainMessage(
//                    MESSAGE_NOTIFY_MEDIA_IMAGE_CHANGE, notification);
//            mLoaderThreadHandler.sendMessage(msg);
//        }
//
//        /**
//         * Channels a change notification event through the loader thread to ensure
//         * proper concurrency.
//         */
//        public void notifyRemoteImageChange(RemoteImageChangeNotification notification) {
//            ensureHandler();
//            Message msg = mLoaderThreadHandler.obtainMessage(
//                    MESSAGE_NOTIFY_REMOTE_IMAGE_CHANGE, notification);
//            mLoaderThreadHandler.sendMessage(msg);
//        }
//
//        /**
//         * Receives the above message, loads photos and then sends a message
//         * to the main thread to process them.
//         */
//        @Override
//        public boolean handleMessage(Message msg) {
//            try {
//                switch (msg.what) {
////                    case MESSAGE_PRELOAD_AVATARS:
////                        @SuppressWarnings("unchecked")
////                        List<AvatarRequest> requests = (List<AvatarRequest>) msg.obj;
////                        mPreloadRequests.clear();
////                        mPreloadRequests.addAll(requests);
////                        mPreloadStatus = PRELOAD_STATUS_NOT_STARTED;
////                        preloadAvatarsInBackground();
////                        break;
//                    case MESSAGE_CONTINUE_PRELOAD:
////                        preloadAvatarsInBackground();
//                        break;
//                    case MESSAGE_LOAD_IMAGES:
//                        loadImagesInBackground();
//                        break;
//                    case MESSAGE_NOTIFY_AVATAR_CHANGE:
//                        sendMessageAvatarChange((String) msg.obj);
//                        break;
//                    case MESSAGE_NOTIFY_MEDIA_IMAGE_CHANGE:
//                        sendMessageMediaImageChange((MediaImageChangeNotification) msg.obj);
//                        break;
//                    case MESSAGE_NOTIFY_REMOTE_IMAGE_CHANGE:
//                        sendMessageRemoteImageChange((RemoteImageChangeNotification) msg.obj);
//                        break;
//                }
//                return true;
//            } catch (Throwable t) {
//                Thread.getDefaultUncaughtExceptionHandler()
//                    .uncaughtException(Thread.currentThread(), t);
//                return false;
//            }
//        }
//
////        /**
////         * The first time it is called, figures out which photos need to be preloaded.
////         * Each subsequent call preloads the next batch of photos and requests
////         * another cycle of preloading after a delay.  The whole process ends when
////         * we either run out of photos to preload or fill up cache.
////         */
////        private void preloadAvatarsInBackground() {
////            if (mPreloadStatus == PRELOAD_STATUS_DONE) {
////                return;
////            }
////
////            if (mPreloadStatus == PRELOAD_STATUS_NOT_STARTED) {
////                if (mPreloadRequests.isEmpty()) {
////                    mPreloadStatus = PRELOAD_STATUS_DONE;
////                } else {
////                    mPreloadStatus = PRELOAD_STATUS_IN_PROGRESS;
////                }
////                continuePreloading();
////                return;
////            }
////
////            if (mImageHolderCache.size() > mImageHolderCacheRedZoneBytes) {
////                mPreloadStatus = PRELOAD_STATUS_DONE;
////                return;
////            }
////
////            mRequests.clear();
////
////            int count = 0;
////            int preloadSize = mPreloadRequests.size();
////            while (preloadSize > 0 && mRequests.size() < PRELOAD_BATCH) {
////                preloadSize--;
////                AvatarRequest request = mPreloadRequests.get(preloadSize);
////                mPreloadRequests.remove(preloadSize);
////
////                if (mImageHolderCache.get(request) == null) {
////                    mRequests.add(request);
////                    count++;
////                }
////            }
////
////            loadImagesFromDatabase(true);
////
////            if (preloadSize == 0) {
////                mPreloadStatus = PRELOAD_STATUS_DONE;
////            }
////
////            if (EsLog.isLoggable(TAG, Log.INFO)) {
////                Log.v(TAG, "Preloaded " + count + " avatars. "
////                        + "Cache size (bytes): " + mImageHolderCache.size());
////            }
////
////            // Ask to preload the next batch.
////            continuePreloading();
////        }
//
//        /**
//         * Forwards the change notification event to the main thread.
//         */
//        private void sendMessageAvatarChange(String gaiaId) {
//            Message msg = mMainThreadHandler.obtainMessage(MESSAGE_AVATAR_CHANGED, gaiaId);
//            mMainThreadHandler.sendMessage(msg);
//        }
//
//        /**
//         * Forwards the change notification event to the main thread.
//         */
//        private void sendMessageMediaImageChange(MediaImageChangeNotification notification) {
//            deliverImage(notification.request, notification.imageBytes, true, false);
//            Message msg = mMainThreadHandler.obtainMessage(
//                    MESSAGE_MEDIA_IMAGE_CHANGED, notification);
//            mMainThreadHandler.sendMessage(msg);
//        }
//
//        /**
//         * Forwards the change notification event to the main thread.
//         */
//        private void sendMessageRemoteImageChange(RemoteImageChangeNotification notification) {
//            deliverImage(notification.request, notification.imageBytes, true, false);
//            Message msg = mMainThreadHandler.obtainMessage(
//                    MESSAGE_REMOTE_IMAGE_CHANGED, notification);
//            mMainThreadHandler.sendMessage(msg);
//        }
//
//        /**
//         * Loads photos from the database, puts them in cache and then notifies the UI thread
//         * that they have been loaded.
//         */
//        private void loadImagesInBackground() {
//            obtainRequestsToLoad(mRequests);
//            loadImagesFromDatabase(false);
//            continuePreloading();
//        }
//
////        /**
////         * Loads photos from the database, puts them in cache and then notifies the UI thread
////         * that they have been loaded.
////         */
////        private void loadImagesFromDatabase(boolean preloading) {
////            int count = mRequests.size();
////            if (count == 0) {
////                return;
////            }
////
////            // Remove loaded photos from the preload queue: we don't want
////            // the preloading process to load them again.
////            if (!preloading && mPreloadStatus == PRELOAD_STATUS_IN_PROGRESS) {
////                mPreloadRequests.removeAll(mRequests);
////                if (mPreloadRequests.isEmpty()) {
////                    mPreloadStatus = PRELOAD_STATUS_DONE;
////                }
////            }
////
////            ArrayList<AvatarRequest> avatarRequests = null;
////            ArrayList<MediaImageRequest> mediaRequests = null;
////            ArrayList<EventThemeImageRequest> eventThemeRequests = null;
////            ArrayList<ImageRequest> remoteRequests = null;
////
////            for (ImageRequest request : mRequests) {
////                if (request instanceof AvatarRequest) {
////                    if (avatarRequests == null) {
////                        avatarRequests = new ArrayList<AvatarRequest>();
////                    }
////                    avatarRequests.add((AvatarRequest) request);
////                } else if (request instanceof MediaImageRequest) {
////                    if (mediaRequests == null) {
////                        mediaRequests = new ArrayList<MediaImageRequest>();
////                    }
////                    mediaRequests.add((MediaImageRequest) request);
////                } else if (request instanceof EventThemeImageRequest) {
////                    if (eventThemeRequests == null) {
////                        eventThemeRequests = new ArrayList<EventThemeImageRequest>();
////                    }
////                    eventThemeRequests.add((EventThemeImageRequest) request);
////                } else {
////                    if (remoteRequests == null) {
////                        remoteRequests = new ArrayList<ImageRequest>();
////                    }
////                    remoteRequests.add(request);
////                }
////            }
////
////            if (mediaRequests != null) {
////                Map<MediaImageRequest, byte[]> avatars = EsPostsData.loadMedia(
////                        mContext, mediaRequests);
////
////                for (Entry<MediaImageRequest, byte[]> entry : avatars.entrySet()) {
////                    MediaImageRequest request = entry.getKey();
////                    deliverImage(request, entry.getValue(), true, preloading);
////                    mRequests.remove(request);
////                }
////            }
////
////            if (avatarRequests != null) {
////                Map<AvatarRequest, byte[]> avatars = EsAvatarData.loadAvatars(
////                        mContext, avatarRequests);
////
////                for (Entry<AvatarRequest, byte[]> entry : avatars.entrySet()) {
////                    AvatarRequest request = entry.getKey();
////                    deliverImage(request, entry.getValue(), true, preloading);
////                    mRequests.remove(request);
////                }
////            }
////
////            if (eventThemeRequests != null) {
////                for (EventThemeImageRequest request : eventThemeRequests) {
////                    byte[] themeBytes = EsEventData.loadEventTheme(mContext, request);
////                    if (themeBytes != null) {
////                        deliverImage(request, themeBytes, true, false);
////                        mRequests.remove(request);
////                    }
////                }
////            }
////
////            // NOTE: Do not use the same pattern as other images for the following image.
////            // Since all of these photos are "local" [either because they're physically
////            // stored on the device or because they're available through the Picasa
////            // content provider], there is no need to store them in the database. Just
////            // throw all of the requests into a loader thread and be done with it.
////            if (remoteRequests != null) {
////                final int requestCount = remoteRequests.size();
////                for (int i = 0; i < requestCount; i++) {
////                    final ImageRequest request = remoteRequests.get(i);
////
////                    // Only if we still need to load
////                    if (mPendingRequests.containsValue(request)) {
////                        RemoteImageLoader.downloadImage(mContext, request);
////                    }
////                }
////            }
////
////            // Remaining photos were not found in the database - mark the cache accordingly.
////            for (ImageRequest request : mRequests) {
////                deliverImage(request, null, false, preloading);
////            }
////
////            mMainThreadHandler.sendEmptyMessage(MESSAGE_IMAGES_LOADED);
////        }
//    }
}
