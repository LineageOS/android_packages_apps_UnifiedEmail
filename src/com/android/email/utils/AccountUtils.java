/*
 * Copyright (C) 2012 The Android Open Source Project
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
package com.android.email.utils;

import com.android.email.providers.AccountCacheProvider;
import com.android.email.providers.UIProvider;

import android.accounts.Account;
import android.accounts.AccountManagerCallback;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;


import java.util.ArrayList;
import java.util.List;

public class AccountUtils {
    /**
     * Merge two lists of accounts into one list of accounts without duplicates.
     *
     * @param existingList List of accounts.
     * @param accounts Accounts to merge in.
     * @param prioritizeAccountList Boolean indicating whether this method
     *            should prioritize the list of Account objects when merging the
     *            lists
     * @return Merged list of accounts.
     */
    public static List<String> mergeAccountLists(List<String> existingList, Account[] accounts,
            boolean prioritizeAccountList) {

        List<String> newAccountList = new ArrayList<String>();
        // Make sure the accounts are actually synchronized
        // (we won't be able to save/send for accounts that
        // have never been synchronized)
        for (int i = 0; i < accounts.length; i++) {
            final String accountName = accounts[i].name;
            // If the account is in the cached list or the caller requested
            // that we prioritize the list of Account objects, put it in the new list
            if (prioritizeAccountList
                    || (existingList != null && existingList.contains(accountName))) {
                newAccountList.add(accountName);
            }
        }
        return newAccountList;
    }

    /**
     * In the future, this will get syncing accounts from the account manager.
     * Currently, it just gets all accounts from the UIProvider.
     * @param context
     * @param callback
     * @param type
     * @param features
     * @return
     */
    public static Account[] getSyncingAccounts(Context context,
            AccountManagerCallback<Account[]> callback, String type, String[] features) {
        // TODO: use account manager.
        // AccountManager.get(context).getAccountsByTypeAndFeatures(type, features, callback, null);
        ContentResolver resolver = context.getContentResolver();
        Cursor accountsCursor = resolver.query(AccountCacheProvider.getAccountsUri(),
                UIProvider.ACCOUNTS_PROJECTION, null, null, null);
        ArrayList<Account> accounts = new ArrayList<Account>();
        if (accountsCursor != null) {
            while (accountsCursor.moveToNext()) {
                accounts.add(new Account(accountsCursor.getString(UIProvider.ACCOUNT_NAME_COLUMN),
                        "unknown"));
            }
        }
        return accounts.toArray(new Account[accounts.size()]);
    }
}
