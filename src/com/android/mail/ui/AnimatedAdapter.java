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
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.database.Cursor;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SimpleCursorAdapter;

import com.android.mail.R;
import com.android.mail.browse.ConversationCursor;
import com.android.mail.browse.ConversationItemView;
import com.android.mail.browse.ConversationItemViewCoordinates;
import com.android.mail.browse.SwipeableConversationItemView;
import com.android.mail.providers.Account;
import com.android.mail.providers.AccountObserver;
import com.android.mail.providers.Conversation;
import com.android.mail.providers.Folder;
import com.android.mail.providers.UIProvider;
import com.android.mail.ui.SwipeableListView.ListItemsRemovedListener;
import com.android.mail.utils.LogTag;
import com.android.mail.utils.LogUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;

public class AnimatedAdapter extends SimpleCursorAdapter implements
        android.animation.Animator.AnimatorListener {
    private static final String LAST_DELETING_ITEMS = "last_deleting_items";
    private static final String LEAVE_BEHIND_ITEM = "leave_behind_item";
    private final static int TYPE_VIEW_CONVERSATION = 0;
    private final static int TYPE_VIEW_FOOTER = 1;
    private final HashSet<Long> mDeletingItems = new HashSet<Long>();
    private final ArrayList<Long> mLastDeletingItems = new ArrayList<Long>();
    private final HashSet<Long> mUndoingItems = new HashSet<Long>();
    private final HashSet<Long> mSwipeDeletingItems = new HashSet<Long>();
    private final HashSet<Long> mSwipeUndoingItems = new HashSet<Long>();
    private final HashMap<Long, SwipeableConversationItemView> mAnimatingViews =
            new HashMap<Long, SwipeableConversationItemView>();
    private final HashMap<Long, LeaveBehindItem> mFadeLeaveBehindItems =
            new HashMap<Long, LeaveBehindItem>();
    /** The current account */
    private Account mAccount;
    private final Context mContext;
    private final ConversationSelectionSet mBatchConversations;
    /**
     * The next action to perform. Do not read or write this. All accesses should
     * be in {@link #performAndSetNextAction(DestructiveAction)} which commits the
     * previous action, if any.
     */
    private ListItemsRemovedListener mPendingDestruction;
    /**
     * A destructive action that refreshes the list and performs no other action.
     */
    private final ListItemsRemovedListener mRefreshAction = new ListItemsRemovedListener() {
        @Override
        public void onListItemsRemoved() {
            notifyDataSetChanged();
        }
    };

    public interface Listener {
        void onAnimationEnd(AnimatedAdapter adapter);
    }

    private View mFooter;
    private boolean mShowFooter;
    private Folder mFolder;
    private final SwipeableListView mListView;
    private boolean mSwipeEnabled;
    private LeaveBehindItem mLeaveBehindItem;
    /** True if priority inbox markers are enabled, false otherwise. */
    private boolean mPriorityMarkersEnabled;
    private ControllableActivity mActivity;
    private final AccountObserver mAccountListener = new AccountObserver() {
        @Override
        public void onChanged(Account newAccount) {
            setAccount(newAccount);
            notifyDataSetChanged();
        }
    };

    private final void setAccount(Account newAccount) {
        mAccount = newAccount;
        mPriorityMarkersEnabled = mAccount.settings.priorityArrowsEnabled;
        mSwipeEnabled = mAccount.supportsCapability(UIProvider.AccountCapabilities.UNDO);
    }

    /**
     * Used only for debugging.
     */
    private static final String LOG_TAG = LogTag.getLogTag();

    public AnimatedAdapter(Context context, int textViewResourceId, ConversationCursor cursor,
            ConversationSelectionSet batch,
            ControllableActivity activity, SwipeableListView listView) {
        super(context, textViewResourceId, cursor, UIProvider.CONVERSATION_PROJECTION, null, 0);
        mContext = context;
        mBatchConversations = batch;
        setAccount(mAccountListener.initialize(activity.getAccountController()));
        mActivity = activity;
        mShowFooter = false;
        mListView = listView;
    }

    public final void destroy() {
        // Set a null cursor in the adapter
        swapCursor(null);
        mAccountListener.unregisterAndDestroy();
    }

    @Override
    public int getCount() {
        final int count = super.getCount();
        return mShowFooter ? count + 1 : count;
    }

    public void setUndo(boolean undo) {
        if (undo && !mLastDeletingItems.isEmpty()) {
            mUndoingItems.addAll(mLastDeletingItems);
            mLastDeletingItems.clear();
            // Start animation
            notifyDataSetChanged();
            performAndSetNextAction(mRefreshAction);
        }
    }

    public void setSwipeUndo(boolean undo) {
        if (undo && !mLastDeletingItems.isEmpty()) {
            mSwipeUndoingItems.addAll(mLastDeletingItems);
            mLastDeletingItems.clear();
            // Start animation
            notifyDataSetChanged();
            performAndSetNextAction(mRefreshAction);
        }
    }

    public View createConversationItemView(SwipeableConversationItemView view, Context context,
            Conversation conv) {
        if (view == null) {
            view = new SwipeableConversationItemView(context, mAccount.name);
        }
        view.bind(conv, mActivity, mBatchConversations, mFolder,
                mAccount != null ? mAccount.settings.hideCheckboxes : false, mSwipeEnabled,
                mPriorityMarkersEnabled, this);
        return view;
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }

    @Override
    public int getViewTypeCount() {
        // TYPE_VIEW_CONVERSATION, TYPE_VIEW_DELETING, TYPE_VIEW_UNDOING, and
        // TYPE_VIEW_FOOTER, TYPE_VIEW_LEAVEBEHIND.
        return 5;
    }

    @Override
    public int getItemViewType(int position) {
        // Try to recycle views.
        if (mShowFooter && position == super.getCount()) {
            return TYPE_VIEW_FOOTER;
        }
        return TYPE_VIEW_CONVERSATION;
    }

    /**
     * Deletes the selected conversations from the conversation list view with a
     * translation and then a shrink. These conversations <b>must</b> have their
     * {@link Conversation#position} set to the position of these conversations
     * among the list. This will only remove the element from the list. The job
     * of deleting the actual element is left to the the listener. This listener
     * will be called when the animations are complete and is required to delete
     * the conversation.
     * @param conversations
     * @param listener
     */
    public void swipeDelete(Collection<Conversation> conversations,
            ListItemsRemovedListener listener) {
        delete(conversations, listener, mSwipeDeletingItems);
    }


    /**
     * Deletes the selected conversations from the conversation list view by
     * shrinking them away. These conversations <b>must</b> have their
     * {@link Conversation#position} set to the position of these conversations
     * among the list. This will only remove the element from the list. The job
     * of deleting the actual element is left to the the listener. This listener
     * will be called when the animations are complete and is required to delete
     * the conversation.
     * @param conversations
     * @param listener
     */
    public void delete(Collection<Conversation> conversations, ListItemsRemovedListener listener) {
        delete(conversations, listener, mDeletingItems);
    }

    private void delete(Collection<Conversation> conversations, ListItemsRemovedListener listener,
            HashSet<Long> list) {
        // Clear out any remaining items and add the new ones
        mLastDeletingItems.clear();

        final int startPosition = mListView.getFirstVisiblePosition();
        final int endPosition = mListView.getLastVisiblePosition();

        // Only animate visible items
        for (Conversation c: conversations) {
            if (c.position >= startPosition && c.position <= endPosition) {
                mLastDeletingItems.add(c.id);
                list.add(c.id);
            }
        }

        if (list.isEmpty()) {
            // If we have no deleted items on screen, skip the animation
            listener.onListItemsRemoved();
        } else {
            performAndSetNextAction(listener);
        }
        notifyDataSetChanged();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (mShowFooter && position == super.getCount()) {
            return mFooter;
        }
        ConversationCursor cursor = (ConversationCursor) getItem(position);
        Conversation conv = new Conversation(cursor);
        if (isPositionUndoing(conv.id)) {
            return getUndoingView(position, conv, parent, false /* don't show swipe background */);
        } if (isPositionUndoingSwipe(conv.id)) {
            return getUndoingView(position, conv, parent, true /* show swipe background */);
        } else if (isPositionDeleting(conv.id)) {
            return getDeletingView(position, conv, parent, false);
        } else if (isPositionSwipeDeleting(conv.id)) {
            return getDeletingView(position, conv, parent, true);
        }
        if (hasFadeLeaveBehinds()) {
            if(isPositionFadeLeaveBehind(conv)) {
                LeaveBehindItem fade  = getFadeLeaveBehindItem(position, conv);
                fade.startAnimation(mActivity.getViewMode(), this);
                return fade;
            }
        }
        if (hasLeaveBehinds()) {
            if(isPositionLeaveBehind(conv)) {
                LeaveBehindItem fadeIn = getLeaveBehindItem(conv);
                if (hasFadeLeaveBehinds()) {
                    // Avoid the fade in and just show the text.
                    fadeIn.showTextImmediately();
                } else {
                    fadeIn.startFadeInAnimation();
                }
                return fadeIn;
            }
        }
        if (convertView != null && !(convertView instanceof SwipeableConversationItemView)) {
            LogUtils.w(LOG_TAG, "Incorrect convert view received; nulling it out");
            convertView = null;
        } else if (convertView != null) {
            ((SwipeableConversationItemView) convertView).reset();
        }
        return createConversationItemView((SwipeableConversationItemView) convertView, mContext,
                conv);
    }

    private boolean hasLeaveBehinds() {
        return mLeaveBehindItem != null;
    }

    private boolean hasFadeLeaveBehinds() {
        return !mFadeLeaveBehindItems.isEmpty();
    }

    public LeaveBehindItem setupLeaveBehind(Conversation target, ToastBarOperation undoOp,
            int deletedRow) {
        fadeOutLeaveBehindItems();
        boolean isWide = ConversationItemViewCoordinates.isWideMode(ConversationItemViewCoordinates
                .getMode(mContext, mActivity.getViewMode()));
        LeaveBehindItem leaveBehind = (LeaveBehindItem) LayoutInflater.from(mContext).inflate(
                isWide? R.layout.swipe_leavebehind_wide : R.layout.swipe_leavebehind, null);
        leaveBehind.bindOperations(deletedRow, mAccount, this, undoOp, target, mFolder);
        mLeaveBehindItem = leaveBehind;
        mLastDeletingItems.add(target.id);
        return leaveBehind;
    }

    public void fadeOutLeaveBehindItems() {
        // Remove any previously existing leave behind item.
        final int startPosition = mListView.getFirstVisiblePosition();
        final int endPosition = mListView.getLastVisiblePosition();

        if (hasLeaveBehinds()) {
            // If the item is visible, fade it out. Otherwise, just remove
            // it.
            Conversation conv = mLeaveBehindItem.getData();
            if (conv.position >= startPosition && conv.position <= endPosition) {
                mFadeLeaveBehindItems.put(conv.id, mLeaveBehindItem);
            } else {
                mLeaveBehindItem.commit();
            }
            clearLeaveBehind(conv.id);
        }
        if (!mLastDeletingItems.isEmpty()) {
            mLastDeletingItems.clear();
        }
        notifyDataSetChanged();
    }

    public SwipeableListView getListView() {
        return mListView;
    }

    public void commitLeaveBehindItems(boolean animate) {
        // Remove any previously existing leave behinds.
        boolean changed = false;
        if (hasLeaveBehinds()) {
            if (animate) {
                mLeaveBehindItem.dismiss();
            } else {
                mLeaveBehindItem.commit();
            }
            changed = true;
        }
        if (hasFadeLeaveBehinds() && !animate) {
            // Find any fading leave behind items and commit them all, too.
            for (LeaveBehindItem item : mFadeLeaveBehindItems.values()) {
                item.commit();
            }
            mFadeLeaveBehindItems.clear();
            changed = true;
        }
        if (!mLastDeletingItems.isEmpty()) {
            mLastDeletingItems.clear();
            changed = true;
        }
        if (changed) {
            notifyDataSetChanged();
        }
    }

    private LeaveBehindItem getLeaveBehindItem(Conversation target) {
        return mLeaveBehindItem;
    }

    private LeaveBehindItem getFadeLeaveBehindItem(int position, Conversation target) {
        return mFadeLeaveBehindItems.get(target.id);
    }

    @Override
    public long getItemId(int position) {
        if (mShowFooter && position == super.getCount()) {
            return -1;
        }
        return super.getItemId(position);
    }

    private View getDeletingView(int position, Conversation conversation, ViewGroup parent,
            boolean swipe) {
        conversation.position = position;
        SwipeableConversationItemView deletingView = mAnimatingViews.get(conversation.id);
        if (deletingView == null) {
            // The undo animation consists of fading in the conversation that
            // had been destroyed.
            deletingView = newConversationItemView(position, parent, conversation);
            deletingView.startDeleteAnimation(this, swipe);
        }
        return deletingView;
    }

    private View getUndoingView(int position, Conversation conv, ViewGroup parent, boolean swipe) {
        conv.position = position;
        SwipeableConversationItemView undoView = mAnimatingViews.get(conv.id);
        if (undoView == null) {
            // The undo animation consists of fading in the conversation that
            // had been destroyed.
            undoView = newConversationItemView(position, parent, conv);
            undoView.startUndoAnimation(mActivity.getViewMode(), this, swipe);
        }
        return undoView;
    }

    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        SwipeableConversationItemView view = new SwipeableConversationItemView(context,
                mAccount.name);
        return view;
    }

    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        if (! (view instanceof SwipeableConversationItemView)) {
            return;
        }
        ((SwipeableConversationItemView) view).bind(cursor, mActivity, mBatchConversations, mFolder,
                mAccount != null ? mAccount.settings.hideCheckboxes : false,
                        mSwipeEnabled, mPriorityMarkersEnabled, this);
    }

    private SwipeableConversationItemView newConversationItemView(int position, ViewGroup parent,
            Conversation conversation) {
        SwipeableConversationItemView view = (SwipeableConversationItemView) super.getView(
                position, null, parent);
        view.reset();
        view.bind(conversation, mActivity, mBatchConversations, mFolder,
                mAccount != null ? mAccount.settings.hideCheckboxes : false, mSwipeEnabled,
                mPriorityMarkersEnabled, this);
        mAnimatingViews.put(conversation.id, view);
        return view;
    }

    @Override
    public Object getItem(int position) {
        if (mShowFooter && position == super.getCount()) {
            return mFooter;
        }
        return super.getItem(position);
    }

    private boolean isPositionDeleting(long id) {
        return mDeletingItems.contains(id);
    }

    private boolean isPositionSwipeDeleting(long id) {
        return mSwipeDeletingItems.contains(id);
    }

    private boolean isPositionUndoing(long id) {
        return mUndoingItems.contains(id);
    }

    private boolean isPositionUndoingSwipe(long id) {
        return mSwipeUndoingItems.contains(id);
    }

    private boolean isPositionUndoingType(long id) {
        return isPositionUndoing(id) || isPositionUndoingSwipe(id);
    }

    private boolean isPositionLeaveBehind(Conversation conv) {
        return hasLeaveBehinds()
                && mLeaveBehindItem.getConversationId() == conv.id
                && conv.isMostlyDead();
    }

    private boolean isPositionFadeLeaveBehind(Conversation conv) {
        return hasFadeLeaveBehinds()
                && mFadeLeaveBehindItems.containsKey(conv.id)
                && conv.isMostlyDead();
    }

    private boolean isPositionTypeLeaveBehind(Conversation conv) {
        if (hasLeaveBehinds()) {
            return isPositionLeaveBehind(conv) || isPositionFadeLeaveBehind(conv);
        }
        return false;
    }

    @Override
    public void onAnimationStart(Animator animation) {
        if (!mUndoingItems.isEmpty()) {
            mDeletingItems.clear();
            mLastDeletingItems.clear();
            mSwipeDeletingItems.clear();
        } else {
            mUndoingItems.clear();
        }
    }

    /**
     * Performs the pending destruction, if any and assigns the next pending action.
     * @param next The next action that is to be performed, possibly null (if no next action is
     * needed).
     */
    private final void performAndSetNextAction(ListItemsRemovedListener next) {
        if (mPendingDestruction != null) {
            mPendingDestruction.onListItemsRemoved();
        }
        mPendingDestruction = next;
    }

    @Override
    public void onAnimationEnd(Animator animation) {
        Object obj;
        if (animation instanceof AnimatorSet) {
            AnimatorSet set = (AnimatorSet) animation;
            obj = ((ObjectAnimator) set.getChildAnimations().get(0)).getTarget();
        } else {
            obj = ((ObjectAnimator) animation).getTarget();
        }
        updateAnimatingConversationItems(obj, mSwipeDeletingItems);
        updateAnimatingConversationItems(obj, mDeletingItems);
        updateAnimatingConversationItems(obj, mSwipeUndoingItems);
        updateAnimatingConversationItems(obj, mUndoingItems);
        if (hasFadeLeaveBehinds() && obj instanceof LeaveBehindItem) {
            LeaveBehindItem objItem = (LeaveBehindItem) obj;
            clearLeaveBehind(objItem.getConversationId());
            objItem.commit();
            // The view types have changed, since the animating views are gone.
            notifyDataSetChanged();
        }

        if (!isAnimating()) {
            mActivity.onAnimationEnd(this);
        }
    }

    private void updateAnimatingConversationItems(Object obj, HashSet<Long> items) {
        if (!items.isEmpty()) {
            if (obj instanceof ConversationItemView) {
                final ConversationItemView target = (ConversationItemView) obj;
                final long id = target.getConversation().id;
                items.remove(id);
                mAnimatingViews.remove(id);
                if (items.isEmpty()) {
                    performAndSetNextAction(null);
                    notifyDataSetChanged();
                }
            }
        }
    }

    @Override
    public boolean areAllItemsEnabled() {
        // The animating positions are not enabled.
        return false;
    }

    @Override
    public boolean isEnabled(int position) {
        return !isPositionDeleting(position) && !isPositionUndoing(position);
    }

    @Override
    public void onAnimationCancel(Animator animation) {
        onAnimationEnd(animation);
    }

    @Override
    public void onAnimationRepeat(Animator animation) {
    }

    public void showFooter() {
        setFooterVisibility(true);
    }

    public void hideFooter() {
        setFooterVisibility(false);
    }

    public void setFooterVisibility(boolean show) {
        if (mShowFooter != show) {
            mShowFooter = show;
            notifyDataSetChanged();
        }
    }

    public void addFooter(View footerView) {
        mFooter = footerView;
    }

    public void setFolder(Folder folder) {
        mFolder = folder;
    }

    public void clearLeaveBehind(long itemId) {
        if (hasLeaveBehinds() && mLeaveBehindItem.getConversationId() == itemId) {
            mLeaveBehindItem = null;
        } else if (hasFadeLeaveBehinds()) {
            mFadeLeaveBehindItems.remove(itemId);
        } else {
            LogUtils.d(LOG_TAG, "Trying to clear a non-existant leave behind");
        }
    }

    public void onSaveInstanceState(Bundle outState) {
        long[] lastDeleting = new long[mLastDeletingItems.size()];
        for (int i = 0; i < lastDeleting.length; i++) {
            lastDeleting[i] = mLastDeletingItems.get(i);
        }
        outState.putLongArray(LAST_DELETING_ITEMS, lastDeleting);
        if (hasLeaveBehinds()) {
            outState.putParcelable(LEAVE_BEHIND_ITEM, mLeaveBehindItem.getLeaveBehindData());
        }
    }

    public void onRestoreInstanceState(Bundle outState) {
        if (outState.containsKey(LAST_DELETING_ITEMS)) {
            final long[] lastDeleting = outState.getLongArray(LAST_DELETING_ITEMS);
            for (int i = 0; i < lastDeleting.length;i++) {
                mLastDeletingItems.add(lastDeleting[i]);
            }
        }
        if (outState.containsKey(LEAVE_BEHIND_ITEM)) {
            LeaveBehindItem.LeaveBehindData left = outState.getParcelable(LEAVE_BEHIND_ITEM);
            LeaveBehindItem item = setupLeaveBehind(left.data, left.op, left.data.position);
            mLeaveBehindItem = item;
        }
    }

    /**
     * Return if the adapter is in the process of animating anything.
     */
    public boolean isAnimating() {
        return !mUndoingItems.isEmpty()
                || !mSwipeUndoingItems.isEmpty()
                || hasFadeLeaveBehinds()
                || !mDeletingItems.isEmpty()
                || !mSwipeDeletingItems.isEmpty();
    }

    /**
     * Get the ConversationCursor associated with this adapter.
     */
    public ConversationCursor getConversationCursor() {
        return (ConversationCursor) getCursor();
    }
}
