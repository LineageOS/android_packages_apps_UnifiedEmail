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

package com.android.mail.browse;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.webkit.WebView;

import com.android.mail.utils.LogUtils;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

public class ConversationWebView extends WebView implements ScrollNotifier {

    private final Set<ScrollListener> mScrollListeners =
            new CopyOnWriteArraySet<ScrollListener>();

    /**
     * True when WebView is handling a touch-- in between POINTER_DOWN and
     * POINTER_UP/POINTER_CANCEL.
     */
    private boolean mHandlingTouch;

    private static final String LOG_TAG = new LogUtils().getLogTag();

    public ConversationWebView(Context c) {
        this(c, null);
    }

    public ConversationWebView(Context c, AttributeSet attrs) {
        super(c, attrs);
    }

    @Override
    public void addScrollListener(ScrollListener l) {
        mScrollListeners.add(l);
    }

    @Override
    public void removeScrollListener(ScrollListener l) {
        mScrollListeners.remove(l);
    }

    @Override
    protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        super.onScrollChanged(l, t, oldl, oldt);

        for (ScrollListener listener : mScrollListeners) {
            listener.onNotifierScroll(l, t);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        final int action = ev.getActionMasked();

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                mHandlingTouch = true;
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                LogUtils.d(LOG_TAG, "WebView disabling intercepts: POINTER_DOWN");
                requestDisallowInterceptTouchEvent(true);
                break;
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                mHandlingTouch = false;
                break;
        }

        return super.onTouchEvent(ev);
    }

    public boolean isHandlingTouch() {
        return mHandlingTouch;
    }

}
