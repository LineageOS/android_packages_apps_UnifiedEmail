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
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ListView;

import com.android.mail.R;
import com.android.mail.browse.ConversationItemView;
import com.android.mail.providers.Conversation;
import com.android.mail.ui.SwipeHelper.Callback;
import com.android.mail.ui.UndoBarView.UndoListener;
import com.google.common.collect.ImmutableList;

import java.util.Collection;

public class SwipeableListView extends ListView implements Callback {
    private SwipeHelper mSwipeHelper;
    private SwipeCompleteListener mSwipeCompleteListener;

    public SwipeableListView(Context context) {
        this(context, null);
    }

    public SwipeableListView(Context context, AttributeSet attrs) {
        this(context, attrs, -1);
    }

    public SwipeableListView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        float densityScale = getResources().getDisplayMetrics().density;
        mSwipeHelper = new SwipeHelper(SwipeHelper.X, this, densityScale, densityScale);
    }

    public void setSwipeCompleteListener(SwipeCompleteListener listener) {
        mSwipeCompleteListener = listener;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        return mSwipeHelper.onInterceptTouchEvent(ev) ||
            super.onInterceptTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        return mSwipeHelper.onTouchEvent(ev) ||
            super.onTouchEvent(ev);
    }

    @Override
    public View getChildAtPosition(MotionEvent ev) {
        // find the view under the pointer, accounting for GONE views
        final int count = getChildCount();
        int y = 0;
        int touchY = (int) ev.getY();
        int childIdx = 0;
        View slidingChild;
        for (; childIdx < count; childIdx++) {
            slidingChild = getChildAt(childIdx);
            if (slidingChild.getVisibility() == GONE) {
                continue;
            }
            y += slidingChild.getMeasuredHeight();
            if (touchY < y) return slidingChild;
        }
        return null;
    }

    @Override
    public View getChildContentView(View v) {
        return v;
    }

    @Override
    public boolean canChildBeDismissed(View v) {
        return v instanceof ConversationItemView;
    }

    @Override
    public void onChildDismissed(View v) {
        if (v instanceof ConversationItemView) {
            Conversation c = ((ConversationItemView) v).getConversation();
            c.position = getPositionForView(v);
            AnimatedAdapter adapter = ((AnimatedAdapter) getAdapter());
            final ImmutableList<Conversation> conversations = ImmutableList.of(c);
            adapter.delete(conversations, new ActionCompleteListener() {
                @Override
                public void onActionComplete() {
                    mSwipeCompleteListener.onSwipeComplete(conversations);
                }
            });
        }
    }

    @Override
    public void onBeginDrag(View v) {
        // We do this so the underlying ScrollView knows that it won't get
        // the chance to intercept events anymore
        requestDisallowInterceptTouchEvent(true);
        v.setActivated(true);
    }

    @Override
    public void onDragCancelled(View v) {
        v.setActivated(false);
    }

    public interface SwipeCompleteListener {
        public void onSwipeComplete(Collection<Conversation> conversations);
    }
}
