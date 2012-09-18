/*
 * Copyright (C) 2012 The Android Open Source Project
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
import android.animation.Animator.AnimatorListener;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.AnimationUtils;
import android.view.animation.DecelerateInterpolator;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;

import com.android.mail.R;
import com.android.mail.browse.SecureConversationViewFragment;

/**
 * TitleBar is used to display conversation and message info for the
 * SecureConversationViewFragment.
 */
public class TitleBar extends RelativeLayout {

    public TitleBar(Context context) {
        super(context);
    }

    public TitleBar(Context context, AttributeSet attr) {
        super(context, attr);
    }

    private static final float ANIM_TITLEBAR_DECELERATE = 2.5f;

    private WebView mWebView;

    // state
    private boolean mShowing;
    private boolean mSkipTitleBarAnimations;
    private Animator mTitleBarAnimator;

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    public void setWebView(WebView view) {
        mWebView = view;
    }

    void setSkipTitleBarAnimations(boolean skip) {
        mSkipTitleBarAnimations = skip;
    }

    void setupTitleBarAnimator(Animator animator) {
        Resources res = getContext().getResources();
        int duration = res.getInteger(R.integer.title_bar_show_animation_duration);
        animator.setInterpolator(new DecelerateInterpolator(ANIM_TITLEBAR_DECELERATE));
        animator.setDuration(duration);
    }

    void show() {
        cancelTitleBarAnimation(false);
        if (mSkipTitleBarAnimations) {
            this.setVisibility(View.VISIBLE);
            this.setTranslationY(0);
        } else {
            int visibleHeight = getVisibleTitleHeight();
            float startPos = (-getMeasuredHeight() + visibleHeight);
            if (getTranslationY() != 0) {
                startPos = Math.max(startPos, getTranslationY());
            }
            mTitleBarAnimator = ObjectAnimator.ofFloat(this, "translationY", startPos, 0);
            setupTitleBarAnimator(mTitleBarAnimator);
            mTitleBarAnimator.start();
        }
        mShowing = true;
    }

    void hide() {
        if (!mSkipTitleBarAnimations) {
            cancelTitleBarAnimation(false);
            int visibleHeight = getVisibleTitleHeight();
            mTitleBarAnimator = ObjectAnimator.ofFloat(this, "translationY", getTranslationY(),
                    (-getMeasuredHeight() + visibleHeight));
            mTitleBarAnimator.addListener(mHideTileBarAnimatorListener);
            setupTitleBarAnimator(mTitleBarAnimator);
            mTitleBarAnimator.start();
        } else {
            onScrollChanged();
        }
        mShowing = false;
    }

    void cancelTitleBarAnimation(boolean reset) {
        if (mTitleBarAnimator != null) {
            mTitleBarAnimator.cancel();
            mTitleBarAnimator = null;
        }
        if (reset) {
            setTranslationY(0);
        }
    }

    private AnimatorListener mHideTileBarAnimatorListener = new AnimatorListener() {

        @Override
        public void onAnimationStart(Animator animation) {
        }

        @Override
        public void onAnimationRepeat(Animator animation) {
        }

        @Override
        public void onAnimationEnd(Animator animation) {
            // update position
            onScrollChanged();
        }

        @Override
        public void onAnimationCancel(Animator animation) {
        }
    };

    private int getVisibleTitleHeight() {
        return getMeasuredHeight() - Math.max(0, mWebView.getScrollY());
    }

    public void onScrollChanged() {
        if (!mShowing) {
            setTranslationY(getVisibleTitleHeight() - getMeasuredHeight());
        }
    }

    public String getHtmlContent() {
        return getContext().getResources().getString(R.string.title_bar_html, getMeasuredHeight());
    }

}
