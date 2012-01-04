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

import android.provider.BaseColumns;

import java.lang.String;


public class UIProvider {
    public static final long INVALID_CONVERSATION_ID = -1;
    public static final long INVALID_MESSAGE_ID = -1;

    // The actual content provider should define its own authority
    public static final String AUTHORITY = "com.android.email.providers";

    public static final String ACCOUNT_LIST_TYPE =
            "vnd.android.cursor.dir/vnd.com.android.mail.account";
    public static final String ACCOUNT_TYPE =
            "vnd.android.cursor.item/vnd.com.android.mail.account";

    public static final String[] ACCOUNTS_PROJECTION = {
            BaseColumns._ID,
            AccountColumns.NAME,
            AccountColumns.PROVIDER_VERSION,
            AccountColumns.URI,
            AccountColumns.CAPABILITIES,
            AccountColumns.FOLDER_LIST_URI,
            AccountColumns.SEARCH_URI,
            AccountColumns.ACCOUNT_FROM_ADDRESSES_URI,
            AccountColumns.SAVE_NEW_DRAFT_URI,
            AccountColumns.SEND_MESSAGE_URI
    };

    public static final class AccountCapabilities {
        public static final int SYNCABLE_FOLDERS = 0x0001;
        public static final int REPORT_SPAM = 0x0002;
        public static final int ARCHIVE = 0x0004;
        public static final int MUTE = 0x0008;
        public static final int SERVER_SEARCH = 0x0010;
        public static final int FOLDER_SERVER_SEARCH = 0x0020;
        public static final int SANITIZED_HTML = 0x0040;
        public static final int DRAFT_SYNCHRONIZATION = 0x0080;
        public static final int MULTIPLE_FROM_ADDRESSES = 0x0100;
        public static final int SMART_REPLY = 0x0200;
        public static final int LOCAL_SEARCH = 0x0400;
        public static final int THREADED_CONVERSATIONS = 0x0800;
    }

    public static final class AccountColumns {
        /**
         * This string column contains the human visible name for the account.
         */
        public static final String NAME = "name";

        /**
         * This integer column returns the version of the UI provider schema from which this
         * account provider will return results.
         */
        public static final String PROVIDER_VERSION = "providerVersion";

        /**
         * This string column contains the uri to directly access the information for this account.
         */
        public static final String URI = "accountUri";

        /**
         * This integer column contains a bit field of the possible cabibilities that this account
         * supports.
         */
        public static final String CAPABILITIES = "capabilities";

        /**
         * This string column contains the content provider uri to return the
         * list of top level folders for this account.
         */
        public static final String FOLDER_LIST_URI = "folderListUri";

        /**
         * This string column contains the content provider uri that can be queried for search
         * results.
         */
        public static final String SEARCH_URI = "searchUri";

        /**
         * This string column contains the content provider uri that can be queried to access the
         * from addresses for this account.
         */
        public static final String ACCOUNT_FROM_ADDRESSES_URI = "accountFromAddressesUri";

        /**
         * This string column contains the content provider uri that can be used to save (insert)
         * new draft messages for this account.
         */
        public static final String SAVE_NEW_DRAFT_URI = "saveNewDraftUri";

        /**
         * This string column contains the content provider uri that can be used to send
         * a message for this account.
         * NOTE: This might be better to be an update operation on the messageUri.
         */
        public static final String SEND_MESSAGE_URI = "sendMessageUri";
    }

    // We define a "folder" as anything that contains a list of conversations.
    public static final String FOLDER_LIST_TYPE =
            "vnd.android.cursor.dir/vnd.com.android.mail.folder";
    public static final String FOLDER_TYPE =
            "vnd.android.cursor.item/vnd.com.android.mail.folder";

    public static final String[] FOLDERS_PROJECTION = {
        BaseColumns._ID,
        FolderColumns.URI,
        FolderColumns.NAME,
        FolderColumns.HAS_CHILDREN,
        FolderColumns.CAPABILITIES,
        FolderColumns.SYNC_FREQUENCY,
        FolderColumns.SYNC_WINDOW,
        FolderColumns.CONVERSATION_LIST_URI,
        FolderColumns.CHILD_FOLDERS_LIST_URI
    };

    public static final class FolderCapabilities {
        public static final int SYNCABLE = 0x0001;
        public static final int PARENT = 0x0002;
        public static final int CAN_HOLD_MAIL = 0x0004;
        public static final int CAN_ACCEPT_MOVED_MESSAGES = 0x0008;
    }

    public static final class FolderColumns {
        public static String URI = "folderUri";
        /**
         * This string column contains the human visible name for the folder.
         */
        public static final String NAME = "name";
        /**
         * This int column represents the capabilities of the folder specified by
         * FolderCapabilities flags.
         */
        public static String CAPABILITIES = "capabilities";
        /**
         * This boolean column represents whether or not this folder has any
         * child folders.
         */
        public static String HAS_CHILDREN = "hasChildren";
        /**
         * This int column represents how often the folder should be synced.
         */
        public static String SYNC_FREQUENCY = "syncFrequency";
        /**
         * This int column represents how large the sync window is.
         */
        public static String SYNC_WINDOW = "syncWindow";
        /**
         * This string column contains the content provider uri to return the
         * list of conversations for this folder.
         */
        public static final String CONVERSATION_LIST_URI = "conversationListUri";
        /**
         * This string column contains the content provider uri to return the
         * list of child folders of this folder.
         */
        public static String CHILD_FOLDERS_LIST_URI = "childFoldersListUri";

        public FolderColumns() {};
    }

    // We define a "folder" as anything that contains a list of conversations.
    public static final String CONVERSATION_LIST_TYPE =
            "vnd.android.cursor.dir/vnd.com.android.mail.conversation";
    public static final String CONVERSATION_TYPE =
            "vnd.android.cursor.item/vnd.com.android.mail.conversation";

    public static final String[] CONVERSATION_PROJECTION = {
        BaseColumns._ID,
        ConversationColumns.URI,
        ConversationColumns.MESSAGE_LIST_URI,
        ConversationColumns.SUBJECT,
        ConversationColumns.SNIPPET,
        ConversationColumns.SENDER_INFO,
        ConversationColumns.DATE_RECEIVED_MS,
        ConversationColumns.HAS_ATTACHMENTS
    };

    // These column indexes only work when the caller uses the
    // default CONVERSATION_PROJECTION defined above.
    public static final int CONVERSATION_ID_COLUMN = 0;
    public static final int CONVERSATION_URI_COLUMN = 1;
    public static final int CONVERSATION_MESSAGE_LIST_URI_COLUMN = 2;
    public static final int CONVERSATION_SUBJECT_COLUMN = 3;
    public static final int CONVERSATION_SNIPPET_COLUMN = 4;
    public static final int CONVERSATION_SENDER_INFO_COLUMN = 5;
    public static final int CONVERSATION_DATE_RECEIVED_MS_COLUMN = 6;
    public static final int CONVERSATION_HAS_ATTACHMENTS_COLUMN = 7;

    public static final class ConversationColumns {
        public static final String URI = "conversationUri";
        /**
         * This string column contains the content provider uri to return the
         * list of messages for this conversation.
         */
        public static final String MESSAGE_LIST_URI = "messageListUri";
        /**
         * This string column contains the subject string for a conversation.
         */
        public static final String SUBJECT = "subject";
        /**
         * This string column contains the snippet string for a conversation.
         */
        public static final String SNIPPET = "snippet";
        /**
         * This string column contains the sender info string for a
         * conversation.
         */
        public static final String SENDER_INFO = "senderInfo";
        /**
         * This long column contains the time in ms of the latest update to a
         * conversation.
         */
        public static final String DATE_RECEIVED_MS = "dateReceivedMs";

        /**
         * This boolean column contains whether any messages in this conversation
         * have attachments.
         */
        public static final String HAS_ATTACHMENTS = "hasAttachments";

        public ConversationColumns() {
        };
    }

    /**
     * Returns a uri that, when queried, will return a cursor with a list of information for the
     * list of configured accounts.
     * @return
     */
    // TODO: create a static registry for the starting point for the UI provider.
//    public static Uri getAccountsUri() {
//        return Uri.parse(BASE_URI_STRING + "/");
//    }

    public static final class DraftType {
        public static final String COMPOSE = "compose";
        public static final String REPLY = "reply";
        public static final String REPLY_ALL = "replyAll";
        public static final String FORWARD = "forward";

        private DraftType() {}
    }

    public static final String[] MESSAGE_PROJECTION = {
        BaseColumns._ID,
        MessageColumns.SERVER_ID,
        MessageColumns.URI,
        MessageColumns.CONVERSATION_ID,
        MessageColumns.SUBJECT,
        MessageColumns.SNIPPET,
        MessageColumns.FROM,
        MessageColumns.TO,
        MessageColumns.CC,
        MessageColumns.BCC,
        MessageColumns.REPLY_TO,
        MessageColumns.DATE_RECEIVED_MS,
        MessageColumns.BODY_HTML,
        MessageColumns.BODY_TEXT,
        MessageColumns.EMBEDS_EXTERNAL_RESOURCES,
        MessageColumns.REF_MESSAGE_ID,
        MessageColumns.DRAFT_TYPE,
        MessageColumns.INCLUDE_QUOTED_TEXT,
        MessageColumns.QUOTE_START_POS,
        MessageColumns.CLIENT_CREATED,
        MessageColumns.CUSTOM_FROM_ADDRESS,
        MessageColumns.HAS_ATTACHMENTS,
        MessageColumns.ATTACHMENT_LIST_URI,
        MessageColumns.MESSAGE_FLAGS
    };

    public static final String MESSAGE_LIST_TYPE =
            "vnd.android.cursor.dir/vnd.com.android.mail.message";
    public static final String MESSAGE_TYPE =
            "vnd.android.cursor.item/vnd.com.android.mail.message";

    public static final int MESSAGE_ID_COLUMN = 0;
    public static final int MESSAGE_SERVER_ID_COLUMN = 1;
    public static final int MESSAGE_URI_COLUMN = 2;
    public static final int MESSAGE_CONVERSATION_ID_COLUMN = 3;
    public static final int MESSAGE_SUBJECT_COLUMN = 4;
    public static final int MESSAGE_SNIPPET_COLUMN = 5;
    public static final int MESSAGE_FROM_COLUMN = 6;
    public static final int MESSAGE_TO_COLUMN = 7;
    public static final int MESSAGE_CC_COLUMN = 8;
    public static final int MESSAGE_BCC_COLUMN = 9;
    public static final int MESSAGE_REPLY_TO_COLUMN = 10;
    public static final int MESSAGE_DATE_RECEIVED_MS_COLUMN = 11;
    public static final int MESSAGE_BODY_HTML = 12;
    public static final int MESSAGE_BODY_TEXT = 13;
    public static final int MESSAGE_EMBEDS_EXTERNAL_RESOURCES_COLUMN = 14;
    public static final int MESSAGE_REF_MESSAGE_ID_COLUMN = 15;
    public static final int MESSAGE_DRAFT_TYPE_COLUMN = 16;
    public static final int MESSAGE_INCLUDE_QUOTED_TEXT_COLUMN = 17;
    public static final int MESSAGE_QUOTE_START_POS_COLUMN = 18;
    public static final int MESSAGE_CLIENT_CREATED_COLUMN = 19;
    public static final int MESSAGE_CUSTOM_FROM_ADDRESS_COLUMN = 20;
    public static final int MESSAGE_HAS_ATTACHMENTS_COLUMN = 21;
    public static final int MESSAGE_ATTACHMENT_LIST_URI_COLUMN = 22;
    public static final int MESSAGE_FLAGS_COLUMN = 23;

    public static final class MessageFlags {
        public static final int SYNCABLE = 0x0001;
        public static final int PARENT = 0x0002;
        public static final int CAN_HOLD_MAIL = 0x0004;
        public static final int CAN_ACCEPT_MOVED_MESSAGES = 0x0008;
        public static final int STARRED = 0x0012;
    }

    public static final class MessageColumns {
        public static final String URI = "messageUri";
        public static final String SERVER_ID = "localMessageId";
        public static final String CONVERSATION_ID = "conversationId";
        public static final String SUBJECT = "subject";
        public static final String SNIPPET = "snippet";
        public static final String FROM = "fromAddress";
        public static final String TO = "toAddresses";
        public static final String CC = "ccAddresses";
        public static final String BCC = "bccAddresses";
        public static final String REPLY_TO = "replyToAddress";
        public static final String DATE_RECEIVED_MS = "dateReceivedMs";
        public static final String BODY_HTML = "bodyHtml";
        public static final String BODY_TEXT = "bodyText";
        public static final String EMBEDS_EXTERNAL_RESOURCES = "bodyEmbedsExternalResources";
        public static final String REF_MESSAGE_ID = "refMessageId";
        public static final String DRAFT_TYPE = "draftType";
        public static final String INCLUDE_QUOTED_TEXT = "includeQuotedText";
        public static final String QUOTE_START_POS = "quoteStartPos";
        public static final String CLIENT_CREATED = "clientCreated";
        public static final String CUSTOM_FROM_ADDRESS = "customFromAddress";
        public static final String HAS_ATTACHMENTS = "hasAttachments";
        public static final String ATTACHMENT_LIST_URI = "attachmentListUri";
        public static final String MESSAGE_FLAGS = "messagesFlags";

        private MessageColumns() {}
    }

    // We define a "folder" as anything that contains a list of conversations.
    public static final String ATTACHMENT_LIST_TYPE =
            "vnd.android.cursor.dir/vnd.com.android.mail.attachment";
    public static final String ATTACHMENT_TYPE =
            "vnd.android.cursor.item/vnd.com.android.mail.attachment";

    public static final String[] ATTACHMENT_PROJECTION = {
        BaseColumns._ID,
        AttachmentColumns.NAME,
        AttachmentColumns.SIZE,
        AttachmentColumns.ORIGIN,
        AttachmentColumns.ORIGIN_EXTRAS,
        AttachmentColumns.CONTENT_TYPE,
        AttachmentColumns.SYNCED
    };

    public static final int ACCOUNT_NAME_COLUMN = 1;

    public static final class AttachmentColumns {
        public static final String NAME = "name";
        public static final String SIZE = "size";
        public static final String ORIGIN = "origin";
        public static final String ORIGIN_EXTRAS = "originExtras";
        public static final String CONTENT_TYPE = "contentType";
        public static final String SYNCED = "synced";
    }
}
