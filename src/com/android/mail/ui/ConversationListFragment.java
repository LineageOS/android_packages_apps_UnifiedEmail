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
import android.app.LoaderManager;
import android.content.Context;
import android.content.Loader;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.view.LayoutInflater;
import android.view.MotionEvent;
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
import com.android.mail.browse.ConversationCursor.ConversationListener;
import com.android.mail.browse.ConversationItemView;
import com.android.mail.browse.ConversationListFooterView;
import com.android.mail.browse.SelectedConversationsActionMenu;
import com.android.mail.providers.Account;
import com.android.mail.providers.Conversation;
import com.android.mail.providers.Folder;
import com.android.mail.providers.UIProvider;
import com.android.mail.ui.SwipeableListView.SwipeCompleteListener;
import com.android.mail.ui.ViewMode.ModeChangeListener;
import com.android.mail.utils.LogUtils;
import com.android.mail.utils.Utils;
import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.Collection;

/**
 * The conversation list UI component.
 */
public final class ConversationListFragment extends ListFragment implements
        OnItemLongClickListener, ModeChangeListener, UndoBarView.OnUndoCancelListener,
        ConversationSetObserver, ActionCompleteListener, ConversationListener,
        LoaderManager.LoaderCallbacks<ConversationCursor>, UndoBarView.UndoListener,
        SwipeCompleteListener {
    // Keys used to pass data to {@link ConversationListFragment}.
    private static final String CONVERSATION_LIST_KEY = "conversation-list";
    // Batch conversations stored in the Bundle using this key.
    private static final String SAVED_CONVERSATIONS = "saved-conversations";
    // Key used to keep track of the scroll state of the list.
    private static final String LIST_STATE_KEY = "list-state";
    private static final String LOG_TAG = new LogUtils().getLogTag();

    // True if we are on a tablet device
    private static boolean mTabletDevice;

    /**
     * Frequency of update of timestamps. Initialized in {@link #onCreate(Bundle)} and final
     * afterwards.
     */
    private static int TIMESTAMP_UPDATE_INTERVAL = 0;

    private static final int CONVERSATION_LOADER_ID = 0;

    private ControllableActivity mActivity;

    // Control state.
    private ConversationListCallbacks mCallbacks;
    private ConversationCursor mConversationListCursor;
    private View mEmptyView;

    private final Handler mHandler = new Handler();
    // True if the view is in CAB (Contextual Action Bar: some conversations are selected) mode
    private boolean mIsCabMode;
    // List save state.
    private Parcelable mListSavedState;

    // The internal view objects.
    private SwipeableListView mListView;

    private TextView mSearchResultCountTextView;
    private TextView mSearchStatusTextView;

    private View mSearchStatusView;

    /** The currently selected conversation */
    private int mCurrentPosition = -1;

    /**
     * Current Account being viewed
     */
    private Account mAccount;
    /**
     * Current folder being viewed.
     */
    private Folder mFolder;

    private UndoBarView mUndoView;

    /**
     * A simple method to update the timestamps of conversations periodically.
     */
    private Runnable mUpdateTimestampsRunnable = null;

    private ConversationListContext mViewContext;

    private AnimatedAdapter mListAdapter;

    /**
     * Selected conversations, if any.
     */
    private ConversationSelectionSet mSelectedSet = new ConversationSelectionSet();
    private SelectedConversationsActionMenu mSelectedConversationsActionMenu;
    private ConversationListFooterView mFooterView;
    private int mSwipeAction;

    /**
     * Constructor needs to be public to handle orientation changes and activity lifecycle events.
     */
    public ConversationListFragment() {
        super();
        // Allow the fragment to observe changes to its own selection set. No other object is
        // aware of the selected set.
        mSelectedSet.addObserver(this);
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
    private void configureSearchResultHeader() {
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
        mUndoView = (UndoBarView) mActivity.findViewById(R.id.undo_view);
        mUndoView.setOnCancelListener(this);
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

        mListAdapter = new AnimatedAdapter(mActivity.getApplicationContext(), -1,
                mConversationListCursor, mSelectedSet, mAccount, mActivity.getViewMode());
        mFooterView = (ConversationListFooterView) LayoutInflater.from(
                mActivity.getActivityContext()).inflate(R.layout.conversation_list_footer_view,
                null);
        mListAdapter.addFooter(mFooterView);
        mListView.setAdapter(mListAdapter);
        mListView.setSwipeCompleteListener(this);
        // Don't need to add ourselves to our own set observer.
        // mActivity.getBatchConversations().addObserver(this);
        mActivity.setViewModeListener(this);
        mActivity.attachConversationList(this);
        mTabletDevice = Utils.useTabletUI(mActivity.getApplicationContext());
        initializeUiForFirstDisplay();

        // The onViewModeChanged callback doesn't get called when the mode object is created, so
        // force setting the mode manually this time around.
        onViewModeChanged(mActivity.getViewMode().getMode());

        if (mActivity.isFinishing()) {
            // Activity is finishing, just bail.
            return;
        }

        // Show list and start loading list.
        showList();
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
        mSwipeAction = mAccount.supportsCapability(UIProvider.AccountCapabilities.ARCHIVE) ?
                R.id.archive : R.id.delete;
        if (savedState != null) {
            mListSavedState = savedState.getParcelable(LIST_STATE_KEY);
        }
        setRetainInstance(true);
    }

    /**
     * Restore the state of selected conversations. This needs to be done after the correct mode
     * is set and the action bar is fully initialized. If not, several key pieces of state
     * information will be missing, and the split views may not be initialized correctly.
     * @param savedState
     */
    private void restoreSelectedConversations(Bundle savedState) {
        if (savedState == null) {
            onSetEmpty();
            return;
        }
        mSelectedSet = savedState.getParcelable(SAVED_CONVERSATIONS);
        if (mSelectedSet.isEmpty()) {
            onSetEmpty();
            return;
        }
        // We have some selected conversations. Perform all the actions needed.
        onSetPopulated(mSelectedSet);
    }

    @Override
    public View onCreateView(LayoutInflater inflater,
            ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.conversation_list, null);
        mEmptyView = rootView.findViewById(R.id.empty_view);
        mListView = (SwipeableListView) rootView.findViewById(android.R.id.list);
        mListView.setHeaderDividersEnabled(false);
        mListView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        mListView.setOnItemLongClickListener(this);

        // Note - we manually save/restore the listview state.
        mListView.setSaveEnabled(false);

        // Belt and suspenders here; make sure we do any necessary sync of the ConversationCursor
        if (mConversationListCursor != null && mConversationListCursor.isRefreshReady()) {
            mConversationListCursor.sync();
        }
        return rootView;
    }

    @Override
    public void onDestroyView() {
        mListSavedState = mListView.onSaveInstanceState();

        // Clear the adapter.
        mListView.setAdapter(null);

        mActivity.unsetViewModeListener(this);

        // Since we want to keep the conversation list around to request deletes on later,
        // don't null out the conversation list fragment.
        // mActivity.attachConversationList(null);

        if (!mActivity.isChangingConfigurations()) {
            mActivity.getLoaderManager().destroyLoader(mViewContext.hashCode());
        }

        super.onDestroyView();
    }

    @Override
    public void onDetach() {
        mActivity.attachConversationList(null);
        super.onDetach();
    }

    @Override
    public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
        // Long click is for adding conversations to a selection. Add conversation here.

        // TODO(viki): True for now. We need to look at settings and perform a different action if
        // check boxes are not visible.
        final boolean isCheckBoxVisible = true;
        if (isCheckBoxVisible && mTabletDevice && mIsCabMode) {
            viewConversation(position);
            return true;
        }
        assert (view instanceof ConversationItemView);
        ConversationItemView item = (ConversationItemView) view;
        // Handle drag mode if allowed, otherwise toggle selection.
        //        if (!mViewMode.getMode() == ViewMode.CONVERSATION_LIST || !mTabletDevice) {
        // Add this conversation to the selected set.
        final Conversation conversation = item.getConversation();
        // mSelectedSet.toggle(conversation);
        item.toggleCheckMark();
        // Verify that the checkbox is in sync with the selection set.
        //assert (item.isSelected() == mSelectedSet.contains(conversation));
        return true;
    }

    @Override
    public void onListItemClick(ListView l, View view, int position, long id) {
        viewConversation(position);
        mCurrentPosition = position;
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

    public void onTouchEvent(MotionEvent event) {
        if (!mUndoView.isEventInUndo(event)) {
            mUndoView.doHide();
        }
    }

    @Override
    public void onUndoCancel() {
        mUndoView.hide(false);
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
        onFolderUpdated(mViewContext.folder);
        getLoaderManager().initLoader(CONVERSATION_LOADER_ID, Bundle.EMPTY, this);
    }

    /**
     * View the message at the given position.
     * @param position
     */
    protected void viewConversation(int position) {
        mConversationListCursor.moveToPosition(position);
        Conversation conv = new Conversation(mConversationListCursor);
        conv.position = position;
        mCallbacks.onConversationSelected(conv);
        getListView().setItemChecked(position, true);
    }

    @Override
    public void onSetEmpty() {
        mSelectedConversationsActionMenu = null;
    }

    @Override
    public void onSetPopulated(ConversationSelectionSet set) {
        mSelectedConversationsActionMenu = new SelectedConversationsActionMenu(mActivity,
                mSelectedSet, mListAdapter, this, this, mAccount, mFolder);
        mSelectedConversationsActionMenu.activate();
    }

    @Override
    public void onActionComplete() {
        if (mConversationListCursor.isRefreshReady()) {
            finishRefresh();
        }
    }

    @Override
    public void onUndoAvailable(UndoOperation op) {
        if (op != null && mAccount.supportsCapability(UIProvider.AccountCapabilities.UNDO)) {
            if (mUndoView == null) {
                mUndoView = (UndoBarView) mActivity.findViewById(R.id.undo_view);
            }
            mUndoView.setOnCancelListener(mListAdapter);
            mUndoView.show(true, mActivity.getActivityContext(), op, mAccount, mListAdapter);
        }
    }

    @Override
    public void onSetChanged(ConversationSelectionSet set) {
        // Do nothing. We don't care about changes to the set.
    }

    /**
     * Called when there is new data at the underlying provider
     * refresh() here causes the new data to be retrieved asynchronously
     * NOTE: The UI needn't take any action immediately (i.e. it might wait until a more
     * convenient time to get the update from the provider)
     */
    @Override
    public void onRefreshRequired() {
        // Refresh the query in the background
        mConversationListCursor.refresh();
    }

    @Override
    public void onRefreshReady() {
        ArrayList<Integer> deletedRows = mConversationListCursor.getRefreshDeletions();
        // If we have any deletions from the server, animate them away
        if (!deletedRows.isEmpty()) {
            mListAdapter.delete(deletedRows, this);
        } else {
            finishRefresh();
        }
    }

    /**
     * Request a refresh of the list. No sync is carried out and none is promised.
     */
    public void requestListRefresh() {
        mListAdapter.notifyDataSetChanged();
    }

    public void requestDelete(final ActionCompleteListener listener) {
        if (isVisible()) {
            mListAdapter.delete(new ArrayList<Integer>(ImmutableList.of(mCurrentPosition)),
                    listener);
        } else {
            listener.onActionComplete();
        }
    }

    /**
     * Complete the cursor refresh process by syncing to the underlying cursor and redrawing
     */
    private void finishRefresh() {
        // Swap cursors
        mConversationListCursor.sync();
        // Redraw with new data
        mListAdapter.notifyDataSetChanged();
    }

    @Override
    public Loader<ConversationCursor> onCreateLoader(int id, Bundle args) {
        configureSearchResultHeader();
        mListAdapter.hideFooter();
        return new ConversationCursorLoader((Activity) mActivity, mAccount,
                    UIProvider.CONVERSATION_PROJECTION, mFolder.conversationListUri);
    }

    @Override
    public void onLoadFinished(Loader<ConversationCursor> loader, ConversationCursor data) {
        mConversationListCursor = data;
        if (mConversationListCursor.isRefreshReady()) {
            mConversationListCursor.sync();
        }
        mListAdapter.swapCursor(mConversationListCursor);
        onFolderUpdated(mFolder);
        mConversationListCursor.addListener(this);
        if (mActivity.shouldShowFirstConversation()) {
            if (mConversationListCursor.getCount() > 0) {
                mConversationListCursor.moveToPosition(0);
                getListView().setItemChecked(0, true);
                Conversation conv = new Conversation(mConversationListCursor);
                conv.position = 0;
                mCallbacks.onConversationSelected(conv);
            }
        }
    }

    @Override
    public void onLoaderReset(Loader<ConversationCursor> loader) {
        mListAdapter.swapCursor(null);

        if (mConversationListCursor != null) {
            mConversationListCursor.removeListener(this);
            mConversationListCursor = null;
        }
    }

    public void onFolderUpdated(Folder folder) {
        mFolder = folder;
        mListAdapter.setFolder(mFolder);
        mFooterView.updateStatus(mFolder);
        if (mFolder.isSyncInProgress()) {
            mListAdapter.showFooter();
        } else if (!mFolder.isSyncInProgress()
                && mFolder.lastSyncResult == UIProvider.LastSyncResult.SUCCESS) {
            // Check the status of the folder to see if we are done loading.
            updateSearchResultHeader(mFolder != null ? mFolder.totalCount : 0);
            if (mFolder.totalCount == 0) {
                mListView.setEmptyView(mEmptyView);
            } else {
                if (folder.loadMoreUri == null) {
                    mListAdapter.hideFooter();
                } else {
                    if ((mListAdapter.getCursor() != null) &&
                            (folder.totalCount > mListAdapter.getCount())) {
                        mListAdapter.showFooter();
                    } else {
                        mListAdapter.hideFooter();
                    }
                }
            }
        }
    }

    @Override
    public void onSwipeComplete(Collection<Conversation> conversations) {
        Context context = getActivity().getApplicationContext();
        switch (mSwipeAction) {
            case R.id.archive:
                Conversation.archive(context, conversations);
                break;
            case R.id.delete:
                Conversation.delete(context, conversations);
                break;
        }
        mListAdapter.notifyDataSetChanged();
        onUndoAvailable(new UndoOperation(conversations.size(), mSwipeAction));
    }

    public ConversationCursor getConversationListCursor() {
        return mConversationListCursor;
    }
}
