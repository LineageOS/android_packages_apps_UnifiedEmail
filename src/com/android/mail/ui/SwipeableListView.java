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
import android.net.Uri;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
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

public class SwipeableListView extends ListView implements Callback {
    private SwipeHelper mSwipeHelper;
    private boolean mEnableSwipe = false;

    public static final String LOG_TAG = LogTag.getLogTag();

    private ConversationSelectionSet mConvSelectionSet;
    private int mSwipeAction;
    private Folder mFolder;

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
        View view = v.getView();
        return view instanceof ConversationItemView || view instanceof LeaveBehindItem;
    }

    @Override
    public void onChildDismissed(SwipeableItemView v) {
        View view = v.getView();
        if (view != null) {
            if (view instanceof ConversationItemView) {
                dismissChildren((ConversationItemView) view, null);
            } else if (view instanceof LeaveBehindItem) {
                ((LeaveBehindItem) view).commit();
            }
        }
    }

    // Call this whenever a new action is taken; this forces a commit of any
    // existing destructive actions.
    public void commitDestructiveActions() {
        final AnimatedAdapter adapter = getAnimatedAdapter();
        if (adapter != null) {
            adapter.commitLeaveBehindItems();
        }
    }

    @Override
    public void onChildrenDismissed(SwipeableItemView target,
            Collection<ConversationItemView> views) {
        assert (target instanceof ConversationItemView);
        dismissChildren((ConversationItemView) target.getView(), views);
    }

    private void dismissChildren(final ConversationItemView target,
            final Collection<ConversationItemView> conversationViews) {
        final Context context = getContext();
        final AnimatedAdapter adapter = getAnimatedAdapter();
        final ToastBarOperation undoOp;
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
            undoOp = new ToastBarOperation(conversationViews != null ? (conversations.size() + 1)
                    : 1, mSwipeAction, ToastBarOperation.UNDO);
            handleLeaveBehind(target, undoOp, context);
            adapter.delete(conversations, new DestructiveAction() {
                @Override
                public void performAction() {
                    ConversationCursor cc = (ConversationCursor) adapter.getCursor();
                    switch (mSwipeAction) {
                        case R.id.archive:
                            cc.archive(context, conversations);
                            break;
                        case R.id.change_folder:
                            FolderOperation folderOp = new FolderOperation(mFolder, false);
                            // For each conversation, for each operation, remove
                            // the current folder.
                            for (Conversation target : conversations) {
                                HashMap<Uri, Folder> targetFolders = Folder
                                        .hashMapForFolders(target.getRawFolders());
                                targetFolders.remove(folderOp.mFolder.uri);
                                target.setRawFolders(Folder.getSerializedFolderString(targetFolders
                                        .values()));
                                cc.updateString(context, Conversation.listOf(target),
                                        Conversation.UPDATE_FOLDER_COLUMN,
                                        target.getRawFoldersString());
                            }
                            break;
                        case R.id.delete:
                            cc.delete(context, conversations);
                            break;
                    }
                }
            });
        } else {
            undoOp = new ToastBarOperation(1, mSwipeAction, ToastBarOperation.UNDO);
            target.getConversation().position = target.getParent() != null ?
                    getPositionForView(target) : -1;
            handleLeaveBehind(target, undoOp, context);
        }
    }

    private void handleLeaveBehind(ConversationItemView target, ToastBarOperation undoOp,
            Context context) {
        Conversation conv = target.getConversation();
        final AnimatedAdapter adapter = getAnimatedAdapter();
        if (adapter == null) {
            return;
        }
        adapter.setupLeaveBehind(conv, undoOp, conv.position);
        ConversationCursor cc = (ConversationCursor) adapter.getCursor();
        switch (mSwipeAction) {
            case R.id.change_folder:
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
            view.addBackground(getContext(), getSwipeActionText());
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
     * Get the position within the adapter's data set for the view, where view is a an adapter item
     * or a descendant of an adapter item.
     *
     * @param view an adapter item, or a descendant of an adapter item. This must be visible in this
     *        AdapterView at the time of the call.
     * @return the position within the adapter's data set of the view, or {@link #INVALID_POSITION}
     *         if the view does not correspond to a list item (or it is not currently visible).
     */
    // TODO(mindyp): remove this override once I fix b/6884047
    @Override
    public int getPositionForView(View view) {
        View listItem = view;
        View v = null;
        try {
            while (!(v = (View) listItem.getParent()).equals(this)) {
                listItem = v;
            }
        } catch (ClassCastException e) {
            // We made it up to the window without find this list view
            return INVALID_POSITION;
        } catch (NullPointerException e) {
            LogUtils.e(LOG_TAG, e, "WHAT HAS NO PARENT "
                    + (listItem != null ? listItem.getClass() : null));
            return INVALID_POSITION;
        }
        return super.getPositionForView(view);
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
            conv.position = conv.position == -1 && view.getParent() != null ?
                    getPositionForView(view) : conv.position;
            conversations.add(conv);
        }
        AnimatedAdapter adapter = getAnimatedAdapter();
        if (adapter != null) {
            adapter.delete(conversations, listener);
        }
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

    /**
     * Get the text resource corresponding to the result of a swipe.
     */
    public int getSwipeActionText() {
        switch (mSwipeAction) {
            case R.id.archive:
                return R.string.archive;
            case R.id.delete:
                return R.string.delete;
            case R.id.change_folder:
                return R.string.remove_folder;
        }
        return -1;
    }
}
