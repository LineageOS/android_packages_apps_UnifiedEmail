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

import android.net.Uri;
import android.provider.BaseColumns;


public class UIProvider {
    // This authority is only needed to get to the account list
    // NOTE: Overlay applications may want to override this authority
    public static final String AUTHORITY = "com.android.email.providers";

    static final String BASE_URI_STRING = "content://" + AUTHORITY;

    public static final String ACCOUNT_LIST_TYPE =
            "vnd.android.cursor.dir/vnd.com.android.email.account";
    public static final String ACCOUNT_TYPE =
            "vnd.android.cursor.item/vnd.com.android.email.account";

    public static final String[] ACCOUNTS_PROJECTION = {
            BaseColumns._ID,
            AccountColumns.NAME,
            AccountColumns.PROVIDER_VERSION,
            AccountColumns.URI,
            AccountColumns.CAPABILITIES,
            AccountColumns.FOLDER_LIST_URI,
            AccountColumns.SEARCH_URI,
            AccountColumns.ACCOUNT_FROM_ADDRESSES_URI,
    };

    public static final class AccountCapabilities {
        public static final int SUPPORTS_SYNCABLE_FOLDERS = 0x0001;
        public static final int SUPPORTS_REPORT_SPAM = 0x0002;
        public static final int SUPPORTS_ARCHIVE = 0x0004;
        public static final int SUPPORTS_SERVER_SEARCH = 0x0008;
        public static final int SUPPORTS_FOLDER_SERVER_SEARCH = 0x00018;
        public static final int RETURNS_SANITIZED_HTML = 0x0020;
        public static final int SUPPORTS_DRAFT_SYNCHRONIZATION = 0x0040;
        public static final int SUPPORTS_MULTIPLE_FROM_ADDRESSES = 0x0080;
        public static final int SUPPORTS_SMART_REPLY = 0x0100;
        public static final int SUPPORTS_LOCAL_SEARCH = 0x0200;
        public static final int SUPPORTS_THREADED_CONVERSATIONS = 0x0400;
    }

    public static final class AccountColumns {
        public static final String NAME = "name";
        public static final String PROVIDER_VERSION = "providerVersion";
        public static final String URI = "uri";
        public static final String CAPABILITIES = "capabilities";
        public static final String FOLDER_LIST_URI = "folderListUri";
        public static final String SEARCH_URI = "searchUri";
        public static final String ACCOUNT_FROM_ADDRESSES_URI = "accountFromAddressesUri";
        public static final String SAVE_NEW_DRAFT_URI = "saveNewDraftUri";

        private AccountColumns() {};
    }

    /**
     * Returns a uri that, when queried, will return a cursor with a list of information for the
     * list of configured accounts.
     * @return
     */
    public static Uri getAccountsUri() {
        return Uri.parse(BASE_URI_STRING + "/");
    }


    public static final class MessageColumns {
        public static final String ID = "_id";
        public static final String URI = "uri";
        public static final String MESSAGE_ID = "messageId";
        public static final String CONVERSATION_ID = "conversation";
        public static final String SUBJECT = "subject";
        public static final String SNIPPET = "snippet";
        public static final String FROM = "fromAddress";
        public static final String TO = "toAddresses";
        public static final String CC = "ccAddresses";
        public static final String BCC = "bccAddresses";
        public static final String REPLY_TO = "replyToAddresses";
        public static final String DATE_SENT_MS = "dateSentMs";
        public static final String DATE_RECEIVED_MS = "dateReceivedMs";
        public static final String LIST_INFO = "listInfo";
        public static final String BODY = "body";
        public static final String EMBEDS_EXTERNAL_RESOURCES = "bodyEmbedsExternalResources";
        public static final String REF_MESSAGE_ID = "refMessageId";
        public static final String FORWARD = "forward";
        public static final String INCLUDE_QUOTED_TEXT = "includeQuotedText";
        public static final String QUOTE_START_POS = "quoteStartPos";
        public static final String CLIENT_CREATED = "clientCreated";
        public static final String CUSTOM_FROM_ADDRESS = "customFromAddress";

        // TODO: Add attachments, flags

        private MessageColumns() {}
    }
}