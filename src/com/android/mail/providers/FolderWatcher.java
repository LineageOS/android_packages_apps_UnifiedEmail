/*******************************************************************************
 *      Copyright (C) 2012 Google Inc.
 *      Licensed to The Android Open Source Project.
 *
 *      Licensed under the Apache License, Version 2.0 (the "License");
 *      you may not use this file except in compliance with the License.
 *      You may obtain a copy of the License at
 *
 *           http://www.apache.org/licenses/LICENSE-2.0
 *
 *      Unless required by applicable law or agreed to in writing, software
 *      distributed under the License is distributed on an "AS IS" BASIS,
 *      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *      See the License for the specific language governing permissions and
 *      limitations under the License.
 *******************************************************************************/

package com.android.mail.providers;

import android.app.LoaderManager;
import android.content.CursorLoader;
import android.content.Loader;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

import com.android.mail.ui.AbstractActivityController;
import com.android.mail.ui.RestrictedActivity;
import com.android.mail.utils.LogUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A container to keep a list of Folder objects, with the ability to automatically keep in sync with
 * the folders in the providers.
 */
public class FolderWatcher implements LoaderManager.LoaderCallbacks<Cursor>{
    /** List of URIs that are watched. */
    private final List<Uri> mUri = new ArrayList<Uri>();
    /** Map returning the most recent folder object for each URI */
    private final Map<Uri, Folder> mFolder = new HashMap<Uri, Folder>();
    private final RestrictedActivity mActivity;

    private final static String LOG_TAG = LogUtils.TAG;

    /**
     * Create a {@link FolderWatcher}.
     * @param activity
     */
    public FolderWatcher(RestrictedActivity activity) {
        mActivity = activity;
    }

    /**
     * Starts watching the given URI for changes. It is safe to call {@link #startWatching(Uri)}
     * repeatedly for the same URI.
     * @param uri
     */
    public void startWatching(Uri uri) {
        // If the URI is already watched, nothing to do.
        if (uri == null || mFolder.containsKey(uri)) {
            return;
        }
        // This is the ID of the new URI: always at the end of the list.
        final int id = mUri.size();
        LogUtils.d(LOG_TAG, "Watching %s, at position %d.", uri, id);
        mFolder.put(uri, null);
        mUri.add(uri);
        final LoaderManager lm = mActivity.getLoaderManager();
        lm.initLoader(getLoaderFromPosition(id), null, this);
    }

    /**
     * Returns the loader ID for a position inside the {@link #mUri} table.
     * @param position
     * @return
     */
    private static final int getLoaderFromPosition(int position) {
        return position + AbstractActivityController.LAST_LOADER_ID;
    }

    /**
     * Returns the position inside the {@link #mUri} table from a loader ID.
     * @param loaderId
     * @return
     */
    private static final int getPositionFromLoader(int loaderId) {
        return loaderId - AbstractActivityController.LAST_LOADER_ID;
    }

    /**
     * Stops watching the given URI for folder changes. Subsequent calls to {@link #get(Uri)} for
     * this uri will return null.
     * @param uri
     */
    public void stopWatching(Uri uri) {
        final int id = mUri.indexOf(uri);
        // Does not exist in the list, safely back out.
        if (id < 0) {
            return;
        }
        // Destroy the loader before removing references to the object.
        final LoaderManager lm = mActivity.getLoaderManager();
        lm.destroyLoader(getLoaderFromPosition(id));
        mFolder.remove(id);
        mUri.remove(id);
    }

    /**
     * Returns the updated folder for the given URI. The URI must be watched with
     * {@link #startWatching(Uri)}. If the URI is not watched, this method returns null.
     * @param uri
     * @return
     */
    public Folder get(Uri uri) {
        return mFolder.get(uri);
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        final int position = getPositionFromLoader(id);
        if (position < 0 || position > mUri.size()) {
            return null;
        }
        return new CursorLoader(mActivity.getActivityContext(), mUri.get(position),
                UIProvider.FOLDERS_PROJECTION, null, null, null);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        if (data == null || data.getCount() <= 0 || !data.moveToFirst()) {
            return;
        }
        final Uri uri = mUri.get(getPositionFromLoader(loader.getId()));
        final Folder folder = new Folder(data);
        mFolder.put(uri, folder);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> arg0) {
        // Do nothing.
    }
}
