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

package com.android.mail.photomanager;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.LruCache;
import android.util.TypedValue;

import com.android.mail.R;
import com.android.mail.photomanager.ContactPhotoManager.DefaultImageProvider;
import com.android.mail.ui.DividedImageCanvas;
import com.google.common.base.Objects;

/**
 * LetterTileProvider is an implementation of the DefaultImageProvider. When no
 * matching contact photo is found, and there is a supplied displayName or email
 * address whose first letter corresponds to an English alphabet letter (or
 * number), this method creates a bitmap with the letter in the center of a
 * tile. If there is no English alphabet character (or digit), it creates a
 * bitmap with the default contact avatar.
 */
public class LetterTileProvider extends DefaultImageProvider {
    private Bitmap mDefaultBitmap;
    private final LruCache<Integer, Bitmap> mTileBitmapCache;
    private static int sTilePaddingLeftHalf;
    private static int sTilePaddingBottomHalf;
    private static int sTilePaddingLeftQuarter;
    private static int sTilePaddingBottomQuarter;
    private static int sTilePaddingBottom;
    private static int sTilePaddingLeft;
    private static int sTileLetterFontSize = -1;
    private static int sTileLetterFontSizeSmall;
    private static int sTileLetterFontSizeMed;
    private static int sTileColor;
    private static int sTileFontColor;
    private static TextPaint sPaint = new TextPaint();
    private static int DEFAULT_AVATAR_DRAWABLE = R.drawable.ic_contact_picture;
    private static final Pattern ALPHABET = Pattern.compile("^[a-zA-Z]+$");

    public LetterTileProvider() {
        super();
        final float cacheSizeAdjustment =
                (MemoryUtils.getTotalMemorySize() >= MemoryUtils.LARGE_RAM_THRESHOLD) ?
                        1.0f : 0.5f;
        final int bitmapCacheSize = (int) (cacheSizeAdjustment * 26);
        mTileBitmapCache = new LruCache<Integer, Bitmap>(bitmapCacheSize);
    }

    @Override
    public void applyDefaultImage(String displayName, String address, DividedImageCanvas view,
            int extent) {
        Bitmap bitmap = null;
        final String display = !TextUtils.isEmpty(displayName) ? displayName : address;
        final String firstChar = display.substring(0, 1);
        // If its a valid english alphabet letter...
        if (isLetter(firstChar)) {
            final String first = firstChar.toUpperCase();
            DividedImageCanvas.Dimensions d = view.getDesiredDimensions(address);
            int hash = Objects.hashCode(first, d);
            bitmap = mTileBitmapCache.get(hash);
            if (bitmap == null) {
                // Create bitmap based on the first char
                bitmap = Bitmap.createBitmap(d.width, d.height, Bitmap.Config.ARGB_8888);
                sPaint.setColor(Color.BLACK);
                if (sTileLetterFontSize == -1) {
                    final Resources res = view.getContext().getResources();
                    sTileLetterFontSize = res.getDimensionPixelSize(R.dimen.tile_letter_font_size);
                    sTileLetterFontSizeMed = res
                            .getDimensionPixelSize(R.dimen.tile_letter_font_size_med);
                    sTileLetterFontSizeSmall = res
                            .getDimensionPixelSize(R.dimen.tile_letter_font_size_small);
                    sTilePaddingBottom = res
                            .getDimensionPixelSize(R.dimen.tile_letter_padding_bottom);
                    sTilePaddingBottomHalf = res
                            .getDimensionPixelSize(R.dimen.tile_letter_padding_bottom_half);
                    sTilePaddingBottomQuarter = res
                            .getDimensionPixelSize(R.dimen.tile_letter_padding_bottom_quarter);
                    sTilePaddingLeft = res.getDimensionPixelSize(R.dimen.tile_letter_padding_left);
                    sTilePaddingLeftHalf = res
                            .getDimensionPixelSize(R.dimen.tile_letter_padding_left_half);
                    sTilePaddingLeftQuarter = res
                            .getDimensionPixelSize(R.dimen.tile_letter_padding_left_quarter);
                    sTileColor = res.getColor(R.color.letter_tile_color);
                    sTileFontColor = res.getColor(R.color.letter_tile_font_color);
                }
                sPaint.setTextSize(getFontSize(d.scale));
                sPaint.setTypeface(Typeface.DEFAULT);
                sPaint.setColor(sTileFontColor);
                Canvas c = new Canvas(bitmap);
                c.drawColor(sTileColor);
                c.drawText(first, getLeftPadding(d.scale), d.height - getBottomPadding(d.scale),
                        sPaint);
                mTileBitmapCache.put(hash, bitmap);
            }
        } else {
            if (mDefaultBitmap == null) {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inMutable = true;
                mDefaultBitmap = BitmapFactory.decodeResource(view.getContext().getResources(),
                        DEFAULT_AVATAR_DRAWABLE, options);
            }
            bitmap = mDefaultBitmap;
        }
        view.addDivisionImage(bitmap, address);
    }

    private int getFontSize(float scale)  {
        if (scale == DividedImageCanvas.ONE) {
            return sTileLetterFontSize;
        } else if (scale == DividedImageCanvas.HALF) {
            return sTileLetterFontSizeMed;
        } else {
            return sTileLetterFontSizeSmall;
        }
    }

    private int getBottomPadding(float scale)  {
        if (scale == DividedImageCanvas.ONE) {
            return sTilePaddingBottom;
        } else if (scale == DividedImageCanvas.HALF){
            return sTilePaddingBottomHalf;
        } else {
            return sTilePaddingBottomQuarter;
        }
    }

    private int getLeftPadding(float scale)  {
        if (scale == DividedImageCanvas.ONE) {
            return sTilePaddingLeft;
        } else if (scale == DividedImageCanvas.HALF){
            return sTilePaddingLeftHalf;
        } else {
            return sTilePaddingLeftQuarter;
        }
    }

    private boolean isLetter(String letter) {
        Matcher m = ALPHABET.matcher(letter);
        return m.matches();
    }
}
