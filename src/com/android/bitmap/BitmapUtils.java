package com.android.bitmap;

import android.graphics.Rect;

public abstract class BitmapUtils {

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
     * @param verticalSliceFraction determines the vertical center point for the crop rect. Range is
*            from [0.0, 1.0]. To perform a vertically centered crop, use
*            0.5. Otherwise, see absoluteFraction.
     * @param absoluteFraction determines how the verticalSliceFraction affects the vertical center
*            point. If this parameter is true, the vertical center of the resulting output
*            rectangle will be exactly [verticalSliceFraction * srcH], with care taken to keep
*            the bounds within the source rectangle. If this parameter is false, the vertical
*            center will be calculated so that the values of verticalSliceFraction from 0.0 to
*            1.0 will linearly cover the entirety of the source rectangle.
     * @param outRect a Rect to write the resulting crop coordinates into
     */
    public static void calculateCroppedSrcRect(final int srcW, final int srcH, final int dstW,
            final int dstH, final int dstSliceH, int sampleSize, final float verticalSliceFraction,
            final boolean absoluteFraction, final Rect outRect) {
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

        final int centerV;
        if (absoluteFraction) {
            final int minCenterV = srcCroppedH / 2;
            final int maxCenterV = srcH - srcCroppedH / 2;
            centerV = Math.max(minCenterV,
                    Math.min(maxCenterV, Math.round(srcH * verticalSliceFraction)));
        } else {
            centerV = Math.round(Math.abs(srcH - srcCroppedSliceH) * verticalSliceFraction
                    + srcCroppedH / 2);
        }

        outRect.top = centerV - srcCroppedH / 2;
        outRect.bottom = outRect.top + srcCroppedH;
    }

}
