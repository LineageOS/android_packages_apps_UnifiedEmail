/**
 * Copyright (c) 2011, Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.mail.ui;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;

import com.android.mail.providers.Account;
import com.android.mail.providers.Folder;
import com.android.mail.utils.LogUtils;
import com.android.mail.utils.LruCache;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * A self-updating list of folder canonical names for the N most recently touched folders, ordered
 * from least-recently-touched to most-recently-touched. N is a fixed size determined upon
 * creation.
 *
 * RecentFoldersCache returns lists of this type, and will keep them updated when observers are
 * registered on them.
 *
 */
public final class RecentFolderList {
    private static final String TAG = "RecentFolderList";
    /** The application context */
    private final Context mContext;
    /** The current account */
    private Account mAccount = null;

    private final LruCache<String, Folder> mFolderCache;
    /**
     *  We want to show five recent folders, and one space for the current folder (not displayed
     *  to the user).
     */
    private final static int NUM_FOLDERS = 5 + 1;

    /**
     * Compare based on alphanumeric name of the folder, ignoring case.
     */
    private static final Comparator<Folder> ALPHABET_IGNORECASE = new Comparator<Folder>() {
        @Override
        public int compare(Folder lhs, Folder rhs) {
            return lhs.name.compareToIgnoreCase(rhs.name);
        }
    };
    /**
     * Class to store the recent folder list asynchronously.
     */
    private class StoreRecent extends AsyncTask<Void, Void, Void> {
        final Account mAccount;
        final Folder mFolder;

        public StoreRecent(Account account, Folder folder) {
            mAccount = account;
            mFolder = folder;
        }

        @Override
        protected Void doInBackground(Void... v) {
            Uri uri = mAccount.recentFolderListUri;
            if (uri != null) {
                ContentValues values = new ContentValues();
                values.put(mFolder.uri.toString(), System.currentTimeMillis());
                // TODO: Remove when well tested
                LogUtils.i(TAG, "Save: " + mFolder.name);
                mContext.getContentResolver().update(uri, values, null, null);
            }
            return null;
        }
    }

    /**
     * Create a Recent Folder List from the given account. This will query the UIProvider to
     * retrieve the RecentFolderList from persistent storage (if any).
     * @param account
     */
    public RecentFolderList(Context context) {
        mFolderCache = new LruCache<String, Folder>(NUM_FOLDERS);
        mContext = context;
    }

    /**
     * Change the current account. This causes the recent label list to be written out to the
     * provider. When a cursor over the recent folders for this account is available, the client
     * <b>must</b> call {@link #loadFromUiProvider(Cursor)} with the updated cursor. Till then,
     * the recent account list will be empty.
     * @param account
     */
    public void setCurrentAccount(Account account) {
        mAccount = account;
        // At some point in the future, the load method will return and populate our cache with
        // useful entries. But for now, the cache is invalid.
        mFolderCache.clear();
    }

    /**
     * Load the account information from the UI provider given the cursor over the recent folders.
     * @param data a cursor over the recent folders.
     */
    public void loadFromUiProvider(Cursor data) {
        if (mAccount == null || data == null) {
            return;
        }
        int i = 0;
        while (data.moveToNext()) {
            Folder folder = new Folder(data);
            String folderUriString = folder.uri.toString();
            mFolderCache.putElement(folderUriString, folder);
            // TODO: Remove when well tested
            LogUtils.i(TAG, "Account " + mAccount.name + ", Recent: " + folder.name);
            i++;
            if (i >= NUM_FOLDERS)
                break;
        }
    }

    /**
     * Marks the given folder as 'accessed' by the user interface, its entry is updated in the
     * recent folder list, and the current time is written to the provider
     * @param folder the folder we have changed to.
     */
    public void touchFolder(Folder folder) {
        mFolderCache.putElement(folder.uri.toString(), folder);
        new StoreRecent(mAccount, folder).execute();
    }

    /**
     * Generate a sorted array of recent folders, excluding the specified folders.
     * @param exclude the folder to be excluded.
     */
    public Folder[] getSortedArray(Folder exclude) {
        // TODO: Need to exclude default inbox folder as well!
        final int spaceForCurrentFolder =
                (exclude != null && mFolderCache.getElement(exclude.uri.toString()) != null)
                        ? 1 : 0;
        final int numRecents = mFolderCache.size() - spaceForCurrentFolder;
        final Folder[] folders = new Folder[numRecents];
        int i = 0;
        final List<Folder> recent = new ArrayList<Folder>(mFolderCache.values());
        Collections.sort(recent, ALPHABET_IGNORECASE);
        for (Folder f : recent) {
            if (exclude == null || !f.uri.equals(exclude.uri)) {
                folders[i++] = f;
            }
        }
        return folders;
    }
}
