/*
 * Copyright (C) 2012 Google Inc.
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

package com.android.mail.ui;

import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;

import com.android.mail.providers.Attachment;
import com.android.mail.utils.LogTag;
import com.android.mail.utils.LogUtils;

import java.io.IOException;

/**
 * Performs the load of a thumbnail bitmap in a background
 * {@link AsyncTask}. Available for use with any view that implements
 * the {@link AttachmentBitmapHolder} interface.
 */
public class ThumbnailLoadTask extends AsyncTask<Uri, Void, Bitmap> {
    private static final String LOG_TAG = LogTag.getLogTag();

    private final AttachmentBitmapHolder mHolder;
    private final int mWidth;
    private final int mHeight;

    public static void setupThumbnailPreview(
            ThumbnailLoadTask task, final AttachmentBitmapHolder holder,
            final Attachment attachment, final Attachment prevAttachment) {
        final int width = holder.getThumbnailWidth();
        final int height = holder.getThumbnailHeight();
        if (attachment == null || width == 0 || height == 0) {
            holder.setThumbnailToDefault();
            return;
        }

        final Uri imageUri = attachment.getImageUri();
        final Uri prevImageUri = (prevAttachment == null) ? null : prevAttachment.getImageUri();
        // begin loading a thumbnail if this is an image and either the thumbnail or the original
        // content is ready (and different from any existing image)
        if (imageUri != null && (prevImageUri == null || !imageUri.equals(prevImageUri))) {
            // cancel/dispose any existing task and start a new one
            if (task != null) {
                task.cancel(true);
            }

            task = new ThumbnailLoadTask(
                    holder, width, height);
            task.execute(imageUri);
        } else if (imageUri == null) {
            // not an image, or no thumbnail exists. fall back to default.
            // async image load must separately ensure the default appears upon load failure.
            holder.setThumbnailToDefault();
        }
    }

    public ThumbnailLoadTask(AttachmentBitmapHolder holder, int width, int height) {
        mHolder = holder;
        mWidth = width;
        mHeight = height;
    }

    @Override
    protected Bitmap doInBackground(Uri... params) {
        final Uri thumbnailUri = params[0];

        AssetFileDescriptor fd = null;
        Bitmap result = null;

        try {
            fd = mHolder.getResolver().openAssetFileDescriptor(thumbnailUri, "r");
            if (isCancelled() || fd == null) {
                return null;
            }

            final BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;

            BitmapFactory.decodeFileDescriptor(fd.getFileDescriptor(), null, opts);
            if (isCancelled() || opts.outWidth == -1 || opts.outHeight == -1) {
                return null;
            }

            opts.inJustDecodeBounds = false;
            // Shrink both X and Y (but do not over-shrink)
            // and pick the least affected dimension to ensure the thumbnail is fillable
            // (i.e. ScaleType.CENTER_CROP)
            final int wDivider = Math.max(opts.outWidth / mWidth, 1);
            final int hDivider = Math.max(opts.outHeight / mHeight, 1);
            opts.inSampleSize = Math.min(wDivider, hDivider);

            LogUtils.d(LOG_TAG, "in background, src w/h=%d/%d dst w/h=%d/%d, divider=%d",
                    opts.outWidth, opts.outHeight, mWidth, mHeight, opts.inSampleSize);

            result = BitmapFactory.decodeFileDescriptor(fd.getFileDescriptor(), null, opts);

        } catch (Throwable t) {
            LogUtils.e(LOG_TAG, t, "Unable to decode thumbnail %s", thumbnailUri);
        } finally {
            if (fd != null) {
                try {
                    fd.close();
                } catch (IOException e) {
                    LogUtils.e(LOG_TAG, e, "");
                }
            }
        }

        return result;
    }

    @Override
    protected void onPostExecute(Bitmap result) {
        if (result == null) {
            LogUtils.d(LOG_TAG, "back in UI thread, decode failed");
            mHolder.setThumbnailToDefault();
            return;
        }

        LogUtils.d(LOG_TAG, "back in UI thread, decode success, w/h=%d/%d", result.getWidth(),
                result.getHeight());
        mHolder.setThumbnail(result);
    }

}
