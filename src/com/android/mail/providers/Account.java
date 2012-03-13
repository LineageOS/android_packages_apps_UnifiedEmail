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

import com.android.mail.utils.LogUtils;

import android.database.Cursor;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

import com.google.common.base.Objects;

import org.json.JSONException;
import org.json.JSONObject;

import java.lang.StringBuilder;

public class Account extends android.accounts.Account implements Parcelable {
    /**
     * The version of the UI provider schema from which this account provider
     * will return results.
     */
    public final int providerVersion;

    /**
     * The uri to directly access the information for this account.
     */
    public final Uri uri;

    /**
     * The possible capabilities that this account supports.
     */
    public final int capabilities;

    /**
     * The content provider uri to return the list of top level folders for this
     * account.
     */
    public final Uri folderListUri;

    /**
     * The content provider uri that can be queried for search results.
     */
    public final Uri searchUri;

    /**
     * The content provider uri that can be queried to access the from addresses
     * for this account.
     */
    public final Uri accountFromAddressesUri;

    /**
     * The content provider uri that can be used to save (insert) new draft
     * messages for this account. NOTE: This might be better to be an update
     * operation on the messageUri.
     */
    public final Uri saveDraftUri;

    /**
     * The content provider uri that can be used to send a message for this
     * account.
     * NOTE: This might be better to be an update operation on the
     * messageUri.
     */
    public final Uri sendMessageUri;

    /**
     * The content provider uri that can be used to expunge message from this
     * account. NOTE: This might be better to be an update operation on the
     * messageUri.
     */
    public final Uri expungeMessageUri;

    /**
     * The content provider uri that can be used to undo the last operation
     * performed.
     */
    public final Uri undoUri;

    /**
     * Uri for EDIT intent that will cause the settings screens for this account type to be
     * shown.
     */
    public final Uri settingsIntentUri;

    /**
     * The content provider uri that can be used to query user settings/preferences
     */
    public final Uri settingsQueryUri;

    /**
     * Uri for VIEW intent that will cause the help screens for this account type to be
     * shown.
     */
    public final Uri helpIntentUri;

    /**
     * The sync status of the account
     */
    public final int syncStatus;

    /**
     * Uri for VIEW intent that will cause the compose screen for this account type to be
     * shown.
     */
    public final Uri composeIntentUri;

    public final String mimeType;
    /**
     * URI for recent folders for this account.
     */
    public final Uri recentFolderListUri;

    private static final String LOG_TAG = new LogUtils().getLogTag();

    /**
     * Return a serialized String for this folder.
     */
    public synchronized String serialize() {
        JSONObject json = new JSONObject();
        try {
            json.put(UIProvider.AccountColumns.NAME, name);
            json.put(UIProvider.AccountColumns.TYPE, type);
            json.put(UIProvider.AccountColumns.PROVIDER_VERSION, providerVersion);
            json.put(UIProvider.AccountColumns.URI, uri);
            json.put(UIProvider.AccountColumns.CAPABILITIES, capabilities);
            json.put(UIProvider.AccountColumns.FOLDER_LIST_URI, folderListUri);
            json.put(UIProvider.AccountColumns.SEARCH_URI, searchUri);
            json.put(UIProvider.AccountColumns.ACCOUNT_FROM_ADDRESSES_URI,
                    accountFromAddressesUri);
            json.put(UIProvider.AccountColumns.SAVE_DRAFT_URI, saveDraftUri);
            json.put(UIProvider.AccountColumns.SEND_MAIL_URI, sendMessageUri);
            json.put(UIProvider.AccountColumns.EXPUNGE_MESSAGE_URI, expungeMessageUri);
            json.put(UIProvider.AccountColumns.UNDO_URI, undoUri);
            json.put(UIProvider.AccountColumns.SETTINGS_INTENT_URI, settingsIntentUri);
            json.put(UIProvider.AccountColumns.SETTINGS_QUERY_URI, settingsQueryUri);
            json.put(UIProvider.AccountColumns.HELP_INTENT_URI, helpIntentUri);
            json.put(UIProvider.AccountColumns.SYNC_STATUS, syncStatus);
            json.put(UIProvider.AccountColumns.COMPOSE_URI, composeIntentUri);
            json.put(UIProvider.AccountColumns.MIME_TYPE, mimeType);
            json.put(UIProvider.AccountColumns.RECENT_FOLDER_LIST_URI, recentFolderListUri);
        } catch (JSONException e) {
            LogUtils.wtf(LOG_TAG, e, "Could not serialize account with name " + name);
        }
        return json.toString();
    }

    /**
     * Create a new instance of an Account object using a serialized instance created previously
     * using {@link #serialize()}. This returns null if the serialized instance was invalid or does
     * not represent a valid account object.
     *
     * @param serializedAccount
     * @return
     */
    public static Account newinstance(String serializedAccount) {
        // The heavy lifting is done by Account(name, type, serializedAccount). This method
        // is a wrapper to check for errors and exceptions and return back a null in cases
        // something breaks.
        JSONObject json = null;
        try {
            json = new JSONObject(serializedAccount);
            final String name = (String) json.get(UIProvider.AccountColumns.NAME);
            final String type = (String) json.get(UIProvider.AccountColumns.TYPE);
            return new Account(name, type, serializedAccount);
        } catch (JSONException e) {
            LogUtils.wtf(LOG_TAG, e, "Could not create an account from this input: \""
                    + serializedAccount);
            return null;
        }
    }

    /**
     * Parse a string (possibly null or empty) into a URI. If the string is null or empty, null
     * is returned back. Otherwise an empty URI is returned.
     * @param uri
     * @return a valid URI, possibly {@link android.net.Uri#EMPTY}
     */
    private static Uri getValidUri(String uri) {
        if (uri == JSONObject.NULL)
            return Uri.EMPTY;
        return Uri.parse(uri);
    }

    /**
     * Construct a new Account instance from a previously serialized string. This calls
     * {@link android.accounts.Account#Account(String, String)} with name and type given as the
     * first two arguments.
     *
     * <p>
     * This is private. Public uses should go through the safe {@link #newinstance(String)} method.
     * </p>
     * @param name name of account in {@link android.accounts.Account}
     * @param type type of account in {@link android.accounts.Account}
     * @param jsonAccount string obtained from {@link #serialize()} on a valid account.
     * @throws JSONException
     */
    private Account(String name, String type, String jsonAccount) throws JSONException {
        super(name, type);
        final JSONObject json = new JSONObject(jsonAccount);
        providerVersion = Integer.valueOf(json.getInt(UIProvider.AccountColumns.PROVIDER_VERSION));
        uri = Uri.parse(json.optString(UIProvider.AccountColumns.URI));
        capabilities = Integer.valueOf(json.getInt(UIProvider.AccountColumns.CAPABILITIES));
        folderListUri = getValidUri(json.optString(UIProvider.AccountColumns.FOLDER_LIST_URI));
        searchUri = getValidUri(json.optString(UIProvider.AccountColumns.SEARCH_URI));
        accountFromAddressesUri = getValidUri(json
                .optString(UIProvider.AccountColumns.ACCOUNT_FROM_ADDRESSES_URI));
        saveDraftUri = getValidUri(json.optString(UIProvider.AccountColumns.SAVE_DRAFT_URI));
        sendMessageUri = getValidUri(json.optString(UIProvider.AccountColumns.SEND_MAIL_URI));
        expungeMessageUri = getValidUri(json
                .optString(UIProvider.AccountColumns.EXPUNGE_MESSAGE_URI));
        undoUri = getValidUri(json.optString(UIProvider.AccountColumns.UNDO_URI));
        settingsIntentUri = getValidUri(json
                .optString(UIProvider.AccountColumns.SETTINGS_INTENT_URI));
        settingsQueryUri = getValidUri(json.optString(
                UIProvider.AccountColumns.SETTINGS_QUERY_URI));
        helpIntentUri = getValidUri(json.optString(UIProvider.AccountColumns.HELP_INTENT_URI));
        syncStatus = Integer.valueOf(json.optInt(UIProvider.AccountColumns.SYNC_STATUS));
        composeIntentUri = getValidUri(json.optString(UIProvider.AccountColumns.COMPOSE_URI));
        mimeType = json.optString(UIProvider.AccountColumns.MIME_TYPE);
        recentFolderListUri = getValidUri(
                json.optString(UIProvider.AccountColumns.RECENT_FOLDER_LIST_URI));
    }

    public Account(Parcel in) {
        super(in);
        providerVersion = in.readInt();
        uri = in.readParcelable(null);
        capabilities = in.readInt();
        folderListUri = in.readParcelable(null);
        searchUri = in.readParcelable(null);
        accountFromAddressesUri = in.readParcelable(null);
        saveDraftUri = in.readParcelable(null);
        sendMessageUri = in.readParcelable(null);
        expungeMessageUri = in.readParcelable(null);
        undoUri = in.readParcelable(null);
        settingsIntentUri = in.readParcelable(null);
        settingsQueryUri = in.readParcelable(null);
        helpIntentUri = in.readParcelable(null);
        syncStatus = in.readInt();
        composeIntentUri = in.readParcelable(null);
        mimeType = in.readString();
        recentFolderListUri = in.readParcelable(null);
    }

    public Account(Cursor cursor) {
        super(cursor.getString(UIProvider.ACCOUNT_NAME_COLUMN), "unknown");
        String fromAddresses = cursor
                .getString(UIProvider.ACCOUNT_FROM_ADDRESSES_URI_COLUMN);
        accountFromAddressesUri = !TextUtils.isEmpty(fromAddresses) ? Uri.parse(fromAddresses)
                : null;
        capabilities = cursor.getInt(UIProvider.ACCOUNT_CAPABILITIES_COLUMN);
        providerVersion = cursor.getInt(UIProvider.ACCOUNT_PROVIDER_VERISON_COLUMN);
        uri = Uri.parse(cursor.getString(UIProvider.ACCOUNT_URI_COLUMN));
        folderListUri = Uri.parse(cursor.getString(UIProvider.ACCOUNT_FOLDER_LIST_URI_COLUMN));
        final String search = cursor.getString(UIProvider.ACCOUNT_SEARCH_URI_COLUMN);
        searchUri = !TextUtils.isEmpty(search) ? Uri.parse(search) : null;
        final String saveDraft = cursor.getString(UIProvider.ACCOUNT_SAVE_DRAFT_URI_COLUMN);
        saveDraftUri = !TextUtils.isEmpty(saveDraft) ? Uri.parse(saveDraft) : null;
        final String send = cursor.getString(UIProvider.ACCOUNT_SEND_MESSAGE_URI_COLUMN);
        sendMessageUri = !TextUtils.isEmpty(send) ? Uri.parse(send) : null;
        final String expunge = cursor.getString(UIProvider.ACCOUNT_EXPUNGE_MESSAGE_URI_COLUMN);
        expungeMessageUri = !TextUtils.isEmpty(expunge) ? Uri.parse(expunge) : null;
        final String undo = cursor.getString(UIProvider.ACCOUNT_UNDO_URI_COLUMN);
        undoUri = !TextUtils.isEmpty(undo) ? Uri.parse(undo) : null;
        final String settings = cursor.getString(UIProvider.ACCOUNT_SETTINGS_INTENT_URI_COLUMN);
        settingsIntentUri = !TextUtils.isEmpty(settings) ? Uri.parse(settings) : null;
        final String settingsQuery = cursor.getString(UIProvider.ACCOUNT_SETTINGS_QUERY_URI_COLUMN);
        settingsQueryUri = !TextUtils.isEmpty(settingsQuery) ? Uri.parse(settingsQuery) : null;
        final String help = cursor.getString(UIProvider.ACCOUNT_HELP_INTENT_URI_COLUMN);
        helpIntentUri = !TextUtils.isEmpty(help) ? Uri.parse(help) : null;
        syncStatus = cursor.getInt(UIProvider.ACCOUNT_SYNC_STATUS_COLUMN);
        final String compose = cursor.getString(UIProvider.ACCOUNT_COMPOSE_INTENT_URI_COLUMN);
        composeIntentUri = !TextUtils.isEmpty(compose) ? Uri.parse(compose) : null;
        mimeType = cursor.getString(UIProvider.ACCOUNT_MIME_TYPE_COLUMN);
        final String recent = cursor.getString(UIProvider.ACCOUNT_RECENT_FOLDER_LIST_URI_COLUMN);
        recentFolderListUri = !TextUtils.isEmpty(recent) ? Uri.parse(recent) : null;
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
        dest.writeParcelable(uri, 0);
        dest.writeInt(capabilities);
        dest.writeParcelable(folderListUri, 0);
        dest.writeParcelable(searchUri, 0);
        dest.writeParcelable(accountFromAddressesUri, 0);
        dest.writeParcelable(saveDraftUri, 0);
        dest.writeParcelable(sendMessageUri, 0);
        dest.writeParcelable(expungeMessageUri, 0);
        dest.writeParcelable(undoUri, 0);
        dest.writeParcelable(settingsIntentUri, 0);
        dest.writeParcelable(settingsQueryUri, 0);
        dest.writeParcelable(helpIntentUri, 0);
        dest.writeInt(syncStatus);
        dest.writeParcelable(composeIntentUri, 0);
        dest.writeString(mimeType);
        dest.writeParcelable(recentFolderListUri, 0);
    }

    /**
     * Get the settings associated with this account.
     * TODO: this method is just a stand-in.
     */
    public Cursor getSettings() {
        return null;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();

        sb.append("name=");
        sb.append(name);
        sb.append(",type=");
        sb.append(type);
        sb.append(",accountFromAddressUri=");
        sb.append(accountFromAddressesUri);
        sb.append(",capabilities=");
        sb.append(capabilities);
        sb.append(",providerVersion=");
        sb.append(providerVersion);
        sb.append(",folderListUri=");
        sb.append(folderListUri);
        sb.append(",searchUri=");
        sb.append(searchUri);
        sb.append(",saveDraftUri=");
        sb.append(saveDraftUri);
        sb.append(",sendMessageUri=");
        sb.append(sendMessageUri);
        sb.append(",expungeMessageUri=");
        sb.append(expungeMessageUri);
        sb.append(",undoUri=");
        sb.append(undoUri);
        sb.append(",settingsIntentUri=");
        sb.append(settingsIntentUri);
        sb.append(",settingsQueryUri=");
        sb.append(settingsQueryUri);
        sb.append(",helpIntentUri=");
        sb.append(helpIntentUri);
        sb.append(",syncStatus=");
        sb.append(syncStatus);
        sb.append(",composeIntentUri=");
        sb.append(composeIntentUri);
        sb.append(",mimeType=");
        sb.append(mimeType);
        sb.append(",recentFoldersUri=");
        sb.append(recentFolderListUri);

        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }

        if ((o == null) || (o.getClass() != this.getClass())) {
            return false;
        }

        final Account other = (Account) o;
        return TextUtils.equals(name, other.name) && TextUtils.equals(type, other.type) &&
                capabilities == other.capabilities && providerVersion == other.providerVersion &&
                Objects.equal(uri, other.uri) &&
                Objects.equal(folderListUri, other.folderListUri) &&
                Objects.equal(searchUri, other.searchUri) &&
                Objects.equal(accountFromAddressesUri, other.accountFromAddressesUri) &&
                Objects.equal(saveDraftUri, other.saveDraftUri) &&
                Objects.equal(sendMessageUri, other.sendMessageUri) &&
                Objects.equal(expungeMessageUri, other.expungeMessageUri) &&
                Objects.equal(undoUri, other.undoUri) &&
                Objects.equal(settingsIntentUri, other.settingsIntentUri) &&
                Objects.equal(settingsQueryUri, other.settingsQueryUri) &&
                Objects.equal(helpIntentUri, other.helpIntentUri) &&
                (syncStatus == other.syncStatus) &&
                Objects.equal(composeIntentUri, other.composeIntentUri) &&
                TextUtils.equals(mimeType, other.mimeType) &&
                Objects.equal(recentFolderListUri, other.recentFolderListUri);
    }

    @Override
    public int hashCode() {
        return super.hashCode() ^ Objects.hashCode(name, type, capabilities, providerVersion,
                uri, folderListUri, searchUri, accountFromAddressesUri, saveDraftUri,
                sendMessageUri, expungeMessageUri, undoUri, settingsIntentUri, settingsQueryUri,
                helpIntentUri, syncStatus, composeIntentUri, mimeType, recentFolderListUri);
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
