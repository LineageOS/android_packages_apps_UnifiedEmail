/**
 * Copyright (c) 2012, Google Inc.
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

import android.database.Cursor;
import android.os.Parcel;


public class Account extends android.accounts.Account {
    /**
     * The version of the UI provider schema from which this account provider
     * will return results.
     */
    public int providerVersion;

    /**
     * The uri to directly access the information for this account.
     */
    public String accountUri;

    /**
     * The possible capabilities that this account supports.
     */
    public int capabilities;

    /**
     * The content provider uri to return the list of top level folders for this
     * account.
     */
    public String folderListUri;

    /**
     * The content provider uri that can be queried for search results.
     */
    public String searchUri;

    /**
     * The content provider uri that can be queried to access the from addresses
     * for this account.
     */
    public String accountFromAddressesUri;

    /**
     * The content provider uri that can be used to save (insert) new draft
     * messages for this account. NOTE: This might be better to be an update
     * operation on the messageUri.
     */
    public String saveDraftUri;

    /**
     * The content provider uri that can be used to send a message for this
     * account.
     * NOTE: This might be better to be an update operation on the
     * messageUri.
     */
    public String sendMessageUri;

    /**
     * The content provider uri that can be used to expunge message from this
     * account. NOTE: This might be better to be an update operation on the
     * messageUri.
     */
    public String expungeMessageUri;

    public Account(Parcel in) {
        super(in);
        providerVersion = in.readInt();
        accountUri = in.readString();
        capabilities = in.readInt();
        folderListUri = in.readString();
        searchUri = in.readString();
        accountFromAddressesUri = in.readString();
        saveDraftUri = in.readString();
        sendMessageUri = in.readString();
        expungeMessageUri = in.readString();
    }

    public Account(String address, String type) {
        super(address, type);
    }

    public Account(Cursor cursor) {
        super(cursor.getString(UIProvider.ACCOUNT_NAME_COLUMN), "unknown");
        accountFromAddressesUri = cursor.getString(UIProvider.ACCOUNT_FROM_ADDRESSES_URI_COLUMN);
        capabilities = cursor.getInt(UIProvider.ACCOUNT_CAPABILITIES_COLUMN);
        providerVersion = cursor.getInt(UIProvider.ACCOUNT_PROVIDER_VERISON_COLUMN);
        accountUri = cursor.getString(UIProvider.ACCOUNT_URI_COLUMN);
        folderListUri = cursor.getString(UIProvider.ACCOUNT_FOLDER_LIST_URI_COLUMN);
        searchUri = cursor.getString(UIProvider.ACCOUNT_SEARCH_URI_COLUMN);
        saveDraftUri = cursor.getString(UIProvider.ACCOUNT_SAVE_DRAFT_URI_COLUMN);
        sendMessageUri = cursor.getString(UIProvider.ACCOUNT_SEND_MESSAGE_URI_COLUMN);
        expungeMessageUri = cursor.getString(UIProvider.ACCOUNT_EXPUNGE_MESSAGE_URI_COLUMN);
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeInt(providerVersion);
        dest.writeString(accountUri);
        dest.writeInt(capabilities);
        dest.writeString(folderListUri);
        dest.writeString(searchUri);
        dest.writeString(accountFromAddressesUri);
        dest.writeString(saveDraftUri);
        dest.writeString(sendMessageUri);
        dest.writeString(expungeMessageUri);
    }

    @SuppressWarnings("hiding")
    public static final Creator<Account> CREATOR = new Creator<Account>() {
        @Override
        public Account createFromParcel(Parcel source) {
            return new Account(source);
        }

        @Override
        public Account[] newArray(int size) {
            return new Account[size];
        }
    };
}
