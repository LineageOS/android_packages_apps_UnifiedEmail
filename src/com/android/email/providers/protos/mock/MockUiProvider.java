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

package com.android.email.providers.protos.mock;

import com.android.email.providers.UIProvider.AccountCapabilities;
import com.android.email.providers.UIProvider.AccountColumns;
import com.android.email.providers.UIProvider.ConversationColumns;
import com.android.email.providers.UIProvider.FolderCapabilities;
import com.android.email.providers.UIProvider.FolderColumns;
import com.android.email.providers.UIProvider.MessageColumns;

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
import java.util.Date;
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
        Map<String, Object> accountDetailsMap1;

        // Account 1
        accountDetailsMap1 = createAccountDetailsMap(0);

        accountList.add(accountDetailsMap1);
        String accountUri1 = (String)accountDetailsMap1.get(AccountColumns.URI);
        builder.put(accountUri1, ImmutableList.of(accountDetailsMap1));

        // Account 2
        Map<String, Object> accountDetailsMap2 = createAccountDetailsMap(1);
        accountList.add(accountDetailsMap2);

        String accountUri2 = (String)accountDetailsMap2.get(AccountColumns.URI);
        builder.put(accountUri2, ImmutableList.of(accountDetailsMap2));

        // Add the account list to the builder
        builder.put(getAccountsUri().toString(), accountList);

        Map<String, Object> folderDetailsMap0 = createFolderDetailsMap(0, "zero", true);
        builder.put(
                folderDetailsMap0.get(FolderColumns.CHILD_FOLDERS_LIST_URI).toString(),
                ImmutableList.of(createFolderDetailsMap(10, "zeroChild0"),
                        createFolderDetailsMap(11, "zeroChild1")));

        Map<String, Object> conv0 = createConversationDetailsMap("zeroConv0".hashCode(),
                "zeroConv0");
        Map<String, Object> conv1 = createConversationDetailsMap("zeroConv1".hashCode(),
                "zeroConv1");
        builder.put(folderDetailsMap0.get(FolderColumns.CONVERSATION_LIST_URI).toString(),
                ImmutableList.of(conv0, conv1));

        Map<String, Object> message0 = createMessageDetailsMap("zeroConv0".hashCode(), "zeroConv0");
        builder.put(conv0.get(ConversationColumns.MESSAGE_LIST_URI).toString(),
                ImmutableList.of(message0));
        Map<String, Object> message1 = createMessageDetailsMap("zeroConv1".hashCode(), "zeroConv1");
        builder.put(conv1.get(ConversationColumns.MESSAGE_LIST_URI).toString(),
                ImmutableList.of(message1));

        Map<String, Object> folderDetailsMap1 = createFolderDetailsMap(1, "one");
        builder.put(accountDetailsMap1.get(AccountColumns.FOLDER_LIST_URI).toString(),
                ImmutableList.of(folderDetailsMap0, folderDetailsMap1));

        Map<String, Object> folderDetailsMap2 = createFolderDetailsMap(2, "two");
        Map<String, Object> folderDetailsMap3 = createFolderDetailsMap(3, "three");
        builder.put(accountDetailsMap2.get(AccountColumns.FOLDER_LIST_URI).toString(),
                ImmutableList.of(folderDetailsMap2, folderDetailsMap3));

        Map<String, Object> conv2 = createConversationDetailsMap("zeroConv2".hashCode(),
                "zeroConv2");
        Map<String, Object> conv3 = createConversationDetailsMap("zeroConv3".hashCode(),
                "zeroConv3");
        builder.put(folderDetailsMap2.get(FolderColumns.CONVERSATION_LIST_URI).toString(),
                ImmutableList.of(conv2, conv3));

        Map<String, Object> message2 = createMessageDetailsMap("zeroConv2".hashCode(), "zeroConv2");
        builder.put(conv2.get(ConversationColumns.MESSAGE_LIST_URI).toString(),
                ImmutableList.of(message2));
        Map<String, Object> message3 = createMessageDetailsMap("zeroConv3".hashCode(), "zeroConv3");
        builder.put(conv3.get(ConversationColumns.MESSAGE_LIST_URI).toString(),
                ImmutableList.of(message3));

        MOCK_QUERY_RESULTS = builder.build();
    }

    private static Map<String, Object> createConversationDetailsMap(int conversationId,
            String subject) {
        final String conversationUri = "content://" + AUTHORITY + "/conversation/" + conversationId;
        Map<String, Object> conversationMap = Maps.newHashMap();
        conversationMap.put(BaseColumns._ID, Long.valueOf(conversationId));
        conversationMap.put(ConversationColumns.SUBJECT, "Conversation " + subject);
        conversationMap.put(ConversationColumns.MESSAGE_LIST_URI, conversationUri + "/getMessages");
        conversationMap.put(ConversationColumns.SNIPPET, "snippet");
        conversationMap.put(ConversationColumns.SENDER_INFO, "Conversation " + subject);
        conversationMap.put(ConversationColumns.DATE_RECEIVED_MS, new Date().getTime());
        return conversationMap;
    }

    private static Map<String, Object> createMessageDetailsMap(int messageId,
            String subject) {
        Map<String, Object> messageMap = Maps.newHashMap();
        messageMap.put(BaseColumns._ID, Long.valueOf(messageId));
        messageMap.put(MessageColumns.SUBJECT, "Message " + subject);
        return messageMap;
    }

    private static Map<String, Object> createFolderDetailsMap(int folderId, String name) {
        return createFolderDetailsMap(folderId, name, false);
    }

    private static Map<String, Object> createFolderDetailsMap(int folderId, String name,
            boolean hasChildren) {
        final String folderUri = "content://" + AUTHORITY + "/folder/" + folderId;
        Map<String, Object> folderMap = Maps.newHashMap();
        folderMap.put(BaseColumns._ID, Long.valueOf(folderId));
        folderMap.put(FolderColumns.NAME, "Folder " + name);
        folderMap.put(FolderColumns.HAS_CHILDREN, new Integer(hasChildren ? 1 : 0));
        folderMap.put(FolderColumns.CONVERSATION_LIST_URI, folderUri + "/getConversations");
        folderMap.put(FolderColumns.CHILD_FOLDERS_LIST_URI, folderUri + "/getChildFolders");
        folderMap.put(FolderColumns.CAPABILITIES,
                Long.valueOf(
                        FolderCapabilities.SYNCABLE |
                        FolderCapabilities.PARENT |
                        FolderCapabilities.CAN_HOLD_MAIL |
                        FolderCapabilities.CAN_ACCEPT_MOVED_MESSAGES));
        return folderMap;
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

