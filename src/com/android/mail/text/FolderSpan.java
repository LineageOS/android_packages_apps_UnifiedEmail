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

package com.android.mail.text;

import android.graphics.Canvas;
import android.support.v4.text.BidiFormatter;
import android.text.TextUtils;

import com.android.mail.ui.FolderDisplayer;

/**
 * A replacement span to use when displaying folders in conversation view. Prevents a folder name
 * from wrapping mid-name, and ellipsizes very long folder names that can't fit on a single line.
 * Also ensures that folder text is drawn vertically centered within the background color chip.
 */
public class FolderSpan extends CenteredDrawableSpan {
    private final String mName;
    private final int mFgColor;
    private final int mBgColor;
    private final FolderDisplayer.FolderDrawableResources mRes;
    private final BidiFormatter mFormatter;
    private final FolderSpanDimensions mDim;

    public FolderSpan(String name, int fgColor, int bgColor,
            FolderDisplayer.FolderDrawableResources res, BidiFormatter formatter,
            FolderSpanDimensions dim) {
        super();

        mName = name;
        mFgColor = fgColor;
        mBgColor = bgColor;
        mRes = res;
        mFormatter = formatter;
        mDim = dim;
    }

    @Override
    protected int getDrawableWidth() {
        sWorkPaint.setTextSize(mRes.folderFontSize);
        return Math.min((int) sWorkPaint.measureText(mName) + 2 * mRes.folderHorizontalPadding,
                mDim.getMaxChipWidth());
    }

    @Override
    protected int getDrawableHeight() {
        return mRes.folderFontSize + 2 * mRes.folderVerticalPadding;
    }

    @Override
    protected void drawOnCanvas(Canvas canvas, float x, float y) {
        final int width = getDrawableWidth();
        String name = mName;
        if (width == mDim.getMaxChipWidth()) {
            name = TextUtils.ellipsize(mName, sWorkPaint, width - 2 * mRes.folderHorizontalPadding,
                    TextUtils.TruncateAt.MIDDLE).toString();
        }
        FolderDisplayer.drawFolder(canvas, x, y, width, getDrawableHeight(), name, mFgColor,
                mBgColor, mRes, mFormatter, sWorkPaint);
    }

    public static interface FolderSpanDimensions {
        int getMaxChipWidth();
    }
}
