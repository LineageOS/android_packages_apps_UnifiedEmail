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
import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.SearchView;
import android.widget.SearchView.OnQueryTextListener;
import android.widget.SearchView.OnSuggestionListener;

import com.android.mail.ConversationListContext;
import com.android.mail.R;
import com.android.mail.preferences.MailPrefs;
import com.android.mail.providers.Account;
import com.android.mail.providers.AccountObserver;
import com.android.mail.providers.AllAccountObserver;
import com.android.mail.providers.Conversation;
import com.android.mail.providers.Folder;
import com.android.mail.providers.FolderObserver;
import com.android.mail.providers.SearchRecentSuggestionsProvider;
import com.android.mail.providers.UIProvider;
import com.android.mail.providers.UIProvider.AccountCapabilities;
import com.android.mail.providers.UIProvider.FolderCapabilities;
import com.android.mail.providers.UIProvider.FolderType;
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
        OnQueryTextListener, OnSuggestionListener, MenuItem.OnActionExpandListener {

    // This is a private setting available starting JB MR1.1.
    private static final int DISPLAY_TITLE_MULTIPLE_LINES = 0x20;

    protected ActionBar mActionBar;
    protected ControllableActivity mActivity;
    protected ActivityController mController;
    /**
     * The current mode of the ActionBar. This references constants in {@link ViewMode}
     */
    private int mMode = ViewMode.UNKNOWN;

    private MenuItem mSearch;
    /**
     * The account currently being shown
     */
    private Account mAccount;
    /**
     * The folder currently being shown
     */
    private Folder mFolder;

    private SearchView mSearchWidget;
    private MenuItem mHelpItem;
    private MenuItem mSendFeedbackItem;
    private MenuItem mRefreshItem;
    private MenuItem mFolderSettingsItem;
    private MenuItem mEmptyTrashItem;
    private MenuItem mEmptySpamItem;
    private View mRefreshActionView;
    /** True if the current device is a tablet, false otherwise. */
    protected final boolean mIsOnTablet;
    private boolean mRefreshInProgress;
    private Conversation mCurrentConversation;
    private AllAccountObserver mAllAccountObserver;
    private boolean mHaveMultipleAccounts = false;

    public static final String LOG_TAG = LogTag.getLogTag();

    private final Runnable mInvalidateMenu = new Runnable() {
        @Override
        public void run() {
            mActivity.invalidateOptionsMenu();
        }
    };
    private FolderObserver mFolderObserver;

    /** A handler that changes the subtitle when it receives a message. */
    private final class SubtitleHandler extends Handler {
        /** Message sent to display the account email address in the subtitle. */
        private static final int EMAIL = 0;

        @Override
        public void handleMessage(Message message) {
            assert (message.what == EMAIL);
            final String subtitleText;
            if (mAccount != null && mHaveMultipleAccounts) {
                // Display the account name (email address).
                subtitleText = mAccount.name;
            } else {
                // Clear the subtitle.
                subtitleText = "";
            }
            mActionBar.setSubtitle(subtitleText);
            super.handleMessage(message);
        }
    }

    /** Changes the subtitle to display the account name */
    private final SubtitleHandler mHandler = new SubtitleHandler();
    /** Unread count for the current folder. */
    private int mUnreadCount = 0;
    /** We show the email address after this delay: 5 seconds currently */
    private static final int ACCOUNT_DELAY_MS = 5 * 1000;
    /** At what point do we stop showing the unread count: 999+ currently */
    private final int UNREAD_LIMIT;

    /** Updates the resolver and tells it the most recent account. */
    private final class UpdateProvider extends AsyncTask<Bundle, Void, Void> {
        final Uri mAccount;
        final ContentResolver mResolver;
        public UpdateProvider(Uri account, ContentResolver resolver) {
            mAccount = account;
            mResolver = resolver;
        }

        @Override
        protected Void doInBackground(Bundle... params) {
            mResolver.call(mAccount, UIProvider.AccountCallMethods.SET_CURRENT_ACCOUNT,
                    mAccount.toString(), params[0]);
            return null;
        }
    }

    private final AccountObserver mAccountObserver = new AccountObserver() {
        @Override
        public void onChanged(Account newAccount) {
            updateAccount(newAccount);
        }
    };

    public MailActionBarView(Context context) {
        this(context, null);
    }

    public MailActionBarView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public MailActionBarView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        final Resources r = getResources();
        mIsOnTablet = Utils.useTabletUI(r);
        UNREAD_LIMIT = r.getInteger(R.integer.maxUnreadCount);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
    }

    public void expandSearch() {
        if (mSearch != null) {
            mSearch.expandActionView();
        }
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
        mEmptyTrashItem = menu.findItem(R.id.empty_trash);
        mEmptySpamItem = menu.findItem(R.id.empty_spam);
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

    public void initialize(ControllableActivity activity, ActivityController callback,
            ActionBar actionBar) {
        mActionBar = actionBar;
        mController = callback;
        mActivity = activity;
        mFolderObserver = new FolderObserver() {
            @Override
            public void onChanged(Folder newFolder) {
                onFolderUpdated(newFolder);
            }
        };
        // Return values are purposely discarded. Initialization happens quite early, and we don't
        // have a valid folder, or a valid list of accounts.
        mFolderObserver.initialize(mController);
        mAllAccountObserver = new AllAccountObserver() {
            @Override
            public void onChanged(Account[] allAccounts) {
                mHaveMultipleAccounts = (allAccounts.length > 1);
            }
        };
        mAllAccountObserver.initialize(mController);
        updateAccount(mAccountObserver.initialize(activity.getAccountController()));
    }

    private void updateAccount(Account account) {
        final boolean accountChanged = mAccount == null || !mAccount.uri.equals(account.uri);
        mAccount = account;
        if (mAccount != null && accountChanged) {
            final ContentResolver resolver = mActivity.getActivityContext().getContentResolver();
            final Bundle bundle = new Bundle(1);
            bundle.putParcelable(UIProvider.SetCurrentAccountColumns.ACCOUNT, account);
            final UpdateProvider updater = new UpdateProvider(mAccount.uri, resolver);
            updater.execute(bundle);
            setFolderAndAccount(false /* folderChanged */);
        }
    }

    /**
     * Called by the owner of the ActionBar to change the current folder.
     */
    public void setFolder(Folder folder) {
        setRefreshInProgress(false);
        mFolder = folder;
        setFolderAndAccount(true);
        mActivity.invalidateOptionsMenu();
    }

    public void onDestroy() {
        if (mFolderObserver != null) {
            mFolderObserver.unregisterAndDestroy();
            mFolderObserver = null;
        }
        if (mAllAccountObserver != null) {
            mAllAccountObserver.unregisterAndDestroy();
            mAllAccountObserver = null;
        }
        mAccountObserver.unregisterAndDestroy();
        mHandler.removeMessages(SubtitleHandler.EMAIL);
    }

    @Override
    public void onViewModeChanged(int newMode) {
        mMode = newMode;
        mActivity.invalidateOptionsMenu();
        mHandler.removeMessages(SubtitleHandler.EMAIL);
        // Check if we are either on a phone, or in Conversation mode on tablet. For these, the
        // recent folders is enabled.
        switch (mMode) {
            case ViewMode.UNKNOWN:
                break;
            case ViewMode.CONVERSATION_LIST:
                showNavList();
                break;
            case ViewMode.SEARCH_RESULTS_CONVERSATION:
                mActionBar.setDisplayHomeAsUpEnabled(true);
                setEmptyMode();
                break;
            case ViewMode.CONVERSATION:
                closeSearchField();
                mActionBar.setDisplayHomeAsUpEnabled(true);
                setEmptyMode();
                break;
            case ViewMode.FOLDER_LIST:
                closeSearchField();
                mActionBar.setDisplayHomeAsUpEnabled(true);
                setFoldersMode();
                break;
            case ViewMode.WAITING_FOR_ACCOUNT_INITIALIZATION:
                // We want the user to be able to switch accounts while waiting for an account
                // to sync.
                showNavList();
                break;
        }
    }

    /**
     * Close the search query entry field to avoid keyboard events, and to restore the actionbar
     * to non-search mode.
     */
    private void closeSearchField() {
        if (mSearch == null) {
            return;
        }
        mSearch.collapseActionView();
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
        if (mEmptyTrashItem != null) {
            mEmptyTrashItem.setVisible(mAccount != null && mFolder != null
                    && mAccount.supportsCapability(AccountCapabilities.EMPTY_TRASH)
                    && mFolder.isTrash());
        }
        if (mEmptySpamItem != null) {
            mEmptySpamItem.setVisible(mAccount != null && mFolder != null
                    && mAccount.supportsCapability(AccountCapabilities.EMPTY_SPAM)
                    && mFolder.isType(FolderType.SPAM));
        }

        switch (mMode) {
            case ViewMode.CONVERSATION:
            case ViewMode.SEARCH_RESULTS_CONVERSATION:
                // We update the ActionBar options when we are entering conversation view because
                // waiting for the AbstractConversationViewFragment to do it causes duplicate icons
                // to show up during the time between the conversation is selected and the fragment
                // is added.
                setConversationModeOptions(menu);
                // We want to use the user's preferred menu order here
                reorderMenu(getContext(), menu);
                break;
            case ViewMode.CONVERSATION_LIST:
                // Show compose and search based on the account
                // The only option that needs to be disabled is search
                Utils.setMenuItemVisibility(menu, R.id.search,
                        mAccount.supportsCapability(AccountCapabilities.FOLDER_SERVER_SEARCH));
                break;
            case ViewMode.SEARCH_RESULTS_LIST:
                // Show only compose
                // The only option that needs to be disabled is search
                Utils.setMenuItemVisibility(menu, R.id.search, false);
                break;
        }

        return false;
    }

    /**
     * Reorders the specified {@link Menu}, taking into account the user's Archive/Delete
     * preference.
     */
    public static void reorderMenu(final Context context, final Menu menu) {
        final boolean preferDelete = MailPrefs.get(context).getPreferDelete();

        for (int i = 0; i < menu.size(); i++) {
            final MenuItem menuItem = menu.getItem(i);
            final int itemId = menuItem.getItemId();

            if (preferDelete) {
                if (itemId == R.id.archive || itemId == R.id.remove_folder) {
                    menuItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
                }
            } else {
                if (itemId == R.id.delete || itemId == R.id.discard_drafts) {
                    menuItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
                }
            }
        }
    }

    /**
     * Put the ActionBar in List navigation mode.
     */
    private void showNavList() {
        setTitleModeFlags(ActionBar.DISPLAY_SHOW_TITLE | ActionBar.DISPLAY_SHOW_CUSTOM);
        setFolderAndAccount(false);
    }

    /**
     * Put the ActionBar in standard mode, and show the word "Folders" along with the account name
     * (if user has multiple accounts)
     */
    private void setFoldersMode() {
        setTitleModeFlags(ActionBar.DISPLAY_SHOW_TITLE);
        mActionBar.setTitle(R.string.folders);
        if (mHaveMultipleAccounts) {
            mActionBar.setSubtitle(mAccount.name);
        }
    }

    /**
     * Set the actionbar mode to empty: no title, no subtitle, no custom view.
     */
    protected void setEmptyMode() {
        // Disable title/subtitle and the custom view by setting the bitmask to all off.
        setTitleModeFlags(0);
    }

    /**
     * Removes the back button from being shown
     */
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
        mController.executeSearch(query.trim());
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

    private void onRefreshStopped() {
        setRefreshInProgress(false);
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
        mController.executeSearch(query.trim());
        return true;
    }

    /**
     * Uses the current state to update the current folder {@link #mFolder} and the current
     * account {@link #mAccount} shown in the actionbar. Also updates the actionbar subtitle to
     * momentarily display the unread count if it has changed.
     * @param folderChanged true if folder changed in terms of URI
     */
    private void setFolderAndAccount(final boolean folderChanged) {
        // Very little can be done if the actionbar or activity is null.
        if (mActionBar == null || mActivity == null) {
            return;
        }
        if (ViewMode.isWaitingForSync(mMode)) {
            // Account is not synced: clear title and update the subtitle.
            mActionBar.setTitle("");
            removeUnreadCount();
            return;
        }
        // Check if we should be changing the actionbar at all, and back off if not.
        final boolean isShowingFolder = mIsOnTablet || ViewMode.isListMode(mMode);
        if (!isShowingFolder) {
            return;
        }
        if (mFolder == null) {
            return;
        }
        mActionBar.setTitle(mFolder.name);

        final int folderUnreadCount = mFolder.isUnreadCountHidden() ? 0 : mFolder.unreadCount;
        // The user shouldn't see "999+ unread messages", and then a short while later: "999+
        // unread messages". So we set our unread count just past the limit. This way we can
        // change the subtitle the first time around but not for subsequent changes as far as the
        // unread count remains over the limit.
        final int toDisplay = (folderUnreadCount > UNREAD_LIMIT)
                ? (UNREAD_LIMIT + 1) : folderUnreadCount;
        if (mUnreadCount != toDisplay || folderChanged) {
            if (toDisplay == 0) {
                removeUnreadCount();
            } else {
                mActionBar.setSubtitle(Utils.getUnreadMessageString(
                        mActivity.getApplicationContext(), toDisplay));
                // This is a new update, remove previous messages, if any.
                mHandler.removeMessages(SubtitleHandler.EMAIL);
                // In a short while, show the account name in its place.
                mHandler.sendEmptyMessageDelayed(SubtitleHandler.EMAIL, ACCOUNT_DELAY_MS);
            }
        }
        // Remember the new value for the next run
        mUnreadCount = toDisplay;
    }

    /**
     * Remove the unread count and show the account name, if required.
     */
    private void removeUnreadCount() {
        // Remove all previous messages which might change the subtitle
        mHandler.removeMessages(SubtitleHandler.EMAIL);
        // Update the subtitle: clear it or show account name.
        mHandler.sendEmptyMessage(SubtitleHandler.EMAIL);
    }

    /**
     * Notify that the folder has changed.
     */
    public void onFolderUpdated(Folder folder) {
        if (folder == null) {
            return;
        }
        /** True if we are changing folders. */
        final boolean changingFolders = (mFolder == null || !mFolder.uri.equals(folder.uri));
        mFolder = folder;
        setFolderAndAccount(changingFolders);
        if (folder.isSyncInProgress()) {
            onRefreshStarted();
        } else {
            // Stop the spinner here.
            onRefreshStopped();
        }
        final ConversationListContext listContext = mController == null ? null :
                mController.getCurrentListContext();
        if (changingFolders && !ConversationListContext.isSearchResult(listContext)) {
            closeSearchField();
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

    /**
     * Sets the actionbar mode: Pass it an integer which contains each of these values, perhaps
     * OR'd together: {@link ActionBar#DISPLAY_SHOW_CUSTOM}, {@link ActionBar#DISPLAY_SHOW_TITLE},
     * and {@link #DISPLAY_TITLE_MULTIPLE_LINES}. To disable all, pass a zero.
     * @param enabledFlags
     */
    private void setTitleModeFlags(int enabledFlags) {
        final int mask = ActionBar.DISPLAY_SHOW_TITLE
                | ActionBar.DISPLAY_SHOW_CUSTOM | DISPLAY_TITLE_MULTIPLE_LINES;
        mActionBar.setDisplayOptions(enabledFlags, mask);
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
        Utils.setMenuItemVisibility(menu, R.id.move_to, mFolder != null
                && mFolder.supportsCapability(FolderCapabilities.ALLOWS_REMOVE_CONVERSATION));
        final MenuItem removeFolder = menu.findItem(R.id.remove_folder);
        if (mFolder != null && removeFolder != null) {
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
