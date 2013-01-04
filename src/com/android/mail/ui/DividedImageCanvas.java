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
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;

import com.android.mail.photomanager.BitmapUtil;

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
public class DividedImageCanvas {
    public static final int MAX_DIVISIONS = 4;

    private ArrayList<String> mDivisionIds;
    private ArrayList<Bitmap> mDivisionImages;
    private Bitmap mDividedBitmap;
    private Canvas mCanvas;
    private int mWidth;
    private int mHeight;

    private final Context mContext;
    private final InvalidateCallback mCallback;

    private static final Paint sPaint = new Paint();
    private static final Rect sSrc = new Rect();
    private static final Rect sDest = new Rect();

    public DividedImageCanvas(Context context, InvalidateCallback callback) {
        mContext = context;
        mCallback = callback;
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
        mDivisionImages = new ArrayList<Bitmap>(divisionIds.size());
        for (int i = 0; i < mDivisionIds.size(); i++) {
            mDivisionImages.add(null);
        }
    }

    private void draw(Bitmap b, Canvas c, int left, int top, int right, int bottom) {
        if (b != null) {
            // l t r b
            sSrc.set(0, 0, b.getWidth(), b.getHeight());
            sDest.set(left, top, right, bottom);
            c.drawBitmap(b, sSrc, sDest, sPaint);
        }
    }

    /**
     * Add a bitmap to this view in the quadrant matching its id.
     * @param b Bitmap
     * @param id Id to look for that was previously set in setDivisionIds.
     */
    public void addDivisionImage(Bitmap b, String id) {
        int pos = mDivisionIds.indexOf(id);
        if (pos >= 0 && mDivisionImages.get(pos) == null && b != null) {
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
                    mDivisionImages.set(pos, b);
                    mCanvas.drawBitmap(mDivisionImages.get(0), 0, 0, sPaint);
                    complete = true;
                    break;
                case 2:
                    // Draw 2 bitmaps split vertically down the middle
                    mDivisionImages
                            .set(pos, BitmapUtil.obtainBitmapWithHalfWidth(b, width, height));
                    switch (pos) {
                        case 0:
                            draw(mDivisionImages.get(0), mCanvas, 0, 0, width / 2, height);
                            break;
                        case 1:
                            draw(mDivisionImages.get(1), mCanvas, width / 2, 0, width, height);
                            break;
                    }
                    complete = mDivisionImages.get(0) != null && mDivisionImages.get(1) != null;
                    break;
                case 3:
                    // Draw 3 bitmaps: the first takes up all vertical
                    // space,
                    // the 2nd and 3rd are stacked in the second vertical
                    // position.
                    switch (pos) {
                        case 0:
                            mDivisionImages.set(pos,
                                    BitmapUtil.obtainBitmapWithHalfWidth(b, width, height));
                            draw(mDivisionImages.get(0), mCanvas, 0, 0, width / 2, height);
                            break;
                        case 1:
                            mDivisionImages.set(pos, BitmapUtil
                                    .obtainBitmapWithHalfHeightAndHalfWidth(b, width, height));
                            draw(mDivisionImages.get(1), mCanvas, width / 2, 0, width, height / 2);
                            break;
                        case 2:
                            mDivisionImages.set(pos, BitmapUtil
                                    .obtainBitmapWithHalfHeightAndHalfWidth(b, width, height));
                            draw(mDivisionImages.get(2), mCanvas, width / 2, height / 2, width,
                                    height);
                            break;
                    }
                    complete = mDivisionImages.get(0) != null && mDivisionImages.get(1) != null
                            && mDivisionImages.get(2) != null;
                    break;
                default:
                    // Draw all 4 bitmaps in a grid
                    mDivisionImages.set(pos,
                            BitmapUtil.obtainBitmapWithHalfHeightAndHalfWidth(b, width, height));
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
                    break;
            }
            // Create the new image bitmap.
            if (complete) {
                mCallback.invalidate();
            }
        }
    }

    /**
     * Draw the contents of the DividedImageCanvas to the supplied canvas.
     */
    public void draw(Canvas canvas) {
        if (mDividedBitmap != null) {
            canvas.drawBitmap(mDividedBitmap, 0, 0, sPaint);
        }
    }

    /**
     * Reset all state associated with this view so that it can be reused.
     */
    public void reset() {
        mDividedBitmap = null;
        mDivisionIds = null;
        mDivisionImages = null;
        mCanvas = null;
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
}
