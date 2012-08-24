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
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.LoaderManager;
import android.app.SearchManager;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.CursorLoader;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.database.DataSetObservable;
import android.database.DataSetObserver;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.provider.SearchRecentSuggestions;
import android.view.DragEvent;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

import com.android.mail.ConversationListContext;
import com.android.mail.R;
import com.android.mail.browse.ConversationCursor;
import com.android.mail.browse.ConversationPagerController;
import com.android.mail.browse.MessageCursor.ConversationMessage;
import com.android.mail.browse.SelectedConversationsActionMenu;
import com.android.mail.browse.SyncErrorDialogFragment;
import com.android.mail.compose.ComposeActivity;
import com.android.mail.providers.Account;
import com.android.mail.providers.Conversation;
import com.android.mail.providers.ConversationInfo;
import com.android.mail.providers.Folder;
import com.android.mail.providers.FolderWatcher;
import com.android.mail.providers.MailAppProvider;
import com.android.mail.providers.Settings;
import com.android.mail.providers.SuggestionsProvider;
import com.android.mail.providers.UIProvider;
import com.android.mail.providers.UIProvider.AccountCapabilities;
import com.android.mail.providers.UIProvider.AccountCursorExtraKeys;
import com.android.mail.providers.UIProvider.ConversationColumns;
import com.android.mail.providers.UIProvider.FolderCapabilities;
import com.android.mail.ui.ActionableToastBar.ActionClickedListener;
import com.android.mail.utils.ContentProviderTask;
import com.android.mail.utils.LogTag;
import com.android.mail.utils.LogUtils;
import com.android.mail.utils.Utils;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Set;
import java.util.TimerTask;


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
    // Keys for serialization of various information in Bundles.
    /** Tag for {@link #mAccount} */
    private static final String SAVED_ACCOUNT = "saved-account";
    /** Tag for {@link #mFolder} */
    private static final String SAVED_FOLDER = "saved-folder";
    /** Tag for {@link #mCurrentConversation} */
    private static final String SAVED_CONVERSATION = "saved-conversation";
    /** Tag for {@link #mSelectedSet} */
    private static final String SAVED_SELECTED_SET = "saved-selected-set";
    private static final String SAVED_TOAST_BAR_OP = "saved-toast-bar-op";
    protected static final String SAVED_HIERARCHICAL_FOLDER = "saved-hierarchical-folder";
    /** Tag for {@link ConversationListContext#searchQuery} */
    private static final String SAVED_QUERY = "saved-query";

    /** Tag  used when loading a wait fragment */
    protected static final String TAG_WAIT = "wait-fragment";
    /** Tag used when loading a conversation list fragment. */
    public static final String TAG_CONVERSATION_LIST = "tag-conversation-list";
    /** Tag used when loading a folder list fragment. */
    protected static final String TAG_FOLDER_LIST = "tag-folder-list";

    protected Account mAccount;
    protected Folder mFolder;
    protected MailActionBarView mActionBarView;
    protected final ControllableActivity mActivity;
    protected final Context mContext;
    private final FragmentManager mFragmentManager;
    protected final RecentFolderList mRecentFolderList;
    protected ConversationListContext mConvListContext;
    protected Conversation mCurrentConversation;

    /** A {@link android.content.BroadcastReceiver} that suppresses new e-mail notifications. */
    private SuppressNotificationReceiver mNewEmailReceiver = null;

    protected Handler mHandler = new Handler();

    /**
     * The current mode of the application. All changes in mode are initiated by
     * the activity controller. View mode changes are propagated to classes that
     * attach themselves as listeners of view mode changes.
     */
    protected final ViewMode mViewMode;
    protected ContentResolver mResolver;
    protected boolean isLoaderInitialized = false;
    private AsyncRefreshTask mAsyncRefreshTask;

    private boolean mDestroyed;

    private final Set<Uri> mCurrentAccountUris = Sets.newHashSet();
    protected ConversationCursor mConversationListCursor;
    private final DataSetObservable mConversationListObservable = new DataSetObservable() {
        @Override
        public void registerObserver(DataSetObserver observer) {
            final int count = mObservers.size();
            super.registerObserver(observer);
            LogUtils.d(LOG_TAG, "IN AAC.register(List)Observer: %s before=%d after=%d", observer,
                    count, mObservers.size());
        }
        @Override
        public void unregisterObserver(DataSetObserver observer) {
            final int count = mObservers.size();
            super.unregisterObserver(observer);
            LogUtils.d(LOG_TAG, "IN AAC.unregister(List)Observer: %s before=%d after=%d", observer,
                    count, mObservers.size());
        }
    };

    private RefreshTimerTask mConversationListRefreshTask;

    /** Listeners that are interested in changes to the current account. */
    private final DataSetObservable mAccountObservers = new DataSetObservable() {
        @Override
        public void registerObserver(DataSetObserver observer) {
            final int count = mObservers.size();
            super.registerObserver(observer);
            LogUtils.d(LOG_TAG, "IN AAC.register(Account)Observer: %s before=%d after=%d",
                    observer, count, mObservers.size());
        }
        @Override
        public void unregisterObserver(DataSetObserver observer) {
            final int count = mObservers.size();
            super.unregisterObserver(observer);
            LogUtils.d(LOG_TAG, "IN AAC.unregister(Account)Observer: %s before=%d after=%d",
                    observer, count, mObservers.size());
        }
    };

    /**
     * Selected conversations, if any.
     */
    private final ConversationSelectionSet mSelectedSet = new ConversationSelectionSet();

    private final int mFolderItemUpdateDelayMs;

    /** Keeps track of selected and unselected conversations */
    final protected ConversationPositionTracker mTracker;

    /**
     * Action menu associated with the selected set.
     */
    SelectedConversationsActionMenu mCabActionMenu;
    protected ActionableToastBar mToastBar;
    protected ConversationPagerController mPagerController;

    // this is split out from the general loader dispatcher because its loader doesn't return a
    // basic Cursor
    private final ConversationListLoaderCallbacks mListCursorCallbacks =
            new ConversationListLoaderCallbacks();

    private final DataSetObservable mFolderObservable = new DataSetObservable();

    protected static final String LOG_TAG = LogTag.getLogTag();
    /** Constants used to differentiate between the types of loaders. */
    private static final int LOADER_ACCOUNT_CURSOR = 0;
    private static final int LOADER_FOLDER_CURSOR = 2;
    private static final int LOADER_RECENT_FOLDERS = 3;
    private static final int LOADER_CONVERSATION_LIST = 4;
    private static final int LOADER_ACCOUNT_INBOX = 5;
    private static final int LOADER_SEARCH = 6;
    private static final int LOADER_ACCOUNT_UPDATE_CURSOR = 7;
    /**
     * Guaranteed to be the last loader ID used by the activity. Loaders are owned by Activity or
     * fragments, and within an activity, loader IDs need to be unique. A hack to ensure that the
     * {@link FolderWatcher} can create its folder loaders without clashing with the IDs of those
     * of the {@link AbstractActivityController}. Currently, the {@link FolderWatcher} is the only
     * other class that uses this activity's LoaderManager. If another class needs activity-level
     * loaders, consider consolidating the loaders in a central location: a UI-less fragment
     * perhaps.
     */
    public static final int LAST_LOADER_ID = 100;

    private static final int ADD_ACCOUNT_REQUEST_CODE = 1;
    private static final int REAUTHENTICATE_REQUEST_CODE = 2;

    /** The pending destructive action to be carried out before swapping the conversation cursor.*/
    private DestructiveAction mPendingDestruction;
    protected AsyncRefreshTask mFolderSyncTask;
    // Task for setting any share intents for the account to enabled.
    // This gets cancelled if the user kills the app before it finishes, and
    // will just run the next time the user opens the app.
    private AsyncTask<String, Void, Void> mEnableShareIntents;
    private Folder mFolderListFolder;
    private boolean mIsDragHappening;
    private int mShowUndoBarDelay;
    public static final String SYNC_ERROR_DIALOG_FRAGMENT_TAG = "SyncErrorDialogFragment";

    public AbstractActivityController(MailActivity activity, ViewMode viewMode) {
        mActivity = activity;
        mFragmentManager = mActivity.getFragmentManager();
        mViewMode = viewMode;
        mContext = activity.getApplicationContext();
        mRecentFolderList = new RecentFolderList(mContext);
        mTracker = new ConversationPositionTracker(this);
        // Allow the fragment to observe changes to its own selection set. No other object is
        // aware of the selected set.
        mSelectedSet.addObserver(this);

        mFolderItemUpdateDelayMs =
                mContext.getResources().getInteger(R.integer.folder_item_refresh_delay_ms);
        mShowUndoBarDelay =
                mContext.getResources().getInteger(R.integer.show_undo_bar_delay_ms);
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
        final int mode = mViewMode.getMode();
        final int helpContextResId;
        switch (mode) {
            case ViewMode.WAITING_FOR_ACCOUNT_INITIALIZATION:
                helpContextResId = R.string.wait_help_context;
                break;
            default:
                helpContextResId = R.string.main_help_context;
        }
        return mContext.getString(helpContextResId);
    }

    @Override
    public final ConversationCursor getConversationListCursor() {
        return mConversationListCursor;
    }

    /**
     * Check if the fragment is attached to an activity and has a root view.
     * @param in
     * @return true if the fragment is valid, false otherwise
     */
    private static final boolean isValidFragment(Fragment in) {
        if (in == null || in.getActivity() == null || in.getView() == null) {
            return false;
        }
        return true;
    }

    /**
     * Get the conversation list fragment for this activity. If the conversation list fragment
     * is not attached, this method returns null
     *
     */
    protected ConversationListFragment getConversationListFragment() {
        final Fragment fragment = mFragmentManager.findFragmentByTag(TAG_CONVERSATION_LIST);
        if (isValidFragment(fragment)) {
            return (ConversationListFragment) fragment;
        }
        return null;
    }

    /**
     * Returns the folder list fragment attached with this activity. If no such fragment is attached
     * this method returns null.
     *
     */
    protected FolderListFragment getFolderListFragment() {
        final Fragment fragment = mFragmentManager.findFragmentByTag(TAG_FOLDER_LIST);
        if (isValidFragment(fragment)) {
            return (FolderListFragment) fragment;
        }
        return null;
    }

    /**
     * Initialize the action bar. This is not visible to OnePaneController and
     * TwoPaneController so they cannot override this behavior.
     */
    private void initializeActionBar() {
        final ActionBar actionBar = mActivity.getActionBar();
        if (actionBar == null) {
            return;
        }

        // be sure to inherit from the ActionBar theme when inflating
        final LayoutInflater inflater = LayoutInflater.from(actionBar.getThemedContext());
        final boolean isSearch = mActivity.getIntent() != null
                && Intent.ACTION_SEARCH.equals(mActivity.getIntent().getAction());
        mActionBarView = (MailActionBarView) inflater.inflate(
                isSearch ? R.layout.search_actionbar_view : R.layout.actionbar_view, null);
        mActionBarView.initialize(mActivity, this, mViewMode, actionBar, mRecentFolderList);
    }

    /**
     * Attach the action bar to the activity.
     */
    private void attachActionBar() {
        final ActionBar actionBar = mActivity.getActionBar();
        if (actionBar != null && mActionBarView != null) {
            actionBar.setCustomView(mActionBarView, new ActionBar.LayoutParams(
                    LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
            actionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM,
                    ActionBar.DISPLAY_SHOW_CUSTOM | ActionBar.DISPLAY_SHOW_TITLE);
            mActionBarView.attach();
        }
        mViewMode.addListener(mActionBarView);
    }

    /**
     * Returns whether the conversation list fragment is visible or not.
     * Different layouts will have their own notion on the visibility of
     * fragments, so this method needs to be overriden.
     *
     */
    protected abstract boolean isConversationListVisible();

    /**
     * Switch the current account to the one provided as an argument to the method.
     * @param account new account
     * @param shouldReloadInbox whether the default inbox should be reloaded.
     */
    private void switchAccount(Account account, boolean shouldReloadInbox){
        // Current account is different from the new account, restart loaders and show
        // the account Inbox.
        mAccount = account;
        LogUtils.d(LOG_TAG, "AbstractActivityController.switchAccount(): mAccount = %s",
                mAccount.uri);
        cancelRefreshTask();
        // If we are currently listening to changes in conversation, the account switch is going to
        // make us do a lot of un-necessary work. Let's stop listening to changes.
        if (mPagerController != null) {
            mPagerController.stopListening();
        }
        updateSettings();
        if (shouldReloadInbox) {
            loadAccountInbox();
        }
        restartOptionalLoader(LOADER_RECENT_FOLDERS);
        mActivity.invalidateOptionsMenu();
        disableNotificationsOnAccountChange(mAccount);
        restartOptionalLoader(LOADER_ACCOUNT_UPDATE_CURSOR);
        MailAppProvider.getInstance().setLastViewedAccount(mAccount.uri.toString());
    }

    @Override
    public void onAccountChanged(Account account) {
        LogUtils.d(LOG_TAG, "onAccountChanged (%s) called.", account);
        // Is the account or account settings different from the existing account?
        final boolean firstLoad = mAccount == null;
        final boolean accountChanged = firstLoad || !account.uri.equals(mAccount.uri);
        final boolean settingsChanged = firstLoad || !account.settings.equals(mAccount.settings);
        if (accountChanged || settingsChanged) {
            if (account != null) {
                final String accountName = account.name;
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        MailActivity.setForegroundNdef(MailActivity.getMailtoNdef(accountName));
                    }
                });
            }
            if (accountChanged) {
                commitDestructiveActions(false);
            }
            switchAccount(account, accountChanged);
        }
    }

    /**
     * Changes the settings for the current account. The new settings are provided as a parameter.
     * @param settings
     */
    public void updateSettings() {
        mAccountObservers.notifyChanged();
        resetActionBarIcon();
        mActivity.invalidateOptionsMenu();
        // If the user was viewing the default Inbox here, and the new setting contains a different
        // default Inbox, we don't want to load a different folder here. So do not change the
        // current folder.
    }

    /**
     * Adds a listener interested in change in the current account. If a class is storing a
     * reference to the current account, it should listen on changes, so it can receive updates to
     * settings. Must happen in the UI thread.
     */
    @Override
    public void registerAccountObserver(DataSetObserver obs) {
        mAccountObservers.registerObserver(obs);
    }

    /**
     * Removes a listener from receiving current account changes.
     * Must happen in the UI thread.
     */
    @Override
    public void unregisterAccountObserver(DataSetObserver obs) {
        mAccountObservers.unregisterObserver(obs);
    }

    @Override
    public Account getAccount() {
        return mAccount;
    }

    private void fetchSearchFolder(Intent intent) {
        Bundle args = new Bundle();
        args.putString(ConversationListContext.EXTRA_SEARCH_QUERY, intent
                .getStringExtra(ConversationListContext.EXTRA_SEARCH_QUERY));
        mActivity.getLoaderManager().restartLoader(LOADER_SEARCH, args, this);
    }

    @Override
    public void onFolderChanged(Folder folder) {
        changeFolder(folder, null);
    }

    /**
     * Sets the folder state without changing view mode and without creating a list fragment, if
     * possible.
     * @param folder
     */
    private void setListContext(Folder folder, String query) {
        updateFolder(folder);
        if (query != null) {
            mConvListContext = ConversationListContext.forSearchQuery(mAccount, mFolder, query);
        } else {
            mConvListContext = ConversationListContext.forFolder(mAccount, mFolder);
        }
        cancelRefreshTask();
    }

    /**
     * Changes the folder to the value provided here. This causes the view mode to change.
     * @param folder the folder to change to
     * @param query if non-null, this represents the search string that the folder represents.
     */
    private void changeFolder(Folder folder, String query) {
        if (!Objects.equal(mFolder, folder)) {
            commitDestructiveActions(false);
        }
        if (folder != null && !folder.equals(mFolder)
                || (mViewMode.getMode() != ViewMode.CONVERSATION_LIST)) {
            setListContext(folder, query);
            showConversationList(mConvListContext);
        }
    }

    /**
     * Update the conversation list to {@link #mConvListContext} without creating a new frament if
     * possible.
     */
    protected abstract void updateConversationList();

    private void setFirstFolder(Folder folder, String query) {
        setListContext(folder, query);
        updateConversationList();
    }

    @Override
    public void onFolderSelected(Folder folder) {
        onFolderChanged(folder);
    }

    /**
     * Update the recent folders. This only needs to be done once when accessing a new folder.
     */
    private void updateRecentFolderList() {
        if (mFolder != null) {
            mRecentFolderList.touchFolder(mFolder, mAccount);
        }
    }

    // TODO(mindyp): set this up to store a copy of the folder as a transient
    // field in the account.
    @Override
    public void loadAccountInbox() {
        restartOptionalLoader(LOADER_ACCOUNT_INBOX);
    }

    /** Set the current folder */
    private void updateFolder(Folder folder) {
        // Start watching folder for sync status.
        boolean wasNull = mFolder == null;
        if (folder != null && !folder.equals(mFolder) && folder.isInitialized()) {
            LogUtils.d(LOG_TAG, "AbstractActivityController.setFolder(%s)", folder.name);
            final LoaderManager lm = mActivity.getLoaderManager();
            mFolder = folder;
            mActionBarView.setFolder(mFolder);

            // Stop listening to changes to the previous folder.
            if (mPagerController != null) {
                mPagerController.stopListening();
            }

            // Only when we switch from one folder to another do we want to restart the
            // folder and conversation list loaders (to trigger onCreateLoader).
            // The first time this runs when the activity is [re-]initialized, we want to re-use the
            // previous loader's instance and data upon configuration change (e.g. rotation).
            // If there was not already an instance of the loader, init it.
            if (lm.getLoader(LOADER_FOLDER_CURSOR) == null) {
                lm.initLoader(LOADER_FOLDER_CURSOR, null, this);
            } else {
                lm.restartLoader(LOADER_FOLDER_CURSOR, null, this);
            }
            // In this case, we are starting from no folder, which would occur
            // the first time the app was launched or on orientation changes.
            // We want to attach to an existing loader, if available.
            if (wasNull || lm.getLoader(LOADER_CONVERSATION_LIST) == null) {
                lm.initLoader(LOADER_CONVERSATION_LIST, null, mListCursorCallbacks);
            } else {
                // However, if there was an existing folder AND we have changed
                // folders, we want to restart the loader to get the information
                // for the newly selected folder
                lm.destroyLoader(LOADER_CONVERSATION_LIST);
                lm.initLoader(LOADER_CONVERSATION_LIST, null, mListCursorCallbacks);
            }
        } else if (!folder.isInitialized()) {
            LogUtils.e(LOG_TAG, new Error(), "Uninitialized Folder %s in setFolder.", folder);
        }
    }

    @Override
    public Folder getFolder() {
        return mFolder;
    }

    @Override
    public Folder getHierarchyFolder() {
        return mFolderListFolder;
    }

    @Override
    public void setHierarchyFolder(Folder folder) {
        mFolderListFolder = folder;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case ADD_ACCOUNT_REQUEST_CODE:
                // We were waiting for the user to create an account
                if (resultCode == Activity.RESULT_OK) {
                    // restart the loader to get the updated list of accounts
                    mActivity.getLoaderManager().initLoader(
                            LOADER_ACCOUNT_CURSOR, null, this);
                } else {
                    // The user failed to create an account, just exit the app
                    mActivity.finish();
                }
                break;
            case REAUTHENTICATE_REQUEST_CODE:
                if (resultCode == Activity.RESULT_OK) {
                    // The user successfully authenticated, attempt to refresh the list
                    final Uri refreshUri = mFolder != null ? mFolder.refreshUri : null;
                    if (refreshUri != null) {
                        startAsyncRefreshTask(refreshUri);
                    }
                }
                break;
        }
    }

    @Override
    public void onConversationListVisibilityChanged(boolean visible) {
        if (mConversationListCursor != null) {
            // The conversation list is visible.
            Utils.setConversationCursorVisibility(mConversationListCursor, visible);
        }
    }

    /**
     * Called when a conversation is visible. Child classes must call the super class implementation
     * before performing local computation.
     */
    @Override
    public void onConversationVisibilityChanged(boolean visible) {
        return;
    }

    @Override
    public boolean onCreate(Bundle savedState) {
        initializeActionBar();
        // Allow shortcut keys to function for the ActionBar and menus.
        mActivity.setDefaultKeyMode(Activity.DEFAULT_KEYS_SHORTCUT);
        mResolver = mActivity.getContentResolver();
        mNewEmailReceiver = new SuppressNotificationReceiver();
        mRecentFolderList.initialize(mActivity);

        // All the individual UI components listen for ViewMode changes. This
        // simplifies the amount of logic in the AbstractActivityController, but increases the
        // possibility of timing-related bugs.
        mViewMode.addListener(this);
        mPagerController = new ConversationPagerController(mActivity, this);
        mToastBar = (ActionableToastBar) mActivity.findViewById(R.id.toast_bar);
        attachActionBar();

        final Intent intent = mActivity.getIntent();
        // Immediately handle a clean launch with intent, and any state restoration
        // that does not rely on restored fragments or loader data
        // any state restoration that relies on those can be done later in
        // onRestoreInstanceState, once fragments are up and loader data is re-delivered
        if (savedState != null) {
            if (savedState.containsKey(SAVED_ACCOUNT)) {
                setAccount((Account) savedState.getParcelable(SAVED_ACCOUNT));
                mActivity.invalidateOptionsMenu();
            }
            if (savedState.containsKey(SAVED_FOLDER)) {
                final Folder folder = (Folder) savedState.getParcelable(SAVED_FOLDER);
                final String query = savedState.getString(SAVED_QUERY, null);
                setFirstFolder(folder, query);
            }
        } else if (intent != null) {
            handleIntent(intent);
        }
        // Create the accounts loader; this loads the account switch spinner.
        mActivity.getLoaderManager().initLoader(LOADER_ACCOUNT_CURSOR, null, this);
        return true;
    }

    @Override
    public void onRestart() {
        DialogFragment fragment = (DialogFragment)
                mFragmentManager.findFragmentByTag(SYNC_ERROR_DIALOG_FRAGMENT_TAG);
        if (fragment != null) {
            fragment.dismiss();
        }
    }

    @Override
    public Dialog onCreateDialog(int id, Bundle bundle) {
        return null;
    }

    @Override
    public final boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = mActivity.getMenuInflater();
        inflater.inflate(mActionBarView.getOptionsMenuId(), menu);
        mActionBarView.onCreateOptionsMenu(menu);
        return true;
    }

    @Override
    public final boolean onKeyDown(int keyCode, KeyEvent event) {
        // TODO(viki): Auto-generated method stub
        return false;
    }

    @Override
    public final boolean onOptionsItemSelected(MenuItem item) {
        final int id = item.getItemId();
        LogUtils.d(LOG_TAG, "AbstractController.onOptionsItemSelected(%d) called.", id);
        boolean handled = true;
        final Collection<Conversation> target = Conversation.listOf(mCurrentConversation);
        final Settings settings = (mAccount == null) ? null : mAccount.settings;
        // The user is choosing a new action; commit whatever they had been doing before.
        commitDestructiveActions(true);
        switch (id) {
            case R.id.archive: {
                final boolean showDialog = (settings != null && settings.confirmArchive);
                confirmAndDelete(target, showDialog, R.plurals.confirm_archive_conversation,
                        getAction(R.id.archive, target));
                break;
            }
            case R.id.remove_folder:
                delete(target, getRemoveFolder(target, mFolder, true, false, true));
                break;
            case R.id.delete: {
                final boolean showDialog = (settings != null && settings.confirmDelete);
                confirmAndDelete(target, showDialog, R.plurals.confirm_delete_conversation,
                        getAction(R.id.delete, target));
                break;
            }
            case R.id.mark_important:
                updateConversation(Conversation.listOf(mCurrentConversation),
                        ConversationColumns.PRIORITY, UIProvider.ConversationPriority.HIGH);
                break;
            case R.id.mark_not_important:
                if (mFolder != null && mFolder.isImportantOnly()) {
                    delete(target, getAction(R.id.mark_not_important, target));
                } else {
                    updateConversation(Conversation.listOf(mCurrentConversation),
                            ConversationColumns.PRIORITY, UIProvider.ConversationPriority.LOW);
                }
                break;
            case R.id.mute:
                delete(target, getAction(R.id.mute, target));
                break;
            case R.id.report_spam:
                delete(target, getAction(R.id.report_spam, target));
                break;
            case R.id.mark_not_spam:
                // Currently, since spam messages are only shown in list with other spam messages,
                // marking a message not as spam is a destructive action
                delete(target, getAction(R.id.mark_not_spam, target));
                break;
            case R.id.report_phishing:
                delete(target, getAction(R.id.report_phishing, target));
                break;
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
            case R.id.folder_options:
                Utils.showFolderSettings(mActivity.getActivityContext(), mAccount, mFolder);
                break;
            case R.id.help_info_menu_item:
                Utils.showHelp(mActivity.getActivityContext(), mAccount, getHelpContext());
                break;
            case R.id.feedback_menu_item:
                Utils.sendFeedback(mActivity.getActivityContext(), mAccount, false);
                break;
            case R.id.manage_folders_item:
                Utils.showManageFolder(mActivity.getActivityContext(), mAccount);
                break;
            case R.id.change_folder:
                new FoldersSelectionDialog(mActivity.getActivityContext(), mAccount, this,
                        Conversation.listOf(mCurrentConversation), false, mFolder).show();
                break;
            default:
                handled = false;
                break;
        }
        return handled;
    }

    @Override
    public void updateConversation(Collection<Conversation> target, ContentValues values) {
        mConversationListCursor.updateValues(mContext, target, values);
        refreshConversationList();
    }

    @Override
    public void updateConversation(Collection <Conversation> target, String columnName,
            boolean value) {
        mConversationListCursor.updateBoolean(mContext, target, columnName, value);
        refreshConversationList();
    }

    @Override
    public void updateConversation(Collection <Conversation> target, String columnName,
            int value) {
        mConversationListCursor.updateInt(mContext, target, columnName, value);
        refreshConversationList();
    }

    @Override
    public void updateConversation(Collection <Conversation> target, String columnName,
            String value) {
        mConversationListCursor.updateString(mContext, target, columnName, value);
        refreshConversationList();
    }

    @Override
    public void markConversationMessagesUnread(Conversation conv, Set<Uri> unreadMessageUris,
            String originalConversationInfo) {
        // The only caller of this method is the conversation view, from where marking unread should
        // *always* take you back to list mode.
        showConversation(null);

        // locally mark conversation unread (the provider is supposed to propagate message unread
        // to conversation unread)
        conv.read = false;

        // only do a granular 'mark unread' if a subset of messages are unread
        final int unreadCount = (unreadMessageUris == null) ? 0 : unreadMessageUris.size();
        final int numMessages = conv.getNumMessages();
        final boolean subsetIsUnread = (numMessages > 1 && unreadCount > 0
                && unreadCount < numMessages);

        if (!subsetIsUnread) {
            // Conversations are neither marked read, nor viewed, and we don't want to show
            // the next conversation.
            markConversationsRead(Collections.singletonList(conv), false, false, false);
        } else {
            mConversationListCursor.setConversationColumn(conv.uri, ConversationColumns.READ, 0);

            // locally update conversation's conversationInfo to revert to original version
            if (originalConversationInfo != null) {
                mConversationListCursor.setConversationColumn(conv.uri,
                        ConversationColumns.CONVERSATION_INFO, originalConversationInfo);
            }

            // applyBatch with each CPO as an UPDATE op on each affected message uri
            final ArrayList<ContentProviderOperation> ops = Lists.newArrayList();
            String authority = null;
            for (Uri messageUri : unreadMessageUris) {
                if (authority == null) {
                    authority = messageUri.getAuthority();
                }
                ops.add(ContentProviderOperation.newUpdate(messageUri)
                        .withValue(UIProvider.MessageColumns.READ, 0)
                        .build());
            }

            new ContentProviderTask() {
                @Override
                protected void onPostExecute(Result result) {
                    // TODO: handle errors?
                }
            }.run(mResolver, authority, ops);
        }
    }

    @Override
    public void markConversationsRead(Collection<Conversation> targets, boolean read,
            boolean viewed) {
        // We want to show the next conversation if we are marking unread.
        markConversationsRead(targets, read, viewed, true);
    }

    private void markConversationsRead(Collection<Conversation> targets, boolean read,
            boolean markViewed, boolean showNext) {
        // auto-advance if requested and the current conversation is being marked unread
        if (showNext && !read) {
            showNextConversation(targets);
        }

        for (Conversation target : targets) {
            final ContentValues values = new ContentValues();
            values.put(ConversationColumns.READ, read);
            if (markViewed) {
                values.put(ConversationColumns.VIEWED, true);
            }
            final ConversationInfo info = target.conversationInfo;
            if (info != null) {
                info.markRead(read);
                values.put(ConversationColumns.CONVERSATION_INFO, ConversationInfo.toString(info));
            }
            updateConversation(Conversation.listOf(target), values);
        }
        // Update the conversations in the selection too.
        for (final Conversation c : targets) {
            c.read = read;
            if (markViewed) {
                c.markViewed();
            }
        }
    }

    /**
     * Auto-advance to a different conversation if the currently visible conversation in
     * conversation mode is affected (deleted, marked unread, etc.).
     *
     * <p>Does nothing if outside of conversation mode.
     *
     * @param target the set of conversations being deleted/marked unread
     */
    private void showNextConversation(Collection<Conversation> target) {
        final boolean currentConversationInView = (mViewMode.getMode() == ViewMode.CONVERSATION)
                && Conversation.contains(target, mCurrentConversation);
        if (currentConversationInView) {
            final Conversation next = mTracker.getNextConversation(
                    Settings.getAutoAdvanceSetting(mAccount.settings), target);
            LogUtils.d(LOG_TAG, "showNextConversation: showing %s next.", next);
            showConversation(next);
        }
    }

    @Override
    public void starMessage(ConversationMessage msg, boolean starred) {
        if (msg.starred == starred) {
            return;
        }

        msg.starred = starred;

        // locally propagate the change to the owning conversation
        // (figure the provider will properly propagate the change when it commits it)
        //
        // when unstarring, only propagate the change if this was the only message starred
        final boolean conversationStarred = starred || msg.isConversationStarred();
        if (conversationStarred != msg.conversation.starred) {
            msg.conversation.starred = conversationStarred;
            mConversationListCursor.setConversationColumn(msg.conversation.uri,
                    ConversationColumns.STARRED, conversationStarred);
        }

        final ContentValues values = new ContentValues(1);
        values.put(UIProvider.MessageColumns.STARRED, starred ? 1 : 0);

        new ContentProviderTask.UpdateTask() {
            @Override
            protected void onPostExecute(Result result) {
                // TODO: handle errors?
            }
        }.run(mResolver, msg.uri, values, null /* selection*/, null /* selectionArgs */);
    }

    private void requestFolderRefresh() {
        if (mFolder != null) {
            if (mAsyncRefreshTask != null) {
                mAsyncRefreshTask.cancel(true);
            }
            mAsyncRefreshTask = new AsyncRefreshTask(mContext, mFolder.refreshUri);
            mAsyncRefreshTask.execute();
        }
    }

    /**
     * Confirm (based on user's settings) and delete a conversation from the conversation list and
     * from the database.
     * @param target the conversations to act upon
     * @param showDialog true if a confirmation dialog is to be shown, false otherwise.
     * @param confirmResource the resource ID of the string that is shown in the confirmation dialog
     * @param action the action to perform after animating the deletion of the conversations.
     */
    protected void confirmAndDelete(final Collection<Conversation> target, boolean showDialog,
            int confirmResource, final DestructiveAction action) {
        if (showDialog) {
            final AlertDialog.OnClickListener onClick = new AlertDialog.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    delete(target, action);
                }
            };
            final CharSequence message = Utils.formatPlural(mContext, confirmResource,
                    target.size());
            new AlertDialog.Builder(mActivity.getActivityContext()).setMessage(message)
                    .setPositiveButton(R.string.ok, onClick)
                    .setNegativeButton(R.string.cancel, null)
                    .create().show();
        } else {
            delete(target, action);
        }
    }

    @Override
    public void delete(final Collection<Conversation> target, final DestructiveAction action) {
        // Order of events is critical! The Conversation View Fragment must be notified
        // of the next conversation with showConversation(next) *before* the conversation list
        // fragment has a chance to delete the conversation, animating it away.

        // Update the conversation fragment if the current conversation is deleted.
        showNextConversation(target);
        // The conversation list deletes and performs the action if it exists.
        final ConversationListFragment convListFragment = getConversationListFragment();
        if (convListFragment != null) {
            LogUtils.d(LOG_TAG, "AAC.requestDelete: ListFragment is handling delete.");
            convListFragment.requestDelete(target, action);
            return;
        }
        // No visible UI element handled it on our behalf. Perform the action ourself.
        action.performAction();
    }

    /**
     * Requests that the action be performed and the UI state is updated to reflect the new change.
     * @param target
     * @param action
     */
    private void requestUpdate(final Collection<Conversation> target,
            final DestructiveAction action) {
        action.performAction();
        refreshConversationList();
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
        enableNotifications();
    }

    @Override
    public void onResume() {
        // Register the receiver that will prevent the status receiver from
        // displaying its notification icon as long as we're running.
        // The SupressNotificationReceiver will block the broadcast if we're looking at the folder
        // that the notification was received for.
        disableNotifications();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        if (mAccount != null) {
            LogUtils.d(LOG_TAG, "Saving the account now");
            outState.putParcelable(SAVED_ACCOUNT, mAccount);
        }
        if (mFolder != null) {
            outState.putParcelable(SAVED_FOLDER, mFolder);
        }
        // If this is a search activity, let's store the search query term as well.
        if (ConversationListContext.isSearchResult(mConvListContext)) {
            outState.putString(SAVED_QUERY, mConvListContext.searchQuery);
        }
        int mode = mViewMode.getMode();
        if (mCurrentConversation != null
                && (mode == ViewMode.CONVERSATION ||
                mViewMode.getMode() == ViewMode.SEARCH_RESULTS_CONVERSATION)) {
            outState.putParcelable(SAVED_CONVERSATION, mCurrentConversation);
        }
        if (!mSelectedSet.isEmpty()) {
            outState.putParcelable(SAVED_SELECTED_SET, mSelectedSet);
        }
        if (mToastBar.getVisibility() == View.VISIBLE) {
            outState.putParcelable(SAVED_TOAST_BAR_OP, mToastBar.getOperation());
        }
        ConversationListFragment convListFragment = getConversationListFragment();
        if (convListFragment != null) {
            convListFragment.getAnimatedAdapter()
            .onSaveInstanceState(outState);
        }
    }

    @Override
    public void onSearchRequested(String query) {
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_SEARCH);
        intent.putExtra(ConversationListContext.EXTRA_SEARCH_QUERY, query);
        intent.putExtra(Utils.EXTRA_ACCOUNT, mAccount);
        intent.setComponent(mActivity.getComponentName());
        mActionBarView.collapseSearch();
        mActivity.startActivity(intent);
    }

    @Override
    public void onStop() {
        if (mEnableShareIntents != null) {
            mEnableShareIntents.cancel(true);
        }
    }

    @Override
    public void onDestroy() {
        // unregister the ViewPager's observer on the conversation cursor
        mPagerController.onDestroy();
        mActionBarView.onDestroy();
        mRecentFolderList.destroy();
        mDestroyed = true;
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

        // Commit any destructive undoable actions the user may have performed.
        commitDestructiveActions(true);

        // We don't want to invalidate the options menu when switching to
        // conversation
        // mode, as it will happen when the conversation finishes loading.
        if (newMode != ViewMode.CONVERSATION) {
            mActivity.invalidateOptionsMenu();
        }
    }

    public boolean isDestroyed() {
        return mDestroyed;
    }

    private void commitDestructiveActions(boolean animate) {
        ConversationListFragment fragment = getConversationListFragment();
        if (fragment != null) {
            fragment.commitDestructiveActions(animate);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        ConversationListFragment convList = getConversationListFragment();
        if (hasFocus && convList != null && convList.isVisible()) {
            // The conversation list is visible.
            Utils.setConversationCursorVisibility(mConversationListCursor, true);
        }
    }

    /**
     * Set the account, and carry out all the account-related changes that rely on this.
     * @param account
     */
    // TODO(viki): Two different methods do the same thing. Resolve
    // {@link #setAccount(Account)} and {@link #switchAccount(Account, boolean)}
    private void setAccount(Account account) {
        if (account == null) {
            LogUtils.w(LOG_TAG, new Error(),
                    "AAC ignoring null (presumably invalid) account restoration");
            return;
        }
        LogUtils.d(LOG_TAG, "AbstractActivityController.setAccount(): account = %s", account.uri);
        mAccount = account;
        // Only change AAC state here. Do *not* modify any other object's state. The object
        // should listen on account changes.
        restartOptionalLoader(LOADER_RECENT_FOLDERS);
        mActivity.invalidateOptionsMenu();
        disableNotificationsOnAccountChange(mAccount);
        restartOptionalLoader(LOADER_ACCOUNT_UPDATE_CURSOR);
        MailAppProvider.getInstance().setLastViewedAccount(mAccount.uri.toString());

        if (account.settings == null) {
            LogUtils.w(LOG_TAG, new Error(), "AAC ignoring account with null settings.");
            return;
        }
        mAccountObservers.notifyChanged();
    }

    /**
     * Restore the state from the previous bundle. Subclasses should call this
     * method from the parent class, since it performs important UI
     * initialization.
     *
     * @param savedState
     */
    @Override
    public void onRestoreInstanceState(Bundle savedState) {
        LogUtils.d(LOG_TAG, "IN AAC.onRestoreInstanceState");
        if (savedState.containsKey(SAVED_CONVERSATION)) {
            // Open the conversation.
            final Conversation conversation =
                    (Conversation)savedState.getParcelable(SAVED_CONVERSATION);
            if (conversation != null && conversation.position < 0) {
                // Set the position to 0 on this conversation, as we don't know where it is
                // in the list
                conversation.position = 0;
            }
            showConversation(conversation);
        }

        if (savedState.containsKey(SAVED_TOAST_BAR_OP)) {
            ToastBarOperation op = ((ToastBarOperation) savedState
                    .getParcelable(SAVED_TOAST_BAR_OP));
            if (op != null) {
                if (op.getType() == ToastBarOperation.UNDO) {
                    onUndoAvailable(op);
                } else if (op.getType() == ToastBarOperation.ERROR) {
                    onError(mFolder, true);
                }
            }
        }

        ConversationListFragment convListFragment = getConversationListFragment();
        if (convListFragment != null) {
            convListFragment.getAnimatedAdapter()
                    .onRestoreInstanceState(savedState);
        }
        /**
         * Restore the state of selected conversations. This needs to be done after the correct mode
         * is set and the action bar is fully initialized. If not, several key pieces of state
         * information will be missing, and the split views may not be initialized correctly.
         * @param savedState
         */
        restoreSelectedConversations(savedState);
    }

    private void handleIntent(Intent intent) {
        boolean handled = false;
        if (Intent.ACTION_VIEW.equals(intent.getAction())) {
            if (intent.hasExtra(Utils.EXTRA_ACCOUNT)) {
                setAccount(Account.newinstance(intent
                        .getStringExtra(Utils.EXTRA_ACCOUNT)));
            }
            if (mAccount == null) {
                return;
            }
            mActivity.invalidateOptionsMenu();
            final boolean isConversationMode = intent.hasExtra(Utils.EXTRA_CONVERSATION);
            // TODO(viki): Allow the controller to set the mode instead of a mode transition.
            if (isConversationMode) {
                mViewMode.enterConversationMode();
            } else {
                mViewMode.enterConversationListMode();
            }

            Folder folder = null;
            if (intent.hasExtra(Utils.EXTRA_FOLDER)) {
                // Open the folder.
                folder = Folder.fromString(intent.getStringExtra(Utils.EXTRA_FOLDER));
            }
            if (folder != null) {
                onFolderChanged(folder);
                handled = true;
            }

            if (isConversationMode) {
                // Open the conversation.
                LogUtils.d(LOG_TAG, "SHOW THE CONVERSATION at %s",
                        intent.getParcelableExtra(Utils.EXTRA_CONVERSATION));
                final Conversation conversation =
                        (Conversation)intent.getParcelableExtra(Utils.EXTRA_CONVERSATION);
                if (conversation != null && conversation.position < 0) {
                    // Set the position to 0 on this conversation, as we don't know where it is
                    // in the list
                    conversation.position = 0;
                }
                showConversation(conversation);
                handled = true;
            }

            if (!handled) {
                // Nothing was saved; just load the account inbox.
                loadAccountInbox();
            }
        } else if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
            if (intent.hasExtra(Utils.EXTRA_ACCOUNT)) {
                // Save this search query for future suggestions.
                final String query = intent.getStringExtra(SearchManager.QUERY);
                final String authority = mContext.getString(R.string.suggestions_authority);
                SearchRecentSuggestions suggestions = new SearchRecentSuggestions(
                        mContext, authority, SuggestionsProvider.MODE);
                suggestions.saveRecentQuery(query, null);
                if (Utils.showTwoPaneSearchResults(mActivity.getActivityContext())) {
                    mViewMode.enterSearchResultsConversationMode();
                } else {
                    mViewMode.enterSearchResultsListMode();
                }
                setAccount((Account) intent.getParcelableExtra(Utils.EXTRA_ACCOUNT));
                mActivity.invalidateOptionsMenu();
                restartOptionalLoader(LOADER_RECENT_FOLDERS);
                fetchSearchFolder(intent);
            } else {
                LogUtils.e(LOG_TAG, "Missing account extra from search intent.  Finishing");
                mActivity.finish();
            }
        }
        if (mAccount != null) {
            restartOptionalLoader(LOADER_ACCOUNT_UPDATE_CURSOR);
        }
    }

    /**
     * Copy any selected conversations stored in the saved bundle into our selection set,
     * triggering {@link ConversationSetObserver} callbacks as our selection set changes.
     *
     */
    private final void restoreSelectedConversations(Bundle savedState) {
        if (savedState == null) {
            mSelectedSet.clear();
            return;
        }
        final ConversationSelectionSet selectedSet = savedState.getParcelable(SAVED_SELECTED_SET);
        if (selectedSet == null || selectedSet.isEmpty()) {
            mSelectedSet.clear();
            return;
        }

        // putAll will take care of calling our registered onSetPopulated method
        mSelectedSet.putAll(selectedSet);
    }

    @Override
    public SubjectDisplayChanger getSubjectDisplayChanger() {
        return mActionBarView;
    }

    /**
     * Children can override this method, but they must call super.showConversation().
     * {@inheritDoc}
     */
    @Override
    public void showConversation(Conversation conversation) {
        // Set the current conversation just in case it wasn't already set.
        setCurrentConversation(conversation);
        // Add the folder that we were viewing to the recent folders list.
        // TODO: this may need to be fine tuned.  If this is the signal that is indicating that
        // the list is shown to the user, this could fire in one pane if the user goes directly
        // to a conversation
        updateRecentFolderList();
    }

    /**
     * Children can override this method, but they must call super.showWaitForInitialization().
     * {@inheritDoc}
     */
    @Override
    public void showWaitForInitialization() {
        mViewMode.enterWaitingForInitializationMode();
    }

    @Override
    public void hideWaitForInitialization() {
    }

    @Override
    public void updateWaitMode() {
        final FragmentManager manager = mActivity.getFragmentManager();
        final WaitFragment waitFragment =
                (WaitFragment)manager.findFragmentByTag(TAG_WAIT);
        if (waitFragment != null) {
            waitFragment.updateAccount(mAccount);
        }
    }

    /**
     * Returns true if we are waiting for the account to sync, and cannot show any folders or
     * conversation for the current account yet.
     *
     */
    public boolean inWaitMode() {
        final FragmentManager manager = mActivity.getFragmentManager();
        final WaitFragment waitFragment =
                (WaitFragment)manager.findFragmentByTag(TAG_WAIT);
        if (waitFragment != null) {
            final Account fragmentAccount = waitFragment.getAccount();
            return fragmentAccount.uri.equals(mAccount.uri) &&
                    mViewMode.getMode() == ViewMode.WAITING_FOR_ACCOUNT_INITIALIZATION;
        }
        return false;
    }

    /**
     * Children can override this method, but they must call super.showConversationList().
     * {@inheritDoc}
     */
    @Override
    public void showConversationList(ConversationListContext listContext) {
    }

    @Override
    public void onConversationSelected(Conversation conversation) {
        // Only animate destructive actions if we are going to be showing the
        // conversation list when we show the next conversation.
        commitDestructiveActions(Utils.useTabletUI(mContext));
        showConversation(conversation);
        if (Intent.ACTION_SEARCH.equals(mActivity.getIntent().getAction())) {
            mViewMode.enterSearchResultsConversationMode();
        } else {
            mViewMode.enterConversationMode();
        }
    }

    /**
     * Set the current conversation. This is the conversation on which all actions are performed.
     * Do not modify mCurrentConversation except through this method, which makes it easy to
     * perform common actions associated with changing the current conversation.
     * @param conversation
     */
    @Override
    public void setCurrentConversation(Conversation conversation) {
        mCurrentConversation = conversation;
        mTracker.initialize(mCurrentConversation);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        // Create a loader to listen in on account changes.
        switch (id) {
            case LOADER_ACCOUNT_CURSOR:
                return new CursorLoader(mContext, MailAppProvider.getAccountsUri(),
                        UIProvider.ACCOUNTS_PROJECTION, null, null, null);
            case LOADER_FOLDER_CURSOR:
                final CursorLoader loader = new CursorLoader(mContext, mFolder.uri,
                        UIProvider.FOLDERS_PROJECTION, null, null, null);
                loader.setUpdateThrottle(mFolderItemUpdateDelayMs);
                return loader;
            case LOADER_RECENT_FOLDERS:
                if (mAccount != null && mAccount.recentFolderListUri != null) {
                    return new CursorLoader(mContext, mAccount.recentFolderListUri,
                            UIProvider.FOLDERS_PROJECTION, null, null, null);
                }
                break;
            case LOADER_ACCOUNT_INBOX:
                final Uri defaultInbox = Settings.getDefaultInboxUri(mAccount.settings);
                final Uri inboxUri = defaultInbox.equals(Uri.EMPTY) ?
                    mAccount.folderListUri : defaultInbox;
                LogUtils.d(LOG_TAG, "Loading the default inbox: %s", inboxUri);
                if (inboxUri != null) {
                    return new CursorLoader(mContext, inboxUri, UIProvider.FOLDERS_PROJECTION, null,
                            null, null);
                }
                break;
            case LOADER_SEARCH:
                return Folder.forSearchResults(mAccount,
                        args.getString(ConversationListContext.EXTRA_SEARCH_QUERY),
                        mActivity.getActivityContext());
            case LOADER_ACCOUNT_UPDATE_CURSOR:
                return new CursorLoader(mContext, mAccount.uri, UIProvider.ACCOUNTS_PROJECTION,
                        null, null, null);
            default:
                LogUtils.wtf(LOG_TAG, "Loader returned unexpected id: %d", id);
        }
        return null;
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {

    }

    /**
     * {@link LoaderManager} currently has a bug in
     * {@link LoaderManager#restartLoader(int, Bundle, android.app.LoaderManager.LoaderCallbacks)}
     * where, if a previous onCreateLoader returned a null loader, this method will NPE. Work around
     * this bug by destroying any loaders that may have been created as null (essentially because
     * they are optional loads, and may not apply to a particular account).
     * <p>
     * A simple null check before restarting a loader will not work, because that would not
     * give the controller a chance to invalidate UI corresponding the prior loader result.
     *
     * @param id loader ID to safely restart
     */
    private void restartOptionalLoader(int id) {
        final LoaderManager lm = mActivity.getLoaderManager();
        lm.destroyLoader(id);
        lm.restartLoader(id, Bundle.EMPTY, this);
    }

    @Override
    public void registerConversationListObserver(DataSetObserver observer) {
        mConversationListObservable.registerObserver(observer);
    }

    @Override
    public void unregisterConversationListObserver(DataSetObserver observer) {
        mConversationListObservable.unregisterObserver(observer);
    }

    @Override
    public void registerFolderObserver(DataSetObserver observer) {
        mFolderObservable.registerObserver(observer);
    }

    @Override
    public void unregisterFolderObserver(DataSetObserver observer) {
        mFolderObservable.unregisterObserver(observer);
    }

    /**
     * Returns true if the number of accounts is different, or if the current account has been
     * removed from the device
     * @param accountCursor
     * @return
     */
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
            // Is there a new account that we do not know about?
            if (!mCurrentAccountUris.contains(accountUri)) {
                return true;
            }
        } while (accountCursor.moveToNext());

        // As long as we found the current account, the list hasn't been updated
        return !foundCurrentAccount;
    }

    /**
     * Updates accounts for the app. If the current account is missing, the first
     * account in the list is set to the current account (we <em>have</em> to choose something).
     *
     * @param accounts cursor into the AccountCache
     * @return true if the update was successful, false otherwise
     */
    private boolean updateAccounts(Cursor accounts) {
        if (accounts == null || !accounts.moveToFirst()) {
            return false;
        }

        final Account[] allAccounts = Account.getAllAccounts(accounts);
        // A match for the current account's URI in the list of accounts.
        Account currentFromList = null;

        // Save the uris for the accounts
        mCurrentAccountUris.clear();
        for (Account account : allAccounts) {
            LogUtils.d(LOG_TAG, "updateAccounts(%s)", account);
            mCurrentAccountUris.add(account.uri);
            if (mAccount != null && account.uri.equals(mAccount.uri)) {
                currentFromList = account;
            }
        }

        // 1. current account is already set and is in allAccounts:
        //    1a. It has changed -> load the updated account.
        //    2b. It is unchanged -> no-op
        // 2. current account is set and is not in allAccounts -> pick first (acct was deleted?)
        // 3. saved preference has an account -> pick that one
        // 4. otherwise just pick first

        boolean accountChanged = false;
        /// Assume case 4, initialize to first account, and see if we can find anything better.
        Account newAccount = allAccounts[0];
        if (currentFromList != null) {
            // Case 1: Current account exists but has changed
            if (!currentFromList.equals(mAccount)) {
                newAccount = currentFromList;
                accountChanged = true;
            }
            // Case 1b: else, current account is unchanged: nothing to do.
        } else {
            // Case 2: Current account is not in allAccounts, the account needs to change.
            accountChanged = true;
            if (mAccount == null) {
                // Case 3: Check for last viewed account, and check if it exists in the list.
                final String lastAccountUri = MailAppProvider.getInstance().getLastViewedAccount();
                if (lastAccountUri != null) {
                    for (final Account account : allAccounts) {
                        if (lastAccountUri.equals(account.uri.toString())) {
                            newAccount = account;
                            break;
                        }
                    }
                }
            }
        }
        if (accountChanged) {
            onAccountChanged(newAccount);
        }
        // Whether we have updated the current account or not, we need to update the list of
        // accounts in the ActionBar.
        mActionBarView.setAccounts(allAccounts);
        return (allAccounts.length > 0);
    }

    private void disableNotifications() {
        mNewEmailReceiver.activate(mContext, this);
    }

    private void enableNotifications() {
        mNewEmailReceiver.deactivate();
    }

    private void disableNotificationsOnAccountChange(Account account) {
        // If the new mail suppression receiver is activated for a different account, we want to
        // activate it for the new account.
        if (mNewEmailReceiver.activated() &&
                !mNewEmailReceiver.notificationsDisabledForAccount(account)) {
            // Deactivate the current receiver, otherwise multiple receivers may be registered.
            mNewEmailReceiver.deactivate();
            mNewEmailReceiver.activate(mContext, this);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        // We want to reinitialize only if we haven't ever been initialized, or
        // if the current account has vanished.
        if (data == null) {
            LogUtils.e(LOG_TAG, "Received null cursor from loader id: %d", loader.getId());
        }
        switch (loader.getId()) {
            case LOADER_ACCOUNT_CURSOR:
                // If the account list is not null, and the account list cursor is empty,
                // we need to start the specified activity.
                if (data != null && data.getCount() == 0) {
                    // If an empty cursor is returned, the MailAppProvider is indicating that
                    // no accounts have been specified.  We want to navigate to the "add account"
                    // activity that will handle the intent returned by the MailAppProvider

                    // If the MailAppProvider believes that all accounts have been loaded, and the
                    // account list is still empty, we want to prompt the user to add an account
                    final Bundle extras = data.getExtras();
                    final boolean accountsLoaded =
                            extras.getInt(AccountCursorExtraKeys.ACCOUNTS_LOADED) != 0;

                    if (accountsLoaded) {
                        final Intent noAccountIntent = MailAppProvider.getNoAccountIntent(mContext);
                        if (noAccountIntent != null) {
                            mActivity.startActivityForResult(noAccountIntent,
                                    ADD_ACCOUNT_REQUEST_CODE);
                        }
                    }
                } else {
                    final boolean accountListUpdated = accountsUpdated(data);
                    if (!isLoaderInitialized || accountListUpdated) {
                        isLoaderInitialized = updateAccounts(data);
                    }
                }
                break;
            case LOADER_ACCOUNT_UPDATE_CURSOR:
                // We have gotten an update for current account.

                // Make sure that this is an update for the current account
                if (data != null && data.moveToFirst()) {
                    final Account updatedAccount = new Account(data);

                    if (updatedAccount.uri.equals(mAccount.uri)) {
                        // Keep a reference to the previous settings object
                        final Settings previousSettings = mAccount.settings;

                        // Update the controller's reference to the current account
                        mAccount = updatedAccount;
                        LogUtils.d(LOG_TAG, "AbstractActivityController.onLoadFinished(): "
                                + "mAccount = %s", mAccount.uri);

                        // Only notify about a settings change if something differs
                        if (!Objects.equal(mAccount.settings, previousSettings)) {
                            mAccountObservers.notifyChanged();
                        }

                        // Got an update for the current account
                        final boolean inWaitingMode = inWaitMode();
                        if (!updatedAccount.isAccountIntialized() && !inWaitingMode) {
                            // Transition to waiting mode
                            showWaitForInitialization();
                        } else if (updatedAccount.isAccountIntialized()) {
                            if (inWaitingMode) {
                                // Dismiss waiting mode
                                hideWaitForInitialization();
                            }
                        } else if (!updatedAccount.isAccountIntialized() && inWaitingMode) {
                            // Update the WaitFragment's account object
                            updateWaitMode();
                        }
                    } else {
                        LogUtils.e(LOG_TAG, "Got update for account: %s with current account: %s",
                                updatedAccount.uri, mAccount.uri);
                        // We need to restart the loader, so the correct account information will
                        // be returned
                        restartOptionalLoader(LOADER_ACCOUNT_UPDATE_CURSOR);
                    }
                }
                break;
            case LOADER_FOLDER_CURSOR:
                // Check status of the cursor.
                if (data != null && data.moveToFirst()) {
                    final Folder folder = new Folder(data);
                    LogUtils.d(LOG_TAG, "FOLDER STATUS = %d", folder.syncStatus);

                    mFolder = folder;
                    mFolderObservable.notifyChanged();

                } else {
                    LogUtils.d(LOG_TAG, "Unable to get the folder %s",
                            mFolder != null ? mAccount.name : "");
                }
                break;
            case LOADER_RECENT_FOLDERS:
                // No recent folders and we are running on a phone? Populate the default recents.
                if (data != null && data.getCount() == 0 && !Utils.useTabletUI(mContext)) {
                    final class PopulateDefault extends AsyncTask<Uri, Void, Void> {
                        @Override
                        protected Void doInBackground(Uri... uri) {
                            // Asking for an update on the URI and ignore the result.
                            final ContentResolver resolver = mContext.getContentResolver();
                            resolver.update(uri[0], null, null, null);
                            return null;
                        }
                    }
                    final Uri uri = mAccount.defaultRecentFolderListUri;
                    LogUtils.v(LOG_TAG, "Default recents at %s", uri);
                    new PopulateDefault().execute(uri);
                    break;
                }
                LogUtils.v(LOG_TAG, "Reading recent folders from the cursor.");
                mRecentFolderList.loadFromUiProvider(data);
                mActionBarView.requestRecentFoldersAndRedraw();
                break;
            case LOADER_ACCOUNT_INBOX:
                if (data != null && !data.isClosed() && data.moveToFirst()) {
                    Folder inbox = new Folder(data);
                    onFolderChanged(inbox);
                    // Just want to get the inbox, don't care about updates to it
                    // as this will be tracked by the folder change listener.
                    mActivity.getLoaderManager().destroyLoader(LOADER_ACCOUNT_INBOX);
                } else {
                    LogUtils.d(LOG_TAG, "Unable to get the account inbox for account %s",
                            mAccount != null ? mAccount.name : "");
                }
                break;
            case LOADER_SEARCH:
                data.moveToFirst();
                Folder search = new Folder(data);
                updateFolder(search);
                mConvListContext = ConversationListContext.forSearchQuery(mAccount, mFolder,
                        mActivity.getIntent()
                                .getStringExtra(UIProvider.SearchQueryParameters.QUERY));
                showConversationList(mConvListContext);
                mActivity.invalidateOptionsMenu();
                mActivity.getLoaderManager().destroyLoader(LOADER_SEARCH);
                break;
        }
    }

    /**
     * Destructive actions on Conversations. This class should only be created by controllers, and
     * clients should only require {@link DestructiveAction}s, not specific implementations of the.
     * Only the controllers should know what kind of destructive actions are being created.
     */
    public class ConversationAction implements DestructiveAction {
        /**
         * The action to be performed. This is specified as the resource ID of the menu item
         * corresponding to this action: R.id.delete, R.id.report_spam, etc.
         */
        private final int mAction;
        /** The action will act upon these conversations */
        private final Collection<Conversation> mTarget;
        /** Whether this destructive action has already been performed */
        private boolean mCompleted;
        /** Whether this is an action on the currently selected set. */
        private final boolean mIsSelectedSet;

        /**
         * Create a listener object. action is one of four constants: R.id.y_button (archive),
         * R.id.delete , R.id.mute, and R.id.report_spam.
         * @param action
         * @param target Conversation that we want to apply the action to.
         * @param isBatch whether the conversations are in the currently selected batch set.
         */
        public ConversationAction(int action, Collection<Conversation> target, boolean isBatch) {
            mAction = action;
            mTarget = ImmutableList.copyOf(target);
            mIsSelectedSet = isBatch;
        }

        /**
         * The action common to child classes. This performs the action specified in the constructor
         * on the conversations given here.
         */
        @Override
        public void performAction() {
            if (isPerformed()) {
                return;
            }
            boolean undoEnabled = mAccount.supportsCapability(AccountCapabilities.UNDO);

            // Are we destroying the currently shown conversation? Show the next one.
            if (LogUtils.isLoggable(LOG_TAG, LogUtils.DEBUG)){
                LogUtils.d(LOG_TAG, "ConversationAction.performAction():"
                        + "\nmTarget=%s\nCurrent=%s",
                        Conversation.toString(mTarget), mCurrentConversation);
            }

            if (mConversationListCursor == null) {
                LogUtils.e(LOG_TAG, "null ConversationCursor in ConversationAction.performAction():"
                        + "\nmTarget=%s\nCurrent=%s",
                        Conversation.toString(mTarget), mCurrentConversation);
                return;
            }

            switch (mAction) {
                case R.id.archive:
                    LogUtils.d(LOG_TAG, "Archiving");
                    mConversationListCursor.archive(mContext, mTarget);
                    break;
                case R.id.delete:
                    LogUtils.d(LOG_TAG, "Deleting");
                    mConversationListCursor.delete(mContext, mTarget);
                    if (mFolder.supportsCapability(FolderCapabilities.DELETE_ACTION_FINAL)) {
                        undoEnabled = false;
                    }
                    break;
                case R.id.mute:
                    LogUtils.d(LOG_TAG, "Muting");
                    if (mFolder.supportsCapability(FolderCapabilities.DESTRUCTIVE_MUTE)) {
                        for (Conversation c : mTarget) {
                            c.localDeleteOnUpdate = true;
                        }
                    }
                    mConversationListCursor.mute(mContext, mTarget);
                    break;
                case R.id.report_spam:
                    LogUtils.d(LOG_TAG, "Reporting spam");
                    mConversationListCursor.reportSpam(mContext, mTarget);
                    break;
                case R.id.mark_not_spam:
                    LogUtils.d(LOG_TAG, "Marking not spam");
                    mConversationListCursor.reportNotSpam(mContext, mTarget);
                    break;
                case R.id.report_phishing:
                    LogUtils.d(LOG_TAG, "Reporting phishing");
                    mConversationListCursor.reportPhishing(mContext, mTarget);
                    break;
                case R.id.remove_star:
                    LogUtils.d(LOG_TAG, "Removing star");
                    // Star removal is destructive in the Starred folder.
                    mConversationListCursor.updateBoolean(mContext, mTarget,
                            ConversationColumns.STARRED, false);
                    break;
                case R.id.mark_not_important:
                    LogUtils.d(LOG_TAG, "Marking not-important");
                    // Marking not important is destructive in a mailbox
                    // containing only important messages
                    if (mFolder != null && mFolder.isImportantOnly()) {
                        for (Conversation conv : mTarget) {
                            conv.localDeleteOnUpdate = true;
                        }
                    }
                    mConversationListCursor.updateInt(mContext, mTarget,
                            ConversationColumns.PRIORITY, UIProvider.ConversationPriority.LOW);
                    break;
            }
            if (undoEnabled) {
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        onUndoAvailable(new ToastBarOperation(mTarget.size(), mAction,
                                ToastBarOperation.UNDO));
                    }
                }, mShowUndoBarDelay);
            }
            refreshConversationList();
            if (mIsSelectedSet) {
                mSelectedSet.clear();
            }
        }

        /**
         * Returns true if this action has been performed, false otherwise.
         *
         */
        private synchronized boolean isPerformed() {
            if (mCompleted) {
                return true;
            }
            mCompleted = true;
            return false;
        }
    }

    /**
     * Get a destructive action for a menu action.
     * This is a temporary method, to control the profusion of {@link DestructiveAction} classes
     * that are created. Please do not copy this paradigm.
     * @param action the resource ID of the menu action: R.id.delete, for example
     * @param target the conversations to act upon.
     * @return a {@link DestructiveAction} that performs the specified action.
     */
    private final DestructiveAction getAction(int action, Collection<Conversation> target) {
        final DestructiveAction da = new ConversationAction(action, target, false);
        registerDestructiveAction(da);
        return da;
    }

    // Called from the FolderSelectionDialog after a user is done selecting folders to assign the
    // conversations to.
    @Override
    public final void assignFolder(Collection<FolderOperation> folderOps,
            Collection<Conversation> target, boolean batch, boolean showUndo) {
        // Actions are destructive only when the current folder can be assigned
        // to (which is the same as being able to un-assign a conversation from the folder) and
        // when the list of folders contains the current folder.
        final boolean isDestructive = mFolder
                .supportsCapability(FolderCapabilities.CAN_ACCEPT_MOVED_MESSAGES)
                && FolderOperation.isDestructive(folderOps, mFolder);
        LogUtils.d(LOG_TAG, "onFolderChangesCommit: isDestructive = %b", isDestructive);
        if (isDestructive) {
            for (final Conversation c : target) {
                c.localDeleteOnUpdate = true;
            }
        }
        final DestructiveAction folderChange = getFolderChange(target, folderOps, isDestructive,
                batch, showUndo);
        // Update the UI elements depending no their visibility and availability
        // TODO(viki): Consolidate this into a single method requestDelete.
        if (isDestructive) {
            delete(target, folderChange);
        } else {
            requestUpdate(target, folderChange);
        }
    }

    @Override
    public final void onRefreshRequired() {
        if (isAnimating() || isDragging()) {
            LogUtils.d(LOG_TAG, "onRefreshRequired: delay until animating done");
            return;
        }
        // Refresh the query in the background
        if (mConversationListCursor.isRefreshRequired()) {
            mConversationListCursor.refresh();
        }
    }

    @Override
    public void startDragMode() {
        mIsDragHappening = true;
    }

    @Override
    public void stopDragMode() {
        mIsDragHappening = false;
        if (mConversationListCursor.isRefreshReady()) {
            LogUtils.d(LOG_TAG, "Stopped animating: try sync");
            onRefreshReady();
        }

        if (mConversationListCursor.isRefreshRequired()) {
            LogUtils.d(LOG_TAG, "Stopped animating: refresh");
            mConversationListCursor.refresh();
        }
    }

    private boolean isDragging() {
        return mIsDragHappening;
    }

    private boolean isAnimating() {
        boolean isAnimating = false;
        ConversationListFragment convListFragment = getConversationListFragment();
        if (convListFragment != null) {
            AnimatedAdapter adapter = convListFragment.getAnimatedAdapter();
            if (adapter != null) {
                isAnimating = adapter.isAnimating();
            }
        }
        return isAnimating;
    }

    /**
     * Called when the {@link ConversationCursor} is changed or has new data in it.
     * <p>
     * {@inheritDoc}
     */
    @Override
    public final void onRefreshReady() {
        if (!isAnimating()) {
            // Swap cursors
            mConversationListCursor.sync();
        }
        mTracker.onCursorUpdated();
    }

    @Override
    public final void onDataSetChanged() {
        updateConversationListFragment();
        mConversationListObservable.notifyChanged();
    }

    /**
     * If the Conversation List Fragment is visible, updates the fragment.
     */
    private final void updateConversationListFragment() {
        final ConversationListFragment convList = getConversationListFragment();
        if (convList != null) {
            refreshConversationList();
            if (convList.isVisible()) {
                Utils.setConversationCursorVisibility(mConversationListCursor, true);
            }
        }
    }

    /**
     * This class handles throttled refresh of the conversation list
     */
    static class RefreshTimerTask extends TimerTask {
        final Handler mHandler;
        final AbstractActivityController mController;

        RefreshTimerTask(AbstractActivityController controller, Handler handler) {
            mHandler = handler;
            mController = controller;
        }

        @Override
        public void run() {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    LogUtils.d(LOG_TAG, "Delay done... calling onRefreshRequired");
                    mController.onRefreshRequired();
                }});
        }
    }

    /**
     * Cancel the refresh task, if it's running
     */
    private void cancelRefreshTask () {
        if (mConversationListRefreshTask != null) {
            mConversationListRefreshTask.cancel();
            mConversationListRefreshTask = null;
        }
    }

    @Override
    public void onAnimationEnd(AnimatedAdapter animatedAdapter) {
        if (mConversationListCursor.isRefreshReady()) {
            LogUtils.d(LOG_TAG, "Stopped animating: try sync");
            onRefreshReady();
        }

        if (mConversationListCursor.isRefreshRequired()) {
            LogUtils.d(LOG_TAG, "Stopped animating: refresh");
            mConversationListCursor.refresh();
        }
    }

    @Override
    public void onSetEmpty() {
    }

    @Override
    public void onSetPopulated(ConversationSelectionSet set) {
        final ConversationListFragment convList = getConversationListFragment();
        if (convList == null) {
            return;
        }
        mCabActionMenu = new SelectedConversationsActionMenu(mActivity, set, mFolder,
                (SwipeableListView) convList.getListView());
        enableCabMode();
    }

    @Override
    public void onSetChanged(ConversationSelectionSet set) {
        // Do nothing. We don't care about changes to the set.
    }

    @Override
    public ConversationSelectionSet getSelectedSet() {
        return mSelectedSet;
    }

    /**
     * Disable the Contextual Action Bar (CAB). The selected set is not changed.
     */
    protected void disableCabMode() {
        // Commit any previous destructive actions when entering/ exiting CAB mode.
        commitDestructiveActions(true);
        if (mCabActionMenu != null) {
            mCabActionMenu.deactivate();
        }
    }

    /**
     * Re-enable the CAB menu if required. The selection set is not changed.
     */
    protected void enableCabMode() {
        // Commit any previous destructive actions when entering/ exiting CAB mode.
        commitDestructiveActions(true);
        if (mCabActionMenu != null) {
            mCabActionMenu.activate();
        }
    }

    /**
     * Unselect conversations and exit CAB mode.
     */
    protected final void exitCabMode() {
        mSelectedSet.clear();
    }

    @Override
    public void startSearch() {
        if (mAccount == null) {
            // We cannot search if there is no account. Drop the request to the floor.
            LogUtils.d(LOG_TAG, "AbstractActivityController.startSearch(): null account");
            return;
        }
        if (mAccount.supportsCapability(UIProvider.AccountCapabilities.LOCAL_SEARCH)
                | mAccount.supportsCapability(UIProvider.AccountCapabilities.SERVER_SEARCH)) {
            onSearchRequested(mActionBarView.getQuery());
        } else {
            Toast.makeText(mActivity.getActivityContext(), mActivity.getActivityContext()
                    .getString(R.string.search_unsupported), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void exitSearchMode() {
        if (mViewMode.getMode() == ViewMode.SEARCH_RESULTS_LIST) {
            mActivity.finish();
        }
    }

    /**
     * Supports dragging conversations to a folder.
     */
    @Override
    public boolean supportsDrag(DragEvent event, Folder folder) {
        return (folder != null
                && event != null
                && event.getClipDescription() != null
                && folder.supportsCapability
                    (UIProvider.FolderCapabilities.CAN_ACCEPT_MOVED_MESSAGES)
                && folder.supportsCapability
                    (UIProvider.FolderCapabilities.CAN_HOLD_MAIL)
                && !mFolder.uri.equals(folder.uri));
    }

    /**
     * Handles dropping conversations to a folder.
     */
    @Override
    public void handleDrop(DragEvent event, final Folder folder) {
        if (!supportsDrag(event, folder)) {
            return;
        }
        final Collection<Conversation> conversations = mSelectedSet.values();
        final Collection<FolderOperation> dropTarget = FolderOperation.listOf(new FolderOperation(
                folder, true));
        // Drag and drop is destructive: we remove conversations from the
        // current folder.
        final DestructiveAction action = getFolderChange(conversations, dropTarget, true, true,
                true);
        delete(conversations, action);
    }

    @Override
    public void onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            if (mToastBar != null && !mToastBar.isEventInToastBar(event)) {
                mToastBar.hide(true);
            }
        }
    }

    @Override
    public void onConversationSeen(Conversation conv) {
        mPagerController.onConversationSeen(conv);
    }

    private class ConversationListLoaderCallbacks implements
        LoaderManager.LoaderCallbacks<ConversationCursor> {

        @Override
        public Loader<ConversationCursor> onCreateLoader(int id, Bundle args) {
            Loader<ConversationCursor> result = new ConversationCursorLoader((Activity) mActivity,
                    mAccount, mFolder.conversationListUri, mFolder.name);
            return result;
        }

        @Override
        public void onLoadFinished(Loader<ConversationCursor> loader, ConversationCursor data) {
            LogUtils.d(LOG_TAG, "IN AAC.ConversationCursor.onLoadFinished, data=%s loader=%s",
                    data, loader);
            // Clear our all pending destructive actions before swapping the conversation cursor
            destroyPending(null);
            mConversationListCursor = data;
            mConversationListCursor.addListener(AbstractActivityController.this);

            mTracker.onCursorUpdated();

            mConversationListObservable.notifyChanged();
            // Register the AbstractActivityController as a listener to changes in
            // data in the cursor.
            final ConversationListFragment convList = getConversationListFragment();
            if (convList != null) {
                convList.onCursorUpdated();

                if (convList.isVisible()) {
                    // The conversation list is visible.
                    Utils.setConversationCursorVisibility(mConversationListCursor, true);
                }
            }
            // Shown for search results in two-pane mode only.
            if (shouldShowFirstConversation()) {
                if (mConversationListCursor.getCount() > 0) {
                    mConversationListCursor.moveToPosition(0);
                    final Conversation conv = new Conversation(mConversationListCursor);
                    conv.position = 0;
                    onConversationSelected(conv);
                }
            }
        }

        @Override
        public void onLoaderReset(Loader<ConversationCursor> loader) {
            LogUtils.d(LOG_TAG, "IN AAC.ConversationCursor.onLoaderReset, data=%s loader=%s",
                    mConversationListCursor, loader);

            if (mConversationListCursor != null) {
                // Unregister the listener
                mConversationListCursor.removeListener(AbstractActivityController.this);
                mConversationListCursor = null;

                // Inform anyone who is interested about the change
                mTracker.onCursorUpdated();
                mConversationListObservable.notifyChanged();

                final ConversationListFragment convList = getConversationListFragment();
                if (convList != null) {
                    convList.onCursorUpdated();
                }
            }
        }
    }

    /**
     * Destroy the pending {@link DestructiveAction} till now and assign the given action as the
     * next destructive action..
     * @param nextAction the next destructive action to be performed. This can be null.
     */
    private final void destroyPending(DestructiveAction nextAction) {
        // If there is a pending action, perform that first.
        if (mPendingDestruction != null) {
            mPendingDestruction.performAction();
        }
        mPendingDestruction = nextAction;
    }

    /**
     * Register a destructive action with the controller. This performs the previous destructive
     * action as a side effect. This method is final because we don't want the child classes to
     * embellish this method any more.
     * @param action
     */
    private final void registerDestructiveAction(DestructiveAction action) {
        // TODO(viki): This is not a good idea. The best solution is for clients to request a
        // destructive action from the controller and for the controller to own the action. This is
        // a half-way solution while refactoring DestructiveAction.
        destroyPending(action);
        return;
    }

    @Override
    public final DestructiveAction getBatchAction(int action) {
        final DestructiveAction da = new ConversationAction(action, mSelectedSet.values(), true);
        registerDestructiveAction(da);
        return da;
    }

    @Override
    public final DestructiveAction getDeferredBatchAction(int action) {
        final DestructiveAction da = new ConversationAction(action, mSelectedSet.values(), true);
        return da;
    }

    /**
     * Class to change the folders that are assigned to a set of conversations. This is destructive
     * because the user can remove the current folder from the conversation, in which case it has
     * to be animated away from the current folder.
     */
    private class FolderDestruction implements DestructiveAction {
        private final Collection<Conversation> mTarget;
        private final ArrayList<FolderOperation> mFolderOps = new ArrayList<FolderOperation>();
        private final boolean mIsDestructive;
        /** Whether this destructive action has already been performed */
        private boolean mCompleted;
        private boolean mIsSelectedSet;
        private boolean mShowUndo;
        private int mAction;

        /**
         * Create a new folder destruction object to act on the given conversations.
         * @param target
         */
        private FolderDestruction(final Collection<Conversation> target,
                final Collection<FolderOperation> folders, boolean isDestructive, boolean isBatch,
                boolean showUndo, int action) {
            mTarget = ImmutableList.copyOf(target);
            mFolderOps.addAll(folders);
            mIsDestructive = isDestructive;
            mIsSelectedSet = isBatch;
            mShowUndo = showUndo;
            mAction = action;
        }

        @Override
        public void performAction() {
            if (isPerformed()) {
                return;
            }
            if (mIsDestructive && mShowUndo) {
                ToastBarOperation undoOp = new ToastBarOperation(mTarget.size(),
                        mAction, ToastBarOperation.UNDO);
                onUndoAvailable(undoOp);
            }
            // For each conversation, for each operation, add/ remove the
            // appropriate folders.
            for (Conversation target : mTarget) {
                HashMap<Uri, Folder> targetFolders = Folder
                        .hashMapForFolders(target.getRawFolders());
                if (mIsDestructive) {
                    target.localDeleteOnUpdate = true;
                }
                for (FolderOperation op : mFolderOps) {
                    if (op.mAdd) {
                        targetFolders.put(op.mFolder.uri, op.mFolder);
                    } else {
                        targetFolders.remove(op.mFolder.uri);
                    }
                }
                target.setRawFolders(Folder.getSerializedFolderString(targetFolders.values()));
                mConversationListCursor.updateString(mContext, Conversation.listOf(target),
                        Conversation.UPDATE_FOLDER_COLUMN, target.getRawFoldersString());
            }
            refreshConversationList();
            if (mIsSelectedSet) {
                mSelectedSet.clear();
            }
        }

        /**
         * Returns true if this action has been performed, false otherwise.
         *
         */
        private synchronized boolean isPerformed() {
            if (mCompleted) {
                return true;
            }
            mCompleted = true;
            return false;
        }
    }

    private final DestructiveAction getFolderChange(Collection<Conversation> target,
            Collection<FolderOperation> folders, boolean isDestructive, boolean isBatch,
            boolean showUndo) {
        final DestructiveAction da = new FolderDestruction(target, folders, isDestructive, isBatch,
                showUndo, R.id.change_folder);
        registerDestructiveAction(da);
        return da;
    }

    @Override
    public final DestructiveAction getDeferredRemoveFolder(Collection<Conversation> target,
            Folder toRemove, boolean isDestructive, boolean isBatch,
            boolean showUndo) {
        Collection<FolderOperation> folderOps = new ArrayList<FolderOperation>();
        folderOps.add(new FolderOperation(toRemove, false));
        return new FolderDestruction(target, folderOps, isDestructive, isBatch,
                showUndo, R.id.remove_folder);
    }

    private final DestructiveAction getRemoveFolder(Collection<Conversation> target,
            Folder toRemove, boolean isDestructive, boolean isBatch,
            boolean showUndo) {
        Collection<FolderOperation> folderOps = new ArrayList<FolderOperation>();
        folderOps.add(new FolderOperation(toRemove, false));
        DestructiveAction da = new FolderDestruction(target, folderOps, isDestructive, isBatch,
                showUndo, R.id.remove_folder);
        registerDestructiveAction(da);
        return da;
    }

    @Override
    public final void refreshConversationList() {
        final ConversationListFragment convList = getConversationListFragment();
        if (convList == null) {
            return;
        }
        convList.requestListRefresh();
    }

    protected final ActionClickedListener getUndoClickedListener(
            final AnimatedAdapter listAdapter) {
        return new ActionClickedListener() {
            @Override
            public void onActionClicked() {
                if (mAccount.undoUri != null) {
                    // NOTE: We might want undo to return the messages affected, in which case
                    // the resulting cursor might be interesting...
                    // TODO: Use UIProvider.SEQUENCE_QUERY_PARAMETER to indicate the set of
                    // commands to undo
                    if (mConversationListCursor != null) {
                        mConversationListCursor.undo(
                                mActivity.getActivityContext(), mAccount.undoUri);
                    }
                    if (listAdapter != null) {
                        listAdapter.setUndo(true);
                    }
                }
            }
        };
    }

    protected final void showErrorToast(final Folder folder, boolean replaceVisibleToast) {
        mToastBar.setConversationMode(false);

        ActionClickedListener listener = null;
        int actionTextResourceId;
        final int lastSyncResult = folder.lastSyncResult;
        switch (lastSyncResult) {
            case UIProvider.LastSyncResult.CONNECTION_ERROR:
                listener = getRetryClickedListener(folder);
                actionTextResourceId = R.string.retry;
                break;
            case UIProvider.LastSyncResult.AUTH_ERROR:
                listener = getSignInClickedListener();
                actionTextResourceId = R.string.signin;
                break;
            case UIProvider.LastSyncResult.SECURITY_ERROR:
                return; // Currently we do nothing for security errors.
            case UIProvider.LastSyncResult.STORAGE_ERROR:
                listener = getStorageErrorClickedListener();
                actionTextResourceId = R.string.info;
                break;
            case UIProvider.LastSyncResult.INTERNAL_ERROR:
                listener = getInternalErrorClickedListener();
                actionTextResourceId = R.string.report;
                break;
            default:
                return;
        }
        mToastBar.show(
                listener,
                R.drawable.ic_alert_white,
                Utils.getSyncStatusText(mActivity.getActivityContext(),
                        lastSyncResult),
                false, /* showActionIcon */
                actionTextResourceId,
                replaceVisibleToast,
                new ToastBarOperation(1, 0, ToastBarOperation.ERROR));
    }

    private ActionClickedListener getRetryClickedListener(final Folder folder) {
        return new ActionClickedListener() {
            @Override
            public void onActionClicked() {
                final Uri uri = folder.refreshUri;

                if (uri != null) {
                    startAsyncRefreshTask(uri);
                }
            }
        };
    }

    private ActionClickedListener getSignInClickedListener() {
        return new ActionClickedListener() {
            @Override
            public void onActionClicked() {
                promptUserForAuthentication(mAccount);
            }
        };
    }

    private ActionClickedListener getStorageErrorClickedListener() {
        return new ActionClickedListener() {
            @Override
            public void onActionClicked() {
                showStorageErrorDialog();
            }
        };
    }

    private void showStorageErrorDialog() {
        DialogFragment fragment = (DialogFragment)
                mFragmentManager.findFragmentByTag(SYNC_ERROR_DIALOG_FRAGMENT_TAG);
        if (fragment == null) {
            fragment = SyncErrorDialogFragment.newInstance();
        }
        fragment.show(mFragmentManager, SYNC_ERROR_DIALOG_FRAGMENT_TAG);
    }

    private ActionClickedListener getInternalErrorClickedListener() {
        return new ActionClickedListener() {
            @Override
            public void onActionClicked() {
                Utils.sendFeedback(
                        mActivity.getActivityContext(), mAccount, true /* reportingProblem */);
            }
        };
    }

    @Override
    public void onFooterViewErrorActionClick(Folder folder, int errorStatus) {
        Uri uri = null;
        switch (errorStatus) {
            case UIProvider.LastSyncResult.CONNECTION_ERROR:
                if (folder != null && folder.refreshUri != null) {
                    uri = folder.refreshUri;
                }
                break;
            case UIProvider.LastSyncResult.AUTH_ERROR:
                promptUserForAuthentication(mAccount);
                return;
            case UIProvider.LastSyncResult.SECURITY_ERROR:
                return; // Currently we do nothing for security errors.
            case UIProvider.LastSyncResult.STORAGE_ERROR:
                showStorageErrorDialog();
                return;
            case UIProvider.LastSyncResult.INTERNAL_ERROR:
                Utils.sendFeedback(
                        mActivity.getActivityContext(), mAccount, true /* reportingProblem */);
                return;
            default:
                return;
        }

        if (uri != null) {
            startAsyncRefreshTask(uri);
        }
    }

    @Override
    public void onFooterViewLoadMoreClick(Folder folder) {
        if (folder != null && folder.loadMoreUri != null) {
            startAsyncRefreshTask(folder.loadMoreUri);
        }
    }

    private void startAsyncRefreshTask(Uri uri) {
        if (mFolderSyncTask != null) {
            mFolderSyncTask.cancel(true);
        }
        mFolderSyncTask = new AsyncRefreshTask(mActivity.getActivityContext(), uri);
        mFolderSyncTask.execute();
    }

    private void promptUserForAuthentication(Account account) {
        if (account != null && !Utils.isEmpty(account.reauthenticationIntentUri)) {
            final Intent authenticationIntent =
                    new Intent(Intent.ACTION_VIEW, account.reauthenticationIntentUri);
            mActivity.startActivityForResult(authenticationIntent, REAUTHENTICATE_REQUEST_CODE);
        }
    }
}
