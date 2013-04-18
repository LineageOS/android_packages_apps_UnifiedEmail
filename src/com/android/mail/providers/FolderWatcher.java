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

import com.google.common.collect.ImmutableList;

import android.app.LoaderManager;
import android.content.CursorLoader;
import android.content.Loader;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.widget.BaseAdapter;

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
public class FolderWatcher {
    public static final String FOLDER_URI = "FOLDER-URI";
    /** List of URIs that are watched. */
    private final List<Uri> mUris = new ArrayList<Uri>();
    /** Map returning the most recent unread count for each URI */
    private final Map<Uri, Integer> mUnreadCount = new HashMap<Uri, Integer>();
    private final RestrictedActivity mActivity;
    /** Handles folder callbacks and reads unread counts. */
    private final UnreadLoads mUnreadCallback = new UnreadLoads();

    /**
     * The adapter that consumes this data. We use this only to notify the consumer that new data
     * is available.
     */
    private BaseAdapter mConsumer;

    private final static String LOG_TAG = LogUtils.TAG;

    /**
     * Create a {@link FolderWatcher}.
     * @param activity Upstream activity
     * @param consumer If non-null, a consumer to be notified when the unread count changes
     */
    public FolderWatcher(RestrictedActivity activity, BaseAdapter consumer) {
        mActivity = activity;
        mConsumer = consumer;
    }

    /**
     * Start watching all the accounts in this list and stop watching accounts NOT on this list.
     * Does nothing if the list of all accounts is null.
     * @param allAccounts all the current accounts on the device.
     */
    public void updateAccountList(Account[] allAccounts) {
        if (allAccounts == null) {
            return;
        }
        // Create list of Inbox URIs from the array of accounts.
        final List<Uri> newAccounts = new ArrayList<Uri>(allAccounts.length);
        for (final Account account : allAccounts) {
            newAccounts.add(account.settings.defaultInbox);
        }
        // Stop watching accounts not in the new list.
        for (final Uri previous : ImmutableList.copyOf(mUris)) {
            if (!newAccounts.contains(previous)) {
                stopWatching(previous);
            }
        }
        // Add accounts in the new list, that are not already watched.
        for (final Uri fresh : newAccounts) {
            if (!mUris.contains(fresh)) {
                startWatching(fresh);
            }
        }
    }

    /**
     * Starts watching the given URI for changes. It is NOT safe to call this method repeatedly
     * for the same URI.
     * @param uri the URI for an inbox whose unread count is to be watched
     */
    private void startWatching(Uri uri) {
        final int location = insertAtNextEmptyLocation(uri);
        LogUtils.d(LOG_TAG, "Watching %s, at position %d.", uri, location);
        // No unread count yet, put a safe placeholder for now.
        mUnreadCount.put(uri, 0);
        final LoaderManager lm = mActivity.getLoaderManager();
        final Bundle args = new Bundle();
        args.putString(FOLDER_URI, uri.toString());
        lm.initLoader(getLoaderFromPosition(location), args, mUnreadCallback);
    }

    /**
     * Locates the next empty position in {@link #mUris} and inserts the URI there, returning the
     * location.
     * @return location where the URI was inserted.
     */
    private int insertAtNextEmptyLocation(Uri newElement) {
        Uri uri;
        int location = -1;
        for (int size = mUris.size(), i = 0; i < size; i++) {
            uri = mUris.get(i);
            // Hole in the list, use this position
            if (uri == null) {
                location = i;
                break;
            }
        }
        // No hole found, return the current size;
        if (location < 0) {
            location = mUris.size();
        }
        mUris.add(location, newElement);
        return location;
    }

    /**
     * Returns the loader ID for a position inside the {@link #mUris} table.
     * @param position position in the {@link #mUris} list
     * @return a loader id
     */
    private static int getLoaderFromPosition(int position) {
        return position + AbstractActivityController.LAST_LOADER_ID;
    }

    /**
     * Stops watching the given URI for folder changes. Subsequent calls to
     * {@link #getUnreadCount(Account)} for this uri will return null.
     * @param uri the URI for a folder
     */
    private void stopWatching(Uri uri) {
        final int id = mUris.indexOf(uri);
        // Does not exist in the list, we have stopped watching it already.
        if (id < 0) {
            return;
        }
        // Destroy the loader before removing references to the object.
        final LoaderManager lm = mActivity.getLoaderManager();
        lm.destroyLoader(getLoaderFromPosition(id));
        mUnreadCount.remove(uri);
        mUris.add(id, null);
    }

    /**
     * Returns the unread count for the default inbox for the account given. The account must be
     * watched with {@link #updateAccountList(Account[])}. If the account was not in an account
     * list passed previously, this method returns zero.
     * @param account an account whose unread count we wisht to track
     * @return the unread count if the account was in array passed previously to {@link
     * #updateAccountList(Account[])}. Zero otherwise.
     */
    public final int getUnreadCount(Account account) {
        final Uri uri = account.settings.defaultInbox;
        if (mUnreadCount.containsKey(uri)) {
            final Integer count = mUnreadCount.get(uri);
            if (count != null) {
                return count;
            }
        }
        return 0;
    }

    /**
     * Class to perform {@link LoaderManager.LoaderCallbacks} for populating unread counts.
     */
    private class UnreadLoads implements LoaderManager.LoaderCallbacks<Cursor> {
        // TODO(viki): Fix http://b/8494129 and read only the URI and unread count.
        /** Only interested in the folder unread count, but asking for everything due to
         * bug 8494129. */
        private final String[] projection = UIProvider.FOLDERS_PROJECTION;

        @Override
        public Loader<Cursor> onCreateLoader(int id, Bundle args) {
            final Uri uri = Uri.parse(args.getString(FOLDER_URI));
            return new CursorLoader(mActivity.getActivityContext(), uri, projection,
                    null, null, null);
        }

        @Override
        public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
            if (data == null || data.getCount() <= 0 || !data.moveToFirst()) {
                return;
            }
            final Uri uri = Uri.parse(data.getString(UIProvider.FOLDER_URI_COLUMN));
            final int unreadCount = data.getInt(UIProvider.FOLDER_UNREAD_COUNT_COLUMN);
            final Integer prevUnreadCount = mUnreadCount.get(uri);
            final boolean changed = prevUnreadCount == null ||
                    unreadCount != prevUnreadCount.intValue();
            mUnreadCount.put(uri, unreadCount);
            // Once we have updated data, we notify the parent class that something new appeared.
            if (changed) {
                mConsumer.notifyDataSetChanged();
            }
        }

        @Override
        public void onLoaderReset(Loader<Cursor> loader) {
            // Do nothing.
        }
    }
}
