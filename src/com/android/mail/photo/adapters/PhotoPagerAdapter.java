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

package com.android.mail.photo.adapters;

import android.content.Context;
import android.database.Cursor;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;

import com.android.mail.photo.Intents;
import com.android.mail.photo.Intents.PhotoViewIntentBuilder;
import com.android.mail.photo.Pageable;
import com.android.mail.photo.fragments.LoadingFragment;
import com.android.mail.photo.fragments.PhotoViewFragment;
import com.android.mail.photo.provider.PhotoContract;

/**
 * Pager adapter for the photo view
 */
public class PhotoPagerAdapter extends BaseCursorPagerAdapter {
    final Long mForceLoadId;
    private Pageable mPageable;
    private int mContentUriIndex;
    private int mPhotoNameIndex;

    public PhotoPagerAdapter(Context context, FragmentManager fm, Cursor c,
            Long forceLoadId) {
        super(context, fm, c);
        mForceLoadId = forceLoadId;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getCount() {
        if (mPageable != null && mPageable.hasMore()) {
            return super.getCount() + 1;
        }
        return super.getCount();
    }

    @Override
    public Fragment getItem(Context context, Cursor cursor) {
        final String photoUri = cursor.getString(mContentUriIndex);
        final String photoName = cursor.getString(mPhotoNameIndex);

        // create new PhotoViewFragment
        final PhotoViewIntentBuilder builder =
                Intents.newPhotoViewFragmentIntentBuilder(mContext);
          builder
            .setPhotoName(photoName)
            .setResolvedPhotoUri(photoUri)
            .setForceLoadId(mForceLoadId);

        return new PhotoViewFragment(builder.build(), cursor.getPosition());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Fragment getItem(int position) {
        final Cursor cursor = isDataValid() ? getCursor() : null;
        if (cursor != null && (cursor.isClosed() || position >= cursor.getCount())) {
            // Show the "loading" fragment while more data is loaded
            mPageable.loadMore();
            return new LoadingFragment();
        }
        return super.getItem(position);
    }

    /**
     * Sets the {@link Pageable}
     */
    public void setPageable(Pageable pageable) {
        mPageable = pageable;
    }

    @Override
    public Cursor swapCursor(Cursor newCursor) {
        mContentUriIndex =
                newCursor.getColumnIndex(PhotoContract.PhotoViewColumns.CONTENT_URI);
        mPhotoNameIndex =
                newCursor.getColumnIndex(PhotoContract.PhotoViewColumns.NAME);

        return super.swapCursor(newCursor);
    }
}
