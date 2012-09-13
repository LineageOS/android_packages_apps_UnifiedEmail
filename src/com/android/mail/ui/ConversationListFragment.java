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
import com.android.mail.browse.ConversationItemViewModel;
import com.android.mail.browse.ConversationListFooterView;
import com.android.mail.providers.Account;
import com.android.mail.providers.AccountObserver;
import com.android.mail.providers.Conversation;
import com.android.mail.providers.Folder;
import com.android.mail.providers.Settings;
import com.android.mail.providers.UIProvider;
import com.android.mail.providers.UIProvider.AccountCapabilities;
import com.android.mail.providers.UIProvider.FolderCapabilities;
import com.android.mail.providers.UIProvider.FolderType;
import com.android.mail.providers.UIProvider.Swipe;
import com.android.mail.ui.SwipeableListView.ListItemSwipedListener;
import com.android.mail.ui.SwipeableListView.ListItemsRemovedListener;
import com.android.mail.ui.ViewMode.ModeChangeListener;
import com.android.mail.utils.LogTag;
import com.android.mail.utils.LogUtils;
import com.android.mail.utils.Utils;

import java.util.Collection;

/**
 * The conversation list UI component.
 */
public final class ConversationListFragment extends ListFragment implements
        OnItemLongClickListener, ModeChangeListener, ListItemSwipedListener {
    /** Key used to pass data to {@link ConversationListFragment}. */
    private static final String CONVERSATION_LIST_KEY = "conversation-list";
    /** Key used to keep track of the scroll state of the list. */
    private static final String LIST_STATE_KEY = "list-state";

    private static final String LOG_TAG = LogTag.getLogTag();

    // True if we are on a tablet device
    private static boolean mTabletDevice;

    /**
     * Frequency of update of timestamps. Initialized in
     * {@link #onCreate(Bundle)} and final afterwards.
     */
    private static int TIMESTAMP_UPDATE_INTERVAL = 0;

    private ControllableActivity mActivity;

    // Control state.
    private ConversationListCallbacks mCallbacks;

    private final Handler mHandler = new Handler();

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
    private View mEmptyView;
    private ErrorListener mErrorListener;
    private DataSetObserver mFolderObserver;
    private DataSetObserver mConversationListStatusObserver;

    private ConversationSelectionSet mSelectedSet;
    private final AccountObserver mAccountObserver = new AccountObserver() {
        @Override
        public void onChanged(Account newAccount) {
            mAccount = newAccount;
            setSwipeAction();
        }
    };
    private ConversationUpdater mUpdater;

    /**
     * Constructor needs to be public to handle orientation changes and activity
     * lifecycle events.
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

    @Override
    public void onResume() {
        Utils.dumpLayoutRequests("CLF.onResume()", getView());

        super.onResume();
        // Hacky workaround for http://b/6946182
        Utils.fixSubTreeLayoutIfOrphaned(getView(), "ConversationListFragment");
    }

    /**
     * Creates a new instance of {@link ConversationListFragment}, initialized
     * to display conversation list context.
     */
    public static ConversationListFragment newInstance(ConversationListContext viewContext) {
        ConversationListFragment fragment = new ConversationListFragment();
        Bundle args = new Bundle();
        args.putBundle(CONVERSATION_LIST_KEY, viewContext.toBundle());
        fragment.setArguments(args);
        return fragment;
    }

    /**
     * Show the header if the current conversation list is showing search
     * results.
     */
    void configureSearchResultHeader() {
        if (mActivity == null) {
            return;
        }
        // Only show the header if the context is for a search result
        final Resources res = getResources();
        final boolean showHeader = ConversationListContext.isSearchResult(mViewContext);
        // TODO(viki): This code contains intimate understanding of the view.
        // Much of this logic
        // needs to reside in a separate class that handles the text view in
        // isolation. Then,
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
     * Show the header if the current conversation list is showing search
     * results.
     */
    private void updateSearchResultHeader(int count) {
        if (mActivity == null) {
            return;
        }
        // Only show the header if the context is for a search result
        final Resources res = getResources();
        final boolean showHeader = ConversationListContext.isSearchResult(mViewContext);
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
        // TODO(mindyp): find some way to make the notification container more
        // re-usable.
        // TODO(viki): refactor according to comment in
        // configureSearchResultHandler()
        mSearchStatusView = mActivity.findViewById(R.id.search_status_view);
        mSearchStatusTextView = (TextView) mActivity.findViewById(R.id.search_status_text_view);
        mSearchResultCountTextView = (TextView) mActivity
                .findViewById(R.id.search_result_count_view);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        // Strictly speaking, we get back an android.app.Activity from
        // getActivity. However, the
        // only activity creating a ConversationListContext is a MailActivity
        // which is of type
        // ControllableActivity, so this cast should be safe. If this cast
        // fails, some other
        // activity is creating ConversationListFragments. This activity must be
        // of type
        // ControllableActivity.
        final Activity activity = getActivity();
        if (!(activity instanceof ControllableActivity)) {
            LogUtils.e(LOG_TAG, "ConversationListFragment expects only a ControllableActivity to"
                    + "create it. Cannot proceed.");
        }
        mActivity = (ControllableActivity) activity;
        // Since we now have a controllable activity, load the account from it,
        // and register for
        // future account changes.
        mAccount = mAccountObserver.initialize(mActivity.getAccountController());
        mCallbacks = mActivity.getListHandler();
        mErrorListener = mActivity.getErrorListener();
        // Start off with the current state of the folder being viewed.
        mFooterView = (ConversationListFooterView) LayoutInflater.from(
                mActivity.getActivityContext()).inflate(R.layout.conversation_list_footer_view,
                null);
        mFooterView.setClickListener(mActivity);
        mListAdapter = new AnimatedAdapter(mActivity.getApplicationContext(), -1,
                getConversationListCursor(), mActivity.getSelectedSet(), mActivity, mListView);
        mListAdapter.addFooter(mFooterView);
        mListView.setAdapter(mListAdapter);
        mSelectedSet = mActivity.getSelectedSet();
        mListView.setSelectionSet(mSelectedSet);
        mListAdapter.hideFooter();
        mFolderObserver = new FolderObserver();
        mActivity.getFolderController().registerFolderObserver(mFolderObserver);
        mConversationListStatusObserver = new ConversationListStatusObserver();
        mUpdater = mActivity.getConversationUpdater();
        mUpdater.registerConversationListObserver(mConversationListStatusObserver);
        mTabletDevice = Utils.useTabletUI(mActivity.getApplicationContext());
        initializeUiForFirstDisplay();
        configureSearchResultHeader();
        // The onViewModeChanged callback doesn't get called when the mode
        // object is created, so
        // force setting the mode manually this time around.
        onViewModeChanged(mActivity.getViewMode().getMode());
        mActivity.getViewMode().addListener(this);

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
        mUpdateTimestampsRunnable = new Runnable() {
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

        setRetainInstance(false);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        View rootView = inflater.inflate(R.layout.conversation_list, null);
        mEmptyView = rootView.findViewById(R.id.empty_view);
        mListView = (SwipeableListView) rootView.findViewById(android.R.id.list);
        mListView.setHeaderDividersEnabled(false);
        mListView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        mListView.setOnItemLongClickListener(this);
        mListView.enableSwipe(mAccount.supportsCapability(AccountCapabilities.UNDO));
        mListView.setSwipedListener(this);

        // Restore the list state
        if (savedState != null && savedState.containsKey(LIST_STATE_KEY)) {
            mListView.onRestoreInstanceState(savedState.getParcelable(LIST_STATE_KEY));
            // TODO: find a better way to unset the selected item when restoring
            mListView.clearChoices();
        }

        final ConversationCursor conversationListCursor = getConversationListCursor();
        // Belt and suspenders here; make sure we do any necessary sync of the
        // ConversationCursor
        if (conversationListCursor != null && conversationListCursor.isRefreshReady()) {
            conversationListCursor.sync();
        }
        Utils.dumpLayoutRequests("CLF.onCreateView()", container);
        return rootView;
    }

    @Override
    public void onDestroy() {
        Utils.dumpLayoutRequests("CLF.onDestroy()", getView());
        super.onDestroy();
    }

    @Override
    public void onDestroyView() {
        Utils.dumpLayoutRequests("CLF.onDestroyView()", getView());

        // Clear the list's adapter
        mListAdapter.destroy();
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
            mUpdater.unregisterConversationListObserver(mConversationListStatusObserver);
            mConversationListStatusObserver = null;
        }
        mAccountObserver.unregisterAndDestroy();
        super.onDestroyView();
    }

    /**
     * There are three binary variables, which determine what we do with a
     * message. checkbEnabled: Whether check boxes are enabled or not (forced
     * true on tablet) cabModeOn: Whether CAB mode is currently on or not.
     * pressType: long or short tap (There is a third possibility: phone or
     * tablet, but they have <em>identical</em> behavior) The matrix of
     * possibilities is:
     * <p>
     * Long tap: Always toggle selection of conversation. If CAB mode is not
     * started, then start it.
     * <pre>
     *              | Checkboxes | No Checkboxes
     *    ----------+------------+---------------
     *    CAB mode  |   Select   |     Select
     *    List mode |   Select   |     Select
     *
     * </pre>
     *
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
        ((ConversationItemView) view).toggleCheckMarkOrBeginDrag();
        return true;
    }

    /**
     * See the comment for
     * {@link #onItemLongClick(AdapterView, View, int, long)}.
     * <p>
     * Short tap behavior:
     *
     * <pre>
     *              | Checkboxes | No Checkboxes
     *    ----------+------------+---------------
     *    CAB mode  |    Peek    |     Select
     *    List mode |    Peek    |      Peek
     * </pre>
     *
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
            ((ConversationItemView) view).toggleCheckMarkOrBeginDrag();
        } else {
            viewConversation(position);
        }
        // When a new list item is clicked, commit any existing leave behind
        // items.
        // Wait until we have opened the desired conversation to cause any
        // position changes.
        commitDestructiveActions(Utils.useTabletUI(mActivity.getActivityContext()));
    }

    @Override
    public void onPause() {
        Utils.dumpLayoutRequests("CLF.onPause()", getView());
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
        Utils.dumpLayoutRequests("CLF.onStart()", getView());
        super.onStart();
        mHandler.postDelayed(mUpdateTimestampsRunnable, TIMESTAMP_UPDATE_INTERVAL);
    }

    @Override
    public void onStop() {
        Utils.dumpLayoutRequests("CLF.onStop()", getView());
        super.onStop();
        mHandler.removeCallbacks(mUpdateTimestampsRunnable);
    }

    @Override
    public void onViewModeChanged(int newMode) {
        // Change the divider based on view mode.
        if (mTabletDevice) {
            if (newMode == ViewMode.CONVERSATION) {
                mListView.setBackgroundResource(R.drawable.panel_conversation_leftstroke);
            } else if (newMode == ViewMode.CONVERSATION_LIST
                    || newMode == ViewMode.SEARCH_RESULTS_LIST) {
                // There are no selected conversations when in conversation
                // list mode.
                mListView.clearChoices();
                mListView.setBackgroundDrawable(null);
            }
        } else {
            mListView.setBackgroundDrawable(null);
        }
        if (mFooterView != null) {
            mFooterView.onViewModeChanged(newMode);
        }
    }

    /**
     * Handles a request to show a new conversation list, either from a search
     * query or for viewing a folder. This will initiate a data load, and hence
     * must be called on the UI thread.
     */
    private void showList() {
        mListView.setEmptyView(null);
        onFolderUpdated(mActivity.getFolderController().getFolder());
        onConversationListStatusUpdated();
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
            mCallbacks.onConversationSelected(conv, false /* inLoaderCallbacks */);
        }
    }

    /**
     * Sets the selected position (the highlighted conversation) to the position
     * provided here.
     * @param position
     */
    protected final void setSelected(int position) {
        mListView.smoothScrollToPosition(position);
        mListView.clearChoices();
        mListView.setItemChecked(position, true);
    }

    private ConversationCursor getConversationListCursor() {
        return mCallbacks != null ? mCallbacks.getConversationListCursor() : null;
    }

    /**
     * Request a refresh of the list. No sync is carried out and none is
     * promised.
     */
    public void requestListRefresh() {
        mListAdapter.notifyDataSetChanged();
    }

    /**
     * Change the UI to delete the conversations provided and then call the
     * {@link DestructiveAction} provided here <b>after</b> the UI has been
     * updated.
     * @param conversations
     * @param action
     */
    public void requestDelete(final Collection<Conversation> conversations,
            final DestructiveAction action) {
        for (Conversation conv : conversations) {
            conv.localDeleteOnUpdate = true;
        }
        // Delete the local delete items (all for now) and when done,
        // update...
        mListAdapter.delete(conversations, new ListItemsRemovedListener() {
            @Override
            public void onListItemsRemoved() {
                action.performAction();
            }
        });
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
        // Blow away conversation items cache.
        ConversationItemViewModel.onFolderUpdated(mFolder);
    }

    public void onConversationListStatusUpdated() {
        final ConversationCursor cursor = getConversationListCursor();
        final boolean showFooter = cursor != null && mFooterView.updateStatus(cursor);
        Bundle extras = cursor != null ? cursor.getExtras() : Bundle.EMPTY;
        int error = extras.containsKey(UIProvider.CursorExtraKeys.EXTRA_ERROR) ?
                extras.getInt(UIProvider.CursorExtraKeys.EXTRA_ERROR)
                : UIProvider.LastSyncResult.SUCCESS;
        int status = extras.getInt(UIProvider.CursorExtraKeys.EXTRA_STATUS);
        if (error == UIProvider.LastSyncResult.SUCCESS
                && (status == UIProvider.CursorStatus.LOADED
                    || status == UIProvider.CursorStatus.COMPLETE)) {
            updateSearchResultHeader(mFolder != null ? mFolder.totalCount : 0);
            if (mFolder == null || mFolder.totalCount == 0) {
                mListView.setEmptyView(mEmptyView);
            }
        }
        mListAdapter.setFooterVisibility(showFooter);
    }

    private void setSwipeAction() {
        int swipeSetting = Settings.getSwipeSetting(mAccount.settings);
        if (swipeSetting == Swipe.DISABLED
                || !mAccount.supportsCapability(AccountCapabilities.UNDO)
                || (mFolder != null && mFolder.isTrash())) {
            mListView.enableSwipe(false);
        } else {
            int action;
            if (ConversationListContext.isSearchResult(mViewContext)
                    || (mFolder != null && mFolder.type == FolderType.SPAM)) {
                action = R.id.delete;
            } else if (mFolder == null) {
                action = R.id.remove_folder;
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
                                action = R.id.remove_folder;
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

    public void onCursorUpdated() {
        if (mListAdapter != null) {
            mListAdapter.swapCursor(mCallbacks.getConversationListCursor());
        }
    }

    public void commitDestructiveActions(boolean animate) {
        if (mListView != null) {
            mListView.commitDestructiveActions(animate);

        }
    }

    @Override
    public void onListItemSwiped(Collection<Conversation> conversations) {
        mUpdater.showNextConversation(conversations);
    }

}
