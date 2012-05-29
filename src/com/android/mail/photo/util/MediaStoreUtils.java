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

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.provider.BaseColumns;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utilities for MediaStore.
 */
public class MediaStoreUtils {
    public static final String TAG = "MediaStoreUtils";

    // Special HTC-only MediaStore storage volume
    public static final Uri PHONE_STORAGE_IMAGES_URI =
            MediaStore.Images.Media.getContentUri("phoneStorage");

    public static final Uri PHONE_STORAGE_VIDEO_URI =
            MediaStore.Video.Media.getContentUri("phoneStorage");

    /**
     * Define constants for Video info query.
     */
    @SuppressWarnings("unused")
    private static interface VideoQuery {
        /** Projection of the VideoQuery cursors */
        public static final String[] PROJECTION = {
            BaseColumns._ID,
            MediaStore.Video.VideoColumns.DURATION,
            MediaStore.Video.VideoColumns.RESOLUTION,
        };

        public static final int INDEX_ID = 0;
        public static final int INDEX_DURATION_MSEC = 1;
        public static final int INDEX_RESOLUTION = 2;
    }

    /** regex used to parse video resolution "XxY" -- never trust MediaStore! */
    private static final Pattern PAT_RESOLUTION = Pattern.compile("(\\d+)[xX](\\d+)");

    /**
     * Prevent instantiation
     */
    private MediaStoreUtils() {
    }

    /**
     * Check if a URI is from the MediaStore
     *
     * @param uri The URI
     */
    public static boolean isMediaStoreUri(Uri uri) {
        return uri != null
                && ContentResolver.SCHEME_CONTENT.equals(uri.getScheme())
                && MediaStore.AUTHORITY.equals(uri.getAuthority());
    }

    /**
     * Checks if a {@link Uri} is an external {@link MediaStore} URI.
     * <p>
     * The {@code getThumbnail} methods of {@link MediaStore} are hard-coded to
     * only support external media URIs. There is an API for loading internal
     * thumbnails, but it is not public and the code cannot be copied easily.
     *
     * @param uri a content URI.
     * @return {@code true} if the {@link Uri} belongs to {@link MediaStore} and
     *         is external, {@code false} otherwise.
     * @throws NullPointerException if the argument is {@code null}.
     * @see android.provider.MediaStore.Images.Media#EXTERNAL_CONTENT_URI
     * @see android.provider.MediaStore.Video.Media#EXTERNAL_CONTENT_URI
     */
    public static boolean isExternalMediaStoreUri(Uri uri) {
        if (isMediaStoreUri(uri)) {
            String path = uri.getPath();
            String externalImagePrefix = MediaStore.Images.Media.EXTERNAL_CONTENT_URI.getPath();
            String externalVideoPrefix = MediaStore.Video.Media.EXTERNAL_CONTENT_URI.getPath();
            return path.startsWith(externalImagePrefix) || path.startsWith(externalVideoPrefix);
        } else {
            return false;
        }
    }

    /**
     * @return true if the MimeType type is image
     */
    public static boolean isImageMimeType(String mimeType) {
        return mimeType != null && mimeType.startsWith("image/");
    }

    /**
     * @return true if the MimeType type is video
     */
    public static boolean isVideoMimeType(String mimeType) {
        return mimeType != null && mimeType.startsWith("video/");
    }

    /**
     * Gets the MediaStore thumbnail bitmap for an image or video.
     *
     * @param context this can be an Application Context
     * @param uri image or video Uri
     * @param kind MediaStore.{Images|Video}.Thumbnails.MINI_KIND or MICRO_KIND
     * @return thumbnail bitmap or null
     */
    public static Bitmap getThumbnail(Context context, Uri uri, int kind) {
        // determine actual pixel dimensions
        final int microSize = ImageUtils.getMaxThumbnailDimension(context, kind);
        return getThumbnailHelper(context, uri, microSize, microSize, kind);
    }

    /**
     * Gets the MediaStore thumbnail bitmap for an image or video.
     *
     * @param context this can be an Application Context
     * @param uri image or video Uri
     * @param width desired output width
     * @param height desired output height
     * @return thumbnail bitmap or null
     */
    public static Bitmap getThumbnail(Context context, Uri uri, int width, int height) {
        // determine if we want mini or micro thumbnails
        final int microSize = ImageUtils.getMaxThumbnailDimension(context,
                MediaStore.Images.Thumbnails.MICRO_KIND);
        int kind = (width > microSize || height > microSize)
                ? MediaStore.Images.Thumbnails.MINI_KIND
                : MediaStore.Images.Thumbnails.MICRO_KIND;

        return getThumbnailHelper(context, uri, width, height, kind);
    }

    /**
     * Deletes the MediaStore entry and, as necessary on some pre-ICS devices, corresponding
     * native file
     *
     * @param resolver context reolver
     * @param localContentUri image or video Uri
     * @return true if delete succeeds, false otherwise
     */
    public static boolean deleteLocalFileAndMediaStore(ContentResolver resolver,
            Uri localContentUri) {
        final String filePath = MediaStoreUtils.getFilePath(resolver, localContentUri);

        boolean status = resolver.delete(localContentUri, null, null) == 1;

        if (status && filePath != null) {
            final File file = new File(filePath);
            if (file.exists()) {
                status = file.delete();
            }
        }

        return status;
    }

    /**
     * Safe method to retrieve mimetype of a content Uri.
     *
     * On some phones, getType() can throw an exception for no good reason.
     *
     * @param resolver is a standard ContentResolver
     * @param uri is a the target content Uri
     * @return valid mime-type; null if type was unknown or an exception was thrown
     */
    public static String safeGetMimeType(ContentResolver resolver, Uri uri) {
        String mimeType = null;
        try {
            mimeType = resolver.getType(uri);
        } catch (Exception e) {
            if (Log.isLoggable(TAG, Log.WARN)) {
                Log.w(TAG, "safeGetMimeType failed for uri=" + uri, e);
            }
        }
        return mimeType;
    }

//    /**
//     * Converts a MediaStore video Uri to VideoData proto byte array.
//     *
//     * @param context can be an ApplicationContext
//     * @param uri is a MediaStore Video Uri
//     * @return byte[] proto byte array, or null if Uri is not a MediaStore video
//     */
//    public static byte[] toVideoDataBytes(Context context, Uri uri) {
//        final VideoData videoData = toVideoData(context, uri);
//        return videoData == null ? null : videoData.toByteArray();
//    }
//
//    /**
//     * Converts a MediaStore video Uri to an array of VideoData proto.
//     *
//     * @param context can be an ApplicationContext
//     * @param uri is a MediaStore Video Uri
//     * @return VideoData proto byte array, or null if Uri is not a MediaStore video
//     */
//    public static VideoData toVideoData(Context context, Uri uri) {
//        // see if this is a video
//        final ContentResolver cr = context.getContentResolver();
//        if (!MediaStoreUtils.isVideoMimeType(safeGetMimeType(cr, uri))) {
//            return null;
//        }
//
//        // format VideoStream info
//        final VideoStream.Builder vs = VideoStream.newBuilder();
//        vs.setStreamUrl(uri.toString());
//
//        // 0 == unknown format, see ContentHeader.VideoFormat.INVALID_VIDEO_FORMAT
//        vs.setFormatId(0);
//
//        // query for resolution -- string formatted as "XxY"
//        int width = 0;
//        int height = 0;
//        long durationMsec = 0L;
//        final Cursor cursor = cr.query(uri, VideoQuery.PROJECTION, null, null, null);
//        if (cursor != null) {
//            try {
//                if (cursor.moveToFirst()) {
//                    durationMsec = cursor.getLong(VideoQuery.INDEX_DURATION_MSEC);
//
//                    final String resolution = cursor.getString(VideoQuery.INDEX_RESOLUTION);
//                    if (resolution != null) {
//                        final Matcher m = PAT_RESOLUTION.matcher(resolution);
//                        if (m.find()) {
//                            width = Integer.parseInt(m.group(1));
//                            height = Integer.parseInt(m.group(2));
//                        }
//                    }
//                }
//            } finally {
//                cursor.close();
//            }
//        }
//        vs.setVideoWidth(width);
//        vs.setVideoHeight(height);
//
//        // manufacture VideoData bytes
//        final VideoData vd = VideoData.newBuilder()
//                .setStatus(VideoStatus.FINAL)
//                .setDuration(durationMsec)
//                .addStream(vs)
//                .build();
//        return vd;
//    }

    /**
     * @return the file path for a given MediaStore uri, or null if there was a problem
     */
    private static String getFilePath(ContentResolver cr, Uri uri) {
        // ask MediaStore for the actual file path
        Cursor cursor = cr.query(uri, new String [] {MediaStore.MediaColumns.DATA},
                null, null, null);
        if (cursor == null) {
            if (Log.isLoggable(TAG, Log.WARN)) {
                Log.w(TAG, "getFilePath: query returned null cursor for uri=" + uri);
            }
            return null;
        }

        String path = null;
        try {
            if (!cursor.moveToFirst()) {
                if (Log.isLoggable(TAG, Log.WARN)) {
                    Log.w(TAG, "getFilePath: query returned empty cursor for uri=" + uri);
                }
                return null;
            }
            // read the file path
            path = cursor.getString(0);
            if (TextUtils.isEmpty(path)) {
                if (Log.isLoggable(TAG, Log.WARN)) {
                    Log.w(TAG, "getFilePath: MediaColumns.DATA was empty for uri=" + uri);
                }
                return null;
            }

        } finally {
            cursor.close();
        }
        return path;
    }

    /**
     * Gets the MediaStore thumbnail bitmap for an image or video.
     *
     * @param context this can be an Application Context
     * @param uri image or video URI
     * @param width desired output width
     * @param height desired output height
     * @param kind MediaStore.{Images|Video}.Thumbnails.MINI_KIND or MICRO_KIND
     * @return the thumb nail image, or {@code null}
     */
    private static Bitmap getThumbnailHelper(
            Context context, Uri uri, int width, int height, int kind) {
        // guard against bogus Uri's
        if (uri == null) {
            return null;
        }

        // Thumb nails are only available for external media URIs
        if (!isExternalMediaStoreUri(uri)) {
            return null;
        }

        final ContentResolver cr = context.getContentResolver();
        final long id = ContentUris.parseId(uri);

        // query the appropriate MediaStore thumb nail provider
        final String mimeType = safeGetMimeType(cr, uri);
        Bitmap bmp;
        if (isImageMimeType(mimeType)) {
            bmp = MediaStore.Images.Thumbnails.getThumbnail(cr, id, kind, null);

        } else if (isVideoMimeType(mimeType)) {
            bmp = MediaStore.Video.Thumbnails.getThumbnail(cr, id, kind, null);

        } else {
            if (Log.isLoggable(TAG, Log.WARN)) {
                Log.w(TAG, "getThumbnail: unrecognized mimeType=" + mimeType + ", uri=" + uri);
            }
            return null;
        }

        // if we got the thumb nail, we still have to rotate and crop as necessary
        if (bmp != null) {
            bmp = ImageUtils.rotateBitmap(cr, uri, bmp);

            if (bmp.getWidth() != width || bmp.getHeight() != height) {
                final Bitmap resizedBitmap = ImageUtils.resizeAndCropBitmap(
                        bmp, width, height);
                bmp.recycle();
                bmp = resizedBitmap;
            }
        }
        return bmp;
    }
}
