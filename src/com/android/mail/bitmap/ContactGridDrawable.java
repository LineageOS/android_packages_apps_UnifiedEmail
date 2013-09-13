/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.mail.bitmap;

import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Rect;

import com.android.bitmap.BitmapCache;
import com.android.mail.R;

/**
 * A 2x2 grid of contact drawables. Adds horizontal and vertical dividers.
 */
public class ContactGridDrawable extends CompositeDrawable<ContactDrawable> {

    public static final int SCALE_TYPE_ONE = 0;
    public static final int SCALE_TYPE_HALF = 1;
    public static final int SCALE_TYPE_QUARTER = 2;

    private static final int MAX_CONTACTS_COUNT = 4;

    private ContactResolver mContactResolver;
    private BitmapCache mCache;
    private final Resources mRes;
    private Paint mPaint;

    private static int sDividerWidth = -1;
    private static int sDividerColor;

    public ContactGridDrawable(final Resources res) {
        super(MAX_CONTACTS_COUNT);

        if (sDividerWidth == -1) {
            sDividerWidth = res.getDimensionPixelSize(R.dimen.tile_divider_width);
            sDividerColor = res.getColor(R.color.tile_divider_color);
        }

        mRes = res;
        mPaint = new Paint();
        mPaint.setStrokeWidth(sDividerWidth);
        mPaint.setColor(sDividerColor);
    }

    @Override
    protected ContactDrawable createDivisionDrawable(final int i) {
        final ContactDrawable drawable = new ContactDrawable(mRes, mCache, mContactResolver);
        drawable.setScale(calculateScale(i));
        return drawable;
    }

    @Override
    public void setCount(final int count) {
        super.setCount(count);

        for (int i = 0; i < mCount; i++) {
            final ContactDrawable drawable = mDrawables.get(i);
            if (drawable != null) {
                drawable.setScale(calculateScale(i));
            }
        }
    }

    /**
     * Given which section a drawable is in, calculate its scale based on the current total count.
     * @param i The section, indexed by 0.
     */
    private int calculateScale(final int i) {
        switch (mCount) {
            case 1:
                // 1 bitmap: passthrough
                return SCALE_TYPE_ONE;
            case 2:
                // 2 bitmaps split vertically
                return SCALE_TYPE_HALF;
            case 3:
                // 1st is tall on the left, 2nd/3rd stacked vertically on the right
                return i == 0 ? SCALE_TYPE_HALF : SCALE_TYPE_QUARTER;
            case 4:
                // 4 bitmaps in a 2x2 grid
                return SCALE_TYPE_QUARTER;
            default:
                return SCALE_TYPE_ONE;
        }
    }

    @Override
    public void draw(final Canvas canvas) {
        super.draw(canvas);

        final Rect bounds = getBounds();
        if (!isVisible() || bounds.isEmpty()) {
            return;
        }

        // Draw horizontal and vertical dividers.
        switch (mCount) {
            case 1:
                // 1 bitmap: passthrough
                break;
            case 2:
                // 2 bitmaps split vertically
                canvas.drawLine(bounds.centerX(), bounds.top, bounds.centerX(), bounds.bottom,
                        mPaint);
                break;
            case 3:
                // 1st is tall on the left, 2nd/3rd stacked vertically on the right
                canvas.drawLine(bounds.centerX(), bounds.top, bounds.centerX(), bounds.bottom,
                        mPaint);
                canvas.drawLine(bounds.centerX(), bounds.centerY(), bounds.right, bounds.centerY(),
                        mPaint);
                break;
            case 4:
                // 4 bitmaps in a 2x2 grid
                canvas.drawLine(bounds.centerX(), bounds.top, bounds.centerX(), bounds.bottom,
                        mPaint);
                canvas.drawLine(bounds.left, bounds.centerY(), bounds.right, bounds.centerY(),
                        mPaint);
                break;
        }
    }

    @Override
    public void setAlpha(final int alpha) {
        super.setAlpha(alpha);
        final int old = mPaint.getAlpha();
        mPaint.setAlpha(alpha);
        if (alpha != old) {
            invalidateSelf();
        }
    }

    @Override
    public void setColorFilter(final ColorFilter cf) {
        super.setColorFilter(cf);
        mPaint.setColorFilter(cf);
        invalidateSelf();
    }

    public void setBitmapCache(final BitmapCache cache) {
        mCache = cache;
    }

    public void setContactResolver(final ContactResolver contactResolver) {
        mContactResolver = contactResolver;
    }
}
