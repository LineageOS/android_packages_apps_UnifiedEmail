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

import android.text.TextUtils;

import com.android.mail.providers.Account;
import com.android.mail.providers.Folder;
import com.android.mail.utils.LogUtils;
import com.android.mail.utils.LruCache;

import java.util.Collections;
import java.util.ArrayList;
import java.util.List;
import java.util.Comparator;

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
    private static final String LOG_TAG = new LogUtils().getLogTag();
    /**
     * Compare based on alphanumeric name of the folder, ignoring case.
     */
    private static final Comparator<Folder> ALPHABET_IGNORECASE = new Comparator<Folder>() {
        @Override
        public int compare(Folder lhs, Folder rhs) {
            return lhs.name.compareToIgnoreCase(rhs.name);
        }
    };
    private final LruCache<String, Folder> mFolderCache;

    /**
     * Create a Recent Folder List from the given account. This will query the UIProvider to
     * retrieve the RecentFolderList from persistent storage (if any).
     * @param account
     */
    public RecentFolderList(Account account) {
        // We want to show five recent folders, and one space for the current folder (not displayed
        // to user).
        final int NUM_ACCOUNTS = 5 + 1;
        mFolderCache = new LruCache<String, Folder>(NUM_ACCOUNTS);
    }

    /**
     * Changes the current folder and returns the updated list of recent folders, <b>not</b>
     * including the current folder.
     * @param folder the folder we have changed to.
     */
    public Folder[] changeCurrentFolder(Folder folder) {
        mFolderCache.putElement(folder.id, folder);
        return getSortedArray(folder);
    }

    /**
     * Requests the UIProvider to save this RecentFolderList to persistent storage.
     */
    public void save() {
        // TODO: Implement
    }

    /**
     * Generate a sorted array of recent folders, <b>not</b> including the current folder.
     * @param currentFolder the current folder being displayed.
     */
    public Folder[] getSortedArray(Folder currentFolder) {
        final int spaceForCurrentFolder =
                (currentFolder != null && mFolderCache.getElement(currentFolder.id) != null)
                        ? 1 : 0;
        final int numRecents = mFolderCache.size() - spaceForCurrentFolder;
        final Folder[] folders = new Folder[numRecents];
        int i = 0;
        final List<Folder> recent = new ArrayList<Folder>(mFolderCache.values());
        Collections.sort(recent, ALPHABET_IGNORECASE);
        for (Folder f : recent) {
            if (!TextUtils.equals(f.id, currentFolder.id)) {
                folders[i++] = f;
            }
        }
        return folders;
    }
}
