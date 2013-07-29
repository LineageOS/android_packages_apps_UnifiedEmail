package com.android.bitmap;

import android.graphics.Rect;

public abstract class BitmapUtils {

    public static void calculateCroppedSrcRect(int srcW, int srcH, int dstW, int dstH,
            int dstSliceH, float verticalSliceFraction, Rect outRect) {
        calculateCroppedSrcRect(srcW, srcH, dstW, dstH, dstSliceH, Integer.MAX_VALUE,
                verticalSliceFraction, outRect);
    }

    public static void calculateCroppedSrcRect(int srcW, int srcH, int dstW, int dstH,
            int sampleSize, Rect outRect) {
        calculateCroppedSrcRect(srcW, srcH, dstW, dstH, dstH, sampleSize, 0.5f, outRect);
    }

    /**
     * Calculate a center-crop rectangle for the given input and output
     * parameters. The output rectangle to use is written in the given outRect.
     *
     * @param srcW the source width
     * @param srcH the source height
     * @param dstW the destination width
     * @param dstH the destination height
     * @param dstSliceH the height extent (in destination coordinates) to
     *            exclude when cropping. You would typically pass dstH, unless
     *            you are trying to normalize different items to the same
     *            vertical crop range.
     * @param sampleSize a scaling factor that rect calculation will only use if
     *            it's more aggressive than regular scaling
     * @param verticalSliceFraction the vertical center point for the crop rect,
     *            from [0.0, 1.0]. To perform a vertically centered crop, use
     *            0.5.
     * @param outRect a Rect to write the resulting crop coordinates into
     */
    public static void calculateCroppedSrcRect(int srcW, int srcH, int dstW, int dstH,
            int dstSliceH, int sampleSize, float verticalSliceFraction, Rect outRect) {
        if (sampleSize < 1) {
            sampleSize = 1;
        }
        final float regularScale = Math.min(
                (float) srcW / dstW,
                (float) srcH / dstH);

        final float scale = Math.min(sampleSize, regularScale);

        final int srcCroppedW = Math.round(dstW * scale);
        final int srcCroppedH = Math.round(dstH * scale);
        final int srcCroppedSliceH = Math.round(dstSliceH * scale);

        outRect.left = (srcW - srcCroppedW) / 2;
        outRect.right = outRect.left + srcCroppedW;

        final int centerV = Math.round(
                (srcH - srcCroppedSliceH) * verticalSliceFraction + srcCroppedH / 2);

        outRect.top = centerV - srcCroppedH / 2;
        outRect.bottom = outRect.top + srcCroppedH;
    }

}
