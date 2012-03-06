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

package com.android.mail.providers;

import com.android.mail.providers.Account;
import com.android.mail.providers.protos.boot.AccountReceiver;

import android.content.Intent;
import android.content.Loader;
import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Loader.OnLoadCompleteListener;
import android.content.SharedPreferences;
import com.android.mail.utils.LogUtils;

import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.provider.BaseColumns;
import android.text.TextUtils;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import java.lang.IllegalStateException;
import java.lang.Override;
import java.util.Collections;
import java.util.Map;
import java.util.Set;


/**
 * The Account Cache provider allows email providers to register "accounts" and the UI has a single
 * place to query for the list of accounts.
 *
 * During development this will allow new account types to be added, and allow them to be shown in
 * the application.  For example, the mock accounts can be enabled/disabled.
 * In the future, once other processes can add new accounts, this could allow other "mail"
 * applications have their content appear within the application
 */
public abstract class AccountCacheProvider extends ContentProvider
        implements OnLoadCompleteListener<Cursor>{

    private static final String SHARED_PREFERENCES_NAME = "AccountCacheProvider";
    private static final String ACCOUNT_LIST_KEY = "accountList";

    private final static String LOG_TAG = new LogUtils().getLogTag();

    private final Map<Uri, Account> mAccountCache = Maps.newHashMap();

    // Map from content provider query uri to the set of account uri that resulted from that query
    private final static Map<Uri, Set<Uri>> QUERY_URI_ACCOUNT_URIS_MAP = Maps.newHashMap();

    private final Map<Uri, CursorLoader> mCursorLoaderMap = Maps.newHashMap();

    private ContentResolver mResolver;
    private static String sAuthority;
    private static AccountCacheProvider sInstance;

    private SharedPreferences mSharedPrefs;

    /**
     * Allows the implmenting provider to specify the authority that should be used.
     */
    protected abstract String getAuthority();

    public static Uri getAccountsUri() {
        return Uri.parse("content://" + sAuthority + "/");
    }

    public static AccountCacheProvider getInstance() {
        return sInstance;
    }

    @Override
    public boolean onCreate() {
        sInstance = this;
        sAuthority = getAuthority();
        mResolver = getContext().getContentResolver();

        final Intent intent = new Intent(AccountReceiver.ACTION_PROVIDER_CREATED);
        getContext().sendBroadcast(intent);

        // Load the previously saved account list
        loadCachedAccountList();

        return true;
    }

    @Override
    public void shutdown() {
        sInstance = null;

        for (CursorLoader loader : mCursorLoaderMap.values()) {
            loader.stopLoading();
        }
        mCursorLoaderMap.clear();
    }

    @Override
    public Cursor query(Uri url, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        // This content provider currently only supports one query (to return the list of accounts).
        // No reason to check the uri.  Currently only checking the projections

        // Validates and returns the projection that should be used.
        final String[] resultProjection = UIProviderValidator.validateAccountProjection(projection);
        final MatrixCursor cursor = new MatrixCursor(resultProjection);

        // Make a copy of the account cache

        final Set<Account> accountList;
        synchronized (mAccountCache) {
            accountList = ImmutableSet.copyOf(mAccountCache.values());
        }
        for (Account account : accountList) {
            final MatrixCursor.RowBuilder builder = cursor.newRow();

            for (String column : resultProjection) {
                if (TextUtils.equals(column, BaseColumns._ID)) {
                    // TODO(pwestbro): remove this as it isn't used.
                    builder.add(Integer.valueOf(0));
                } else if (TextUtils.equals(column, UIProvider.AccountColumns.NAME)) {
                    builder.add(account.name);
                } else if (TextUtils.equals(column, UIProvider.AccountColumns.PROVIDER_VERSION)) {
                    // TODO fix this
                    builder.add(Integer.valueOf(account.providerVersion));
                } else if (TextUtils.equals(column, UIProvider.AccountColumns.URI)) {
                    builder.add(account.uri);
                } else if (TextUtils.equals(column, UIProvider.AccountColumns.CAPABILITIES)) {
                    builder.add(Integer.valueOf(account.capabilities));
                } else if (TextUtils.equals(column, UIProvider.AccountColumns.FOLDER_LIST_URI)) {
                    builder.add(account.folderListUri);
                } else if (TextUtils.equals(column, UIProvider.AccountColumns.SEARCH_URI)) {
                    builder.add(account.searchUri);
                } else if (TextUtils.equals(column,
                        UIProvider.AccountColumns.ACCOUNT_FROM_ADDRESSES_URI)) {
                    builder.add(account.accountFromAddressesUri);
                } else if (TextUtils.equals(column, UIProvider.AccountColumns.SAVE_DRAFT_URI)) {
                    builder.add(account.saveDraftUri);
                } else if (TextUtils.equals(column, UIProvider.AccountColumns.SEND_MAIL_URI)) {
                    builder.add(account.sendMessageUri);
                } else if (TextUtils.equals(column,
                        UIProvider.AccountColumns.EXPUNGE_MESSAGE_URI)) {
                    builder.add(account.expungeMessageUri);
                } else if (TextUtils.equals(column, UIProvider.AccountColumns.UNDO_URI)) {
                    builder.add(account.undoUri);
                } else if (TextUtils.equals(column,
                        UIProvider.AccountColumns.SETTINGS_INTENT_URI)) {
                    builder.add(account.settingsIntentUri);
                } else if (TextUtils.equals(column,
                        UIProvider.AccountColumns.SETTINGS_QUERY_URI)) {
                    builder.add(account.settingsQueryUri);
                } else if (TextUtils.equals(column,
                        UIProvider.AccountColumns.HELP_INTENT_URI)) {
                    builder.add(account.helpIntentUri);
                } else if (TextUtils.equals(column, UIProvider.AccountColumns.SYNC_STATUS)) {
                    builder.add(Integer.valueOf(account.syncStatus));
                } else if (TextUtils.equals(column, UIProvider.AccountColumns.COMPOSE_URI)) {
                    builder.add(account.composeIntentUri);
                } else if (TextUtils.equals(column, UIProvider.AccountColumns.MIME_TYPE)) {
                    builder.add(account.mimeType);
                } else if (TextUtils.equals(column,
                        UIProvider.AccountColumns.RECENT_FOLDER_LIST_URI)) {
                    builder.add(account.recentFolderListUri);
                } else {
                    throw new IllegalStateException("Column not found: " + column);
                }
            }
        }

        cursor.setNotificationUri(mResolver, getAccountsUri());
        return cursor;
    }

    @Override
    public Uri insert(Uri url, ContentValues values) {
        return url;
    }

    @Override
    public int update(Uri url, ContentValues values, String selection,
            String[] selectionArgs) {
        return 0;
    }

    @Override
    public int delete(Uri url, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    /**
     * Asynchronously ads all of the accounts that are specified by the result set returned by
     * {@link ContentProvider#query()} for the specified uri.  The content provider handling the
     * query needs to handle the {@link UIProvider.ACCOUNTS_PROJECTION}
     * Any changes to the underlying provider will automatically be reflected.
     * @param resolver
     * @param accountsQueryUri
     */
    public static void addAccountsForUriAsync(Uri accountsQueryUri) {
        getInstance().startAccountsLoader(accountsQueryUri);
    }

    private synchronized void startAccountsLoader(Uri accountsQueryUri) {
        final CursorLoader accountsCursorLoader = new CursorLoader(getContext(), accountsQueryUri,
                UIProvider.ACCOUNTS_PROJECTION, null, null, null);

        // Listen for the results
        accountsCursorLoader.registerListener(accountsQueryUri.hashCode(), this);
        accountsCursorLoader.startLoading();

        // If there is a previous loader for the given uri, stop it
        final CursorLoader oldLoader = mCursorLoaderMap.get(accountsQueryUri);
        if (oldLoader != null) {
            oldLoader.stopLoading();
        }
        mCursorLoaderMap.put(accountsQueryUri, accountsCursorLoader);
    }

    public static void addAccount(Account account) {
        final AccountCacheProvider provider = getInstance();
        if (provider == null) {
            throw new IllegalStateException("AccountCacheProvider not intialized");
        }
        provider.addAccountImpl(account);
    }

    private void addAccountImpl(Account account) {
        synchronized (mAccountCache) {
            if (account != null) {
                LogUtils.v(LOG_TAG, "adding account %s", account);
                mAccountCache.put(account.uri, account);
            }
        }
        // Explicitly calling this out of the synchronized block in case any of the observers get
        // called synchronously.
        broadcastAccountChange();

        // Cache the updated account list
        cacheAccountList();
    }

    public static void removeAccount(Uri accountUri) {
        final AccountCacheProvider provider = getInstance();
        if (provider == null) {
            throw new IllegalStateException("AccountCacheProvider not intialized");
        }
        provider.removeAccounts(Collections.singleton(accountUri));
    }

    private void removeAccounts(Set<Uri> uris) {
        synchronized (mAccountCache) {
            for (Uri accountUri : uris) {
                mAccountCache.remove(accountUri);
            }
        }

        // Explicitly calling this out of the synchronized block in case any of the observers get
        // called synchronously.
        broadcastAccountChange();

        // Cache the updated account list
        cacheAccountList();
    }

    private static void broadcastAccountChange() {
        final AccountCacheProvider provider = sInstance;

        if (provider != null) {
            provider.mResolver.notifyChange(getAccountsUri(), null);
        }
    }

    private void loadCachedAccountList() {
        final SharedPreferences preference = getPreferences();

        final Set<String> accountsStringSet = preference.getStringSet(ACCOUNT_LIST_KEY, null);

        if (accountsStringSet != null) {
            for (String serializedAccount : accountsStringSet) {
                try {
                    final Account account = new Account(serializedAccount);
                    addAccount(account);
                } catch (IllegalArgumentException e) {
                    // Unable to create account object, skip to next
                    LogUtils.e(LOG_TAG,
                            "Unable to create account object from serialized string'%s'",
                            serializedAccount);
                }
            }
        }
    }

    private void cacheAccountList() {
        final SharedPreferences preference = getPreferences();

        final Set<Account> accountList;
        synchronized (mAccountCache) {
            accountList = ImmutableSet.copyOf(mAccountCache.values());
        }

        final Set<String> serializedAccounts = Sets.newHashSet();
        for (Account account : accountList) {
            serializedAccounts.add(account.serialize());
        }

        final SharedPreferences.Editor editor = getPreferences().edit();
        editor.putStringSet(ACCOUNT_LIST_KEY, serializedAccounts);
        editor.apply();
    }

    private SharedPreferences getPreferences() {
        if (mSharedPrefs == null) {
            mSharedPrefs = getContext().getSharedPreferences(
                    SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
        }
        return mSharedPrefs;
    }



    @Override
    public void onLoadComplete(Loader<Cursor> loader, Cursor data) {
        if (data == null) {
            LogUtils.d(LOG_TAG, "null account cursor returned");
            return;
        }

        LogUtils.d(LOG_TAG, "Cursor with %d accounts returned", data.getCount());
        final CursorLoader cursorLoader = (CursorLoader)loader;
        final Uri accountsQueryUri = cursorLoader.getUri();
        // TODO(pwestbro):
        // 1) Keep a cache of Cursors which would allow changes to be observered.
        final Set<Uri> previousQueryUriMap = QUERY_URI_ACCOUNT_URIS_MAP.get(accountsQueryUri);

        final Set<Uri> newQueryUriMap = Sets.newHashSet();
        while (data.moveToNext()) {
            final Account account = new Account(data);
            final Uri accountUri = account.uri;
            newQueryUriMap.add(accountUri);
            addAccount(account);
        }

        // Save the new set, or remove the previous entry if it is empty
        if (newQueryUriMap.size() > 0) {
            QUERY_URI_ACCOUNT_URIS_MAP.put(accountsQueryUri, newQueryUriMap);
        } else {
            QUERY_URI_ACCOUNT_URIS_MAP.remove(accountsQueryUri);
        }

        if (previousQueryUriMap != null) {
            // Remove all of the accounts that are in the new result set
            previousQueryUriMap.removeAll(newQueryUriMap);

            // For all of the entries that had been in the previous result set, and are not
            // in the new result set, remove them from the cache
            if (previousQueryUriMap.size() > 0) {
                removeAccounts(previousQueryUriMap);
            }
        }
    }
}