/**
 * Copyright (c) 2011, Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.mail.compose;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.RelativeLayout;
import com.android.mail.R;

public class CcBccView extends RelativeLayout {

    private final View mCc;
    private final View mBcc;

    public CcBccView(Context context) {
        this(context, null);
    }

    public CcBccView(Context context, AttributeSet attrs) {
        this(context, attrs, -1);
    }

    public CcBccView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        LayoutInflater.from(context).inflate(R.layout.cc_bcc_view, this);
        mCc = findViewById(R.id.cc_content);
        mBcc = findViewById(R.id.bcc_content);
    }

    public void show(boolean animate, boolean showCc, boolean showBcc) {
        boolean ccWasAlreadyShown = mCc.isShown();
        mCc.setVisibility(showCc ? View.VISIBLE : View.GONE);
        mBcc.setVisibility(showBcc ? View.VISIBLE : View.GONE);

        if (animate) {
            animate(showCc, showBcc, ccWasAlreadyShown);
        } else {
            int ccHeight = showCc ? mCc.getLayoutParams().height : 0;
            int bccHeight = showBcc ? mBcc.getLayoutParams().height : 0;
            getLayoutParams().height = ccHeight + bccHeight;
            if (showCc) {
                mCc.setAlpha(1);
            }
            if (showBcc) {
                mBcc.setAlpha(1);
            }
            requestLayout();
        }
    }

    private void animate(Boolean showCc, boolean showBcc, boolean ccWasAlreadyShown) {
        Resources res = getResources();
        // First, have the height of the wrapper grow to fit the fields.
        int ccHeight = showCc ? mCc.getLayoutParams().height : 0;
        int bccHeight = showBcc ? mBcc.getLayoutParams().height : 0;
        ObjectAnimator heightAnimator = ObjectAnimator.ofInt(this, "ccBccHeight",
                getLayoutParams().height, ccHeight + bccHeight);
        heightAnimator.setDuration(res.getInteger(R.integer.expand_cc_bcc_dur));

        // Then, have cc/ bcc fade in
        int fadeDuration = res.getInteger(R.integer.fadein_cc_bcc_dur);
        ObjectAnimator bccAnimator = ObjectAnimator.ofFloat(mBcc, "alpha", 0, 1);
        bccAnimator.setDuration(fadeDuration);

        AnimatorSet transitionSet = new AnimatorSet();
        Animator fadeAnimation;
        if (!ccWasAlreadyShown) {
            ObjectAnimator ccAnimator = ObjectAnimator.ofFloat(mCc, "alpha", 0, 1);
            ccAnimator.setDuration(fadeDuration);
            fadeAnimation = new AnimatorSet();
            ((AnimatorSet) fadeAnimation).playTogether(ccAnimator, bccAnimator);
        } else {
            fadeAnimation = bccAnimator;
        }
        transitionSet.playSequentially(heightAnimator, fadeAnimation);
        transitionSet.start();
    }
}
