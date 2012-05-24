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

package com.android.mail.photo.loaders;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.support.v4.content.CursorLoader;
import android.util.Log;

/**
 * Cursor loader that automatically registers for notification on a URI.
 */
public class BaseCursorLoader extends CursorLoader {
    /** Whether or not a content observer has been registered */
    private boolean mObserverRegistered;
    /** Observer that force loads the cursor if the observed uri is notified */
    private final ForceLoadContentObserver mObserver = new ForceLoadContentObserver();
    /** The observed uri */
    private final Uri mNotificationUri;
    /** If {@code true}, this loader received an exception and it can no longer be used */
    private boolean mLoaderException;

    /**
     * @see CursorLoader#CursorLoader(Context)
     */
    public BaseCursorLoader(Context context) {
        this(context, null);
    }

    /**
     * @see CursorLoader#CursorLoader(Context)
     */
    public BaseCursorLoader(Context context, Uri notificationUri) {
        super(context);
        mNotificationUri = notificationUri;
    }

    /**
     * @see CursorLoader#CursorLoader(Context, Uri, String[], String, String[], String)
     */
    public BaseCursorLoader(Context context, Uri uri, String[] projection, String selection,
            String[] selectionArgs, String sortOrder) {
        this(context, uri, projection, selection, selectionArgs, sortOrder, null);
    }

   /**
    * @see CursorLoader#CursorLoader(Context, Uri, String[], String, String[], String)
    */
    public BaseCursorLoader(Context context, Uri uri, String[] projection, String selection,
            String[] selectionArgs, String sortOrder, Uri notificationUri) {
        super(context, uri, projection, selection, selectionArgs, sortOrder);
        mNotificationUri = notificationUri;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onStartLoading() {
        super.onStartLoading();
        if (!mObserverRegistered && mNotificationUri != null) {
            getContext().getContentResolver().registerContentObserver(mNotificationUri,
                    false, mObserver);
            mObserverRegistered = true;
        }
    }

    /**
     * Overriding the default behavior of CursorLoader, which currently leads to
     * skipping data loads.  See http://b/6028807
     */
    @Override
    protected void onStopLoading() {
    }

    /**
     * Loads data in a background thread.
     *
     * @see CursorLoader#loadInBackground()
     */
    public Cursor esLoadInBackground() {
        return super.loadInBackground();
    }

    /**
     * Override {@link #esLoadInBackground()} instead.
     *
     * {@inheritDoc}
     */
    @Override
    public final Cursor loadInBackground() {
        Cursor cursor;
        try {
            cursor = esLoadInBackground();
        } catch (Throwable ex) {
            Log.w("EsCursorLoader", "loadInBackground failed", ex);
            mLoaderException = true;
            cursor = null;
        }

        return cursor;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deliverResult(Cursor cursor) {
        // Only deliver results if the loader is active
        if (!mLoaderException) {
            super.deliverResult(cursor);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onAbandon() {
        if (mObserverRegistered) {
            getContext().getContentResolver().unregisterContentObserver(mObserver);
            mObserverRegistered = false;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onReset() {
        cancelLoad();
        super.onReset();
        onAbandon();
    }
}
