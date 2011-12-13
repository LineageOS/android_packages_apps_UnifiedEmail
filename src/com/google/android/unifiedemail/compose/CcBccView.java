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
package com.google.android.unifiedemail.compose;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.RelativeLayout;
import com.google.android.unifiedemail.R;

public class CcBccView extends RelativeLayout {

    public CcBccView(Context context) {
        super(context, null);
    }

    public CcBccView(Context context, AttributeSet attrs) {
        this(context, attrs, -1);
    }

    public CcBccView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        LayoutInflater.from(context).inflate(R.layout.cc_bcc_view, this);
    }

    public void show() {
        View cc = findViewById(R.id.cc_content);
        View bcc = findViewById(R.id.bcc_content);
        boolean ccWasAlreadyShown = cc.isShown();
        cc.setVisibility(View.VISIBLE);
        bcc.setVisibility(View.VISIBLE);

        Resources res = getResources();
        // First, have the height of the wrapper grow to fit the fields.
        ObjectAnimator heightAnimator = ObjectAnimator.ofInt(this, "ccBccHeight",
                getLayoutParams().height, bcc.getLayoutParams().height
                        + cc.getLayoutParams().height);
        heightAnimator.setDuration(res.getInteger(R.integer.expand_cc_bcc_dur));

        // Then, have cc/ bcc fade in
        int fadeDuration = res.getInteger(R.integer.fadein_cc_bcc_dur);
        ObjectAnimator bccAnimator = ObjectAnimator.ofFloat(bcc, "alpha", 0, 1);
        bccAnimator.setDuration(fadeDuration);

        AnimatorSet transitionSet = new AnimatorSet();
        Animator fadeAnimation;
        if (!ccWasAlreadyShown) {
            ObjectAnimator ccAnimator = ObjectAnimator.ofFloat(cc, "alpha", 0, 1);
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
