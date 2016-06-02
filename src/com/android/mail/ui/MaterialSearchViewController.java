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

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.text.TextUtils;
import android.view.View;
import android.widget.Toast;

import com.android.emailcommon.service.SearchParams;
import com.android.mail.ConversationListContext;
import com.android.mail.R;
import com.android.mail.providers.SearchRecentSuggestionsProvider;
import com.android.mail.utils.ViewUtils;

import java.util.Locale;

/**
 * Controller for interactions between ActivityController and our custom search views.
 */
public class MaterialSearchViewController implements
        TwoPaneLayout.ConversationListLayoutListener {
    private static final long FADE_IN_OUT_DURATION_MS = 150;

    // The controller is not in search mode. Both search action bar and the suggestion list
    // are not visible to the user.
    public static final int SEARCH_VIEW_STATE_GONE = 0;
    // The controller is actively in search (as in the action bar is focused and the user can type
    // into the search query). Both the search action bar and the suggestion list are visible.
    public static final int SEARCH_VIEW_STATE_VISIBLE = 1;
    // The controller is in a search ViewMode but not actively searching. This is relevant when
    // we have to show the search actionbar on top while the user is not interacting with it.
    public static final int SEARCH_VIEW_STATE_ONLY_ACTIONBAR = 2;

    private static final String EXTRA_CONTROLLER_STATE = "extraSearchViewControllerViewState";
    private static final String EXTRA_SEARCH_KEY_WORD = "extraSearchKeyWord";
    private static final String EXTRA_SEARCH_FACTOR = "extraSearchFactor";

    private MailActivity mActivity;
    private ActivityController mController;

    private SearchRecentSuggestionsProvider mSuggestionsProvider;

    private MaterialSearchActionView mSearchActionView;
    private MaterialSearchSuggestionsList mSearchSuggestionList;
    private MaterialSearchFactorSelecteView mSearchFactorView;
    private int mViewMode;
    private int mControllerState;
    private int mEndXCoordForTabletLandscape;

    private boolean mSavePending;
    private boolean mDestroyProvider;
    private String mKeyWord;
    private final static int INIT_ID = -1000;
    private int mFactorId = INIT_ID;
    private boolean mIsShowEmptyView = true;

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
        mSearchFactorView = (MaterialSearchFactorSelecteView) mActivity
                .findViewById(R.id.mail_filter_and_result);
        mSearchFactorView.setController(this);
        if (savedInstanceState != null && savedInstanceState.containsKey(EXTRA_CONTROLLER_STATE)) {
            mControllerState = savedInstanceState.getInt(EXTRA_CONTROLLER_STATE);
            mKeyWord = savedInstanceState.getString(EXTRA_SEARCH_KEY_WORD);
            mFactorId = savedInstanceState.getInt(EXTRA_SEARCH_FACTOR);
        }


    }

    /**
     * This controller should not be used after this is called.
     */
    public void onDestroy() {
        mDestroyProvider = mSavePending;
        if (!mSavePending) {
            mSuggestionsProvider.cleanup();
        }
        mActivity = null;
        mController = null;
        mSearchActionView = null;
        mSearchSuggestionList = null;
    }

    public void saveState(Bundle outState) {
        outState.putInt(EXTRA_CONTROLLER_STATE, mControllerState);
        outState.putString(EXTRA_SEARCH_KEY_WORD, mKeyWord);
        outState.putInt(EXTRA_SEARCH_FACTOR, mFactorId);
    }

    @Override
    public void onConversationListLayout(int xEnd, boolean drawerOpen) {
        // Only care about the first layout
        if (mEndXCoordForTabletLandscape != xEnd) {
            // This is called when we get into tablet landscape mode
            mEndXCoordForTabletLandscape = xEnd;
            if (ViewMode.isSearchMode(mViewMode)) {
                final int defaultVisibility = mController.shouldShowSearchBarByDefault(mViewMode) ?
                        View.VISIBLE : View.GONE;
                setViewVisibilityAndAlpha(mSearchActionView,
                        drawerOpen ? View.INVISIBLE : defaultVisibility);
            }
            adjustViewForTwoPaneLandscape();
        }
    }

    public boolean handleBackPress() {
        if (mSearchActionView.isShown()) {
            onSearchCanceled();
            return true;
        }
        return false;
    }

    /**
     * Set the new visibility state of the search controller.
     * @param state the new view state, must be one of the following options:
     *   {@link MaterialSearchViewController#SEARCH_VIEW_STATE_ONLY_ACTIONBAR},
     *   {@link MaterialSearchViewController#SEARCH_VIEW_STATE_VISIBLE},
     *   {@link MaterialSearchViewController#SEARCH_VIEW_STATE_GONE},
     */
    public void showSearchActionBar(int state) {
        // By default animate the visibility changes
        showSearchActionBar(state, true /* animate */);
    }

    /**
     * @param animate if true, the search bar and suggestion list will fade in/out of view.
     */
    public void showSearchActionBar(int state, boolean animate) {
        mControllerState = state;

        // ACTIONBAR is only applicable in search mode
        final boolean onlyActionBar = state == SEARCH_VIEW_STATE_ONLY_ACTIONBAR;
        final boolean isStateVisible = state == SEARCH_VIEW_STATE_VISIBLE;

        final boolean isSearchBarVisible = isStateVisible || onlyActionBar;

        final int searchBarVisibility = isSearchBarVisible ? View.VISIBLE : View.GONE;
        final int suggestionListVisibility = isStateVisible ? View.VISIBLE : View.GONE;
        final int filterVisibility = onlyActionBar ?View.VISIBLE:View.GONE;
        if (animate) {
            fadeInOutView(mSearchActionView, searchBarVisibility);
            fadeInOutView(mSearchSuggestionList, suggestionListVisibility);
            fadeInOutView(mSearchFactorView, filterVisibility);
        } else {
            setViewVisibilityAndAlpha(mSearchActionView, searchBarVisibility);
            setViewVisibilityAndAlpha(mSearchSuggestionList, suggestionListVisibility);
            setViewVisibilityAndAlpha(mSearchFactorView, filterVisibility);
        }
        mSearchActionView.focusSearchBar(isStateVisible);

        final boolean useDefaultColor = !isSearchBarVisible || shouldAlignWithTl();
        final int statusBarColor = useDefaultColor ? R.color.mail_activity_status_bar_color :
                R.color.search_status_bar_color;
        ViewUtils.setStatusBarColor(mActivity, statusBarColor);

        // Specific actions for each view state
        if (onlyActionBar) {
            adjustViewForTwoPaneLandscape();
        } else if (isStateVisible) {
            // Set to default layout/assets
            mSearchActionView.adjustViewForTwoPaneLandscape(false /* do not align */, 0);
        } else {
            // For non-search view mode, clear the query term for search
            if (!ViewMode.isSearchMode(mViewMode)) {
                mSearchActionView.clearSearchQuery();
            }
        }
    }

    /**
     * Helper function to fade in/out the provided view by animating alpha.
     */
    private void fadeInOutView(final View v, final int visibility) {
        if (visibility == View.VISIBLE) {
            v.setVisibility(View.VISIBLE);
            v.animate()
                    .alpha(1f)
                    .setDuration(FADE_IN_OUT_DURATION_MS)
                    .setListener(null);
        } else {
            v.animate()
                    .alpha(0f)
                    .setDuration(FADE_IN_OUT_DURATION_MS)
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            v.setVisibility(visibility);
                        }
                    });
        }
    }

    /**
     * Sets the view's visibility and alpha so that we are guaranteed that alpha = 1 when the view
     * is visible, and alpha = 0 otherwise.
     */
    private void setViewVisibilityAndAlpha(View v, int visibility) {
        v.setVisibility(visibility);
        if (visibility == View.VISIBLE) {
            v.setAlpha(1f);
        } else {
            v.setAlpha(0f);
        }
    }

    private boolean shouldAlignWithTl() {
        return mController.isTwoPaneLandscape() &&
                mControllerState == SEARCH_VIEW_STATE_ONLY_ACTIONBAR &&
                ViewMode.isSearchMode(mViewMode);
    }

    private void adjustViewForTwoPaneLandscape() {
        // Try to adjust if the layout happened already
        if (mEndXCoordForTabletLandscape != 0) {
            mSearchActionView.adjustViewForTwoPaneLandscape(shouldAlignWithTl(),
                    mEndXCoordForTabletLandscape);
        }
    }

    public void onQueryTextChanged(String query) {
        mSearchSuggestionList.setQuery(query);
    }

    public void onSearchCanceled() {
        showSearchActionBar(SEARCH_VIEW_STATE_GONE);
        mKeyWord = null;
        mFactorId = INIT_ID;
        mSearchActionView.clearSearchQuery();
        mController.exitLocalSearch();
    }

    public void onSearchPerformed(String query) {
        query = query.trim();
        if (!TextUtils.isEmpty(query)) {
            mSearchActionView.setQueryText(query);
            mSearchFactorView.checkAllFactor();
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
        if(!TextUtils.isEmpty(query)){
            new SaveRecentQueryTask().execute(query);
        }
    }

    // static asynctask to save the query in the background.
    private class SaveRecentQueryTask extends AsyncTask<String, Void, Void> {

        @Override
        protected void onPreExecute() {
            mSavePending = true;
        }

        @Override
        protected Void doInBackground(String... args) {
            mSuggestionsProvider.saveRecentQuery(args[0]);
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            if (mDestroyProvider) {
                mSuggestionsProvider.cleanup();
                mDestroyProvider = false;
            }
            mSavePending = false;
        }
    }

    public void changeFactorAction(String factor, boolean isUser) {
        setKeywordAndFactorId();
        mController.executeSearch(mKeyWord, factor, isUser);
    }

    public void updateSearchResultCount(int count, boolean isShowEmptyView) {
        mIsShowEmptyView = isShowEmptyView;
        if (isShowEmptyView) {
            setViewVisibilityAndAlpha(mSearchFactorView, View.GONE);
        }
    }

    private void setKeywordAndFactorId() {
        mKeyWord = mSearchActionView.getQueryText();
        mFactorId = mSearchFactorView.getCheckRadioButtonId();
    }

    public void restoreLocalSearch() {
        if (mControllerState == SEARCH_VIEW_STATE_VISIBLE) {
            showSearchActionBar(SEARCH_VIEW_STATE_VISIBLE);
        } else if (mControllerState == SEARCH_VIEW_STATE_ONLY_ACTIONBAR) {
            if (mKeyWord != null && mFactorId != INIT_ID) {
                mSearchActionView.setQueryText(mKeyWord);
                mSearchFactorView
                        .changeCheckRadioButton(mSearchFactorView.getFactor(mFactorId), false);
            }
        }
    }

    public void setQueryText(String query) {
        mSearchActionView.setQueryText(query);
    }

    public boolean isQueryTextNull() {
        return TextUtils.isEmpty(mSearchActionView.getQueryText().trim());
    }

    public void focusSearchBar(boolean hasFocus) {
        mSearchActionView.focusSearchBar(hasFocus);
    }

    public boolean isOnlyActionbar() {
        return mControllerState == SEARCH_VIEW_STATE_ONLY_ACTIONBAR;
    }

    public String getKeyWord() {
        return mKeyWord;
    }

    public boolean ismIsShowEmptyView() {
        return mIsShowEmptyView;
    }

    public void setFloatingComposeButtonVisible(int visible) {
        // mController.setFloatingComposeButtonVisible(visible);
    }

}
