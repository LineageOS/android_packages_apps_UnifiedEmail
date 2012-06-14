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

package com.android.mail.photo.loaders;

import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.util.Log;

import com.android.mail.photo.Pageable;

/**
 * Loader for all types of photo lists. This will load a set of photos for an album id,
 * of a specific user, for a circle id or a stream id. See {@link AlbumViewFragment}
 * for the algorithm that determines which type of album data will be retrieved.
 */
public abstract class PhotoCursorLoader extends BaseCursorLoader implements Pageable {
    private final static String TAG = "PhotoCursorLoader";

    /** Load an unlimited number of rows */
    public static final int LOAD_LIMIT_UNLIMITED = -1;

    private static final String DEFAULT_SORT_ORDER = "";

    /** Whether or not a content observer has been registered */
    private boolean mObserverRegistered;
    /** Observer that force loads the cursor if the observed uri is notified */
    private final ForceLoadContentObserver mObserver = new ForceLoadContentObserver();
    /** Whether or not this cursor is a paging cursor */
    private final boolean mPaging;
    /** The initial number of pages to load */
    private final int mInitialPageCount;

    /** The total number of rows to load */
    private int mLoadLimit = CURSOR_PAGE_SIZE;
    /** Whether or not there are more rows to load */
    private boolean mHasMore;
    /** Whether or not rows are in the process of loading */
    private boolean mIsLoadingMore;
    /** Whether or not the cursor is pageable */
    private boolean mPageable;
    
    private final Uri mPhotosUri;

    public PhotoCursorLoader(Context context, Uri photosUri,
            boolean paging, int initialPageCount) {
        super(context, getNotificationUri());
        mPaging = paging;
        mPageable = paging;
        mInitialPageCount = initialPageCount;
        mLoadLimit = (mPageable && initialPageCount != LOAD_LIMIT_UNLIMITED)
                ? initialPageCount * CURSOR_PAGE_SIZE : LOAD_LIMIT_UNLIMITED;
        mPhotosUri = photosUri;
    }

    @Override
    public Cursor esLoadInBackground() {
        if (getUri() == null) {
            Log.w(TAG, "load NULL URI; return empty cursor");
            return new MatrixCursor(getProjection());
        }

        final int loadLimit = mLoadLimit;
        final boolean changeSortOrder = mPageable && mLoadLimit != LOAD_LIMIT_UNLIMITED;
        final String origSortOrder = getSortOrder();

        // Make sure we're sorting photos correctly
        if (origSortOrder == null) {
            setSortOrder(getDefaultSortOrder());
        }

        // Optionally change the sort order to add a LIMIT / OFFSET to the query
        if (changeSortOrder) {
            final String sortOrder = getSortOrder();

            // TODO(toddke) Make the limits parameters to enable query caching
            setSortOrder((sortOrder != null ? sortOrder : "") + " LIMIT 0, " + loadLimit);
        }

        return super.esLoadInBackground();
    }

    @Override
    public void loadMore() {
        if (mPageable && mHasMore) {
            mLoadLimit += CURSOR_PAGE_SIZE;
            mIsLoadingMore = true;
            onContentChanged();
        }
    }

    @Override
    public boolean hasMore() {
        return mPageable && mHasMore;
    }

    @Override
    protected void onStartLoading() {
        if (!mObserverRegistered) {
            getContext().getContentResolver().registerContentObserver(mPhotosUri,
                    false, mObserver);
            mObserverRegistered = true;
        }
        super.onStartLoading();
    }

    @Override
    protected void onAbandon() {
        if (mObserverRegistered) {
            getContext().getContentResolver().unregisterContentObserver(mObserver);
            mObserverRegistered = false;
        }
        super.onAbandon();
    }

    /** Gets whether or not the loader is in the process of loading more data */
    public boolean isLoadingMore() {
        return mPageable && mIsLoadingMore;
    }

    /** Gets the current page */
    @Override
    public int getCurrentPage() {
        return (mPageable && mLoadLimit != LOAD_LIMIT_UNLIMITED)
                ? (mLoadLimit / CURSOR_PAGE_SIZE) : LOAD_LIMIT_UNLIMITED;
    }

    /** Reset paging to the default state */
    public void resetPaging() {
        mLoadLimit = (mPageable && mInitialPageCount != LOAD_LIMIT_UNLIMITED)
                ? mInitialPageCount * CURSOR_PAGE_SIZE : LOAD_LIMIT_UNLIMITED;
        mHasMore = false;
        mPageable = mPaging;
    }

//    /**
//     * Performs a network request. The actual request depends upon the values passed in.
//     */
//    private void doNetworkRequest() {
//        if (mNetworkRequestMade /*&& !isLoadingCirclePhotos()*/) {
//            return;
//        }
//        mNetworkRequestMade = true;
//
//        final TacoTruckOperation eso = new TacoTruckOperation(getContext(), mAccount, null, null);
//        if (mStreamId != null && !IGNORE_STREAM_ID.equals(mStreamId)) {
//            eso.getStreamPhotos(mOwnerGaiaId, mStreamId, 0, EsPhotosData.MAX_STREAM_PHOTOS_COUNT);
//        } else if (mEventId != null) {
//            // TODO(toddke) Implement network request for getting event photos
//        } else if (mPhotoOfUserGaiaId != null) {
//            eso.getPhotosOfUser(mPhotoOfUserGaiaId);
//        } else if (mAlbumId != null) {
//            eso.getAlbum(mOwnerGaiaId, mAlbumId);
//        } else if (mActivityId != null) {
//            eso.getActivityPhotos(mActivityId);
//        } else {
//            eso.getPhotoConsumptionStream(mCircleId, EsPhotosData.CIRCLE_LIST_PHOTO_COUNT,
//                    mCircleOffset);
//        }
//        eso.start();
//
//        // No need to worry about any error condition. If the data cannot be fetched, we will
//        // display a generic "no photos available" message.
//    }

    /**
     * Returns a URI that can be used to load the cursor. May return {@code null} if a cursor
     * cannot be loaded for the requested data.
     */
    final Uri getLoaderUri() {
//        final Uri notificationUri = getNotificationUri(mOwnerGaiaId, mAlbumId, mCircleId,
//                mPhotoOfUserGaiaId, mStreamId, mActivityId, mEventId, mPhotoUrl);
//        final Uri loaderUri;
//
//        if (notificationUri != null) {
//            loaderUri = EsProvider.appendAccountParameter(notificationUri, mAccount);
//        } else {
//            loaderUri = null;
//        }
//        return loaderUri;

        return mPhotosUri;
    }

    /**
     * Returns the default sort order for this loader. Can be used to extend the default ordering
     * of the results.
     */
    final String getDefaultSortOrder() {
//        if (mAlbumId != null) {
//            return ALBUM_SORT_ORDER;
//        } else if (mActivityId != null) {
//            return ACTIVITY_SORT_ORDER;
//        } else if (mEventId != null) {
//            return EVENT_SORT_ORDER;
//        } else if (isLoadingCirclePhotos()) {
//            return CIRCLE_SORT_ORDER;
//        }
        return DEFAULT_SORT_ORDER;
    }

//    /**
//     * Returns whether or not we're loading photos for a circle [including the consumption stream].
//     */
//    private boolean isLoadingCirclePhotos() {
//        return (mStreamId == null) && (mPhotoOfUserGaiaId == null) && (mAlbumId == null) &&
//                (mActivityId == null) && (mPhotoUrl == null);
//    }

    /**
     * Returns a notification URI depending upon the values passed in.
     */
    private static Uri getNotificationUri() {
//        final Uri notificationUri;
//
//        if (streamId != null && !IGNORE_STREAM_ID.equals(streamId)) {
//            if (ownerGaiaId == null) {
//                Log.w(TAG, "Viewing stream photos w/o a valid owner GAIA ID");
//                notificationUri = null;
//            } else {
//                Uri.Builder builder = EsProvider.PHOTO_BY_STREAM_ID_AND_OWNER_ID_URI.buildUpon();
//                notificationUri =
//                        Uri.withAppendedPath(builder.appendPath(streamId).build(), ownerGaiaId);
//            }
//        } else if (eventId != null) {
//            notificationUri =
//                    Uri.withAppendedPath(EsProvider.PHOTO_BY_EVENT_ID_URI, eventId);
//        } else if (photoOfUserId != null) {
//            notificationUri =
//                    Uri.withAppendedPath(EsProvider.PHOTO_OF_USER_ID_URI, photoOfUserId);
//        } else if (albumId != null) {
//            if (ownerGaiaId == null) {
//                Log.w(TAG, "Viewing album photos w/o a valid owner GAIA ID");
//                notificationUri = null;
//            } else {
//                notificationUri =
//                        ContentUris.withAppendedId(EsProvider.PHOTO_BY_ALBUM_URI, albumId);
//            }
//        } else if (circleId != null) {
//            notificationUri =
//                    EsProvider.PHOTO_BY_CIRCLE_ID_URI.buildUpon().appendPath(circleId).build();
//        } else if (activityId != null) {
//            Uri.Builder builder = EsProvider.PHOTO_BY_ACTIVITY_ID_URI.buildUpon();
//            notificationUri = builder.appendPath(activityId).build();
//        } else if (photoUrl != null) {
//            notificationUri = null;
//        } else {
//            notificationUri = EsProvider.PHOTO_BY_NULL_CIRCLE_ID_URI;
//        }
//
//        return notificationUri;


        return null;
    }
}
