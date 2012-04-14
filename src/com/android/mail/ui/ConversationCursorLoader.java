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
import com.android.mail.providers.Account;
import com.android.mail.providers.UIProvider;
import com.android.mail.providers.UIProvider.AccountCapabilities;

public class ConversationCursorLoader extends AsyncTaskLoader<ConversationCursor> {
    private final Uri mUri;
    private final String[] mProjection;
    private final Activity mActivity;
    private final boolean mInitialConversationLimit;

    public ConversationCursorLoader(Activity activity, Account account, String[] projection,
            Uri uri) {
        super(activity);
        mProjection = projection;
        mUri = uri;
        mActivity = activity;
        mInitialConversationLimit =
                account.supportsCapability(AccountCapabilities.INITIAL_CONVERSATION_LIMIT);
        // Initialize the state of the conversation cursor
        ConversationCursor.initialize(mActivity, mInitialConversationLimit);
    }

    @Override
    public ConversationCursor loadInBackground() {
        return ConversationCursor.create(mActivity, UIProvider.ConversationColumns.URI, mUri,
                mProjection);
    }

    @Override
    protected void onReset() {
        stopLoading();
    }

    @Override
    protected void onStartLoading() {
        forceLoad();
        ConversationCursor.resume();
    }

    @Override
    protected void onStopLoading() {
        cancelLoad();
        ConversationCursor.pause();
    }
}
