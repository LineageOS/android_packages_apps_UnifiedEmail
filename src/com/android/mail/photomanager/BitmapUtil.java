/*
 * Copyright (C) 2012 The Android Open Source Project
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
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;

import com.android.mail.utils.LogUtils;

/**
 * Provides static functions to decode bitmaps at the optimal size
 */
public class BitmapUtil {

    private static final boolean DEBUG = false;

    private BitmapUtil() {
    }

    /**
     * Returns Width or Height of the picture, depending on which size is
     * smaller. Doesn't actually decode the picture, so it is pretty efficient
     * to run.
     */
    public static int getSmallerExtentFromBytes(byte[] bytes) {
        final BitmapFactory.Options options = new BitmapFactory.Options();

        // don't actually decode the picture, just return its bounds
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);

        // test what the best sample size is
        return Math.min(options.outWidth, options.outHeight);
    }

    /**
     * Finds the optimal sampleSize for loading the picture
     *
     * @param originalSmallerExtent Width or height of the picture, whichever is
     *            smaller
     * @param targetExtent Width or height of the target view, whichever is
     *            bigger. If either one of the parameters is 0 or smaller, no
     *            sampling is applied
     */
    public static int findOptimalSampleSize(int originalSmallerExtent, int targetExtent) {
        // If we don't know sizes, we can't do sampling.
        if (targetExtent < 1)
            return 1;
        if (originalSmallerExtent < 1)
            return 1;

        // Test what the best sample size is. To do that, we find the sample
        // size that gives us
        // the best trade-off between resulting image size and memory
        // requirement. We allow
        // the down-sampled image to be 20% smaller than the target size. That
        // way we can get around
        // unfortunate cases where e.g. a 720 picture is requested for 362 and
        // not down-sampled at
        // all. Why 20%? Why not. Prove me wrong.
        int extent = originalSmallerExtent;
        int sampleSize = 1;
        while ((extent >> 1) >= targetExtent * 0.8f) {
            sampleSize <<= 1;
            extent >>= 1;
        }

        return sampleSize;
    }

    /**
     * Decodes the bitmap with the given sample size
     */
    public static Bitmap decodeBitmapFromBytes(byte[] bytes, int sampleSize) {
        final BitmapFactory.Options options;
        if (sampleSize <= 1) {
            options = null;
        } else {
            options = new BitmapFactory.Options();
            options.inSampleSize = sampleSize;
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
    }

    /**
     * Decode an image into a Bitmap, using sub-sampling if the desired dimensions call for it.
     * Also applies a center-crop a la {@link android.widget.ImageView.ScaleType#CENTER_CROP}.
     *
     * @param src an encoded image
     * @param w desired width in px
     * @param h desired height in px
     * @return an exactly-sized decoded Bitmap that is center-cropped.
     */
    public static Bitmap decodeByteArrayWithCenterCrop(byte[] src, int w, int h) {
        try {
            // calculate sample size based on w/h
            final BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(src, 0, src.length, opts);
            if (opts.mCancel || opts.outWidth == -1 || opts.outHeight == -1) {
                return null;
            }
            opts.inSampleSize = Math.min(opts.outWidth / w, opts.outHeight / h);
            opts.inJustDecodeBounds = false;
            final Bitmap decoded = BitmapFactory.decodeByteArray(src, 0, src.length, opts);

            return centerCrop(decoded, w, h);

        } catch (Throwable t) {
            LogUtils.w(PhotoManager.TAG, t, "unable to decode image");
            return null;
        }
    }

    /**
     * Returns a new Bitmap copy with a center-crop effect a la
     * {@link android.widget.ImageView.ScaleType#CENTER_CROP}.
     *
     * @param src original bitmap of any size
     * @param w desired width in px
     * @param h desired height in px
     * @return a copy of src conforming to the given width and height
     */
    public static Bitmap centerCrop(Bitmap src, int w, int h) {
        final Matrix m = new Matrix();
        final float scale = Math.max(
                (float) w / src.getWidth(),
                (float) h / src.getHeight());
        m.setScale(scale, scale);

        final int srcX, srcY, srcW, srcH;

        srcW = Math.round(w / scale);
        srcH = Math.round(h / scale);
        srcX = (src.getWidth() - srcW) / 2;
        srcY = (src.getHeight() - srcH) / 2;

        final Bitmap cropped = Bitmap.createBitmap(src, srcX, srcY, srcW, srcH, m,
                true /* filter */);

        if (DEBUG) LogUtils.i(PhotoManager.TAG,
                "IN centerCrop, srcW/H=%s/%s desiredW/H=%s/%s srcX/Y=%s/%s" +
                " innerW/H=%s/%s scale=%s resultW/H=%s/%s",
                src.getWidth(), src.getHeight(), w, h, srcX, srcY, srcW, srcH, scale,
                cropped.getWidth(), cropped.getHeight());
        if (DEBUG && (w != cropped.getWidth() || h != cropped.getHeight())) {
            LogUtils.e(PhotoManager.TAG, new Error(), "last center crop violated assumptions.");
        }

        return cropped;
    }
}
