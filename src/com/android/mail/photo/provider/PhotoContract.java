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

package com.android.mail.photo.provider;

import android.provider.BaseColumns;

public final class PhotoContract {
    /** Columns for the view {@link #PHOTO_VIEW} */
    public static interface PhotoViewColumns extends BaseColumns {
        public static final String PHOTO_ID = "photo_id";
        public static final String URI = "uri";
        public static final String OWNER_ID = "owner_id";
        public static final String TITLE = "title";
        public static final String VIDEO_DATA = "video_data";
        public static final String ALBUM_NAME = "album_name";
    }

    public static interface PhotoQuery {
        /** Projection of the returned cursor */
        public final static String[] PROJECTION = {
            PhotoViewColumns._ID,
            PhotoViewColumns.URI,
            PhotoViewColumns.PHOTO_ID,
            PhotoViewColumns.OWNER_ID,
            PhotoViewColumns.TITLE,
            PhotoViewColumns.VIDEO_DATA,
            PhotoViewColumns.ALBUM_NAME,
        };

        public final static int INDEX_ID = 0;
        public final static int INDEX_URI = 1;
        public final static int INDEX_PHOTO_ID = 2;
        public final static int INDEX_OWNER_ID = 3;
        public final static int INDEX_TITLE = 4;
        public final static int INDEX_VIDEO_DATA = 5;
        public final static int INDEX_ALBUM_NAME = 6;
    }
}
