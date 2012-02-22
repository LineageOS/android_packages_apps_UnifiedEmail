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
import android.os.Parcelable;

public class Account extends android.accounts.Account implements Parcelable {
    /**
     * The version of the UI provider schema from which this account provider
     * will return results.
     */
    public final int providerVersion;

    /**
     * The uri to directly access the information for this account.
     */
    public final String accountUri;

    /**
     * The possible capabilities that this account supports.
     */
    public final int capabilities;

    /**
     * The content provider uri to return the list of top level folders for this
     * account.
     */
    public final String folderListUri;

    /**
     * The content provider uri that can be queried for search results.
     */
    public final String searchUri;

    /**
     * The content provider uri that can be queried to access the from addresses
     * for this account.
     */
    public final String accountFromAddressesUri;

    /**
     * The content provider uri that can be used to save (insert) new draft
     * messages for this account. NOTE: This might be better to be an update
     * operation on the messageUri.
     */
    public final String saveDraftUri;

    /**
     * The content provider uri that can be used to send a message for this
     * account.
     * NOTE: This might be better to be an update operation on the
     * messageUri.
     */
    public final String sendMessageUri;

    /**
     * The content provider uri that can be used to expunge message from this
     * account. NOTE: This might be better to be an update operation on the
     * messageUri.
     */
    public final String expungeMessageUri;

    /**
     * The content provider uri that can be used to undo the last operation
     * performed.
     */
    public final String undoUri;

    /**
     * Uri for VIEW intent that will cause the settings screens for this account type to be
     * shown.
     */
    public final String settingIntentUri;

    /**
     * The sync status of the account
     */
    public final int syncStatus;

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
        undoUri = in.readString();
        settingIntentUri = in.readString();
        syncStatus = in.readInt();
    }

    // TODO(pwestbro): remove this constructor.
    // This constructor should't be necessary.  Any usage of an Account object constructed
    // this way may have unexpected results.
    @Deprecated
    public Account(String address, String type) {
        super(address, type);
        providerVersion = -1;
        accountUri = null;
        capabilities = 0;
        folderListUri = null;
        searchUri = null;
        accountFromAddressesUri = null;
        saveDraftUri = null;
        sendMessageUri = null;
        expungeMessageUri = null;
        undoUri = null;
        settingIntentUri = null;
        syncStatus = 0;
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
        undoUri = cursor.getString(UIProvider.ACCOUNT_UNDO_URI_COLUMN);
        settingIntentUri = cursor.getString(UIProvider.ACCOUNT_SETTINGS_INTENT_URI_COLUMN);
        syncStatus = cursor.getInt(UIProvider.ACCOUNT_SYNC_STATUS_COLUMN);
    }

    public boolean supportsCapability(int capability) {
        return (capabilities & capability) != 0;
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
        dest.writeString(undoUri);
        dest.writeString(settingIntentUri);
        dest.writeInt(syncStatus);
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
