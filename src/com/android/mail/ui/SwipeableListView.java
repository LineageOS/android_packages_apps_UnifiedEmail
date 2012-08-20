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

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.net.Uri;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewParent;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.ListView;

import com.android.mail.R;
import com.android.mail.browse.ConversationCursor;
import com.android.mail.browse.ConversationItemView;
import com.android.mail.browse.SwipeableConversationItemView;
import com.android.mail.providers.Conversation;
import com.android.mail.providers.Folder;
import com.android.mail.ui.SwipeHelper.Callback;
import com.android.mail.utils.LogTag;
import com.android.mail.utils.LogUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;

public class SwipeableListView extends ListView implements Callback, OnScrollListener {
    private SwipeHelper mSwipeHelper;
    private boolean mEnableSwipe = false;

    public static final String LOG_TAG = LogTag.getLogTag();

    private ConversationSelectionSet mConvSelectionSet;
    private int mSwipeAction;
    private Folder mFolder;
    private boolean mAttachedToWindow;
    private boolean mLayoutCalled;

    public SwipeableListView(Context context) {
        this(context, null);
    }

    public SwipeableListView(Context context, AttributeSet attrs) {
        this(context, attrs, -1);
    }

    public SwipeableListView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        float densityScale = getResources().getDisplayMetrics().density;
        float pagingTouchSlop = ViewConfiguration.get(context).getScaledPagingTouchSlop();
        mSwipeHelper = new SwipeHelper(context, SwipeHelper.X, this, densityScale,
                pagingTouchSlop);
        setOnScrollListener(this);
    }
    @Override
    protected void onAttachedToWindow() {
        mAttachedToWindow = true;
        super.onAttachedToWindow();
    }

    @Override
    protected void onDetachedFromWindow() {
        mAttachedToWindow = false;
        super.onDetachedFromWindow();
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        mLayoutCalled = true;
        super.onLayout(changed, l, t, r, b);
    }

    public final boolean isWedged(){
        return mAttachedToWindow && !mLayoutCalled;
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        float densityScale = getResources().getDisplayMetrics().density;
        mSwipeHelper.setDensityScale(densityScale);
        float pagingTouchSlop = ViewConfiguration.get(getContext()).getScaledPagingTouchSlop();
        mSwipeHelper.setPagingTouchSlop(pagingTouchSlop);
    }

    /**
     * Enable swipe gestures.
     */
    public void enableSwipe(boolean enable) {
        mEnableSwipe = enable;
    }

    public boolean isSwipeEnabled() {
        return mEnableSwipe;
    }

    public void setSwipeAction(int action) {
        mSwipeAction = action;
    }

    public int getSwipeAction() {
        return mSwipeAction;
    }

    public void setSelectionSet(ConversationSelectionSet set) {
        mConvSelectionSet = set;
    }

    public void setCurrentFolder(Folder folder) {
        mFolder = folder;
    }

    @Override
    public ConversationSelectionSet getSelectionSet() {
        return mConvSelectionSet;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (mEnableSwipe) {
            return mSwipeHelper.onInterceptTouchEvent(ev) || super.onInterceptTouchEvent(ev);
        } else {
            return super.onInterceptTouchEvent(ev);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (mEnableSwipe) {
            return mSwipeHelper.onTouchEvent(ev) || super.onTouchEvent(ev);
        } else {
            return super.onTouchEvent(ev);
        }
    }

    @Override
    public View getChildAtPosition(MotionEvent ev) {
        // find the view under the pointer, accounting for GONE views
        final int count = getChildCount();
        int touchY = (int) ev.getY();
        int childIdx = 0;
        View slidingChild;
        for (; childIdx < count; childIdx++) {
            slidingChild = getChildAt(childIdx);
            if (slidingChild.getVisibility() == GONE) {
                continue;
            }
            if (touchY >= slidingChild.getTop() && touchY <= slidingChild.getBottom()) {
                if (slidingChild instanceof SwipeableConversationItemView) {
                    return ((SwipeableConversationItemView) slidingChild).getSwipeableItemView();
                }
                return slidingChild;
            }
        }
        return null;
    }

    @Override
    public boolean canChildBeDismissed(SwipeableItemView v) {
        return v.canChildBeDismissed();
    }

    @Override
    public void onChildDismissed(SwipeableItemView v) {
        v.dismiss();
    }

    // Call this whenever a new action is taken; this forces a commit of any
    // existing destructive actions.
    public void commitDestructiveActions() {
        final AnimatedAdapter adapter = getAnimatedAdapter();
        if (adapter != null) {
            adapter.commitLeaveBehindItems();
        }
    }

    public void dismissChild(final ConversationItemView target) {
        final Context context = getContext();
        final ToastBarOperation undoOp;

        undoOp = new ToastBarOperation(1, mSwipeAction, ToastBarOperation.UNDO);
        Conversation conv = target.getConversation();
        target.getConversation().position = findConversation(target, conv);
        final AnimatedAdapter adapter = getAnimatedAdapter();
        if (adapter == null) {
            return;
        }
        adapter.setupLeaveBehind(conv, undoOp, conv.position);
        ConversationCursor cc = (ConversationCursor) adapter.getCursor();
        switch (mSwipeAction) {
            case R.id.remove_folder:
                FolderOperation folderOp = new FolderOperation(mFolder, false);
                HashMap<Uri, Folder> targetFolders = Folder
                        .hashMapForFolders(conv.getRawFolders());
                targetFolders.remove(folderOp.mFolder.uri);
                conv.setRawFolders(Folder.getSerializedFolderString(targetFolders.values()));
                cc.mostlyDestructiveUpdate(context, Conversation.listOf(conv),
                        Conversation.UPDATE_FOLDER_COLUMN, conv.getRawFoldersString());
                break;
            case R.id.archive:
                cc.mostlyArchive(context, Conversation.listOf(conv));
                break;
            case R.id.delete:
                cc.mostlyDelete(context, Conversation.listOf(conv));
                break;
        }
        adapter.notifyDataSetChanged();
        if (mConvSelectionSet != null && !mConvSelectionSet.isEmpty()
                && mConvSelectionSet.contains(conv)) {
            mConvSelectionSet.toggle(null, conv);
        }
    }

    @Override
    public void onBeginDrag(View v) {
        // We do this so the underlying ScrollView knows that it won't get
        // the chance to intercept events anymore
        requestDisallowInterceptTouchEvent(true);
        SwipeableConversationItemView view = null;
        if (v instanceof ConversationItemView) {
            view = (SwipeableConversationItemView) v.getParent();
        }
        if (view != null) {
            view.addBackground(getContext());
            view.setBackgroundVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onDragCancelled(SwipeableItemView v) {
        SwipeableConversationItemView view = null;
        if (v instanceof ConversationItemView) {
            view = (SwipeableConversationItemView) ((View) v).getParent();
        }
        if (view != null) {
            view.removeBackground();
        }
    }

    /**
     * Archive items using the swipe away animation before shrinking them away.
     */
    public void destroyItems(final ArrayList<ConversationItemView> views,
            final DestructiveAction listener) {
        if (views == null || views.size() == 0) {
            return;
        }
        // Need to find the items in the LIST!
        final ArrayList<Conversation> conversations = new ArrayList<Conversation>();
        for (ConversationItemView view : views) {
            Conversation conv = view.getConversation();
            conv.position = findConversation(view, conv);
            conversations.add(conv);
        }
        AnimatedAdapter adapter = getAnimatedAdapter();
        if (adapter != null) {
            adapter.swipeDelete(conversations, listener);
        }
    }

    private int findConversation(ConversationItemView view, Conversation conv) {
        int position = conv.position;
        long convId = conv.id;
        try {
            if (position == INVALID_POSITION) {
                position = getPositionForView(view);
            }
        } catch (Exception e) {
            position = INVALID_POSITION;
            LogUtils.w(LOG_TAG, "Exception finding position; using alternate strategy");
        }
        if (position == INVALID_POSITION) {
            // Try the other way!
            Conversation foundConv;
            long foundId;
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                if (child instanceof SwipeableConversationItemView) {
                    foundConv = ((SwipeableConversationItemView) child).getSwipeableItemView()
                            .getConversation();
                    foundId = foundConv.id;
                    if (foundId == convId) {
                        position = i;
                        break;
                    }
                }
            }
        }
        return position;
    }

    private AnimatedAdapter getAnimatedAdapter() {
        return (AnimatedAdapter) getAdapter();
    }

    public interface SwipeCompleteListener {
        public void onSwipeComplete(Collection<Conversation> conversations);
    }

    @Override
    public boolean performItemClick(View view, int pos, long id) {
        boolean handled = super.performItemClick(view, pos, id);
        // Commit any existing destructive actions when the user selects a
        // conversation to view.
        commitDestructiveActions();
        return handled;
    }

    @Override
    public void onScroll(AbsListView view, int arg1, int arg2, int arg3) {
        // Do nothing; we only care about going from not scrolling to scrolling.
    }

    @Override
    public void onScrollStateChanged(AbsListView view, int state) {
        if (state == OnScrollListener.SCROLL_STATE_TOUCH_SCROLL
                || state == OnScrollListener.SCROLL_STATE_FLING) {
            commitDestructiveActions();
        }
    }
}
