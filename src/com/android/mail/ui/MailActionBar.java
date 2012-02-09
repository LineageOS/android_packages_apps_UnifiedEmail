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
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.SpinnerAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.android.mail.R;
import com.android.mail.AccountRecentLabelSpinner;
import com.android.mail.AccountSpinnerAdapter;
import com.android.mail.ConversationListContext;

/**
 * View to manage the various states of the Gmail Action Bar
 *
 * TODO(viki): Include ConversatinSubjectDisplayer here as well.
 */
public class MailActionBar extends LinearLayout implements ActionBarView, OnNavigationListener {
    /**
     * This interface is used to send notifications back to the calling
     * activity. MenuHandler takes care of updating the provider, so this
     * interface should be used for notification purposes only (such as updating
     * the UI).
     */
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
         * Invoked when the user uses the {@link MailActionBar} to change accounts.
         *
         * @return whether the account can be switched successfully.
         */
        boolean navigateToAccount(final String account);

        void navigateToFolder(final String folderCanonicalName);

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
    protected RestrictedActivity mActivity;
    private Callback mCallback;
    protected View mFolderView;
    private Mode mMode;

    private MenuItem mRefreshItem;

    private MenuItem mSearch;
    SpinnerAdapter mSpinner;
    protected AccountRecentLabelSpinner mSpinnerView;

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
        mMode = Mode.NORMAL;
    }

    @Override
    public boolean createOptionsMenu(Menu menu) {
        // If the mode is valid, then set the initial menu
        if (mMode == Mode.INVALID) {
            return false;
        }
        mActivity.getMenuInflater().inflate(getOptionsMenuId(), menu);
        // mSearch = menu.findItem(R.id.search);
        // mRefreshItem = menu.findItem(R.id.refresh);
        return true;
    }

    @Override
    public Mode getMode() {
        return mMode;
    }

    @Override
    public int getOptionsMenuId() {
        switch (mMode){
            case NORMAL:
                // Fallthrough
            case SEARCH_RESULTS:
                return R.menu.conversation_list_menu;
            case FOLDER:
                return R.menu.label_list_menu;
            case SEARCH_RESULTS_CONVERSATION:
                return R.menu.conversation_actions;
            case CONVERSATION_SUBJECT:
                return R.menu.conversation_actions;
        }
        return 0;
    }

    @Override
    public void handleRestore(Bundle savedInstanceState) {
        // TODO(viki): Auto-generated method stub

    }

    @Override
    public void handleSaveInstanceState(Bundle outState) {
        // TODO(viki): Auto-generated method stub

    }

    @Override
    public void initialize(RestrictedActivity activity, Callback callback, ViewMode viewMode,
            ActionBar actionBar) {
        // TODO(viki): Auto-generated method stub
        mActionBar = actionBar;
        mCallback = callback;
        mActivity = activity;
        mSpinnerView = (AccountRecentLabelSpinner) findViewById(R.id.account_spinner);

        // Set the mode to Navigation mode
        mActionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);
        mSpinner = new AccountSpinnerAdapter(getContext());
        mActionBar.setListNavigationCallbacks(mSpinner, this);
    }

    @Override
    public boolean onNavigationItemSelected(int itemPosition, long itemId) {
        // Don't do anything. Toast on the action.
        Toast.makeText(getContext(), "Selected item " + itemPosition, Toast.LENGTH_SHORT).show();
        return false;
    }

    @Override
    public void onPause() {
        // TODO(viki): Auto-generated method stub

    }

    @Override
    public void onResume() {
        // TODO(viki): Auto-generated method stub

    }

    @Override
    public void onStatusResult(String account, int status) {
        // Update the inbox folder if required
        mCallback.stopActionBarStatusCursorLoader(account);
    }

    @Override
    public boolean prepareOptionsMenu(Menu menu) {
        if (mSubjectView != null){
            mSubjectView.setVisibility(GONE);
        }
        if (mFolderView != null){
            mFolderView.setVisibility(GONE);
        }

        switch (mMode){
            case NORMAL:
                mSpinnerView.setVisibility(VISIBLE);
                if (mSearch != null){
                    mSearch.collapseActionView();
                }
                break;
            case CONVERSATION_LIST:
                // Do nothing?
                break;
            case CONVERSATION:
                // Do nothing?
                break;
            case CONVERSATION_SUBJECT:
                mSpinnerView.setVisibility(GONE);
                break;
            case SEARCH_RESULTS:
                mActionBar.setDisplayHomeAsUpEnabled(true);
                mSpinnerView.setVisibility(GONE);
                if (mSearch != null) {
                    mSearch.collapseActionView();
                }
            case FOLDER:
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
    public boolean setMode(Mode mode) {
        mMode = mode;
        return true;
    }

    @Override
    public void updateActionBar(String[] accounts, String currentAccount) {
    }
}
