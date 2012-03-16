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

import android.app.Activity;
import android.content.AsyncTaskLoader;
import android.net.Uri;

import com.android.mail.browse.ConversationCursor;
import com.android.mail.providers.UIProvider;

public class ConversationCursorLoader extends AsyncTaskLoader<ConversationCursor> {
    private Uri mUri;
    private String[] mProjection;
    private Activity mActivity;

    public ConversationCursorLoader(Activity activity, String[] projection, Uri uri) {
        super(activity);
        mProjection = projection;
        mUri = uri;
        mActivity = activity;

        // Initialize the state of the conversation cursor
        ConversationCursor.initialize(mActivity);
    }

    @Override
    public ConversationCursor loadInBackground() {
        return ConversationCursor.create(mActivity, UIProvider.ConversationColumns.URI, mUri,
                mProjection, null, null, null);
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
