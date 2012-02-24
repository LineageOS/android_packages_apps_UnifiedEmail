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
import android.text.TextUtils;

import java.util.regex.Pattern;

import com.android.mail.utils.LogUtils;

public class Account extends android.accounts.Account implements Parcelable {
    /**
     * The version of the UI provider schema from which this account provider
     * will return results.
     */
    public final int providerVersion;

    /**
     * The uri to directly access the information for this account.
     */
    public final String uri;

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
     * Uri for EDIT intent that will cause the settings screens for this account type to be
     * shown.
     */
    public final String settingIntentUri;

    /**
     * Uri for VIEW intent that will cause the help screens for this account type to be
     * shown.
     */
    public final String helpIntentUri;

    /**
     * The sync status of the account
     */
    public final int syncStatus;

    /**
     * Total number of members that comprise an instance of an account. This is
     * the number of members that need to be serialized or parceled. This
     * includes the members described above and name and type from the
     * superclass.
     */
    private static final int NUMBER_MEMBERS = 15;

    /**
     * Examples of expected format for the joined account strings
     *
     * Example of a joined account string:
     *       630107622^*^^i^*^^i^*^0
     *       <id>^*^<canonical name>^*^<name>^*^<color index>
     *
     */
    private static final String ACCOUNT_COMPONENT_SEPARATOR = "^*^";
    private static final Pattern ACCOUNT_COMPONENT_SEPARATOR_PATTERN =
            Pattern.compile("\\^\\*\\^");
    private static final String LOG_TAG = new LogUtils().getLogTag();

    /**
     * Return a serialized String for this folder.
     */
    public synchronized String serialize() {
        StringBuilder out = new StringBuilder();
        out.append(name).append(ACCOUNT_COMPONENT_SEPARATOR);
        out.append(type).append(ACCOUNT_COMPONENT_SEPARATOR);
        out.append(providerVersion).append(ACCOUNT_COMPONENT_SEPARATOR);
        out.append(uri).append(ACCOUNT_COMPONENT_SEPARATOR);
        out.append(capabilities).append(ACCOUNT_COMPONENT_SEPARATOR);
        out.append(folderListUri).append(ACCOUNT_COMPONENT_SEPARATOR);
        out.append(searchUri).append(ACCOUNT_COMPONENT_SEPARATOR);
        out.append(accountFromAddressesUri).append(ACCOUNT_COMPONENT_SEPARATOR);
        out.append(saveDraftUri).append(ACCOUNT_COMPONENT_SEPARATOR);
        out.append(sendMessageUri).append(ACCOUNT_COMPONENT_SEPARATOR);
        out.append(expungeMessageUri).append(ACCOUNT_COMPONENT_SEPARATOR);
        out.append(undoUri).append(ACCOUNT_COMPONENT_SEPARATOR);
        out.append(settingIntentUri).append(ACCOUNT_COMPONENT_SEPARATOR);
        out.append(helpIntentUri).append(ACCOUNT_COMPONENT_SEPARATOR);
        out.append(syncStatus);
        return out.toString();
    }

    /**
     * Construct a new Account instance from a previously serialized string.
     * @param serializedAccount string obtained from {@link #serialize()} on a valid account.
     */
    public Account(String serializedAccount) {
        super(TextUtils.split(serializedAccount, ACCOUNT_COMPONENT_SEPARATOR_PATTERN)[0], TextUtils
                .split(serializedAccount, ACCOUNT_COMPONENT_SEPARATOR_PATTERN)[1]);
        String[] accountMembers = TextUtils.split(serializedAccount,
                ACCOUNT_COMPONENT_SEPARATOR_PATTERN);
        if (accountMembers.length != NUMBER_MEMBERS) {
            throw new IllegalArgumentException(
                    "Account de-serializing failed. Wrong number of members detected.");
        }
        providerVersion = Integer.valueOf(accountMembers[2]);
        uri = accountMembers[3];
        capabilities = Integer.valueOf(accountMembers[4]);
        folderListUri = accountMembers[5];
        searchUri = accountMembers[6];
        accountFromAddressesUri = accountMembers[7];
        saveDraftUri = accountMembers[8];
        sendMessageUri = accountMembers[9];
        expungeMessageUri = accountMembers[10];
        undoUri = accountMembers[11];
        settingIntentUri = accountMembers[12];
        helpIntentUri = accountMembers[13];
        syncStatus = Integer.valueOf(accountMembers[14]);
    }

    public Account(Parcel in) {
        super(in);
        providerVersion = in.readInt();
        uri = in.readString();
        capabilities = in.readInt();
        folderListUri = in.readString();
        searchUri = in.readString();
        accountFromAddressesUri = in.readString();
        saveDraftUri = in.readString();
        sendMessageUri = in.readString();
        expungeMessageUri = in.readString();
        undoUri = in.readString();
        settingIntentUri = in.readString();
        helpIntentUri = in.readString();
        syncStatus = in.readInt();
    }

    public Account(Cursor cursor) {
        super(cursor.getString(UIProvider.ACCOUNT_NAME_COLUMN), "unknown");
        accountFromAddressesUri = cursor.getString(UIProvider.ACCOUNT_FROM_ADDRESSES_URI_COLUMN);
        capabilities = cursor.getInt(UIProvider.ACCOUNT_CAPABILITIES_COLUMN);
        providerVersion = cursor.getInt(UIProvider.ACCOUNT_PROVIDER_VERISON_COLUMN);
        uri = cursor.getString(UIProvider.ACCOUNT_URI_COLUMN);
        folderListUri = cursor.getString(UIProvider.ACCOUNT_FOLDER_LIST_URI_COLUMN);
        searchUri = cursor.getString(UIProvider.ACCOUNT_SEARCH_URI_COLUMN);
        saveDraftUri = cursor.getString(UIProvider.ACCOUNT_SAVE_DRAFT_URI_COLUMN);
        sendMessageUri = cursor.getString(UIProvider.ACCOUNT_SEND_MESSAGE_URI_COLUMN);
        expungeMessageUri = cursor.getString(UIProvider.ACCOUNT_EXPUNGE_MESSAGE_URI_COLUMN);
        undoUri = cursor.getString(UIProvider.ACCOUNT_UNDO_URI_COLUMN);
        settingIntentUri = cursor.getString(UIProvider.ACCOUNT_SETTINGS_INTENT_URI_COLUMN);
        helpIntentUri = cursor.getString(UIProvider.ACCOUNT_HELP_INTENT_URI_COLUMN);
        syncStatus = cursor.getInt(UIProvider.ACCOUNT_SYNC_STATUS_COLUMN);
    }

    /**
     * Returns an array of all Accounts located at this cursor.
     * This method does not close the cursor.
     * @param cursor cursor pointing to the list of accounts
     * @return the array of all accounts stored at this cursor.
     */
    public static Account[] getAllAccounts(Cursor cursor) {
        final int initialLength = cursor.getCount();
        if (initialLength <= 0 || !cursor.moveToFirst()) {
            // Return zero length account array rather than null
            return new Account[0];
        }

        Account[] allAccounts = new Account[initialLength];
        for (int i = 0; i < initialLength; i++) {
            allAccounts[i] = new Account(cursor);
            if (!cursor.moveToNext()) {
                LogUtils.d(LOG_TAG, "Expecting " + initialLength + " accounts. Got: " + i);
                break;
            }
        }
        return allAccounts;
    }

    public boolean supportsCapability(int capability) {
        return (capabilities & capability) != 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeInt(providerVersion);
        dest.writeString(uri);
        dest.writeInt(capabilities);
        dest.writeString(folderListUri);
        dest.writeString(searchUri);
        dest.writeString(accountFromAddressesUri);
        dest.writeString(saveDraftUri);
        dest.writeString(sendMessageUri);
        dest.writeString(expungeMessageUri);
        dest.writeString(undoUri);
        dest.writeString(settingIntentUri);
        dest.writeString(helpIntentUri);
        dest.writeInt(syncStatus);
    }

    /**
     * Get the settings associated with this account.
     * TODO: this method is just a stand-in.
     */
    public Cursor getSettings() {
        return null;
    }

    public Folder getAccountInbox() {
        // TODO: (mindyp) fill in with call to settings or reading of account settings
        // to get the default inbox for this account.
        return null;
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
