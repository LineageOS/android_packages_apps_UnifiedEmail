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

import android.app.ActionBar;
import android.app.ActionBar.OnNavigationListener;
import android.content.Context;
import android.os.Bundle;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.SpinnerAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.android.mail.R;
import com.android.mail.AccountSpinnerAdapter;
import com.android.mail.ConversationListContext;
import com.android.mail.providers.Account;
import com.android.mail.providers.UIProvider.AccountCapabilities;
import com.android.mail.providers.Folder;

/**
 * View to manage the various states of the Mail Action Bar
 *
 * TODO(viki): Include ConversationSubjectDisplayer here as well.
 */
public final class MailActionBar extends LinearLayout implements ActionBarView {
    /**
     * This interface is used to send notifications back to the calling
     * activity. MenuHandler takes care of updating the provider, so this
     * interface should be used for notification purposes only (such as updating
     * the UI).
     */
    // TODO(viki): This callback is currently unused and may be entirely unnecessary in the new
    // code, where the Actionbar is switched into navigation mode, relying on the framework for most
    // heavy lifting. Also, we can switch ViewMode to the appropriate mode and rely on all UI
    // components updating through ViewMode change listeners.
    public interface Callback {
        /**
         * Enter search mode
         */
        void enterSearchMode();

        /**
         * Exits search mode
         */
        void exitSearchMode();

        /**
         * Returns the current account.
         */
        String getCurrentAccount();

        /**
         * Called when the TwoPaneActionBar wants to get the current conversation list context.
         */
        ConversationListContext getCurrentListContext();

        /**
         * Invoked when the user is already viewing search results
         * and enters a new query.
         * @param string Query
         */
        void reloadSearch(String string);

        void showFolderList();

        void startActionBarStatusCursorLoader(String account);

        void stopActionBarStatusCursorLoader(String account);
    }

    private String[] mAccountNames;
    private ActionBar mActionBar;
    private RestrictedActivity mActivity;
    private ActivityController mCallback;
    private View mFolderView;
    /**
     * The current mode of the ActionBar. This references constants in {@link ViewMode}
     */
    private int mMode = ViewMode.UNKNOWN;

    private MenuItem mRefreshItem;

    private MenuItem mSearch;
    SpinnerAdapter mSpinner;
    /**
     * The account currently being shown
     */
    private Account mAccount;

    // TODO(viki): This is a SnippetTextView in the Gmail source code. Resolve.
    private TextView mSubjectView;

    public MailActionBar(Context context) {
        this(context, null);
    }

    public MailActionBar(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public MailActionBar(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    public boolean createOptionsMenu(Menu menu) {
        // If the mode is valid, then set the initial menu
        if (mMode == ViewMode.UNKNOWN) {
            return false;
        }
        mActivity.getMenuInflater().inflate(getOptionsMenuId(), menu);
        // mSearch = menu.findItem(R.id.search);
        // mRefreshItem = menu.findItem(R.id.refresh);
        return true;
    }

    @Override
    public int getOptionsMenuId() {
        // Relies on the ordering of the view modes, since they are integer constants.
        final int[] modeMenu = {
                // 0: UNKNOWN
                R.menu.conversation_list_menu,
                // 1: CONVERSATION
                R.menu.conversation_actions,
                // 2: CONVERSATION_LIST
                R.menu.conversation_list_menu,
                // 3: FOLDER_LIST
                R.menu.folder_list_menu,
                // 4: SEARCH_RESULTS
                R.menu.conversation_list_menu
        };
        return modeMenu[mMode];
    }

    @Override
    public void handleRestore(Bundle savedInstanceState) {
    }

    @Override
    public void handleSaveInstanceState(Bundle outState) {
    }

    @Override
    public void initialize(RestrictedActivity activity, ActivityController callback, ViewMode viewMode,
            ActionBar actionBar) {
        mActionBar = actionBar;
        mCallback = callback;
        mActivity = activity;

        // Set the mode to Navigation mode
        mActionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);
        mSpinner = new AccountSpinnerAdapter(getContext());
        mActionBar.setListNavigationCallbacks(mSpinner, this);
    }

    @Override
    public boolean onNavigationItemSelected(int position, long id) {
        final int type = mSpinner.getItemViewType(position);
        switch (type) {
            case AccountSpinnerAdapter.TYPE_ACCOUNT:
                mCallback.onAccountChanged((Account) mSpinner.getItem(position));
                // Get the capabilities associated with this account.
                final Object item = mSpinner.getItem(position);
                assert (item instanceof Account);
                mAccount = (Account) item;
                break;
        }
        return false;
    }

    @Override
    public void onPause() {
    }

    @Override
    public void onResume() {
    }

    @Override
    public void onStatusResult(String account, int status) {
        // Update the inbox folder if required
        mCallback.stopActionBarStatusCursorLoader(account);
    }

    @Override
    public void onViewModeChanged(int newMode) {
        mMode = newMode;

        // Always update the options menu and redraw. This will read the new mode and redraw
        // the options menu.
        mActivity.invalidateOptionsMenu();
    }

    /**
     * If shouldSetView is true, then the view is made visible, otherwise its visiblity is View.GONE
     * @param view the view whose visibility is modified
     * @param shouldSetView if true, the view is made visible, GONE otherwise
     */
    private void setVisibility(int resourceId, boolean shouldSetView) {
        final View view = findViewById(resourceId);
        assert (view != null);
        final int visibility = shouldSetView ? View.VISIBLE : View.GONE;
        view.setVisibility(visibility);
    }

    @Override
    public boolean prepareOptionsMenu(Menu menu) {
        // We start out with every option enabled. Based on the current view, we disable actions
        // that are possible.
        if (mSubjectView != null){
            mSubjectView.setVisibility(GONE);
        }
        if (mFolderView != null){
            mFolderView.setVisibility(GONE);
        }

        switch (mMode){
            case ViewMode.UNKNOWN:
                if (mSearch != null){
                    mSearch.collapseActionView();
                }
                break;
            case ViewMode.CONVERSATION_LIST:
                // Show compose, search, labels, and sync based on the account
                // The only option that needs to be disabled is search
                setVisibility(R.id.search, mAccount.supportsCapability(
                        AccountCapabilities.FOLDER_SERVER_SEARCH));
                break;
            case ViewMode.CONVERSATION:
                setVisibility(R.id.y_button, mAccount.supportsCapability(
                        AccountCapabilities.ARCHIVE));
                setVisibility(R.id.report_spam, mAccount.supportsCapability(
                        AccountCapabilities.REPORT_SPAM));
                setVisibility(R.id.mute, mAccount.supportsCapability(AccountCapabilities.MUTE));
                break;
            case ViewMode.SEARCH_RESULTS:
                mActionBar.setDisplayHomeAsUpEnabled(true);
                if (mSearch != null) {
                    mSearch.collapseActionView();
                }
            case ViewMode.FOLDER_LIST:
                break;
        }
        return false;
    }

    @Override
    public void removeBackButton() {
        if (mActionBar == null) {
            return;
        }
        mActionBar.setDisplayOptions(
                ActionBar.DISPLAY_SHOW_HOME,
                ActionBar.DISPLAY_HOME_AS_UP | ActionBar.DISPLAY_SHOW_HOME);
        mActivity.getActionBar().setHomeButtonEnabled(false);
    }

    @Override
    public void setBackButton() {
        if (mActionBar == null){
            return;
        }
        mActionBar.setDisplayOptions(
                ActionBar.DISPLAY_HOME_AS_UP | ActionBar.DISPLAY_SHOW_HOME,
                ActionBar.DISPLAY_HOME_AS_UP | ActionBar.DISPLAY_SHOW_HOME);
        mActivity.getActionBar().setHomeButtonEnabled(true);
    }

    @Override
    public void setFolder(String folder) {
        // TODO(viki): Add this functionality to change the label.
    }

    @Override
    public void updateActionBar(String[] accounts, String currentAccount) {
    }
}
