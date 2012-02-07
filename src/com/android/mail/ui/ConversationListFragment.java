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

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.app.Activity;
import android.app.ListFragment;
import android.content.ContentResolver;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.MarginLayoutParams;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ListView;
import android.widget.TextView;

import com.android.mail.R;
import com.android.mail.ConversationListContext;
import com.android.mail.browse.ConversationCursor;
import com.android.mail.browse.ConversationItemView.StarHandler;
import com.android.mail.providers.Account;
import com.android.mail.providers.AccountCacheProvider;
import com.android.mail.providers.UIProvider;
import com.android.mail.ui.ViewMode.ModeChangeListener;
import com.android.mail.utils.LogUtils;
import com.android.mail.utils.Utils;

/**
 * The conversation list UI component.
 */
public final class ConversationListFragment extends ListFragment
        implements ConversationSetObserver,
        OnItemLongClickListener,
        ModeChangeListener, UndoBarView.OnUndoCancelListener {
    // Keys used to pass data to {@link ConversationListFragment}.
    private static final String CONVERSATION_LIST_KEY = "conversation-list";

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

    private static final AnimatorListener UNDO_HIDE_ANIMATOR_LISTENER = new AnimatorListener() {
        @Override
        public void onAnimationCancel(Animator animation) {
            // Do nothing.
        }
        @Override
        public void onAnimationEnd(Animator animation) {
            // Do nothing.
        }
        @Override
        public void onAnimationRepeat(Animator animation) {
            // Do nothing.
        }
        @Override
        public void onAnimationStart(Animator animation) {
            // Do nothing.
        }
    };

    private ControllableActivity mActivity;
    private boolean mAnimateChanges;

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
    private ListView mListView;
    private int mPosition = ListView.INVALID_POSITION;

    private TextView mSearchResultCountTextView;
    private TextView mSearchStatusTextView;

    private View mSearchStatusView;

    private int mSelectedCursorPosition = mPosition;

    /**
     * Current Account being viewed
     */
    private String mAccount;
    /**
     * Current label/folder being viewed.
     */
    private String mFolder;
    /**
     * Object to deal with starring of messages.
     */
    private StarHandler mStarHandler;

    private UndoBarView mUndoView;

    /**
     * A simple method to update the timestamps of conversations periodically.
     */
    private Runnable mUpdateTimestampsRunnable = null;

    private ConversationListContext mViewContext;
    private ContentResolver mResolver;

    private AnimatedAdapter mListAdapter;

    private ConversationSelectionSet mBatchConversations = new ConversationSelectionSet();
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
     * Hides the control for an {@link UndoOperation}
     * @param animate if true, hiding the undo view will be animated.
     */
    private void hideUndoView(boolean animate) {
        if (mUndoView.isShown()) {
            mUndoView.hide(animate);
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
        mUndoView.setUndoHideListener(UNDO_HIDE_ANIMATOR_LISTENER);
    }

    private boolean isSearchResult() {
        return mViewContext != null && mViewContext.isSearchResult();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        LogUtils.v(LOG_TAG, "onActivityCreated in ConversationListFragment(this=%s)",
                this);
        super.onActivityCreated(savedInstanceState);
        // Strictly speaking, we get back an android.app.Activity from getActivity. However, the
        // only activity creating a ConversationListContext is a MailActivity which is of type
        // ControllableActivity, so this cast should be safe. If this cast fails, some other
        // activity is creating ConversationListFragments. This activity must be of type
        // ControllableActivity.
        final Activity activity = getActivity();
        if (! (activity instanceof ControllableActivity)){
            LogUtils.e(LOG_TAG, "ConversationListFragment expects only a ControllableActivity to" +
                    "create it. Cannot proceed.");
        }
        mActivity = (ControllableActivity) activity;
        mCallbacks = mActivity.getListHandler();
        mStarHandler = mActivity.getStarHandler();
        mActivity.getBatchConversations().addObserver(this);
        mActivity.setViewModeListener(this);
        mActivity.attachConversationList(this);
        mTabletDevice = Utils.useTabletUI(mActivity.getApplicationContext());
        mResolver = mActivity.getContentResolver();
        initializeUiForFirstDisplay();

        // The onViewModeChanged callback doesn't get called when the mode object is created, so
        // force setting the mode manually this time around.
        onViewModeChanged(mActivity.getViewMode());

        if (mActivity.isFinishing()) {
            // Activity is finishing, just bail.
            return;
        }

        // Show list and start loading list.
        showList();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        LogUtils.v(LOG_TAG, "onCreate in ConversationListFragment(this=%s)", this);
        super.onCreate(savedInstanceState);

        // Initialize fragment constants from resources
        Resources res = getResources();
        TIMESTAMP_UPDATE_INTERVAL = res.getInteger(R.integer.timestamp_update_interval);
        mUpdateTimestampsRunnable = new Runnable(){
            @Override
            public void run() {
                mListView.invalidateViews();
                mHandler.postDelayed(mUpdateTimestampsRunnable, TIMESTAMP_UPDATE_INTERVAL);
            }
        };

        // Get the context from the arguments
        Bundle args = getArguments();
        mViewContext = ConversationListContext.forBundle(args.getBundle(CONVERSATION_LIST_KEY));

        if (savedInstanceState != null) {
            mListSavedState = savedInstanceState.getParcelable(LIST_STATE_KEY);
        }
        setRetainInstance(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater,
            ViewGroup container, Bundle savedInstanceState) {
        LogUtils.v(LOG_TAG, "onCreateView in ConversationListFragment(this=%s)", this);
        View rootView = inflater.inflate(R.layout.conversation_list, null);

        mEmptyView = rootView.findViewById(R.id.empty_view);

        mListView = (ListView) rootView.findViewById(android.R.id.list);
        mListView.setHeaderDividersEnabled(false);
        mListView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        mListView.setOnItemLongClickListener(this);

        // Note - we manually save/restore the listview state.
        mListView.setSaveEnabled(false);

        return rootView;
    }

    @Override
    public void onDestroyView() {
        // Clear the selected position and save list state manually so we can restore it after data
        // has finished loading.
        if (mPosition != ListView.INVALID_POSITION) {
            mListView.setItemChecked(mPosition, false);
        }
        mListSavedState = mListView.onSaveInstanceState();

        // Clear the adapter.
        mListView.setAdapter(null);

        mActivity.unsetViewModeListener(this);
        mActivity.attachConversationList(null);
        mActivity.getBatchConversations().removeObserver(this);

        if (!mActivity.isChangingConfigurations()) {
            mActivity.getLoaderManager().destroyLoader(mViewContext.hashCode());
        }

        super.onDestroyView();
    }

    @Override
    public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
        // TODO(viki): Add some functionality here.
        return true;
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        viewConversation(position);
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
    public void onSetChanged(ConversationSelectionSet set) {
        // No-op, since all set operations will cause a list refresh anyways, except for batch
        // operations like "select all" or "deselect all" which are done through menus external
        // to the list.
    }

    @Override
    public void onSetEmpty() {
        mIsCabMode = false;
        refreshList();
    }

    @Override
    public void onSetPopulated(ConversationSelectionSet set) {
        mIsCabMode = true;
        refreshList();
    }

    @Override
    public void onStart() {
        LogUtils.v(LOG_TAG, "onStart in ConversationListFragment(this=%s)", this);
        super.onStart();

        mHandler.postDelayed(mUpdateTimestampsRunnable, TIMESTAMP_UPDATE_INTERVAL);
    }

    @Override
    public void onStop() {
        LogUtils.v(LOG_TAG, "onStop in ConversationListFragment(this=%s)", this);
        super.onStop();

        mHandler.removeCallbacks(mUpdateTimestampsRunnable);
    }

    public void onTouchEvent(MotionEvent event) {
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
            } else {
                mListView.setBackgroundDrawable(null);
            }
            mAnimateChanges = true;
        } else {
            mListView.setBackgroundDrawable(null);
            mAnimateChanges = newMode == ViewMode.CONVERSATION_LIST;
        }
    }

    /**
     * Visually refresh the conversation list. This does not start a sync.
     */
    private void refreshList() {
        // Do nothing for now.
    }

    /**
     * Handles a request to show a new conversation list, either from a search query or for viewing
     * a label. This will initiate a data load, and hence must be called on the UI thread.
     */
    private void showList() {
        mListView.setEmptyView(null);

        // Get an account and a folder list
        Uri foldersUri = Uri.parse(mViewContext.mAccount.folderListUri);
        // TODO(viki) fill with real position
        final int position = 0;
        Account mSelectedAccount = mViewContext.mAccount;

        Uri conversationListUri = null;
        if (foldersUri != null) {
            // TODO(viki): Look up the folder from the ConversationListContext rather than the first
            // folder here.
            Cursor cursor = mResolver.query(AccountCacheProvider.getAccountsUri(),
                    UIProvider.ACCOUNTS_PROJECTION, null, null, null);
            if (cursor != null) {
                try {
                    final int uriCol = cursor.getColumnIndex(
                            UIProvider.FolderColumns.CONVERSATION_LIST_URI);
                    cursor.moveToFirst();
                    conversationListUri = Uri.parse(cursor.getString(uriCol));
                } finally {
                    cursor.close();
                }
            }
        }
        // Create the cursor for the list using the update cache
        // Make this asynchronous
        mConversationListCursor = ConversationCursor.create((Activity) mActivity, //f unsafe
                UIProvider.ConversationColumns.URI, conversationListUri,
                UIProvider.CONVERSATION_PROJECTION, null, null, null);
        mListAdapter = new AnimatedAdapter(mActivity.getApplicationContext(), position,
                mConversationListCursor, mBatchConversations, mSelectedAccount);
        mListView.setAdapter(mListAdapter);
        configureSearchResultHeader();
    }

    public void updateSelectedPosition() {
        if (mPosition != mSelectedCursorPosition) {
            // Clear temporary selection position. This would occur
            // if the position was after items that were being destroyed.
            mListView.setItemChecked(mPosition, false);
            mPosition = mSelectedCursorPosition;
            mListView.setItemChecked(mPosition, true);
        }
    }

    /**
     * View the message at the given position.
     * @param position
     */
    private void viewConversation(int position){
        mCallbacks.onConversationSelected(position);
    }
}
