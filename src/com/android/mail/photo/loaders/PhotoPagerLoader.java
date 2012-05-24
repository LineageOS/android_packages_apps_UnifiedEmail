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

import com.android.mail.photo.provider.PhotoContract.PhotoQuery;

/**
 * Loader for a set of photo IDs.
 */
public class PhotoPagerLoader extends PhotoCursorLoader {

    public PhotoPagerLoader(
            Context context, Uri photosUri, int pageHint) {
        super(context, photosUri, pageHint != LOAD_LIMIT_UNLIMITED, pageHint);
    }

    @Override
    public Cursor esLoadInBackground() {
        Cursor returnCursor = null;

        final Uri loaderUri = getLoaderUri();

        if (true) {
            returnCursor = new MatrixCursor(PhotoQuery.PROJECTION);
            ((MatrixCursor) returnCursor).newRow()
            .add(0)             // _id
            .add(loaderUri.toString());
            return returnCursor;
        }

        setUri(loaderUri);
        setProjection(PhotoQuery.PROJECTION);
        returnCursor = super.esLoadInBackground();

        if (returnCursor == null || returnCursor.getCount() == 0) {
            returnCursor.close();
            returnCursor = new MatrixCursor(PhotoQuery.PROJECTION);
            ((MatrixCursor) returnCursor).newRow()
            .add(0)             // _id
            .add(loaderUri.toString());      // url
        }

        return returnCursor;
    }
}
