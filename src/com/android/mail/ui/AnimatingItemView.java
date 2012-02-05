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

import com.android.mail.R;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.util.AttributeSet;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;

import com.android.mail.browse.ConversationItemView;
import com.android.mail.providers.Conversation;

public class AnimatingItemView extends LinearLayout {
    private Conversation mData;
    private ObjectAnimator mAnimator;
    private int mAnimatedHeight;

    public AnimatingItemView(Context context) {
        this(context, null);
    }

    public AnimatingItemView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AnimatingItemView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public AnimatingItemView(Context context, Conversation item,
            android.animation.Animator.AnimatorListener listener, boolean undo) {
        this(context);
        mData = item;
        setMinimumHeight(140);
        int start = undo ? 0 : 140;
        int end = undo ? 140: 0;
        if (!undo) {
            setBackgroundResource(R.drawable.list_activated_holo);
        }
        mAnimator = ObjectAnimator.ofInt(this, "animatedHeight", start, end);
        mAnimatedHeight = start;
        mAnimator.setInterpolator(new DecelerateInterpolator(2.0f));
        mAnimator.setDuration(300);
        mAnimator.addListener(listener);
        mAnimator.start();
    }

    public Conversation getData() {
        return mData;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), mAnimatedHeight);
        return;
    }

    // Used by animator
    @SuppressWarnings("unused")
    public void setAnimatedHeight(int height) {
        mAnimatedHeight = height;
        requestLayout();
    }

}
