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

package com.android.mail.photo.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.android.mail.R;

/**
 * Base implementation for a list fragment
 */
public abstract class BaseFragment extends Fragment {

    // State keys
    private static final String STATE_PENDING_REQ_ID_NEWER_KEY = "n_pending_req";
    private static final String STATE_PENDING_REQ_ID_OLDER_KEY = "o_pending_req";

    // Progress flags
    protected static final int PROGRESS_FLAG_NONE = 0;
    protected static final int PROGRESS_FLAG_NEWER = 1;
    protected static final int PROGRESS_FLAG_OLDER = 2;

    // Handler message ID
    protected static final int MESSAGE_ID_SHOW_PROGRESS_VIEW = 0;

    // Progress view delay
    private static final int PROGRESS_VIEW_DELAY = 800;

    // Instance variables
    protected Integer mNewerReqId;
    protected Integer mOlderReqId;
    private boolean mPaused;
    private boolean mRestoredFragment;

    private final Handler mHandler = new Handler() {

        /**
         * {@inheritDoc}
         */
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == MESSAGE_ID_SHOW_PROGRESS_VIEW) {
                doShowEmptyViewProgressDelayed();
            }
        }
    };

    /**
     * Returns {@code true} if the content is empty (has no items). Otherwise, {@code false}.
     */
    protected abstract boolean isEmpty();

    /**
     * {@inheritDoc}
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null) {
            mRestoredFragment = true;
            if (savedInstanceState.containsKey(STATE_PENDING_REQ_ID_NEWER_KEY)) {
                mNewerReqId = savedInstanceState.getInt(STATE_PENDING_REQ_ID_NEWER_KEY);
            }

            if (savedInstanceState.containsKey(STATE_PENDING_REQ_ID_OLDER_KEY)) {
                mOlderReqId = savedInstanceState.getInt(STATE_PENDING_REQ_ID_OLDER_KEY);
            }
        }
    }

    /**
     * Create the view with the specified layout resource id
     *
     * @param inflater The inflater
     * @param container The container
     * @param savedInstanceState The saved instance state
     * @param layoutResId The layout resource id
     *
     * @return The view
     */
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState, int layoutResId) {
        final View view = inflater.inflate(layoutResId, container, false);

        return view;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onResume() {
        super.onResume();

//        boolean hadPending = false;
//        if (mNewerReqId != null) {
//            if (EsService.isRequestPending(mNewerReqId)) {
//                if (isEmpty()) {
//                    showEmptyViewProgress(getView());
//                }
//            } else {
//                mNewerReqId = null;
//                hadPending = true;
//            }
//        }
//
//        if (mOlderReqId != null) {
//            if (EsService.isRequestPending(mOlderReqId)) {
//                if (isEmpty()) {
//                    showEmptyViewProgress(getView());
//                }
//            } else {
//                mOlderReqId = null;
//                hadPending = true;
//            }
//        }
//
//        if (hadPending && mNewerReqId == null && mOlderReqId == null) {
//            onResumeContentFetched(getView());
//
//            if (isEmpty()) {
//                showEmptyView(getView());
//            }
//        }

        mPaused = false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onPause() {
        super.onPause();

        mPaused = true;
    }

    /**
     * @return true if activity is paused
     */
    protected boolean isPaused() {
        return mPaused;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mNewerReqId != null) {
            outState.putInt(STATE_PENDING_REQ_ID_NEWER_KEY, mNewerReqId);
        }

        if (mOlderReqId != null) {
            outState.putInt(STATE_PENDING_REQ_ID_OLDER_KEY, mOlderReqId);
        }
    }

    public void startExternalActivity(Intent intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
        startActivity(intent);
    }

    /**
     * Display loading progress after a short delay.
     *
     * @param view The layout view
     */
    protected void showEmptyViewProgress(View view) {
        if (mRestoredFragment) {
            if (!mHandler.hasMessages(MESSAGE_ID_SHOW_PROGRESS_VIEW) && isEmpty()) {
                mHandler.sendEmptyMessageDelayed(MESSAGE_ID_SHOW_PROGRESS_VIEW,
                        PROGRESS_VIEW_DELAY);
            }
        } else {
            doShowEmptyViewProgress(view);
        }
    }

    /**
     * Shows the progress view a
     */
    protected void doShowEmptyViewProgressDelayed() {
        if (isAdded() && !isPaused()) {
            View view = getView();
            if (view != null) {
                doShowEmptyViewProgress(view);
            }
        }
    }

    /**
     * Display loading progress
     */
    protected void doShowEmptyViewProgress(View view) {
        if (isEmpty()) {
            final View emptyView = view.findViewById(android.R.id.empty);
            emptyView.setVisibility(View.VISIBLE);
            emptyView.findViewById(R.id.list_empty_text).setVisibility(View.GONE);
            emptyView.findViewById(R.id.list_empty_progress).setVisibility(View.VISIBLE);
        }
    }

    /**
     * Display the empty view
     */
    protected void doShowEmptyView(View view) {
        if (isEmpty()) {
            final View emptyView = view.findViewById(android.R.id.empty);
            emptyView.setVisibility(View.VISIBLE);
            emptyView.findViewById(R.id.list_empty_text).setVisibility(View.VISIBLE);
            emptyView.findViewById(R.id.list_empty_progress).setVisibility(View.GONE);
        }
    }

    /**
     * Display loading progress
     *
     * @param view The layout view
     * @param progressText The progress text
     */
    protected void showEmptyViewProgress(View view, String progressText) {
        if (isEmpty()) {
            ((TextView) view.findViewById(R.id.list_empty_progress_text)).setText(progressText);
            showEmptyViewProgress(view);
        }
    }

    /**
     * Show only the empty view
     *
     * @param view The layout view
     */
    protected void showEmptyView(View view) {
        removeProgressViewMessages();
        doShowEmptyView(view);
    }

    /**
     * Hide the empty view and show the content
     *
     * @param view The layout view
     */
    protected void showContent(View view) {
        removeProgressViewMessages();
        view.findViewById(android.R.id.empty).setVisibility(View.GONE);
    }

    /**
     * Setup the empty view
     *
     * @param view The view
     * @param emptyViewText The empty list view text
     */
    protected void setupEmptyView(View view, int emptyViewText) {
        final TextView etv = (TextView)view.findViewById(R.id.list_empty_text);
        etv.setText(emptyViewText);
    }

    /**
     * If there are no pending requests hide the spinner
     *
     * @param progressView The progress view
     */
    protected void updateSpinner(ProgressBar progressView) {
        if (progressView == null) {
            return;
        }

        progressView.setVisibility(
                mNewerReqId == null && mOlderReqId == null ? View.GONE : View.VISIBLE);
    }

    /**
     * Remove MESSAGE_ID_SHOW_PROGRESS_VIEW messages.
     */
    protected void removeProgressViewMessages() {
        mHandler.removeMessages(MESSAGE_ID_SHOW_PROGRESS_VIEW);
    }

    /**
     * The content fetch completed while the activity was paused
     *
     * @param view The context view
     */
    protected void onResumeContentFetched(View view) {
    }
}
