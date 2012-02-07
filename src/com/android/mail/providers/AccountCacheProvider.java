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

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.provider.BaseColumns;
import android.text.TextUtils;

import com.google.common.collect.Maps;
import com.google.common.base.Objects;

import java.lang.Integer;
import java.lang.String;
import java.util.Map;


/**
 * The Account Cache provider allows email providers to register "accounts" and the UI has a single
 * place to query for the list of accounts.
 *
 * During development this will allow new account types to be added, and allow them to be shown in
 * the application.  For example, the mock accounts can be enabled/disabled.
 * In the future, once other processes can add new accounts, this could allow other "mail"
 * applications have their content appear within the application
 */
public abstract class AccountCacheProvider extends ContentProvider {

    private final static Map<String, CachedAccount> ACCOUNT_CACHE = Maps.newHashMap();

    private static String sAuthority;

    /**
     * Allows the implmenting provider to specify the authority that should be used.
     */
    protected abstract String getAuthority();


    public static Uri getAccountsUri() {
        return Uri.parse("content://" + sAuthority + "/");
    }

    @Override
    public boolean onCreate() {
        sAuthority = getAuthority();
        return true;
    }

    @Override
    public Cursor query(Uri url, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        // This content provider currently only supports one query (to return the list of accounts).
        // No reason to check the uri.  Currently only checking the projections

        // Validates and returns the projection that should be used.
        final String[] resultProjection = UIProviderValidator.validateAccountProjection(projection);
        final MatrixCursor cursor = new MatrixCursor(resultProjection);

        for (CachedAccount account : ACCOUNT_CACHE.values()) {
            final MatrixCursor.RowBuilder builder = cursor.newRow();

            for (String column : resultProjection) {
                if (TextUtils.equals(column, BaseColumns._ID)) {
                    builder.add(Integer.valueOf((int)account.mId));
                } else if (TextUtils.equals(column, UIProvider.AccountColumns.NAME)) {
                    builder.add(account.mName);
                } else if (TextUtils.equals(column, UIProvider.AccountColumns.PROVIDER_VERSION)) {
                    // TODO fix this
                    builder.add(Integer.valueOf(0));
                } else if (TextUtils.equals(column, UIProvider.AccountColumns.URI)) {
                    builder.add(account.mUri);
                } else if (TextUtils.equals(column, UIProvider.AccountColumns.CAPABILITIES)) {
                    builder.add(Integer.valueOf((int)account.mCapabilities));
                } else if (TextUtils.equals(column, UIProvider.AccountColumns.FOLDER_LIST_URI)) {
                    builder.add(account.mFolderListUri);
                } else if (TextUtils.equals(column, UIProvider.AccountColumns.SEARCH_URI)) {
                    builder.add(account.mSearchUri);
                } else if (TextUtils.equals(column,
                        UIProvider.AccountColumns.ACCOUNT_FROM_ADDRESSES_URI)) {
                    builder.add(account.mAccountFromAddressesUri);
                } else if (TextUtils.equals(column, UIProvider.AccountColumns.SAVE_DRAFT_URI)) {
                    builder.add(account.mSaveDraftUri);
                } else if (TextUtils.equals(column, UIProvider.AccountColumns.SEND_MAIL_URI)) {
                    builder.add(account.mSendMailUri);
                } else if (TextUtils.equals(column,
                        UIProvider.AccountColumns.EXPUNGE_MESSAGE_URI)) {
                    builder.add(account.mExpungeMessageUri);
                } else if (TextUtils.equals(column, UIProvider.AccountColumns.UNDO_URI)) {
                    builder.add(account.mUndoUri);
                } else {
                    throw new IllegalStateException("Column not found: " + column);
                }
            }

        }
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


    public static void addAccount(CachedAccount account) {
        synchronized (ACCOUNT_CACHE) {
            if (account != null) {
                ACCOUNT_CACHE.put(account.mUri, account);
            }
        }
    }

    public static void removeAccount(String accountUri) {
        synchronized (ACCOUNT_CACHE) {
            final CachedAccount account = ACCOUNT_CACHE.get(accountUri);

            if (account != null) {
                ACCOUNT_CACHE.remove(account);
            }
        }
    }

    public static class CachedAccount {
        private final long mId;
        private final String mName;
        private final String mUri;
        private final long mCapabilities;
        private final String mFolderListUri;
        private final String mSearchUri;
        private final String mAccountFromAddressesUri;
        private final String mSaveDraftUri;
        private final String mSendMailUri;
        private final String mExpungeMessageUri;
        private final String mUndoUri;

        public CachedAccount(long id, String name, String uri, long capabilities,
                String folderListUri, String searchUri, String fromAddressesUri,
                String saveDraftUri, String sendMailUri, String expungeMessageUri,
                String undoUri) {
            mId = id;
            mName = name;
            mUri = uri;
            mCapabilities = capabilities;
            mFolderListUri = folderListUri;
            mSearchUri = searchUri;
            mAccountFromAddressesUri = fromAddressesUri;
            mSaveDraftUri = saveDraftUri;
            mSendMailUri = sendMailUri;
            mExpungeMessageUri = expungeMessageUri;
            mUndoUri = undoUri;
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) {
                return true;
            }

            if ((o == null) || (o.getClass() != this.getClass())) {
                return false;
            }

            CachedAccount other = (CachedAccount) o;
            return mId == other.mId && TextUtils.equals(mName, other.mName) &&
                    TextUtils.equals(mUri, other.mUri) && mCapabilities == other.mCapabilities &&
                    TextUtils.equals(mFolderListUri, other.mFolderListUri) &&
                    TextUtils.equals(mSearchUri, other.mSearchUri) &&
                    TextUtils.equals(mAccountFromAddressesUri, other.mAccountFromAddressesUri) &&
                    TextUtils.equals(mSaveDraftUri, other.mSaveDraftUri) &&
                    TextUtils.equals(mSendMailUri, other.mSendMailUri) &&
                    TextUtils.equals(mExpungeMessageUri, other.mExpungeMessageUri) &&
                    TextUtils.equals(mUndoUri, other.mUndoUri);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(mId, mName, mUri, mCapabilities, mFolderListUri, mSearchUri,
                    mAccountFromAddressesUri, mSaveDraftUri, mSendMailUri, mExpungeMessageUri,
                    mUndoUri);
        }
    }
}