/*
 * Copyright (C) 2014 Google Inc.
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

package com.android.mail.utils;

import android.text.Spannable;
import android.text.Spanned;
import android.text.style.URLSpan;
import android.widget.TextView;

import com.android.mail.browse.UrlSpan;

/**
 * Utility class for styling UI.
 */
public class StyleUtils {

    /**
     * Removes any {@link android.text.style.URLSpan}s from the text view
     * and replaces them with their non-underline version {@link com.android.mail.browse.UrlSpan}.
     */
    public static void stripUnderlines(TextView textView) {
        final Spannable spannable = (Spannable) textView.getText();
        final URLSpan[] urls = textView.getUrls();

        for (URLSpan span : urls) {
            final int start = spannable.getSpanStart(span);
            final int end = spannable.getSpanEnd(span);
            spannable.removeSpan(span);
            span = new UrlSpan(span.getURL());
            spannable.setSpan(span, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }
}
