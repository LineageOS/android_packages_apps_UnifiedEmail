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

import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint.Align;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.text.TextUtils;

import com.android.mail.R;
import com.android.mail.photomanager.ContactPhotoManager.ContactIdentifier;
import com.android.mail.photomanager.PhotoManager.DefaultImageProvider;
import com.android.mail.photomanager.PhotoManager.PhotoIdentifier;
import com.android.mail.ui.DividedImageCanvas;
import com.android.mail.ui.ImageCanvas;
import com.android.mail.utils.LogTag;
import com.android.mail.utils.LogUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LetterTileProvider is an implementation of the DefaultImageProvider. When no
 * matching contact photo is found, and there is a supplied displayName or email
 * address whose first letter corresponds to an English alphabet letter (or
 * number), this method creates a bitmap with the letter in the center of a
 * tile. If there is no English alphabet character (or digit), it creates a
 * bitmap with the default contact avatar.
 */
public class LetterTileProvider implements DefaultImageProvider {
    private static final String TAG = LogTag.getLogTag();
    private Bitmap mDefaultBitmap;
    private static Bitmap[] sBitmapBackgroundCache;
    private static Typeface sSansSerifLight;
    private static Rect sBounds;
    private static int sTileLetterFontSize = -1;
    private static int sTileLetterFontSizeSmall;
    private static int sTileFontColor;
    private static TextPaint sPaint = new TextPaint();
    private static int DEFAULT_AVATAR_DRAWABLE = R.drawable.ic_contact_picture;
    private static final Pattern ALPHABET = Pattern.compile("^[a-zA-Z0-9]+$");
    private static final int POSSIBLE_BITMAP_SIZES = 3;

    // This should match the total number of colors defined in colors.xml for letter_tile_color
    private static final int NUM_OF_TILE_COLORS = 7;

    public LetterTileProvider() {
        super();
    }

    @Override
    public void applyDefaultImage(PhotoIdentifier id, ImageCanvas view, int extent) {
        ContactIdentifier contactIdentifier = (ContactIdentifier) id;
        DividedImageCanvas dividedImageView = (DividedImageCanvas) view;

        String displayName = contactIdentifier.name;
        String address = contactIdentifier.emailAddress;

        Bitmap bitmap = null;
        final String display = !TextUtils.isEmpty(displayName) ? displayName : address;
        final String firstChar = display.substring(0, 1);
        // If its a valid english alphabet letter...
        if (isLetter(firstChar)) {
            final Resources res = dividedImageView.getContext().getResources();
            if (sTileLetterFontSize == -1) {
                sTileLetterFontSize = res.getDimensionPixelSize(R.dimen.tile_letter_font_size);
                sTileLetterFontSizeSmall = res
                        .getDimensionPixelSize(R.dimen.tile_letter_font_size_small);
                sTileFontColor = res.getColor(R.color.letter_tile_font_color);
                sSansSerifLight = Typeface.create("sans-serif-light", Typeface.NORMAL);
                sBounds = new Rect();
                sPaint.setTypeface(sSansSerifLight);
                sPaint.setColor(sTileFontColor);
                sPaint.setTextAlign(Align.CENTER);
                sPaint.setAntiAlias(true);
                sBitmapBackgroundCache = new Bitmap[POSSIBLE_BITMAP_SIZES];
            }
            final String first = firstChar.toUpperCase();
            DividedImageCanvas.Dimensions d = dividedImageView.getDesiredDimensions(address);
            bitmap = getBitmap(d);
            if (bitmap == null) {
                LogUtils.w(TAG,
                        "LetterTileProvider width(%d) or height(%d) is 0 for name %s and address %s.",
                        dividedImageView.getWidth(), dividedImageView.getHeight(), displayName,
                        address);
                return;
            }
            Canvas c = new Canvas(bitmap);
            c.drawColor(pickColor(res, address));
            sPaint.setTextSize(getFontSize(d.scale));
            sPaint.getTextBounds(first, 0, first.length(), sBounds);
            c.drawText(first, 0 + d.width / 2, 0 + d.height / 2 + (sBounds.bottom - sBounds.top)
                    / 2, sPaint);
        } else {
            if (mDefaultBitmap == null) {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inMutable = true;
                mDefaultBitmap = BitmapFactory.decodeResource(dividedImageView.getContext().getResources(),
                        DEFAULT_AVATAR_DRAWABLE, options);
            }
            bitmap = mDefaultBitmap;
        }
        dividedImageView.addDivisionImage(bitmap, address);
    }

    private static Bitmap getBitmap(final DividedImageCanvas.Dimensions d) {
        if (d.width <= 0 || d.height <= 0) {
            LogUtils.w(TAG,
                    "LetterTileProvider width(%d) or height(%d) is 0.", d.width, d.height);
            return null;
        }
        final int pos;
        float scale = d.scale;
        if (scale == DividedImageCanvas.ONE) {
            pos = 0;
        } else if (scale == DividedImageCanvas.HALF) {
            pos = 1;
        } else {
            pos = 2;
        }
        Bitmap bitmap = sBitmapBackgroundCache[pos];
        if (bitmap == null) {
            // create and place the bitmap
            bitmap = Bitmap.createBitmap(d.width, d.height, Bitmap.Config.ARGB_8888);
            sBitmapBackgroundCache[pos] = bitmap;
        }
        return bitmap;
    }

    private static int getFontSize(float scale)  {
        if (scale == DividedImageCanvas.ONE) {
            return sTileLetterFontSize;
        } else {
            return sTileLetterFontSizeSmall;
        }
    }

    private static boolean isLetter(String letter) {
        Matcher m = ALPHABET.matcher(letter);
        return m.matches();
    }

    private static int pickColor(Resources res, String emailAddress) {
        // String.hashCode() implementation is not supposed to change across java versions, so
        // this should guarantee the same email address always maps to the same color.
        int color = Math.abs(emailAddress.hashCode()) % NUM_OF_TILE_COLORS;
        TypedArray colors = res.obtainTypedArray(R.array.letter_tile_colors);
        return colors.getColor(color, R.color.letter_tile_default_color);
    }
}
