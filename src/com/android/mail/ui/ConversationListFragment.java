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

import android.animation.Animator.AnimatorListener;
import android.app.Activity;
import android.app.ListFragment;
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

import com.android.mail.R;
import com.android.mail.ConversationListContext;
import com.android.mail.browse.ConversationItemView.StarHandler;
import com.android.mail.ui.ViewMode.ModeChangeListener;
import com.android.mail.utils.LogUtils;

/**
 * The conversation list UI component.
 */
public final class ConversationListFragment extends ListFragment
        implements ConversationSetObserver,
        OnItemLongClickListener,
        ModeChangeListener {
    // Keys used to pass data to {@link ConversationListFragment}.
    private static final String CONVERSATION_LIST_KEY = "conversation-list";

    // Key used to keep track of the scroll state of the list.
    private static final String LIST_STATE_KEY = "list-state";

    private static final String LOG_TAG = new LogUtils().getLogTag();

    /**
     * Frequency of update of timestamps. Initialized in {@link #onCreate(Bundle)} and final
     * afterwards.
     */
    private static int TIMESTAMP_UPDATE_INTERVAL = 0;

    private ControllableActivity mActivity;

    private boolean mAnimateChanges;

    // Control state.
    private ConversationListCallbacks mCallbacks;
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

    private AnimatorListener mUndoHideListener;

    /**
     * A simple method to update the timestamps of conversations periodically.
     */
    private Runnable mUpdateTimestampsRunnable = null;

    private ConversationListContext mViewContext;

    // Which mode is the underlying controller in?
    private ViewMode mViewMode;

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
     * Initializes all internal state for a rendering.
     */
    private void bindActivityInfo() {
        final Activity activity = getActivity();
        mCallbacks = (ConversationListCallbacks) activity;
        mViewMode = mActivity.getViewMode();
        StarHandler starHandler = (StarHandler) activity;
        if (mViewMode != null) {
            mViewMode.addListener(this);
        }

        mActivity.getBatchConversations().addObserver(this);

        // TODO(mindyp): find some way to make the notification container more re-usable.
        // TODO(viki): refactor according to comment in configureSearchResultHandler()
        mSearchStatusView = activity.findViewById(R.id.search_status_view);
        mSearchStatusTextView = (TextView) activity.findViewById(R.id.search_status_text_view);
        mSearchResultCountTextView = (TextView) activity
                .findViewById(R.id.search_result_count_view);
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

    private boolean isSearchResult() {
        return mViewContext != null && mViewContext.isSearchResult();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        LogUtils.v(LOG_TAG, "onActivityCreated in ConversationListFragment(this=%s)",
                this);
        super.onActivityCreated(savedInstanceState);
        mActivity = (ControllableActivity) getActivity();
        mActivity.attachConversationList(this);
        bindActivityInfo();

        // Set the current view mode.
        onViewModeChanged(mViewMode);

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

        mViewMode.removeListener(this);
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
    public void onViewModeChanged(ViewMode mode) {
        // Change the divider based on view mode.
        Resources res = getView().getResources();
        if (mode.isTwoPane()) {
            if (mode.isConversationMode()) {
                mListView.setBackgroundResource(R.drawable.panel_conversation_leftstroke);
            } else {
                mListView.setBackgroundDrawable(null);
            }
            mAnimateChanges = true;
        } else {
            mListView.setBackgroundDrawable(null);
            mAnimateChanges = mode.isConversationListMode();
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
