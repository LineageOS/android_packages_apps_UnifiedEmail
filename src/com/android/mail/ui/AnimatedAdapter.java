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
import android.util.SparseArray;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.SimpleCursorAdapter;

import com.android.mail.browse.ConversationCursor;
import com.android.mail.browse.ConversationItemView;
import com.android.mail.providers.Account;
import com.android.mail.providers.Conversation;
import com.android.mail.providers.UIProvider;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;

public class AnimatedAdapter extends SimpleCursorAdapter implements
        android.animation.Animator.AnimatorListener {
    private HashSet<Integer> mDeletingItems = new HashSet<Integer>();
    private Account mSelectedAccount;
    private Context mContext;
    private ConversationSelectionSet mBatchConversations;
    private ActionCompleteListener mActionCompleteListener;
    private SparseArray<AnimatingItemView> mAnimatingViews = new SparseArray<AnimatingItemView>();
    private boolean mUndo = false;

    public AnimatedAdapter(Context context, int textViewResourceId, ConversationCursor cursor,
            ConversationSelectionSet batch, Account account) {
        super(context, textViewResourceId, cursor, UIProvider.CONVERSATION_PROJECTION, null, 0);
        mContext = context;
        mBatchConversations = batch;
        mSelectedAccount = account;
    }

    public void setUndo(boolean state) {
        mUndo = state;
        if (mUndo) {
            mDeletingItems.clear();
            mDeletingItems.addAll(mLastDeletingItems);
            mActionCompleteListener = new ActionCompleteListener() {
                @Override
                public void onActionComplete() {
                    notifyDataSetChanged();
                }};
        }
    }

    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        ConversationItemView view = new ConversationItemView(context, mSelectedAccount.name);
        return view;
    }

    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        if (!isPositionAnimating(view)) {
            ((ConversationItemView) view).bind(cursor, null, mSelectedAccount.name, null,
                    new ViewMode(mContext), mBatchConversations);
        }
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }

    @Override
    public int getViewTypeCount() {
        // Our normal view and the animating (not recycled) view
        return 2;
    }

    @Override
    public int getItemViewType(int position) {
        // Don't recycle animating views
        if (isPositionAnimating(position)) {
            return AdapterView.ITEM_VIEW_TYPE_IGNORE;
        }
        return 0;
    }

    public void delete(Collection<Conversation> conversations,
            ActionCompleteListener listener) {
        // Animate out the positions.
        // Call when all the animations are complete.
        ArrayList<Integer> positions = new ArrayList<Integer>();
        for (Conversation c : conversations) {
            positions.add(c.position);
        }
        mActionCompleteListener = listener;
        delete(positions);
    }

    private ArrayList<Integer> mLastDeletingItems = new ArrayList<Integer>();

    public void delete(ArrayList<Integer> deletedRows) {
        // Clear out any remaining items and add the new ones
        mLastDeletingItems.clear();
        mLastDeletingItems.addAll(deletedRows);
        mDeletingItems.addAll(deletedRows);
        // TODO(viki): Rather than notifying for a full data set change, perhaps we can mark
        // only the affected conversations?
        notifyDataSetChanged();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (isPositionAnimating(position)) {
            return getAnimatingView(position, convertView, parent);
        }
        return super.getView(position, convertView, parent);
    }

    private View getAnimatingView(int position, View convertView, ViewGroup parent) {
        AnimatingItemView view = mAnimatingViews.get(position);
        if (view == null) {
            Conversation conversation = Conversation.from((ConversationCursor) getItem(position));
            conversation.position = position;
            view = new AnimatingItemView(mContext, conversation, this, mUndo);
        }
        return view;
    }

    private boolean isPositionAnimating(int position) {
        return mDeletingItems.contains(position);
    }

    private boolean isPositionAnimating(View view) {
        return (view instanceof AnimatingItemView);
    }

    @Override
    public void onAnimationStart(Animator animation) {
        // TODO Auto-generated method stub
    }

    @Override
    public void onAnimationEnd(Animator animation) {
        if (!mDeletingItems.isEmpty()) {
            // See if we have received all the animations we expected; if so,
            // call the listener and reset it.
            int position = ((AnimatingItemView)
                    ((ObjectAnimator) animation).getTarget()).getData().position;
            mDeletingItems.remove(position);
            if (mDeletingItems.isEmpty()) {
                mAnimatingViews.clear();
                if (mUndo) {
                    mLastDeletingItems.clear();
                    mUndo = false;
                }
                if (mActionCompleteListener != null) {
                    mActionCompleteListener.onActionComplete();
                    mActionCompleteListener = null;
                }
            }
        }
    }

    @Override
    public void onAnimationCancel(Animator animation) {
        onAnimationEnd(animation);
    }

    @Override
    public void onAnimationRepeat(Animator animation) {
        // TODO Auto-generated method stub
    }
}
