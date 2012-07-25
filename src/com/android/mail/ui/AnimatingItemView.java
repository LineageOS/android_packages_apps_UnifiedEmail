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
import android.animation.ObjectAnimator;
import android.content.Context;
import android.util.AttributeSet;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;

import com.android.mail.browse.ConversationItemViewCoordinates;
import com.android.mail.providers.Conversation;

public class AnimatingItemView extends LinearLayout {
    public AnimatingItemView(Context context) {
        super(context);
    }

    public AnimatingItemView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    private Conversation mData;
    private ObjectAnimator mAnimator;
    private int mAnimatedHeight = -1;

    /**
     * Start the animation on an animating view.
     * @param item the conversation to animate
     * @param listener the method to call when the animation is done
     * @param undo true if an operation is being undone. We animate the item away during delete.
     * Undoing populates the item.
     */
    public void startAnimation(ViewMode viewMode, AnimatorListener listener) {
        int minHeight = ConversationItemViewCoordinates.getMinHeight(getContext(), viewMode);
        setMinimumHeight(minHeight);
        final int start = minHeight;
        final int end = 0;

        mAnimator = ObjectAnimator.ofInt(this, "animatedHeight", start, end);
        mAnimatedHeight = start;
        mAnimator.setInterpolator(new DecelerateInterpolator(2.0f));
        mAnimator.setDuration(300);
        mAnimator.addListener(listener);
        mAnimator.start();
    }

    public void setData(Conversation conversation) {
        mData = conversation;
    }

    public Conversation getData() {
        return mData;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (mAnimatedHeight != -1) {
            setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), mAnimatedHeight);
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
        return;
    }

    // Used by animator
    @SuppressWarnings("unused")
    public void setAnimatedHeight(int height) {
        mAnimatedHeight = height;
        requestLayout();
    }

}
