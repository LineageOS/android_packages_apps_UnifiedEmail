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

import android.app.DialogFragment;
import android.app.FragmentManager;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.mail.R;
import com.android.mail.providers.Folder;
import com.android.mail.providers.UIProvider;
import com.android.mail.ui.AbstractActivityController;
import com.android.mail.ui.AsyncRefreshTask;
import com.android.mail.ui.ViewMode;
import com.android.mail.utils.Utils;

public class ConversationListFooterView extends LinearLayout implements View.OnClickListener {
    private View mLoading;
    private View mNetworkError;
    private View mLoadMore;
    private Button mErrorActionButton;
    private TextView mErrorText;
    private AsyncRefreshTask mFolderSyncTask;
    private Folder mFolder;
    private Uri mLoadMoreUri;
    private int mErrorStatus;
    private FragmentManager mFragmentManager;
    private boolean mTabletDevice;
    // Backgrounds for different states.
    private static Drawable sWideBackground;
    private static Drawable sNormalBackground;

    public ConversationListFooterView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mTabletDevice = Utils.useTabletUI(context);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mLoading = findViewById(R.id.loading);
        mNetworkError = findViewById(R.id.network_error);
        mLoadMore = findViewById(R.id.load_more);
        mLoadMore.setOnClickListener(this);
        mErrorActionButton = (Button) findViewById(R.id.error_action_button);
        mErrorActionButton.setOnClickListener(this);
        mErrorText = (TextView)findViewById(R.id.error_text);
    }

    public void setFragmentManager(FragmentManager manager) {
        mFragmentManager = manager;
    }

    public void onClick(View v) {
        int id = v.getId();
        Folder f = (Folder) v.getTag();
        Uri uri = null;
        switch (id) {
            case R.id.error_action_button:
                switch (mErrorStatus) {
                    case UIProvider.LastSyncResult.CONNECTION_ERROR:
                        if (f != null && f.refreshUri != null) {
                            uri = f.refreshUri;
                        }
                        break;
                    case UIProvider.LastSyncResult.AUTH_ERROR:
                        // TODO - open sign-in page here
                        return;
                    case UIProvider.LastSyncResult.SECURITY_ERROR:
                        return; // Currently we do nothing for security errors.
                    case UIProvider.LastSyncResult.STORAGE_ERROR:
                        DialogFragment fragment = (DialogFragment)
                        mFragmentManager.findFragmentByTag(
                                AbstractActivityController.SYNC_ERROR_DIALOG_FRAGMENT_TAG);
                        if (fragment == null) {
                            fragment = SyncErrorDialogFragment.newInstance();
                        }
                        fragment.show(mFragmentManager,
                                AbstractActivityController.SYNC_ERROR_DIALOG_FRAGMENT_TAG);
                        return;
                    case UIProvider.LastSyncResult.INTERNAL_ERROR:
                        // TODO - open report feedback code
                        return;
                    default:
                        return;
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
            mFolderSyncTask = new AsyncRefreshTask(getContext(), uri);
            mFolderSyncTask.execute();
        }
    }

    public void setFolder(Folder folder) {
        mFolder = folder;
        mErrorActionButton.setTag(mFolder);
        mLoadMore.setTag(mFolder);
        mLoadMoreUri = folder.loadMoreUri;
    }

    /**
     * Update the view to reflect the new folder status.
     */
    public boolean updateStatus(final ConversationCursor cursor) {
        if (cursor == null) {
            return false;
        }
        boolean showFooter = true;
        final Bundle extras = cursor.getExtras();
        final int cursorStatus = extras.getInt(UIProvider.CursorExtraKeys.EXTRA_STATUS);
        mErrorStatus = extras.containsKey(UIProvider.CursorExtraKeys.EXTRA_ERROR) ?
                extras.getInt(UIProvider.CursorExtraKeys.EXTRA_ERROR)
                : UIProvider.LastSyncResult.SUCCESS;
        if (UIProvider.CursorStatus.isWaitingForResults(cursorStatus)) {
            mLoading.setVisibility(View.VISIBLE);
            mNetworkError.setVisibility(View.GONE);
            mLoadMore.setVisibility(View.GONE);
        } else if (mErrorStatus != UIProvider.LastSyncResult.SUCCESS) {
            mNetworkError.setVisibility(View.VISIBLE);
            mErrorText.setText(Utils.getSyncStatusText(getContext(), mErrorStatus));
            mLoading.setVisibility(View.GONE);
            mLoadMore.setVisibility(View.GONE);
            // Only show the "Retry" button for I/O errors; it won't help for
            // internal errors.
            mErrorActionButton.setVisibility(
                    mErrorStatus != UIProvider.LastSyncResult.SECURITY_ERROR ?
                    View.VISIBLE : View.GONE);

            int actionTextResourceId = R.string.retry;
            switch (mErrorStatus) {
                case UIProvider.LastSyncResult.CONNECTION_ERROR:
                    actionTextResourceId = R.string.retry;
                    break;
                case UIProvider.LastSyncResult.AUTH_ERROR:
                    actionTextResourceId = R.string.signin;
                    break;
                case UIProvider.LastSyncResult.SECURITY_ERROR:
                    mNetworkError.setVisibility(View.GONE);
                    break; // Currently we do nothing for security errors.
                case UIProvider.LastSyncResult.STORAGE_ERROR:
                    actionTextResourceId = R.string.info;
                    break;
                case UIProvider.LastSyncResult.INTERNAL_ERROR:
                    actionTextResourceId = R.string.report;
                    break;
                default:
                    mNetworkError.setVisibility(View.GONE);
                    break;
            }
            mErrorActionButton.setText(actionTextResourceId);

        } else if (mLoadMoreUri != null) {
            mLoading.setVisibility(View.GONE);
            mNetworkError.setVisibility(View.GONE);
            mLoadMore.setVisibility(View.VISIBLE);
        } else {
            showFooter = false;
        }
        return showFooter;
    }

    /**
     * Update to the appropriate background when the view mode changes.
     */
    public void onViewModeChanged(int newMode) {
        Drawable drawable;
        if (mTabletDevice && newMode == ViewMode.CONVERSATION_LIST) {
            drawable = getWideBackground();
        } else {
            drawable = getNormalBackground();
        }
        setBackgroundDrawable(drawable);
    }

    private Drawable getWideBackground() {
        if (sWideBackground == null) {
            sWideBackground = getBackground(R.drawable.conversation_wide_unread_selector);
        }
        return sWideBackground;
    }

    private Drawable getNormalBackground() {
        if (sNormalBackground == null) {
            sNormalBackground = getBackground(R.drawable.conversation_unread_selector);
        }
        return sNormalBackground;
    }

    private Drawable getBackground(int resId) {
        return getContext().getResources().getDrawable(resId);
    }
}
