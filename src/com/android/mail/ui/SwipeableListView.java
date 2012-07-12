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
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.content.res.Configuration;
import android.net.Uri;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.ListView;

import com.android.mail.R;
import com.android.mail.browse.ConversationCursor;
import com.android.mail.browse.ConversationItemView;
import com.android.mail.providers.Conversation;
import com.android.mail.providers.Folder;
import com.android.mail.providers.UIProvider.ConversationColumns;
import com.android.mail.ui.SwipeHelper.Callback;
import com.android.mail.utils.LogTag;
import com.google.common.base.Objects;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

public class SwipeableListView extends ListView implements Callback{
    private SwipeHelper mSwipeHelper;
    private boolean mEnableSwipe = false;

    public static final String LOG_TAG = LogTag.getLogTag();

    private ConversationSelectionSet mConvSelectionSet;
    private int mSwipeAction;
    private Folder mFolder;
    private ConversationUpdater mConversationUpdater;

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
        float scrollSlop = context.getResources().getInteger(R.integer.swipeScrollSlop);
        float minSwipe = context.getResources().getDimension(R.dimen.min_swipe);
        float minVert = context.getResources().getDimension(R.dimen.min_vert);
        float minLock = context.getResources().getDimension(R.dimen.min_lock);
        mSwipeHelper = new SwipeHelper(SwipeHelper.X, this, densityScale, pagingTouchSlop,
                scrollSlop, minSwipe, minVert, minLock);
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

    public void setSelectionSet(ConversationSelectionSet set) {
        mConvSelectionSet = set;
    }

    public void setCurrentFolder(Folder folder) {
        mFolder = folder;
    }

    public void setConversationUpdater(ConversationUpdater updater) {
        mConversationUpdater = updater;
    }

    @Override
    public ConversationSelectionSet getSelectionSet() {
        return mConvSelectionSet;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (mEnableSwipe) {
            return mSwipeHelper.onInterceptTouchEvent(ev)
                    || super.onInterceptTouchEvent(ev);
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
                return slidingChild;
            }
        }
        return null;
    }

    @Override
    public boolean canChildBeDismissed(SwipeableItemView v) {
        View view = v.getView();
        return view instanceof ConversationItemView || view instanceof LeaveBehindItem;
    }

    @Override
    public void onChildDismissed(SwipeableItemView v) {
        View view = v.getView();
        if (view instanceof ConversationItemView) {
        dismissChildren((ConversationItemView) v, null);
        } else if (view instanceof LeaveBehindItem) {
            ((LeaveBehindItem)view).commit();
        }
    }

    @Override
    public void onChildrenDismissed(SwipeableItemView target,
            Collection<ConversationItemView> views) {
        assert(target instanceof ConversationItemView);
        dismissChildren((ConversationItemView) target.getView(), views);
    }

    private void dismissChildren(final ConversationItemView target,
            final Collection<ConversationItemView> conversationViews) {
        final Context context = getContext();
        final AnimatedAdapter adapter = ((AnimatedAdapter) getAdapter());
        final UndoOperation undoOp;
        if (conversationViews != null) {
            final ArrayList<Conversation> conversations = new ArrayList<Conversation>(
                    conversationViews.size());
            Conversation conversation;
            for (ConversationItemView view : conversationViews) {
                if (view.getConversation().id != target.getConversation().id) {
                    conversation = view.getConversation();
                    conversation.localDeleteOnUpdate = true;
                    conversations.add(conversation);
                }
            }
            undoOp = new UndoOperation(
                    conversationViews != null ? (conversations.size() + 1) : 1, mSwipeAction);
            handleLeaveBehind(target, undoOp, context);
            adapter.delete(conversations, new DestructiveAction() {
                @Override
                public void performAction() {
                    ConversationCursor cc = (ConversationCursor)adapter.getCursor();
                    switch (mSwipeAction) {
                        case R.id.archive:
                            cc.archive(context, conversations);
                            break;
                        case R.id.change_folder:
                            Collection<Folder> folders = getFolders(conversations);
                            cc.updateString(context, conversations,
                                    ConversationColumns.FOLDER_LIST, Folder.getUriString(folders));
                            cc.updateString(context, conversations,
                                    ConversationColumns.RAW_FOLDERS,
                                    Folder.getSerializedFolderString(mFolder, folders));
                            break;
                        case R.id.delete:
                            cc.delete(context, conversations);
                            break;
                    }
                }
            });
        } else {
            undoOp = new UndoOperation(1, mSwipeAction);
            target.getConversation().position = target.getParent() != null ?
                    getPositionForView(target) : -1;
            handleLeaveBehind(target, undoOp, context);
        }
    }

    private Collection<Folder> getFolders(Collection<Conversation> conversations) {
        ArrayList<Folder> folders = new ArrayList<Folder>();
        for (Conversation conversation : conversations) {
            folders.addAll(Folder.forFoldersString(conversation.rawFolders));
        }
        for (Folder folder : folders) {
            if (Objects.equal(folder.uri, mFolder.uri)) {
                folders.remove(folder);
            }
        }
        return folders;
    }

    private void handleLeaveBehind(ConversationItemView target, UndoOperation undoOp,
            Context context) {
        Conversation conv = target.getConversation();
        final AnimatedAdapter adapter = ((AnimatedAdapter) getAdapter());
        adapter.setupLeaveBehind(conv, undoOp, conv.position);
        ConversationCursor cc = (ConversationCursor)adapter.getCursor();
        switch (mSwipeAction) {
            case R.id.change_folder:
                Collection<Conversation> convs = Conversation.listOf(conv);
                Collection<Folder> folders = getFolders(convs);
                cc.mostlyDestructiveUpdate(context, convs,
                        ConversationColumns.FOLDER_LIST, Folder.getUriString(folders));
                cc.mostlyDestructiveUpdate(context, convs,
                        ConversationColumns.RAW_FOLDERS,
                        Folder.getSerializedFolderString(mFolder, folders));
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
    }

    @Override
    public void onDragCancelled(SwipeableItemView v) {
    }

    /**
     * Archive items using the swipe away animation before shrinking them away.
     */
    public void archiveItems(ArrayList<ConversationItemView> views,
            final DestructiveAction listener) {
        if (views == null || views.size() == 0) {
            return;
        }
        final ArrayList<Conversation> conversations = new ArrayList<Conversation>();
        for (ConversationItemView view : views) {
            conversations.add(view.getConversation());
        }
        mSwipeHelper.dismissChildren(views.get(0), views, new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                ((AnimatedAdapter) getAdapter()).delete(conversations, listener);
            }
        });
    }

    public interface SwipeCompleteListener {
        public void onSwipeComplete(Collection<Conversation> conversations);
    }
}