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

import android.net.Uri;

/**
 * A request for a media image of a specific size.
 */
public class LocalImageRequest extends ImageRequest {
    private final Uri mUri;
    private final int mWidth;
    private final int mHeight;

    private int mHashCode;

    public LocalImageRequest(int width, int height) {
        mUri = null;
        mWidth = width;
        mHeight = height;
    }

    /**
     * @return the original Uri
     */
    public Uri getUri() {
        return mUri;
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
        return mUri == null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        if (mHashCode == 0) {
            int result = 17;
            result = 31 * result + mUri.hashCode();
            result = 31 * result + mWidth;
            result = 31 * result + mHeight;
            mHashCode = result;
        }
        return mHashCode;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }

        if (!(o instanceof LocalImageRequest)) {
            return false;
        }

        final LocalImageRequest other = (LocalImageRequest) o;
        return (mUri.equals(other.mUri) &&
                mWidth == other.mWidth &&
                mHeight == other.mHeight);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "LocalImageRequest: " + mUri.toString() + " (" + mWidth + ", " + mHeight + ")";
    }
}
