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

package com.android.mail.photo.content;

import android.text.TextUtils;

import com.android.mail.photo.util.ImageUtils;

/**
 * A request for a media image of a specific size.
 */
public class MediaImageRequest extends ImageRequest {

    private final String mUrl;
    private final String mMediaType;
    private final int mWidth;
    private final int mHeight;
    private final boolean mCropAndResize;

    private String mDownloadUrl;
    private int mHashCode;

    public MediaImageRequest() {
        this(null, null, 0, 0, false);
    }

    public MediaImageRequest(String url, String mediaType, int size) {
        this(url, mediaType, size, size, true);
    }

    public MediaImageRequest(
            String url, String mediaType, int width, int height, boolean cropAndResize) {
        if (url == null) {
            throw new NullPointerException();
        }

        mUrl = url;
        mMediaType = mediaType;
        mWidth = width;
        mHeight = height;
        mCropAndResize = cropAndResize;
    }

    /**
     * @return the original URL
     */
    public String getUrl() {
        return mUrl;
    }

    /**
     * @return the URL
     */
    public String getDownloadUrl() {
        if (mDownloadUrl == null) {
            if (!mCropAndResize || mWidth == 0) {
                mDownloadUrl = mUrl;
            } else if (mWidth == mHeight) {
                mDownloadUrl = ImageUtils.getCroppedAndResizedUrl(mWidth, mUrl);
            } else {
                mDownloadUrl = ImageUtils.getCenterCroppedAndResizedUrl(mWidth, mHeight, mUrl);
            }

            if (mDownloadUrl.startsWith("//")) {
                mDownloadUrl = "http:" + mDownloadUrl;
            }
        }
        return mDownloadUrl;
    }

    /**
     * @return the media type
     */
    public String getMediaType() {
        return mMediaType;
    }

    /**
     * @return the width
     */
    public int getWidth() {
        return mWidth;
    }

    /**
     * @return the height
     */
    public int getHeight() {
        return mHeight;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isEmpty() {
        return mUrl == null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        if (mHashCode == 0) {
            if (mUrl != null) {
                mHashCode = mUrl.hashCode();
            } else {
                mHashCode = 1;
            }
        }
        return mHashCode;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object o) {
        if (!(o instanceof MediaImageRequest)) {
            return false;
        }

        MediaImageRequest k = (MediaImageRequest) o;
        return mWidth == k.mWidth && mHeight == k.mHeight
                && TextUtils.equals(mUrl, k.mUrl)
                && TextUtils.equals(mMediaType, k.mMediaType);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "MediaImageRequest: " + mMediaType + " " + mUrl + " (" + mWidth
                + ", " + mHeight + ")";
    }
}
