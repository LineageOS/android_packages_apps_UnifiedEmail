/*
 * Copyright (C) 2010 The Android Open Source Project
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
import android.webkit.WebView;

import com.android.mail.ui.TitleBar;

/**
 * Extends webview to allow us to add a titlebar that moves with WebView scrolling.
 */
public class SecureConversationWebView extends WebView {
    private TitleBar mTitleBar;
    public SecureConversationWebView(Context context) {
        super(context);
    }

    public SecureConversationWebView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public SecureConversationWebView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public SecureConversationWebView(Context context, AttributeSet attrs, int defStyle,
            boolean privateBrowsing) {
        super(context, attrs, defStyle, privateBrowsing);
    }

    public void setTitleBar(TitleBar titleBar) {
        mTitleBar = titleBar;
    }
    public void onScrollChanged(int l, int t, int oldl, int oldt) {
        super.onScrollChanged(l, t, oldl, oldt);
        // Change position of title bar here
        mTitleBar.onScrollChanged();
    }
}
