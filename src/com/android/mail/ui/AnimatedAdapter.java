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
import com.android.mail.browse.SwipeableConversationItemView;
import com.android.mail.providers.Account;
import com.android.mail.providers.Conversation;
import com.android.mail.providers.Folder;
import com.android.mail.providers.Settings;
import com.android.mail.providers.UIProvider;
import com.android.mail.utils.LogTag;
import com.android.mail.utils.LogUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;

public class AnimatedAdapter extends SimpleCursorAdapter implements
        android.animation.Animator.AnimatorListener, Settings.ChangeListener {
    private static final String LAST_DELETING_ITEMS = "last_deleting_items";
    private static final String LEAVE_BEHIND_ITEM = "leave_behind_item";
    private final static int TYPE_VIEW_CONVERSATION = 0;
    private final static int TYPE_VIEW_DELETING = 1;
    private final static int TYPE_VIEW_UNDOING = 2;
    private final static int TYPE_VIEW_FOOTER = 3;
    private final static int TYPE_VIEW_LEAVEBEHIND = 4;
    private final HashSet<Integer> mDeletingItems = new HashSet<Integer>();
    private final HashSet<Integer> mUndoingItems = new HashSet<Integer>();
    private final HashSet<Integer> mSwipeDeletingItems = new HashSet<Integer>();
    private final HashSet<Integer> mSwipeUndoingItems = new HashSet<Integer>();
    private final HashMap<Long, SwipeableConversationItemView> mAnimatingViews =
            new HashMap<Long, SwipeableConversationItemView>();
    private HashMap<Long, LeaveBehindItem> mFadeLeaveBehindItems =
            new HashMap<Long, LeaveBehindItem>();
    /** The current account */
    private final Account mAccount;
    private final Context mContext;
    private final ConversationSelectionSet mBatchConversations;
    /**
     * The next action to perform. Do not read or write this. All accesses should
     * be in {@link #performAndSetNextAction(DestructiveAction)} which commits the
     * previous action, if any.
     */
    private DestructiveAction mPendingDestruction;
    /**
     * A destructive action that refreshes the list and performs no other action.
     */
    private final DestructiveAction mRefreshAction = new DestructiveAction() {
        @Override
        public void performAction() {
            notifyDataSetChanged();
        }
    };

    public interface Listener {
        void onAnimationEnd(AnimatedAdapter adapter);
    }

    private final ArrayList<Integer> mLastDeletingItems = new ArrayList<Integer>();
    private View mFooter;
    private boolean mShowFooter;
    private Folder mFolder;
    private final SwipeableListView mListView;
    private Settings mCachedSettings;
    private final boolean mSwipeEnabled;
    private LeaveBehindItem mLeaveBehindItem;
    /** True if priority inbox markers are enabled, false otherwise. */
    private final boolean mPriorityMarkersEnabled;
    private ControllableActivity mActivity;
    /**
     * Used only for debugging.
     */
    private static final String LOG_TAG = LogTag.getLogTag();

    public AnimatedAdapter(Context context, int textViewResourceId, ConversationCursor cursor,
            ConversationSelectionSet batch, Account account, Settings settings,
            ControllableActivity activity, SwipeableListView listView) {
        super(context, textViewResourceId, cursor, UIProvider.CONVERSATION_PROJECTION, null, 0);
        mContext = context;
        mBatchConversations = batch;
        mAccount = account;
        mActivity = activity;
        mShowFooter = false;
        mListView = listView;
        mCachedSettings = settings;
        mSwipeEnabled = account.supportsCapability(UIProvider.AccountCapabilities.UNDO);
        mPriorityMarkersEnabled = account.settings.priorityArrowsEnabled;
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
                mCachedSettings != null ? mCachedSettings.hideCheckboxes : false,
                        mSwipeEnabled, mPriorityMarkersEnabled, this);
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
        if (isPositionDeleting(position)) {
            return TYPE_VIEW_DELETING;
        }
        if (isPositionUndoingType(position)) {
            return TYPE_VIEW_UNDOING;
        }
        if (mShowFooter && position == super.getCount()) {
            return TYPE_VIEW_FOOTER;
        }
        if (isPositionTypeLeaveBehind(position)) {
            return TYPE_VIEW_LEAVEBEHIND;
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
    public void swipeDelete(Collection<Conversation> conversations, DestructiveAction listener) {
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
    public void delete(Collection<Conversation> conversations, DestructiveAction listener) {
        delete(conversations, listener, mDeletingItems);
    }

    private void delete(Collection<Conversation> conversations, DestructiveAction action,
            HashSet<Integer> list) {
        // Animate out the positions.
        // Call when all the animations are complete.
        final ArrayList<Integer> deletedRows = new ArrayList<Integer>();
        for (Conversation c : conversations) {
            deletedRows.add(c.position);
        }
        // Clear out any remaining items and add the new ones
        mLastDeletingItems.clear();

        final int startPosition = mListView.getFirstVisiblePosition();
        final int endPosition = mListView.getLastVisiblePosition();

        // Only animate visible items
        for (int deletedRow: deletedRows) {
            if (deletedRow >= startPosition && deletedRow <= endPosition) {
                mLastDeletingItems.add(deletedRow);
                list.add(deletedRow);
            }
        }

        if (list.isEmpty()) {
            // If we have no deleted items on screen, skip the animation
            action.performAction();
        } else {
            performAndSetNextAction(action);
        }

        // TODO(viki): Rather than notifying for a full data set change,
        // perhaps we can mark
        // only the affected conversations?
        notifyDataSetChanged();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (mShowFooter && position == super.getCount()) {
            return mFooter;
        }
        if (isPositionUndoing(position)) {
            return getUndoingView(position, parent, false /* don't show swipe background */);
        } if (isPositionUndoingSwipe(position)) {
            return getUndoingView(position, parent, true /* show swipe background */);
        } else if (isPositionDeleting(position)) {
            return getDeletingView(position, parent, false);
        } else if (isPositionSwipeDeleting(position)) {
            return getDeletingView(position, parent, true);
        }
        if (hasFadeLeaveBehinds()) {
            Conversation conv = new Conversation((ConversationCursor) getItem(position));
            if(isPositionFadeLeaveBehind(conv)) {
                LeaveBehindItem fade  = getFadeLeaveBehindItem(position, conv);
                fade.startAnimation(mActivity.getViewMode(), this);
                return fade;
            }
        }
        if (hasLeaveBehinds()) {
            Conversation conv = new Conversation((ConversationCursor) getItem(position));
            if(isPositionLeaveBehind(conv)) {
                return getLeaveBehindItem(conv);
            }
        }
        if (convertView != null && !(convertView instanceof SwipeableConversationItemView)) {
            LogUtils.w(LOG_TAG, "Incorrect convert view received; nulling it out");
            convertView = null;
        } else if (convertView != null) {
            ((SwipeableConversationItemView) convertView).reset();
        }
        return super.getView(position, convertView, parent);
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
        LeaveBehindItem leaveBehind = (LeaveBehindItem) LayoutInflater.from(mContext).inflate(
                R.layout.swipe_leavebehind, null);
        leaveBehind.bindOperations(deletedRow, mAccount, this, undoOp, target, mFolder);
        mLeaveBehindItem = leaveBehind;
        mLastDeletingItems.add(deletedRow);
        return leaveBehind;
    }

    public void fadeOutLeaveBehindItems(DestructiveAction action) {
        fadeOutLeaveBehindItems();
        performAndSetNextAction(action);
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
            }
            clearLeaveBehind(conv.id);
        }
        if (!mLastDeletingItems.isEmpty()) {
            mLastDeletingItems.clear();
        }
        notifyDataSetChanged();
    }

    public void commitLeaveBehindItems() {
        // Remove any previously existing leave behinds.
        if (hasLeaveBehinds()) {
            mLeaveBehindItem.commit();
        }
        if (hasFadeLeaveBehinds()) {
            for (LeaveBehindItem item : mFadeLeaveBehindItems.values()) {
                item.commit();
            }
        }
        if (!mLastDeletingItems.isEmpty()) {
            mLastDeletingItems.clear();
        }
        notifyDataSetChanged();
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

    private View getDeletingView(int position, ViewGroup parent, boolean swipe) {
        Conversation conversation = new Conversation((ConversationCursor) getItem(position));
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

    private View getUndoingView(int position, ViewGroup parent, boolean swipe) {
        Conversation conversation = new Conversation((ConversationCursor) getItem(position));
        conversation.position = position;
        SwipeableConversationItemView undoView = mAnimatingViews.get(conversation.id);
        if (undoView == null) {
            // The undo animation consists of fading in the conversation that
            // had been destroyed.
            undoView = newConversationItemView(position, parent, conversation);
            undoView.startUndoAnimation(mListView.getSwipeActionText(), mActivity.getViewMode(),
                    this, swipe);
        }
        return undoView;
    }

    private SwipeableConversationItemView newConversationItemView(int position, ViewGroup parent,
            Conversation conversation) {
        SwipeableConversationItemView view = (SwipeableConversationItemView) super.getView(
                position, null, parent);
        view.bind(conversation, mActivity, mBatchConversations, mFolder,
                mCachedSettings != null ? mCachedSettings.hideCheckboxes : false, mSwipeEnabled,
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

    private boolean isPositionDeleting(int position) {
        return mDeletingItems.contains(position);
    }

    private boolean isPositionSwipeDeleting(int position) {
        return mSwipeDeletingItems.contains(position);
    }

    private boolean isPositionUndoing(int position) {
        return mUndoingItems.contains(position);
    }

    private boolean isPositionUndoingSwipe(int position) {
        return mSwipeUndoingItems.contains(position);
    }

    private boolean isPositionUndoingType(int position) {
        return isPositionUndoing(position) || isPositionUndoingSwipe(position);
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

    private boolean isPositionTypeLeaveBehind(int position) {
        if (hasLeaveBehinds()) {
            Object item = getItem(position);
            if (item instanceof ConversationCursor) {
                Conversation conv = new Conversation((ConversationCursor) item);
                return isPositionLeaveBehind(conv) || isPositionFadeLeaveBehind(conv);
            }
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
    private final void performAndSetNextAction(DestructiveAction next) {
        if (mPendingDestruction != null) {
            mPendingDestruction.performAction();
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

    private void updateAnimatingConversationItems(Object obj, HashSet<Integer> items) {
        if (!items.isEmpty()) {
            if (obj instanceof ConversationItemView) {
                final ConversationItemView target = (ConversationItemView) obj;
                final int position = target.getPosition();
                items.remove(position);
                mAnimatingViews.remove(target.getConversation().id);
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

    /**
     * Callback invoked when settings for the current account have been changed.
     * @param updatedSettings
     */
    @Override
    public void onSettingsChanged(Settings updatedSettings) {
        mCachedSettings = updatedSettings;
        notifyDataSetChanged();
    }

    public void onSaveInstanceState(Bundle outState) {
        int[] lastDeleting = new int[mLastDeletingItems.size()];
        for (int i = 0; i < lastDeleting.length; i++) {
            lastDeleting[i] = mLastDeletingItems.get(i);
        }
        outState.putIntArray(LAST_DELETING_ITEMS, lastDeleting);
        if (hasLeaveBehinds()) {
            outState.putParcelable(LEAVE_BEHIND_ITEM, mLeaveBehindItem.getLeaveBehindData());
        }
    }

    public void onRestoreInstanceState(Bundle outState) {
        if (outState.containsKey(LAST_DELETING_ITEMS)) {
            final int[] lastDeleting = outState.getIntArray(LAST_DELETING_ITEMS);
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
                || !mFadeLeaveBehindItems.isEmpty()
                || !mDeletingItems.isEmpty()
                || !mSwipeDeletingItems.isEmpty();
    }
}
