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

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.LruCache;
import android.util.TypedValue;

import com.android.mail.R;
import com.android.mail.photomanager.ContactPhotoManager.DefaultImageProvider;
import com.android.mail.ui.DividedImageCanvas;

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
    private final LruCache<String, Bitmap> mTileBitmapCache;
    private static int sTilePaddingBottom;
    private static int sTilePaddingLeft;
    private static int sTileLetterFontSize = -1;
    private static TextPaint sPaint = new TextPaint();
    private static int DEFAULT_AVATAR_DRAWABLE = R.drawable.ic_contact_picture;

    public LetterTileProvider() {
        super();
        final float cacheSizeAdjustment =
                (MemoryUtils.getTotalMemorySize() >= MemoryUtils.LARGE_RAM_THRESHOLD) ?
                1.0f : 0.5f;
        final int bitmapCacheSize = (int) (cacheSizeAdjustment * 26);
        mTileBitmapCache = new LruCache<String, Bitmap>(bitmapCacheSize);
    }

    @Override
    public void applyDefaultImage(String displayName, String address, DividedImageCanvas view,
            int extent) {
        String display = !TextUtils.isEmpty(displayName) ? displayName : address;
        String firstChar = display.substring(0, 1).toUpperCase();
        Bitmap bitmap;
        byte[] bytes = firstChar.getBytes();
        // If its a valid ascii character...
        if (bytes[0] > 31 && bytes[0] < 253) {
            bitmap = mTileBitmapCache.get(firstChar);
            if (bitmap == null) {
                // Create bitmap based on the first char
                int width = view.getWidth();
                int height = view.getHeight();
                bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                Canvas c = new Canvas(bitmap);
                sPaint.setColor(Color.BLACK);
                if (sTileLetterFontSize == -1) {
                    final Resources res = view.getContext().getResources();
                    sTileLetterFontSize = res
                            .getDimensionPixelSize(R.dimen.tile_letter_font_size);
                    sTilePaddingBottom = res
                            .getDimensionPixelSize(R.dimen.tile_letter_padding_bottom);
                    sTilePaddingLeft = res
                            .getDimensionPixelSize(R.dimen.tile_letter_padding_left);
                }
                sPaint.setTextSize(sTileLetterFontSize);
                c.drawText(firstChar, sTilePaddingLeft, height - sTilePaddingBottom, sPaint);
                mTileBitmapCache.put(firstChar, bitmap);
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
}
