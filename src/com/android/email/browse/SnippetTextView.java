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

package com.android.email.browse;

import android.content.Context;
import android.text.Layout;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.widget.TextView;

/**
 * A special TextView that can use its geometry and styling to determine whether and where a string
 * will break.
 *
 */
public class SnippetTextView extends TextView {

    private int mMaxLines;

    public SnippetTextView(Context context) {
        this(context, null);
    }

    public SnippetTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void setMaxLines(int maxlines) {
        super.setMaxLines(maxlines);
        mMaxLines = maxlines;
    }

    public String getTextRemainder(final String text, int wSpec, int hSpec) {
        if (text == null || text.length() == 0) {
            return null;
        }

        String remainder;
        CharSequence savedText = getText();
        TextUtils.TruncateAt savedEllipsize = getEllipsize();
        setEllipsize(null);
        setText(text);

        Layout layout = getLayout();

        if (layout == null) {
            measure(wSpec, hSpec);
            layout = getLayout();
        }

        if (layout == null) {
            // Bail
            return text;
        }

        int lineCount = layout.getLineCount();
        if (lineCount <= mMaxLines) {
            remainder = null;
        } else {
            remainder = text.substring(layout.getLineStart(mMaxLines), text.length());
        }

        setEllipsize(savedEllipsize);
        setText(savedText);
        return remainder;
    }
}