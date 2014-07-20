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
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;

import com.android.oldbitmap.BitmapCache;
import com.android.oldbitmap.DecodeTask.Request;
import com.android.oldbitmap.ReusableBitmap;
import com.android.mail.R;
import com.android.mail.bitmap.ContactResolver.ContactDrawableInterface;

/**
 * A drawable that encapsulates all the functionality needed to display a contact image,
 * including request creation/cancelling and data unbinding/re-binding. While no contact images
 * can be shown, a default letter tile will be shown instead.
 *
 * <p/>
 * The actual contact resolving and decoding is handled by {@link ContactResolver}.
 */
public class ContactDrawable extends Drawable implements ContactDrawableInterface {

    private final BitmapCache mCache;
    private final ContactResolver mContactResolver;

    private ContactRequest mContactRequest;
    private ReusableBitmap mBitmap;
    private final Paint mPaint;
    private int mScale;

    /** Letter tile */
    private static TypedArray sColors;
    private static int sDefaultColor;
    private static int sTileLetterFontSize;
    private static int sTileLetterFontSizeSmall;
    private static int sTileFontColor;
    private static Bitmap DEFAULT_AVATAR;
    /** Reusable components to avoid new allocations */
    private static final Paint sPaint = new Paint();
    private static final Rect sRect = new Rect();
    private static final char[] sFirstChar = new char[1];

    /** This should match the total number of colors defined in colors.xml for letter_tile_color */
    private static final int NUM_OF_TILE_COLORS = 12;

    public ContactDrawable(final Resources res, final BitmapCache cache,
            final ContactResolver contactResolver) {
        mCache = cache;
        mContactResolver = contactResolver;
        mPaint = new Paint();
        mPaint.setFilterBitmap(true);
        mPaint.setDither(true);

        if (sColors == null) {
            sColors = res.obtainTypedArray(R.array.letter_tile_colors);
            sDefaultColor = res.getColor(R.color.letter_tile_default_color);
            sTileLetterFontSize = res.getDimensionPixelSize(R.dimen.tile_letter_font_size);
            sTileLetterFontSizeSmall = res
                    .getDimensionPixelSize(R.dimen.tile_letter_font_size_small);
            sTileFontColor = res.getColor(R.color.letter_tile_font_color);
            DEFAULT_AVATAR = BitmapFactory.decodeResource(res, R.drawable.ic_generic_man);

            sPaint.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
            sPaint.setTextAlign(Align.CENTER);
            sPaint.setAntiAlias(true);
        }
    }

    @Override
    public void draw(final Canvas canvas) {
        final Rect bounds = getBounds();
        if (!isVisible() || bounds.isEmpty()) {
            return;
        }

        if (mBitmap != null && mBitmap.bmp != null) {
            // Draw sender image.
            drawBitmap(mBitmap.bmp, mBitmap.getLogicalWidth(), mBitmap.getLogicalHeight(), canvas);
        } else {
            // Draw letter tile.
            drawLetterTile(canvas);
        }
    }

    /**
     * Draw the bitmap onto the canvas at the current bounds taking into account the current scale.
     */
    private void drawBitmap(final Bitmap bitmap, final int width, final int height,
            final Canvas canvas) {
        final Rect bounds = getBounds();

        if (mScale != ContactGridDrawable.SCALE_TYPE_HALF) {
            sRect.set(0, 0, width, height);
        } else {
            // For skinny bounds, draw the middle two quarters.
            sRect.set(width / 4, 0, width / 4 * 3, height);
        }
        canvas.drawBitmap(bitmap, sRect, bounds, mPaint);
    }

    private void drawLetterTile(final Canvas canvas) {
        if (mContactRequest == null) {
            return;
        }

        // Draw background color.
        final String email = mContactRequest.getEmail();
        sPaint.setColor(pickColor(email));
        sPaint.setAlpha(mPaint.getAlpha());
        canvas.drawRect(getBounds(), sPaint);

        // Draw letter/digit or generic avatar.
        final String displayName = mContactRequest.getDisplayName();
        final char firstChar = displayName.charAt(0);
        final Rect bounds = getBounds();
        if (isEnglishLetterOrDigit(firstChar)) {
            // Draw letter or digit.
            sFirstChar[0] = Character.toUpperCase(firstChar);
            sPaint.setTextSize(mScale == ContactGridDrawable.SCALE_TYPE_ONE ? sTileLetterFontSize
                    : sTileLetterFontSizeSmall);
            sPaint.getTextBounds(sFirstChar, 0, 1, sRect);
            sPaint.setColor(sTileFontColor);
            canvas.drawText(sFirstChar, 0, 1, bounds.centerX(),
                    bounds.centerY() + sRect.height() / 2, sPaint);
        } else {
            drawBitmap(DEFAULT_AVATAR, DEFAULT_AVATAR.getWidth(), DEFAULT_AVATAR.getHeight(),
                    canvas);
        }
    }

    private static int pickColor(final String email) {
        // String.hashCode() implementation is not supposed to change across java versions, so
        // this should guarantee the same email address always maps to the same color.
        // The email should already have been normalized by the ContactRequest.
        final int color = Math.abs(email.hashCode()) % NUM_OF_TILE_COLORS;
        return sColors.getColor(color, sDefaultColor);
    }

    private static boolean isEnglishLetterOrDigit(final char c) {
        return ('A' <= c && c <= 'Z') || ('a' <= c && c <= 'z') || ('0' <= c && c <= '9');
    }

    @Override
    public void setAlpha(final int alpha) {
        mPaint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(final ColorFilter cf) {
        mPaint.setColorFilter(cf);
    }

    @Override
    public int getOpacity() {
        return 0;
    }

    public void setDecodeDimensions(final int decodeWidth, final int decodeHeight) {
        mCache.setPoolDimensions(decodeWidth, decodeHeight);
    }

    public void setScale(final int scale) {
        mScale = scale;
    }

    public void unbind() {
        setImage(null);
    }

    public void bind(final String name, final String email) {
        setImage(new ContactRequest(name, email));
    }

    private void setImage(final ContactRequest contactRequest) {
        if (mContactRequest != null && mContactRequest.equals(contactRequest)) {
            return;
        }

        if (mBitmap != null) {
            mBitmap.releaseReference();
            mBitmap = null;
        }

        mContactResolver.remove(mContactRequest, this);
        mContactRequest = contactRequest;

        if (contactRequest == null) {
            invalidateSelf();
            return;
        }

        final ReusableBitmap cached = mCache.get(contactRequest, true /* incrementRefCount */);
        if (cached != null) {
            setBitmap(cached);
        } else {
            decode();
        }
    }

    private void setBitmap(final ReusableBitmap bmp) {
        if (mBitmap != null && mBitmap != bmp) {
            mBitmap.releaseReference();
        }
        mBitmap = bmp;
        invalidateSelf();
    }

    private void decode() {
        if (mContactRequest == null) {
            return;
        }
        // Add to batch.
        mContactResolver.add(mContactRequest, this);
    }

    public void onDecodeComplete(final Request key, final ReusableBitmap result) {
        final ContactRequest request = (ContactRequest) key;
        // Remove from batch.
        mContactResolver.remove(request, this);
        if (request.equals(mContactRequest)) {
            setBitmap(result);
        } else {
            // if the requests don't match (i.e. this request is stale), decrement the
            // ref count to allow the bitmap to be pooled
            if (result != null) {
                result.releaseReference();
            }
        }
    }
}
