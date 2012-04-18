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

import com.android.mail.providers.UIProvider.AutoAdvance;
import com.android.mail.providers.UIProvider.DefaultReplyBehavior;
import com.android.mail.providers.UIProvider.MessageTextSize;
import com.android.mail.providers.UIProvider.SnapHeaderValue;
import com.android.mail.utils.LogUtils;
import com.android.mail.utils.Utils;

import android.database.Cursor;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

import org.json.JSONException;
import org.json.JSONObject;


/**
 * Model to hold Settings for an account.
 */
public class Settings implements Parcelable {
    /**
     * Interface to listen to settings changes. You need to register with the
     * {@link com.android.mail.ui.ActivityController} for observing changes to settings.
     */
    public interface ChangeListener {
        /**
         * Method that is called when settings are changed.
         * @param updatedSettings the updated settings.
         */
        public void onSettingsChanged(Settings updatedSettings);
    }

    private static final String LOG_TAG = new LogUtils().getLogTag();

    static final Settings EMPTY_SETTINGS = new Settings();

    public final String signature;
    public final int autoAdvance;
    public final int messageTextSize;
    public final int snapHeaders;
    public final int replyBehavior;
    public final boolean hideCheckboxes;
    public final boolean confirmDelete;
    public final boolean confirmArchive;
    public final boolean confirmSend;
    public final Uri defaultInbox;
    public final boolean forceReplyFromDefault;

    private Settings() {
        signature = null;
        autoAdvance = AutoAdvance.LIST;
        messageTextSize = MessageTextSize.NORMAL;
        snapHeaders = SnapHeaderValue.ALWAYS;
        replyBehavior = DefaultReplyBehavior.REPLY;
        hideCheckboxes = false;
        confirmDelete = false;
        confirmArchive = false;
        confirmSend = false;
        defaultInbox = null;
        forceReplyFromDefault = false;
    }

    public Settings(Parcel inParcel) {
        signature = inParcel.readString();
        autoAdvance = inParcel.readInt();
        messageTextSize = inParcel.readInt();
        snapHeaders = inParcel.readInt();
        replyBehavior = inParcel.readInt();
        hideCheckboxes = inParcel.readInt() != 0;
        confirmDelete = inParcel.readInt() != 0;
        confirmArchive = inParcel.readInt() != 0;
        confirmSend = inParcel.readInt() != 0;
        final String inbox = inParcel.readString();
        defaultInbox = !TextUtils.isEmpty(inbox) ? Uri.parse(inbox) : null;
        forceReplyFromDefault = inParcel.readInt() != 0;
    }

    public Settings(Cursor cursor) {
        signature = cursor.getString(UIProvider.ACCOUNT_SETTINGS_SIGNATURE_COLUMN);
        autoAdvance = cursor.getInt(UIProvider.ACCOUNT_SETTINGS_AUTO_ADVANCE_COLUMN);
        messageTextSize = cursor.getInt(UIProvider.ACCOUNT_SETTINGS_MESSAGE_TEXT_SIZE_COLUMN);
        snapHeaders = cursor.getInt(UIProvider.ACCOUNT_SETTINGS_SNAP_HEADERS_COLUMN);
        replyBehavior = cursor.getInt(UIProvider.ACCOUNT_SETTINGS_REPLY_BEHAVIOR_COLUMN);
        hideCheckboxes = cursor.getInt(UIProvider.ACCOUNT_SETTINGS_HIDE_CHECKBOXES_COLUMN) != 0;
        confirmDelete = cursor.getInt(UIProvider.ACCOUNT_SETTINGS_CONFIRM_DELETE_COLUMN) != 0;
        confirmArchive = cursor.getInt(UIProvider.ACCOUNT_SETTINGS_CONFIRM_ARCHIVE_COLUMN) != 0;
        confirmSend = cursor.getInt(UIProvider.ACCOUNT_SETTINGS_CONFIRM_SEND_COLUMN) != 0;
        final String inbox = cursor.getString(UIProvider.ACCOUNT_SETTINGS_DEFAULT_INBOX_COLUMN);
        defaultInbox = !TextUtils.isEmpty(inbox) ? Uri.parse(inbox) : null;
        forceReplyFromDefault = cursor.getInt(
                UIProvider.ACCOUNT_SETTINGS_FORCE_REPLY_FROM_DEFAULT_COLUMN) != 0;
    }

    private Settings(JSONObject json) throws JSONException {
        signature = (String) json.optString(UIProvider.AccountColumns.SettingsColumns.SIGNATURE);

        autoAdvance = json.optInt(UIProvider.AccountColumns.SettingsColumns.AUTO_ADVANCE);
        messageTextSize = json.optInt(UIProvider.AccountColumns.SettingsColumns.MESSAGE_TEXT_SIZE);
        snapHeaders = json.optInt(UIProvider.AccountColumns.SettingsColumns.SNAP_HEADERS);
        replyBehavior = json.optInt(UIProvider.AccountColumns.SettingsColumns.REPLY_BEHAVIOR);
        hideCheckboxes = json.optBoolean(UIProvider.AccountColumns.SettingsColumns.HIDE_CHECKBOXES);
        confirmDelete = json.optBoolean(UIProvider.AccountColumns.SettingsColumns.CONFIRM_DELETE);
        confirmArchive = json.optBoolean(UIProvider.AccountColumns.SettingsColumns.CONFIRM_ARCHIVE);
        confirmSend = json.optBoolean(UIProvider.AccountColumns.SettingsColumns.CONFIRM_SEND);
        defaultInbox = getValidUri(
                json.optString(UIProvider.AccountColumns.SettingsColumns.DEFAULT_INBOX));
        forceReplyFromDefault =
                json.optBoolean(UIProvider.AccountColumns.SettingsColumns.FORCE_REPLY_FROM_DEFAULT);
    }

    /**
     * Parse a string (possibly null or empty) into a URI. If the string is null or empty, null
     * is returned back. Otherwise an empty URI is returned.
     * @param uri
     * @return a valid URI, possibly {@link android.net.Uri#EMPTY}
     */
    private static Uri getValidUri(String uri) {
        return Utils.getValidUri(uri);
    }

    /**
     * Return a serialized String for these settings.
     */
    public synchronized String serialize() {
        final JSONObject json = toJSON();
        return json.toString();
    }

    /**
     * Return a JSONObject for these settings.
     */
    public synchronized JSONObject toJSON() {
        final JSONObject json = new JSONObject();
        try {
            json.put(UIProvider.AccountColumns.SettingsColumns.SIGNATURE, signature);
            json.put(UIProvider.AccountColumns.SettingsColumns.AUTO_ADVANCE, autoAdvance);
            json.put(UIProvider.AccountColumns.SettingsColumns.MESSAGE_TEXT_SIZE, messageTextSize);
            json.put(UIProvider.AccountColumns.SettingsColumns.SNAP_HEADERS, snapHeaders);
            json.put(UIProvider.AccountColumns.SettingsColumns.REPLY_BEHAVIOR, replyBehavior);
            json.put(UIProvider.AccountColumns.SettingsColumns.HIDE_CHECKBOXES, hideCheckboxes);
            json.put(UIProvider.AccountColumns.SettingsColumns.CONFIRM_DELETE, confirmDelete);
            json.put(UIProvider.AccountColumns.SettingsColumns.CONFIRM_ARCHIVE, confirmArchive);
            json.put(UIProvider.AccountColumns.SettingsColumns.CONFIRM_SEND, confirmSend);
            json.put(UIProvider.AccountColumns.SettingsColumns.DEFAULT_INBOX, defaultInbox);
            json.put(UIProvider.AccountColumns.SettingsColumns.FORCE_REPLY_FROM_DEFAULT,
                    forceReplyFromDefault);
        } catch (JSONException e) {
            LogUtils.wtf(LOG_TAG, e, "Could not serialize settings");
        }
        return json;
    }

    /**
     * Create a new instance of an Settings object using a serialized instance created previously
     * using {@link #serialize()}. This returns null if the serialized instance was invalid or does
     * not represent a valid account object.
     *
     * @param serializedAccount
     * @return
     */
    public static Settings newInstance(String serializedSettings) {
        JSONObject json = null;
        try {
            json = new JSONObject(serializedSettings);
            return new Settings(json);
        } catch (JSONException e) {
            LogUtils.wtf(LOG_TAG, e, "Could not create an settings from this input: \"%s\"",
                    serializedSettings);
            return null;
        }
    }


    /**
     * Create a new instance of an Settings object using a JSONObject  instance created previously
     * using {@link #toJSON(). This returns null if the serialized instance was invalid or does
     * not represent a valid account object.
     *
     * @param serializedAccount
     * @return
     */
    public static Settings newInstance(JSONObject json) {
        if (json == null) {
            return null;
        }
        try {
            return new Settings(json);
        } catch (JSONException e) {
            LogUtils.wtf(LOG_TAG, e, "Could not create an settings from this input: \"%s\"",
                    json.toString());
            return null;
        }
    }



    /**
     * Integer column contaning the user's specified snap header preference.  This value
     * will be one of the values in {@link UIProvider.SnapHeaderValue}
     */
    public static final String SNAP_HEADERS = "snap_headers";

    /**
     * Integer column containing the user's specified default reply behavior.  This value will
     * be one of the values in {@link UIProvider.DefaultReplyBehavior}
     */
    public static final String REPLY_BEHAVIOR = "reply_behavior";

    /**
     * Integer column containing the user's specified checkbox preference. A
     * non zero value means to hide checkboxes.
     */
    public static final String HIDE_CHECKBOXES = "hide_checkboxes";

    /**
     * Integer column containing the user's specified confirm delete preference value.
     * A non zero value indicates that the user has indicated that a confirmation should
     * be shown when a delete action is performed.
     */
    public static final String CONFIRM_DELETE = "confirm_delete";

    /**
     * Integer column containing the user's specified confirm archive preference value.
     * A non zero value indicates that the user has indicated that a confirmation should
     * be shown when an archive action is performed.
     */
    public static final String CONFIRM_ARCHIVE = "confirm_archive";

    /**
     * Integer column containing the user's specified confirm send preference value.
     * A non zero value indicates that the user has indicated that a confirmation should
     * be shown when a send action is performed.
     */
    public static final String CONFIRM_SEND = "confirm_send";
    /**
     * String folder containing the serialized default inbox folder for an account.
     */
    public static final String DEFAULT_INBOX = "default_inbox";
    /**
     * Integer column containing a non zero value if replies should always be sent from
     * a default address instead of a recipient.
     */
    public static String FORCE_REPLY_FROM_DEFAULT = "force_reply_from_default";


    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(signature);
        dest.writeInt(autoAdvance);
        dest.writeInt(messageTextSize);
        dest.writeInt(snapHeaders);
        dest.writeInt(replyBehavior);
        dest.writeInt(hideCheckboxes ? 1 : 0);
        dest.writeInt(confirmDelete ? 1 : 0);
        dest.writeInt(confirmArchive? 1 : 0);
        dest.writeInt(confirmSend? 1 : 0);
        dest.writeString(defaultInbox.toString());
        dest.writeInt(forceReplyFromDefault ? 1 : 0);
    }

    @SuppressWarnings("hiding")
    public static final Creator<Settings> CREATOR = new Creator<Settings>() {
        @Override
        public Settings createFromParcel(Parcel source) {
            return new Settings(source);
        }

        @Override
        public Settings[] newArray(int size) {
            return new Settings[size];
        }
    };
}
