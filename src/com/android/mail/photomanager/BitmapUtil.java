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

import java.io.InputStream;

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
     * Decode an image into a Bitmap, using sub-sampling if the hinted dimensions call for it.
     * Does not crop to fit the hinted dimensions.
     *
     * @param src an encoded image
     * @param w hint width in px
     * @param h hint height in px
     * @return a decoded Bitmap that is not exactly sized to the hinted dimensions.
     */
    public static Bitmap decodeByteArray(byte[] src, int w, int h) {
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
            return BitmapFactory.decodeByteArray(src, 0, src.length, opts);
        } catch (Throwable t) {
            LogUtils.w(PhotoManager.TAG, t, "unable to decode image");
            return null;
        }
    }
    /**
     * Decode an input stream into a Bitmap, using sub-sampling if the hinted dimensions call for
     * it. Does not crop to fit the hinted dimensions.
     *
     * @param factory a factory to retrieve fresh input streams from.
     * @param w hint width in px
     * @param h hint height in px
     * @return a decoded Bitmap that is not exactly sized to the hinted dimensions.
     */
    public static Bitmap decodeStream(InputStreamFactory factory, int w, int h) {
        try {
            // calculate sample size based on w/h
            final BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            InputStream src = factory.newInputStream();
            BitmapFactory.decodeStream(src, null, opts);
            if (src != null) {
                src.close();
            }

            if (opts.mCancel || opts.outWidth == -1 || opts.outHeight == -1) {
                return null;
            }

            opts.inSampleSize = Math.min(opts.outWidth / w, opts.outHeight / h);
            opts.inJustDecodeBounds = false;
            src = factory.newInputStream();
            Bitmap bitmap = BitmapFactory.decodeStream(src, null, opts);
            if (src != null) {
                src.close();
            }
            return bitmap;
        } catch (Throwable t) {
            LogUtils.w(PhotoManager.TAG, t, "unable to decode image");
            return null;
        }
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
            final Bitmap decoded = decodeByteArray(src, w, h);
            return centerCrop(decoded, w, h);

        } catch (Throwable t) {
            LogUtils.w(PhotoManager.TAG, t, "unable to crop image");
            return null;
        }
    }

    /**
     * Decode an input stream into a Bitmap, using sub-sampling if the desired dimensions call
     * for it. Also applies a center-crop a la {@link android.widget.ImageView
     * .ScaleType#CENTER_CROP}.
     *
     * @param factory a factory to retrieve fresh input streams from.
     * @param w desired width in px
     * @param h desired height in px
     * @param horizontalCenterPercent determines which part of the src to crop from. Range from 0
     *                                .0f to 1.0f. The value determines which part of the src
     *                                maps to the horizontal center of the resulting bitmap.
     * @param verticalCenterPercent determines which part of the src to crop from. Range from 0
     *                              .0f to 1.0f. The value determines which part of the src maps
     *                              to the vertical center of the resulting bitmap.
     * @return an exactly-sized decoded Bitmap that is center-cropped.
     */
    public static Bitmap decodeStreamWithCrop(final InputStreamFactory factory, final int w,
            final int h, final float horizontalCenterPercent, final float verticalCenterPercent) {
        final Bitmap decoded;
        try {
            decoded = decodeStream(factory, w, h);
        } catch (Throwable t) {
            LogUtils.w(PhotoManager.TAG, t, "unable to decode image");
            return null;
        }
        try {
            final Bitmap cropped = crop(decoded, w, h, horizontalCenterPercent,
                    verticalCenterPercent);
            LogUtils.d(PhotoManager.TAG, "Full decoded bitmap size %d bytes, cropped size %d bytes",
                    decoded.getByteCount(), cropped.getByteCount());
            return cropped;
        } catch (Throwable t) {
            LogUtils.w(PhotoManager.TAG, t, "unable to crop image");
            return null;
        }
    }

    /**
     * Returns a new Bitmap copy with a center-crop effect a la
     * {@link android.widget.ImageView.ScaleType#CENTER_CROP}. May return the input bitmap if no
     * scaling is necessary.
     *
     * @param src original bitmap of any size
     * @param w desired width in px
     * @param h desired height in px
     * @return a copy of src conforming to the given width and height, or src itself if it already
     *         matches the given width and height
     */
    public static Bitmap centerCrop(final Bitmap src, final int w, final int h) {
        return crop(src, w, h, 0.5f, 0.5f);
    }

    /**
     * Returns a new Bitmap copy with a crop effect depending on the crop anchor given. 0.5f is like
     * {@link android.widget.ImageView.ScaleType#CENTER_CROP}. The crop anchor will be be nudged
     * so the entire cropped bitmap will fit inside the src. May return the input bitmap if no
     * scaling is necessary.
     *
     *
     * Example of changing verticalCenterPercent:
     *   _________            _________
     *  |         |          |         |
     *  |         |          |_________|
     *  |         |          |         |/___0.3f
     *  |---------|          |_________|\
     *  |         |<---0.5f  |         |
     *  |---------|          |         |
     *  |         |          |         |
     *  |         |          |         |
     *  |_________|          |_________|
     *
     * @param src original bitmap of any size
     * @param w desired width in px
     * @param h desired height in px
     * @param horizontalCenterPercent determines which part of the src to crop from. Range from 0
     *                                .0f to 1.0f. The value determines which part of the src
     *                                maps to the horizontal center of the resulting bitmap.
     * @param verticalCenterPercent determines which part of the src to crop from. Range from 0
     *                              .0f to 1.0f. The value determines which part of the src maps
     *                              to the vertical center of the resulting bitmap.
     * @return a copy of src conforming to the given width and height, or src itself if it already
     *         matches the given width and height
     */
    public static Bitmap crop(final Bitmap src, final int w, final int h,
            final float horizontalCenterPercent, final float verticalCenterPercent) {
        if (horizontalCenterPercent < 0 || horizontalCenterPercent > 1 || verticalCenterPercent < 0
                || verticalCenterPercent > 1) {
            throw new IllegalArgumentException(
                    "horizontalCenterPercent and verticalCenterPercent must be between 0.0f and "
                            + "1.0f, inclusive.");
        }
        final int srcWidth = src.getWidth();
        final int srcHeight = src.getHeight();

        // exit early if no resize/crop needed
        if (w == srcWidth && h == srcHeight) {
            return src;
        }

        final Matrix m = new Matrix();
        final float scale = Math.max(
                (float) w / srcWidth,
                (float) h / srcHeight);
        m.setScale(scale, scale);

        final int srcCroppedW, srcCroppedH;
        int srcX, srcY;

        srcCroppedW = Math.round(w / scale);
        srcCroppedH = Math.round(h / scale);
        srcX = (int) (srcWidth * horizontalCenterPercent - srcCroppedW / 2);
        srcY = (int) (srcHeight * verticalCenterPercent - srcCroppedH / 2);

        // Nudge srcX and srcY to be within the bounds of src
        srcX = Math.max(Math.min(srcX, srcWidth - srcCroppedW), 0);
        srcY = Math.max(Math.min(srcY, srcHeight - srcCroppedH), 0);

        final Bitmap cropped = Bitmap.createBitmap(src, srcX, srcY, srcCroppedW, srcCroppedH, m,
                true /* filter */);

        if (DEBUG) LogUtils.i(PhotoManager.TAG,
                "IN centerCrop, srcW/H=%s/%s desiredW/H=%s/%s srcX/Y=%s/%s" +
                " innerW/H=%s/%s scale=%s resultW/H=%s/%s",
                srcWidth, srcHeight, w, h, srcX, srcY, srcCroppedW, srcCroppedH, scale,
                cropped.getWidth(), cropped.getHeight());
        if (DEBUG && (w != cropped.getWidth() || h != cropped.getHeight())) {
            LogUtils.e(PhotoManager.TAG, new Error(), "last center crop violated assumptions.");
        }

        return cropped;
    }

    public interface InputStreamFactory {
        InputStream newInputStream();
    }
}
