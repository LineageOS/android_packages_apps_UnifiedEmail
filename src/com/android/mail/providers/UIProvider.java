/*******************************************************************************
 *      Copyright (C) 2011 Google Inc.
 *      Licensed to The Android Open Source Project.
 *
 *      Licensed under the Apache License, Version 2.0 (the "License");
 *      you may not use this file except in compliance with the License.
 *      You may obtain a copy of the License at
 *
 *           http://www.apache.org/licenses/LICENSE-2.0
 *
 *      Unless required by applicable law or agreed to in writing, software
 *      distributed under the License is distributed on an "AS IS" BASIS,
 *      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *      See the License for the specific language governing permissions and
 *      limitations under the License.
 *******************************************************************************/

package com.android.mail.providers;

import android.content.Context;
import android.provider.BaseColumns;
import android.text.TextUtils;

import com.android.common.contacts.DataUsageStatUpdater;

import java.lang.String;
import java.util.ArrayList;


public class UIProvider {
    public static final String EMAIL_SEPARATOR = "\n";
    public static final long INVALID_CONVERSATION_ID = -1;
    public static final long INVALID_MESSAGE_ID = -1;

    // The actual content provider should define its own authority
    public static final String AUTHORITY = "com.android.mail.providers";

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
            AccountColumns.SAVE_DRAFT_URI,
            AccountColumns.SEND_MAIL_URI,
            AccountColumns.EXPUNGE_MESSAGE_URI,
            AccountColumns.UNDO_URI
    };

    public static final int ACCOUNT_ID_COLUMN = 0;
    public static final int ACCOUNT_NAME_COLUMN = 1;
    public static final int ACCOUNT_PROVIDER_VERISON_COLUMN = 2;
    public static final int ACCOUNT_URI_COLUMN = 3;
    public static final int ACCOUNT_CAPABILITIES_COLUMN = 4;
    public static final int ACCOUNT_FOLDER_LIST_URI_COLUMN = 5;
    public static final int ACCOUNT_SEARCH_URI_COLUMN = 6;
    public static final int ACCOUNT_FROM_ADDRESSES_URI_COLUMN = 7;
    public static final int ACCOUNT_SAVE_DRAFT_URI_COLUMN = 8;
    public static final int ACCOUNT_SEND_MESSAGE_URI_COLUMN = 9;
    public static final int ACCOUNT_EXPUNGE_MESSAGE_URI_COLUMN = 10;
    public static final int ACCOUNT_UNDO_URI_COLUMN = 11;

    public static final class AccountCapabilities {
        /**
         * Whether folders can be synchronized back to the server.
         */
        public static final int SYNCABLE_FOLDERS = 0x0001;
        /**
         * Whether the server allows reporting spam back.
         */
        public static final int REPORT_SPAM = 0x0002;
        /**
         * Whether the server supports a concept of Archive: removing mail from the Inbox but
         * keeping it around.
         */
        public static final int ARCHIVE = 0x0004;
        /**
         * Whether the server will stop notifying on updates to this thread? This requires
         * THREADED_CONVERSATIONS to be true, otherwise it should be ignored.
         */
        public static final int MUTE = 0x0008;
        /**
         * Whether the server supports searching over all messages. This requires SYNCABLE_FOLDERS
         * to be true, otherwise it should be ignored.
         */
        public static final int SERVER_SEARCH = 0x0010;
        /**
         * Whether the server supports constraining search to a single folder. Requires
         * SYNCABLE_FOLDERS, otherwise it should be ignored.
         */
        public static final int FOLDER_SERVER_SEARCH = 0x0020;
        /**
         * Whether the server sends us sanitized HTML (guaranteed to not contain malicious HTML).
         */
        public static final int SANITIZED_HTML = 0x0040;
        /**
         * Whether the server allows synchronization of draft messages. This does NOT require
         * SYNCABLE_FOLDERS to be set.
         */
        public static final int DRAFT_SYNCHRONIZATION = 0x0080;
        /**
         * Does the server allow the user to compose mails (and reply) using addresses other than
         * their account name? For instance, GMail allows users to set FROM addresses that are
         * different from account@gmail.com address. For instance, user@gmail.com could have another
         * FROM: address like user@android.com. If the user has enabled multiple FROM address, he
         * can compose (and reply) using either address.
         */
        public static final int MULTIPLE_FROM_ADDRESSES = 0x0100;
        /**
         * Whether the server allows the original message to be included in the reply by setting a
         * flag on the reply. If we can avoid including the entire previous message, we save on
         * bandwidth (replies are shorter).
         */
        public static final int SMART_REPLY = 0x0200;
        /**
         * Does this account support searching locally, on the device? This requires the backend
         * storage to support a mechanism for searching.
         */
        public static final int LOCAL_SEARCH = 0x0400;
        /**
         * Whether the server supports a notion of threaded conversations: where replies to messages
         * are tagged to keep conversations grouped. This could be full threading (each message
         * lists its parent) or conversation-level threading (each message lists one conversation
         * which it belongs to)
         */
        public static final int THREADED_CONVERSATIONS = 0x0800;
        /**
         * Whether the server supports allowing a conversation to be in multiple folders. (Or allows
         * multiple labels on a single conversation, since labels and folders are interchangeable
         * in this application.)
         */
        public static final int MULTIPLE_FOLDERS_PER_CONV = 0x1000;
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
         * new draft messages for this account. NOTE: This might be better to
         * be an update operation on the messageUri.
         */
        public static final String SAVE_DRAFT_URI = "saveDraftUri";

        /**
         * This string column contains the content provider uri that can be used to send
         * a message for this account.
         * NOTE: This might be better to be an update operation on the messageUri.
         */
        public static final String SEND_MAIL_URI = "sendMailUri";

        /**
         * This string column contains the content provider uri that can be used
         * to expunge a message from this account. NOTE: This might be better to
         * be an update operation on the messageUri.
         */
        public static final String EXPUNGE_MESSAGE_URI = "expungeMessageUri";

        /**
         * This string column contains the content provider uri that can be used
         * to undo the last committed action.
         */
        public static String UNDO_URI = "undoUri";
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
        FolderColumns.CHILD_FOLDERS_LIST_URI,
        FolderColumns.UNREAD_COUNT,
        FolderColumns.TOTAL_COUNT,
    };

    public static final int FOLDER_ID_COLUMN = 0;
    public static final int FOLDER_URI_COLUMN = 1;
    public static final int FOLDER_NAME_COLUMN = 2;
    public static final int FOLDER_HAS_CHILDREN_COLUMN = 3;
    public static final int FOLDER_CAPABILITIES_COLUMN = 4;
    public static final int FOLDER_SYNC_FREQUENCY_COLUMN = 5;
    public static final int FOLDER_SYNC_WINDOW_COLUMN = 6;
    public static final int FOLDER_CONVERSATION_LIST_URI_COLUMN = 7;
    public static final int FOLDER_CHILD_FOLDERS_LIST_COLUMN = 8;
    public static final int FOLDER_UNREAD_COUNT_COLUMN = 9;
    public static final int FOLDER_TOTAL_COUNT_COLUMN = 10;

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
         * This int column represents whether or not this folder has any
         * child folders.
         */
        public static String HAS_CHILDREN = "hasChildren";
        /**
         * This int column represents how often the folder should be synchronized with the server.
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

        public static String UNREAD_COUNT = "unreadCount";

        public static String TOTAL_COUNT = "totalCount";

        public FolderColumns() {}
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
        ConversationColumns.HAS_ATTACHMENTS,
        ConversationColumns.NUM_MESSAGES,
        ConversationColumns.NUM_DRAFTS,
        ConversationColumns.SENDING_STATE,
        ConversationColumns.PRIORITY,
        ConversationColumns.READ,
        ConversationColumns.STARRED
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
    public static final int CONVERSATION_NUM_MESSAGES_COLUMN = 8;
    public static final int CONVERSATION_NUM_DRAFTS_COLUMN = 9;
    public static final int CONVERSATION_SENDING_STATE_COLUMN = 10;
    public static final int CONVERSATION_PRIORITY_COLUMN = 11;
    public static final int CONVERSATION_READ_COLUMN = 12;
    public static final int CONVERSATION_STARRED_COLUMN = 13;

    public static final class ConversationSendingState {
        public static final int OTHER = 0;
        public static final int SENDING = 1;
        public static final int SENT = 2;
        public static final int SEND_ERROR = -1;
    }

    public static final class ConversationPriority {
        public static final int LOW = 0;
        public static final int HIGH = 1;
    }

    public static final class ConversationFlags {
        public static final int READ = 1<<0;
        public static final int STARRED = 1<<1;
        public static final int REPLIED = 1<<2;
        public static final int FORWARDED = 1<<3;
    }

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

        /**
         * This int column contains the number of messages in this conversation.
         * For unthreaded, this will always be 1.
         */
        public static String NUM_MESSAGES = "numMessages";

        /**
         * This int column contains the number of drafts associated with this
         * conversation.
         */
        public static String NUM_DRAFTS = "numDrafts";

        /**
         * This int column contains the state of drafts and replies associated
         * with this conversation. Use ConversationSendingState to interpret
         * this field.
         */
        public static String SENDING_STATE = "sendingState";

        /**
         * This int column contains the priority of this conversation. Use
         * ConversationPriority to interpret this field.
         */
        public static String PRIORITY = "priority";

        /**
         * This boolean column indicates whether the conversation has been read
         */
        public static String READ = "read";

        /**
         * This boolean column indicates whether the conversation has been read
         */
        public static String STARRED = "starred";

        public ConversationColumns() {
        }
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
        public static final int NOT_A_DRAFT = 0;
        public static final int COMPOSE = 1;
        public static final int REPLY = 2;
        public static final int REPLY_ALL = 3;
        public static final int FORWARD = 4;

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
        MessageColumns.APPEND_REF_MESSAGE_CONTENT,
        MessageColumns.HAS_ATTACHMENTS,
        MessageColumns.ATTACHMENT_LIST_URI,
        MessageColumns.MESSAGE_FLAGS,
        MessageColumns.JOINED_ATTACHMENT_INFOS,
        MessageColumns.SAVE_MESSAGE_URI,
        MessageColumns.SEND_MESSAGE_URI
    };

    /** Separates attachment info parts in strings in a message. */
    public static final String MESSAGE_ATTACHMENT_INFO_SEPARATOR = "\n";
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
    public static final int MESSAGE_BODY_HTML_COLUMN = 12;
    public static final int MESSAGE_BODY_TEXT_COLUMN = 13;
    public static final int MESSAGE_EMBEDS_EXTERNAL_RESOURCES_COLUMN = 14;
    public static final int MESSAGE_REF_MESSAGE_ID_COLUMN = 15;
    public static final int MESSAGE_DRAFT_TYPE_COLUMN = 16;
    public static final int MESSAGE_APPEND_REF_MESSAGE_CONTENT_COLUMN = 17;
    public static final int MESSAGE_HAS_ATTACHMENTS_COLUMN = 18;
    public static final int MESSAGE_ATTACHMENT_LIST_URI_COLUMN = 19;
    public static final int MESSAGE_FLAGS_COLUMN = 20;
    public static final int MESSAGE_JOINED_ATTACHMENT_INFOS_COLUMN = 21;
    public static final int MESSAGE_SAVE_URI_COLUMN = 22;
    public static final int MESSAGE_SEND_URI_COLUMN = 23;

    public static final class MessageFlags {
        public static final int STARRED =       1 << 0;
        public static final int UNREAD =        1 << 1;
        public static final int REPLIED =       1 << 2;
        public static final int FORWARDED =     1 << 3;
    }

    public static final class MessageColumns {
        /**
         * This string column contains a content provider URI that points to this single message.
         */
        public static final String URI = "messageUri";
        /**
         * This string column contains a server-assigned ID for this message.
         */
        public static final String SERVER_ID = "serverMessageId";
        public static final String CONVERSATION_ID = "conversationId";
        /**
         * This string column contains the subject of a message.
         */
        public static final String SUBJECT = "subject";
        /**
         * This string column contains a snippet of the message body.
         */
        public static final String SNIPPET = "snippet";
        /**
         * This string column contains the single email address (and optionally name) of the sender.
         */
        public static final String FROM = "fromAddress";
        /**
         * This string column contains a comma-delimited list of "To:" recipient email addresses.
         */
        public static final String TO = "toAddresses";
        /**
         * This string column contains a comma-delimited list of "CC:" recipient email addresses.
         */
        public static final String CC = "ccAddresses";
        /**
         * This string column contains a comma-delimited list of "BCC:" recipient email addresses.
         * This value will be null for incoming messages.
         */
        public static final String BCC = "bccAddresses";
        /**
         * This string column contains the single email address (and optionally name) of the
         * sender's reply-to address.
         */
        public static final String REPLY_TO = "replyToAddress";
        /**
         * This long column contains the timestamp (in millis) of receipt of the message.
         */
        public static final String DATE_RECEIVED_MS = "dateReceivedMs";
        /**
         * This string column contains the HTML form of the message body, if available. If not,
         * a provider must populate BODY_TEXT.
         */
        public static final String BODY_HTML = "bodyHtml";
        /**
         * This string column contains the plaintext form of the message body, if HTML is not
         * otherwise available. If HTML is available, this value should be left empty (null).
         */
        public static final String BODY_TEXT = "bodyText";
        public static final String EMBEDS_EXTERNAL_RESOURCES = "bodyEmbedsExternalResources";
        /**
         * This string column contains an opaque string used by the sendMessage api.
         */
        public static final String REF_MESSAGE_ID = "refMessageId";
        /**
         * This integer column contains the type of this draft, or zero (0) if this message is not a
         * draft. See {@link DraftType} for possible values.
         */
        public static final String DRAFT_TYPE = "draftType";
        /**
         * This boolean column indicates whether an outgoing message should trigger special quoted
         * text processing upon send. The value should default to zero (0) for protocols that do
         * not support or require this flag, and for all incoming messages.
         */
        public static final String APPEND_REF_MESSAGE_CONTENT = "appendRefMessageContent";
        /**
         * This boolean column indicates whether a message has attachments. The list of attachments
         * can be retrieved using the URI in {@link MessageColumns#ATTACHMENT_LIST_URI}.
         */
        public static final String HAS_ATTACHMENTS = "hasAttachments";
        /**
         * This string column contains the content provider URI for the list of
         * attachments associated with this message.
         */
        public static final String ATTACHMENT_LIST_URI = "attachmentListUri";
        /**
         * This long column is a bit field of flags defined in {@link MessageFlags}.
         */
        public static final String MESSAGE_FLAGS = "messageFlags";
        /**
         * This string column contains a specially formatted string representing all
         * attachments that we added to a message that is being sent or saved.
         */
        public static final String JOINED_ATTACHMENT_INFOS = "joinedAttachmentInfos";
        /**
         * This string column contains the content provider URI for saving this
         * message.
         */
        public static final String SAVE_MESSAGE_URI = "saveMessageUri";
        /**
         * This string column contains content provider URI for sending this
         * message.
         */
        public static final String SEND_MESSAGE_URI = "sendMessageUri";

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
        AttachmentColumns.URI,
        AttachmentColumns.ORIGIN_EXTRAS,
        AttachmentColumns.CONTENT_TYPE,
        AttachmentColumns.SYNCED
    };
    private static final String EMAIL_SEPARATOR_PATTERN = "\n";
    public static final int ATTACHMENT_ID_COLUMN = 0;
    public static final int ATTACHMENT_NAME_COLUMN = 1;
    public static final int ATTACHMENT_SIZE_COLUMN = 2;
    public static final int ATTACHMENT_URI_COLUMN = 3;
    public static final int ATTACHMENT_ORIGIN_EXTRAS_COLUMN = 4;
    public static final int ATTACHMENT_CONTENT_TYPE_COLUMN = 5;
    public static final int ATTACHMENT_SYNCED_COLUMN = 6;

    public static final class AttachmentColumns {
        public static final String NAME = "name";
        public static final String SIZE = "size";
        public static final String URI = "uri";
        public static final String ORIGIN_EXTRAS = "originExtras";
        public static final String CONTENT_TYPE = "contentType";
        public static final String SYNCED = "synced";
    }

    public static int getMailMaxAttachmentSize(String account) {
        // TODO: query the account to see what the max attachment size is?
        return 5 * 1024 * 1024;
    }

    public static String getAttachmentTypeSetting() {
        // TODO: query the account to see what kinds of attachments it supports?
        return "com.google.android.gm.allowAddAnyAttachment";
    }

    public static void incrementRecipientsTimesContacted(Context context, String addressString) {
        DataUsageStatUpdater statsUpdater = new DataUsageStatUpdater(context);
        ArrayList<String> recipients = new ArrayList<String>();
        String[] addresses = TextUtils.split(addressString, EMAIL_SEPARATOR_PATTERN);
        for (String address : addresses) {
            recipients.add(address);
        }
        statsUpdater.updateWithAddress(recipients);
    }

    public static final String[] UNDO_PROJECTION = {
        ConversationColumns.MESSAGE_LIST_URI
    };
    public static final int UNDO_MESSAGE_LIST_COLUMN = 0;

    // Parameter used to indicate the sequence number for an undoable operation
    public static final String SEQUENCE_QUERY_PARAMETER = "seq";
}
