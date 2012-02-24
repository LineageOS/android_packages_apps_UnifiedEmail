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
import android.app.LoaderManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.net.Uri;
import android.view.ActionMode;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.android.mail.R;
import com.android.mail.ConversationListContext;
import com.android.mail.compose.ComposeActivity;
import com.android.mail.providers.Account;
import com.android.mail.providers.AccountCacheProvider;
import com.android.mail.providers.Conversation;
import com.android.mail.providers.Folder;
import com.android.mail.providers.UIProvider;
import com.android.mail.providers.UIProvider.LastSyncResult;
import com.android.mail.ui.AsyncRefreshTask;
import com.android.mail.utils.LogUtils;
import com.android.mail.utils.Utils;

/**
 * This is an abstract implementation of the Activity Controller. This class knows how to
 * respond to menu items, state changes, layout changes, etc.  It weaves together the views and
 * listeners, dispatching actions to the respective underlying classes.
 *
 * <p>Even though this class is abstract, it should provide default implementations for most, if
 * not all the methods in the ActivityController interface. This makes the task of the subclasses
 * easier: OnePaneActivityController and TwoPaneActivityController can be concise when the common
 * functionality is in AbstractActivityController.
 *</p>
 * <p>
 * In the Gmail codebase, this was called BaseActivityController</p>
 */
public abstract class AbstractActivityController implements ActivityController {
    private static final String SAVED_CONVERSATION = "saved-conversation";
    private static final String SAVED_CONVERSATION_POSITION = "saved-conv-pos";
    // Keys for serialization of various information in Bundles.
    private static final String SAVED_LIST_CONTEXT = "saved-list-context";

    /**
     * Are we on a tablet device or not.
     */
    public final boolean IS_TABLET_DEVICE;

    protected Account mAccount;
    protected Folder mFolder;
    protected ActionBarView mActionBarView;
    protected final RestrictedActivity mActivity;
    protected final Context mContext;
    protected ConversationListContext mConvListContext;
    private FetchAccountFolderTask mFetchAccountFolderTask;
    protected Conversation mCurrentConversation;

    protected ConversationListFragment mConversationListFragment;
    /**
     * The current mode of the application. All changes in mode are initiated by the activity
     * controller. View mode changes are propagated to classes that attach themselves as listeners
     * of view mode changes.
     */
    protected final ViewMode mViewMode;
    protected ContentResolver mResolver;
    protected FolderListFragment mFolderListFragment;
    protected ConversationViewFragment mConversationViewFragment;
    protected boolean isLoaderInitialized = false;
    private AsyncRefreshTask mAsyncRefreshTask;

    private MenuItem mRefreshItem;
    private View mRefreshActionView;
    private boolean mRefreshInProgress;
    private Handler mHandler = new Handler();
    private Runnable mInvalidateMenu = new Runnable() {
        @Override
        public void run() {
            mActivity.invalidateOptionsMenu();
        }
    };
    private static final String LOG_TAG = new LogUtils().getLogTag();
    private static final int ACCOUNT_CURSOR_LOADER = 0;
    private static final int FOLDER_CURSOR_LOADER = 1;

    public AbstractActivityController(MailActivity activity, ViewMode viewMode) {
        mActivity = activity;
        mViewMode = viewMode;
        mContext = activity.getApplicationContext();
        IS_TABLET_DEVICE = Utils.useTabletUI(mContext);
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
    public void enterSearchMode() {
        // TODO(viki): Auto-generated method stub
    }

    @Override
    public void exitSearchMode() {
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

    @Override
    public void handleSearchRequested() {
        // TODO(viki): Auto-generated method stub
    }

    /**
     * Initialize the action bar. This is not visible to OnePaneController and TwoPaneController so
     * they cannot override this behavior.
     */
    private void initCustomActionBarView() {
        ActionBar actionBar = mActivity.getActionBar();
        mActionBarView = (MailActionBar) LayoutInflater.from(mContext).inflate(
                R.layout.actionbar_view, null);

        if (actionBar != null && mActionBarView != null) {
            // Why have a different variable for the same thing? We should apply the same actions
            // on mActionBarView instead.
            mActionBarView.initialize(mActivity, this, mViewMode, actionBar);
            actionBar.setCustomView((LinearLayout) mActionBarView, new ActionBar.LayoutParams(
                    LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
            actionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM,
                    ActionBar.DISPLAY_SHOW_CUSTOM | ActionBar.DISPLAY_SHOW_TITLE);
        }
    }

    /**
     * Returns whether the conversation list fragment is visible or not. Different layouts will have
     * their own notion on the visibility of fragments, so this method needs to be overriden.
     * @return
     */
    protected abstract boolean isConversationListVisible();

    @Override
    public void onAccountChanged(Account account) {
        if (!account.equals(mAccount)) {
            mAccount = account;

            fetchAccountFolderInfo();
        }
    }

    private void fetchAccountFolderInfo() {
        if (mFetchAccountFolderTask != null) {
            mFetchAccountFolderTask.cancel(true);
        }
        mFetchAccountFolderTask = new FetchAccountFolderTask();
        mFetchAccountFolderTask.execute();
    }

    @Override
    public void onFolderChanged(Folder folder) {
        if (!folder.equals(mFolder)) {
            setFolder(folder);
            final Intent intent = mActivity.getIntent();
            intent.putExtra(ConversationListContext.EXTRA_FOLDER, mFolder);
            //  TODO(viki): Show the list context from Intent
            mConvListContext = ConversationListContext.forIntent(mContext, mAccount, intent);
            // Instead of this, switch to the conversation list mode and have that do the right
            // things automatically.
            showConversationList(mConvListContext);
            mViewMode.enterConversationListMode();
        }
    }

    private void setFolder(Folder folder) {
        // Start watching folder for sync status.
        if (!folder.equals(mFolder)) {
            mRefreshInProgress = false;
            mFolder = folder;
            mActivity.getLoaderManager().restartLoader(FOLDER_CURSOR_LOADER, null, this);
        }
    }

    @Override
    public void onActionModeFinished(ActionMode mode) {
        // TODO(viki): Auto-generated method stub
    }

    @Override
    public void onActionModeStarted(ActionMode mode) {
        // TODO(viki): Auto-generated method stub
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
        final Intent intent = mActivity.getIntent();
        // Get a Loader to the Account
        mActivity.getLoaderManager().initLoader(ACCOUNT_CURSOR_LOADER, null, this);
        // Allow shortcut keys to function for the ActionBar and menus.
        mActivity.setDefaultKeyMode(Activity.DEFAULT_KEYS_SHORTCUT);
        mResolver = mActivity.getContentResolver();

        // All the individual UI components listen for ViewMode changes. This simplifies the
        // amount of logic in the AbstractActivityController, but increases the possibility of
        // timing-related bugs.
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
        mRefreshItem = menu.findItem(R.id.refresh);
        return true;
    }

    @Override
    public void onEndBulkOperation() {
        // TODO(viki): Auto-generated method stub

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
            case R.id.preferences:
                showPreferences();
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

    public void onRefreshStarted() {
        if (!mRefreshInProgress) {
            mRefreshInProgress = true;
            mHandler.post(mInvalidateMenu);
        }
    }

    public void onRefreshStopped(int status) {
        if (mRefreshInProgress) {
            mRefreshInProgress = false;
            switch (status) {
                case LastSyncResult.SUCCESS:
                    break;
                default:
                    Context context = mActivity.getActivityContext();
                    Toast.makeText(context, Utils.getSyncStatusText(context, status),
                            Toast.LENGTH_LONG).show();
                    break;
            }
            mHandler.post(mInvalidateMenu);
        }
    }

    @Override
    public void onPause() {
        isLoaderInitialized = false;
    }

    @Override
    public void onPrepareDialog(int id, Dialog dialog, Bundle bundle) {
        // TODO(viki): Auto-generated method stub

    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
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
        return true;
    }

    @Override
    public void onResume() {
        if (mActionBarView != null) {
            mActionBarView.onResume();
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        if (mConvListContext != null) {
            outState.putBundle(SAVED_LIST_CONTEXT, mConvListContext.toBundle());
        }
    }

    @Override
    public void showPreferences() {
        final Intent preferenceIntent = new Intent(Intent.ACTION_EDIT,
                Uri.parse(mAccount.settingIntentUri));
        mActivity.startActivity(preferenceIntent);
    }

    @Override
    public void onSearchRequested() {
        // TODO(viki): Auto-generated method stub
    }

    @Override
    public void onStartBulkOperation() {
        // TODO(viki): Auto-generated method stub
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
     * {@inheritDoc}
     *
     * Subclasses must override this to listen to mode changes from the ViewMode. Subclasses
     * <b>must</b> call the parent's onViewModeChanged since the parent will handle common state
     * changes.
     */
    @Override
    public void onViewModeChanged(int newMode) {
        // Perform any mode specific work here.
        // reset the action bar icon based on the mode. Why don't the individual controllers do
        // this themselves?

        // In conversation list mode, clean up the conversation.
        if (newMode == ViewMode.CONVERSATION_LIST) {
            // Clean up the conversation here.
        }

        // We don't want to invalidate the options menu when switching to conversation
        // mode, as it will happen when the conversation finishes loading.
        if (newMode != ViewMode.CONVERSATION) {
            mActivity.invalidateOptionsMenu();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        // TODO(viki): Auto-generated method stub
    }

    @Override
    public void reloadSearch(String string) {
        // TODO(viki): Auto-generated method stub
    }

    /**
     * @param savedState
     */
    protected void restoreListContext(Bundle savedState) {
        // TODO(viki): Auto-generated method stub
        Bundle listContextBundle = savedState.getBundle(SAVED_LIST_CONTEXT);
        if (listContextBundle != null) {
            mConvListContext = ConversationListContext.forBundle(listContextBundle);
        }
    }

    /**
     * Restore the state from the previous bundle. Subclasses should call this method from the
     * parent class, since it performs important UI initialization.
     * @param savedState
     */
    protected void restoreState(Bundle savedState) {
        if (savedState != null) {
            restoreListContext(savedState);
            // Attach the menu handler here.

            // Restore the view mode
            mViewMode.handleRestore(savedState);
        }
    }

    @Override
    public void setSubject(String subject) {
        // Do something useful with the subject. This requires changing the
        // conversation view's subject text.
    }

    @Override
    public void startActionBarStatusCursorLoader(String account) {
        // TODO(viki): Auto-generated method stub
    }

    @Override
    public void stopActionBarStatusCursorLoader(String account) {
        // TODO(viki): Auto-generated method stub
    }

    @Override
    public void toggleStar(boolean toggleOn, long conversationId, long maxMessageId) {
        // TODO(viki): Auto-generated method stub
    }

    @Override
    public void onConversationSelected(Conversation conversation) {
        mCurrentConversation = conversation;
        showConversation(mCurrentConversation);
        mViewMode.enterConversationMode();
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        // Create a loader to listen in on account changes.
        if (id == ACCOUNT_CURSOR_LOADER) {
            return new CursorLoader(mContext,
                    AccountCacheProvider.getAccountsUri(), UIProvider.ACCOUNTS_PROJECTION, null,
                    null, null);
        } else if (id == FOLDER_CURSOR_LOADER) {
            return new CursorLoader(mActivity.getActivityContext(),
                    Uri.parse(mFolder.uri), UIProvider.FOLDERS_PROJECTION, null, null, null);
        }
        return null;
    }

    /**
     * Return whether the given account exists in the cursor.
     * @param accountCursor
     * @param account
     * @return true if the account exists in the account cursor, false otherwise.
     */
    private boolean existsInCursor(Cursor accountCursor, Account account) {
        accountCursor.moveToFirst();
        do {
            if (account.equals(new Account(accountCursor)))
                return true;
        } while (accountCursor.moveToNext());
        return false;
    }

    /**
     * Update the accounts on the device. This currently loads the first account in the list.
     * @param loader
     * @param data
     * @return true if the update was successful, false otherwise
     */
    private boolean updateAccounts(Loader<Cursor> loader, Cursor accounts) {
        // Load the first account in the absence of any other information.
        if (accounts == null || !accounts.moveToFirst()) {
            return false;
        }
        mAccount = new Account(accounts);
        fetchAccountFolderInfo();
        return true;
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        // We want to reinitialize only if we haven't ever been initialized, or
        // if the current account has vanished.
        int id = loader.getId();
        if (id == ACCOUNT_CURSOR_LOADER) {
            if (!isLoaderInitialized || !existsInCursor(data, mAccount)) {
                isLoaderInitialized = updateAccounts(loader, data);
            }
        } else if (id == FOLDER_CURSOR_LOADER) {
            // Check status of the cursor.
            if (data != null) {
                data.moveToFirst();
                Folder folder = new Folder(data);
                if (folder.isSyncInProgress()) {
                    onRefreshStarted();
                } else {
                    // Stop the spinner here.
                    onRefreshStopped(folder.lastSyncResult);
                }
                LogUtils.v(LOG_TAG, "FOLDER STATUS = " + folder.syncStatus);
            }
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        // Do nothing for now, since we don't have any state. When a load is finished, the
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
            final Intent intent = mActivity.getIntent();
            // TODO(viki): Show the list context from Intent
            return ConversationListContext.forIntent(mContext, mAccount, intent);
        }

        @Override
        public void onPostExecute(ConversationListContext result) {
            mConvListContext = result;
            setFolder(mConvListContext.mFolder);
            showConversationList(mConvListContext);
            mViewMode.enterConversationListMode();
            mFetchAccountFolderTask = null;
        }
    }
}
