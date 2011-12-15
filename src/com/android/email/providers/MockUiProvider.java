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

package com.android.email.providers;

import com.android.email.providers.UIProvider.AccountCapabilities;
import com.android.email.providers.UIProvider.AccountColumns;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.provider.BaseColumns;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.lang.Long;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class MockUiProvider extends ContentProvider {

    public static final String AUTHORITY = "com.android.mail.mockprovider";


    static final String BASE_URI_STRING = "content://" + AUTHORITY;

    // A map of query result for uris
    // TODO(pwestbro) read this map from an external
    private static final Map<String, List<Map<String, Object>>> MOCK_QUERY_RESULTS;

    static {
        ImmutableMap.Builder<String, List<Map<String, Object>>> builder = ImmutableMap.builder();

        // Add results for account list
        final List<Map<String, Object>> accountList = Lists.newArrayList();
        Map<String, Object> accountDetailsMap;

        // Account 1
        accountDetailsMap = createAccountDetailsMap(0);

        accountList.add(accountDetailsMap);
        String accountUri = (String)accountDetailsMap.get(AccountColumns.URI);
        builder.put(accountUri, ImmutableList.of(accountDetailsMap));

        // Account 2
        accountDetailsMap = createAccountDetailsMap(1);
        accountList.add(accountDetailsMap);

        accountUri = (String)accountDetailsMap.get(AccountColumns.URI);
        builder.put(accountUri, ImmutableList.of(accountDetailsMap));

        // Add the account list to the builder
        builder.put(getAccountsUri().toString(), accountList);

        MOCK_QUERY_RESULTS = builder.build();
    }

    private static Map<String, Object> createAccountDetailsMap(int accountId) {
        final String accountUri =  "content://" + AUTHORITY + "/account/" + accountId;
        Map<String, Object> accountMap = Maps.newHashMap();
        accountMap.put(BaseColumns._ID, Long.valueOf(accountId));
        accountMap.put(AccountColumns.NAME, "Account " + accountId);
        accountMap.put(AccountColumns.PROVIDER_VERSION, Long.valueOf(1));
        accountMap.put(AccountColumns.URI, accountUri);
        accountMap.put(AccountColumns.CAPABILITIES,
                Long.valueOf(
                        AccountCapabilities.SYNCABLE_FOLDERS |
                        AccountCapabilities.REPORT_SPAM |
                        AccountCapabilities.ARCHIVE |
                        AccountCapabilities.MUTE |
                        AccountCapabilities.SERVER_SEARCH |
                        AccountCapabilities.FOLDER_SERVER_SEARCH |
                        AccountCapabilities.SANITIZED_HTML |
                        AccountCapabilities.DRAFT_SYNCHRONIZATION |
                        AccountCapabilities.MULTIPLE_FROM_ADDRESSES |
                        AccountCapabilities.SMART_REPLY |
                        AccountCapabilities.LOCAL_SEARCH |
                        AccountCapabilities.THREADED_CONVERSATIONS));
        accountMap.put(AccountColumns.FOLDER_LIST_URI, accountUri + "/folders");
        accountMap.put(AccountColumns.SEARCH_URI, accountUri + "/search");
        accountMap.put(AccountColumns.ACCOUNT_FROM_ADDRESSES_URI, accountUri + "/fromAddresses");
        accountMap.put(AccountColumns.SAVE_NEW_DRAFT_URI, accountUri + "/saveDraft");
        accountMap.put(AccountColumns.SEND_MESSAGE_URI, accountUri + "/sendMessage");
        return accountMap;
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri url, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {

        // TODO (pwestbro): respect the projection that is specified by the caller

        final List<Map<String, Object>> queryResults =
                MOCK_QUERY_RESULTS.get(url.toString());

        if (queryResults != null && queryResults.size() > 0) {
            // Get the projection.  If there are rows in the result set, pick the first item to
            // generate the projection
            // TODO (pwestbro): handle the case where we want to return an empty result.\

            final Set<String> keys = queryResults.get(0).keySet();

            final String[] resultSetProjection = keys.toArray(new String[keys.size()]);
            MatrixCursor matrixCursor = new MatrixCursor(resultSetProjection, queryResults.size());

            for (Map<String, Object> queryResult : queryResults) {
                MatrixCursor.RowBuilder rowBuilder = matrixCursor.newRow();

                for (String key : keys) {
                    rowBuilder.add(queryResult.get(key));
                }
            }
            return matrixCursor;
        }

        return null;
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

    public static Uri getAccountsUri() {
        // TODO: this should probably return a different specific to the mock account list
        return Uri.parse(BASE_URI_STRING + "/");
    }
}

