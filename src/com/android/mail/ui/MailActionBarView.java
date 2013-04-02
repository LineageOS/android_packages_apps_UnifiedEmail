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
import android.os.Bundle;
import android.os.Handler;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.TextAppearanceSpan;
import android.util.AttributeSet;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.SearchView;
import android.widget.SearchView.OnQueryTextListener;
import android.widget.SearchView.OnSuggestionListener;
import android.widget.TextView;

import com.android.mail.ConversationListContext;
import com.android.mail.R;
import com.android.mail.browse.SnippetTextView;
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

    // This is a private setting available starting JB MR1.1.
    private static final int DISPLAY_TITLE_MULTIPLE_LINES = 0x20;

    /** True if we want the subject in the actionbar */
    public static final boolean SHOW_ACTIONBAR_SUBJECT = true;

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

    private SnippetTextView mSubjectView;
    private TextView mUnreadView;

    private SearchView mSearchWidget;
    private MenuItem mHelpItem;
    private MenuItem mSendFeedbackItem;
    private MenuItem mRefreshItem;
    private MenuItem mFolderSettingsItem;
    private View mRefreshActionView;
    /** True if the current device is a tablet, false otherwise. */
    private boolean mIsOnTablet;
    private boolean mRefreshInProgress;
    private Conversation mCurrentConversation;
    private AllAccountObserver mAllAccountObserver;
    private boolean mHaveMultipleAccounts = false;

    public static final String LOG_TAG = LogTag.getLogTag();

    private final Handler mHandler = new Handler();
    private final Runnable mInvalidateMenu = new Runnable() {
        @Override
        public void run() {
            mActivity.invalidateOptionsMenu();
        }
    };
    private final boolean mShowConversationSubject;
    private FolderObserver mFolderObserver;

    private final AccountObserver mAccountObserver = new AccountObserver() {
        @Override
        public void onChanged(Account newAccount) {
            updateAccount(newAccount);
        }
    };

    private final OnLayoutChangeListener mSnippetLayoutListener = new OnLayoutChangeListener() {
        @Override
        public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft,
                int oldTop, int oldRight, int oldBottom) {
            v.removeOnLayoutChangeListener(this);
            // We're in a layout, so try not to do anything layout-related here, and post a
            // runnable instead.
            v.post(new Runnable() {
                @Override
                public void run() {
                    // Framework only started supporting multi-line action bars in JB MR1.1,
                    // so on earlier versions, always use our custom action bar.
                    if (actionBarReportsMultipleLineTitle(mActionBar)) {
                        // Switch over to title mode when the first fragment asks for a subject
                        // remainder. We assume that layout has happened by now, so the
                        // SnippetTextView already has measurements it needs to calculate
                        // remainders, and it's safe to switch over to TITLE mode to inherit
                        // standard system behaviors.
                        setTitleModeFlags(ActionBar.DISPLAY_SHOW_TITLE |
                                DISPLAY_TITLE_MULTIPLE_LINES);

                        // Work around a bug where the title's container is stuck GONE when a title
                        // is set while in CUSTOM mode.
                        mActionBar.setTitle(mActionBar.getTitle());
                    }
                }
            });
        }
    };

    private static boolean actionBarReportsMultipleLineTitle(ActionBar bar) {
        boolean reports = false;
        try {
            if (bar != null) {
                reports = (ActionBar.class.getField("DISPLAY_TITLE_MULTIPLE_LINES") != null);
            }
        } catch (NoSuchFieldException e) {
            // stay false
        }
        return reports;
    }

    // Created via view inflation.
    @SuppressWarnings("unused")
    public MailActionBarView(Context context) {
        this(context, null);
    }

    public MailActionBarView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public MailActionBarView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        final Resources r = getResources();
        mShowConversationSubject = SHOW_ACTIONBAR_SUBJECT
                && r.getBoolean(R.bool.show_conversation_subject);
        mIsOnTablet = Utils.useTabletUI(r);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mSubjectView = (SnippetTextView) findViewById(R.id.conversation_subject);
        mUnreadView = (TextView) findViewById(R.id.unread_count);
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

    /**
     * Get whether to show the conversation subject in the action bar.
     */
    protected boolean showConversationSubject() {
        return ViewMode.isConversationMode(mMode) && mShowConversationSubject;
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
        mAccount = account;
        if (mAccount != null) {
            final ContentResolver resolver = mActivity.getActivityContext().getContentResolver();
            final Bundle bundle = new Bundle(1);
            bundle.putParcelable(UIProvider.SetCurrentAccountColumns.ACCOUNT, account);
            resolver.call(mAccount.uri, UIProvider.AccountCallMethods.SET_CURRENT_ACCOUNT,
                    mAccount.uri.toString(), bundle);
            setFolderAndAccount();
        }
    }

    /**
     * Called by the owner of the ActionBar to set the
     * folder that is currently being displayed.
     */
    public void setFolder(Folder folder) {
        setRefreshInProgress(false);
        mFolder = folder;
        setFolderAndAccount();
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
    }

    @Override
    public void onViewModeChanged(int newMode) {
        mMode = newMode;
        mActivity.invalidateOptionsMenu();
        // Check if we are either on a phone, or in Conversation mode on tablet. For these, the
        // recent folders is enabled.
        switch (mMode) {
            case ViewMode.UNKNOWN:
                closeSearchField();
                break;
            case ViewMode.CONVERSATION_LIST:
                closeSearchField();
                showNavList();
                break;
            case ViewMode.CONVERSATION:
                closeSearchField();
                mActionBar.setDisplayHomeAsUpEnabled(true);
                if (!mShowConversationSubject) {
                    showNavList();
                } else {
                    setSnippetMode();
                }
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
        setTitleModeFlags(ActionBar.DISPLAY_SHOW_TITLE | ActionBar.DISPLAY_SHOW_CUSTOM);
        mUnreadView.setVisibility(View.VISIBLE);
        mSubjectView.setVisibility(View.GONE);
        setFolderAndAccount();
    }

    /**
     * Set the actionbar mode to "snippet" mode: no list navigation, show what looks like 2-line
     * "standard" snippet. Later on, {@link #getUnshownSubject(String)} will seamlessly switch
     * back to bog-standard SHOW_TITLE mode once the text remainders can safely be determined.
     */
    protected void setSnippetMode() {
        setTitleModeFlags(ActionBar.DISPLAY_SHOW_CUSTOM);
        mUnreadView.setVisibility(View.GONE);
        mSubjectView.setVisibility(View.VISIBLE);
        mSubjectView.addOnLayoutChangeListener(mSnippetLayoutListener);
    }

    private void setFoldersMode() {
        setTitleModeFlags(ActionBar.DISPLAY_SHOW_TITLE);
        mUnreadView.setVisibility(View.GONE);
        mActionBar.setTitle(R.string.folders);
        if (mHaveMultipleAccounts) {
            mActionBar.setSubtitle(mAccount.name);
        }
    }

    /**
     * Set the actionbar mode to empty: no title, no custom content.
     */
    protected void setEmptyMode() {
        setTitleModeFlags(ActionBar.DISPLAY_SHOW_TITLE);
        if (mSubjectView != null) {
            mSubjectView.setVisibility(View.GONE);
        }
        if (mUnreadView != null) {
            mUnreadView.setVisibility(View.GONE);
        }
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
     * account {@link #mAccount} shown in the actionbar.
     */
    private void setFolderAndAccount() {
        // Very little can be done if the actionbar or activity is null.
        if (mActionBar == null || mActivity == null) {
            return;
        }
        // Check if we should be changing the actionbar at all, and back off if not.
        final boolean isShowingFolderAndAccount = mIsOnTablet || ViewMode.isListMode(mMode);
        if (!isShowingFolderAndAccount) {
            return;
        }
        if (mAccount != null && mHaveMultipleAccounts) {
            mActionBar.setSubtitle(mAccount.name);
        }
        if (mFolder == null) {
            return;
        }
        mActionBar.setTitle(mFolder.name);
        if (mUnreadView != null) {
            mUnreadView.setText(Utils.getUnreadCountString(
                    mActivity.getApplicationContext(), mFolder.unreadCount));
        }
    }

    /**
     * Notify that the folder has changed.
     */
    public void onFolderUpdated(Folder folder) {
        if (folder == null) {
            return;
        }
        setFolderAndAccount();
        if (folder.isSyncInProgress()) {
            onRefreshStarted();
        } else {
            // Stop the spinner here.
            onRefreshStopped();
        }
        final ConversationListContext listContext = mController == null ? null :
                mController.getCurrentListContext();
        if (!ConversationListContext.isSearchResult(listContext)) {
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

    private void setTitleModeFlags(int enabledFlags) {
        final int mask = ActionBar.DISPLAY_SHOW_TITLE
                | ActionBar.DISPLAY_SHOW_CUSTOM | DISPLAY_TITLE_MULTIPLE_LINES;
        mActionBar.setDisplayOptions(enabledFlags, mask);
    }

    @Override
    public void setSubject(String subject) {
        if (!mShowConversationSubject) {
            return;
        }

        // Use a smaller font size than the default action bar title text size
        SpannableStringBuilder builder = new SpannableStringBuilder();
        SpannableString textSizeSpannable = new SpannableString(subject);
        textSizeSpannable.setSpan(
            new TextAppearanceSpan(getContext(), R.style.SubjectActionBarTitleText),
            0, subject.length(), 0);
        builder.append(textSizeSpannable);

        mActionBar.setTitle(builder);
        mActionBar.setSubtitle(null);
        mSubjectView.setText(subject);
    }

    @Override
    public void clearSubjectAndUpdate() {
        if (!mShowConversationSubject) {
            return;
        }
        // Wipe subject view text
        mSubjectView.setText(null);
        // Set folder and account as title and subtitle
        setFolderAndAccount();
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
        // TODO(alice): reenable "move to" after it works properly with section inbox
        Utils.setMenuItemVisibility(menu, R.id.move_to, false);
        // Utils.setMenuItemVisibility(menu, R.id.move_to, mFolder != null
        //        && mFolder.supportsCapability(FolderCapabilities.ALLOWS_REMOVE_CONVERSATION));
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
