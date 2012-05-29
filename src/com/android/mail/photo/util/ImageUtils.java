/*
 * Copyright (C) 2011 Google Inc.
 * Licensed to The Android Open Source Project.
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

package com.android.mail.photo.util;

import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore.Images;
import android.provider.MediaStore.Images.ImageColumns;
import android.provider.MediaStore.Images.Thumbnails;
import android.provider.MediaStore.MediaColumns;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;

import com.android.mail.R;
import com.android.mail.photo.PhotoViewActivity;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Image utilities
 */
public class ImageUtils {
    /** Specifies no background colour should be added during image resizing */
    public static int NO_COLOR = 0;

    public static final int INSERT_PHOTO_DIALOG_ID = R.id.dialog_insert_photo;
    
    // added from EsService
    public static final int CROP_NONE = 0;
    public static final int CROP_SQUARE = 1;
    public static final int CROP_WIDE = 2;

    private static int MICRO_KIND_MAX_DIMENSION = 0;
    private static int MINI_KIND_MAX_DIMENSION = 0;

    private static int DEFAULT_JPEG_QUALITY = 90;

    // Logging
    private static final String TAG = "ImageUtils";

    // Paints and modes
    private static final Paint sResizePaint = new Paint(Paint.FILTER_BITMAP_FLAG);

    /** The paint used for cropped photos */
    private static final Paint sCropPaint;
    static {
        sCropPaint = new Paint();
        sCropPaint.setAntiAlias(true);
        sCropPaint.setFilterBitmap(true);
        sCropPaint.setDither(true);
    }

    private static final Paint sOutStrokePaint = new Paint();
    static {
        sOutStrokePaint.setStrokeWidth(1);
        sOutStrokePaint.setStyle(Paint.Style.STROKE);
        sOutStrokePaint.setColor(0xff999999);
    }

    private static final Paint sInStrokePaint = new Paint();
    static {
        sInStrokePaint.setStrokeWidth(1);
        sInStrokePaint.setStyle(Paint.Style.STROKE);
        sInStrokePaint.setColor(0xfff0f0f0);
    }

    /** Minimum class memory class to use full-res photos */
    private final static long MIN_NORMAL_CLASS = 32;
    /** Minimum class memory class to use small photos */
    private final static long MIN_SMALL_CLASS = 24;
    public static final boolean sUseLowResImages;
    static {
        if (Build.VERSION.SDK_INT >= 11) {
            // On HC and beyond, assume devices are more capable
            sUseLowResImages = false;
        } else {
            if (PhotoViewActivity.sMemoryClass >= MIN_SMALL_CLASS) {
                sUseLowResImages = false;
            } else {
                // If we're not in the small class, use low-res [i.e. RGB_565] photos
                sUseLowResImages = true;
            }
        }
    }

    public static enum ImageSize {
        EXTRA_SMALL,
        SMALL,
        NORMAL,
    }

    public static final ImageSize sUseImageSize;
    static {
        // On HC and beyond, assume devices are more capable
        if (Build.VERSION.SDK_INT >= 11) {
            sUseImageSize = ImageSize.NORMAL;
        } else {
            if (PhotoViewActivity.sMemoryClass >= MIN_NORMAL_CLASS) {
                // We have plenty of memory; use full sized photos
                sUseImageSize = ImageSize.NORMAL;
            } else if (PhotoViewActivity.sMemoryClass >= MIN_SMALL_CLASS) {
                // We have slight less memory; use smaller sized photos
                sUseImageSize = ImageSize.SMALL;
            } else {
                // We have little memory; use very small sized photos
                sUseImageSize = ImageSize.EXTRA_SMALL;
            }
        }
    }

    /**
     * Interface for when a dialog informing about a camera photo insertion
     * should be shown or hidden.
     */
    public interface InsertCameraPhotoDialogDisplayer {
        public void showInsertCameraPhotoDialog();
        public void hideInsertCameraPhotoDialog();
    }

    /**
     * This class cannot be instantiated
     */
    private ImageUtils() {
    }


    /**
     * Parses an image from a byte array. May return either a Bitmap or
     * a {@link Drawable}.
     *
     * @param data byte array of compressed image data
     * @return The decoded bitmap or {@link Drawable}, or null if the image could not be decoded.
     */
    public static Object decodeMedia(byte[] data) {
        try {
            if (GifDrawable.isGif(data)) {
                return new GifDrawable(data);
            } else {
                return BitmapFactory.decodeByteArray(data, 0, data.length);
            }
        } catch (OutOfMemoryError oome) {
            Log.e(TAG, "ImageUtils#decodeMedia(byte[]) threw an OOME", oome);
            return null;
        }
    }

    /**
     * Wrapper around {@link BitmapFactory#decodeByteArray(byte[], int, int)}
     * that returns {@code null} on {@link OutOfMemoryError}.
     *
     * @param data byte array of compressed image data
     * @param offset offset into imageData for where the decoder should begin
     *               parsing.
     * @param length the number of bytes, beginning at offset, to parse
     * @return The decoded bitmap, or null if the image could not be decode.
     */
    public static Bitmap decodeByteArray(byte[] data, int offset, int length) {
        try {
            return BitmapFactory.decodeByteArray(data, offset, length);
        } catch (OutOfMemoryError oome) {
            Log.e(TAG, "ImageUtils#decodeByteArray(byte[], int, int) threw an OOME", oome);
            return null;
        }
    }

    /**
     * Wrapper around {@link BitmapFactory#decodeByteArray(byte[], int, int,
     * BitmapFactory.Options)} that returns {@code null} on {@link
     * OutOfMemoryError}.
     *
     * @param data byte array of compressed image data
     * @param offset offset into imageData for where the decoder should begin
     *               parsing.
     * @param length the number of bytes, beginning at offset, to parse
     * @param opts null-ok; Options that control downsampling and whether the
     *             image should be completely decoded, or just is size returned.
     * @return The decoded bitmap, or null if the image could not be decode.
     */
    public static Bitmap decodeByteArray(byte[] data, int offset, int length,
            BitmapFactory.Options opts) {
        try {
            return BitmapFactory.decodeByteArray(data, offset, length, opts);
        } catch (OutOfMemoryError oome) {
            Log.e(TAG, "ImageUtils#decodeByteArray(byte[], int, int, Options) threw an OOME", oome);
            return null;
        }
    }

    /**
     * Wrapper around {@link BitmapFactory#decodeResource(Resources, int)}
     * that returns {@code null} on {@link OutOfMemoryError}.
     *
     * @param res The resources object containing the image data
     * @param id The resource id of the image data
     * @return The decoded bitmap, or null if the image could not be decode.
     */
    public static Bitmap decodeResource(Resources res, int id) {
        try {
            return BitmapFactory.decodeResource(res, id);
        } catch (OutOfMemoryError oome) {
            Log.e(TAG, "ImageUtils#decodeResource(Resources, int) threw an OOME", oome);
            return null;
        }
    }

    /**
     * Wrapper around {@link BitmapFactory#decodeStream(InputStream, Rect,
     * BitmapFactory.Options)} that returns {@code null} on {@link
     * OutOfMemoryError}.
     *
     * @param is The input stream that holds the raw data to be decoded into a
     *           bitmap.
     * @param outPadding If not null, return the padding rect for the bitmap if
     *                   it exists, otherwise set padding to [-1,-1,-1,-1]. If
     *                   no bitmap is returned (null) then padding is
     *                   unchanged.
     * @param opts null-ok; Options that control downsampling and whether the
     *             image should be completely decoded, or just is size returned.
     * @return The decoded bitmap, or null if the image data could not be
     *         decoded, or, if opts is non-null, if opts requested only the
     *         size be returned (in opts.outWidth and opts.outHeight)
     */
    public static Bitmap decodeStream(InputStream is, Rect outPadding, BitmapFactory.Options opts) {
        try {
            return BitmapFactory.decodeStream(is, outPadding, opts);
        } catch (OutOfMemoryError oome) {
            Log.e(TAG, "ImageUtils#decodeStream(InputStream, Rect, Options) threw an OOME", oome);
            return null;
        }
    }

    /**
     * Create a bitmap from a local URI
     *
     * @param resolver The ContentResolver
     * @param uri The local URI
     * @param maxSize The maximum size (either width or height)
     *
     * @return The new bitmap
     */
    public static Bitmap createLocalBitmap(ContentResolver resolver, Uri uri, int maxSize) {
        InputStream inputStream = null;
        try {
            final BitmapFactory.Options opts = new BitmapFactory.Options();
            final Point bounds = getImageBounds(resolver, uri);

            inputStream = resolver.openInputStream(uri);
            opts.inSampleSize = Math.max(bounds.x / maxSize, bounds.y / maxSize);

            final Bitmap decodedBitmap = decodeStream(inputStream, null, opts);

            // Correct thumbnail orientation as necessary
            // TODO: Fix rotation if it's actually a problem
            //return rotateBitmap(resolver, uri, decodedBitmap);
            return decodedBitmap;

        } catch (FileNotFoundException exception) {
            // Do nothing - the photo will appear to be missing
        } catch (IOException exception) {
            // Do nothing - the photo will appear to be missing
        } finally {
            try {
                if (inputStream != null) {
                    inputStream.close();
                }
            } catch (IOException ignore) {
            }
        }
        return null;
    }

    /**
     * Creates a bitmap from the given bytes at the specified dimension and with the
     * specified crop. Sub-sample as necessary.
     *
     * TODO(toddke) Currently, we only perform the wide crop in this method. The square
     * crop is already handled via the FIFE / Image Proxy URLs. When the photo cache and
     * image cache are merged, we'll need to support square crop as well.
     */
    public static Bitmap createBitmap(byte[] imageBytes, int width, int height, int cropType) {
        if (imageBytes == null || imageBytes.length == 0) {
            return null;
        }

        final ByteArrayInputStream inputStream = new ByteArrayInputStream(imageBytes);
        final boolean useLowResImages = ImageUtils.sUseLowResImages;
        try {
            final BitmapFactory.Options opts = new BitmapFactory.Options();
            final Point bounds = getImageBounds(imageBytes);

            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "PhotoCache#createBitmap; w: " +
                        bounds.x + ", h: " + bounds.y + ", max: " + width);
            }
            opts.inSampleSize = Math.max(bounds.x / width, bounds.y / height);
            if (useLowResImages) {
                opts.inPreferredConfig = Config.RGB_565;
            }

            final Bitmap decodedBitmap = decodeStream(inputStream, null, opts);
            if (decodedBitmap == null) {
                return null;
            }

            final Bitmap croppedBitmap;
            if (cropType == CROP_WIDE) { // changed from EsService.CROP_WIDE
                croppedBitmap = cropWideBitmap(decodedBitmap, width, height);
                decodedBitmap.recycle();

                if (croppedBitmap == null) {
                    return null;
                }
            } else {
                croppedBitmap = decodedBitmap;
            }

            if (useLowResImages) {
                final Bitmap lowResBitmap = ImageUtils.getLowResBitmap(croppedBitmap);
                if (lowResBitmap != croppedBitmap) {
                    croppedBitmap.recycle();
                }
                return lowResBitmap;
            } else {
                return croppedBitmap;
            }
        } catch (OutOfMemoryError e) {
            // Do nothing - the photo will appear to be missing
        } finally {
            try {
                inputStream.close();
            } catch (IOException ignore) {
            }
        }
        return null;
    }

    /**
     * Crops the given bitmap according to the {@link EsService#CROP_WIDE} style. The
     * center of the bitmap is used to create a new bitmap of exactly width x height
     * pixels, maintaining the original aspect ratio. The original bitmap will be
     * cropped and/or enlarged as necessary.
     */
    private static Bitmap cropWideBitmap(Bitmap inputBitmap, int width, int height) {
        final Rect srcRect;

        final int srcWidth = inputBitmap.getWidth();
        final int srcHeight = inputBitmap.getHeight();
        final int dstWidth  = width;
        final int dstHeight = height;

        if (srcWidth == dstWidth && srcHeight == dstHeight) {
            // Photo is exactly the same size as the on-screen image
            srcRect = new Rect(0, 0, srcWidth, srcHeight);
        } else {
            // create a source rectangle of the same aspect ratio as the requested size.
            int cropWidth = srcWidth;
            int cropHeight = srcHeight;
            if (srcWidth * dstHeight > srcHeight * dstWidth) {
                // the input bitmap is a wider aspect ratio.  Crop the sides.
                cropWidth = srcHeight * dstWidth / dstHeight;
            } else {
                // The input bitmap is a taller aspect ratio.  Crop the top and bottom.
                cropHeight = srcWidth * dstHeight / dstWidth;
            }

            final int left = (srcWidth - cropWidth) / 2;
            final int top = (srcHeight - cropHeight) / 2;
            srcRect = new Rect(left, top, left + cropWidth, top + cropHeight);
        }

        // Create the new bitmap
        final Bitmap.Config bitmapConfig =
                ImageUtils.sUseLowResImages ? Bitmap.Config.RGB_565 : Bitmap.Config.ARGB_8888;
        final Bitmap bitmap = Bitmap.createBitmap(width, height, bitmapConfig);
        if (bitmap == null) {
            return null;
        }

        final Canvas canvas = new Canvas(bitmap);
        final Rect dstRect = new Rect(0, 0, width, height);

        synchronized (sCropPaint) {
            canvas.drawBitmap(inputBitmap, srcRect, dstRect, sCropPaint);
        }

        return bitmap;
    }

    /**
     * Gets the image bounds
     */
    private static Point getImageBounds(byte[] imageBytes) {
        final BitmapFactory.Options opts = new BitmapFactory.Options();
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(imageBytes);

        try {
            opts.inJustDecodeBounds = true;
            decodeStream(inputStream, null, opts);
            return new Point(opts.outWidth, opts.outHeight);
        } finally {
            try {
                inputStream.close();
            } catch (IOException ignore) {
            }
        }
    }

    /**
     * Create a center-cropped bitmap from a uri.
     *
     * @param resolver The ContentResolver
     * @param uri The uri
     * @param width The width of the output bitmap
     * @param height The height of the output bitmap
     *
     * @return the new bitmap
     */
    public static Bitmap createCroppedBitmap(ContentResolver resolver, Uri uri,
            int width, int height) {
        try {
            InputStream inputStream = resolver.openInputStream(uri);
            final BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            decodeStream(inputStream, null, opts);
            inputStream.close();

            // use Math.min() here to ensure that each of the image dimensions are
            // >= the target size
            inputStream = resolver.openInputStream(uri);
            opts.inJustDecodeBounds = false;
            opts.inSampleSize = Math.min(opts.outWidth / width, opts.outHeight / height);
            Bitmap srcBitmap = decodeStream(inputStream, null, opts);
            inputStream.close();
            if (srcBitmap == null) {
                return null;
            }
            final int srcWidth = srcBitmap.getWidth();
            final int srcHeight = srcBitmap.getHeight();

            if (srcWidth == width && srcHeight == height) {
                return srcBitmap;
            }

            Bitmap destBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            if (destBitmap == null) {
                srcBitmap.recycle();
                return null;
            }

            final Canvas canvas = new Canvas(destBitmap);
            int croppedWidth = srcWidth;
            int croppedHeight = srcHeight;
            // We want to take the center part of the image with the same aspect
            // ratio as the target, and crop the rest.  The same behavior as CENTER_CROP.
            if (srcWidth * height > srcHeight * width) {
                // The input bitmap is a wider aspect ratio.  Crop the sides.
                croppedWidth = srcHeight * width / height;
            } else {
                // The input bitmap is a taller aspect ratio.  Crop the top and bottom.
                croppedHeight = srcWidth * height / width;
            }
            final int left = (srcWidth - croppedWidth) / 2;
            final int top = (srcHeight - croppedHeight) / 2;
            final Rect src = new Rect(left, top, left + croppedWidth, top + croppedHeight);
            synchronized (sResizePaint) {
                canvas.drawBitmap(srcBitmap, src, new Rect(0, 0, width, height), sResizePaint);
            }
            srcBitmap.recycle();

            // correct orientation, as necessary
            return rotateBitmap(resolver, uri, destBitmap);
        } catch (FileNotFoundException exception) {
            return null;
        } catch (IOException exception) {
            return null;
        }
    }

    /**
     * Returns the maximum dimension in pixels for a given MediaStore.Images.Thumbnails kind.
     *
     * @param context The context
     * @param kind MICRO_KIND or MINI_KIND
     *
     * @return maxDimension in pixels
     */
    public static int getMaxThumbnailDimension(Context context, int kind) {
        // determine max dimension based on kind
        final int maxDimension;
        switch (kind) {
            case Thumbnails.MICRO_KIND:
                maxDimension = getThumbnailSize(context, Thumbnails.MICRO_KIND);
                break;

            case Thumbnails.MINI_KIND:
                maxDimension = getThumbnailSize(context, Thumbnails.MINI_KIND);
                break;

            default:
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "illegal kind=" + kind + " specified; using MINI_KIND");
                }
                maxDimension = getThumbnailSize(context, Thumbnails.MINI_KIND);
                break;
        }
        return maxDimension;
    }

    /**
     * Convert thumbnail dimensions to pixels
     *
     * @param context The context
     * @param kind The kind
     *
     * @return The size of the thumbnail in pixels
     */
    public static int getThumbnailSize(Context context, int kind) {
        switch (kind) {
            case Thumbnails.MICRO_KIND: {
                if (MICRO_KIND_MAX_DIMENSION == 0) {
                    MICRO_KIND_MAX_DIMENSION = context.getResources().getDimensionPixelSize(
                            R.dimen.micro_kind_max_dimension);
                }
                return MICRO_KIND_MAX_DIMENSION;
            }

            case Thumbnails.MINI_KIND:
            default: {
                if (MINI_KIND_MAX_DIMENSION == 0) {
                    MINI_KIND_MAX_DIMENSION = context.getResources().getDimensionPixelSize(
                            R.dimen.mini_kind_max_dimension);
                }
                return MINI_KIND_MAX_DIMENSION;
            }
        }
    }

    /**
     * Scale a bitmap to a square bitmap
     *
     * @param imageBytes The input bitmap
     * @param size The width and height
     *
     * @return The new bitmap
     */
    public static byte[] resizeToSquareBitmap(byte[] imageBytes, int size) {
        return resizeToSquareBitmap(imageBytes, size, NO_COLOR);
    }

    /**
     * Scale a bitmap to a square bitmap
     *
     * @param imageBytes The input bitmap
     * @param size The width and height
     * @param backgroundColor The background color that should be used for translucent avatars.
     *
     * @return The new bitmap
     */
    public static byte[] resizeToSquareBitmap(byte[] imageBytes, int size, int backgroundColor) {
        if (imageBytes == null) {
            return imageBytes;
        }

        final BitmapFactory.Options dbo = new BitmapFactory.Options();
        dbo.inJustDecodeBounds = true;
        decodeByteArray(imageBytes, 0, imageBytes.length, dbo);

        int nativeWidth = dbo.outWidth;
        int nativeHeight = dbo.outHeight;
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "resizeToSquareBitmap: Input: " + nativeWidth + "x" + nativeHeight
                    + ", resize to: " + size);
        }

        Bitmap bitmap;
        int sampleSize = Math.min(nativeWidth / size, nativeHeight / size);
        if (sampleSize > 1) {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sampleSize;
            bitmap = decodeByteArray(imageBytes, 0, imageBytes.length, options);
        } else {
            bitmap = decodeByteArray(imageBytes, 0, imageBytes.length);
        }

        if (bitmap == null) {
            return null;
        }

        Bitmap scaledBitmap = resizeToSquareBitmap(bitmap, size, backgroundColor);
        bitmap.recycle();

        if (scaledBitmap == null) {
            return null;
        }

        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        scaledBitmap.compress(CompressFormat.JPEG, 80, stream);
        scaledBitmap.recycle();
        scaledBitmap = null;

        return stream.toByteArray();
    }

    /**
     * Scale a bitmap to a square bitmap
     *
     * @param inputBitmap The input bitmap
     * @param size The width and height
     *
     * @return The new bitmap
     */
    public static Bitmap resizeToSquareBitmap(Bitmap inputBitmap, int size) {
        return resizeToSquareBitmap(inputBitmap, size, NO_COLOR);
    }

    /**
     * Scale a bitmap to a square bitmap
     *
     * @param inputBitmap The input bitmap
     * @param size The width and height
     * @param backgroundColor The solid color used to paint the image background. If
     *              {@link #NO_COLOR}, no background will be painted.
     *
     * @return The new bitmap
     */
    public static Bitmap resizeToSquareBitmap(Bitmap inputBitmap, int size,
            int backgroundColor) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "resizeToSquareBitmap: Input: " + inputBitmap.getWidth()
                    + "x" + inputBitmap.getHeight() + ", output:" + size + "x" + size);
        }

        final Bitmap bitmap;
        try {
            // Create the new bitmap
            bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        } catch (OutOfMemoryError e) {
            Log.w(TAG, "resizeToSquareBitmap OutOfMemoryError for image size: " + size, e);
            return null;
        }

        if (bitmap == null) {
            return null;
        }

        final Canvas canvas = new Canvas(bitmap);
        if (backgroundColor != NO_COLOR) {
            canvas.drawColor(backgroundColor);
        }

        if (inputBitmap.getWidth() != size || inputBitmap.getHeight() != size) {
            final Rect src = new Rect(0, 0, inputBitmap.getWidth(), inputBitmap.getHeight());
            final Rect dest = new Rect(0, 0, size, size);
            synchronized(sResizePaint) {
                canvas.drawBitmap(inputBitmap, src, dest, sResizePaint);
            }
        } else {
            canvas.drawBitmap(inputBitmap, 0, 0, null);
        }

        return bitmap;
    }

    /**
     * Resize and crop a bitmap.
     *
     * @param inputBitmap The input bitmap
     * @param height The height
     * @param width The width
     *
     * @return The new bitmap
     */
    public static Bitmap resizeAndCropBitmap(Bitmap inputBitmap, int width, int height) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "resizeAndCropBitmap: Input: " + inputBitmap.getWidth()
                    + "x" + inputBitmap.getHeight() + ", output:"
                    + width + "x" + height);
        }

        // Create the new bitmap
        final Bitmap bitmap = Bitmap.createBitmap(
                width, height, Bitmap.Config.ARGB_8888);
        if (bitmap == null) {
            return null;
        }

        final Canvas canvas = new Canvas(bitmap);
        if (inputBitmap.getWidth() != width || inputBitmap.getHeight() != height) {
            // create a source rectangle of the same aspect ratio as the requested size.
            int croppedWidth = inputBitmap.getWidth();
            int croppedHeight = inputBitmap.getHeight();
            if (inputBitmap.getWidth() * height > inputBitmap.getHeight() * width) {
                // the input bitmap is a wider aspect ratio.  Crop the sides.
                croppedWidth = inputBitmap.getHeight() * width / height;
            } else {
                // The input bitmap is a taller aspect ratio.  Crop the top and bottom.
                croppedHeight = inputBitmap.getWidth() * height / width;
            }

            int left = (inputBitmap.getWidth() - croppedWidth) / 2;
            int top = (inputBitmap.getHeight() - croppedHeight) / 2;
            final Rect src = new Rect(left, top,
                    left + croppedWidth, top + croppedHeight);
            final Rect dest = new Rect(0, 0, width, height);
            synchronized(sResizePaint) {
                canvas.drawBitmap(inputBitmap, src, dest, sResizePaint);
            }
        } else {
            canvas.drawBitmap(inputBitmap, 0, 0, null);
        }

        return bitmap;
    }

    /**
     * Resize a bitmap
     *
     * @param imageBytes The image bytes
     * @param width The width of the resized image
     * @param height The width of the resized image
     *
     * @return The resized bitmap
     */
    public static Bitmap resizeBitmap(byte[] imageBytes, int width, int height) {
        final BitmapFactory.Options dbo = new BitmapFactory.Options();
        dbo.inJustDecodeBounds = true;
        decodeByteArray(imageBytes, 0, imageBytes.length, dbo);

        final int nativeWidth = dbo.outWidth;
        final int nativeHeight = dbo.outHeight;
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "resizeBitmap: Input: " + nativeWidth + "x" + nativeHeight
                    + ", resize to: " + width + "x" + height);
        }

        final Bitmap srcBitmap;
        if (nativeWidth > width || nativeHeight > height) {
            final float bitmapWidth = (nativeWidth * width) / nativeHeight;
            final float bitmapHeight = (nativeHeight * height) / nativeWidth;

            if (nativeWidth / bitmapWidth > 1 || nativeHeight / bitmapHeight > 1) {
                // Create a scaled bitmap
                final BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = Math.max(nativeWidth / (int)bitmapWidth,
                        nativeHeight / (int)bitmapHeight);
                srcBitmap = decodeByteArray(imageBytes, 0, imageBytes.length, options);
            } else {
                srcBitmap = decodeByteArray(imageBytes, 0, imageBytes.length);
            }
        } else {
            srcBitmap = decodeByteArray(imageBytes, 0, imageBytes.length);
        }

        if (srcBitmap == null) {
            return null;
        }

        // Crop the bitmap
        final Bitmap croppedBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        if (croppedBitmap == null) {
            srcBitmap.recycle();
            return null;
        }

        final int srcWidth = srcBitmap.getWidth();
        final int srcHeight = srcBitmap.getHeight();

        int croppedWidth = srcWidth;
        int croppedHeight = srcHeight;
        if (nativeWidth * height > width * nativeHeight) {
            //  the input bitmap is a wider aspect ratio. Crop the sides.
            croppedWidth = srcBitmap.getHeight() * width / height;
        } else {
            // the input bitmap is a taller aspect ratio.  Crop the top and bottom.
            croppedHeight = srcBitmap.getWidth() * height / width;
        }

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "resizeBitmap: cropped: " + croppedWidth + "x" + croppedHeight);
        }

        final int srcLeft = (srcWidth - croppedWidth) / 2;
        final int srcTop = (srcHeight - croppedHeight) / 2;
        final Rect src = new Rect(srcLeft, srcTop, srcLeft + croppedWidth, srcTop + croppedHeight);
        final Rect dest = new Rect(0, 0, width, height);

        final Canvas croppedCanvas = new Canvas(croppedBitmap);
        croppedCanvas.drawColor(0xffe0e0e0);
        synchronized (sResizePaint) {
            croppedCanvas.drawBitmap(srcBitmap, src, dest, sResizePaint);
        }

        srcBitmap.recycle();

        return croppedBitmap;
    }

    /**
     * Resize the bitmap so that its height does not exceed the supplied value.
     *
     * @param imageBytes The image bytes
     * @param height The maximum height of the scaled image
     *
     * @return The resized bitmap as bytes
     */
    public static byte[] resizeBitmapToHeight(byte[] imageBytes, int height) {
        if (imageBytes == null) {
            return imageBytes;
        }

        final BitmapFactory.Options dbo = new BitmapFactory.Options();
        dbo.inJustDecodeBounds = true;
        decodeByteArray(imageBytes, 0, imageBytes.length, dbo);

        int nativeWidth = dbo.outWidth;
        int nativeHeight = dbo.outHeight;
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "scaleBitmap: Input: " + nativeWidth + "x" + nativeHeight
                    + ", resize to: " + height);
        }

        if (nativeHeight <= height) {
            return imageBytes;
        }

        int width = (int) ((float) nativeWidth / nativeHeight * height);
        Bitmap bitmap;
        if (nativeWidth / width > 1 || nativeHeight / height > 1) {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = Math.max(nativeWidth / width, nativeHeight / height);
            bitmap = decodeByteArray(imageBytes, 0, imageBytes.length, options);
            if (bitmap == null) {
                return null;
            }
            nativeWidth = bitmap.getWidth();
            nativeHeight = bitmap.getHeight();
        } else {
            bitmap = decodeByteArray(imageBytes, 0, imageBytes.length);
            if (bitmap == null) {
                return null;
            }
        }

        Bitmap scaledBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        if (scaledBitmap == null) {
            bitmap.recycle();
            return null;
        }

        final Canvas canvas = new Canvas(scaledBitmap);
        synchronized (sResizePaint) {
            canvas.drawBitmap(bitmap, new Rect(0, 0, nativeWidth, nativeHeight),
                    new Rect(0, 0, width, height), sResizePaint);
        }
        bitmap.recycle();
        bitmap = null;

        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        scaledBitmap.compress(CompressFormat.PNG, 100, stream);
        scaledBitmap.recycle();
        scaledBitmap = null;

        return stream.toByteArray();
    }

    /**
     * @param context The context
     * @return A {@link ProgressDialog} informing the user a photo is being
     *         inserted
     */
    public static Dialog createInsertCameraPhotoDialog(Context context) {
        final ProgressDialog dialog = new ProgressDialog(context);
        dialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        dialog.setCancelable(false);
        dialog.setMessage(context.getString(R.string.dialog_inserting_camera_photo));
        return dialog;
    }

    /**
     * Inserts a newly taken photo into the media store. We cannot directly use
     * {@code Images.Media#insertImage(ContentResolver, String, String, String)}
     * as this method will not properly set the photo's timestamp. Additionally,
     * any EXIF information in the original image is lost and there's a much higher
     * chance for an OOME as insertImage() actually decodes the JPEG just to
     * immediately re-encode it back to a JPEG.
     * <p>
     * NOTE: This code was shamelessly copied and merged from the Camera app
     * [see method addImage() in Storage.java] and Images.Media#insertImage().
     *
     * NOTE: This method should not be called from the UI thread. It performs
     * file IO and generates a thumbnail.
     *
     * @param context The context
     * @param filename The name of the photo
     * @return The media URL of the photo
     * @throws FileNotFoundException If the file is not found
     */
    public static String insertCameraPhoto(Context context, String filename)
            throws FileNotFoundException {
        final File f = new File(Environment.getExternalStorageDirectory(), filename);

        final long dateTaken = System.currentTimeMillis();
        final String photoName = createPhotoName(context, dateTaken);
        final ContentResolver resolver = context.getContentResolver();

        // Insert into MediaStore
        final ContentValues values = new ContentValues(5);
        final int orientation = ImageUtils.getExifRotation(resolver, f.getAbsolutePath());

        values.put(ImageColumns.TITLE, photoName);
        values.put(ImageColumns.DISPLAY_NAME, photoName + ".jpg");
        values.put(ImageColumns.DATE_TAKEN, dateTaken);
        values.put(ImageColumns.MIME_TYPE, "image/jpeg");
        values.put(ImageColumns.ORIENTATION, orientation);

        // TODO(kkiyohara): be smarter about figuring out what storage is available, or
        // maybe preventing the photo from being taken if the SD card (external storage)
        // is missing.
        Uri mediaUri;
        try {
            mediaUri = resolver.insert(Images.Media.EXTERNAL_CONTENT_URI, values);
        } catch (Exception e1) {
            // here when saving to external failed, try internal
            try {
                mediaUri = resolver.insert(Images.Media.INTERNAL_CONTENT_URI, values);
            } catch (Exception e2) {
                try {
                    // last chance, try save to HTC-specific PhoneStorage
                    mediaUri = resolver.insert(MediaStoreUtils.PHONE_STORAGE_IMAGES_URI, values);
                } catch (Exception e3) {
                    Log.e(TAG, "Failed to save image", e3);
                    return null;
                }
            }
        }

        try {
            // On some platforms this method may throw a NullPointerException
            final OutputStream imageOut = resolver.openOutputStream(mediaUri);
            final FileInputStream imageIn = new FileInputStream(f);

            try {
                final int downloadBufferSize = 10240;
                final byte[] array = new byte[downloadBufferSize];
                int bytesRead;

                do {
                    bytesRead = imageIn.read(array);
                    if (bytesRead == -1) {
                        break;
                    }
                    imageOut.write(array, 0, bytesRead);
                } while (true);
            } finally {
                imageOut.close();
            }

            // Wait until MINI_KIND thumbnail is generated.
            //
            // If Images.Media.EXTERNAL_CONTENT_URI is not writable, then
            // it is not possible to generate the thumbnail using public APIs.
            if (MediaStoreUtils.isExternalMediaStoreUri(mediaUri)) {
                Bitmap bmp = MediaStoreUtils.getThumbnail(
                        context, mediaUri, Images.Thumbnails.MINI_KIND);
                bmp.recycle();
                bmp = null;
            }
        } catch (FileNotFoundException fe) {
            Log.e(TAG, "File not found", fe);
            throw fe;
        } catch (Exception e) {
            Log.e(TAG, "Failed to insert image", e);
            if (mediaUri != null) {
                resolver.delete(mediaUri, null, null);
                mediaUri = null;
            }
        } finally {
            f.delete();
        }

        return (mediaUri == null ? null : mediaUri.toString());
    }

    /**
     * Returns a a name that is consistent with the Android camera application.
     */
    private static String createPhotoName(Context context, long dateTaken) {
        final Date date = new Date(dateTaken);
        final SimpleDateFormat dateFormat =
                new SimpleDateFormat(context.getString(R.string.image_file_name_format));

        return dateFormat.format(date);
    }

    /**
     * Gets a URL that can be used to download an image at the given size. The size specifies
     * the maximum width or height of the image. If the given URL is either a FIFE URL or an
     * Image Proxy URL, it will be modified to contain the proper sizing parameters. Otherwise,
     * the URL will be converted to an Image Proxy URL.
     *
     * @return A URL that can be used to retrieve an image of the given size.
     */
    public static String getResizedUrl(int size, String url) {
        if (FIFEUtil.isFifeHostedUrl(url)) {
            return FIFEUtil.setImageUrlSize(size, url, false);
        } else {
            return ImageProxyUtil.setImageUrlSize(size, url);
        }
    }

    /**
     * Gets a URL that can be used to download an image at the given size. The size specifies
     * the maximum width or height of the image. If the given URL is either a FIFE URL or an
     * Image Proxy URL, it will be modified to contain the proper sizing parameters. Otherwise,
     * the URL will be converted to an Image Proxy URL.
     *
     * @return A URL that can be used to retrieve an image of the given size.
     */
    public static String getResizedUrl(int width, int height, String url) {
        if (FIFEUtil.isFifeHostedUrl(url)) {
            return FIFEUtil.setImageUrlSize(width, height, url, false, false);
        } else {
            return ImageProxyUtil.setImageUrlSize(width, height, url);
        }
    }

    /**
     * See {@link #getCroppedAndResizedUrl(int, String)} for more information. This method
     * differs from getCroppedAndResizedUrl because it attempts to get a center cropped
     * version of the requested image. This is only possible for FIFE hosted URLs; Image
     * Proxy URLs will work as they do in getCroppedAndResizedUrl.
     *
     * @return A URL that can be used to retrieve an image of the given size.
     */
    public static String getCenterCroppedAndResizedUrl(int width, int height, String url) {
        if (url == null) {
            return null;
        }

        if (FIFEUtil.isFifeHostedUrl(url)) {
            final StringBuilder options = new StringBuilder();
            options.append("w").append(width);
            options.append("-h").append(height);
            options.append("-d");
            options.append("-n");
            return FIFEUtil.setImageUrlOptions(options.toString(), url).toString();
        } else {
            return ImageProxyUtil.setImageUrlSize(width, height, url);
        }
    }

    /**
     * See {@link #getResizedUrl(int, String)} for more information. This method differs
     * from getResizedUrl because it attempts to get a cropped version of the requested
     * image, meaning that for a given size, the returned image will be of dimension size
     * in both x and y. This is only possible for FIFE hosted URLs; Image Proxy URLs will
     * work as they do in getResizedUrl.
     *
     * @param size The size
     * @param url The URL
     * @return A URL that can be used to retrieve an image of the given size,
     *         cropped if possible.
     */
    public static String getCroppedAndResizedUrl(int size, String url) {
        if (FIFEUtil.isFifeHostedUrl(url)) {
            return FIFEUtil.setImageUrlSize(size, url, true);
        } else {
            // The image proxy has no facility to crop images
            return ImageProxyUtil.setImageUrlSize(size, url);
        }
    }

    /**
     * For some images, namely PNG images, the decode ignores the preferred config option and
     * always decodes them as 32bpp. On devices that will see the most benefit, we re-encode
     * the image as 16bpp. Otherwise, prefer to have greater fidelity in a PNG. The specified
     * bitmap will be recycled automatically as necessary.
     */
    public static Bitmap getLowResBitmap(Bitmap bitmap) {
        if (bitmap == null) {
            return null;
        }

        if (bitmap.getConfig() == Config.ARGB_8888) {
            final int width = bitmap.getWidth();
            final int height = bitmap.getHeight();
            final Bitmap lowResBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
            final Canvas canvas = new Canvas(lowResBitmap);
            final Rect src = new Rect(0, 0, width, height);
            final Rect dest = new Rect(0, 0, width, height);

            synchronized(sResizePaint) {
                canvas.drawBitmap(bitmap, src, dest, sResizePaint);
            }
            bitmap.recycle();
            return lowResBitmap;
        }
        return bitmap;
    }

    /**
     * Gets the image bounds
     *
     * @param resolver The ContentResolver
     * @param uri The uri
     *
     * @return The image bounds
     */
    private static Point getImageBounds(ContentResolver resolver, Uri uri)
            throws IOException {
        final BitmapFactory.Options opts = new BitmapFactory.Options();
        InputStream inputStream = null;

        try {
            opts.inJustDecodeBounds = true;
            inputStream = resolver.openInputStream(uri);
            decodeStream(inputStream, null, opts);

            return new Point(opts.outWidth, opts.outHeight);
        } finally {
            try {
                if (inputStream != null) {
                    inputStream.close();
                }
            } catch (IOException ignore) {
            }
        }
    }

    /**
     * Get the file path of a media item
     *
     * @return the filepath for a given MediaStore uri, or null if there was a
     *         problem
     */
    private static String getFilePath(ContentResolver resolver, Uri uri) {
        // Ask MediaStore for the actual file path
        final Cursor cursor = resolver.query(uri,
                new String[] {MediaColumns._ID, MediaColumns.DATA}, null, null, null);
        if (cursor == null) {
            Log.w(TAG, "getFilePath: query returned null cursor for uri=" + uri);
            return null;
        }

        String path = null;
        try {
            if (!cursor.moveToFirst()) {
                Log.w(TAG, "getFilePath: query returned empty cursor for uri=" + uri);
                return null;
            }

            // Get the file path
            path = cursor.getString(cursor.getColumnIndexOrThrow(MediaColumns.DATA));
            if (TextUtils.isEmpty(path)) {
                Log.w(TAG, "getFilePath: MediaColumns.DATA was empty for uri=" + uri);
                return null;
            }
        } finally {
            cursor.close();
        }

        return path;
    }

    /**
     * Encode the given image as a Base64 string (recycle the bitmap)
     *
     * @param imageBytes The image bytes
     *
     * @return A base64 encoded string
     */
    public static String encodeImageBytes(byte[] imageBytes) {
        String base64 = Base64.encodeToString(imageBytes, Base64.NO_WRAP);
        return "data:image/jpeg;base64," + base64;
    }

    /**
     * Decode an image from a Base64 string
     *
     * @param string A base64 encoded string
     *
     * @return The image bytes
     */
    public static byte[] decodeImageBytes(String string) {
        int start = string.indexOf("base64,");
        if (start == -1) {
            return null;
        }

        return Base64.decode(string.substring(start+7), Base64.DEFAULT);
    }

    /**
     * Compress the bitmap to JPEG and return the compressed image bytes. The given bitmap will
     * be recycled.
     *
     * @param bitmap The bitmap
     * @param quality the quality level for JPEG coding (90 is default).
     *
     * @return The compressed image bytes
     */
    public static byte[] compressBitmap(Bitmap bitmap, int quality) {
        final ByteArrayOutputStream stream = new ByteArrayOutputStream();
        try {
            bitmap.compress(CompressFormat.JPEG, quality, stream); // Copy #1
            stream.flush();
        } catch (IOException ignore) {
        } finally {
            try {
                stream.close();
            } catch (IOException ignore) {
            }
        }
        bitmap.recycle();
        bitmap = null;

        final byte[] imageBytes = stream.toByteArray(); // Copy #2
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "compressBitmap: Image size: " + imageBytes.length);
        }
        return imageBytes;
    }

    /**
     * Compress the bitmap to JPEG and return the compressed image bytes. The given bitmap will
     * be recycled.  A default quality level of 90 is used.
     *
     * @param bitmap The bitmap
     *
     * @return The compressed image bytes
     */
    public static byte[] compressBitmap(Bitmap bitmap) {
        return compressBitmap(bitmap, DEFAULT_JPEG_QUALITY);
    }

    /**
     * Retrieve the EXIF rotation of an image
     *
     * @param cr the content resolver, only used when the path given is an
     *      actual content uri.
     * @param path an absolute file path to the photo for which we want to get
     *      the rotation angle. Can also be a content uri, in which case
     *      the content resolver is used.
     *
     * @return the number of degrees an image needs to be rotated to face the
     *      "correct" way. Does this by reading the actual file's EXIF
     *       metadata.
     */
    private static int getExifRotation(ContentResolver cr, String path) {
        // create the Exif interface
        ExifInterface exif = null;
        try {
            exif = new ExifInterface(path);
        } catch (IOException e) {
            Log.w(TAG, "failed to create ExifInterface for " + path);
        }

        if (exif == null) {
            return 0;
        }

        // get and translate the orientation
        int orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);

        int degrees = 0;
        switch (orientation) {
            case ExifInterface.ORIENTATION_NORMAL:
                degrees = 0;
                break;

            case ExifInterface.ORIENTATION_ROTATE_90:
                degrees = 90;
                break;

            case ExifInterface.ORIENTATION_ROTATE_180:
                degrees = 180;
                break;

            case ExifInterface.ORIENTATION_ROTATE_270:
                degrees = 270;
                break;
        }

        return degrees;
    }

    /**
     * Rotate a bitmap based on the MediaStore uri's EXIF information.
     *
     * @param cr standard content resolver
     * @param uri MediaStore uri
     * @param bmp bitmap to rotated
     * @return bitmap with proper orientation
     */
    public static Bitmap rotateBitmap(ContentResolver cr, Uri uri, Bitmap bmp) {
        if (bmp != null) {
            final String path = getFilePath(cr, uri);
            final int degrees = getExifRotation(cr, path);
            if (degrees != 0) {
                bmp = rotateBitmap(bmp, degrees);
            }
        }
        return bmp;
    }

    /**
     * Bitmap rotation method
     *
     * @param bitmap The input bitmap
     * @param degrees The rotation angle
     */
    private static Bitmap rotateBitmap(Bitmap bitmap, int degrees) {
        if (degrees != 0 && bitmap != null) {
            final Matrix m = new Matrix();
            final int w = bitmap.getWidth();
            final int h = bitmap.getHeight();
            m.setRotate(degrees, (float) w / 2, (float) h / 2);

            try {
                final Bitmap rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, w, h, m, true);
                if (bitmap != rotatedBitmap) {
                    bitmap.recycle();
                    bitmap = rotatedBitmap;
                }
            } catch (OutOfMemoryError ex) {
                // We have no memory to rotate. Return the original bitmap.
            }
        }

        return bitmap;
    }
}
