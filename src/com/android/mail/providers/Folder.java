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
import android.os.Parcelable.Creator;
import android.text.TextUtils;

import com.android.mail.utils.LogUtils;
import com.google.common.collect.Maps;

import java.util.Collection;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * A folder is a collection of conversations, and perhaps other folders.
 */
public class Folder implements Parcelable {
    /**
     *
     */
    private static final String FOLDER_UNINITIALIZED = "Uninitialized!";

    // Try to match the order of members with the order of constants in UIProvider.

    /**
     * Unique id of this folder.
     */
    public String id;

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
     * The content provider URI to force a refresh of this folder.
     */
    public String refreshUri;

    /**
     * The current sync status of the folder
     */
    public int syncStatus;

    /**
     * The result of the last sync for this folder
     */
    public int lastSyncResult;

    /**
     * Total number of members that comprise an instance of a folder. Count up the members above.
     * This is the number of members that need to be serialized or parceled.
     */
    private static final int NUMBER_MEMBERS = 14;

    /**
     * Used only for debugging.
     */
    private static final String LOG_TAG = new LogUtils().getLogTag();

    /**
     * Examples of expected format for the joined label strings
     *
     * Example of a joined label string:
     *       630107622^*^^i^*^^i^*^0
     *       <id>^*^<canonical name>^*^<name>^*^<color index>
     *
     * The sqlite queries will return a list of labels strings separated with "^**^"
     * Example of a query result:
     *     630107622^*^^i^*^^i^*^0^**^630107626^*^^u^*^^u^*^0^**^630107627^*^^f^*^^f^*^0
     */
    private static final String LABEL_COMPONENT_SEPARATOR = "^*^";
    private static final Pattern LABEL_COMPONENT_SEPARATOR_PATTERN =
            Pattern.compile("\\^\\*\\^");

    private static final String LABEL_SEPARATOR = "^**^";
    private static final Pattern LABEL_SEPARATOR_PATTERN = Pattern.compile("\\^\\*\\*\\^");

    public Folder(Parcel in) {
        id = in.readString();
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
        refreshUri = in.readString();
        syncStatus = in.readInt();
        lastSyncResult = in.readInt();
     }

    public Folder(Cursor cursor) {
        id = cursor.getString(UIProvider.FOLDER_ID_COLUMN);
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
        refreshUri = cursor.getString(UIProvider.FOLDER_REFRESH_URI_COLUMN);
        syncStatus = cursor.getInt(UIProvider.FOLDER_SYNC_STATUS_COLUMN);
        lastSyncResult = cursor.getInt(UIProvider.FOLDER_LAST_SYNC_RESULT_COLUMN);
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(id);
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
        dest.writeString(refreshUri);
        dest.writeInt(syncStatus);
        dest.writeInt(lastSyncResult);
    }

    /**
     * Return a serialized String for this folder.
     */
    public synchronized String serialize(){
        StringBuilder out = new StringBuilder();
        out.append(id).append(LABEL_COMPONENT_SEPARATOR);
        out.append(uri).append(LABEL_COMPONENT_SEPARATOR);
        out.append(name).append(LABEL_COMPONENT_SEPARATOR);
        out.append(capabilities).append(LABEL_COMPONENT_SEPARATOR);
        out.append(hasChildren ? "1": "0").append(LABEL_COMPONENT_SEPARATOR);
        out.append(syncFrequency).append(LABEL_COMPONENT_SEPARATOR);
        out.append(syncWindow).append(LABEL_COMPONENT_SEPARATOR);
        out.append(conversationListUri).append(LABEL_COMPONENT_SEPARATOR);
        out.append(childFoldersListUri).append(LABEL_COMPONENT_SEPARATOR);
        out.append(unreadCount).append(LABEL_COMPONENT_SEPARATOR);
        out.append(totalCount).append(LABEL_COMPONENT_SEPARATOR);
        out.append(refreshUri).append(LABEL_COMPONENT_SEPARATOR);
        out.append(syncStatus).append(LABEL_COMPONENT_SEPARATOR);
        out.append(lastSyncResult).append(LABEL_COMPONENT_SEPARATOR);
        return out.toString();
    }

    /**
     * Construct a new Folder instance from a previously serialized string.
     * @param serializedFolder string obtained from {@link #serialize()} on a valid folder.
     */
    private Folder(String serializedFolder){
        String[] folderMembers = TextUtils.split(serializedFolder, LABEL_SEPARATOR_PATTERN);
        if (folderMembers.length != NUMBER_MEMBERS) {
            // This is a problem.
            // TODO(viki): Find out the appropriate exception for this.
            return;
        }
        uri = folderMembers[0];
        name = folderMembers[1];
        capabilities = Integer.valueOf(folderMembers[2]);
        // 1 for true, 0 for false
        hasChildren = folderMembers[3] == "1";
        syncFrequency = Integer.valueOf(folderMembers[4]);
        syncWindow = Integer.valueOf(folderMembers[5]);
        conversationListUri = folderMembers[6];
        childFoldersListUri = folderMembers[7];
        unreadCount = Integer.valueOf(folderMembers[8]);
        totalCount = Integer.valueOf(folderMembers[9]);
        refreshUri = folderMembers[10];
        syncStatus = Integer.valueOf(folderMembers[11]);
        lastSyncResult = Integer.valueOf(folderMembers[12]);
    }

    /**
     * Constructor that leaves everything uninitialized. For use only by {@link #serialize()}
     * which is responsible for filling in all the fields
     */
    public Folder() {
        name = FOLDER_UNINITIALIZED;
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

    /**
     * Create a Folder map from a string of serialized folders. This can only be done on the output
     * of {@link #serialize(Map)}.
     * @param serializedFolder A string obtained from {@link #serialize(Map)}
     * @return a Map of folder name to folder.
     */
    public static Map<String, Folder> parseFoldersFromString(String serializedFolder) {
        LogUtils.d(LOG_TAG, "label query result: %s", serializedFolder);

        Map<String, Folder> folderMap = Maps.newHashMap();
        if (serializedFolder == null || serializedFolder == "") {
            return folderMap;
        }
        String[] folderPieces = TextUtils.split(
                serializedFolder, LABEL_COMPONENT_SEPARATOR_PATTERN);
        for (int i = 0, n = folderPieces.length; i < n; i++) {
            Folder folder = new Folder(folderPieces[i]);
            if (folder.name != FOLDER_UNINITIALIZED) {
                folderMap.put(folder.name, folder);
            }
        }
        return folderMap;
    }

    /**
     * Serialize the given list of folders
     * @param folderMap A valid map of folder names to Folders
     * @return a string containing a serialized output of folder maps.
     */
    public static String serialize(Map<String, Folder> folderMap) {
        Collection<Folder> folderCollection = folderMap.values();
        Folder[] folderList = folderCollection.toArray(new Folder[]{} );
        int numLabels = folderList.length;
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < numLabels; i++) {
          if (i > 0) {
              result.append(LABEL_SEPARATOR);
          }
          Folder folder = folderList[i];
          result.append(folder.serialize());
        }
        return result.toString();
    }
}
