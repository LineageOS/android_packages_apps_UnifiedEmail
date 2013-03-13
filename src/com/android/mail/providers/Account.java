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

import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

import com.android.mail.content.CursorCreator;
import com.android.mail.content.ObjectCursor;
import com.android.mail.providers.UIProvider.AccountCapabilities;
import com.android.mail.providers.UIProvider.AccountColumns;
import com.android.mail.providers.UIProvider.SyncStatus;
import com.android.mail.utils.LogTag;
import com.android.mail.utils.LogUtils;
import com.android.mail.utils.Utils;
import com.google.common.base.Objects;
import com.google.common.collect.Lists;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Account extends android.accounts.Account implements Parcelable {
    private static final String SETTINGS_KEY = "settings";

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
     * The content provider uri to return the list of all folders for this
     * account.
     */
    public Uri fullFolderListUri;
    /**
     * The content provider uri that can be queried for search results.
     */
    public final Uri searchUri;

    /**
     * The custom from addresses for this account or null if there are none.
     */
    public String accountFromAddresses;

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
     * Uri for VIEW intent that will cause the help screens for this account type to be
     * shown.
     */
    public final Uri helpIntentUri;

    /**
     * Uri for VIEW intent that will cause the send feedback screens for this account type to be
     * shown.
     */
    public final Uri sendFeedbackIntentUri;

    /**
     * Uri for VIEW intent that will cause the reauthentication screen for this account to be
     * shown.
     */
    public final Uri reauthenticationIntentUri;

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
    /**
     * The color used for this account in combined view (Email)
     */
    public final int color;
    /**
     * URI for default recent folders for this account, if any.
     */
    public final Uri defaultRecentFolderListUri;
    /**
     * Settings object for this account.
     */
    public final Settings settings;

    /**
     * URI for forcing a manual sync of this account.
     */
    public final Uri manualSyncUri;

    /**
     * URI for account type specific supplementary account info on outgoing links, if any.
     */
    public final Uri viewIntentProxyUri;

    /**
     * URI for querying for the account cookies to be used when displaying inline content in a
     * conversation
     */
    public final Uri accoutCookieQueryUri;

    /**
     * URI to be used with an update() ContentResolver call with a {@link ContentValues} object
     * where the keys are from the {@link AccountColumns.SettingsColumns}, and the values are the
     * new values.
     */
    public final Uri updateSettingsUri;

    /**
     * Transient cache of parsed {@link #accountFromAddresses}, plus an entry for the main account
     * address.
     */
    private transient List<ReplyFromAccount> mReplyFroms;

    private static final String LOG_TAG = LogTag.getLogTag();

    /**
     * Return a serialized String for this account.
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
            json.put(UIProvider.AccountColumns.FULL_FOLDER_LIST_URI, fullFolderListUri);
            json.put(UIProvider.AccountColumns.SEARCH_URI, searchUri);
            json.put(UIProvider.AccountColumns.ACCOUNT_FROM_ADDRESSES, accountFromAddresses);
            json.put(UIProvider.AccountColumns.EXPUNGE_MESSAGE_URI, expungeMessageUri);
            json.put(UIProvider.AccountColumns.UNDO_URI, undoUri);
            json.put(UIProvider.AccountColumns.SETTINGS_INTENT_URI, settingsIntentUri);
            json.put(UIProvider.AccountColumns.HELP_INTENT_URI, helpIntentUri);
            json.put(UIProvider.AccountColumns.SEND_FEEDBACK_INTENT_URI, sendFeedbackIntentUri);
            json.put(UIProvider.AccountColumns.REAUTHENTICATION_INTENT_URI,
                    reauthenticationIntentUri);
            json.put(UIProvider.AccountColumns.SYNC_STATUS, syncStatus);
            json.put(UIProvider.AccountColumns.COMPOSE_URI, composeIntentUri);
            json.put(UIProvider.AccountColumns.MIME_TYPE, mimeType);
            json.put(UIProvider.AccountColumns.RECENT_FOLDER_LIST_URI, recentFolderListUri);
            json.put(UIProvider.AccountColumns.COLOR, color);
            json.put(UIProvider.AccountColumns.DEFAULT_RECENT_FOLDER_LIST_URI,
                    defaultRecentFolderListUri);
            json.put(UIProvider.AccountColumns.MANUAL_SYNC_URI,
                    manualSyncUri);
            json.put(UIProvider.AccountColumns.VIEW_INTENT_PROXY_URI,
                    viewIntentProxyUri);
            json.put(UIProvider.AccountColumns.ACCOUNT_COOKIE_QUERY_URI, accoutCookieQueryUri);
            json.put(UIProvider.AccountColumns.UPDATE_SETTINGS_URI, updateSettingsUri);
            if (settings != null) {
                json.put(SETTINGS_KEY, settings.toJSON());
            }
        } catch (JSONException e) {
            LogUtils.wtf(LOG_TAG, e, "Could not serialize account with name %s", name);
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
            LogUtils.w(LOG_TAG, e, "Could not create an account from this input: \"%s\"",
                    serializedAccount);
            return null;
        }
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
        providerVersion = json.getInt(UIProvider.AccountColumns.PROVIDER_VERSION);
        uri = Uri.parse(json.optString(UIProvider.AccountColumns.URI));
        capabilities = json.getInt(UIProvider.AccountColumns.CAPABILITIES);
        folderListUri = Utils
                .getValidUri(json.optString(UIProvider.AccountColumns.FOLDER_LIST_URI));
        fullFolderListUri = Utils.getValidUri(json
                .optString(UIProvider.AccountColumns.FULL_FOLDER_LIST_URI));
        searchUri = Utils.getValidUri(json.optString(UIProvider.AccountColumns.SEARCH_URI));
        accountFromAddresses = json.optString(UIProvider.AccountColumns.ACCOUNT_FROM_ADDRESSES,
                "");
        expungeMessageUri = Utils.getValidUri(json
                .optString(UIProvider.AccountColumns.EXPUNGE_MESSAGE_URI));
        undoUri = Utils.getValidUri(json.optString(UIProvider.AccountColumns.UNDO_URI));
        settingsIntentUri = Utils.getValidUri(json
                .optString(UIProvider.AccountColumns.SETTINGS_INTENT_URI));
        helpIntentUri = Utils
                .getValidUri(json.optString(UIProvider.AccountColumns.HELP_INTENT_URI));
        sendFeedbackIntentUri = Utils.getValidUri(json
                .optString(UIProvider.AccountColumns.SEND_FEEDBACK_INTENT_URI));
        reauthenticationIntentUri = Utils.getValidUri(
                json.optString(UIProvider.AccountColumns.REAUTHENTICATION_INTENT_URI));
        syncStatus = json.optInt(UIProvider.AccountColumns.SYNC_STATUS);
        composeIntentUri = Utils.getValidUri(json.optString(UIProvider.AccountColumns.COMPOSE_URI));
        mimeType = json.optString(UIProvider.AccountColumns.MIME_TYPE);
        recentFolderListUri = Utils.getValidUri(json
                .optString(UIProvider.AccountColumns.RECENT_FOLDER_LIST_URI));
        color = json.optInt(UIProvider.AccountColumns.COLOR, 0);
        defaultRecentFolderListUri = Utils.getValidUri(json
                .optString(UIProvider.AccountColumns.DEFAULT_RECENT_FOLDER_LIST_URI));
        manualSyncUri = Utils
                .getValidUri(json.optString(UIProvider.AccountColumns.MANUAL_SYNC_URI));
        viewIntentProxyUri = Utils
                .getValidUri(json.optString(UIProvider.AccountColumns.VIEW_INTENT_PROXY_URI));
        accoutCookieQueryUri = Utils.getValidUri(
                json.optString(UIProvider.AccountColumns.ACCOUNT_COOKIE_QUERY_URI));
        updateSettingsUri = Utils.getValidUri(
                json.optString(UIProvider.AccountColumns.UPDATE_SETTINGS_URI));

        final Settings jsonSettings = Settings.newInstance(json.optJSONObject(SETTINGS_KEY));
        if (jsonSettings != null) {
            settings = jsonSettings;
        } else {
            LogUtils.e(LOG_TAG, new Throwable(),
                    "Unexpected null settings in Account(name, type, jsonAccount)");
            settings = Settings.EMPTY_SETTINGS;
        }
    }

    public Account(Parcel in) {
        super(in);
        providerVersion = in.readInt();
        uri = in.readParcelable(null);
        capabilities = in.readInt();
        folderListUri = in.readParcelable(null);
        fullFolderListUri = in.readParcelable(null);
        searchUri = in.readParcelable(null);
        accountFromAddresses = in.readString();
        expungeMessageUri = in.readParcelable(null);
        undoUri = in.readParcelable(null);
        settingsIntentUri = in.readParcelable(null);
        helpIntentUri = in.readParcelable(null);
        sendFeedbackIntentUri = in.readParcelable(null);
        reauthenticationIntentUri = in.readParcelable(null);
        syncStatus = in.readInt();
        composeIntentUri = in.readParcelable(null);
        mimeType = in.readString();
        recentFolderListUri = in.readParcelable(null);
        color = in.readInt();
        defaultRecentFolderListUri = in.readParcelable(null);
        manualSyncUri = in.readParcelable(null);
        viewIntentProxyUri = in.readParcelable(null);
        accoutCookieQueryUri = in.readParcelable(null);
        updateSettingsUri = in.readParcelable(null);
        final String serializedSettings = in.readString();
        final Settings parcelSettings = Settings.newInstance(serializedSettings);
        if (parcelSettings != null) {
            settings = parcelSettings;
        } else {
            LogUtils.e(LOG_TAG, new Throwable(), "Unexpected null settings in Account(Parcel)");
            settings = Settings.EMPTY_SETTINGS;
        }
    }

    public Account(Cursor cursor) {
        super(cursor.getString(cursor.getColumnIndex(UIProvider.AccountColumns.NAME)), "unknown");
        accountFromAddresses = cursor.getString(
                cursor.getColumnIndex(UIProvider.AccountColumns.ACCOUNT_FROM_ADDRESSES));

        final int capabilitiesColumnIndex =
                cursor.getColumnIndex(UIProvider.AccountColumns.CAPABILITIES);
        if (capabilitiesColumnIndex != -1) {
            capabilities = cursor.getInt(capabilitiesColumnIndex);
        } else {
            capabilities = 0;
        }

        providerVersion =
                cursor.getInt(cursor.getColumnIndex(UIProvider.AccountColumns.PROVIDER_VERSION));
        uri = Uri.parse(cursor.getString(cursor.getColumnIndex(UIProvider.AccountColumns.URI)));
        folderListUri = Uri.parse(
                cursor.getString(cursor.getColumnIndex(UIProvider.AccountColumns.FOLDER_LIST_URI)));
        fullFolderListUri = Utils.getValidUri(cursor.getString(
                cursor.getColumnIndex(UIProvider.AccountColumns.FULL_FOLDER_LIST_URI)));
        searchUri = Utils.getValidUri(
                cursor.getString(cursor.getColumnIndex(UIProvider.AccountColumns.SEARCH_URI)));
        expungeMessageUri = Utils.getValidUri(cursor.getString(
                cursor.getColumnIndex(UIProvider.AccountColumns.EXPUNGE_MESSAGE_URI)));
        undoUri = Utils.getValidUri(
                cursor.getString(cursor.getColumnIndex(UIProvider.AccountColumns.UNDO_URI)));
        settingsIntentUri = Utils.getValidUri(cursor.getString(
                cursor.getColumnIndex(UIProvider.AccountColumns.SETTINGS_INTENT_URI)));
        helpIntentUri = Utils.getValidUri(
                cursor.getString(cursor.getColumnIndex(UIProvider.AccountColumns.HELP_INTENT_URI)));
        sendFeedbackIntentUri = Utils.getValidUri(cursor.getString(
                cursor.getColumnIndex(UIProvider.AccountColumns.SEND_FEEDBACK_INTENT_URI)));
        reauthenticationIntentUri = Utils.getValidUri(cursor.getString(
                cursor.getColumnIndex(UIProvider.AccountColumns.REAUTHENTICATION_INTENT_URI)));
        syncStatus = cursor.getInt(cursor.getColumnIndex(UIProvider.AccountColumns.SYNC_STATUS));
        composeIntentUri = Utils.getValidUri(
                cursor.getString(cursor.getColumnIndex(UIProvider.AccountColumns.COMPOSE_URI)));
        mimeType = cursor.getString(cursor.getColumnIndex(UIProvider.AccountColumns.MIME_TYPE));
        recentFolderListUri = Utils.getValidUri(cursor.getString(
                cursor.getColumnIndex(UIProvider.AccountColumns.RECENT_FOLDER_LIST_URI)));
        color = cursor.getInt(cursor.getColumnIndex(UIProvider.AccountColumns.COLOR));
        defaultRecentFolderListUri = Utils.getValidUri(cursor.getString(
                cursor.getColumnIndex(UIProvider.AccountColumns.DEFAULT_RECENT_FOLDER_LIST_URI)));
        manualSyncUri = Utils.getValidUri(
                cursor.getString(cursor.getColumnIndex(UIProvider.AccountColumns.MANUAL_SYNC_URI)));
        viewIntentProxyUri = Utils.getValidUri(cursor.getString(
                cursor.getColumnIndex(UIProvider.AccountColumns.VIEW_INTENT_PROXY_URI)));
        accoutCookieQueryUri = Utils.getValidUri(cursor.getString(
                cursor.getColumnIndex(UIProvider.AccountColumns.ACCOUNT_COOKIE_QUERY_URI)));
        updateSettingsUri = Utils.getValidUri(cursor.getString(
                cursor.getColumnIndex(UIProvider.AccountColumns.UPDATE_SETTINGS_URI)));
        settings = new Settings(cursor);
    }

    /**
     * Returns an array of all Accounts located at this cursor. This method returns a zero length
     * array if no account was found.  This method does not close the cursor.
     * @param cursor cursor pointing to the list of accounts
     * @return the array of all accounts stored at this cursor.
     */
    public static Account[] getAllAccounts(ObjectCursor<Account> cursor) {
        final int initialLength = cursor.getCount();
        if (initialLength <= 0 || !cursor.moveToFirst()) {
            // Return zero length account array rather than null
            return new Account[0];
        }

        final Account[] allAccounts = new Account[initialLength];
        int i = 0;
        do {
            allAccounts[i++] = cursor.getModel();
        } while (cursor.moveToNext());
        // Ensure that the length of the array is accurate
        assert (i == initialLength);
        return allAccounts;
    }

    public boolean supportsCapability(int capability) {
        return (capabilities & capability) != 0;
    }

    public boolean isAccountSyncRequired() {
        return (syncStatus & SyncStatus.INITIAL_SYNC_NEEDED) == SyncStatus.INITIAL_SYNC_NEEDED;
    }

    public boolean isAccountInitializationRequired() {
        return (syncStatus & SyncStatus.ACCOUNT_INITIALIZATION_REQUIRED) ==
                SyncStatus.ACCOUNT_INITIALIZATION_REQUIRED;
    }

    /**
     * Returns true when when the UI provider has indicated that the account has been initialized,
     * and sync is not required.
     */
    public boolean isAccountReady() {
        return !isAccountInitializationRequired() && !isAccountSyncRequired();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeInt(providerVersion);
        dest.writeParcelable(uri, 0);
        dest.writeInt(capabilities);
        dest.writeParcelable(folderListUri, 0);
        dest.writeParcelable(fullFolderListUri, 0);
        dest.writeParcelable(searchUri, 0);
        dest.writeString(accountFromAddresses);
        dest.writeParcelable(expungeMessageUri, 0);
        dest.writeParcelable(undoUri, 0);
        dest.writeParcelable(settingsIntentUri, 0);
        dest.writeParcelable(helpIntentUri, 0);
        dest.writeParcelable(sendFeedbackIntentUri, 0);
        dest.writeParcelable(reauthenticationIntentUri, 0);
        dest.writeInt(syncStatus);
        dest.writeParcelable(composeIntentUri, 0);
        dest.writeString(mimeType);
        dest.writeParcelable(recentFolderListUri, 0);
        dest.writeInt(color);
        dest.writeParcelable(defaultRecentFolderListUri, 0);
        dest.writeParcelable(manualSyncUri, 0);
        dest.writeParcelable(viewIntentProxyUri, 0);
        dest.writeParcelable(accoutCookieQueryUri, 0);
        dest.writeParcelable(updateSettingsUri, 0);
        if (settings == null) {
            LogUtils.e(LOG_TAG, "unexpected null settings object in writeToParcel");
        }
        dest.writeString(settings != null ? settings.serialize() : "");
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();

        sb.append("name=");
        sb.append(name);
        sb.append(",type=");
        sb.append(type);
        sb.append(",accountFromAddressUri=");
        sb.append(accountFromAddresses);
        sb.append(",capabilities=");
        sb.append(capabilities);
        sb.append(",providerVersion=");
        sb.append(providerVersion);
        sb.append(",folderListUri=");
        sb.append(folderListUri);
        sb.append(",fullFolderListUri=");
        sb.append(fullFolderListUri);
        sb.append(",searchUri=");
        sb.append(searchUri);
        sb.append(",saveDraftUri=");
        sb.append(",expungeMessageUri=");
        sb.append(expungeMessageUri);
        sb.append(",undoUri=");
        sb.append(undoUri);
        sb.append(",settingsIntentUri=");
        sb.append(settingsIntentUri);
        sb.append(",helpIntentUri=");
        sb.append(helpIntentUri);
        sb.append(",sendFeedbackIntentUri=");
        sb.append(sendFeedbackIntentUri);
        sb.append(",reauthenticationIntentUri=");
        sb.append(reauthenticationIntentUri);
        sb.append(",syncStatus=");
        sb.append(syncStatus);
        sb.append(",composeIntentUri=");
        sb.append(composeIntentUri);
        sb.append(",mimeType=");
        sb.append(mimeType);
        sb.append(",recentFoldersUri=");
        sb.append(recentFolderListUri);
        sb.append(",color=");
        sb.append(Integer.toHexString(color));
        sb.append(",defaultRecentFoldersUri=");
        sb.append(defaultRecentFolderListUri);
        sb.append(",settings=");
        sb.append(settings.serialize());

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
                Objects.equal(fullFolderListUri, other.fullFolderListUri) &&
                Objects.equal(searchUri, other.searchUri) &&
                Objects.equal(accountFromAddresses, other.accountFromAddresses) &&
                Objects.equal(expungeMessageUri, other.expungeMessageUri) &&
                Objects.equal(undoUri, other.undoUri) &&
                Objects.equal(settingsIntentUri, other.settingsIntentUri) &&
                Objects.equal(helpIntentUri, other.helpIntentUri) &&
                Objects.equal(sendFeedbackIntentUri, other.sendFeedbackIntentUri) &&
                Objects.equal(reauthenticationIntentUri, other.reauthenticationIntentUri) &&
                (syncStatus == other.syncStatus) &&
                Objects.equal(composeIntentUri, other.composeIntentUri) &&
                TextUtils.equals(mimeType, other.mimeType) &&
                Objects.equal(recentFolderListUri, other.recentFolderListUri) &&
                color == other.color &&
                Objects.equal(defaultRecentFolderListUri, other.defaultRecentFolderListUri) &&
                Objects.equal(viewIntentProxyUri, other.viewIntentProxyUri) &&
                Objects.equal(accoutCookieQueryUri, other.accoutCookieQueryUri) &&
                Objects.equal(updateSettingsUri, other.updateSettingsUri) &&
                Objects.equal(settings, other.settings);
    }

    /**
     * Returns true if the two accounts differ in sync or server-side settings.
     * This is <b>not</b> a replacement for {@link #equals(Object)}.
     * @param other
     * @return
     */
    public final boolean settingsDiffer(Account other) {
        // If the other object doesn't exist, they differ significantly.
        if (other == null) {
            return true;
        }
        // Check all the server-side settings, the user-side settings and the sync status.
        return ((this.syncStatus != other.syncStatus)
                || !Objects.equal(accountFromAddresses, other.accountFromAddresses)
                || color != other.color
                || (this.settings.hashCode() != other.settings.hashCode()));
    }

    @Override
    public int hashCode() {
        return super.hashCode()
                ^ Objects.hashCode(name, type, capabilities, providerVersion, uri, folderListUri,
                        fullFolderListUri, searchUri, accountFromAddresses, expungeMessageUri,
                        undoUri, settingsIntentUri, helpIntentUri, sendFeedbackIntentUri,
                        reauthenticationIntentUri, syncStatus, composeIntentUri, mimeType,
                        recentFolderListUri, color, defaultRecentFolderListUri, viewIntentProxyUri,
                        accoutCookieQueryUri, updateSettingsUri);
    }

    /**
     * Returns whether two Accounts match, as determined by their base URIs.
     * <p>For a deep object comparison, use {@link #equals(Object)}.
     *
     */
    public boolean matches(Account other) {
        return other != null && Objects.equal(uri, other.uri);
    }

    public List<ReplyFromAccount> getReplyFroms() {

        if (mReplyFroms == null) {
            mReplyFroms = Lists.newArrayList();

            // skip if sending is unsupported
            if (supportsCapability(AccountCapabilities.SENDING_UNAVAILABLE)) {
                return mReplyFroms;
            }

            // add the main account address
            mReplyFroms.add(new ReplyFromAccount(this, uri, name, name, name,
                    false /* isDefault */, false /* isCustom */));

            if (!TextUtils.isEmpty(accountFromAddresses)) {
                try {
                    JSONArray accounts = new JSONArray(accountFromAddresses);

                    for (int i = 0, len = accounts.length(); i < len; i++) {
                        final ReplyFromAccount a = ReplyFromAccount.deserialize(this,
                                accounts.getJSONObject(i));
                        if (a != null) {
                            mReplyFroms.add(a);
                        }
                    }

                } catch (JSONException e) {
                    LogUtils.e(LOG_TAG, e, "Unable to parse accountFromAddresses. name=%s", name);
                }
            }
        }
        return mReplyFroms;
    }

    /**
     * @param fromAddress a raw email address, e.g. "user@domain.com"
     * @return if the address belongs to this Account (either as the main address or as a
     * custom-from)
     */
    public boolean ownsFromAddress(String fromAddress) {
        for (ReplyFromAccount replyFrom : getReplyFroms()) {
            if (TextUtils.equals(replyFrom.address, fromAddress)) {
                return true;
            }
        }

        return false;
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

    /**
     * Find the position of the given needle in the given array of accounts.
     * @param haystack the array of accounts to search
     * @param needle the URI of account to find
     * @return a position between 0 and haystack.length-1 if an account is found, -1 if not found.
     */
    public static int findPosition(Account[] haystack, Uri needle) {
        if (haystack != null && haystack.length > 0 && needle != null) {
            // Need to go through the list of current accounts, and fix the
            // position.
            for (int i = 0, size = haystack.length; i < size; ++i) {
                if (haystack[i].uri.equals(needle)) {
                    LogUtils.d(LOG_TAG, "findPositionOfAccount: Found needle at position %d", i);
                    return i;
                }
            }
        }
        return -1;
    }

    /**
     * Creates a {@link Map} where the column name is the key and the value is the value, which can
     * be used for populating a {@link MatrixCursor}.
     */
    public Map<String, Object> getMatrixCursorValueMap() {
        // ImmutableMap.Builder does not allow null values
        final Map<String, Object> map = new HashMap<String, Object>();

        map.put(UIProvider.AccountColumns._ID, 0);
        map.put(UIProvider.AccountColumns.NAME, name);
        map.put(UIProvider.AccountColumns.TYPE, type);
        map.put(UIProvider.AccountColumns.PROVIDER_VERSION, providerVersion);
        map.put(UIProvider.AccountColumns.URI, uri);
        map.put(UIProvider.AccountColumns.CAPABILITIES, capabilities);
        map.put(UIProvider.AccountColumns.FOLDER_LIST_URI, folderListUri);
        map.put(UIProvider.AccountColumns.FULL_FOLDER_LIST_URI, fullFolderListUri);
        map.put(UIProvider.AccountColumns.SEARCH_URI, searchUri);
        map.put(UIProvider.AccountColumns.ACCOUNT_FROM_ADDRESSES, accountFromAddresses);
        map.put(UIProvider.AccountColumns.EXPUNGE_MESSAGE_URI, expungeMessageUri);
        map.put(UIProvider.AccountColumns.UNDO_URI, undoUri);
        map.put(UIProvider.AccountColumns.SETTINGS_INTENT_URI, settingsIntentUri);
        map.put(UIProvider.AccountColumns.HELP_INTENT_URI, helpIntentUri);
        map.put(UIProvider.AccountColumns.SEND_FEEDBACK_INTENT_URI, sendFeedbackIntentUri);
        map.put(
                UIProvider.AccountColumns.REAUTHENTICATION_INTENT_URI, reauthenticationIntentUri);
        map.put(UIProvider.AccountColumns.SYNC_STATUS, syncStatus);
        map.put(UIProvider.AccountColumns.COMPOSE_URI, composeIntentUri);
        map.put(UIProvider.AccountColumns.MIME_TYPE, mimeType);
        map.put(UIProvider.AccountColumns.RECENT_FOLDER_LIST_URI, recentFolderListUri);
        map.put(UIProvider.AccountColumns.DEFAULT_RECENT_FOLDER_LIST_URI,
                defaultRecentFolderListUri);
        map.put(UIProvider.AccountColumns.MANUAL_SYNC_URI, manualSyncUri);
        map.put(UIProvider.AccountColumns.VIEW_INTENT_PROXY_URI, viewIntentProxyUri);
        map.put(UIProvider.AccountColumns.ACCOUNT_COOKIE_QUERY_URI, accoutCookieQueryUri);
        map.put(UIProvider.AccountColumns.COLOR, color);
        map.put(UIProvider.AccountColumns.UPDATE_SETTINGS_URI, updateSettingsUri);
        map.put(AccountColumns.SettingsColumns.SIGNATURE, settings.signature);
        map.put(AccountColumns.SettingsColumns.AUTO_ADVANCE, settings.getAutoAdvanceSetting());
        map.put(AccountColumns.SettingsColumns.MESSAGE_TEXT_SIZE, settings.messageTextSize);
        map.put(AccountColumns.SettingsColumns.SNAP_HEADERS, settings.snapHeaders);
        map.put(AccountColumns.SettingsColumns.REPLY_BEHAVIOR, settings.replyBehavior);
        map.put(
                AccountColumns.SettingsColumns.HIDE_CHECKBOXES, settings.hideCheckboxes ? 1 : 0);
        map.put(AccountColumns.SettingsColumns.CONFIRM_DELETE, settings.confirmDelete ? 1 : 0);
        map.put(
                AccountColumns.SettingsColumns.CONFIRM_ARCHIVE, settings.confirmArchive ? 1 : 0);
        map.put(AccountColumns.SettingsColumns.CONFIRM_SEND, settings.confirmSend ? 1 : 0);
        map.put(AccountColumns.SettingsColumns.DEFAULT_INBOX, settings.defaultInbox);
        map.put(AccountColumns.SettingsColumns.DEFAULT_INBOX_NAME, settings.defaultInboxName);
        map.put(AccountColumns.SettingsColumns.FORCE_REPLY_FROM_DEFAULT,
                settings.forceReplyFromDefault ? 1 : 0);
        map.put(AccountColumns.SettingsColumns.MAX_ATTACHMENT_SIZE, settings.maxAttachmentSize);
        map.put(AccountColumns.SettingsColumns.SWIPE, settings.swipe);
        map.put(AccountColumns.SettingsColumns.PRIORITY_ARROWS_ENABLED,
                settings.priorityArrowsEnabled ? 1 : 0);
        map.put(AccountColumns.SettingsColumns.SETUP_INTENT_URI, settings.setupIntentUri);
        map.put(AccountColumns.SettingsColumns.CONVERSATION_VIEW_MODE,
                settings.conversationViewMode);
        map.put(AccountColumns.SettingsColumns.VEILED_ADDRESS_PATTERN,
                settings.veiledAddressPattern);

        return map;
    }

    /**
     * Public object that knows how to construct Accounts given Cursors.
     */
    public final static CursorCreator<Account> FACTORY = new CursorCreator<Account>() {
        @Override
        public Account createFromCursor(Cursor c) {
            return new Account(c);
        }

        @Override
        public String toString() {
            return "Account CursorCreator";
        }
    };
}
