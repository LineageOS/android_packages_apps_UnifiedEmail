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

package com.android.mail.ui;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;

import com.android.mail.R;
import com.android.mail.photomanager.BitmapUtil;
import com.google.common.base.Objects;

import java.util.ArrayList;

/**
 * DividedImageCanvas creates a canvas that can display into a minimum of 1
 * and maximum of 4 images. As images are added, they
 * are laid out according to the following algorithm:
 * 1 Image: Draw the bitmap filling the entire canvas.
 * 2 Images: Draw 2 bitmaps split vertically down the middle.
 * 3 Images: Draw 3 bitmaps: the first takes up all vertical space; the 2nd and 3rd are stacked in
 *           the second vertical position.
 * 4 Images: Divide the Canvas into 4 equal quadrants and draws 1 bitmap in each.
 */
public class DividedImageCanvas implements ImageCanvas {
    public static final int MAX_DIVISIONS = 4;

    private ArrayList<String> mDivisionIds;
    private Bitmap mDividedBitmap;
    private Canvas mCanvas;
    private int mWidth;
    private int mHeight;

    private final Context mContext;
    private final InvalidateCallback mCallback;
    private final ArrayList<Bitmap> mDivisionImages = new ArrayList<Bitmap>(MAX_DIVISIONS);


    private static final Paint sPaint = new Paint();
    private static final Rect sSrc = new Rect();
    private static final Rect sDest = new Rect();

    public static final float ONE = 1.0f;

    public static final float HALF = 0.5f;

    public static final float QUARTER = 0.25f;

    private static int sDividerLineWidth = -1;
    private static int sDividerColor;

    public DividedImageCanvas(Context context, InvalidateCallback callback) {
        mContext = context;
        mCallback = callback;
        setupDividerLines();
    }

    /**
     * Get application context for this object.
     */
    public Context getContext() {
        return mContext;
    }

    /**
     * Set the id associated with each quadrant. The quadrants are laid out:
     * TopLeft, TopRight, Bottom Left, Bottom Right
     * @param divisionIds
     */
    public void setDivisionIds(ArrayList<String> divisionIds) {
        mDivisionIds = divisionIds;
        for (int i = 0; i < mDivisionIds.size(); i++) {
            mDivisionImages.add(null);
        }
    }

    private static void draw(Bitmap b, Canvas c, int left, int top, int right, int bottom) {
        if (b != null) {
            // l t r b
            sSrc.set(0, 0, b.getWidth(), b.getHeight());
            sDest.set(left, top, right, bottom);
            c.drawBitmap(b, sSrc, sDest, sPaint);
        }
    }

    /**
     * Create a bitmap and add it to this view in the quadrant matching its id.
     * @param b Bitmap
     * @param id Id to look for that was previously set in setDivisionIds.
     * @return created bitmap or null
     */
    public Bitmap addDivisionImage(byte[] bytes, String id) {
        Bitmap b = null;
        final int pos = mDivisionIds.indexOf(id);
        if (pos >= 0 && bytes != null && bytes.length > 0) {
            final int width = mWidth;
            final int height = mHeight;
            // Different layouts depending on count.
            int size = mDivisionIds.size();
            switch (size) {
                case 1:
                    // Draw the bitmap filling the entire canvas.
                    b = BitmapUtil.decodeBitmapFromBytes(bytes, width, height);
                    break;
                case 2:
                    // Draw 2 bitmaps split vertically down the middle
                    b = BitmapUtil.obtainBitmapWithHalfWidth(bytes, width, height);
                    break;
                case 3:
                    switch (pos) {
                        case 0:
                            b = BitmapUtil.obtainBitmapWithHalfWidth(bytes, width, height);
                            break;
                        case 1:
                        case 2:
                            b = BitmapUtil.decodeBitmapFromBytes(bytes, width / 2, height / 2);
                            break;
                    }
                    break;
                case 4:
                    // Draw all 4 bitmaps in a grid
                    b = BitmapUtil.decodeBitmapFromBytes(bytes, width / 2, height / 2);
                    break;
            }
        }
        addDivisionImage(b, id);
        return b;
    }


    /**
     * Get the desired dimensions and scale for the bitmap to be placed in the
     * location corresponding to id.
     * @param id
     * @return
     */
    public Dimensions getDesiredDimensions(String id) {
        int w = 0, h = 0;
        float scale = 0;
        int pos = mDivisionIds.indexOf(id);
        if (pos >= 0) {
            int size = mDivisionIds.size();
            switch (size) {
                case 0:
                    break;
                case 1:
                    w = mWidth;
                    h = mHeight;
                    scale = ONE;
                    break;
                case 2:
                    w = mWidth / 2;
                    h = mHeight;
                    scale = HALF;
                    break;
                case 3:
                    switch (pos) {
                        case 0:
                            w = mWidth / 2;
                            h = mHeight;
                            scale = HALF;
                            break;
                        default:
                            w = mWidth / 2;
                            h = mHeight / 2;
                            scale = QUARTER;
                    }
                    break;
                case 4:
                    w = mWidth / 2;
                    h = mHeight / 2;
                    scale = QUARTER;
                    break;
            }
        }
        return new Dimensions(w, h, scale);
    }

    /**
     * Add a bitmap to this view in the quadrant matching its id.
     * @param b Bitmap
     * @param id Id to look for that was previously set in setDivisionIds.
     */
    public void addDivisionImage(Bitmap b, String id) {
        int pos = mDivisionIds.indexOf(id);
        if (pos >= 0 && b != null) {
            mDivisionImages.set(pos, b);
            boolean complete = false;
            int width = mWidth;
            int height = mHeight;
            if (mDividedBitmap == null) {
                mDividedBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                mCanvas = new Canvas(mDividedBitmap);
            }
            // Different layouts depending on count.
            int size = mDivisionIds.size();
            switch (size) {
                case 0:
                    // Do nothing.
                    break;
                case 1:
                    // Draw the bitmap filling the entire canvas.
                    draw(mDivisionImages.get(0), mCanvas, 0, 0, width, height);
                    complete = true;
                    break;
                case 2:
                    // Draw 2 bitmaps split vertically down the middle
                    switch (pos) {
                        case 0:
                            draw(mDivisionImages.get(0), mCanvas, 0, 0, width / 2, height);
                            break;
                        case 1:
                            draw(mDivisionImages.get(1), mCanvas, width / 2, 0, width, height);
                            break;
                    }
                    complete = mDivisionImages.get(0) != null && mDivisionImages.get(1) != null;
                    if (complete) {
                        // Draw dividers
                        drawVerticalDivider(width, height);
                    }
                    break;
                case 3:
                    // Draw 3 bitmaps: the first takes up all vertical
                    // space, the 2nd and 3rd are stacked in the second vertical
                    // position.
                    switch (pos) {
                        case 0:
                            draw(mDivisionImages.get(0), mCanvas, 0, 0, width / 2, height);
                            break;
                        case 1:
                            draw(mDivisionImages.get(1), mCanvas, width / 2, 0, width, height / 2);
                            break;
                        case 2:
                            draw(mDivisionImages.get(2), mCanvas, width / 2, height / 2, width,
                                    height);
                            break;
                    }
                    complete = mDivisionImages.get(0) != null && mDivisionImages.get(1) != null
                            && mDivisionImages.get(2) != null;
                    if (complete) {
                        // Draw dividers
                        drawVerticalDivider(width, height);
                        drawHorizontalDivider(width / 2, height / 2, width, height / 2);
                    }
                    break;
                default:
                    // Draw all 4 bitmaps in a grid
                    switch (pos) {
                        case 0:
                            draw(mDivisionImages.get(0), mCanvas, 0, 0, width / 2, height / 2);
                            break;
                        case 1:
                            draw(mDivisionImages.get(1), mCanvas, width / 2, 0, width, height / 2);
                            break;
                        case 2:
                            draw(mDivisionImages.get(2), mCanvas, 0, height / 2, width / 2, height);
                            break;
                        case 3:
                            draw(mDivisionImages.get(3), mCanvas, width / 2, height / 2, width,
                                    height);
                            break;
                    }
                    complete = mDivisionImages.get(0) != null && mDivisionImages.get(1) != null
                            && mDivisionImages.get(2) != null && mDivisionImages.get(3) != null;
                    if (complete) {
                        // Draw dividers
                        drawVerticalDivider(width, height);
                        drawHorizontalDivider(0, height / 2, width, height / 2);
                    }
                    break;
            }
            // Create the new image bitmap.
            if (complete) {
                mCallback.invalidate();
            }
        }
    }

    private void setupDividerLines() {
        if (sDividerLineWidth == -1) {
            Resources res = getContext().getResources();
            sDividerLineWidth = res
                    .getDimensionPixelSize(R.dimen.tile_divider_width);
            sDividerColor = res.getColor(R.color.tile_divider_color);
        }
    }

    private static void setupPaint() {
        sPaint.setStrokeWidth(sDividerLineWidth);
        sPaint.setColor(sDividerColor);
    }

    private void drawVerticalDivider(int width, int height) {
        int x1 = width / 2, y1 = 0, x2 = width/2, y2 = height;
        setupPaint();
        mCanvas.drawLine(x1, y1, x2, y2, sPaint);
    }

    private void drawHorizontalDivider(int x1, int y1, int x2, int y2) {
        setupPaint();
        mCanvas.drawLine(x1, y1, x2, y2, sPaint);
    }

    /**
     * Draw the contents of the DividedImageCanvas to the supplied canvas.
     */
    public void draw(Canvas canvas) {
        if (mDividedBitmap != null) {
            canvas.drawBitmap(mDividedBitmap, 0, 0, sPaint);
        }
    }

    @Override
    public void reset() {
        if (mCanvas != null && mDividedBitmap != null) {
            mCanvas.drawColor(Color.WHITE);
        }
        mDivisionIds = null;
        mDivisionImages.clear();
    }

    /**
     * Set the width and height of the canvas.
     * @param width
     * @param height
     */
    public void setDimensions(int width, int height) {
        mWidth = width;
        mHeight = height;
    }

    /**
     * Get the resulting canvas width.
     */
    public int getWidth() {
        return mWidth;
    }

    /**
     * Get the resulting canvas height.
     */
    public int getHeight() {
        return mHeight;
    }

    /**
     * The class that will provided the canvas to which the DividedImageCanvas
     * should render its contents must implement this interface.
     */
    public interface InvalidateCallback {
        public void invalidate();
    }

    /**
     * Dimensions holds the desired width, height, and scale for a bitmap being
     * placed in the DividedImageCanvas.
     */
    public class Dimensions {
        final public int width;
        final public int height;
        final public float scale;
        public Dimensions(int w, int h, float s) {
            width = w;
            height = h;
            scale = s;
        }
    }

    public int getDivisionCount() {
        return mDivisionIds != null ? mDivisionIds.size() : 0;
    }

    /**
     * Generate a unique hashcode to use for the request for an image to put in
     * the specified position of the DividedImageCanvas.
     */
    public static long generateHash(DividedImageCanvas contactImagesHolder, int i, String address) {
        return Objects.hashCode(contactImagesHolder, i, address);
    }

    /**
     * Get the division ids currently associated with this DivisionImageCanvas.
     */
    public ArrayList<String> getDivisionIds() {
        return mDivisionIds;
    }

    @Override
    public Bitmap loadImage(byte[] bytes, String id) {
        return addDivisionImage(bytes, id);
    }
}
