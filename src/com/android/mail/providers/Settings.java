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

import com.google.common.base.Objects;

import com.android.mail.providers.UIProvider.AccountColumns;
import com.android.mail.providers.UIProvider.AutoAdvance;
import com.android.mail.providers.UIProvider.DefaultReplyBehavior;
import com.android.mail.providers.UIProvider.MessageTextSize;
import com.android.mail.providers.UIProvider.SnapHeaderValue;
import com.android.mail.providers.UIProvider.Swipe;
import com.android.mail.utils.LogTag;
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
    private static final String LOG_TAG = LogTag.getLogTag();

    static final Settings EMPTY_SETTINGS = new Settings();

    // Max size for attachments (5 megs). Will be overridden by an account
    // setting, if found.
    private static final int DEFAULT_MAX_ATTACHMENT_SIZE = 5 * 1024 * 1024;

    public static final int SWIPE_SETTING_ARCHIVE = 0;
    public static final int SWIPE_SETTING_DELETE = 1;
    public static final int SWIPE_SETTING_DISABLED = 2;

    private static final int DEFAULT = SWIPE_SETTING_ARCHIVE;

    public final String signature;
    /**
     * Auto advance setting for this account.
     * Integer, one of {@link AutoAdvance#LIST}, {@link AutoAdvance#NEWER},
     * {@link AutoAdvance#OLDER} or  {@link AutoAdvance#UNSET}
     */
    public final int autoAdvance;
    public final int messageTextSize;
    public final int snapHeaders;
    public final int replyBehavior;
    public final boolean hideCheckboxes;
    public final boolean confirmDelete;
    public final boolean confirmArchive;
    public final boolean confirmSend;
    public final Uri defaultInbox;
    /**
     * The name of the default inbox: "Inbox" or "Priority Inbox", internationalized...
     */
    public final String defaultInboxName;
    // If you find the need for more default Inbox information: ID or capabilities, then
    // ask viki to replace the above two members with a single JSON object representing the default
    // folder.  That should make all the information about the folder available without an
    // explosion in the number of members.

    public final boolean forceReplyFromDefault;
    public final int maxAttachmentSize;
    public final int swipe;
    /** True if arrows on the priority inbox are enabled. */
    public final boolean priorityArrowsEnabled;

    /** Cached value of hashCode */
    private int mHashCode;

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
        defaultInbox = Uri.EMPTY;
        defaultInboxName = "";
        forceReplyFromDefault = false;
        maxAttachmentSize = 0;
        swipe = DEFAULT;
        priorityArrowsEnabled = false;
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
        defaultInbox = Utils.getValidUri(inParcel.readString());
        defaultInboxName = inParcel.readString();
        forceReplyFromDefault = inParcel.readInt() != 0;
        maxAttachmentSize = inParcel.readInt();
        swipe = inParcel.readInt();
        priorityArrowsEnabled = inParcel.readInt() != 0;
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
        defaultInbox = Utils.getValidUri(
                cursor.getString(UIProvider.ACCOUNT_SETTINGS_DEFAULT_INBOX_COLUMN));
        defaultInboxName = cursor.getString(UIProvider.ACCOUNT_SETTINGS_DEFAULT_INBOX_NAME_COLUMN);
        forceReplyFromDefault = cursor.getInt(
                UIProvider.ACCOUNT_SETTINGS_FORCE_REPLY_FROM_DEFAULT_COLUMN) != 0;
        maxAttachmentSize = cursor.getInt(UIProvider.ACCOUNT_SETTINGS_MAX_ATTACHMENT_SIZE_COLUMN);
        swipe = cursor.getInt(UIProvider.ACCOUNT_SETTINGS_SWIPE_COLUMN);
        priorityArrowsEnabled =
                cursor.getInt(UIProvider.ACCOUNT_SETTINGS_PRIORITY_ARROWS_ENABLED_COLUMN) != 0;
    }

    private Settings(JSONObject json) throws JSONException {
        signature = json.optString(AccountColumns.SettingsColumns.SIGNATURE);

        autoAdvance = json.optInt(AccountColumns.SettingsColumns.AUTO_ADVANCE);
        messageTextSize = json.optInt(AccountColumns.SettingsColumns.MESSAGE_TEXT_SIZE);
        snapHeaders = json.optInt(AccountColumns.SettingsColumns.SNAP_HEADERS);
        replyBehavior = json.optInt(AccountColumns.SettingsColumns.REPLY_BEHAVIOR);
        hideCheckboxes = json.optBoolean(AccountColumns.SettingsColumns.HIDE_CHECKBOXES);
        confirmDelete = json.optBoolean(AccountColumns.SettingsColumns.CONFIRM_DELETE);
        confirmArchive = json.optBoolean(AccountColumns.SettingsColumns.CONFIRM_ARCHIVE);
        confirmSend = json.optBoolean(AccountColumns.SettingsColumns.CONFIRM_SEND);
        defaultInbox = Utils.getValidUri(
                json.optString(AccountColumns.SettingsColumns.DEFAULT_INBOX));
        defaultInboxName = json.optString(AccountColumns.SettingsColumns.DEFAULT_INBOX_NAME);
        forceReplyFromDefault =
                json.optBoolean(AccountColumns.SettingsColumns.FORCE_REPLY_FROM_DEFAULT);
        maxAttachmentSize = json.getInt(AccountColumns.SettingsColumns.MAX_ATTACHMENT_SIZE);
        swipe = json.optInt(AccountColumns.SettingsColumns.SWIPE);
        priorityArrowsEnabled =
                json.optBoolean(AccountColumns.SettingsColumns.PRIORITY_ARROWS_ENABLED);
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
            json.put(AccountColumns.SettingsColumns.SIGNATURE, signature);
            json.put(AccountColumns.SettingsColumns.AUTO_ADVANCE, autoAdvance);
            json.put(AccountColumns.SettingsColumns.MESSAGE_TEXT_SIZE, messageTextSize);
            json.put(AccountColumns.SettingsColumns.SNAP_HEADERS, snapHeaders);
            json.put(AccountColumns.SettingsColumns.REPLY_BEHAVIOR, replyBehavior);
            json.put(AccountColumns.SettingsColumns.HIDE_CHECKBOXES, hideCheckboxes);
            json.put(AccountColumns.SettingsColumns.CONFIRM_DELETE, confirmDelete);
            json.put(AccountColumns.SettingsColumns.CONFIRM_ARCHIVE, confirmArchive);
            json.put(AccountColumns.SettingsColumns.CONFIRM_SEND, confirmSend);
            json.put(AccountColumns.SettingsColumns.DEFAULT_INBOX, defaultInbox);
            json.put(AccountColumns.SettingsColumns.DEFAULT_INBOX_NAME, defaultInboxName);
            json.put(AccountColumns.SettingsColumns.FORCE_REPLY_FROM_DEFAULT,
                    forceReplyFromDefault);
            json.put(AccountColumns.SettingsColumns.MAX_ATTACHMENT_SIZE,
                    maxAttachmentSize);
            json.put(AccountColumns.SettingsColumns.SWIPE, swipe);
            json.put(AccountColumns.SettingsColumns.PRIORITY_ARROWS_ENABLED, priorityArrowsEnabled);
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
            LogUtils.e(LOG_TAG, e, "Could not create an settings from this input: \"%s\"",
                    serializedSettings);
            return null;
        }
    }


    /**
     * Create a new instance of an Settings object using a JSONObject  instance created previously
     * using {@link #toJSON()}. This returns null if the serialized instance was invalid or does
     * not represent a valid account object.
     *
     * @param json
     * @return
     */
    public static Settings newInstance(JSONObject json) {
        if (json == null) {
            return null;
        }
        try {
            return new Settings(json);
        } catch (JSONException e) {
            LogUtils.e(LOG_TAG, e, "Could not create an settings from this input: \"%s\"",
                    json.toString());
            return null;
        }
    }

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
        dest.writeString(defaultInboxName);
        dest.writeInt(forceReplyFromDefault ? 1 : 0);
        dest.writeInt(maxAttachmentSize);
        dest.writeInt(swipe);
        dest.writeInt(priorityArrowsEnabled ? 1 : 0);
    }

    /**
     * Returns the URI of the current account's default inbox if available, otherwise
     * returns the empty URI {@link Uri#EMPTY}
     * @param settings a settings object, possibly null.
     * @return a valid default Inbox URI, or {@link Uri#EMPTY} if settings are null or no default
     * is specified.
     */
    public static Uri getDefaultInboxUri(Settings settings) {
        if (settings != null && settings.defaultInbox != null) {
            return settings.defaultInbox;
        }
        return Uri.EMPTY;
    }

    /**
     * Return the auto advance setting for the settings provided. It is safe to pass this method
     * a null object. It always returns a valid {@link AutoAdvance} setting.
     * @return the auto advance setting, a constant from {@link AutoAdvance}
     */
    public static int getAutoAdvanceSetting(Settings settings) {
        // TODO(mindyp): if this isn't set, then show the dialog telling the user to set it.
        // Remove defaulting to AutoAdvance.LIST.
        final int autoAdvance = (settings != null) ?
                (settings.autoAdvance == AutoAdvance.UNSET ?
                        AutoAdvance.LIST : settings.autoAdvance)
                : AutoAdvance.LIST;
        return autoAdvance;
    }

    /**
     * Return the swipe setting for the settings provided. It is safe to pass this method
     * a null object. It always returns a valid {@link Swipe} setting.
     * @return the auto advance setting, a constant from {@link Swipe}
     */
    public static int getSwipeSetting(Settings settings) {
        return settings != null ? settings.swipe : Swipe.DEFAULT;
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

    /**
     *  Get the maximum size in KB for attachments.
     */
    public int getMaxAttachmentSize() {
        return maxAttachmentSize <= 0 ? DEFAULT_MAX_ATTACHMENT_SIZE : maxAttachmentSize;
    }

    @Override
    public boolean equals(final Object aThat) {
        LogUtils.d(LOG_TAG, "Settings.equals(%s)", aThat);
        if (this == aThat) {
            return true;
        }
        if ((aThat == null) || (aThat.getClass() != this.getClass())) {
            return false;
        }
        final Settings that = (Settings) aThat;
        // If both signatures are null or empty, we want to treat them as equals
        return (((TextUtils.isEmpty(signature) && TextUtils.isEmpty(that.signature)) ||
                    TextUtils.equals(signature, that.signature))
                && autoAdvance == that.autoAdvance
                && messageTextSize == that.messageTextSize
                && replyBehavior == that.replyBehavior
                && hideCheckboxes == that.hideCheckboxes
                && confirmDelete == that.confirmDelete
                && confirmArchive == that.confirmArchive
                && confirmSend == that.confirmSend
                && Objects.equal(defaultInbox, that.defaultInbox)
                // Not checking default Inbox name, since is is identical to the URI check above.
                && forceReplyFromDefault == that.forceReplyFromDefault
                && maxAttachmentSize == that.maxAttachmentSize
                && swipe == that.swipe
                && priorityArrowsEnabled == that.priorityArrowsEnabled);
    }

    @Override
    public int hashCode() {
        if (mHashCode == 0) {
            mHashCode = calculateHashCode();
        }
        return mHashCode;
    }

    /**
     * Returns the hash code for this object.
     * @return
     */
    private final int calculateHashCode() {
        return super.hashCode()
                ^ Objects.hashCode(signature, autoAdvance, messageTextSize, replyBehavior,
                        hideCheckboxes, confirmDelete, confirmArchive, confirmSend,
                        defaultInbox, forceReplyFromDefault, maxAttachmentSize, swipe,
                        priorityArrowsEnabled);
    }
}
