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
import com.android.mail.providers.AccountObserver;
import com.android.mail.providers.Folder;
import com.android.mail.providers.Settings;
import com.android.mail.utils.LogUtils;
import com.android.mail.utils.LruCache;
import com.android.mail.utils.Utils;

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
     *  We want to show at most five recent folders
     */
    private final static int MAX_RECENT_FOLDERS = 5;
    /**
     *  We exclude the default inbox for the account and the current folder; these might be the
     *  same, but we'll allow for both
     */
    private final static int MAX_EXCLUDED_FOLDERS = 2;

    private final AccountObserver mAccountObserver = new AccountObserver() {
        @Override
        public void onChanged(Account newAccount) {
            setCurrentAccount(newAccount);
        }
    };

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
        /**
         * Copy {@link RecentFolderList#mAccount} in case the account changes between when the
         * AsyncTask is created and when it is executed.
         */
        @SuppressWarnings("hiding")
        private final Account mAccount;
        private final Folder mFolder;

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
            final Uri uri = mAccount.recentFolderListUri;
            if (!Utils.isEmpty(uri)) {
                ContentValues values = new ContentValues();
                // Only the folder URIs are provided. Providers are free to update their specific
                // information, though most will probably write the current timestamp.
                values.put(mFolder.uri.toString(), 0);
                LogUtils.i(TAG, "Save: %s", mFolder.name);
                mContext.getContentResolver().update(uri, values, null, null);
            }
            return null;
        }
    }

    /**
     * Create a Recent Folder List from the given account. This will query the UIProvider to
     * retrieve the RecentFolderList from persistent storage (if any).
     * @param context
     */
    public RecentFolderList(Context context) {
        mFolderCache = new LruCache<String, Folder>(MAX_RECENT_FOLDERS + MAX_EXCLUDED_FOLDERS);
        mContext = context;
    }

    /**
     * Initialize the {@link RecentFolderList} with a controllable activity.
     * @param activity
     */
    public void initialize(ControllableActivity activity){
        setCurrentAccount(mAccountObserver.initialize(activity.getAccountController()));
    }

    /**
     * Change the current account. When a cursor over the recent folders for this account is
     * available, the client <b>must</b> call {@link #loadFromUiProvider(Cursor)} with the updated
     * cursor. Till then, the recent account list will be empty.
     * @param account the new current account
     */
    private void setCurrentAccount(Account account) {
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
        LogUtils.d(TAG, "Number of recents = %d", c.getCount());
        int i = 0;
        if (!c.moveToLast()) {
            LogUtils.d(TAG, "Not able to move to last in recent labels cursor");
            return;
        }
        // Add them backwards, since the most recent values are at the beginning in the cursor.
        // This enables older values to fall off the LRU cache. Also, read all values, just in case
        // there are duplicates in the cursor.
        do {
            final Folder folder = new Folder(c);
            mFolderCache.putElement(folder.uri.toString(), folder);
            LogUtils.v(TAG, "Account %s, Recent: %s", mAccount.name, folder.name);
        } while (c.moveToPrevious());
    }

    /**
     * Marks the given folder as 'accessed' by the user interface, its entry is updated in the
     * recent folder list, and the current time is written to the provider. This should never
     * be called with a null folder.
     * @param folder the folder we touched
     */
    public void touchFolder(Folder folder, Account account) {
        // We haven't got a valid account yet, cannot proceed.
        if (mAccount == null || !mAccount.equals(account)) {
            if (account != null) {
                setCurrentAccount(account);
            } else {
                LogUtils.w(TAG, "No account set for setting recent folders?");
                return;
            }
        }
        assert (folder != null);
        mFolderCache.putElement(folder.uri.toString(), folder);
        new StoreRecent(mAccount, folder).execute();
    }

    /**
     * Generate a sorted list of recent folders, excluding the passed in folder (if any) and
     * default inbox for the current account. This must be called <em>after</em>
     * {@link #setCurrentAccount(Account)} has been called.
     * Returns a list of size {@value #MAX_RECENT_FOLDERS} or smaller.
     * @param excludedFolderUri the uri of folder to be excluded (typically the current folder)
     */
    public ArrayList<Folder> getRecentFolderList(Uri excludedFolderUri) {
        final ArrayList<Uri> excludedUris = new ArrayList<Uri>();
        if (excludedFolderUri != null) {
            excludedUris.add(excludedFolderUri);
        }
        final Uri defaultInbox = (mAccount == null) ?
                Uri.EMPTY : Settings.getDefaultInboxUri(mAccount.settings);
        if (!defaultInbox.equals(Uri.EMPTY)) {
            excludedUris.add(defaultInbox);
        }
        final List<Folder> recent = new ArrayList<Folder>(mFolderCache.values());
        final ArrayList<Folder> recentFolders = new ArrayList<Folder>();
        for (final Folder f : recent) {
            if (!excludedUris.contains(f.uri)) {
                recentFolders.add(f);
            }
            if (recentFolders.size() == MAX_RECENT_FOLDERS) {
                break;
            }
        }
        // Sort the values as the very last step.
        Collections.sort(recentFolders, ALPHABET_IGNORECASE);
        return recentFolders;
    }

    /**
     * Destroys this instance. The object is unusable after this has been called.
     */
    public void destroy() {
        mAccountObserver.unregisterAndDestroy();
    }
}
