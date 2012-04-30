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
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.LoaderManager;
import android.app.SearchManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.CursorLoader;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.database.DataSetObservable;
import android.database.DataSetObserver;
import android.net.Uri;
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
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.Toast;

import com.android.mail.ConversationListContext;
import com.android.mail.R;
import com.android.mail.browse.ConversationCursor;
import com.android.mail.browse.ConversationCursor.ConversationListener;
import com.android.mail.browse.ConversationPagerController;
import com.android.mail.browse.SelectedConversationsActionMenu;
import com.android.mail.compose.ComposeActivity;
import com.android.mail.providers.Account;
import com.android.mail.providers.Conversation;
import com.android.mail.providers.Folder;
import com.android.mail.providers.MailAppProvider;
import com.android.mail.providers.Settings;
import com.android.mail.providers.SuggestionsProvider;
import com.android.mail.providers.UIProvider;
import com.android.mail.providers.UIProvider.AccountCursorExtraKeys;
import com.android.mail.providers.UIProvider.ConversationColumns;
import com.android.mail.providers.UIProvider.FolderCapabilities;
import com.android.mail.utils.LogUtils;
import com.android.mail.utils.Utils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.Timer;
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
public abstract class AbstractActivityController implements ActivityController,
        ConversationListener, OnScrollListener {
    // Keys for serialization of various information in Bundles.
    /** Tag for {@link #mAccount} */
    private static final String SAVED_ACCOUNT = "saved-account";
    /** Tag for {@link #mFolder} */
    private static final String SAVED_FOLDER = "saved-folder";
    /** Tag for {@link #mCurrentConversation} */
    private static final String SAVED_CONVERSATION = "saved-conversation";
    /** Tag for {@link #mSelectedSet} */
    private static final String SAVED_SELECTED_SET = "saved-selected-set";

    /** Tag  used when loading a wait fragment */
    protected static final String TAG_WAIT = "wait-fragment";
    /** Tag used when loading a conversation list fragment. */
    public static final String TAG_CONVERSATION_LIST = "tag-conversation-list";
    /** Tag used when loading a conversation fragment. */
    public static final String TAG_CONVERSATION = "tag-conversation";
    /** Tag used when loading a folder list fragment. */
    protected static final String TAG_FOLDER_LIST = "tag-folder-list";

    private static final long CONVERSATION_LIST_THROTTLE_MS = 4000L;

    /** Are we on a tablet device or not. */
    public final boolean IS_TABLET_DEVICE;

    protected Account mAccount;
    protected Folder mFolder;
    protected ActionBarView mActionBarView;
    protected final RestrictedActivity mActivity;
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

    private final Set<Uri> mCurrentAccountUris = Sets.newHashSet();
    protected Settings mCachedSettings;
    protected ConversationCursor mConversationListCursor;
    private final DataSetObservable mConversationListObservable = new DataSetObservable() {
        @Override
        public void registerObserver(DataSetObserver observer) {
            final int count = mObservers.size();
            super.registerObserver(observer);
            LogUtils.d(LOG_TAG, "IN AAC.registerListObserver: %s before=%d after=%d", observer,
                    count, mObservers.size());
        }
        @Override
        public void unregisterObserver(DataSetObserver observer) {
            final int count = mObservers.size();
            super.unregisterObserver(observer);
            LogUtils.d(LOG_TAG, "IN AAC.unregisterListObserver: %s before=%d after=%d", observer,
                    count, mObservers.size());
        }
    };
    protected boolean mConversationListenerAdded = false;

    private boolean mIsConversationListScrolling = false;
    private long mConversationListRefreshTime = 0;
    private Timer mConversationListTimer = new Timer();
    private RefreshTimerTask mConversationListRefreshTask;

    /** Listeners that are interested in changes to current account settings. */
    private final ArrayList<Settings.ChangeListener> mSettingsListeners = Lists.newArrayList();

    /**
     * Selected conversations, if any.
     */
    private final ConversationSelectionSet mSelectedSet = new ConversationSelectionSet();

    private final int mFolderItemUpdateDelayMs;

    /** Keeps track of selected and unselected conversations */
    final protected ConversationPositionTracker mTracker =
            new ConversationPositionTracker(mSelectedSet);

    /**
     * Action menu associated with the selected set.
     */
    SelectedConversationsActionMenu mCabActionMenu;
    protected UndoBarView mUndoBarView;
    protected ConversationPagerController mPagerController;

    // this is split out from the general loader dispatcher because its loader doesn't return a
    // basic Cursor
    private final ConversationListLoaderCallbacks mListCursorCallbacks =
            new ConversationListLoaderCallbacks();

    protected static final String LOG_TAG = new LogUtils().getLogTag();
    /** Constants used to differentiate between the types of loaders. */
    private static final int LOADER_ACCOUNT_CURSOR = 0;
    private static final int LOADER_FOLDER_CURSOR = 2;
    private static final int LOADER_RECENT_FOLDERS = 3;
    private static final int LOADER_CONVERSATION_LIST = 4;
    private static final int LOADER_ACCOUNT_INBOX = 5;
    private static final int LOADER_SEARCH = 6;
    private static final int LOADER_ACCOUNT_UPDATE_CURSOR = 7;

    private static final int ADD_ACCOUNT_REQUEST_CODE = 1;

    /** The pending destructive action to be carried out before swapping the conversation cursor.*/
    private DestructiveAction mPendingDestruction;

    public AbstractActivityController(MailActivity activity, ViewMode viewMode) {
        mActivity = activity;
        mFragmentManager = mActivity.getFragmentManager();
        mViewMode = viewMode;
        mContext = activity.getApplicationContext();
        IS_TABLET_DEVICE = Utils.useTabletUI(mContext);
        mRecentFolderList = new RecentFolderList(mContext, this);
        // Allow the fragment to observe changes to its own selection set. No other object is
        // aware of the selected set.
        mSelectedSet.addObserver(this);

        mFolderItemUpdateDelayMs =
                mContext.getResources().getInteger(R.integer.folder_item_refresh_delay_ms);
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
     * @return
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
     * @return
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
        mActionBarView = (ActionBarView) LayoutInflater.from(mContext).inflate(
                R.layout.actionbar_view, null);
        if (actionBar != null && mActionBarView != null) {
            // Why have a different variable for the same thing? We should apply
            // the same actions
            // on mActionBarView instead.
            mActionBarView.initialize(mActivity, this, mViewMode, actionBar, mRecentFolderList);
        }
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
     * @return
     */
    protected abstract boolean isConversationListVisible();

    /**
     * Switch the current account to the one provided as an argument to the method.
     * @param account
     */
    private void switchAccount(Account account){
        // Current account is different from the new account, restart loaders and show
        // the account Inbox.
        mAccount = account;
        LogUtils.d(LOG_TAG, "AbstractActivityController.switchAccount(): mAccount = %s",
                mAccount.uri);
        cancelRefreshTask();
        onSettingsChanged(mAccount.settings);
        mActionBarView.setAccount(mAccount);
        loadAccountInbox();

        mRecentFolderList.setCurrentAccount(account);
        restartOptionalLoader(LOADER_RECENT_FOLDERS);
        mActivity.invalidateOptionsMenu();
        disableNotificationsOnAccountChange(mAccount);
        restartOptionalLoader(LOADER_ACCOUNT_UPDATE_CURSOR);
        MailAppProvider.getInstance().setLastViewedAccount(mAccount.uri.toString());
    }

    @Override
    public void onAccountChanged(Account account) {
        LogUtils.d(LOG_TAG, "onAccountChanged (%s) called.", account.uri);
        final boolean accountChanged = (mAccount == null) || !account.uri.equals(mAccount.uri);
        if (accountChanged) {
            switchAccount(account);
            return;
        }
        // Current account is the same as the new account, but the settings might be different.
        if (!account.settings.equals(mAccount.settings)){
            onSettingsChanged(account.settings);
            return;
        }
    }

    /**
     * Returns the URI of the current account's default inbox if available, otherwise
     * returns the empty URI {@link Uri#EMPTY}
     * @return
     */
    private Uri getDefaultInboxUri(Settings settings) {
        if (settings != null && settings.defaultInbox != null) {
            return settings.defaultInbox;
        }
        return Uri.EMPTY;
    }

    /**
     * Changes the settings for the current account. The new settings are provided as a parameter.
     * @param settings
     */
    public void onSettingsChanged(Settings settings) {
        dispatchSettingsChange(settings);
        resetActionBarIcon();
        mActivity.invalidateOptionsMenu();
        // If the user was viewing the default Inbox here, and the new setting contains a different
        // default Inbox, we don't want to load a different folder here.
    }

    @Override
    public Settings getSettings() {
        return mCachedSettings;
    }

    /**
     * Adds a listener interested in change in settings. If a class is storing a reference to
     * Settings, it should listen on changes, so it can receive updates to settings.
     * Must happen in the UI thread.
     */
    public void addSettingsListener(Settings.ChangeListener listener) {
        mSettingsListeners.add(listener);
    }

    /**
     * Removes a listener from receiving settings changes.
     * Must happen in the UI thread.
     */
    public void removeSettingsListener(Settings.ChangeListener listener) {
        mSettingsListeners.remove(listener);
    }

    /**
     * Method that lets the settings listeners know when the settings got changed.
     */
    private void dispatchSettingsChange(Settings updatedSettings) {
        mCachedSettings = updatedSettings;
        // Copy the list of current listeners so that
        final ArrayList<Settings.ChangeListener> allListeners =
                new ArrayList<Settings.ChangeListener>(mSettingsListeners);
        for (Settings.ChangeListener listener : allListeners) {
            if (listener != null) {
                listener.onSettingsChanged(mCachedSettings);
            }
        }
        // And we know that the ConversationListFragment is interested in changes to settings,
        // though it hasn't registered itself with us.
        final ConversationListFragment convList = getConversationListFragment();
        if (convList != null) {
            convList.onSettingsChanged(mCachedSettings);
        }
    }

    private void fetchSearchFolder(Intent intent) {
        Bundle args = new Bundle();
        args.putString(ConversationListContext.EXTRA_SEARCH_QUERY, intent
                .getStringExtra(ConversationListContext.EXTRA_SEARCH_QUERY));
        mActivity.getLoaderManager().restartLoader(LOADER_SEARCH, args, this);
    }

    @Override
    public void onFolderChanged(Folder folder) {
        if (folder != null && !folder.equals(mFolder)) {
            setFolder(folder);
            mConvListContext = ConversationListContext.forFolder(mContext, mAccount, mFolder);
            showConversationList(mConvListContext);

            // Add the folder that we were viewing to the recent folders list.
            // TODO: this may need to be fine tuned.  If this is the signal that is indicating that
            // the list is shown to the user, this could fire in one pane if the user goes directly
            // to a conversation
            updateRecentFolderList();
            cancelRefreshTask();
        }
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
    private void setFolder(Folder folder) {
        // Start watching folder for sync status.
        if (folder != null && !folder.equals(mFolder)) {
            LogUtils.d(LOG_TAG, "AbstractActivityController.setFolder(%s)", folder.name);
            final boolean folderWasNull = (mFolder == null);
            final LoaderManager lm = mActivity.getLoaderManager();
            mActionBarView.setRefreshInProgress(false);
            mFolder = folder;
            mActionBarView.setFolder(mFolder);

            // Only when we switch from one folder to another do we want to restart the
            // folder and conversation list loaders (to trigger onCreateLoader).
            // The first time this runs when the activity is [re-]initialized, we want to re-use the
            // previous loader's instance and data upon configuration change (e.g. rotation).
            if (folderWasNull) {
                lm.initLoader(LOADER_FOLDER_CURSOR, null, this);
                lm.initLoader(LOADER_CONVERSATION_LIST, null, mListCursorCallbacks);
            } else {
                lm.restartLoader(LOADER_FOLDER_CURSOR, null, this);
                lm.restartLoader(LOADER_CONVERSATION_LIST, null, mListCursorCallbacks);
            }
        } else if (folder == null) {
            LogUtils.wtf(LOG_TAG, "Folder in setFolder is null");
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == ADD_ACCOUNT_REQUEST_CODE) {
            // We were waiting for the user to create an account
            if (resultCode == Activity.RESULT_OK) {
                // restart the loader to get the updated list of accounts
                mActivity.getLoaderManager().initLoader(
                        LOADER_ACCOUNT_CURSOR, null, this);
            } else {
                // The user failed to create an account, just exit the app
                mActivity.finish();
            }
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
        initializeActionBar();
        // Allow shortcut keys to function for the ActionBar and menus.
        mActivity.setDefaultKeyMode(Activity.DEFAULT_KEYS_SHORTCUT);
        mResolver = mActivity.getContentResolver();
        mNewEmailReceiver = new SuppressNotificationReceiver();

        // All the individual UI components listen for ViewMode changes. This
        // simplifies the amount of logic in the AbstractActivityController, but increases the
        // possibility of timing-related bugs.
        mViewMode.addListener(this);
        mPagerController = new ConversationPagerController(mActivity, this);
        mUndoBarView = (UndoBarView) mActivity.findViewById(R.id.undo_view);
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
                // Open the folder.
                onFolderChanged((Folder) savedState.getParcelable(SAVED_FOLDER));
            }
        } else if (intent != null) {
            handleIntent(intent);
        }
        // Create the accounts loader; this loads the account switch spinner.
        mActivity.getLoaderManager().initLoader(LOADER_ACCOUNT_CURSOR, null, this);
        return true;
    }

    @Override
    public Dialog onCreateDialog(int id, Bundle bundle) {
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
        final int id = item.getItemId();
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
            case R.id.folder_options:
                Utils.showFolderSettings(mActivity.getActivityContext(), mAccount, mFolder);
                break;
            case R.id.help_info_menu_item:
                // TODO: enable context sensitive help
                Utils.showHelp(mActivity.getActivityContext(), mAccount, null);
                break;
            case R.id.feedback_menu_item:
                Utils.sendFeedback(mActivity.getActivityContext(), mAccount);
                break;
            case R.id.manage_folders_item:
                Utils.showManageFolder(mActivity.getActivityContext(), mAccount);
                break;
            default:
                handled = false;
                break;
        }
        return handled;
    }

    /**
     * Update the specified column name in conversation for a boolean value.
     * @param columnName
     * @param value
     */
    protected void updateCurrentConversation(String columnName, boolean value) {
        mConversationListCursor.updateBoolean(mContext, ImmutableList.of(mCurrentConversation),
                columnName, value);
        refreshConversationList();
    }

    /**
     * Update the specified column name in conversation for an integer value.
     * @param columnName
     * @param value
     */
    protected void updateCurrentConversation(String columnName, int value) {
        mConversationListCursor.updateInt(mContext, ImmutableList.of(mCurrentConversation),
                columnName, value);
        refreshConversationList();
    }

    protected void updateCurrentConversation(String columnName, String value) {
        mConversationListCursor.updateString(mContext, ImmutableList.of(mCurrentConversation),
                columnName, value);
        refreshConversationList();
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

    /**
     * Confirm (based on user's settings) and delete a conversation from the conversation list and
     * from the database.
     * @param showDialog
     * @param confirmResource
     * @param action
     */
    protected void confirmAndDelete(final Collection<Conversation> target, boolean showDialog,
            int confirmResource, final DestructiveAction action) {
        if (showDialog) {
            final AlertDialog.OnClickListener onClick = new AlertDialog.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    requestDelete(target, action);
                }
            };
            final CharSequence message = Utils.formatPlural(mContext, confirmResource, 1);
            new AlertDialog.Builder(mActivity.getActivityContext()).setMessage(message)
                    .setPositiveButton(R.string.ok, onClick)
                    .setNegativeButton(R.string.cancel, null)
                    .create().show();
        } else {
            requestDelete(target, action);
        }
    }

    /**
     * Request the removal of the current conversation with the specified destructive action.
     * @param action
     */
    protected void requestDelete(final Collection<Conversation> target,
            final DestructiveAction action) {
        removeAndDestroy(target, action);
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
        commitLeaveBehindItems();
    }

    @Override
    public void onResume() {
        // Register the receiver that will prevent the status receiver from
        // displaying its notification icon as long as we're running.
        // The SupressNotificationReceiver will block the broadcast if we're looking at the folder
        // that the notification was received for.
        disableNotifications();

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
        if (mFolder != null) {
            outState.putParcelable(SAVED_FOLDER, mFolder);
        }
        if (mCurrentConversation != null && mViewMode.getMode() == ViewMode.CONVERSATION) {
            outState.putParcelable(SAVED_CONVERSATION, mCurrentConversation);
        }
        if (!mSelectedSet.isEmpty()) {
            outState.putParcelable(SAVED_SELECTED_SET, mSelectedSet);
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

    @Override
    public void onDestroy() {
        // unregister the ViewPager's observer on the conversation cursor
        mPagerController.onDestroy();
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

        // We don't want to invalidate the options menu when switching to
        // conversation
        // mode, as it will happen when the conversation finishes loading.
        if (newMode != ViewMode.CONVERSATION) {
            mActivity.invalidateOptionsMenu();
        }
    }

    protected void commitLeaveBehindItems() {
        ConversationListFragment fragment = getConversationListFragment();
        if (fragment != null) {
            fragment.commitLeaveBehindItems();
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

    private void setAccount(Account account) {
        mAccount = account;
        LogUtils.d(LOG_TAG, "AbstractActivityController.setAccount(): mAccount = %s", mAccount.uri);
        dispatchSettingsChange(mAccount.settings);
        mActionBarView.setAccount(mAccount);
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
            setCurrentConversation(conversation);
            showConversation(mCurrentConversation);
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
                setAccount((Account) intent.getParcelableExtra(Utils.EXTRA_ACCOUNT));
            } else if (intent.hasExtra(Utils.EXTRA_ACCOUNT_STRING)) {
                setAccount(Account.newinstance(intent
                        .getStringExtra(Utils.EXTRA_ACCOUNT_STRING)));
            }
            if (mAccount != null) {
                mActivity.invalidateOptionsMenu();
            }

            Folder folder = null;
            if (intent.hasExtra(Utils.EXTRA_FOLDER)) {
                // Open the folder.
                LogUtils.d(LOG_TAG, "SHOW THE FOLDER at %s",
                        intent.getParcelableExtra(Utils.EXTRA_FOLDER));
                folder = (Folder) intent.getParcelableExtra(Utils.EXTRA_FOLDER);

            } else if (intent.hasExtra(Utils.EXTRA_FOLDER_STRING)) {
                // Open the folder.
                folder = new Folder(intent.getStringExtra(Utils.EXTRA_FOLDER_STRING));
            }
            if (folder != null) {
                onFolderChanged(folder);
                handled = true;
            }

            if (intent.hasExtra(Utils.EXTRA_CONVERSATION)) {
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
                setCurrentConversation(conversation);
                showConversation(mCurrentConversation);
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

                mViewMode.enterSearchResultsListMode();
                setAccount((Account) intent.getParcelableExtra(Utils.EXTRA_ACCOUNT));
                mActivity.invalidateOptionsMenu();
                restartOptionalLoader(LOADER_RECENT_FOLDERS);
                mRecentFolderList.setCurrentAccount(mAccount);
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
    private void restoreSelectedConversations(Bundle savedState) {
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
        // Set the current conversation just in case it wasn't already set.
        setCurrentConversation(conversation);
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

    @Override
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
        showConversation(conversation);
        if (mConvListContext != null && mConvListContext.isSearchResult()) {
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
                final Uri inboxUri;
                if (mCachedSettings != null) {
                    inboxUri = mCachedSettings.defaultInbox;
                } else {
                    inboxUri = mAccount.folderListUri;
                }
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

    /**
     * Start a loader with the given id. This should be called when we know that the previous
     * state of the application matches this state, and we are happy if we get the previously
     * created loader with this id. If that is not true, consider calling
     * {@link #restartOptionalLoader(int)} instead.
     * @param id
     */
    private void startLoader(int id) {
        final LoaderManager lm = mActivity.getLoaderManager();
        lm.initLoader(id, Bundle.EMPTY, this);
    }

    @Override
    public void registerConversationListObserver(DataSetObserver observer) {
        mConversationListObservable.registerObserver(observer);
    }

    @Override
    public void unregisterConversationListObserver(DataSetObserver observer) {
        mConversationListObservable.unregisterObserver(observer);
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

        // 1. current account is already set and is in allAccounts -> no-op
        // 2. current account is set and is not in allAccounts -> pick first (acct was deleted?)
        // 3. saved pref has an account -> pick that one
        // 4. otherwise just pick first

        Account newAccount = null;

        if (mAccount != null) {
            if (!mCurrentAccountUris.contains(mAccount.uri)) {
                newAccount = allAccounts[0];
            } else {
                newAccount = mAccount;
            }
        } else {
            final String lastAccountUri = MailAppProvider.getInstance().getLastViewedAccount();
            if (lastAccountUri != null) {
                for (int i = 0; i < allAccounts.length; i++) {
                    final Account acct = allAccounts[i];
                    if (lastAccountUri.equals(acct.uri.toString())) {
                        newAccount = acct;
                        break;
                    }
                }
            }
            if (newAccount == null) {
                newAccount = allAccounts[0];
            }
        }

        onAccountChanged(newAccount);
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
                // If the account list is not not null, and the account list cursor is empty,
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
                        isLoaderInitialized = updateAccounts(loader, data);
                    }
                }
                break;
            case LOADER_ACCOUNT_UPDATE_CURSOR:
                // We have gotten an update for current account.

                // Make sure that this is an update for what is the current account
                if (data != null && data.moveToFirst()) {
                    final Account updatedAccount = new Account(data);

                    if (updatedAccount.uri.equals(mAccount.uri)) {
                        // Update the controller's reference to the current account
                        mAccount = updatedAccount;
                        LogUtils.d(LOG_TAG, "AbstractActivityController.onLoadFinished(): "
                                + "mAccount = %s", mAccount.uri);
                        dispatchSettingsChange(mAccount.settings);

                        // Got an update for the current account
                        final boolean inWaitingMode = inWaitMode();
                        if (!updatedAccount.isAccountIntialized() && !inWaitingMode) {
                            // Transition to waiting mode
                            showWaitForInitialization();
                        } else if (updatedAccount.isAccountIntialized() && inWaitingMode) {
                            // Dismiss waiting mode
                            hideWaitForInitialization();
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
                    Folder folder = new Folder(data);
                    if (folder.isSyncInProgress()) {
                        mActionBarView.onRefreshStarted();
                    } else {
                        // Stop the spinner here.
                        mActionBarView.onRefreshStopped(folder.lastSyncResult);
                    }
                    mActionBarView.onFolderUpdated(folder);
                    final ConversationListFragment convList = getConversationListFragment();
                    if (convList != null) {
                        convList.onFolderUpdated(folder);
                    }
                    LogUtils.d(LOG_TAG, "FOLDER STATUS = %d", folder.syncStatus);
                } else {
                    LogUtils.d(LOG_TAG, "Unable to get the folder %s",
                            mFolder != null ? mAccount.name : "");
                }
                break;
            case LOADER_RECENT_FOLDERS:
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
                setFolder(search);
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
     *
     * These Destructive Actions should not be used by themselves. These must be used by
     * classes that update the UI state like {@link OnePaneController.OnePaneDestructiveAction},
     * {@link TwoPaneController.TwoPaneDestructiveAction} and others.
     */
    protected class ConversationAction implements DestructiveAction {
        /**
         * The action to be performed. This is specified as the resource ID of the menu item
         * corresponding to this action: R.id.delete, R.id.report_spam, etc.
         */
        private final int mAction;
        /** The action will act upon these conversations */
        private final Collection<Conversation> mTarget = new ArrayList<Conversation>();
        /** Whether this destructive action has already been performed */
        private boolean mCompleted;

        /**
         * Create a listener object. action is one of four constants: R.id.y_button (archive),
         * R.id.delete , R.id.mute, and R.id.report_spam.
         * @param action
         * @param target Conversation that we want to apply the action to.
         */
        public ConversationAction(int action, Collection<Conversation> target) {
            mAction = action;
            mTarget.addAll(target);
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
            switch (mAction) {
                case R.id.archive:
                    LogUtils.d(LOG_TAG, "Archiving: %s", mTarget);
                    mConversationListCursor.archive(mContext, mTarget);
                    break;
                case R.id.delete:
                    LogUtils.d(LOG_TAG, "Deleting: %s", mTarget);
                    mConversationListCursor.delete(mContext, mTarget);
                    break;
                case R.id.mute:
                    LogUtils.d(LOG_TAG, "Muting: %s", mTarget);
                    if (mFolder.supportsCapability(FolderCapabilities.DESTRUCTIVE_MUTE)) {
                        for (Conversation c : mTarget) {
                            c.localDeleteOnUpdate = true;
                        }
                    }
                    mConversationListCursor.mute(mContext, mTarget);
                    break;
                case R.id.report_spam:
                    LogUtils.d(LOG_TAG, "Reporting spam: %s", mTarget);
                    mConversationListCursor.reportSpam(mContext, mTarget);
                    break;
                case R.id.remove_star:
                    LogUtils.d(LOG_TAG, "Removing star: %s", mTarget);
                    // Star removal is destructive in the Starred folder.
                    mConversationListCursor.updateBoolean(mContext, mTarget,
                            ConversationColumns.STARRED, false);
                    break;
                case R.id.mark_not_important:
                    LogUtils.d(LOG_TAG, "Marking not-important: %s", mTarget);
                    // Marking not important is destructive in a mailbox containing only important
                    // messages
                    mConversationListCursor.updateInt(mContext, mTarget,
                            ConversationColumns.PRIORITY, UIProvider.ConversationPriority.LOW);
                    break;
            }
            refreshConversationList();
        }

        /**
         * Returns true if this action has been performed, false otherwise.
         * @return
         */
        private synchronized boolean isPerformed() {
            if (mCompleted) {
                return true;
            }
            mCompleted = true;
            return false;
        }
    }

    // Called from the FolderSelectionDialog after a user is done changing
    // folders.
    @Override
    public final void onFolderChangesCommit(ArrayList<Folder> folderChangeList) {
        // Get currently active folder info and compare it to the list
        // these conversations have been given; if they no longer contain
        // the selected folder, delete them from the list.
        final HashSet<String> folderUris = new HashSet<String>();
        boolean hasInbox = false;
        if (folderChangeList != null && !folderChangeList.isEmpty()) {
            for (Folder f : folderChangeList) {
                folderUris.add(f.uri.toString());
                hasInbox |= (f.type == UIProvider.FolderType.INBOX);
            }
        }
        // Destructive if we are in Priority Inbox and we remove the Inbox label.
        final boolean currentlyViewingInbox = (mFolder.type == UIProvider.FolderType.INBOX);
        final boolean destructiveChange = currentlyViewingInbox ? !hasInbox :
                !folderUris.contains(mFolder.uri.toString());
        updateCurrentConversation(ConversationColumns.FOLDER_LIST,
                Folder.getUriString(folderChangeList));
        updateCurrentConversation(ConversationColumns.RAW_FOLDERS,
                Folder.getSerializedFolderString(mFolder, folderChangeList));
        // TODO: (mindyp): set ConversationColumns.RAW_FOLDERS like in
        // SelectedConversationsActionMenu
        if (destructiveChange) {
            mCurrentConversation.localDeleteOnUpdate = true;
            final Collection<Conversation> target = ImmutableList.of(mCurrentConversation);
            final DestructiveAction folderChange = getFolderDestructiveAction();
            requestDelete(target, folderChange);
        } else {
            refreshConversationList();
        }
    }

    @Override
    public void onRefreshRequired() {
        if (mIsConversationListScrolling) {
            LogUtils.d(LOG_TAG, "onRefreshRequired: delay until scrolling done");
            return;
        }
        // Refresh the query in the background
        long now = System.currentTimeMillis();
        long sinceLastRefresh = now - mConversationListRefreshTime;
//        if (sinceLastRefresh > CONVERSATION_LIST_THROTTLE_MS) {
            if (mConversationListCursor.isRefreshRequired()) {
                mConversationListCursor.refresh();
                mTracker.updateCursor(mConversationListCursor);
                mConversationListRefreshTime = now;
            }
//        } else {
//            long delay = CONVERSATION_LIST_THROTTLE_MS - sinceLastRefresh;
//            LogUtils.d(LOG_TAG, "onRefreshRequired: delay for %sms", delay);
//            mConversationListRefreshTask = new RefreshTimerTask(this, mHandler);
//            mConversationListTimer.schedule(mConversationListRefreshTask, delay);
//        }
    }

    /**
     * Called when the {@link ConversationCursor} is changed or has new data in it.
     * <p>
     * {@inheritDoc}
     */
    @Override
    public void onRefreshReady() {
        if (!mIsConversationListScrolling) {
            // Swap cursors
            mConversationListCursor.sync();
        }
        mTracker.updateCursor(mConversationListCursor);
    }

    @Override
    public void onDataSetChanged() {
        updateConversationListFragment();
        mConversationListObservable.notifyChanged();
    }

    private void updateConversationListFragment() {
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
    public void onScrollStateChanged(AbsListView view, int scrollState) {
        boolean isScrolling = (scrollState != OnScrollListener.SCROLL_STATE_IDLE);
        if (!isScrolling) {
            if (mConversationListCursor.isRefreshRequired()) {
                LogUtils.d(LOG_TAG, "Stop scrolling: refresh");
                mConversationListCursor.refresh();
            } else if (mConversationListCursor.isRefreshReady()) {
                LogUtils.d(LOG_TAG, "Stop scrolling: try sync");
                onRefreshReady();
            }
        }
        mIsConversationListScrolling = isScrolling;
    }

    @Override
    public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount,
            int totalItemCount) {
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
        mCabActionMenu = new SelectedConversationsActionMenu(mActivity, set,
                convList.getAnimatedAdapter(), this, this,
                mAccount, mFolder, (SwipeableListView) convList.getListView());
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
        if (mCabActionMenu != null) {
            mCabActionMenu.deactivate();
        }
    }

    /**
     * Re-enable the CAB menu if required. The selection set is not changed.
     */
    protected void enableCabMode() {
        if (mCabActionMenu != null) {
            mCabActionMenu.activate();
        }
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
     * Handles dropping conversations to a label.
     */
    @Override
    public void handleDrop(DragEvent event, final Folder folder) {
        if (!supportsDrag(event, folder)) {
            return;
        }
        final Collection<Conversation> conversations = mSelectedSet.values();
        final Collection<Folder> dropTarget = ImmutableList.of(folder);
        // Drag and drop is destructive: we remove conversations from the current folder.
        final DestructiveAction action = getFolderChange(conversations, dropTarget, true);
        removeAndDestroy(conversations, action);
    }

    @Override
    public void onUndoCancel() {
        mUndoBarView.hide(false);
    }


    @Override
    public void onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            if (mUndoBarView != null && !mUndoBarView.isEventInUndo(event)) {
                mUndoBarView.hide(true);
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

            mConversationListObservable.notifyChanged();

            // Register the AbstractActivityController as a listener to changes in
            // data in the cursor.
            final ConversationListFragment convList = getConversationListFragment();
            if (convList != null) {
                convList.onCursorUpdated();
                convList.getListView().setOnScrollListener(AbstractActivityController.this);

                if (convList.isVisible()) {
                    // The conversation list is visible.
                    Utils.setConversationCursorVisibility(mConversationListCursor, true);
                }
            }
            // Shown for search results in two-pane mode only.
            if (shouldShowFirstConversation()) {
                if (mConversationListCursor.getCount() > 0) {
                    mConversationListCursor.moveToPosition(0);
                    if (convList != null) {
                        convList.getListView().setItemChecked(0, true);
                    }
                    final Conversation conv = new Conversation(mConversationListCursor);
                    conv.position = 0;
                    onConversationSelected(conv);
                }
            }
        }

        @Override
        public void onLoaderReset(Loader<ConversationCursor> loader) {
            final ConversationListFragment convList = getConversationListFragment();
            if (convList == null) {
                return;
            }
            convList.onCursorUpdated();
        }
    }

    @Override
    public void sendConversationRead(String toFragment, Conversation conversation, boolean state,
            boolean local) {
        if (toFragment.equals(TAG_CONVERSATION_LIST)) {
            if (mConversationListCursor != null) {
                if (local) {
                    mConversationListCursor.setConversationColumn(conversation.uri.toString(), ConversationColumns.READ,
                            state);
                } else {
                    mConversationListCursor.markRead(mContext, state, conversation);
                }
            }
        } else if (toFragment.equals(TAG_CONVERSATION)) {
            // TODO Handle setting read in conversation view
        }
    }

    @Override
    public void sendConversationUriStarred(String toFragment, String conversationUri,
            boolean state, boolean local) {
        if (toFragment.equals(TAG_CONVERSATION_LIST)) {
            if (mConversationListCursor != null) {
                if (local) {
                    mConversationListCursor.setConversationColumn(conversationUri, ConversationColumns.STARRED, state);
                } else {
                    mConversationListCursor.updateBoolean(mContext, conversationUri, ConversationColumns.STARRED, state);
                }
            }
        } else if (toFragment.equals(TAG_CONVERSATION)) {
            // TODO Handle setting starred in conversation view
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
     * embellish this method any more. This is a temporary workaround to reduce the number of
     * {@link DestructiveAction} classes. Please do not copy this paradigm.
     * @param action
     */
    protected final void registerDestructiveAction(DestructiveAction action) {
        // TODO(viki): This is not a good idea. The best solution is for clients to request a
        // destructive action from the controller and for the controller to own the action. This is
        // a half-way solution while refactoring DestructiveAction.
        destroyPending(action);
        return;
    }

    /**
     * Listener to act upon destructive actions carried out on multiple conversations. Destructive
     * actions are like delete/archive, and they require the UI state to remove the conversations
     * from the UI.
     */
    private class BatchDestruction implements DestructiveAction {
        private ConversationAction mConversationDeleter;
        /** Whether this destructive action has already been performed */
        private boolean mCompleted = false;
        private final int mId;

        /**
         * Create a destructive action with an ID that is the same as the menu IDs: R.id.delete,
         * R.id.archive, R.id.mute, ...
         * @param action
         */
        private BatchDestruction(int action) {
            mConversationDeleter = new ConversationAction(action, mSelectedSet.values());
            mId = action;
        }

        @Override
        public void performAction() {
            if (isPerformed()) {
                return;
            }
            final int size = mConversationDeleter.mTarget.size();
            onUndoAvailable(new UndoOperation(size, mId, true));
            mConversationDeleter.performAction();
            mSelectedSet.clear();
        }
        /**
         * Returns true if this action has been performed, false otherwise.
         * @return
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
     * Get a destructive action for selected conversations.
     * @param action
     * @return
     */
    // TODO(viki): This either should be in the ActivityController or removed entirely.
    // What we have right now is a half-way solution during the refactoring.
    public final DestructiveAction getBatchDestruction(int action) {
        final DestructiveAction da = new BatchDestruction(action);
        registerDestructiveAction(da);
        return da;
    }

    /**
     * Class to change the folders that are assigned to a set of conversations. This is destructive
     * because the user can remove the current folder from the conversation, in which case it has
     * to be animated away from the current folder.
     */
    private class FolderDestruction implements DestructiveAction {
        private final Collection<Conversation> mTarget = new ArrayList<Conversation>();
        private final ArrayList<Folder> mFolderList = new ArrayList<Folder>();
        private final boolean mIsDestructive;
        /** Whether this destructive action has already been performed */
        private boolean mCompleted;

        /**
         * Create a new folder destruction object to act on the given conversations.
         * @param target
         */
        private FolderDestruction(final Collection<Conversation> target,
                final Collection<Folder> folders, boolean isDestructive) {
            mTarget.addAll(target);
            mFolderList.addAll(folders);
            mIsDestructive = isDestructive;
        }

        @Override
        public void performAction() {
            if (isPerformed()) {
                return;
            }
            if (mIsDestructive) {
                UndoOperation undoOp = new UndoOperation(mTarget.size(), R.id.change_folder);
                onUndoAvailable(undoOp);
            }
            mConversationListCursor.updateString(mContext, mTarget,
                    ConversationColumns.FOLDER_LIST, Folder.getUriString(mFolderList));
            mConversationListCursor.updateString(mContext, mTarget,
                    ConversationColumns.RAW_FOLDERS,
                    Folder.getSerializedFolderString(mFolder, mFolderList));
            refreshConversationList();
        }
        /**
         * Returns true if this action has been performed, false otherwise.
         * @return
         */
        private synchronized boolean isPerformed() {
            if (mCompleted) {
                return true;
            }
            mCompleted = true;
            return false;
        }
    }

    public final DestructiveAction getFolderChange(Collection<Conversation> target,
            Collection<Folder> folders, boolean isDestructive){
        final DestructiveAction da = new FolderDestruction(target, folders, isDestructive);
        registerDestructiveAction(da);
        return da;
    }

    /**
     * Safely refresh the conversation list if it exists.
     */
    public void refreshConversationList() {
        final ConversationListFragment convList = getConversationListFragment();
        if (convList == null) {
            return;
        }
        convList.requestListRefresh();
    }

    /**
     * Remove conversations from the UI and call the destructive action when the UI state is
     * updated.
     * @param target
     * @param action
     */
    public void removeAndDestroy(final Collection<Conversation> target,
            final DestructiveAction action) {
        final ConversationListFragment convList = getConversationListFragment();
        if (convList != null) {
            convList.requestDelete(target, action);
        }
    }
}
