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

package com.android.mail.browse;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.Paint.Align;
import android.text.TextPaint;
import android.text.TextUtils;

import com.android.mail.R;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LetterTileUtils is a static-use class for getting a bitmap corresponding
 * to a given account. Spits out a colored tile with a single letter, which
 * is the default for when a contact photo is missing (or if the language is
 * one without English letters, uses the default grey character image).
 */
public class LetterTileUtils {
    private static Typeface sSansSerifLight;
    private static Rect sBounds;
    private static int sTileLetterFontSize = -1;
    private static int sTileLetterFontSizeSmall;
    private static int sTileFontColor;
    private static final TextPaint sPaint = new TextPaint();
    private static final int DEFAULT_AVATAR_DRAWABLE = R.drawable.ic_contact_picture;
    private static final Pattern ALPHABET = Pattern.compile("[A-Z0-9]");

    // This should match the total number of colors defined in colors.xml for letter_tile_color
    private static final int NUM_OF_TILE_COLORS = 7;

    /**
     * Creates a letter tile to act as an account/contact photo based on the account address/name
     * in the given width & height.
     *
     * @param displayName account's custom name
     * @param address account email address
     * @param context current context of the app
     * @param width width of final bitmap in px (input converted number)
     * @param height height of final bitmap in px (input converted number)
     * @return Bitmap with a colored background and a letter tile corresponding to the account
     */
    public static Bitmap generateLetterTile(
            String displayName, String address, Context context, int width, int height) {
        Bitmap bitmap = null;
        final String display = !TextUtils.isEmpty(displayName) ? displayName : address;
        final String firstChar = display.substring(0, 1).toUpperCase(Locale.US);

        // If its a valid English alphabet letter...
        if (isLetter(firstChar)) {
            // Generate letter tile bitmap based on the first letter in name
            final Resources res = context.getResources();
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
            }
            bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            final Canvas c = new Canvas(bitmap);
            c.drawColor(pickColor(res, address));
            sPaint.setTextSize(sTileLetterFontSize);
            sPaint.getTextBounds(firstChar, 0, firstChar.length(), sBounds);
            c.drawText(firstChar, 0 + width / 2, 0 + height / 2 + (sBounds.bottom - sBounds.top)
                    / 2, sPaint);
        } else {
            // Use default image
            final BitmapFactory.Options options = new BitmapFactory.Options();
            bitmap = BitmapFactory.decodeResource(context.getResources(),
                    DEFAULT_AVATAR_DRAWABLE, options);
        }
        return bitmap;
    }

    /**
     * Check against the English alphabet regex if the letter is usable for the bitmap.
     *
     * @param letter Letter to test against
     * @return true if usable in Letter Tile, false otherwise
     */
    private static boolean isLetter(final String letter) {
        final Matcher m = ALPHABET.matcher(letter);
        return m.matches();
    }

    /**
     * Choose a color based on the given resource and the email address's hashCode.
     *
     * @param emailAddress Address for which we are choosing a color
     * @return Color for the tile represented as an int
     */
    private static int pickColor(Resources res, String emailAddress) {
        // String.hashCode() implementation is not supposed to change across java versions, so
        // this should guarantee the same email address always maps to the same color.
        int color = Math.abs(emailAddress.hashCode()) % NUM_OF_TILE_COLORS;
        TypedArray colors = res.obtainTypedArray(R.array.letter_tile_colors);
        return colors.getColor(color, R.color.letter_tile_default_color);
    }
}
