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

package com.android.mail.text;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.TextPaint;
import android.text.style.ReplacementSpan;

/**
 * A simple replacement span that draws the provided drawable centered relative to the text.
 * Since this class uses a static paint, be advised that any drawing/usage of it should only be done
 * on the main thread (e.g. notifications) to prevent the paint from being modified by two threads.
 */
public abstract class CenteredDrawableSpan extends ReplacementSpan {
    protected static final TextPaint sWorkPaint = new TextPaint();

    protected abstract int getDrawableWidth();
    protected abstract int getDrawableHeight();
    protected abstract void drawOnCanvas(Canvas canvas, float x, float y);

    @Override
    public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
        return getDrawableWidth();
    }

    @Override
    public void draw(Canvas canvas, CharSequence charSequence, int start, int end, float x, int top,
            int baseline, int bottom, Paint paint) {
        final Paint.FontMetricsInt fm = paint.getFontMetricsInt();
        final float topRatio = (float)
                (fm.ascent - fm.top) / (fm.ascent - fm.top + fm.bottom - fm.descent);
        final int transY = (int) (baseline + fm.top +
                (fm.bottom - fm.top - getDrawableHeight()) * topRatio);
        drawOnCanvas(canvas, x, transY);
    }
}
