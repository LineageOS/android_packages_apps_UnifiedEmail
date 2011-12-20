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
    // The actual content provider should define its own authority
    //public static final String AUTHORITY = "com.android.email.providers";

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
            AccountColumns.SEND_MESSAGE_URI,
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
        public static final String URI = "uri";

        /**
         * This integer column contains a bit field of the possible cabibilities that this account
         * supports.
         */
        public static final String CAPABILITIES = "capabilities";

        /**
         * This string column contains the content provider uri to return the list of folders for
         * this account.
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

        private AccountColumns() {};
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


    public static final class MessageColumns {
        public static final String ID = "_id";
        public static final String URI = "uri";
        public static final String MESSAGE_ID = "messageId";
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

        // TODO: Add attachments, flags

        private MessageColumns() {}
    }
}
