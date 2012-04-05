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

package com.android.mail.providers.protos.mock;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Parcel;
import android.provider.BaseColumns;
import android.text.Html;
import android.text.TextUtils;

import com.android.mail.providers.Account;
import com.android.mail.providers.Folder;
import com.android.mail.providers.MailAppProvider;
import com.android.mail.providers.ReplyFromAccount;
import com.android.mail.providers.UIProvider.AccountCapabilities;
import com.android.mail.providers.UIProvider.AccountColumns;
import com.android.mail.providers.UIProvider.AttachmentColumns;
import com.android.mail.providers.UIProvider.ConversationColumns;
import com.android.mail.providers.UIProvider.FolderCapabilities;
import com.android.mail.providers.UIProvider.FolderColumns;
import com.android.mail.providers.UIProvider.MessageColumns;
import com.android.mail.providers.UIProvider.SettingsColumns;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class MockUiProvider extends ContentProvider {

    public static final String AUTHORITY = "com.android.mail.mockprovider";


    static final String BASE_URI_STRING = "content://" + AUTHORITY;

    private static final Uri MOCK_ACCOUNTS_URI = Uri.parse("content://" + AUTHORITY + "/accounts");


    public static final int NUM_MOCK_ACCOUNTS = 2;

    // A map of query result for uris
    // TODO(pwestbro) read this map from an external
    private static Map<String, List<Map<String, Object>>> MOCK_QUERY_RESULTS = Maps.newHashMap();


    public static void initializeMockProvider() {
        ImmutableMap.Builder<String, List<Map<String, Object>>> builder = ImmutableMap.builder();

        // Add results for account list
        final List<Map<String, Object>> accountList = Lists.newArrayList();
        Map<String, Object> accountDetailsMap0;

        // Account 1
        accountDetailsMap0 = createAccountDetailsMap(0, true);

        accountList.add(accountDetailsMap0);
        Uri accountUri1 = (Uri) accountDetailsMap0.get(AccountColumns.URI);
        builder.put(accountUri1.toString(), ImmutableList.of(accountDetailsMap0));

        // Account 2
        Map<String, Object> accountDetailsMap1 = createAccountDetailsMap(1, true);
        accountList.add(accountDetailsMap1);

        Uri accountUri2 = (Uri) accountDetailsMap1.get(AccountColumns.URI);
        builder.put(accountUri2.toString(), ImmutableList.of(accountDetailsMap1));

        // Add the account list to the builder
        builder.put(getAccountsUri().toString(), accountList);

        Map<String, Object> folderDetailsMap0 = createFolderDetailsMap(0, "zero", true, 0, 2);
        builder.put(folderDetailsMap0.get(FolderColumns.URI).toString(),
                ImmutableList.of(folderDetailsMap0));
        builder.put(
                folderDetailsMap0.get(FolderColumns.CHILD_FOLDERS_LIST_URI).toString(),
                ImmutableList.of(createFolderDetailsMap(10, "zeroChild0", 0, 0),
                        createFolderDetailsMap(11, "zeroChild1", 0, 0)));

        Map<String, Object> conv0 = createConversationDetailsMap("zeroConv0".hashCode(),
                "zeroConv0", 1);
        builder.put(conv0.get(ConversationColumns.URI).toString(),
                ImmutableList.of(conv0));
        Map<String, Object> conv1 = createConversationDetailsMap("zeroConv1".hashCode(),
                "zeroConv1", 1);
        builder.put(conv1.get(ConversationColumns.URI).toString(),
                ImmutableList.of(conv1));
        builder.put(folderDetailsMap0.get(FolderColumns.CONVERSATION_LIST_URI).toString(),
                ImmutableList.of(conv0, conv1));

        Map<String, Object> message0 = createMessageDetailsMap("zeroConv0".hashCode(), "zeroConv0",
                1, false);
        builder.put(message0.get(MessageColumns.URI).toString(), ImmutableList.of(message0));
        builder.put(conv0.get(ConversationColumns.MESSAGE_LIST_URI).toString(),
                ImmutableList.of(message0));
        builder.put(message0.get(MessageColumns.ATTACHMENT_LIST_URI).toString(),
                ImmutableList.of(createAttachmentDetailsMap(0, "zero")));
        Map<String, Object> message1 = createMessageDetailsMap("zeroConv1".hashCode(), "zeroConv1",
                1, false);
        builder.put(message1.get(MessageColumns.URI).toString(), ImmutableList.of(message1));
        Map<String, Object> message1a = createMessageDetailsMap("zeroConv1a".hashCode(), "zeroConv1a",
                2, false);
        builder.put(message1a.get(MessageColumns.URI).toString(), ImmutableList.of(message1a));
        builder.put(conv1.get(ConversationColumns.MESSAGE_LIST_URI).toString(),
                ImmutableList.of(message1, message1a));
        builder.put(message1.get(MessageColumns.ATTACHMENT_LIST_URI).toString(),
                ImmutableList.of(createAttachmentDetailsMap(1, "one")));

        Map<String, Object> folderDetailsMap1 = createFolderDetailsMap(1, "one", 0, 0);
        builder.put(folderDetailsMap1.get(FolderColumns.URI).toString(),
                ImmutableList.of(folderDetailsMap1));
        builder.put(accountDetailsMap0.get(AccountColumns.FOLDER_LIST_URI).toString(),
                ImmutableList.of(folderDetailsMap0, folderDetailsMap1));
        Map<String, Object> settings0 = createSettingsDetailsMap(folderDetailsMap0.get(
                FolderColumns.URI).toString());
        builder.put(accountDetailsMap0.get(AccountColumns.SETTINGS_QUERY_URI).toString(),
                ImmutableList.of(settings0));

        Map<String, Object> folderDetailsMap2 = createFolderDetailsMap(2, "two", 2, 2);
        builder.put(folderDetailsMap2.get(FolderColumns.URI).toString(),
                ImmutableList.of(folderDetailsMap2));
        Map<String, Object> folderDetailsMap3 = createFolderDetailsMap(3, "three", 0, 0);
        builder.put(folderDetailsMap3.get(FolderColumns.URI).toString(),
                ImmutableList.of(folderDetailsMap3));
        builder.put(accountDetailsMap1.get(AccountColumns.FOLDER_LIST_URI).toString(),
                ImmutableList.of(folderDetailsMap2, folderDetailsMap3));
        Map<String, Object> settings1 = createSettingsDetailsMap(folderDetailsMap2.get(
                FolderColumns.URI).toString());
        builder.put(accountDetailsMap1.get(AccountColumns.SETTINGS_QUERY_URI).toString(),
                ImmutableList.of(settings1));

        Map<String, Object> conv2 = createConversationDetailsMap("zeroConv2".hashCode(),
                "zeroConv2", 0);
        builder.put(conv2.get(ConversationColumns.URI).toString(),
                ImmutableList.of(conv2));
        Map<String, Object> conv3 = createConversationDetailsMap("zeroConv3".hashCode(),
                "zeroConv3", 0);
        builder.put(conv3.get(ConversationColumns.URI).toString(),
                ImmutableList.of(conv3));
        builder.put(folderDetailsMap2.get(FolderColumns.CONVERSATION_LIST_URI).toString(),
                ImmutableList.of(conv2, conv3));

        Map<String, Object> message2 = createMessageDetailsMap("zeroConv2".hashCode(), "zeroConv2",
                0, true);
        builder.put(message2.get(MessageColumns.URI).toString(), ImmutableList.of(message2));
        builder.put(conv2.get(ConversationColumns.MESSAGE_LIST_URI).toString(),
                ImmutableList.of(message2));
        Map<String, Object> message3 = createMessageDetailsMap("zeroConv3".hashCode(), "zeroConv3",
                0, true);
        builder.put(message3.get(MessageColumns.URI).toString(), ImmutableList.of(message3));
        builder.put(conv3.get(ConversationColumns.MESSAGE_LIST_URI).toString(),
                ImmutableList.of(message3));

        MOCK_QUERY_RESULTS = builder.build();
    }

    private static Map<String, Object> createSettingsDetailsMap(String uri) {
        Map<String, Object> settingsMap = Maps.newHashMap();
        settingsMap.put(SettingsColumns.DEFAULT_INBOX, uri);
        return settingsMap;
    }

    private static Map<String, Object> createConversationDetailsMap(int conversationId,
            String subject, int hasAttachments) {
        final String conversationUri = "content://" + AUTHORITY + "/conversation/" + conversationId;
        Map<String, Object> conversationMap = Maps.newHashMap();
        conversationMap.put(BaseColumns._ID, Long.valueOf(conversationId));
        conversationMap.put(ConversationColumns.URI, conversationUri);
        conversationMap.put(ConversationColumns.MESSAGE_LIST_URI, conversationUri + "/getMessages");
        conversationMap.put(ConversationColumns.SUBJECT, "Conversation " + subject);
        conversationMap.put(ConversationColumns.SNIPPET, "snippet");
        conversationMap.put(ConversationColumns.SENDER_INFO,
                "account1@mock.com, account2@mock.com");
        conversationMap.put(ConversationColumns.DATE_RECEIVED_MS, new Date().getTime());
        conversationMap.put(ConversationColumns.HAS_ATTACHMENTS, hasAttachments);
        conversationMap.put(ConversationColumns.NUM_MESSAGES, 1);
        conversationMap.put(ConversationColumns.NUM_DRAFTS, 1);
        conversationMap.put(ConversationColumns.SENDING_STATE, 1);
        conversationMap.put(ConversationColumns.READ, 0);
        conversationMap.put(ConversationColumns.STARRED, 0);
        Folder[] folders = new Folder[3];
        StringBuilder foldersString = new StringBuilder();
        for (int i = 0; i < folders.length; i++) {
            folders[i] = new Folder();
            folders[i].name = "folder" + i;
            switch (i) {
                case 0:
                    folders[i].bgColor = "#fff000";
                    break;
                case 1:
                    folders[i].bgColor = "#0000FF";
                    break;
                case 2:
                    folders[i].bgColor = "#FFFF00";
                    break;
                default:
                    folders[i].bgColor = null;
                    break;
            }

        }
        for (int i = 0; i < folders.length; i++) {
            foldersString.append(folders[i].serialize());
            if (i < folders.length - 1) {
                foldersString.append(",");
            }
        }
        conversationMap.put(ConversationColumns.RAW_FOLDERS, foldersString.toString());
        return conversationMap;
    }

    private static Map<String, Object> createMessageDetailsMap(int messageId, String subject,
            int hasAttachments, boolean addReplyTo) {
        final String messageUri = "content://" + AUTHORITY + "/message/" + messageId;
        Map<String, Object> messageMap = Maps.newHashMap();
        messageMap.put(BaseColumns._ID, Long.valueOf(messageId));
        messageMap.put(MessageColumns.URI, messageUri);
        messageMap.put(MessageColumns.SUBJECT, "Message " + subject);
        messageMap.put(MessageColumns.SNIPPET, "SNIPPET");
        String html = "<html><body><b><i>This is some html!!!</i></b></body></html>";
        messageMap.put(MessageColumns.BODY_HTML, html);
        messageMap.put(MessageColumns.BODY_TEXT, Html.fromHtml(html));
        messageMap.put(MessageColumns.HAS_ATTACHMENTS, hasAttachments);
        messageMap.put(MessageColumns.DATE_RECEIVED_MS, new Date().getTime());
        messageMap.put(MessageColumns.ATTACHMENT_LIST_URI, messageUri + "/getAttachments");
        messageMap.put(MessageColumns.TO, "account1@mock.com, account2@mock.com");
        messageMap.put(MessageColumns.FROM, "fromaccount1@mock.com");
        if (addReplyTo) {
            messageMap.put(MessageColumns.REPLY_TO, "replytofromaccount1@mock.com");
        }
        return messageMap;
    }

    private static Map<String, Object> createAttachmentDetailsMap(int attachmentId, String name) {
        Map<String, Object> attachmentMap = Maps.newHashMap();
        attachmentMap.put(BaseColumns._ID, Long.valueOf(attachmentId));
        attachmentMap.put(AttachmentColumns.NAME, "Attachment " + name);
        attachmentMap.put(AttachmentColumns.URI,
                "attachmentUri/" + attachmentMap.get(AttachmentColumns.NAME));
        return attachmentMap;
    }

    private static Map<String, Object> createFolderDetailsMap(int folderId, String name,
            int unread, int total) {
        return createFolderDetailsMap(folderId, name, false, unread, total);
    }

    private static Map<String, Object> createFolderDetailsMap(int folderId, String name,
            boolean hasChildren, int unread, int total) {
        final String folderUri = "content://" + AUTHORITY + "/folder/" + folderId;
        Map<String, Object> folderMap = Maps.newHashMap();
        folderMap.put(BaseColumns._ID, Long.valueOf(folderId));
        folderMap.put(FolderColumns.URI, folderUri);
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
        folderMap.put(FolderColumns.UNREAD_COUNT, unread);
        folderMap.put(FolderColumns.TOTAL_COUNT, total);
        folderMap.put(FolderColumns.SYNC_STATUS, 0);
        folderMap.put(FolderColumns.LAST_SYNC_RESULT, 0);
        return folderMap;
    }

    // Temporarily made this public to allow the Gmail accounts to use the mock ui provider uris
    public static Map<String, Object> createAccountDetailsMap(int accountId, boolean cacheMap) {
        final String accountUri = getMockAccountUri(accountId);
        Map<String, Object> accountMap = Maps.newHashMap();
        accountMap.put(BaseColumns._ID, Long.valueOf(accountId));
        accountMap.put(AccountColumns.NAME, "account" + accountId + "@mockuiprovider.com");
        accountMap.put(AccountColumns.PROVIDER_VERSION, Long.valueOf(1));
        accountMap.put(AccountColumns.URI, Uri.parse(accountUri));
        accountMap.put(AccountColumns.CAPABILITIES,
                Integer.valueOf(
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
                        AccountCapabilities.THREADED_CONVERSATIONS |
                        AccountCapabilities.MULTIPLE_FOLDERS_PER_CONV));
        JSONArray replyFroms = new JSONArray();
        ArrayList<ReplyFromAccount> list = new ArrayList<ReplyFromAccount>();
        list.add(new ReplyFromAccount(null, Uri.parse(accountUri), "customAddress1@custom.com",
                "Custom1", false, true));
        list.add(new ReplyFromAccount(null, Uri.parse(accountUri), "customAddress2@custom.com",
                "Custom2", false, true));
        for (ReplyFromAccount a : list) {
            replyFroms.put(a.serialize());
        }
        accountMap.put(AccountColumns.ACCOUNT_FROM_ADDRESSES, replyFroms.toString());
        accountMap.put(AccountColumns.FOLDER_LIST_URI, Uri.parse(accountUri + "/folders"));
        accountMap.put(AccountColumns.SEARCH_URI, Uri.parse(accountUri + "/search"));
        accountMap.put(AccountColumns.SAVE_DRAFT_URI, Uri.parse(accountUri + "/saveDraft"));
        accountMap.put(AccountColumns.SEND_MAIL_URI, Uri.parse(accountUri + "/sendMail"));
        accountMap.put(AccountColumns.EXPUNGE_MESSAGE_URI,
                Uri.parse(accountUri + "/expungeMessage"));
        accountMap.put(AccountColumns.UNDO_URI, Uri.parse(accountUri + "/undo"));
        accountMap.put(AccountColumns.SETTINGS_INTENT_URI, Uri.parse("http://www.google.com"));
        accountMap.put(AccountColumns.SETTINGS_QUERY_URI, Uri.parse(accountUri + "/settings"));
        accountMap.put(AccountColumns.HELP_INTENT_URI, Uri.parse("http://www.google.com"));
        accountMap.put(AccountColumns.SYNC_STATUS, 0);
        accountMap.put(AccountColumns.COMPOSE_URI, Uri.parse(accountUri + "/compose"));
        accountMap.put(AccountColumns.RECENT_FOLDER_LIST_URI,
                Uri.parse(accountUri + "/recentFolderListUri"));
        accountMap.put(AccountColumns.MIME_TYPE, "account/mock");
        if (cacheMap) {
            addAccountInfoToAccountCache(accountMap);
        }
        return accountMap;
    }

    public static String getMockAccountUri(int accountId) {
        return "content://" + AUTHORITY + "/account/" + accountId;
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
            if (projection == null) {
                Set<String> keys = queryResults.get(0).keySet();
                projection = keys.toArray(new String[keys.size()]);
            }
            MatrixCursor matrixCursor = new MatrixCursor(projection, queryResults.size());

            for (Map<String, Object> queryResult : queryResults) {
                MatrixCursor.RowBuilder rowBuilder = matrixCursor.newRow();

                for (String key : projection) {
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

    @VisibleForTesting
    static Uri getAccountsUri() {
        // TODO: this should probably return a different specific to the mock account list
        return Uri.parse(BASE_URI_STRING + "/");
    }

    private static void addAccountInfoToAccountCache(Map<String, Object> accountInfo) {
        final Parcel dest = Parcel.obtain();
        dest.writeString((String) accountInfo.get(AccountColumns.NAME));
        dest.writeString("mock");

        final int providerVersion = 1;
        dest.writeInt(providerVersion);
        dest.writeParcelable((Uri) accountInfo.get(AccountColumns.URI), 0);
        dest.writeInt((Integer) accountInfo.get(AccountColumns.CAPABILITIES));
        dest.writeParcelable((Uri) accountInfo.get(AccountColumns.FOLDER_LIST_URI), 0);
        dest.writeParcelable((Uri) accountInfo.get(AccountColumns.SEARCH_URI), 0);
        dest.writeString((String) accountInfo.get(AccountColumns.ACCOUNT_FROM_ADDRESSES));
        dest.writeParcelable((Uri) accountInfo.get(AccountColumns.SAVE_DRAFT_URI), 0);
        dest.writeParcelable((Uri) accountInfo.get(AccountColumns.SEND_MAIL_URI), 0);
        dest.writeParcelable((Uri) accountInfo.get(AccountColumns.EXPUNGE_MESSAGE_URI), 0);
        dest.writeParcelable((Uri) accountInfo.get(AccountColumns.UNDO_URI), 0);
        dest.writeParcelable((Uri) accountInfo.get(AccountColumns.SETTINGS_INTENT_URI), 0);
        dest.writeParcelable((Uri)accountInfo.get(AccountColumns.SETTINGS_QUERY_URI), 0);
        dest.writeParcelable((Uri) accountInfo.get(AccountColumns.HELP_INTENT_URI), 0);
        dest.writeParcelable((Uri) accountInfo.get(AccountColumns.SEND_FEEDBACK_INTENT_URI), 0);
        dest.writeInt((Integer) accountInfo.get(AccountColumns.SYNC_STATUS));
        dest.writeParcelable((Uri) accountInfo.get(AccountColumns.COMPOSE_URI), 0);
        dest.writeString((String) accountInfo.get(AccountColumns.MIME_TYPE));
        dest.writeParcelable((Uri) accountInfo.get(AccountColumns.RECENT_FOLDER_LIST_URI), 0);
        dest.setDataPosition(0);
        final Account account = new Account(dest);
        MailAppProvider.addAccount(account, MOCK_ACCOUNTS_URI);
    }
}

