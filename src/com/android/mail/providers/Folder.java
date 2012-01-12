/*******************************************************************************
 *      Copyright (C) 2012 Google Inc.
 *      Licensed to The Android Open Source Project.
 *
 *      Licensed under the Apache License, Version 2.0 (the "License");
 *      you may not use this file except in compliance with the License.
 *      You may obtain a copy of the License at
 *
 *           http://www.apache.org/licenses/LICENSE-2.0
 *
 *      Unless required by applicable law or agreed to in writing, software
 *      distributed under the License is distributed on an "AS IS" BASIS,
 *      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *      See the License for the specific language governing permissions and
 *      limitations under the License.
 *******************************************************************************/

package com.android.mail.providers;

import android.database.Cursor;
import android.os.Parcel;
import android.os.Parcelable;
import com.android.mail.utils.LogUtils;

/**
 * A folder is a collection of conversations, and perhaps other folders.
 */
public class Folder implements Parcelable {
    // Try to match the order of members with the order of constants in UIProvider.

    /**
     * The content provider URI that returns this folder for this account.
     */
    public String uri;

    /**
     * The human visible name for this folder.
     */
    public String name;

    /**
     * The possible capabilities that this folder supports.
     */
    public int capabilities;

    /**
     * Whether or not this folder has children folders.
     */
    public boolean hasChildren;

    /**
     * How often this folder should be synchronized with the server.
     */
    public int syncFrequency;

    /**
     * How large the synchronization window is: how many days worth of data is retained on the
     * device.
     */
    public int syncWindow;

    /**
     * The content provider URI to return the list of conversations in this
     * folder.
     */
    public String conversationListUri;

    /**
     * The content provider URI to return the list of child folders of this folder.
     */
    public String childFoldersListUri;

    /**
     * The number of messages that are unread in this folder.
     */
    public int unreadCount;

    /**
     * The total number of messages in this folder.
     */
    public int totalCount;

    /**
     * Used only for debugging.
     */
    private static final String LOG_TAG = new LogUtils().getLogTag();

    public Folder(Parcel in) {
        uri = in.readString();
        name = in.readString();
        capabilities = in.readInt();
        // 1 for true, 0 for false.
        hasChildren = in.readInt() == 1;
        syncFrequency = in.readInt();
        syncWindow = in.readInt();
        conversationListUri = in.readString();
        childFoldersListUri = in.readString();
        unreadCount = in.readInt();
        totalCount = in.readInt();
    }

    public Folder(Cursor cursor) {
        uri = cursor.getString(UIProvider.FOLDER_URI_COLUMN);
        name = cursor.getString(UIProvider.FOLDER_NAME_COLUMN);
        capabilities = cursor.getInt(UIProvider.FOLDER_CAPABILITIES_COLUMN);
        // 1 for true, 0 for false.
        hasChildren = cursor.getInt(UIProvider.FOLDER_HAS_CHILDREN_COLUMN) == 1;
        syncFrequency = cursor.getInt(UIProvider.FOLDER_SYNC_FREQUENCY_COLUMN);
        syncWindow = cursor.getInt(UIProvider.FOLDER_SYNC_WINDOW_COLUMN);
        conversationListUri = cursor.getString(UIProvider.FOLDER_CONVERSATION_LIST_URI_COLUMN);
        childFoldersListUri = cursor.getString(UIProvider.FOLDER_CHILD_FOLDERS_LIST_COLUMN);
        unreadCount = cursor.getInt(UIProvider.FOLDER_UNREAD_COUNT_COLUMN);
        totalCount = cursor.getInt(UIProvider.FOLDER_TOTAL_COUNT_COLUMN);
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(uri);
        dest.writeString(name);
        dest.writeInt(capabilities);
        // 1 for true, 0 for false.
        dest.writeInt(hasChildren ? 1 : 0);
        dest.writeInt(syncFrequency);
        dest.writeInt(syncWindow);
        dest.writeString(conversationListUri);
        dest.writeString(childFoldersListUri);
        dest.writeInt(unreadCount);
        dest.writeInt(totalCount);
    }

    @SuppressWarnings("hiding")
    public static final Creator<Folder> CREATOR = new Creator<Folder>() {
        @Override
        public Folder createFromParcel(Parcel source) {
            return new Folder(source);
        }

        @Override
        public Folder[] newArray(int size) {
            return new Folder[size];
        }
    };

    @Override
    public int describeContents() {
        // Return a sort of version number for this parcelable folder. Starting with zero.
        return 0;
    }
}
