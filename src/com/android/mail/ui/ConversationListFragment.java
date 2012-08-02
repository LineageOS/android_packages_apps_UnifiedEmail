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

import android.app.Activity;
import android.app.ListFragment;
import android.content.Context;
import android.content.res.Resources;
import android.database.DataSetObserver;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.MarginLayoutParams;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ListView;
import android.widget.TextView;

import com.android.mail.ConversationListContext;
import com.android.mail.R;
import com.android.mail.browse.ConversationCursor;
import com.android.mail.browse.ConversationItemView;
import com.android.mail.browse.ConversationListFooterView;
import com.android.mail.providers.Account;
import com.android.mail.providers.Conversation;
import com.android.mail.providers.Folder;
import com.android.mail.providers.Settings;
import com.android.mail.providers.UIProvider;
import com.android.mail.providers.UIProvider.AccountCapabilities;
import com.android.mail.providers.UIProvider.FolderCapabilities;
import com.android.mail.providers.UIProvider.Swipe;
import com.android.mail.ui.SwipeableListView.SwipeCompleteListener;
import com.android.mail.ui.ViewMode.ModeChangeListener;
import com.android.mail.utils.LogTag;
import com.android.mail.utils.LogUtils;
import com.android.mail.utils.Utils;

import java.util.Collection;

/**
 * The conversation list UI component.
 */
public final class ConversationListFragment extends ListFragment implements
        OnItemLongClickListener, ModeChangeListener, SwipeCompleteListener,
        Settings.ChangeListener {
    // Keys used to pass data to {@link ConversationListFragment}.
    private static final String CONVERSATION_LIST_KEY = "conversation-list";
    // Key used to keep track of the scroll state of the list.
    private static final String LIST_STATE_KEY = "list-state";
    private static final String LOG_TAG = LogTag.getLogTag();

    // True if we are on a tablet device
    private static boolean mTabletDevice;

    /**
     * Frequency of update of timestamps. Initialized in {@link #onCreate(Bundle)} and final
     * afterwards.
     */
    private static int TIMESTAMP_UPDATE_INTERVAL = 0;

    private ControllableActivity mActivity;

    // Control state.
    private ConversationListCallbacks mCallbacks;

    private final Handler mHandler = new Handler();
    // List save state.
    private Parcelable mListSavedState;

    // The internal view objects.
    private SwipeableListView mListView;

    private TextView mSearchResultCountTextView;
    private TextView mSearchStatusTextView;

    private View mSearchStatusView;

    /**
     * Current Account being viewed
     */
    private Account mAccount;
    /**
     * Current folder being viewed.
     */
    private Folder mFolder;

    /**
     * A simple method to update the timestamps of conversations periodically.
     */
    private Runnable mUpdateTimestampsRunnable = null;

    private ConversationListContext mViewContext;

    private AnimatedAdapter mListAdapter;

    private ConversationListFooterView mFooterView;
    private int mSwipeAction;
    private ErrorListener mErrorListener;
    private DataSetObserver mFolderObserver;
    private DataSetObserver mConversationListStatusObserver;

    private ConversationSelectionSet mSelectedSet;

    /**
     * Constructor needs to be public to handle orientation changes and activity lifecycle events.
     */
    public ConversationListFragment() {
        super();
    }

    // update the pager title strip as the Folder's conversation count changes
    private class FolderObserver extends DataSetObserver {
        @Override
        public void onChanged() {
            onFolderUpdated(mActivity.getFolderController().getFolder());
        }
    }

    private class ConversationListStatusObserver extends DataSetObserver {
        @Override
        public void onChanged() {
            // update footer
            onConversationListStatusUpdated();
        }
    }

    /**
     * Creates a new instance of {@link ConversationListFragment}, initialized to display
     * conversation list context.
     */
    public static ConversationListFragment newInstance(ConversationListContext viewContext) {
        ConversationListFragment fragment = new ConversationListFragment();
        Bundle args = new Bundle();
        args.putBundle(CONVERSATION_LIST_KEY, viewContext.toBundle());
        fragment.setArguments(args);
        return fragment;
    }

    /**
     * Show the header if the current conversation list is showing search results.
     */
    void configureSearchResultHeader() {
        if (mActivity == null) {
            return;
        }
        // Only show the header if the context is for a search result
        final Resources res = getResources();
        final boolean showHeader = isSearchResult();
        // TODO(viki): This code contains intimate understanding of the view. Much of this logic
        // needs to reside in a separate class that handles the text view in isolation. Then,
        // that logic can be reused in other fragments.
        if (showHeader) {
            mSearchStatusTextView.setText(res.getString(R.string.search_results_searching_header));
            // Initially reset the count
            mSearchResultCountTextView.setText("");
        }
        mSearchStatusView.setVisibility(showHeader ? View.VISIBLE : View.GONE);
        int marginTop = showHeader ? (int) res.getDimension(R.dimen.notification_view_height) : 0;
        MarginLayoutParams layoutParams = (MarginLayoutParams) mListView.getLayoutParams();
        layoutParams.topMargin = marginTop;
        mListView.setLayoutParams(layoutParams);
    }

    /**
     * Show the header if the current conversation list is showing search results.
     */
    private void updateSearchResultHeader(int count) {
        if (mActivity == null) {
            return;
        }
        // Only show the header if the context is for a search result
        final Resources res = getResources();
        final boolean showHeader = isSearchResult();
        if (showHeader) {
            mSearchStatusTextView.setText(res.getString(R.string.search_results_header));
            mSearchResultCountTextView
                    .setText(res.getString(R.string.search_results_loaded, count));
        }
    }

    /**
     * Initializes all internal state for a rendering.
     */
    private void initializeUiForFirstDisplay() {
        // TODO(mindyp): find some way to make the notification container more re-usable.
        // TODO(viki): refactor according to comment in configureSearchResultHandler()
        mSearchStatusView = mActivity.findViewById(R.id.search_status_view);
        mSearchStatusTextView = (TextView) mActivity.findViewById(R.id.search_status_text_view);
        mSearchResultCountTextView = (TextView) mActivity.findViewById(
                R.id.search_result_count_view);
    }

    private boolean isSearchResult() {
        return mViewContext != null && mViewContext.isSearchResult();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        // Strictly speaking, we get back an android.app.Activity from getActivity. However, the
        // only activity creating a ConversationListContext is a MailActivity which is of type
        // ControllableActivity, so this cast should be safe. If this cast fails, some other
        // activity is creating ConversationListFragments. This activity must be of type
        // ControllableActivity.
        final Activity activity = getActivity();
        if (! (activity instanceof ControllableActivity)) {
            LogUtils.e(LOG_TAG, "ConversationListFragment expects only a ControllableActivity to" +
                    "create it. Cannot proceed.");
        }
        mActivity = (ControllableActivity) activity;
        mCallbacks = mActivity.getListHandler();
        mErrorListener = mActivity.getErrorListener();
        // Start off with the current state of the folder being viewed.
        mFooterView = (ConversationListFooterView) LayoutInflater.from(
                mActivity.getActivityContext()).inflate(R.layout.conversation_list_footer_view,
                null);
        mListAdapter = new AnimatedAdapter(mActivity.getApplicationContext(), -1,
                getConversationListCursor(), mActivity.getSelectedSet(), mAccount,
                mActivity.getSettings(), mActivity.getViewMode(), mListView);
        mListAdapter.addFooter(mFooterView);
        mListView.setAdapter(mListAdapter);
        mSelectedSet = mActivity.getSelectedSet();
        mListView.setSelectionSet(mSelectedSet);
        mListAdapter.hideFooter();
        mFolderObserver = new FolderObserver();
        mActivity.getFolderController().registerFolderObserver(mFolderObserver);
        mConversationListStatusObserver = new ConversationListStatusObserver();
        mActivity.getConversationUpdater().registerConversationListObserver(
                mConversationListStatusObserver);
        mTabletDevice = Utils.useTabletUI(mActivity.getApplicationContext());
        initializeUiForFirstDisplay();
        configureSearchResultHeader();
        // The onViewModeChanged callback doesn't get called when the mode object is created, so
        // force setting the mode manually this time around.
        onViewModeChanged(mActivity.getViewMode().getMode());

        // Restore the list state
        if (mListSavedState != null) {
            mListView.onRestoreInstanceState(mListSavedState);

            // TODO: find a better way to unset the selected item when restoring
            mListView.clearChoices();
        }

        if (mActivity.isFinishing()) {
            // Activity is finishing, just bail.
            return;
        }

        // Show list and start loading list.
        showList();
        ToastBarOperation pendingOp = mActivity.getPendingToastOperation();
        if (pendingOp != null) {
            // Clear the pending operation
            mActivity.setPendingToastOperation(null);
            mActivity.onUndoAvailable(pendingOp);
        }
    }

    public AnimatedAdapter getAnimatedAdapter() {
        return mListAdapter;
    }

    @Override
    public void onCreate(Bundle savedState) {
        super.onCreate(savedState);

        // Initialize fragment constants from resources
        final Resources res = getResources();
        TIMESTAMP_UPDATE_INTERVAL = res.getInteger(R.integer.timestamp_update_interval);
        mUpdateTimestampsRunnable = new Runnable(){
            @Override
            public void run() {
                mListView.invalidateViews();
                mHandler.postDelayed(mUpdateTimestampsRunnable, TIMESTAMP_UPDATE_INTERVAL);
            }
        };

        // Get the context from the arguments
        final Bundle args = getArguments();
        mViewContext = ConversationListContext.forBundle(args.getBundle(CONVERSATION_LIST_KEY));
        mAccount = mViewContext.account;
        // TODO(mindyp): do we want this as a setting?
        mSwipeAction = mAccount.supportsCapability(AccountCapabilities.ARCHIVE) ?
                R.id.archive : R.id.delete;
        if (savedState != null) {
            mListSavedState = savedState.getParcelable(LIST_STATE_KEY);
        }
        setRetainInstance(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater,
            ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.conversation_list, null);
        mListView = (SwipeableListView) rootView.findViewById(android.R.id.list);
        mListView.setHeaderDividersEnabled(false);
        mListView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        mListView.setOnItemLongClickListener(this);
        mListView.enableSwipe(mAccount.supportsCapability(AccountCapabilities.UNDO));
        // Note - we manually save/restore the listview state.
        mListView.setSaveEnabled(false);

        ConversationCursor conversationListCursor = getConversationListCursor();
        // Belt and suspenders here; make sure we do any necessary sync of the ConversationCursor
        if (conversationListCursor != null && conversationListCursor.isRefreshReady()) {
            conversationListCursor.sync();
        }
        return rootView;
    }

    @Override
    public void onDestroyView() {
        mListSavedState = mListView.onSaveInstanceState();

        // Set a null cursor in the dapter, and clear the list's adapter
        mListAdapter.swapCursor(null);
        mListView.setAdapter(null);

        mActivity.unsetViewModeListener(this);
        if (!mActivity.isChangingConfigurations()) {
            mActivity.getLoaderManager().destroyLoader(mViewContext.hashCode());
        }
        if (mFolderObserver != null) {
            mActivity.getFolderController().unregisterFolderObserver(mFolderObserver);
            mFolderObserver = null;
        }
        if (mConversationListStatusObserver != null) {
            mActivity.getConversationUpdater().unregisterConversationListObserver(
                    mConversationListStatusObserver);
            mConversationListStatusObserver = null;
        }
        super.onDestroyView();
    }

    /**
     * There are three binary variables, which determine what we do with a message.
     * checkbEnabled: Whether check boxes are enabled or not (forced true on tablet)
     * cabModeOn: Whether CAB mode is currently on or not.
     * pressType: long or short tap
     * (There is a third possibility: phone or tablet, but they have <em>identical</em> behavior)
     * The matrix of possibilities is:
     * <p>Long tap:
     * Always toggle selection of conversation. If CAB mode is not started, then start it.
     * <pre>
     *              | Checkboxes | No Checkboxes
     *    ----------+------------+---------------
     *    CAB mode  |   Select   |     Select
     *    List mode |   Select   |     Select
     *
     * </pre>
     * Reference: http://b/issue?id=6392199
     * <p>
     * {@inheritDoc}
     */
    @Override
    public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
        // Ignore anything that is not a conversation item. Could be a footer.
        if (!(view instanceof ConversationItemView)) {
            return true;
        }
        ((ConversationItemView) view).toggleCheckMark();
        return true;
    }

    /**
     * See the comment for {@link #onItemLongClick(AdapterView, View, int, long)}.
     * <p>Short tap behavior:
     * <pre>
     *              | Checkboxes | No Checkboxes
     *    ----------+------------+---------------
     *    CAB mode  |    Peek    |     Select
     *    List mode |    Peek    |      Peek
     * </pre>
     * Reference: http://b/issue?id=6392199
     * <p>
     * {@inheritDoc}
     */
    @Override
    public void onListItemClick(ListView l, View view, int position, long id) {
        // Ignore anything that is not a conversation item. Could be a footer.
        if (!(view instanceof ConversationItemView)) {
            return;
        }
        if (mAccount.settings.hideCheckboxes && !mSelectedSet.isEmpty()) {
            ((ConversationItemView) view).toggleCheckMark();
        } else {
            viewConversation(position);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mListView != null) {
            outState.putParcelable(LIST_STATE_KEY, mListView.onSaveInstanceState());
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        mHandler.postDelayed(mUpdateTimestampsRunnable, TIMESTAMP_UPDATE_INTERVAL);
    }

    @Override
    public void onStop() {
        super.onStop();
        mHandler.removeCallbacks(mUpdateTimestampsRunnable);
    }

    @Override
    public void onViewModeChanged(int newMode) {
        // Change the divider based on view mode.
        if (mTabletDevice) {
            if (newMode == ViewMode.CONVERSATION) {
                mListView.setBackgroundResource(R.drawable.panel_conversation_leftstroke);
            } else if (newMode == ViewMode.CONVERSATION_LIST) {
                // There are no selected conversations when in conversation list mode.
                mListView.clearChoices();
                mListView.setBackgroundDrawable(null);
            }
        } else {
            mListView.setBackgroundDrawable(null);
        }
    }
    /**
     * Handles a request to show a new conversation list, either from a search query or for viewing
     * a folder. This will initiate a data load, and hence must be called on the UI thread.
     */
    private void showList() {
        mListView.setEmptyView(null);
        onFolderUpdated(mActivity.getFolderController().getFolder());
    }

    /**
     * View the message at the given position.
     * @param position
     */
    protected void viewConversation(int position) {
        LogUtils.d(LOG_TAG, "ConversationListFragment.viewConversation(%d)", position);
        setSelected(position);
        final ConversationCursor cursor = getConversationListCursor();
        if (cursor != null && cursor.moveToPosition(position)) {
            final Conversation conv = new Conversation(cursor);
            conv.position = position;
            mCallbacks.onConversationSelected(conv);
        }
    }

    /**
     * Sets the selected position (the highlighted conversation) to the position provided here.
     * @param position
     */
    protected final void setSelected(int position) {
        mListView.setItemChecked(position, true);
        mListView.smoothScrollToPosition(position);
    }

    private ConversationCursor getConversationListCursor() {
        return mCallbacks != null ? mCallbacks.getConversationListCursor() : null;
    }

    /**
     * Request a refresh of the list. No sync is carried out and none is promised.
     */
    public void requestListRefresh() {
        mListAdapter.notifyDataSetChanged();
    }

    /**
     * Change the UI to delete the conversations provided and then call the
     * {@link DestructiveAction} provided here <b>after</b> the UI has been updated.
     * @param conversations
     * @param action
     */
    public void requestDelete(Collection<Conversation> conversations, DestructiveAction action) {
        for (Conversation conv : conversations) {
            conv.localDeleteOnUpdate = true;
        }
        // Delete the local delete items (all for now) and when done,
        // update...
        mListAdapter.delete(conversations, action);
    }

    public void onFolderUpdated(Folder folder) {
        mFolder = folder;
        setSwipeAction();
        if (mFolder == null) {
            return;
        }
        mListAdapter.setFolder(mFolder);
        mFooterView.setFolder(mFolder);
        if (mFolder.lastSyncResult != UIProvider.LastSyncResult.SUCCESS) {
            mErrorListener.onError(mFolder, false);
        }
    }

    public void onConversationListStatusUpdated() {
        ConversationCursor cursor = getConversationListCursor();
        final boolean showFooter = mFooterView.updateStatus(cursor);
        Bundle extras = cursor.getExtras();
        int error = extras.containsKey(UIProvider.CursorExtraKeys.EXTRA_ERROR) ?
                extras.getInt(UIProvider.CursorExtraKeys.EXTRA_ERROR)
                : UIProvider.LastSyncResult.SUCCESS;
        if (error != UIProvider.LastSyncResult.SUCCESS) {
            // Check the status of the folder to see if we are done loading.
            updateSearchResultHeader(mFolder != null ? mFolder.totalCount : 0);
        }
        mListAdapter.setFooterVisibility(showFooter);
    }

    private void setSwipeAction() {
        int swipeSetting = Settings.getSwipeSetting(mAccount.settings);
        if (swipeSetting == Swipe.DISABLED
                || !mAccount.supportsCapability(AccountCapabilities.UNDO)) {
            mListView.enableSwipe(false);
        } else {
            int action;
            if (isSearchResult()) {
                action = R.id.delete;
            } else if (mFolder == null) {
                action = R.id.change_folder;
            } else {
                // We have enough information to respect user settings.
                switch (swipeSetting) {
                    case Swipe.ARCHIVE:
                        if (mAccount.supportsCapability(AccountCapabilities.ARCHIVE)) {
                            if (mFolder.supportsCapability(FolderCapabilities.ARCHIVE)) {
                                action = R.id.archive;
                                break;
                            } else if (mFolder.supportsCapability
                                    (FolderCapabilities.CAN_ACCEPT_MOVED_MESSAGES)) {
                                action = R.id.change_folder;
                                break;
                            }
                        }
                    case Swipe.DELETE:
                    default:
                        action = R.id.delete;
                        break;
                }
            }
            mListView.setSwipeAction(action);
        }
        mListView.setCurrentFolder(mFolder);
    }

    @Override
    public void onSwipeComplete(Collection<Conversation> conversations) {
        Context context = getActivity().getApplicationContext();
        ConversationCursor cc = getConversationListCursor();
        switch (mSwipeAction) {
            case R.id.archive:
                cc.archive(context, conversations);
                break;
            case R.id.delete:
                cc.delete(context, conversations);
                break;
        }
        mListAdapter.notifyDataSetChanged();
        if (!mActivity.getSelectedSet().isEmpty()) {
            mActivity.getSelectedSet().clear();
        }
        mActivity.onUndoAvailable(new ToastBarOperation(conversations.size(), mSwipeAction,
                ToastBarOperation.UNDO));
    }

    public void onCursorUpdated() {
        if (mListAdapter != null) {
            mListAdapter.swapCursor(mCallbacks.getConversationListCursor());
        }
        onFolderUpdated(mFolder);
    }

    public void commitDestructiveActions() {
        if (mListView != null) {
            mListView.commitDestructiveActions();

        }
    }

    @Override
    public void onSettingsChanged(Settings updatedSettings) {
        if (mListAdapter != null) {
            mListAdapter.onSettingsChanged(updatedSettings);
        }
    }
}
