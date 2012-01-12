/*******************************************************************************
 *      Copyright (C) 2011 Google Inc.
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

package com.android.mail;

import com.android.mail.providers.Folder;
import com.android.mail.providers.UIProvider;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableMap;

import android.content.UriMatcher;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.Collections;
import java.util.Map;

/**
 * Helper class that holds the information specifying a conversation or a message (message id,
 * conversation id, max message id and labels). This class is used to move conversation data between
 * the various activities.
 */
public class ConversationInfo implements Parcelable {
    private final long mConversationId;
    private final long mLocalMessageId;  // can be 0
    private final long mServerMessageId;  // can be 0
    private long mMaxMessageId;

    // This defines an invalid conversation ID. Nobody should rely on its specific value.
    private static final long INVALID_CONVERSATION_ID = -1;

    /**
     * Mapping from name of folder to the Folder object. This is the list of all the folders that
     * this conversation belongs to.
     */
    private final Map<String, Folder> mFolders;

    // TODO(viki) Get rid of this, and all references and side-effects should be changed
    // to something sane: like a boolean value indicating correctness.
    static final ConversationInfo INVALID_CONVERSATION_INFO =
            new ConversationInfo(INVALID_CONVERSATION_ID);

    /**
     * A matcher for data URI's that specify a conversation.
     */
    static final UriMatcher sUrlMatcher = new UriMatcher(UriMatcher.NO_MATCH);
    static {
        sUrlMatcher.addURI(UIProvider.AUTHORITY,
                "account/*/label/*/conversationId/*/maxServerMessageId/*/labels/*", 0);
    }

    public ConversationInfo(long conversationId, long localMessageId, long serverMessageId,
            long maxMessageId, Map<String, Folder> folders) {
        mConversationId = conversationId;
        mLocalMessageId = localMessageId;
        mServerMessageId = serverMessageId;
        mMaxMessageId = maxMessageId;
        mFolders = folders;
    }

    public ConversationInfo(long conversationId, long maxMessageId, Map<String, Folder> folders) {
        this(conversationId, 0, 0, maxMessageId, folders);
    }

    private ConversationInfo(long conversationId) {
        mConversationId = conversationId;
        mServerMessageId = 0;
        mLocalMessageId = 0;
        mMaxMessageId = 0;
        mFolders = Collections.emptyMap();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(mConversationId);
        dest.writeLong(mLocalMessageId);
        dest.writeLong(mServerMessageId);
        dest.writeLong(mMaxMessageId);
        synchronized (this){
            dest.writeString(Folder.serialize(mFolders));
        }
    }

    /**
     * Held together with hope and dreams. Write tests to verify this behavior.
     */
    public static final Parcelable.Creator<ConversationInfo> CREATOR =
            new Parcelable.Creator<ConversationInfo>() {
        @Override
        public ConversationInfo createFromParcel(Parcel source) {
            long conversationId = source.readLong();
            long localMessageId = source.readLong();
            long serverMessageId = source.readLong();
            long maxMessageId = source.readLong();
            return new ConversationInfo(
                    conversationId,
                    localMessageId,
                    serverMessageId,
                    maxMessageId,
                    Folder.parseFoldersFromString(source.readString()));
        }

        @Override
        public ConversationInfo[] newArray(int size) {
            return new ConversationInfo[size];
        }
    };

    public long getConversationId() {
        return mConversationId;
    }

    public long getLocalMessageId() {
        return mLocalMessageId;
    }

    public long getServerMessageId() {
        return mServerMessageId;
    }

    public long getMaxMessageId() {
        return mMaxMessageId;
    }

    @Override
    public boolean equals(Object o) {
        synchronized(this) {
            if (o == this) {
                return true;
            }

            if ((o == null) || (o.getClass() != this.getClass())) {
                return false;
            }

            // TODO(viki): Confirm that keySet() is the correct thing to use here. Two folders
            // with the same keys should be equal, irrespective of order.
            ConversationInfo other = (ConversationInfo) o;
            return mConversationId == other.mConversationId
                    && mLocalMessageId == other.mLocalMessageId
                    && mServerMessageId == other.mServerMessageId
                    && mMaxMessageId == other.mMaxMessageId
                    && mFolders.keySet().equals(other.mFolders.keySet());
        }
    }

    @Override
    public int hashCode() {
        synchronized(this) {
            return Objects.hashCode(mConversationId, mLocalMessageId, mServerMessageId,
                    mMaxMessageId, mFolders.keySet());
        }
    }

    /**
     * Updates the max server message ID of the conversation, when new messages have arrived.
     */
    public void updateMaxMessageId(long maxMessageId) {
        mMaxMessageId = maxMessageId;
    }

    /**
     * @return empty Map if the folders are null, nonempty copy of Folders otherwise
     */
    public Map<String, Folder> getFolders() {
        // If we have an empty folder map, return an empty folder map rather than returning null.
        if (mFolders == null){
            return Collections.emptyMap();
        }
        return ImmutableMap.copyOf(mFolders);
    }

    /**
     * Update a conversation info to add this folder to the update.
     * @param folders
     * @param added
     */
    public void updateFolder(Folder folders, boolean added) {
        if (added){
            mFolders.put(folders.name, folders);
        } else {
            mFolders.remove(folders.name);
        }
    }
}
