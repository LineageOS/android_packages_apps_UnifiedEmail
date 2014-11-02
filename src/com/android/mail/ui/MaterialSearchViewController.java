/*
 * Copyright (C) 2014 Google Inc.
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
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.text.TextUtils;
import android.view.View;
import android.widget.Toast;

import com.android.mail.ConversationListContext;
import com.android.mail.R;
import com.android.mail.providers.SearchRecentSuggestionsProvider;

import java.util.Locale;

/**
 * Controller for interactions between ActivityController and our custom search views.
 */
public class MaterialSearchViewController implements ViewMode.ModeChangeListener,
        TwoPaneLayout.ConversationListLayoutListener {
    // The controller is not in search mode. Both search action bar and the suggestion list
    // are not visible to the user.
    public static final int SEARCH_VIEW_STATE_GONE = 0;
    // The controller is actively in search (as in the action bar is focused and the user can type
    // into the search query). Both the search action bar and the suggestion list are visible.
    public static final int SEARCH_VIEW_STATE_VISIBLE = 1;
    // The controller is in a search ViewMode but not actively searching. This is relevant when
    // we have to show the search actionbar on top while the user is not interacting with it.
    public static final int SEARCH_VIEW_STATE_ONLY_ACTIONBAR = 2;

    private static final String EXTRA_VIEW_STATE = "extraSearchViewControllerViewState";

    private final MailActivity mActivity;
    private final ActivityController mController;

    protected SearchRecentSuggestionsProvider mSuggestionsProvider;

    protected MaterialSearchActionView mSearchActionView;
    protected MaterialSearchSuggestionsList mSearchSuggestionList;

    private int mViewMode;
    private int mViewState;
    private int mEndXCoordForTabletLandscape;

    private boolean mWaitToDestroyProvider;

    public MaterialSearchViewController(MailActivity activity, ActivityController controller,
            Intent intent, Bundle savedInstanceState) {
        mActivity = activity;
        mController = controller;

        final Intent voiceIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        final boolean supportVoice =
                voiceIntent.resolveActivity(mActivity.getPackageManager()) != null;

        mSuggestionsProvider = mActivity.getSuggestionsProvider();
        mSearchSuggestionList = (MaterialSearchSuggestionsList) mActivity.findViewById(
                R.id.search_overlay_view);
        mSearchSuggestionList.setController(this, mSuggestionsProvider);
        mSearchActionView = (MaterialSearchActionView) mActivity.findViewById(
                R.id.search_actionbar_view);
        mSearchActionView.setController(this, intent.getStringExtra(
                ConversationListContext.EXTRA_SEARCH_QUERY), supportVoice);

        if (savedInstanceState != null && savedInstanceState.containsKey(EXTRA_VIEW_STATE)) {
            mViewState = savedInstanceState.getInt(EXTRA_VIEW_STATE);
        }

        mActivity.getViewMode().addListener(this);
    }

    public void onDestroy() {
        if (!mWaitToDestroyProvider) {
            mSuggestionsProvider.cleanup();
        }
        mActivity.getViewMode().removeListener(this);
    }

    public void saveState(Bundle outState) {
        outState.putInt(EXTRA_VIEW_STATE, mViewState);
    }

    @Override
    public void onViewModeChanged(int newMode) {
        if (mController.shouldShowSearchBarByDefault()) {
            showSearchActionBar(MaterialSearchViewController.SEARCH_VIEW_STATE_ONLY_ACTIONBAR);
        } else if (mViewMode == 0) {
            showSearchActionBar(mViewState);
        } else {
            showSearchActionBar(MaterialSearchViewController.SEARCH_VIEW_STATE_GONE);
        }
        mViewMode = newMode;
    }

    @Override
    public void onConversationListLayout(int xEnd, boolean drawerOpen) {
        // Only care about the first layout
        if (mEndXCoordForTabletLandscape != xEnd) {
            // This is called when we get into tablet landscape mode
            mEndXCoordForTabletLandscape = xEnd;
            if (ViewMode.isSearchMode(mViewMode)) {
                final int defaultVisibility = mController.shouldShowSearchBarByDefault() ?
                        View.VISIBLE : View.GONE;
                mSearchActionView.setVisibility(drawerOpen ? View.INVISIBLE : defaultVisibility);
            }
            adjustViewForTwoPaneLandscape();
        }
    }

    public boolean handleBackPress() {
        final boolean shouldShowSearchBar = mController.shouldShowSearchBarByDefault();
        if (shouldShowSearchBar && mSearchSuggestionList.isShown()) {
            showSearchActionBar(MaterialSearchViewController.SEARCH_VIEW_STATE_ONLY_ACTIONBAR);
            return true;
        } else if (!shouldShowSearchBar && mSearchActionView.isShown()) {
            showSearchActionBar(MaterialSearchViewController.SEARCH_VIEW_STATE_GONE);
            return true;
        }
        return false;
    }

    // Should use the view states specified in MaterialSearchViewController
    public void showSearchActionBar(int state) {
        mViewState = state;
        switch (state) {
            case MaterialSearchViewController.SEARCH_VIEW_STATE_ONLY_ACTIONBAR:
                // Only actionbar is only applicable in search mode
                if (mController.shouldShowSearchBarByDefault()) {
                    mSearchActionView.setVisibility(View.VISIBLE);
                    mSearchSuggestionList.setVisibility(View.GONE);
                    mSearchActionView.focusSearchBar(false);
                    adjustViewForTwoPaneLandscape();
                    break;
                }
                // Fallthrough to setting everything invisible
            case MaterialSearchViewController.SEARCH_VIEW_STATE_GONE:
                mSearchActionView.focusSearchBar(false);
                mSearchActionView.setVisibility(View.GONE);
                mSearchSuggestionList.setVisibility(View.GONE);
                // For non-search view mode, clear the query term for search
                if (!ViewMode.isSearchMode(mViewMode)) {
                    mSearchActionView.clearSearchQuery();
                }
                break;
            case MaterialSearchViewController.SEARCH_VIEW_STATE_VISIBLE:
                mSearchActionView.setVisibility(View.VISIBLE);
                // Set to default layout/assets
                mSearchActionView.adjustViewForTwoPaneLandscape(false /* do not align */, 0);
                mSearchSuggestionList.setVisibility(View.VISIBLE);
                mSearchActionView.focusSearchBar(true);
                break;
        }
    }

    private void adjustViewForTwoPaneLandscape() {
        final boolean alignWithTL = mController.isTwoPaneLandscape() &&
                mViewState == MaterialSearchViewController.SEARCH_VIEW_STATE_ONLY_ACTIONBAR &&
                ViewMode.isSearchMode(mViewMode);
        mSearchActionView.adjustViewForTwoPaneLandscape(alignWithTL, mEndXCoordForTabletLandscape);
    }

    public void onQueryTextChanged(String query) {
        mSearchSuggestionList.setQuery(query);
    }

    public void onSearchCanceled() {
        // Special case search mode
        if (mActivity.getViewMode().isSearchMode()) {
            mActivity.setResult(Activity.RESULT_OK);
            mActivity.finish();
        } else {
            showSearchActionBar(MaterialSearchViewController.SEARCH_VIEW_STATE_GONE);
        }
    }

    public void onSearchPerformed(String query) {
        query = query.trim();
        if (!TextUtils.isEmpty(query)) {
            mSearchActionView.clearSearchQuery();
            mController.executeSearch(query);
        }
    }

    public void onVoiceSearch() {
        final Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().getLanguage());

        // Some devices do not support the voice-to-speech functionality.
        try {
            mActivity.startActivityForResult(intent,
                    AbstractActivityController.VOICE_SEARCH_REQUEST_CODE);
        } catch (ActivityNotFoundException e) {
            final String toast =
                    mActivity.getResources().getString(R.string.voice_search_not_supported);
            Toast.makeText(mActivity, toast, Toast.LENGTH_LONG).show();
        }
    }

    public void saveRecentQuery(String query) {
        new SaveRecentQueryTask().execute(query);
    }

    // static asynctask to save the query in the background.
    class SaveRecentQueryTask extends AsyncTask<String, Void, Void> {

        @Override
        protected void onPreExecute() {
            mWaitToDestroyProvider = true;
        }

        @Override
        protected Void doInBackground(String... args) {
            mSuggestionsProvider.saveRecentQuery(args[0]);
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            if (mWaitToDestroyProvider) {
                mSuggestionsProvider.cleanup();
                mWaitToDestroyProvider = false;
            }
        }
    }
}
