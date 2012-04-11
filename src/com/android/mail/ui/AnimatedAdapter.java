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
import android.animation.ObjectAnimator;
import android.content.Context;
import android.database.Cursor;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SimpleCursorAdapter;

import com.android.mail.browse.ConversationCursor;
import com.android.mail.browse.ConversationItemView;
import com.android.mail.providers.Account;
import com.android.mail.providers.Conversation;
import com.android.mail.providers.Folder;
import com.android.mail.providers.Settings;
import com.android.mail.providers.UIProvider;
import com.android.mail.ui.UndoBarView.OnUndoCancelListener;
import com.android.mail.utils.LogUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;

public class AnimatedAdapter extends SimpleCursorAdapter implements
        android.animation.Animator.AnimatorListener, OnUndoCancelListener {
    private final static int TYPE_VIEW_CONVERSATION = 0;
    private final static int TYPE_VIEW_ANIMATING = 1;
    private final static int TYPE_VIEW_FOOTER = 2;
    private HashSet<Integer> mDeletingItems = new HashSet<Integer>();
    private Account mSelectedAccount;
    private Context mContext;
    private ConversationSelectionSet mBatchConversations;
    private ActionCompleteListener mActionCompleteListener;
    private boolean mUndo = false;
    private ArrayList<Integer> mLastDeletingItems = new ArrayList<Integer>();
    private ViewMode mViewMode;
    private View mFooter;
    private boolean mShowFooter;
    private Folder mFolder;
    private final SwipeableListView mListView;
    private Settings mCachedSettings;
    private boolean mSwipeEnabled;
    private DragListener mDragListener;

    /**
     * Used only for debugging.
     */
    private static final String LOG_TAG = new LogUtils().getLogTag();

    public AnimatedAdapter(Context context, int textViewResourceId, ConversationCursor cursor,
            ConversationSelectionSet batch, Account account, Settings settings, ViewMode viewMode,
            SwipeableListView listView, DragListener dragListener) {
        // Use FLAG_REGISTER_CONTENT_OBSERVER to ensure special
        // ConversationCursor notifications (triggered by UI actions) cause any
        // connected ListView to redraw.
        super(context, textViewResourceId, cursor, UIProvider.CONVERSATION_PROJECTION, null,
                FLAG_REGISTER_CONTENT_OBSERVER);
        mContext = context;
        mBatchConversations = batch;
        mSelectedAccount = account;
        mViewMode = viewMode;
        mShowFooter = false;
        mListView = listView;
        mCachedSettings = settings;
        mDragListener = dragListener;
        mSwipeEnabled = account.supportsCapability(UIProvider.AccountCapabilities.ARCHIVE);
    }

    @Override
    public int getCount() {
        final int count = super.getCount();
        return mShowFooter ? count + 1 : count;
    }

    public void setUndo(boolean state) {
        mUndo = state;
        if (mUndo) {
            mDeletingItems.clear();
            mDeletingItems.addAll(mLastDeletingItems);
            // Start animation
            notifyDataSetChanged();
            mActionCompleteListener = new ActionCompleteListener() {
                @Override
                public void onActionComplete() {
                    notifyDataSetChanged();
                }
            };
        }
    }

    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        ConversationItemView view = new ConversationItemView(context, mSelectedAccount.name);
        return view;
    }

    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        if (! (view instanceof ConversationItemView)) {
            return;
        }
        ((ConversationItemView) view).bind(cursor, mViewMode, mBatchConversations, mFolder,
                mCachedSettings != null ? mCachedSettings.hideCheckboxes : false,
                        mSwipeEnabled, mDragListener);
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }

    @Override
    public int getViewTypeCount() {
        // TYPE_VIEW_CONVERSATION, TYPE_VIEW_ANIMATING, and TYPE_VIEW_FOOTER.
        return 3;
    }

    @Override
    public int getItemViewType(int position) {
        // Try to recycle views.
        if (isPositionAnimating(position)) {
            return TYPE_VIEW_ANIMATING;
        }
        if (mShowFooter && position == super.getCount()) {
            return TYPE_VIEW_FOOTER;
        }
        return TYPE_VIEW_CONVERSATION;
    }

    /**
     * Deletes the selected conversations from the conversation list view. These conversations
     * <b>must</b> have their {@link Conversation#position} set to the position of these
     * conversations among the list. . This will only remove the
     * element from the list. The job of deleting the actual element is left to the the listener.
     * This listener will be called when the animations are complete and is required to
     * delete the conversation.
     * @param conversations
     * @param listener
     */
    public void delete(Collection<Conversation> conversations, ActionCompleteListener listener) {
        // Animate out the positions.
        // Call when all the animations are complete.
        final ArrayList<Integer> positions = new ArrayList<Integer>();
        for (Conversation c : conversations) {
            positions.add(c.position);
        }
        delete(positions, listener);
    }

    /**
     * Deletes a conversation with the list positions given here. This will only remove the
     * element from the list. The job of deleting the actual elements is left to the the listener.
     * This listener will be called when the animations are complete and is required to
     * delete the conversations.
     * @param deletedRows the position in the list view to be deleted.
     * @param listener called when the animation is complete. At this point, it is safe to remove
     * the conversations from the database.
     */
    public void delete(ArrayList<Integer> deletedRows, ActionCompleteListener listener) {
        // Clear out any remaining items and add the new ones
        mLastDeletingItems.clear();

        final int startPosition = mListView.getFirstVisiblePosition();
        final int endPosition = mListView.getLastVisiblePosition();

        // Only animate visible items
        for (int deletedRow: deletedRows) {
            if (deletedRow >= startPosition && deletedRow <= endPosition) {
                mLastDeletingItems.add(deletedRow);
                mDeletingItems.add(deletedRow);
            }
        }

        if (mDeletingItems.isEmpty()) {
            // If we have no deleted items on screen, skip the animation
            listener.onActionComplete();
        } else {
            mActionCompleteListener = listener;
        }

        // TODO(viki): Rather than notifying for a full data set change,
        // perhaps we can mark
        // only the affected conversations?
        notifyDataSetChanged();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView != null) {
            // Print errors if we get the wrong view type.
            final int type = getItemViewType(position);
            switch (type) {
                case TYPE_VIEW_CONVERSATION:
                    if (!(convertView instanceof ConversationItemView)) {
                        LogUtils.e(LOG_TAG, "At position " + position + " expecting Conversation"
                                + " got %s", convertView.toString());
                    }
                    break;
                case TYPE_VIEW_ANIMATING:
                    if (!(convertView instanceof AnimatingItemView)) {
                        LogUtils.e(LOG_TAG, "At position " + position + " expecting Animating"
                                + " got %s", convertView.toString());
                    }
                    break;
            }
        }
        if (mShowFooter && position == super.getCount()) {
            return mFooter;
        }
        if (isPositionAnimating(position)) {
            return getAnimatingView(position, convertView, parent);
        }
        // TODO: do this in the swipe helper?
        // If this view gets recycled, we need to reset things set by the
        // animation.
        if (convertView != null) {
            if (convertView.getAlpha() < 1) {
                convertView.setAlpha(1);
            }
            if (convertView.getTranslationX() != 0) {
                convertView.setTranslationX(0);
            }
        }
        return super.getView(position, convertView, parent);
    }

    @Override
    public long getItemId(int position) {
        if (mShowFooter && position == super.getCount()) {
            return -1;
        }
        return super.getItemId(position);
    }

    /**
     * Get an animating view. This happens when a list item is in the process of being removed
     * from the list (items being deleted).
     * @param position the position of the view inside the list
     * @param convertView if null, a recycled view that we can reuse
     * @param parent the parent view
     * @return the view to show when animating an operation.
     */
    private View getAnimatingView(int position, View convertView, ViewGroup parent) {
        // We are getting the wrong view, and we need to gracefully carry on.
        if (convertView != null && !(convertView instanceof AnimatingItemView)) {
            LogUtils.d(LOG_TAG, "AnimatedAdapter.getAnimatingView received the wrong view!");
            convertView = null;
        }
        Conversation conversation = new Conversation((ConversationCursor) getItem(position));
        conversation.position = position;
        if (mUndo) {
            // The undo animation consists of fading in the conversation that
            // had been destroyed.
            final ConversationItemView convView =
                    (ConversationItemView) super.getView(position, null, parent);
            convView.bind(conversation, mViewMode, mBatchConversations, mFolder,
                    mCachedSettings != null ? mCachedSettings.hideCheckboxes : false,
                    mSwipeEnabled, mDragListener);
            convView.startUndoAnimation(mViewMode, this);
            return convView;
        } else {
            // Destroying a conversation just shows a blank shrinking item.
            final AnimatingItemView view = new AnimatingItemView(mContext);
            view.startAnimation(conversation, mViewMode, this);
            return view;
        }
    }

    @Override
    public Object getItem(int position) {
        if (mShowFooter && position == super.getCount()) {
            return mFooter;
        }
        return super.getItem(position);
    }

    private boolean isPositionAnimating(int position) {
        return mDeletingItems.contains(position)
                || (mUndo && mLastDeletingItems.contains(position));
    }

    @Override
    public void onAnimationStart(Animator animation) {
        if (mUndo) {
            mDeletingItems.clear();
            mLastDeletingItems.clear();
            mUndo = false;
        }
    }

    @Override
    public void onAnimationEnd(Animator animation) {
        if (mUndo && !mLastDeletingItems.isEmpty()) {
            // See if we have received all the animations we expected; if
            // so, call the listener and reset it.
            final int position = ((ConversationItemView) ((ObjectAnimator) animation).getTarget())
                    .getPosition();
            mLastDeletingItems.remove(position);
            if (mLastDeletingItems.isEmpty()) {
                if (mActionCompleteListener != null) {
                    mActionCompleteListener.onActionComplete();
                    mActionCompleteListener = null;
                }
                mUndo = false;
            }
        } else if (!mDeletingItems.isEmpty()) {
            // See if we have received all the animations we expected; if
            // so, call the listener and reset it.
            AnimatingItemView target = ((AnimatingItemView) ((ObjectAnimator) animation)
                    .getTarget());
            final int position = target.getData().position;
            mDeletingItems.remove(position);
            if (mDeletingItems.isEmpty()) {
                if (mActionCompleteListener != null) {
                    mActionCompleteListener.onActionComplete();
                    mActionCompleteListener = null;
                }
            }
        }
        // The view types have changed, since the animating views are gone.
        notifyDataSetChanged();
    }

    @Override
    public boolean areAllItemsEnabled() {
        // The animating positions are not enabled.
        return false;
    }

    @Override
    public boolean isEnabled(int position) {
        return !isPositionAnimating(position);
    }

    @Override
    public void onAnimationCancel(Animator animation) {
        onAnimationEnd(animation);
    }

    @Override
    public void onAnimationRepeat(Animator animation) {
    }

    @Override
    public void onUndoCancel() {
        mLastDeletingItems.clear();
    }

    public void showFooter() {
        if (!mShowFooter) {
            mShowFooter = true;
            notifyDataSetChanged();
        }
    }

    public void hideFooter() {
        if (mShowFooter) {
            mShowFooter = false;
            notifyDataSetChanged();
        }
    }

    public void addFooter(View footerView) {
        mFooter = footerView;
    }

    public void setFolder(Folder folder) {
        mFolder = folder;
    }
}
