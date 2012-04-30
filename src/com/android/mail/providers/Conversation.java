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

public class Conversation implements Parcelable {
    public static final int NO_POSITION = -1;

    public long id;
    public Uri uri;
    public String subject;
    public long dateMs;
    public String snippet;
    public boolean hasAttachments;
    public Uri messageListUri;
    public String senders;
    public int numMessages;
    public int numDrafts;
    public int sendingState;
    public int priority;
    public boolean read;
    public boolean starred;
    public String folderList;
    public String rawFolders;
    public int convFlags;
    public int personalLevel;
    public boolean spam;
    public boolean muted;
    public int color;

    // Used within the UI to indicate the adapter position of this conversation
    public transient int position;
    // Used within the UI to indicate that a Conversation should be removed from the
    // ConversationCursor when executing an update, e.g. the the Conversation is no longer
    // in the ConversationList for the current folder, that is it's now in some other folder(s)
    public transient boolean localDeleteOnUpdate;

    // Constituents of convFlags below
    // Flag indicating that the item has been deleted, but will continue being shown in the list
    // Delete/Archive of a mostly-dead item will NOT propagate the delete/archive, but WILL remove
    // the item from the cursor
    public static final int FLAG_MOSTLY_DEAD = 1 << 0;

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(id);
        dest.writeParcelable(uri, flags);
        dest.writeString(subject);
        dest.writeLong(dateMs);
        dest.writeString(snippet);
        dest.writeByte(hasAttachments ? (byte) 1 : 0);
        dest.writeParcelable(messageListUri, 0);
        dest.writeString(senders);
        dest.writeInt(numMessages);
        dest.writeInt(numDrafts);
        dest.writeInt(sendingState);
        dest.writeInt(priority);
        dest.writeByte(read ? (byte) 1 : 0);
        dest.writeByte(starred ? (byte) 1 : 0);
        dest.writeString(folderList);
        dest.writeString(rawFolders);
        dest.writeInt(convFlags);
        dest.writeInt(personalLevel);
        dest.writeInt(spam ? 1 : 0);
        dest.writeInt(muted ? 1 : 0);
        dest.writeInt(color);
    }

    private Conversation(Parcel in) {
        id = in.readLong();
        uri = in.readParcelable(null);
        subject = in.readString();
        dateMs = in.readLong();
        snippet = in.readString();
        hasAttachments = (in.readByte() != 0);
        messageListUri = in.readParcelable(null);
        senders = in.readString();
        numMessages = in.readInt();
        numDrafts = in.readInt();
        sendingState = in.readInt();
        priority = in.readInt();
        read = (in.readByte() != 0);
        starred = (in.readByte() != 0);
        folderList = in.readString();
        rawFolders = in.readString();
        convFlags = in.readInt();
        personalLevel = in.readInt();
        spam = in.readInt() != 0;
        muted = in.readInt() != 0;
        color = in.readInt();
        position = NO_POSITION;
        localDeleteOnUpdate = false;
    }

    @Override
    public String toString() {
        return "[conversation id=" + id + "]";
    }

    public static final Creator<Conversation> CREATOR = new Creator<Conversation>() {

        @Override
        public Conversation createFromParcel(Parcel source) {
            return new Conversation(source);
        }

        @Override
        public Conversation[] newArray(int size) {
            return new Conversation[size];
        }

    };

    public static final Uri MOVE_CONVERSATIONS_URI = Uri.parse("content://moveconversations");

    public Conversation(Cursor cursor) {
        if (cursor != null) {
            id = cursor.getLong(UIProvider.CONVERSATION_ID_COLUMN);
            uri = Uri.parse(cursor.getString(UIProvider.CONVERSATION_URI_COLUMN));
            dateMs = cursor.getLong(UIProvider.CONVERSATION_DATE_RECEIVED_MS_COLUMN);
            subject = cursor.getString(UIProvider.CONVERSATION_SUBJECT_COLUMN);
            // Don't allow null subject
            if (subject == null) {
                subject = "";
            }
            snippet = cursor.getString(UIProvider.CONVERSATION_SNIPPET_COLUMN);
            hasAttachments = cursor.getInt(UIProvider.CONVERSATION_HAS_ATTACHMENTS_COLUMN) != 0;
            String messageList = cursor
                    .getString(UIProvider.CONVERSATION_MESSAGE_LIST_URI_COLUMN);
            messageListUri = !TextUtils.isEmpty(messageList) ? Uri.parse(messageList) : null;
            senders = cursor.getString(UIProvider.CONVERSATION_SENDER_INFO_COLUMN);
            numMessages = cursor.getInt(UIProvider.CONVERSATION_NUM_MESSAGES_COLUMN);
            numDrafts = cursor.getInt(UIProvider.CONVERSATION_NUM_DRAFTS_COLUMN);
            sendingState = cursor.getInt(UIProvider.CONVERSATION_SENDING_STATE_COLUMN);
            priority = cursor.getInt(UIProvider.CONVERSATION_PRIORITY_COLUMN);
            read = cursor.getInt(UIProvider.CONVERSATION_READ_COLUMN) != 0;
            starred = cursor.getInt(UIProvider.CONVERSATION_STARRED_COLUMN) != 0;
            folderList = cursor.getString(UIProvider.CONVERSATION_FOLDER_LIST_COLUMN);
            rawFolders = cursor.getString(UIProvider.CONVERSATION_RAW_FOLDERS_COLUMN);
            convFlags = cursor.getInt(UIProvider.CONVERSATION_FLAGS_COLUMN);
            personalLevel = cursor.getInt(UIProvider.CONVERSATION_PERSONAL_LEVEL_COLUMN);
            spam = cursor.getInt(UIProvider.CONVERSATION_IS_SPAM_COLUMN) != 0;
            muted = cursor.getInt(UIProvider.CONVERSATION_MUTED_COLUMN) != 0;
            color = cursor.getInt(UIProvider.CONVERSATION_COLOR_COLUMN);
            position = NO_POSITION;
            localDeleteOnUpdate = false;
        }
    }

    public Conversation() {
    }

    public static Conversation create(long id, Uri uri, String subject, long dateMs,
            String snippet, boolean hasAttachment, Uri messageListUri, String senders,
            int numMessages, int numDrafts, int sendingState, int priority, boolean read,
            boolean starred, String folderList, String rawFolders, int convFlags,
            int personalLevel, boolean spam, boolean muted) {

        final Conversation conversation = new Conversation();

        conversation.id = id;
        conversation.uri = uri;
        conversation.subject = subject;
        conversation.dateMs = dateMs;
        conversation.snippet = snippet;
        conversation.hasAttachments = hasAttachment;
        conversation.messageListUri = messageListUri;
        conversation.senders = senders;
        conversation.numMessages = numMessages;
        conversation.numDrafts = numDrafts;
        conversation.sendingState = sendingState;
        conversation.priority = priority;
        conversation.read = read;
        conversation.starred = starred;
        conversation.folderList = folderList;
        conversation.rawFolders = rawFolders;
        conversation.convFlags = convFlags;
        conversation.personalLevel = personalLevel;
        conversation.spam = spam;
        conversation.muted = muted;
        conversation.color = 0;
        return conversation;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof Conversation) {
            Conversation conv = (Conversation)o;
            return conv.uri.equals(uri);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return uri.hashCode();
    }

    /**
     * Get if this conversation is marked as high priority.
     */
    public boolean isImportant() {
        return priority == UIProvider.ConversationPriority.IMPORTANT;
    }

    /**
     * Get if this conversation is mostly dead
     */
    public boolean isMostlyDead() {
        return (convFlags & FLAG_MOSTLY_DEAD) != 0;
    }
}