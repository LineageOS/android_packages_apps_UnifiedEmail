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

package com.android.mail.browse;

import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.mail.ConversationListContext;
import com.android.mail.R;
import com.android.mail.providers.Folder;
import com.android.mail.providers.UIProvider;
import com.android.mail.providers.UIProvider.LastSyncResult;
import com.android.mail.utils.Utils;

public class ConversationListFooterView extends LinearLayout implements View.OnClickListener {
    private View mLoading;
    private View mNetworkError;
    private View mLoadMore;
    private View mRetry;
    private TextView mErrorText;
    private FolderSyncTask mFolderSyncTask;

    public ConversationListFooterView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mLoading = findViewById(R.id.loading);
        mNetworkError = findViewById(R.id.network_error);
        mLoadMore = findViewById(R.id.load_more);
        mLoadMore.setOnClickListener(this);
        mRetry = findViewById(R.id.retry_button);
        mRetry.setOnClickListener(this);
        mErrorText = (TextView)findViewById(R.id.error_text);
    }

    public void onClick(View v) {
        int id = v.getId();
        Folder f = (Folder) v.getTag();
        Uri uri = null;
        switch (id) {
            case R.id.retry_button:
                if (f != null) {
                    uri = f.refreshUri;
                }
                break;
            case R.id.load_more:
                if (f != null && f.loadMoreUri != null) {
                    uri = f.loadMoreUri;
                }
                break;
        }
        if (uri != null) {
            if (mFolderSyncTask != null) {
                mFolderSyncTask.cancel(true);
            }
            mFolderSyncTask = new FolderSyncTask(uri);
            mFolderSyncTask.execute();
        }
    }

    /**
     * Update the view to reflect the new folder status.
     */
    public void updateStatus(final Folder folder, int count) {
        mRetry.setTag(folder);
        mLoadMore.setTag(folder);
        if (folder.isSyncInProgress()) {
            mLoading.setVisibility(View.VISIBLE);
            mNetworkError.setVisibility(View.GONE);
            mLoadMore.setVisibility(View.GONE);
        } else if (folder.lastSyncResult != UIProvider.LastSyncResult.SUCCESS) {
            mNetworkError.setVisibility(View.VISIBLE);
            CharSequence error = Utils.getSyncStatusText(getContext(), folder.lastSyncResult);
            if (!TextUtils.isEmpty(error)) {
                mErrorText.setText(error);
            }
            mLoading.setVisibility(View.GONE);
            mLoadMore.setVisibility(View.GONE);
            // Only show the "Retry" button for I/O errors; it won't help for
            // internal errors.
            mRetry.setVisibility(
                    folder.lastSyncResult == UIProvider.LastSyncResult.CONNECTION_ERROR ?
                    View.VISIBLE : View.GONE);
        } else if (folder.loadMoreUri != null && folder.totalCount > count) {
            mLoading.setVisibility(View.GONE);
            mNetworkError.setVisibility(View.GONE);
            mLoadMore.setVisibility(View.VISIBLE);
        }
    }

    private class FolderSyncTask extends AsyncTask<Void, Void, Void> {
        Uri mUri;
        public FolderSyncTask(Uri uri) {
            mUri = uri;
        }

        @Override
        public Void doInBackground(Void... params) {
            getContext().getContentResolver().query(mUri, null, null, null, null);
            return null;
        }
    }
}
