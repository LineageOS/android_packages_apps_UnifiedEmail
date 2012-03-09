/*******************************************************************************
 *      Copyright (C) 2012 Google Inc.
 *      Licensed to The Android Open Source Project.
 *
 *      Licensed under the Apache License, Version 2.0 (the "License");
 *      you may not use this file except in compliance with the License.
 *      You may obtain a copy of the License at
 *
 *           http://www.apache.org/licenses/LICENSE-2.0
 *
 *      Unless required by applicable law or agreed to in writing, software
 *      distributed under the License is distributed on an "AS IS" BASIS,
 *      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *      See the License for the specific language governing permissions and
 *      limitations under the License.
 *******************************************************************************/

package com.android.mail.ui;

import android.app.ActionBar;
import android.app.ActionBar.LayoutParams;
import android.app.Activity;
import android.app.Dialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.widget.LinearLayout;

import com.android.mail.R;
import com.android.mail.ConversationListContext;
import com.android.mail.compose.ComposeActivity;
import com.android.mail.providers.Account;
import com.android.mail.providers.AccountCacheProvider;
import com.android.mail.providers.Conversation;
import com.android.mail.providers.Folder;
import com.android.mail.providers.Settings;
import com.android.mail.providers.UIProvider;
import com.android.mail.ui.AsyncRefreshTask;
import com.android.mail.utils.LogUtils;
import com.android.mail.utils.Utils;

import com.google.common.collect.Sets;

import java.util.Set;


/**
 * This is an abstract implementation of the Activity Controller. This class
 * knows how to respond to menu items, state changes, layout changes, etc. It
 * weaves together the views and listeners, dispatching actions to the
 * respective underlying classes.
 * <p>
 * Even though this class is abstract, it should provide default implementations
 * for most, if not all the methods in the ActivityController interface. This
 * makes the task of the subclasses easier: OnePaneActivityController and
 * TwoPaneActivityController can be concise when the common functionality is in
 * AbstractActivityController.
 * </p>
 * <p>
 * In the Gmail codebase, this was called BaseActivityController
 * </p>
 */
public abstract class AbstractActivityController implements ActivityController {
    private static final String SAVED_CONVERSATION = "saved-conversation";
    private static final String SAVED_CONVERSATION_POSITION = "saved-conv-pos";
    // Keys for serialization of various information in Bundles.
    private static final String SAVED_LIST_CONTEXT = "saved-list-context";
    private static final String SAVED_ACCOUNT = "saved-account";

    /**
     * Are we on a tablet device or not.
     */
    public final boolean IS_TABLET_DEVICE;

    protected Account mAccount;
    protected Folder mFolder;
    protected ActionBarView mActionBarView;
    protected final RestrictedActivity mActivity;
    protected final Context mContext;
    protected final RecentFolderList mRecentFolderList;
    protected ConversationListContext mConvListContext;
    private FetchAccountFolderTask mFetchAccountFolderTask;
    protected Conversation mCurrentConversation;

    protected ConversationListFragment mConversationListFragment;
    /**
     * The current mode of the application. All changes in mode are initiated by
     * the activity controller. View mode changes are propagated to classes that
     * attach themselves as listeners of view mode changes.
     */
    protected final ViewMode mViewMode;
    protected ContentResolver mResolver;
    protected FolderListFragment mFolderListFragment;
    protected ConversationViewFragment mConversationViewFragment;
    protected boolean isLoaderInitialized = false;
    private AsyncRefreshTask mAsyncRefreshTask;

    private final Set<Uri> mCurrentAccountUris = Sets.newHashSet();
    protected Settings mCachedSettings;
    private FetchSearchFolderTask mFetchSearchFolderTask;
    /** Whether we have recorded this folder as a recent folder yet? */
    private boolean mFolderTouched = false;

    protected static final String LOG_TAG = new LogUtils().getLogTag();
    private static final int ACCOUNT_CURSOR_LOADER = 0;
    private static final int ACCOUNT_SETTINGS_LOADER = 1;
    private static final int FOLDER_CURSOR_LOADER = 2;

    public AbstractActivityController(MailActivity activity, ViewMode viewMode) {
        mActivity = activity;
        mViewMode = viewMode;
        mContext = activity.getApplicationContext();
        IS_TABLET_DEVICE = Utils.useTabletUI(mContext);
        mRecentFolderList = new RecentFolderList(mContext);
    }

    @Override
    public synchronized void attachConversationList(ConversationListFragment fragment) {
        // If there is an existing fragment, unregister it
        if (mConversationListFragment != null) {
            mViewMode.removeListener(mConversationListFragment);
        }
        mConversationListFragment = fragment;
        // If the current fragment is non-null, add it as a listener.
        if (fragment != null) {
            mViewMode.addListener(mConversationListFragment);
        }
    }

    @Override
    public synchronized void attachFolderList(FolderListFragment fragment) {
        // If there is an existing fragment, unregister it
        if (mFolderListFragment != null) {
            mViewMode.removeListener(mFolderListFragment);
        }
        mFolderListFragment = fragment;
        if (fragment != null) {
            mViewMode.addListener(mFolderListFragment);
        }
    }

    @Override
    public void attachConversationView(ConversationViewFragment conversationViewFragment) {
        mConversationViewFragment = conversationViewFragment;
    }

    @Override
    public void clearSubject() {
        // TODO(viki): Auto-generated method stub
    }

    @Override
    public Account getCurrentAccount() {
        return mAccount;
    }

    @Override
    public ConversationListContext getCurrentListContext() {
        return mConvListContext;
    }

    @Override
    public String getHelpContext() {
        return "Mail";
    }

    @Override
    public int getMode() {
        return mViewMode.getMode();
    }

    @Override
    public String getUnshownSubject(String subject) {
        // Calculate how much of the subject is shown, and return the remaining.
        return null;
    }

    @Override
    public void handleConversationLoadError() {
        // TODO(viki): Auto-generated method stub
    }

    /**
     * Initialize the action bar. This is not visible to OnePaneController and
     * TwoPaneController so they cannot override this behavior.
     */
    private void initCustomActionBarView() {
        ActionBar actionBar = mActivity.getActionBar();
        mActionBarView = (ActionBarView) LayoutInflater.from(mContext).inflate(
                R.layout.actionbar_view, null);
        if (actionBar != null && mActionBarView != null) {
            // Why have a different variable for the same thing? We should apply
            // the same actions
            // on mActionBarView instead.
            mActionBarView.initialize(mActivity, this, mViewMode, actionBar, mRecentFolderList);
            actionBar.setCustomView(mActionBarView, new ActionBar.LayoutParams(
                    LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
            actionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM,
                    ActionBar.DISPLAY_SHOW_CUSTOM | ActionBar.DISPLAY_SHOW_TITLE);
        }
    }

    /**
     * Returns whether the conversation list fragment is visible or not.
     * Different layouts will have their own notion on the visibility of
     * fragments, so this method needs to be overriden.
     *
     * @return
     */
    protected abstract boolean isConversationListVisible();

    @Override
    public void onAccountChanged(Account account) {
        if (!account.equals(mAccount)) {
            mAccount = account;
            mRecentFolderList.changeCurrentAccount(account);
            onSettingsChanged(null);
            restartSettingsLoader();
            mActionBarView.setAccount(mAccount);
            mActivity.invalidateOptionsMenu();
            // Account changed; existing folder is invalid.
            mFolder = null;
            fetchAccountFolderInfo();
        }
    }

    private void restartSettingsLoader() {
        if (mAccount.settingsQueryUri != null) {
            mActivity.getLoaderManager().restartLoader(ACCOUNT_SETTINGS_LOADER, null, this);
        }
    }

    public void onSettingsChanged(Settings settings) {
        mCachedSettings = settings;
        resetActionBarIcon();
    }

    @Override
    public Settings getSettings() {
        return mCachedSettings;
    }

    private void fetchAccountFolderInfo() {
        if (mFetchAccountFolderTask != null) {
            mFetchAccountFolderTask.cancel(true);
        }
        mFetchAccountFolderTask = new FetchAccountFolderTask();
        mFetchAccountFolderTask.execute();
    }

    private void fetchSearchFolder(Intent intent) {
        if (mFetchSearchFolderTask != null) {
            mFetchSearchFolderTask.cancel(true);
        }
        mFetchSearchFolderTask = new FetchSearchFolderTask(intent
                .getStringExtra(ConversationListContext.EXTRA_SEARCH_QUERY));
        mFetchSearchFolderTask.execute();
    }

    @Override
    public void onFolderChanged(Folder folder) {
        if (folder != null && !folder.equals(mFolder)) {
            setFolder(folder);
            mConvListContext = ConversationListContext.forFolder(mContext, mAccount, mFolder);
            showConversationList(mConvListContext);
        }
    }

    /** Set the current folder */
    private void setFolder(Folder folder) {
        // Start watching folder for sync status.
        if (folder != null && !folder.equals(mFolder)) {
            mActionBarView.setRefreshInProgress(false);
            mFolder = folder;
            mFolderTouched = false;
            mActionBarView.setFolder(mFolder);
            mActivity.getLoaderManager().restartLoader(FOLDER_CURSOR_LOADER, null, this);
        } else if (folder == null) {
            LogUtils.wtf(LOG_TAG, "Folder in setFolder is null");
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        // TODO(viki): Auto-generated method stub
    }

    @Override
    public void onConversationListVisibilityChanged(boolean visible) {
        // TODO(viki): Auto-generated method stub
    }

    /**
     * By default, doing nothing is right. A two-pane controller will need to
     * override this.
     */
    @Override
    public void onConversationVisibilityChanged(boolean visible) {
        // Do nothing.
        return;
    }

    @Override
    public boolean onCreate(Bundle savedState) {
        // Initialize the action bar view.
        initCustomActionBarView();
        // Allow shortcut keys to function for the ActionBar and menus.
        mActivity.setDefaultKeyMode(Activity.DEFAULT_KEYS_SHORTCUT);
        mResolver = mActivity.getContentResolver();

        // All the individual UI components listen for ViewMode changes. This
        // simplifies the amount of logic in the AbstractActivityController, but increases the
        // possibility of timing-related bugs.
        mViewMode.addListener(this);
        assert (mActionBarView != null);
        mViewMode.addListener(mActionBarView);

        restoreState(savedState);
        return true;
    }

    @Override
    public Dialog onCreateDialog(int id, Bundle bundle) {
        // TODO(viki): Auto-generated method stub
        return null;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = mActivity.getMenuInflater();
        inflater.inflate(mActionBarView.getOptionsMenuId(), menu);
        mActionBarView.onCreateOptionsMenu(menu);
        return true;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // TODO(viki): Auto-generated method stub
        return false;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        boolean handled = true;
        switch (id) {
            case android.R.id.home:
                onUpPressed();
                break;
            case R.id.compose:
                ComposeActivity.compose(mActivity.getActivityContext(), mAccount);
                break;
            case R.id.show_all_folders:
                showFolderList();
                break;
            case R.id.refresh:
                requestFolderRefresh();
                break;
            case R.id.settings:
                Utils.showSettings(mActivity.getActivityContext(), mAccount);
                break;
            case R.id.help_info_menu_item:
                // TODO: enable context sensitive help
                Utils.showHelp(mActivity.getActivityContext(), mAccount.helpIntentUri, null);
                break;
            default:
                handled = false;
                break;
        }
        return handled;
    }

    private void requestFolderRefresh() {
        if (mFolder != null) {
            if (mAsyncRefreshTask != null) {
                mAsyncRefreshTask.cancel(true);
            }
            mAsyncRefreshTask = new AsyncRefreshTask(mContext, mFolder);
            mAsyncRefreshTask.execute();
        }
    }

    @Override
    public void onPrepareDialog(int id, Dialog dialog, Bundle bundle) {
        // TODO(viki): Auto-generated method stub

    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        mActionBarView.onPrepareOptionsMenu(menu);
        return true;
    }

    @Override
    public void onPause() {
        isLoaderInitialized = false;
    }

    @Override
    public void onResume() {
        if (mActionBarView != null) {
            mActionBarView.onResume();
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        if (mAccount != null) {
            LogUtils.d(LOG_TAG, "Saving the account now");
            outState.putParcelable(SAVED_ACCOUNT, mAccount);
        }
        if (mConvListContext != null) {
            outState.putBundle(SAVED_LIST_CONTEXT, mConvListContext.toBundle());
        }
    }

    @Override
    public void onSearchRequested(String query) {
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_SEARCH);
        intent.putExtra(ConversationListContext.EXTRA_SEARCH_QUERY, query);
        intent.putExtra(Utils.EXTRA_ACCOUNT, mAccount);
        intent.setComponent(mActivity.getComponentName());
        mActivity.startActivity(intent);
    }

    @Override
    public void onStartDragMode() {
        // TODO(viki): Auto-generated method stub
    }

    @Override
    public void onStop() {
        // TODO(viki): Auto-generated method stub
    }

    @Override
    public void onStopDragMode() {
        // TODO(viki): Auto-generated method stub
    }

    /**
     * {@inheritDoc} Subclasses must override this to listen to mode changes
     * from the ViewMode. Subclasses <b>must</b> call the parent's
     * onViewModeChanged since the parent will handle common state changes.
     */
    @Override
    public void onViewModeChanged(int newMode) {
        // Perform any mode specific work here.
        // reset the action bar icon based on the mode. Why don't the individual
        // controllers do
        // this themselves?

        // In conversation list mode, clean up the conversation.
        if (newMode == ViewMode.CONVERSATION_LIST) {
            // Clean up the conversation here.
        }

        // We don't want to invalidate the options menu when switching to
        // conversation
        // mode, as it will happen when the conversation finishes loading.
        if (newMode != ViewMode.CONVERSATION) {
            mActivity.invalidateOptionsMenu();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        // TODO(viki): Auto-generated method stub
    }

    /**
     * @param savedState
     */
    protected void restoreListContext(Bundle savedState) {
        // TODO(viki): Restore the account, the folder, and the conversation, if any.
        Bundle listContextBundle = savedState.getBundle(SAVED_LIST_CONTEXT);
        if (listContextBundle != null) {
            mConvListContext = ConversationListContext.forBundle(listContextBundle);
            mFolder = mConvListContext.folder;
        }
    }

    /**
     * Restore the state from the previous bundle. Subclasses should call this
     * method from the parent class, since it performs important UI
     * initialization.
     *
     * @param savedState
     */
    protected void restoreState(Bundle savedState) {
        final Intent intent = mActivity.getIntent();
        if (savedState != null) {
            restoreListContext(savedState);
            mAccount = savedState.getParcelable(SAVED_ACCOUNT);
            mActionBarView.setAccount(mAccount);
            restartSettingsLoader();
        } else if (intent != null) {
            if (Intent.ACTION_VIEW.equals(intent.getAction())) {
                if (intent.hasExtra(Utils.EXTRA_ACCOUNT)) {
                    mAccount = ((Account) intent.getParcelableExtra(Utils.EXTRA_ACCOUNT));
                    mActionBarView.setAccount(mAccount);
                    mActivity.getLoaderManager().restartLoader(ACCOUNT_SETTINGS_LOADER, null, this);
                    mActivity.invalidateOptionsMenu();
                }
                if (intent.hasExtra(Utils.EXTRA_FOLDER)) {
                    // Open the folder.
                    LogUtils.d(LOG_TAG, "SHOW THE FOLDER at %s",
                            intent.getParcelableExtra(Utils.EXTRA_FOLDER));
                    onFolderChanged((Folder) intent.getParcelableExtra(Utils.EXTRA_FOLDER));
                }
                if (intent.hasExtra(Utils.EXTRA_CONVERSATION)) {
                    // Open the conversation.
                    LogUtils.d(LOG_TAG, "SHOW THE CONVERSATION at %s",
                            intent.getParcelableExtra(Utils.EXTRA_CONVERSATION));
                    showConversation((Conversation) intent
                            .getParcelableExtra(Utils.EXTRA_CONVERSATION));
                }
            } else if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
                mViewMode.enterSearchResultsListMode();
                mAccount = ((Account) intent.getParcelableExtra(Utils.EXTRA_ACCOUNT));
                mActionBarView.setAccount(mAccount);
                fetchSearchFolder(intent);
            }
        }
        // Create the accounts loader; this loads the account switch spinner.
        mActivity.getLoaderManager().initLoader(ACCOUNT_CURSOR_LOADER, null, this);
    }

    @Override
    public void setSubject(String subject) {
        // Do something useful with the subject. This requires changing the
        // conversation view's subject text.
    }

    /**
     * Children can override this method, but they must call super.showConversation().
     * {@inheritDoc}
     */
    @Override
    public void showConversation(Conversation conversation) {
        // Add the folder that we were viewing to the recent folders list.
        // We don't want to do this on every conversation access, the first is enough.
        if (!mFolderTouched) {
            mRecentFolderList.touchFolder(mFolder);
            mFolderTouched = true;
        }
    }

    @Override
    public void onConversationSelected(Conversation conversation) {
        mCurrentConversation = conversation;
        showConversation(mCurrentConversation);
        if (mConvListContext != null && mConvListContext.isSearchResult()) {
            mViewMode.enterSearchResultsConversationMode();
        } else {
            mViewMode.enterConversationMode();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        // Create a loader to listen in on account changes.
        if (id == ACCOUNT_CURSOR_LOADER) {
            return new CursorLoader(mContext, AccountCacheProvider.getAccountsUri(),
                    UIProvider.ACCOUNTS_PROJECTION, null, null, null);
        } else if (id == FOLDER_CURSOR_LOADER) {
            return new CursorLoader(mActivity.getActivityContext(), mFolder.uri,
                    UIProvider.FOLDERS_PROJECTION, null, null, null);
        } else if (id == ACCOUNT_SETTINGS_LOADER) {
            if (mAccount.settingsQueryUri != null) {
                return new CursorLoader(mActivity.getActivityContext(), mAccount.settingsQueryUri,
                        UIProvider.SETTINGS_PROJECTION, null, null, null);
            }
        }
        return null;
    }

    private boolean accountsUpdated(Cursor accountCursor) {
        // Check to see if the current account hasn't been set, or the account cursor is empty
        if (mAccount == null || !accountCursor.moveToFirst()) {
            return true;
        }

        // Check to see if the number of accounts are different, from the number we saw on the last
        // updated
        if (mCurrentAccountUris.size() != accountCursor.getCount()) {
            return true;
        }

        // Check to see if the account list is different or if the current account is not found in
        // the cursor.
        boolean foundCurrentAccount = false;
        do {
            final Uri accountUri =
                    Uri.parse(accountCursor.getString(UIProvider.ACCOUNT_URI_COLUMN));
            if (!foundCurrentAccount && mAccount.uri.equals(accountUri)) {
                foundCurrentAccount = true;
            }

            if (!mCurrentAccountUris.contains(accountUri)) {
                return true;
            }
        } while (accountCursor.moveToNext());

        // As long as we found the current account, the list hasn't been updated
        return !foundCurrentAccount;
    }

    /**
     * Update the accounts on the device. This currently loads the first account
     * in the list.
     *
     * @param loader
     * @param accounts cursor into the AccountCache
     * @return true if the update was successful, false otherwise
     */
    private boolean updateAccounts(Loader<Cursor> loader, Cursor accounts) {
        if (accounts == null || !accounts.moveToFirst()) {
            return false;
        }

        final Account[] allAccounts = Account.getAllAccounts(accounts);

        // Save the uris for the accounts
        mCurrentAccountUris.clear();
        for (Account account : allAccounts) {
            mCurrentAccountUris.add(account.uri);
        }

        final Account newAccount;
        if (mAccount == null || !mCurrentAccountUris.contains(mAccount.uri)) {
            accounts.moveToFirst();
            newAccount = new Account(accounts);
        } else {
            newAccount = mAccount;
        }
        // only bother updating the account/folder if the new account is different than the
        // existing one
        final boolean refetchFolderInfo = !newAccount.equals(mAccount);
        onAccountChanged(newAccount);

        if(refetchFolderInfo) {
            fetchAccountFolderInfo();
        }

        mActionBarView.setAccounts(allAccounts);
        return (allAccounts.length > 0);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        // We want to reinitialize only if we haven't ever been initialized, or
        // if the current account has vanished.
        final int id = loader.getId();
        if (data == null) {
            LogUtils.e(LOG_TAG, "Received null cursor from loader id: %d", id);
        }
        if (id == ACCOUNT_CURSOR_LOADER) {

            final boolean accountListUpdated = accountsUpdated(data);
            if (!isLoaderInitialized || accountListUpdated) {
                isLoaderInitialized = updateAccounts(loader, data);
            }
        } else if (id == FOLDER_CURSOR_LOADER) {
            // Check status of the cursor.
            if (data != null) {
                data.moveToFirst();
                Folder folder = new Folder(data);
                if (mConvListContext.isSearchResult()) {
                    if (mConversationListFragment != null) {
                        mConversationListFragment.onSearchFolderUpdated(folder);
                    }
                } else {
                    if (folder.isSyncInProgress()) {
                        mActionBarView.onRefreshStarted();
                    } else {
                        // Stop the spinner here.
                        mActionBarView.onRefreshStopped(folder.lastSyncResult);
                    }
                }
                LogUtils.v(LOG_TAG, "FOLDER STATUS = " + folder.syncStatus);
            }
        } else if (id == ACCOUNT_SETTINGS_LOADER) {
            if (data != null) {
                data.moveToFirst();
                onSettingsChanged(new Settings(data));
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        // Do nothing for now, since we don't have any state. When a load is
        // finished, the
        // onLoadFinished will be called and we will be fine.
    }

    @Override
    public void onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            int mode = mViewMode.getMode();
            if (mode == ViewMode.CONVERSATION_LIST) {
                mConversationListFragment.onTouchEvent(event);
            } else if (mode == ViewMode.CONVERSATION) {
                mConversationViewFragment.onTouchEvent(event);
            }
        }
    }

    private class FetchAccountFolderTask extends AsyncTask<Void, Void, ConversationListContext> {
        @Override
        public ConversationListContext doInBackground(Void... params) {
            return ConversationListContext.forFolder(mContext, mAccount, mFolder);
        }

        @Override
        public void onPostExecute(ConversationListContext result) {
            mConvListContext = result;
            setFolder(mConvListContext.folder);
            if (mFolderListFragment != null) {
                mFolderListFragment.selectFolder(mConvListContext.folder);
            }
            showConversationList(mConvListContext);
            mFetchAccountFolderTask = null;
        }
    }

    private class FetchSearchFolderTask extends AsyncTask<Void, Void, Folder> {
        String mQuery;
        public FetchSearchFolderTask(String query) {
            mQuery = query;
        }

        @Override
        public Folder doInBackground(Void... params) {
            Folder searchFolder = Folder.forSearchResults(mAccount, mQuery,
                    mActivity.getActivityContext());
            return searchFolder;
        }

        @Override
        public void onPostExecute(Folder folder) {
            setFolder(folder);
            mConvListContext = ConversationListContext.forSearchQuery(mAccount, mFolder, mQuery);
            showConversationList(mConvListContext);
            mActivity.invalidateOptionsMenu();
        }
    }
}
