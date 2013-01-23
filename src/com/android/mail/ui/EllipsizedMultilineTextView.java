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

package com.android.mail.ui;

import android.content.Context;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.widget.TextView;

/**
 * A special MultiLine TextView that will apply ellipsize logic to only the last
 * line of text, such that the last line may be shorter than any previous lines.
 */
public class EllipsizedMultilineTextView extends TextView {

    public static final int ALL_AVAILABLE = -1;
    private int mMaxLines;
    private int mLastWSpec;
    private int mLastHSpec;

    public EllipsizedMultilineTextView(Context context) {
        this(context, null);
    }

    public EllipsizedMultilineTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void setMaxLines(int maxlines) {
        super.setMaxLines(maxlines);
        mMaxLines = maxlines;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        mLastWSpec = widthMeasureSpec;
        mLastHSpec = heightMeasureSpec;
    }

    /**
     * Ellipsize just the last line of text in this view and set the text to the
     * new ellipsized value.
     * @param text Text to set and ellipsize
     * @param avail available width in pixels for the last line
     * @param paint Paint that has the proper properties set to measure the text
     *            for this view
     */
    public void setText(final CharSequence text, int avail) {
        if (text == null || text.length() == 0) {
            return;
        }

        setEllipsize(null);
        setText(text, TextView.BufferType.SPANNABLE);

        if (avail == ALL_AVAILABLE) {
            return;
        }
        Layout layout = getLayout();

        if (layout == null) {
            measure(mLastWSpec, mLastHSpec);
            layout = getLayout();
        }

        if (layout == null) {
            // Bail
            return;
        }

        CharSequence remainder;
        SpannableStringBuilder builder = new SpannableStringBuilder();
        int lineCount = layout.getLineCount();
        if (lineCount <= mMaxLines) {
            remainder = null;
        } else {
            remainder = TextUtils.ellipsize(
                    text.subSequence(layout.getLineStart(mMaxLines - 1), text.length()),
                    getPaint(), avail, TextUtils.TruncateAt.END);
        }

        builder.append(text, 0, layout.getLineStart(mMaxLines - 1));
        if (!TextUtils.isEmpty(remainder)) {
            builder.append(remainder);
        }
        setText(builder, TextView.BufferType.SPANNABLE);
    }
}