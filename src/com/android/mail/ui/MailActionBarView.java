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
import android.app.SearchManager;
import android.app.SearchableInfo;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.AttributeSet;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.SearchView;
import android.widget.SearchView.OnQueryTextListener;
import android.widget.SearchView.OnSuggestionListener;
import android.widget.Toast;

import com.android.mail.AccountSpinnerAdapter;
import com.android.mail.R;
import com.android.mail.browse.SnippetTextView;
import com.android.mail.providers.Account;
import com.android.mail.providers.Folder;
import com.android.mail.providers.Settings;
import com.android.mail.providers.UIProvider.AccountCapabilities;
import com.android.mail.providers.UIProvider.FolderCapabilities;
import com.android.mail.providers.UIProvider.LastSyncResult;
import com.android.mail.utils.LogUtils;
import com.android.mail.utils.Utils;

/**
 * View to manage the various states of the Mail Action Bar.
 * <p>
 * This also happens to be the custom view we supply to ActionBar.
 *
 */
public class MailActionBarView extends LinearLayout implements OnNavigationListener,
        ViewMode.ModeChangeListener, OnQueryTextListener, OnSuggestionListener,
        MenuItem.OnActionExpandListener, SubjectDisplayChanger {
    protected ActionBar mActionBar;
    protected RestrictedActivity mActivity;
    protected ActivityController mController;
    private View mFolderView;
    /**
     * The current mode of the ActionBar. This references constants in {@link ViewMode}
     */
    private int mMode = ViewMode.UNKNOWN;

    private MenuItem mSearch;
    private AccountSpinnerAdapter mSpinner;
    /**
     * The account currently being shown
     */
    private Account mAccount;
    /**
     * The folder currently being shown
     */
    private Folder mFolder;

    private SnippetTextView mSubjectView;
    private SearchView mSearchWidget;
    private MenuItem mHelpItem;
    private MenuItem mSendFeedbackItem;
    private MenuItem mRefreshItem;
    private MenuItem mFolderSettingsItem;
    private View mRefreshActionView;
    private boolean mRefreshInProgress;
    /**
     * True if we are running on tablet.
     */
    private final boolean mIsOnTablet;

    public static final String LOG_TAG = new LogUtils().getLogTag();

    private final Handler mHandler = new Handler();
    private final Runnable mInvalidateMenu = new Runnable() {
        @Override
        public void run() {
            mActivity.invalidateOptionsMenu();
        }
    };
    /**
     * Whether the first navigation event should be ignored. The {@link #ignoreFirstNavigation(int)}
     * method talks about why this is required.
     */
    private boolean mIgnoreFirstNavigation = true;
    private final boolean mShowConversationSubject;

    public MailActionBarView(Context context) {
        this(context, null);
    }

    public MailActionBarView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public MailActionBarView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mShowConversationSubject = getResources().getBoolean(R.bool.show_conversation_subject);
        mIsOnTablet = Utils.useTabletUI(context);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mSubjectView = (SnippetTextView) findViewById(R.id.conversation_subject);
    }

    /**
     * Close the search view if it is expanded.
     */
    public void collapseSearch() {
        if (mSearch != null) {
            mSearch.collapseActionView();
        }
    }

    /**
     * Get the search menu item.
     */
    protected MenuItem getSearch() {
        return mSearch;
    }

    /**
     * Get whether to show the conversation subject in the action bar.
     */
    protected boolean showConversationSubject() {
        return (mMode == ViewMode.SEARCH_RESULTS_CONVERSATION || mMode == ViewMode.CONVERSATION)
                && mShowConversationSubject;
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        // If the mode is valid, then set the initial menu
        if (mMode == ViewMode.UNKNOWN) {
            return false;
        }
        mSearch = menu.findItem(R.id.search);
        if (mSearch != null) {
            mSearchWidget = (SearchView) mSearch.getActionView();
            mSearch.setOnActionExpandListener(this);
            SearchManager searchManager = (SearchManager) mActivity.getActivityContext()
                    .getSystemService(Context.SEARCH_SERVICE);
            if (searchManager != null && mSearchWidget != null) {
                SearchableInfo info = searchManager.getSearchableInfo(mActivity.getComponentName());
                mSearchWidget.setSearchableInfo(info);
                mSearchWidget.setOnQueryTextListener(this);
                mSearchWidget.setOnSuggestionListener(this);
                mSearchWidget.setIconifiedByDefault(true);
            }
        }
        mHelpItem = menu.findItem(R.id.help_info_menu_item);
        mSendFeedbackItem = menu.findItem(R.id.feedback_menu_item);
        mRefreshItem = menu.findItem(R.id.refresh);
        mFolderSettingsItem = menu.findItem(R.id.folder_options);
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
                R.menu.conversation_search_results_actions,
                // 6: WAITING_FOR_ACCOUNT_INITIALIZATION
                R.menu.wait_mode_actions
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
        // We don't want to include the "Show all folders" menu item on tablet devices
        final boolean showAllFolders = !Utils.useTabletUI(getContext());
        mSpinner = new AccountSpinnerAdapter(getContext(), recentFolders, showAllFolders);
    }

    /**
     * Attach the action bar to the view.
     */
    public void attach() {
        mActionBar.setListNavigationCallbacks(mSpinner, this);
    }

    public void setAccounts(Account[] accounts) {
        final Account currentAccount = mController.getCurrentAccount();
        mSpinner.setAccountArray(accounts);

        int position;
        for (position = 0; position < accounts.length; position++) {
            if (accounts[position].uri.equals(currentAccount.uri)) {
                break;
            }
        }
        if (position >= accounts.length) {
            position = 0;
            LogUtils.w(LOG_TAG, "IN actionbarview setAccounts, account not found, using first.");
        }
        final Uri defaultInbox = Settings.getDefaultInboxUri(mAccount.settings);
        final boolean viewingDefaultInbox =
                (mFolder == null || mAccount == null || mAccount.settings == null) ? false :
                    mFolder.uri.equals(defaultInbox);
        final boolean accountInSpinner = (position >= 0);
        if (accountInSpinner && viewingDefaultInbox) {
            // This position corresponds to current account and default Inbox.  Select it.
            setSelectedPosition(position);
        } else {
            // Set the selected position to a dead spacer. The user is either viewing a different
            // folder, or the account is missing from the spinner.
            setSelectedPosition(mSpinner.getSpacerPosition());
        }
    }

    /**
     * Sets the selected navigation position in the spinner to the position given here.
     * @param position
     */
    private void setSelectedPosition(int position) {
        // Only change the position if we are in the correct mode.
        if (mActionBar.getNavigationMode() != ActionBar.NAVIGATION_MODE_LIST) {
            return;
        }
        LogUtils.d(LOG_TAG, "ActionBarView.setSelectedNavigationItem(%d)", position);
        mActionBar.setSelectedNavigationItem(position);
    }

    /**
     * Called by the owner of the ActionBar to set the
     * folder that is currently being displayed.
     */
    public void setFolder(Folder folder) {
        // Change the currently selected item to an element which is a spacer: valid but not useful
        // This allows us to receive a tap on the account name when the user taps on it, and we can
        // take the user to the default inbox.
        setSelectedPosition(mSpinner.getSpacerPosition());
        mSpinner.setCurrentFolder(folder);
        mSpinner.notifyDataSetChanged();
        mFolder = folder;
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

    /**
     * Returns true if this list navigation event is erroneous and should be ignored.
     *
     *  Rationale: When a spinner is brought up for the first time, and it has never been brought up
     * before, it shows the 0th element. This is fine in most cases, since the navigation mode has
     * to select something. However, if we already have an account: for example if we went from the
     * widget to Conversation view, and the spinner never got a chance to initialize, it needs to
     * ignore this first navigation. If the spinner has ever been shown, then we will allow
     * subsequent calls to onNavigationItemSelected.
     * @param position the position selected in the drop down.
     */
    private boolean ignoreFirstNavigation(int position) {
        if (mIgnoreFirstNavigation && position == 0 && mAccount != null) {
            // Ignore the first navigation item selected because it is the list initializing
            // We already have an account.
            LogUtils.d(LOG_TAG, "ignoreFirstNavigation: Ignoring navigation to position 0."
                    + " mAccount = %s", mAccount.uri);
            // All user taps are now valid: even a tap on the current account to take the user to
            // the default inbox.
            mIgnoreFirstNavigation = false;
            setSelectedPosition(mSpinner.getSpacerPosition());
            // Yes, we want to ignore this navigation. It is not a user-initiated navigation.
            return true;
        }
        // Spinner was correctly initialized and is receiving a valid first tap.  All subsequent
        // taps are user events.
        mIgnoreFirstNavigation = false;
        // No, we don't want to ignore this navigation.
        return false;
    }

    @Override
    public boolean onNavigationItemSelected(int position, long id) {
        if (ignoreFirstNavigation(position)) {
            return false;
        }
        LogUtils.d(LOG_TAG, "onNavigationItemSelected(%d, %d) called", position, id);
        final int type = mSpinner.getType(position);
        switch (type) {
            case AccountSpinnerAdapter.TYPE_ACCOUNT:
                // Get the capabilities associated with this account.
                final Account account = (Account) mSpinner.getItem(position);
                LogUtils.d(LOG_TAG, "onNavigationItemSelected: Selecting account: %s",
                        account.name);
                if (mAccount.uri.equals(account.uri)) {
                    // The selected account is the same, let's load the default inbox.
                    mController.loadAccountInbox();
                } else {
                    // Switching accounts.
                    mController.onAccountChanged(account);
                }
                break;
            case AccountSpinnerAdapter.TYPE_FOLDER:
                final Object folder = mSpinner.getItem(position);
                assert (folder instanceof Folder);
                LogUtils.d(LOG_TAG, "onNavigationItemSelected: Selecting folder: %s",
                        ((Folder)folder).name);
                mController.onFolderChanged((Folder) folder);
                break;
            case AccountSpinnerAdapter.TYPE_ALL_FOLDERS:
                // Change the currently selected item to an element which is a spacer: valid
                // but not useful. This allows us to receive subsequent taps on the
                // "show all folders" menu item.
                setSelectedPosition(mSpinner.getSpacerPosition());
                mController.showFolderList();
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
        // If we are running on a tablet, we need to enable recent folders only in conversation
        // view, and disable them everywhere else.
        if (mIsOnTablet) {
            if (mMode == ViewMode.CONVERSATION) {
                mSpinner.enableRecentFolders();
            } else {
                mSpinner.disableRecentFolders();
            }
        }
    }

    protected int getMode() {
        return mMode;
    }

    public boolean onPrepareOptionsMenu(Menu menu) {
        // We start out with every option enabled. Based on the current view, we disable actions
        // that are possible.
        LogUtils.d(LOG_TAG, "ActionBarView.onPrepareOptionsMenu().");
        if (mFolderView != null) {
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
        if (mFolderSettingsItem != null) {
            mFolderSettingsItem.setVisible(mFolder != null
                    && mFolder.supportsCapability(FolderCapabilities.SUPPORTS_SETTINGS));
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
                if (!mShowConversationSubject) {
                    showNavList();
                } else {
                    setStandardMode();
                }
                break;
            case ViewMode.FOLDER_LIST:
                mActionBar.setDisplayHomeAsUpEnabled(true);
                mActionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_TITLE,
                        ActionBar.DISPLAY_SHOW_TITLE | ActionBar.DISPLAY_SHOW_CUSTOM);
                setStandardMode();
                mActionBar.setTitle(R.string.folder_list_title);
                break;
        }
        return false;
    }

    /**
     * Put the ActionBar in List navigation mode. This starts the spinner up if it is missing.
     */
    private void showNavList() {
        mActionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);
        mActionBar.setDisplayOptions(0,
                ActionBar.DISPLAY_SHOW_TITLE | ActionBar.DISPLAY_SHOW_CUSTOM);
    }

    /**
     * Set the actionbar mode to standard mode: no list navigation.
     */
    protected void setStandardMode() {
        mActionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
        mActionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM, ActionBar.DISPLAY_SHOW_CUSTOM);
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

    /**
     * Get the query text the user entered in the search widget, or empty string
     * if there is none.
     */
    public String getQuery() {
        return mSearchWidget != null ? mSearchWidget.getQuery().toString() : "";
    }

    // Next two methods are called when search suggestions are clicked.
    @Override
    public boolean onSuggestionSelect(int position) {
        return onSuggestionClick(position);
    }

    @Override
    public boolean onSuggestionClick(int position) {
        final Cursor c = mSearchWidget.getSuggestionsAdapter().getCursor();
        final boolean haveValidQuery = (c != null) && c.moveToPosition(position);
        if (!haveValidQuery) {
            LogUtils.d(LOG_TAG, "onSuggestionClick: Couldn't get a search query");
            // We haven't handled this query, but the default behavior will leave EXTRA_ACCOUNT
            // un-populated, leading to a crash. So claim that we have handled the event.
            return true;
        }
        collapseSearch();
        final String query = c.getString(c.getColumnIndex(SearchManager.SUGGEST_COLUMN_QUERY));
        mController.onSearchRequested(query);
        return true;
    }

    /**
     * Notify that the folder has changed.
     */
    public void onFolderUpdated(Folder folder) {
        mSpinner.onFolderUpdated(folder);
    }

    @Override
    public boolean onMenuItemActionExpand(MenuItem item) {
        // Do nothing. Required as part of the interface, we ar only interested in
        // onMenuItemActionCollapse(MenuItem).
        // Have to return true here. Unlike other callbacks, the return value here is whether
        // we want to suppress the action (rather than consume the action). We don't want to
        // suppress the action.
        return true;
    }

    @Override
    public boolean onMenuItemActionCollapse(MenuItem item) {
        // Work around b/6664203 by manually forcing this view to be VISIBLE
        // upon ActionView collapse. DISPLAY_SHOW_CUSTOM will still control its final
        // visibility.
        setVisibility(VISIBLE);
        // Have to return true here. Unlike other callbacks, the return value
        // here is whether we want to suppress the action (rather than consume the action). We
        // don't want to suppress the action.
        return true;
    }

    /**
     * Request the Accounts spinner to redraw itself in light of new data that it needs to request.
     */
    public void requestRecentFoldersAndRedraw() {
        mSpinner.requestRecentFoldersAndRedraw();
    }

    @Override
    public void setSubject(String subject) {
        if (!mShowConversationSubject) {
            return;
        }

        mSubjectView.setText(subject);
    }

    @Override
    public void clearSubject() {
        if (!mShowConversationSubject) {
            return;
        }

        mSubjectView.setText(null);
    }

    @Override
    public String getUnshownSubject(String subject) {
        if (!mShowConversationSubject) {
            return subject;
        }

        return mSubjectView.getTextRemainder(subject);
    }
}
