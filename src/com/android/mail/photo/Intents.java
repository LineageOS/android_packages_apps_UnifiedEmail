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

package com.android.mail.photo;

import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;

import com.android.mail.photo.fragments.PhotoViewFragment;
import com.android.mail.photo.loaders.PhotoCursorLoader;

/**
 * Build intents to start app activities
 */
public class Intents {
    // Logging
    private static final String TAG = "Intents";

    // Intent extras
    public static final String EXTRA_PHOTO_INDEX = "photo_index";
    public static final String EXTRA_PHOTO_ID = "photo_id";
    public static final String EXTRA_PHOTOS_URI = "photos_uri";
    public static final String EXTRA_RESOLVED_PHOTO_URI = "resolved_photo_uri";
    public static final String EXTRA_ALBUM_NAME = "album_name";
    public static final String EXTRA_OWNER_ID = "owner_id";
    public static final String EXTRA_TAG = "tag";
    public static final String EXTRA_SHOW_PHOTO_ONLY = "show_photo_only";
    public static final String EXTRA_NOTIFICATION_ID = "notif_id";
    public static final String EXTRA_REFRESH = "refresh";
    public static final String EXTRA_PAGE_HINT = "page_hint";

    /**
     * Gets a photo view intent builder to display the photos from phone activity.
     *
     * @param context The context
     * @return The intent builder
     */
    public static PhotoViewIntentBuilder newPhotoViewActivityIntentBuilder(Context context) {
        return new PhotoViewIntentBuilder(context, PhotoViewActivity.class);
    }

    /**
     * Gets a photo view intent builder to display the photo view fragment
     *
     * @param context The context
     * @return The intent builder
     */
    public static PhotoViewIntentBuilder newPhotoViewFragmentIntentBuilder(Context context) {
        return new PhotoViewIntentBuilder(context, PhotoViewFragment.class);
    }

    /** Gets a new photo view intent builder */
    public static PhotoViewIntentBuilder newPhotoViewIntentBuilder(Context context, Class<?> cls) {
        return new PhotoViewIntentBuilder(context, cls);
    }

    /** Builder to create a photo view intent */
    public static class PhotoViewIntentBuilder {
        private final Intent mIntent;

        /** The id of the photo being displayed */
        private long mPhotoId;
        /** The name of the album being displayed */
        private String mAlbumName;
        /** The ID of the photo to force load */
        private Long mForceLoadId;
        /** The ID of the notification */
        private String mNotificationId;
        /** A hint for the number of pages to initially load */
        private Integer mPageHint;
        /** The index of the photo to show */
        private Integer mPhotoIndex;
        /** Whether or not to show the photo only [eg don't show comments, etc...] */
        private Boolean mPhotoOnly;
        /** The URI of the group of photos to display */
        private String mPhotosUri;
        /** The URL of the photo to display */
        private String mResolvedPhotoUri;

        private PhotoViewIntentBuilder(Context context, Class<?> cls) {
            mIntent = new Intent(context, cls);
        }

        public PhotoViewIntentBuilder setPhotoId(long photoId) {
            mPhotoId = photoId;
            return this;
        }

        /** Sets the album name */
        public PhotoViewIntentBuilder setAlbumName(String albumName) {
            mAlbumName = albumName;
            return this;
        }

        /** Sets the photo ID to force load */
        public PhotoViewIntentBuilder setForceLoadId(Long forceLoadId) {
            mForceLoadId = forceLoadId;
            return this;
        }

        /** Sets the notification ID */
        public PhotoViewIntentBuilder setNotificationId(String notificationId) {
            mNotificationId = notificationId;
            return this;
        }

        /** Sets the page hint */
        public PhotoViewIntentBuilder setPageHint(Integer pageHint) {
            mPageHint = pageHint;
            return this;
        }

        /** Sets the photo index */
        public PhotoViewIntentBuilder setPhotoIndex(Integer photoIndex) {
            mPhotoIndex = photoIndex;
            return this;
        }

        /** Sets whether to show the photo only */
        public PhotoViewIntentBuilder setPhotoOnly(Boolean photoOnly) {
            mPhotoOnly = photoOnly;
            return this;
        }

        /** Sets the photos URI */
        public PhotoViewIntentBuilder setPhotosUri(String photosUri) {
            mPhotosUri = photosUri;
            return this;
        }

        /** Sets the resolved photo URI. This method is for the case
         *  where the URI given to {@link PhotoViewActivity} points directly
         *  to a single image and does not need to be resolved via a query
         *  to the {@link ContentProvider}. If this value is set, it supersedes
         *  {@link #setPhotosUri(String)}. */
        public PhotoViewIntentBuilder setResolvedPhotoUri(String resolvedPhotoUri) {
            mResolvedPhotoUri = resolvedPhotoUri;
            return this;
        }

        /** Build the intent */
        public Intent build() {
            if (TextUtils.isEmpty(mPhotosUri) && TextUtils.isEmpty(mResolvedPhotoUri)) {
                throw new IllegalArgumentException("Either PhotosUri or ResolvedPhotoUri must be set.");
            }

            mIntent.setAction(Intent.ACTION_VIEW);

            mIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);

            if (mAlbumName != null) {
                mIntent.putExtra(EXTRA_ALBUM_NAME, mAlbumName);
            }

            if (mForceLoadId != null) {
                mIntent.putExtra(EXTRA_REFRESH, (long) mForceLoadId);
            }

            if (mNotificationId != null) {
                mIntent.putExtra(EXTRA_NOTIFICATION_ID, mNotificationId);
            }

            if (mPageHint != null) {
                mIntent.putExtra(EXTRA_PAGE_HINT, (int) mPageHint);
            } else {
                mIntent.putExtra(EXTRA_PAGE_HINT, PhotoCursorLoader.LOAD_LIMIT_UNLIMITED);
            }

            if (mPhotoIndex != null) {
                mIntent.putExtra(EXTRA_PHOTO_INDEX, (int) mPhotoIndex);
            }

            if ((mPhotoOnly != null && mPhotoOnly)) {
                mIntent.putExtra(EXTRA_SHOW_PHOTO_ONLY, true);
            }

            if (mPhotosUri != null) {
                mIntent.putExtra(EXTRA_PHOTOS_URI, mPhotosUri);
            }

            if (mResolvedPhotoUri != null) {
                mIntent.putExtra(EXTRA_RESOLVED_PHOTO_URI, mResolvedPhotoUri);
            }

            return mIntent;
        }
    }
}
