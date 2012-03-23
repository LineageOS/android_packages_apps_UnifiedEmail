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
import android.app.SearchManager;
import android.app.SearchableInfo;
import android.app.ActionBar.OnNavigationListener;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.util.AttributeSet;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.SearchView;
import android.widget.SearchView.OnQueryTextListener;
import android.widget.TextView;
import android.widget.Toast;

import com.android.mail.R;
import com.android.mail.AccountSpinnerAdapter;
import com.android.mail.ConversationListContext;
import com.android.mail.providers.Account;
import com.android.mail.providers.UIProvider.AccountCapabilities;
import com.android.mail.providers.UIProvider.FolderCapabilities;
import com.android.mail.providers.UIProvider.LastSyncResult;
import com.android.mail.providers.Folder;
import com.android.mail.utils.LogUtils;
import com.android.mail.utils.Utils;

/**
 * View to manage the various states of the Mail Action Bar
 *
 * TODO(viki): Include ConversationSubjectDisplayer here as well.
 */
public final class ActionBarView extends LinearLayout implements OnNavigationListener,
        ViewMode.ModeChangeListener, OnQueryTextListener {
    private ActionBar mActionBar;
    private RestrictedActivity mActivity;
    private ActivityController mController;
    private View mFolderView;
    private Folder mFolder;
    /**
     * The current mode of the ActionBar. This references constants in {@link ViewMode}
     */
    private int mMode = ViewMode.UNKNOWN;

    private MenuItem mSearch;
    AccountSpinnerAdapter mSpinner;
    /**
     * The account currently being shown
     */
    private Account mAccount;

    // TODO(viki): This is a SnippetTextView in the Gmail source code. Resolve.
    private TextView mSubjectView;
    private SearchView mSearchWidget;
    private MenuItem mHelpItem;
    private MenuItem mSendFeedbackItem;
    private MenuItem mRefreshItem;
    private View mRefreshActionView;
    private boolean mRefreshInProgress;

    public static final String LOG_TAG = new LogUtils().getLogTag();

    private final Handler mHandler = new Handler();
    private final Runnable mInvalidateMenu = new Runnable() {
        @Override
        public void run() {
            mActivity.invalidateOptionsMenu();
        }
    };

    public ActionBarView(Context context) {
        this(context, null);
    }

    public ActionBarView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ActionBarView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        // If the mode is valid, then set the initial menu
        if (mMode == ViewMode.UNKNOWN) {
            return false;
        }
        mSearch = menu.findItem(R.id.search);
        if (mSearch != null) {
            mSearchWidget = (SearchView) mSearch.getActionView();
            SearchManager searchManager = (SearchManager) mActivity.getActivityContext()
                    .getSystemService(Context.SEARCH_SERVICE);
            if (searchManager != null && mSearchWidget != null) {
                SearchableInfo info = searchManager.getSearchableInfo(mActivity.getComponentName());
                mSearchWidget.setSearchableInfo(info);
                mSearchWidget.setOnQueryTextListener(this);
            }
        }
        mHelpItem = menu.findItem(R.id.help_info_menu_item);
        mSendFeedbackItem = menu.findItem(R.id.feedback_menu_item);
        mRefreshItem = menu.findItem(R.id.refresh);
        return true;
    }

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
                // 4: SEARCH_RESULTS_LIST
                R.menu.conversation_list_search_results_actions,
                // 5: SEARCH_RESULTS_CONVERSATION
                R.menu.conversation_search_results_actions
        };
        return modeMenu[mMode];
    }

    public void handleRestore(Bundle savedInstanceState) {
    }

    public void handleSaveInstanceState(Bundle outState) {
    }

    public void initialize(RestrictedActivity activity, ActivityController callback,
            ViewMode viewMode, ActionBar actionBar, RecentFolderList recentFolders) {
        mActionBar = actionBar;
        mController = callback;
        mActivity = activity;

        mSpinner = new AccountSpinnerAdapter(getContext(), recentFolders);
        // Set the mode to Navigation mode and listen on navigation changes.
        mActionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);
        mActionBar.setListNavigationCallbacks(mSpinner, this);
    }

    public void setAccounts(Account[] accounts) {
        Account currentAccount = mController.getCurrentAccount();
        mSpinner.setAccounts(accounts);

        int position;
        for (position = 0; position < accounts.length; position++) {
            if (accounts[position].equals(currentAccount)) {
                break;
            }
        }
        if (position >= accounts.length) {
            position = 0;
            LogUtils.w(LOG_TAG, "IN actionbarview setAccounts, account not found, using first.");
        }
        mActionBar.setSelectedNavigationItem(position);
    }

    /**
     * Called by the owner of the ActionBar to set the
     * folder that is currently being displayed.
     */
    public void setFolder(Folder folder) {
        mFolder = folder;
        mSpinner.setCurrentFolder(folder);
        mSpinner.notifyDataSetChanged();
    }

    /**
     * Called by the owner of the ActionBar to set the
     * account that is currently being displayed.
     */
    public void setAccount(Account account) {
        mAccount = account;
        mSpinner.setCurrentAccount(account);
        mSpinner.notifyDataSetChanged();
    }

    @Override
    public boolean onNavigationItemSelected(int position, long id) {
        final int type = mSpinner.getType(position);
        switch (type) {
            case AccountSpinnerAdapter.TYPE_ACCOUNT:
                // Get the capabilities associated with this account.
                final Object account = mSpinner.getItem(position);
                assert (account instanceof Account);
                mController.onAccountChanged((Account) account);
                break;
            case AccountSpinnerAdapter.TYPE_FOLDER:
                final Object folder = mSpinner.getItem(position);
                assert (folder instanceof Folder);
                mController.onFolderChanged((Folder) folder);
                break;
        }
        return false;
    }

    public void onPause() {
    }

    public void onResume() {
    }

    @Override
    public void onViewModeChanged(int newMode) {
        mMode = newMode;
        // Always update the options menu and redraw. This will read the new mode and redraw
        // the options menu.
        mActivity.invalidateOptionsMenu();
    }

    public boolean onPrepareOptionsMenu(Menu menu) {
        // We start out with every option enabled. Based on the current view, we disable actions
        // that are possible.
        if (mSubjectView != null){
            mSubjectView.setVisibility(GONE);
        }
        if (mFolderView != null){
            mFolderView.setVisibility(GONE);
        }

        if (mRefreshInProgress) {
            if (mRefreshItem != null) {
                if (mRefreshActionView == null) {
                    mRefreshItem.setActionView(R.layout.action_bar_indeterminate_progress);
                    mRefreshActionView = mRefreshItem.getActionView();
                } else {
                    mRefreshItem.setActionView(mRefreshActionView);
                }
            }
        } else {
            if (mRefreshItem != null) {
                mRefreshItem.setActionView(null);
            }
        }
        if (mHelpItem != null) {
            mHelpItem.setVisible(mAccount != null
                    && mAccount.supportsCapability(AccountCapabilities.HELP_CONTENT));
        }
        if (mSendFeedbackItem != null) {
            mSendFeedbackItem.setVisible(mAccount != null
                    && mAccount.supportsCapability(AccountCapabilities.SEND_FEEDBACK));
        }
        switch (mMode) {
            case ViewMode.UNKNOWN:
                if (mSearch != null) {
                    mSearch.collapseActionView();
                }
                break;
            case ViewMode.CONVERSATION_LIST:
                // Show compose, search, folders, and sync based on the account
                // The only option that needs to be disabled is search
                showNavList();
                Utils.setMenuItemVisibility(menu, R.id.search,
                        mAccount.supportsCapability(AccountCapabilities.FOLDER_SERVER_SEARCH));
                break;
            case ViewMode.CONVERSATION:
                mActionBar.setDisplayHomeAsUpEnabled(true);
                showNavList();
                break;
            case ViewMode.SEARCH_RESULTS_LIST:
                showNavList();
                setPopulatedSearchView();
                break;
            case ViewMode.SEARCH_RESULTS_CONVERSATION:
                mActionBar.setDisplayHomeAsUpEnabled(true);
                showNavList();
                if (Utils.useTabletUI(mActivity.getActivityContext())) {
                    setPopulatedSearchView();
                }
                break;
            case ViewMode.FOLDER_LIST:
                mActionBar.setDisplayHomeAsUpEnabled(true);
                mActionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_TITLE,
                        ActionBar.DISPLAY_SHOW_TITLE | ActionBar.DISPLAY_SHOW_CUSTOM);
                mActionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
                mActionBar.setTitle(R.string.folder_list_title);
                break;
        }
        return false;
    }

    private void showNavList() {
        mActionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);
        mActionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM,
                ActionBar.DISPLAY_SHOW_TITLE | ActionBar.DISPLAY_SHOW_CUSTOM);
    }

    private void setPopulatedSearchView() {
        if (mSearch != null) {
            mSearch.expandActionView();
            ConversationListContext context = mController.getCurrentListContext();
            if (context != null) {
                mSearchWidget.setQuery(context.searchQuery, false);
            }
        }
    }

    public void removeBackButton() {
        if (mActionBar == null) {
            return;
        }
        mActionBar.setDisplayOptions(
                ActionBar.DISPLAY_SHOW_HOME,
                ActionBar.DISPLAY_HOME_AS_UP | ActionBar.DISPLAY_SHOW_HOME);
        mActivity.getActionBar().setHomeButtonEnabled(false);
    }

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
    public boolean onQueryTextSubmit(String query) {
        if (mSearch != null) {
            mSearch.collapseActionView();
            mSearchWidget.setQuery("", false);
        }
        mActivity.onSearchRequested(query);
        return true;
    }

    @Override
    public boolean onQueryTextChange(String newText) {
        // TODO Auto-generated method stub
        return false;
    }

    public boolean setRefreshInProgress(boolean inProgress) {
        if (inProgress != mRefreshInProgress) {
            mRefreshInProgress = inProgress;
            if (mSearch == null || !mSearch.isActionViewExpanded()) {
                mHandler.post(mInvalidateMenu);
            }
            return true;
        }
        return false;
    }

    public void onRefreshStarted() {
        setRefreshInProgress(true);
    }

    public void onRefreshStopped(int status) {
        if (setRefreshInProgress(false)) {
            switch (status) {
                case LastSyncResult.SUCCESS:
                    break;
                default:
                    Context context = mActivity.getActivityContext();
                    Toast.makeText(context, Utils.getSyncStatusText(context, status),
                            Toast.LENGTH_LONG).show();
                    break;
            }
        }
    }
}
