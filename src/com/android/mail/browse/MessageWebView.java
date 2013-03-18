/*
 * Copyright (C) 2013 Google Inc.
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

package com.android.mail.browse;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.webkit.WebView;

import com.android.mail.utils.LogUtils;

/**
 * A WebView designed to live within a {@link MessageScrollView}.
 */
public class MessageWebView extends WebView implements MessageScrollView.Touchable {

    private boolean mTouched;

    public MessageWebView(Context c) {
        this(c, null);
    }

    public MessageWebView(Context c, AttributeSet attrs) {
        super(c, attrs);
    }

    @Override
    public boolean wasTouched() {
        return mTouched;
    }

    @Override
    public void clearTouched() {
        mTouched = false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        mTouched = true;
        final boolean handled = super.onTouchEvent(event);
        LogUtils.d(MessageScrollView.LOG_TAG,"OUT WebView.onTouch, returning handled=%s ev=%s",
                handled, event);
        return handled;
    }

}
