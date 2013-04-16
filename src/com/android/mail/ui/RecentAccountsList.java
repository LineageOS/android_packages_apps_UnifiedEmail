/**
 * Copyright (c) 2013, Google Inc.
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

import android.content.Context;
import android.os.AsyncTask;
import android.text.TextUtils;

import com.android.mail.preferences.MailPrefs;
import com.android.mail.providers.Account;
import com.android.mail.utils.LogUtils;

import java.util.HashMap;
import java.util.LinkedList;

/**
 * A list for the most recently touched account uris, ordered
 * from least-recently-touched to most-recently-touched. Note, the list does
 * not limit the amount of accounts you can have ordered.
 *
 * The accounts cache returns lists of this type, and will keep them updated when observers are
 * registered on them.
 *
 */
public final class RecentAccountsList {
    private static final String TAG = "RecentAccountsList";
    /** Storage for accounts */
    private final MailPrefs mMailPrefs;
    /** Separator for serializing the mAccountsCache into a string */
    private static final String SEPARATOR = "\n";
    /** Separator for splitting the newline-delimited string into an array */
    private static final String SEPARATOR_REGEX = "\\n";
    /** List of account URIs from least to most recently used */
    private final LinkedList<String> mAccountsCache;

    /**
     * Class to store the recent accounts list asynchronously.
     */
    private class StoreRecent extends AsyncTask<Void, Void, Void> {
        /** Account to store in Mail Prefs */
        private final String mAccountUris;

        /**
         * Create a new asynchronous task to store the recent folder list. Both the account
         * and the folder should be non-null.
         * @param accountUris list of account URIs in order
         */
        public StoreRecent(final String accountUris) {
            assert (accountUris != null);
            mAccountUris = accountUris;
        }

        /**
         * Commit changes to MailPrefs which will save the uri string with the
         * respective key
         */
        @Override
        protected Void doInBackground(Void... v) {
            mMailPrefs.setRecentAccountUris(mAccountUris);
            return null;
        }
    }

    /**
     * Create a RecentAccountsList from the given context. Saves the MailPrefs
     * in order to write and read recent accounts to preferences.
     *
     * @param context the context for the activity
     */
    public RecentAccountsList(Context context) {
        mAccountsCache = new LinkedList<String>();
        mMailPrefs = MailPrefs.get(context);
        String accountUris = mMailPrefs.getRecentAccountUris();
        // If the accountUris string is empty, there is nothing to add to the list
        if(!TextUtils.isEmpty(accountUris)) {
            populateAccountsCache(accountUris);
        }
    }

    /**
     * Populate the LinkedList using input account uris that are separated by newlines.
     *
     * @param accountUris list of account uris ordered by least to most recently used
     */
    private void populateAccountsCache(final String accountUris) {
        // Take apart string by the newline character as URIs do not contain them
        final String[] allUris = accountUris.split(SEPARATOR_REGEX);

        // For every uri saved in the prefs, add it to the linked list in order
        // since we want to add most recently used accounts at the -end-.
        for (String accountUri : allUris) {
            mAccountsCache.addLast(accountUri);
        }
    }

    /**
     * Marks the given account as 'accessed' by the user interface, its entry is
     * updated in the recent accounts list and pushed to the end.
     *
     * @param account the account we touched
     */
    public void touchAccount(final Account account) {
        // Invalid account, cannot proceed
        if (account == null) {
            LogUtils.w(TAG, "No account set: touchAccount(null)");
            return;
        }

        // Remove the accountUri touched from somewhere in the LinkedList
        // (if it exists) and add it to the end of the list
        final String accountUri = account.uri.toString();
        mAccountsCache.remove(accountUri);
        mAccountsCache.addLast(accountUri);
        commitCacheChanges();
    }

    /**
     * Takes in an array of unsorted Account objects and orders them from least
     * recently used to most recently used. Takes in account that there may be
     * Accounts that are not in the cache or stale URIs that are still remaining
     * in the cache.
     *
     * Do NOT call this more than necessary. It may affect performance for large
     * number of accounts.
     *
     * @param unsortedAccounts array of Accounts to be ordered
     * @return ordered array of Accounts from least to most recently used, possibly empty.
     */
    public Account[] getSorted(final Account[] unsortedAccounts) {
        if(unsortedAccounts == null) {
            return new Account[0];
        }
        // Create a sortedAccounts list that will collect the final order of accounts
        final LinkedList<Account> sortedAccounts = new LinkedList<Account>();
        // Map for uri -> Account object for quick lookup
        final HashMap<String, Account> accountsMap = new HashMap<String, Account>();
        int currCacheIndex = 0;
        String accountUri;
        Account currAccount;

        // Add unsortedAccounts to map
        for (final Account account : unsortedAccounts) {
            accountsMap.put(account.uri.toString(), account);
        }

        // Traverse cached list and populate sortedAccounts or fix cache if
        // the account URIs are not found in the map
        while (currCacheIndex < mAccountsCache.size()) {
            accountUri = mAccountsCache.get(currCacheIndex);
            currAccount = accountsMap.get(accountUri);
            if (currAccount != null) {
                // If account is found in the map of unsortedAccounts, add to final result
                // and remove from temp map
                sortedAccounts.addLast(currAccount);
                accountsMap.remove(accountUri);
                currCacheIndex++;
            } else {
                // Otherwise, remove from the cache and don't update the index
                mAccountsCache.remove(accountUri);
            }
        }

        // For any accounts that weren't in the cache, add them to the cache as
        // least recently used and the final results as well.
        for (final Account uncachedAccount : accountsMap.values()) {
            mAccountsCache.addFirst(uncachedAccount.uri.toString());
            sortedAccounts.addFirst(uncachedAccount);
        }

        //Return Account array based on final list
        return sortedAccounts.toArray(new Account[sortedAccounts.size()]);
    }

    /**
     * Writes out the account URIs to {@link MailPrefs} - used when destroying
     * object or wanting to commit {@link RecentAccountsList#mAccountsCache}
     * contents.
     */
    public void commitCacheChanges() {
        final String accountUris = TextUtils.join(SEPARATOR,
                mAccountsCache.toArray(new String[mAccountsCache.size()]));
        new StoreRecent(accountUris).executeOnExecutor(AsyncTask.SERIAL_EXECUTOR);
    }
}
