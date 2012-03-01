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
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

/**
 * Model to hold Settings for an account.
 */
public class Settings implements Parcelable {
    public String signature;
    public int autoAdvance;
    public int messageTextSize;
    public int snapHeaders;
    public int replyBehavior;
    public boolean hideCheckboxes;
    public boolean confirmDelete;
    public boolean confirmArchive;
    public boolean confirmSend;
    public Uri defaultInbox;

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
    }

    public Settings(Cursor cursor) {
        signature = cursor.getString(UIProvider.SETTINGS_SIGNATURE_COLUMN);
        autoAdvance = cursor.getInt(UIProvider.SETTINGS_AUTO_ADVANCE_COLUMN);
        messageTextSize = cursor.getInt(UIProvider.SETTINGS_MESSAGE_TEXT_SIZE_COLUMN);
        snapHeaders = cursor.getInt(UIProvider.SETTINGS_SNAP_HEADERS_COLUMN);
        replyBehavior = cursor.getInt(UIProvider.SETTINGS_REPLY_BEHAVIOR_COLUMN);
        hideCheckboxes = cursor.getInt(UIProvider.SETTINGS_HIDE_CHECKBOXES_COLUMN) != 0;
        confirmDelete = cursor.getInt(UIProvider.SETTINGS_CONFIRM_DELETE_COLUMN) != 0;
        confirmArchive = cursor.getInt(UIProvider.SETTINGS_CONFIRM_ARCHIVE_COLUMN) != 0;
        confirmSend = cursor.getInt(UIProvider.SETTINGS_CONFIRM_SEND_COLUMN) != 0;
        final String inbox = cursor.getString(UIProvider.SETTINGS_DEFAULT_INBOX_COLUMN);
        defaultInbox = !TextUtils.isEmpty(inbox) ? Uri.parse(inbox) : null;
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
