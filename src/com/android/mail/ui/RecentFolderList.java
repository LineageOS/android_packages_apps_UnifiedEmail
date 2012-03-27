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
import com.android.mail.providers.Settings;
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
    /** The AbstractActivityController that created us*/
    private final AbstractActivityController mController;
    /** The current account */
    private Account mAccount = null;

    private final LruCache<String, Folder> mFolderCache;
    /**
     *  We want to show at most five recent folders
     */
    private final static int MAX_RECENT_FOLDERS = 5;
    /**
     *  We exclude the default inbox for the account and the current folder; these might be the
     *  same, but we'll allow for both
     */
    private final static int MAX_EXCLUDED_FOLDERS = 2;

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

        /**
         * Create a new asynchronous task to store the recent folder list. Both the account
         * and the folder should be non-null.
         * @param account
         * @param folder
         */
        public StoreRecent(Account account, Folder folder) {
            assert (account != null && folder != null);
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
    public RecentFolderList(Context context, AbstractActivityController controller) {
        mFolderCache = new LruCache<String, Folder>(MAX_RECENT_FOLDERS);
        mContext = context;
        mController = controller;
    }

    /**
     * Change the current account. When a cursor over the recent folders for this account is
     * available, the client <b>must</b> call {@link #loadFromUiProvider(Cursor)} with the updated
     * cursor. Till then, the recent account list will be empty.
     * @param account the new current account
     */
    public void setCurrentAccount(Account account) {
        mAccount = account;
        mFolderCache.clear();
    }

    /**
     * Load the account information from the UI provider given the cursor over the recent folders.
     * @param c a cursor over the recent folders.
     */
    public void loadFromUiProvider(Cursor c) {
        if (mAccount == null || c == null) {
            return;
        }
        int i = 0;
        while (c.moveToNext()) {
            Folder folder = new Folder(c);
            mFolderCache.putElement(folder.uri.toString(), folder);
            // TODO: Remove when well tested
            LogUtils.i(TAG, "Account " + mAccount.name + ", Recent: " + folder.name);
            if (++i == (MAX_RECENT_FOLDERS + MAX_EXCLUDED_FOLDERS))
                break;
        }
    }

    /**
     * Marks the given folder as 'accessed' by the user interface, its entry is updated in the
     * recent folder list, and the current time is written to the provider. This should never
     * be called with a null folder.
     * @param folder the folder we touched
     */
    public void touchFolder(Folder folder) {
        // We haven't got a valid account yet, cannot proceed.
        if (mAccount == null) {
            LogUtils.w(TAG, "No account set for setting recent folders?");
            return;
        }
        assert (folder != null);
        mFolderCache.putElement(folder.uri.toString(), folder);
        new StoreRecent(mAccount, folder).execute();
    }

    /**
     * Generate a sorted list of recent folders, excluding the passed in folder (if any) and
     * the current account's default inbox
     * @param excludedFolder the folder to be excluded (typically the current folder)
     */
    public ArrayList<Folder> getRecentFolderList(Folder excludedFolder) {
        ArrayList<Uri> excludedUris = new ArrayList<Uri>();
        if (excludedFolder != null) {
            excludedUris.add(excludedFolder.uri);
        }
        Settings settings = mController.getSettings();
        if (settings != null) {
            // This could already be in the list, but that's ok
            excludedUris.add(settings.defaultInbox);
        }
        final List<Folder> recent = new ArrayList<Folder>(mFolderCache.values());
        Collections.sort(recent, ALPHABET_IGNORECASE);
        ArrayList<Folder> recentFolders = new ArrayList<Folder>();
        for (Folder f : recent) {
            if (!excludedUris.contains(f.uri)) {
                recentFolders.add(f);
            }
            if (recentFolders.size() == MAX_RECENT_FOLDERS) break;
        }
        return recentFolders;
    }
}
