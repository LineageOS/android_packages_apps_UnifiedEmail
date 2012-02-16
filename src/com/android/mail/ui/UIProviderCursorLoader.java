/*
 * Copyright (C) 2012 Google Inc.
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
package com.android.mail.ui;

import android.content.AsyncTaskLoader;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

public class UIProviderCursorLoader extends AsyncTaskLoader<Cursor> {
    private String mUri;
    private String[] mProjection;

    public UIProviderCursorLoader(Context context, String[] projection, String uri) {
        super(context);
        mProjection = projection;
        mUri = uri;
    }

    @Override
    public Cursor loadInBackground() {
        return getContext().getContentResolver().query(Uri.parse(mUri), mProjection, null, null,
                null);
    }

    @Override
    protected void onReset() {
        stopLoading();
    }

    @Override
    protected void onStartLoading() {
        forceLoad();
    }

    @Override
    protected void onStopLoading() {
        cancelLoad();
    }
}
