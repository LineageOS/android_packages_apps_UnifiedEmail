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
import android.content.Context;
import android.database.Cursor;
import android.database.DataSetObserver;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.SearchView;
import android.widget.TextView;
import android.widget.SearchView.OnQueryTextListener;
import android.widget.SearchView.OnSuggestionListener;

import com.android.mail.AccountSpinnerAdapter;
import com.android.mail.R;
import com.android.mail.browse.SnippetTextView;
import com.android.mail.providers.Account;
import com.android.mail.providers.AccountObserver;
import com.android.mail.providers.Conversation;
import com.android.mail.providers.Folder;
import com.android.mail.providers.SearchRecentSuggestionsProvider;
import com.android.mail.providers.UIProvider;
import com.android.mail.providers.UIProvider.AccountCapabilities;
import com.android.mail.providers.UIProvider.FolderCapabilities;
import com.android.mail.utils.LogTag;
import com.android.mail.utils.LogUtils;
import com.android.mail.utils.Utils;

/**
 * View to manage the various states of the Mail Action Bar.
 * <p>
 * This also happens to be the custom view we supply to ActionBar.
 *
 */
public class MailActionBarView extends LinearLayout implements ViewMode.ModeChangeListener,
        OnQueryTextListener, OnSuggestionListener, MenuItem.OnActionExpandListener,
        SubjectDisplayChanger {
    protected ActionBar mActionBar;
    protected ControllableActivity mActivity;
    protected ActivityController mController;
    private View mFolderView;
    /**
     * The current mode of the ActionBar. This references constants in {@link ViewMode}
     */
    private int mMode = ViewMode.UNKNOWN;

    private MenuItem mSearch;
    private AccountSpinnerAdapter mSpinnerAdapter;
    private MailSpinner mSpinner;
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
    private Conversation mCurrentConversation;
    /**
     * True if we are running on tablet.
     */
    private final boolean mIsOnTablet;

    public static final String LOG_TAG = LogTag.getLogTag();

    private final Handler mHandler = new Handler();
    private final Runnable mInvalidateMenu = new Runnable() {
        @Override
        public void run() {
            mActivity.invalidateOptionsMenu();
        }
    };
    private final boolean mShowConversationSubject;
    private TextView mFolderAccountName;
    private DataSetObserver mFolderObserver;

    private final AccountObserver mAccountObserver = new AccountObserver() {
        @Override
        public void onChanged(Account newAccount) {
            mAccount = newAccount;
            if (mFolderAccountName != null) {
                mFolderAccountName.setText(mAccount.name);
            }
            mSpinner.setAccount(mAccount);
        }
    };
    /** True if the application has more than one account. */
    private boolean mHasManyAccounts;

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

    // update the pager title strip as the Folder's conversation count changes
    private class FolderObserver extends DataSetObserver {
        @Override
        public void onChanged() {
            onFolderUpdated(mController.getFolder());
        }
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mSubjectView = (SnippetTextView) findViewById(R.id.conversation_subject);
        mFolderView = findViewById(R.id.folder_layout);
        mFolderAccountName = (TextView) mFolderView.findViewById(R.id.account);
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

    public void initialize(ControllableActivity activity, ActivityController callback,
            ViewMode viewMode, ActionBar actionBar, RecentFolderList recentFolders) {
        mActionBar = actionBar;
        mController = callback;
        mActivity = activity;
        mFolderObserver = new FolderObserver();
        mController.registerFolderObserver(mFolderObserver);
        // We don't want to include the "Show all folders" menu item on tablet devices
        final boolean showAllFolders = !Utils.useTabletUI(getContext());
        mSpinnerAdapter = new AccountSpinnerAdapter(activity, getContext(), showAllFolders);
        mSpinner = (MailSpinner) findViewById(R.id.account_spinner);
        mSpinner.setAdapter(mSpinnerAdapter);
        mSpinner.setController(mController);
        mAccount = mAccountObserver.initialize(activity.getAccountController());
    }

    /**
     * Attach the action bar to the view.
     */
    public void attach() {
        // Do nothing.
    }

    /**
     * Sets the array of accounts to the value provided here.
     * @param accounts
     */
    public void setAccounts(Account[] accounts) {
        final Account currentAccount = mController.getCurrentAccount();
        mSpinnerAdapter.setAccountArray(accounts);
        mHasManyAccounts = accounts.length > 1;
        enableDisableSpinnner();
    }

    /**
     * Changes the spinner state according to the following logic. On phone we always show recent
     * labels: pre-populating if necessary. So on phone we always want to enable the spinner.
     * On tablet, we enable the spinner when the Folder list is NOT visible: In conversation view,
     * and search conversation view.
     */
    private final void enableDisableSpinnner() {
        // Spinner is always shown on phone, and it is enabled by default, so don't mess with it.
        // By default the drawable is set in the XML layout, and the view is enabled.
        if (!mIsOnTablet) {
            return;
        }
        // More than one account, or in conversation view.
        final boolean enabled = mHasManyAccounts || (mMode == ViewMode.CONVERSATION
                || mMode == ViewMode.SEARCH_RESULTS_CONVERSATION);
        mSpinner.changeEnabledState(enabled);
    }

    /**
     * Called by the owner of the ActionBar to set the
     * folder that is currently being displayed.
     */
    public void setFolder(Folder folder) {
        setRefreshInProgress(false);
        mFolder = folder;
        mSpinner.setFolder(folder);
        mActivity.invalidateOptionsMenu();
    }

    public void onDestroy() {
        if (mFolderObserver != null) {
            mController.unregisterFolderObserver(mFolderObserver);
            mFolderObserver = null;
        }
        mSpinnerAdapter.destroy();
        mAccountObserver.unregisterAndDestroy();
    }

    @Override
    public void onViewModeChanged(int newMode) {
        mMode = newMode;
        // Always update the options menu and redraw. This will read the new mode and redraw
        // the options menu.
        enableDisableSpinnner();
        mActivity.invalidateOptionsMenu();
        // Check if we are either on a phone, or in Conversation mode on tablet. For these, the
        // recent folders is enabled.
        if (!mIsOnTablet || mMode == ViewMode.CONVERSATION) {
            mSpinnerAdapter.enableRecentFolders();
        } else {
            mSpinnerAdapter.disableRecentFolders();
        }

        boolean showFolderView = false;

        switch (mMode) {
            case ViewMode.UNKNOWN:
                if (mSearch != null) {
                    mSearch.collapseActionView();
                }
                break;
            case ViewMode.CONVERSATION_LIST:
                showNavList();
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
                setStandardMode();
                showFolderView = true;
                break;
            case ViewMode.WAITING_FOR_ACCOUNT_INITIALIZATION:
                // We want the user to be able to switch accounts while waiting for an account
                // to sync.
                showNavList();
                break;
        }
        mFolderView.setVisibility(showFolderView ? VISIBLE : GONE);
    }

    protected int getMode() {
        return mMode;
    }

    public boolean onPrepareOptionsMenu(Menu menu) {
        // We start out with every option enabled. Based on the current view, we disable actions
        // that are possible.
        LogUtils.d(LOG_TAG, "ActionBarView.onPrepareOptionsMenu().");

        // TODO: move refresh stuff into setRefreshInProgress. can just setActionView without
        // invalidating.
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
            case ViewMode.CONVERSATION:
            case ViewMode.SEARCH_RESULTS_CONVERSATION:
                // We update the ActionBar options when we are entering conversation view because
                // waiting for the AbstractConversationViewFragment to do it causes duplicate icons
                // to show up during the time between the conversation is selected and the fragment
                // is added.
                setConversationModeOptions(menu);
                break;
            case ViewMode.CONVERSATION_LIST:
                // Show compose, search, folders, and sync based on the account
                // The only option that needs to be disabled is search
                Utils.setMenuItemVisibility(menu, R.id.search,
                        mAccount.supportsCapability(AccountCapabilities.FOLDER_SERVER_SEARCH));
                break;
        }
        return false;
    }

    /**
     * Put the ActionBar in List navigation mode. This starts the spinner up if it is missing.
     */
    private void showNavList() {
        mSpinner.setVisibility(View.VISIBLE);
        mFolderView.setVisibility(View.GONE);
        mFolderAccountName.setVisibility(View.GONE);
    }

    /**
     * Set the actionbar mode to standard mode: no list navigation.
     */
    private void setStandardMode() {
        mSpinner.setVisibility(View.GONE);
        mFolderView.setVisibility(View.VISIBLE);
        mFolderAccountName.setVisibility(View.VISIBLE);
    }

    /**
     * Set the actionbar mode to empty: no title, no custom content.
     */
    protected void setEmptyMode() {
        mSpinner.setVisibility(View.GONE);
        mFolderView.setVisibility(View.GONE);
        mFolderAccountName.setVisibility(View.GONE);
    }

    public void removeBackButton() {
        if (mActionBar == null) {
            return;
        }
        // Remove the back button but continue showing an icon.
        final int mask = ActionBar.DISPLAY_HOME_AS_UP | ActionBar.DISPLAY_SHOW_HOME;
        mActionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_HOME, mask);
        mActivity.getActionBar().setHomeButtonEnabled(false);
    }

    public void setBackButton() {
        if (mActionBar == null){
            return;
        }
        // Show home as up, and show an icon.
        final int mask = ActionBar.DISPLAY_HOME_AS_UP | ActionBar.DISPLAY_SHOW_HOME;
        mActionBar.setDisplayOptions(mask, mask);
        mActivity.getActionBar().setHomeButtonEnabled(true);
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        if (mSearch != null) {
            mSearch.collapseActionView();
            mSearchWidget.setQuery("", false);
        }
        mActivity.onSearchRequested(query.trim());
        return true;
    }

    @Override
    public boolean onQueryTextChange(String newText) {
        return false;
    }

    private boolean setRefreshInProgress(boolean inProgress) {
        if (inProgress != mRefreshInProgress) {
            mRefreshInProgress = inProgress;
            if (mSearch == null || !mSearch.isActionViewExpanded()) {
                mHandler.post(mInvalidateMenu);
            }
            return true;
        }
        return false;
    }

    private void onRefreshStarted() {
        setRefreshInProgress(true);
    }

    private void onRefreshStopped(int status) {
        setRefreshInProgress(false);
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
            // We haven't handled this query, but the default behavior will
            // leave EXTRA_ACCOUNT un-populated, leading to a crash. So claim
            // that we have handled the event.
            return true;
        }
        collapseSearch();
        // what is in the text field
        String queryText = mSearchWidget.getQuery().toString();
        // What the suggested query is
        String query = c.getString(c.getColumnIndex(SearchManager.SUGGEST_COLUMN_QUERY));
        // If the text the user typed in is a prefix of what is in the search
        // widget suggestion query, just take the search widget suggestion
        // query. Otherwise, it is a suffix and we want to remove matching
        // prefix portions.
        if (!TextUtils.isEmpty(queryText) && query.indexOf(queryText) != 0) {
            final int queryTokenIndex = queryText
                    .lastIndexOf(SearchRecentSuggestionsProvider.QUERY_TOKEN_SEPARATOR);
            if (queryTokenIndex > -1) {
                queryText = queryText.substring(0, queryTokenIndex);
            }
            // Since we auto-complete on each token in a query, if the query the
            // user typed up until the last token is a substring of the
            // suggestion they click, make sure we don't double include the
            // query text. For example:
            // user types john, that matches john palo alto
            // User types john p, that matches john john palo alto
            // Remove the first john
            // Only do this if we have multiple query tokens.
            if (queryTokenIndex > -1 && !TextUtils.isEmpty(query) && query.contains(queryText)
                    && queryText.length() < query.length()) {
                int start = query.indexOf(queryText);
                query = query.substring(0, start) + query.substring(start + queryText.length());
            }
        }
        mController.onSearchRequested(query.trim());
        return true;
    }

    /**
     * Notify that the folder has changed.
     */
    public void onFolderUpdated(Folder folder) {
        mSpinner.onFolderUpdated(folder);
        int status = folder.syncStatus;
        if (folder.isSyncInProgress()) {
            onRefreshStarted();
        } else {
            // Stop the spinner here.
            onRefreshStopped(folder.lastSyncResult);
        }
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

    public void setCurrentConversation(Conversation conversation) {
        mCurrentConversation = conversation;
    }

    //We need to do this here instead of in the fragment
    public void setConversationModeOptions(Menu menu) {
        if (mCurrentConversation == null) {
            return;
        }
        final boolean showMarkImportant = !mCurrentConversation.isImportant();
        Utils.setMenuItemVisibility(menu, R.id.mark_important, showMarkImportant
                && mAccount.supportsCapability(UIProvider.AccountCapabilities.MARK_IMPORTANT));
        Utils.setMenuItemVisibility(menu, R.id.mark_not_important, !showMarkImportant
                && mAccount.supportsCapability(UIProvider.AccountCapabilities.MARK_IMPORTANT));
        final boolean showDelete = mFolder != null &&
                mFolder.supportsCapability(UIProvider.FolderCapabilities.DELETE);
        Utils.setMenuItemVisibility(menu, R.id.delete, showDelete);
        // We only want to show the discard drafts menu item if we are not showing the delete menu
        // item, and the current folder is a draft folder and the account supports discarding
        // drafts for a conversation
        final boolean showDiscardDrafts = !showDelete && mFolder != null && mFolder.isDraft() &&
                mAccount.supportsCapability(AccountCapabilities.DISCARD_CONVERSATION_DRAFTS);
        Utils.setMenuItemVisibility(menu, R.id.discard_drafts, showDiscardDrafts);
        final boolean archiveVisible = mAccount.supportsCapability(AccountCapabilities.ARCHIVE)
                && mFolder != null && mFolder.supportsCapability(FolderCapabilities.ARCHIVE)
                && !mFolder.isTrash();
        Utils.setMenuItemVisibility(menu, R.id.archive, archiveVisible);
        Utils.setMenuItemVisibility(menu, R.id.remove_folder, !archiveVisible && mFolder != null
                && mFolder.supportsCapability(FolderCapabilities.CAN_ACCEPT_MOVED_MESSAGES)
                && !mFolder.isProviderFolder());
        final MenuItem removeFolder = menu.findItem(R.id.remove_folder);
        if (removeFolder != null) {
            removeFolder.setTitle(mActivity.getApplicationContext().getString(
                    R.string.remove_folder, mFolder.name));
        }
        Utils.setMenuItemVisibility(menu, R.id.report_spam,
                mAccount.supportsCapability(AccountCapabilities.REPORT_SPAM) && mFolder != null
                        && mFolder.supportsCapability(FolderCapabilities.REPORT_SPAM)
                        && !mCurrentConversation.spam);
        Utils.setMenuItemVisibility(menu, R.id.mark_not_spam,
                mAccount.supportsCapability(AccountCapabilities.REPORT_SPAM) && mFolder != null
                        && mFolder.supportsCapability(FolderCapabilities.MARK_NOT_SPAM)
                        && mCurrentConversation.spam);
        Utils.setMenuItemVisibility(menu, R.id.report_phishing,
                mAccount.supportsCapability(AccountCapabilities.REPORT_PHISHING) && mFolder != null
                        && mFolder.supportsCapability(FolderCapabilities.REPORT_PHISHING)
                        && !mCurrentConversation.phishing);
        Utils.setMenuItemVisibility(menu, R.id.mute,
                        mAccount.supportsCapability(AccountCapabilities.MUTE) && mFolder != null
                        && mFolder.supportsCapability(FolderCapabilities.DESTRUCTIVE_MUTE)
                        && !mCurrentConversation.muted);
    }
}
