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
import android.graphics.Paint;
import android.graphics.Rect;

import com.android.mail.R;
import com.google.common.base.Objects;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

    private final Map<String, Integer> mDivisionMap =
            Maps.newHashMapWithExpectedSize(MAX_DIVISIONS);
    private Bitmap mDividedBitmap;
    private Canvas mCanvas;
    private int mWidth;
    private int mHeight;

    private final Context mContext;
    private final InvalidateCallback mCallback;
    private final ArrayList<Bitmap> mDivisionImages = new ArrayList<Bitmap>(MAX_DIVISIONS);

    /**
     * Ignore any request to draw final output when not yet ready. This prevents partially drawn
     * canvases from appearing.
     */
    private boolean mBitmapValid = false;

    private int mGeneration;

    private static final Paint sPaint = new Paint();
    private static final Rect sDest = new Rect();

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

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("{");
        sb.append(super.toString());
        sb.append(" mDivisionMap=");
        sb.append(mDivisionMap);
        sb.append(" mDivisionImages=");
        sb.append(mDivisionImages);
        sb.append(" mWidth=");
        sb.append(mWidth);
        sb.append(" mHeight=");
        sb.append(mHeight);
        sb.append("}");
        return sb.toString();
    }

    /**
     * Set the id associated with each quadrant. The quadrants are laid out:
     * TopLeft, TopRight, Bottom Left, Bottom Right
     * @param divisionIds
     */
    public void setDivisionIds(List<String> divisionIds) {
        if (divisionIds.size() > MAX_DIVISIONS) {
            throw new IllegalArgumentException("too many divisionIds: " + divisionIds);
        }
        mDivisionMap.clear();
        mDivisionImages.clear();
        int i = 0;
        for (String id : divisionIds) {
            mDivisionMap.put(id, i);
            mDivisionImages.add(null);
            i++;
        }
    }

    private static void draw(Bitmap b, Canvas c, int left, int top, int right, int bottom) {
        if (b != null) {
            // l t r b
            sDest.set(left, top, right, bottom);
            c.drawBitmap(b, null, sDest, sPaint);
        }
    }

    /**
     * Get the desired dimensions and scale for the bitmap to be placed in the
     * location corresponding to id. Caller must allocate the Dimensions object.
     * @param id
     * @param outDim a {@link ImageCanvas.Dimensions} object to write results into
     */
    @Override
    public void getDesiredDimensions(Object id, Dimensions outDim) {
        int w = 0, h = 0;
        float scale = 0;
        final Integer pos = mDivisionMap.get(id);
        if (pos != null && pos >= 0) {
            final int size = mDivisionMap.size();
            switch (size) {
                case 0:
                    break;
                case 1:
                    w = mWidth;
                    h = mHeight;
                    scale = Dimensions.SCALE_ONE;
                    break;
                case 2:
                    w = mWidth / 2;
                    h = mHeight;
                    scale = Dimensions.SCALE_HALF;
                    break;
                case 3:
                    switch (pos) {
                        case 0:
                            w = mWidth / 2;
                            h = mHeight;
                            scale = Dimensions.SCALE_HALF;
                            break;
                        default:
                            w = mWidth / 2;
                            h = mHeight / 2;
                            scale = Dimensions.SCALE_QUARTER;
                    }
                    break;
                case 4:
                    w = mWidth / 2;
                    h = mHeight / 2;
                    scale = Dimensions.SCALE_QUARTER;
                    break;
            }
        }
        outDim.width = w;
        outDim.height = h;
        outDim.scale = scale;
    }

    @Override
    public void drawImage(Bitmap b, Object id) {
        addDivisionImage(b, id);
    }

    /**
     * Add a bitmap to this view in the quadrant matching its id.
     * @param b Bitmap
     * @param id Id to look for that was previously set in setDivisionIds.
     */
    public void addDivisionImage(Bitmap b, Object id) {
        final Integer pos = mDivisionMap.get(id);
        if (pos != null && pos >= 0 && b != null) {
            mDivisionImages.set(pos, b);
            boolean complete = false;
            final int width = mWidth;
            final int height = mHeight;
            // Different layouts depending on count.
            final int size = mDivisionMap.size();
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
                mBitmapValid = true;
                mCallback.invalidate();
            }
        }
    }

    public boolean hasImageFor(Object id) {
        final Integer pos = mDivisionMap.get(id);
        return pos != null && mDivisionImages.get(pos) != null;
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
        if (mDividedBitmap != null && mBitmapValid) {
            canvas.drawBitmap(mDividedBitmap, 0, 0, null);
        }
    }

    @Override
    public void reset() {
        if (mCanvas != null && mDividedBitmap != null) {
            mBitmapValid = false;
        }
        mDivisionMap.clear();
        mDivisionImages.clear();
        mGeneration++;
    }

    @Override
    public int getGeneration() {
        return mGeneration;
    }

    /**
     * Set the width and height of the canvas.
     * @param width
     * @param height
     */
    public void setDimensions(int width, int height) {
        if (mWidth == width && mHeight == height) {
            return;
        }

        mWidth = width;
        mHeight = height;

        mDividedBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        mCanvas = new Canvas(mDividedBitmap);
        mBitmapValid = true;
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

    public int getDivisionCount() {
        return mDivisionMap.size();
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
        return Lists.newArrayList(mDivisionMap.keySet());
    }

    @Deprecated
    @Override
    public Bitmap loadImage(byte[] bytes, Object id) {
        // TODO: remove me soon.
        return null;
    }
}
