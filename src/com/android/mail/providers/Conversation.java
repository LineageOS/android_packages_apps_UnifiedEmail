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

import android.app.Activity;
import android.content.ContentProviderClient;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.widget.Toast;

import com.android.mail.browse.ConversationCursor.ConversationOperation;
import com.android.mail.browse.ConversationCursor.ConversationProvider;

import java.util.ArrayList;
import java.util.Collection;

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
    // Used within the UI to indicate the adapter position of this conversation
    public transient int position;
    // Used within the UI to indicate that a Conversation should be removed from the
    // ConversationCursor when executing an update, e.g. the the Conversation is no longer
    // in the ConversationList for the current folder, that is it's now in some other folder(s)
    public transient boolean localDeleteOnUpdate;

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
        dest.writeParcelable(messageListUri, flags);
        dest.writeString(senders);
        dest.writeInt(numMessages);
        dest.writeInt(numDrafts);
        dest.writeInt(sendingState);
        dest.writeInt(priority);
        dest.writeByte(read ? (byte) 1 : 0);
        dest.writeByte(starred ? (byte) 1 : 0);
        dest.writeString(folderList);
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

    public static Conversation from(Cursor cursor) {
        return new Conversation(cursor);
    }

    private Conversation(Cursor cursor) {
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
            hasAttachments = cursor.getInt(UIProvider.CONVERSATION_HAS_ATTACHMENTS_COLUMN) == 1;
            messageListUri = Uri.parse(cursor
                    .getString(UIProvider.CONVERSATION_MESSAGE_LIST_URI_COLUMN));
            senders = cursor.getString(UIProvider.CONVERSATION_SENDER_INFO_COLUMN);
            numMessages = cursor.getInt(UIProvider.CONVERSATION_NUM_MESSAGES_COLUMN);
            numDrafts = cursor.getInt(UIProvider.CONVERSATION_NUM_DRAFTS_COLUMN);
            sendingState = cursor.getInt(UIProvider.CONVERSATION_SENDING_STATE_COLUMN);
            priority = cursor.getInt(UIProvider.CONVERSATION_PRIORITY_COLUMN);
            read = cursor.getInt(UIProvider.CONVERSATION_READ_COLUMN) == 1;
            starred = cursor.getInt(UIProvider.CONVERSATION_STARRED_COLUMN) == 1;
            folderList = cursor.getString(UIProvider.CONVERSATION_FOLDER_LIST_COLUMN);
            position = NO_POSITION;
            localDeleteOnUpdate = false;
        }
    }

    // Below are methods that update Conversation data (update/delete)

    /**
     * Update a boolean column for a single conversation (see updateBoolean below)
     */
    public int updateBoolean(Context context, String columnName, boolean value) {
        ArrayList<Conversation> conversations = new ArrayList<Conversation>();
        conversations.add(this);
        return updateBoolean(context, conversations, columnName, value);
    }

    /**
     * Update a string column for a group of conversations (see updateValues below)
     */
    public static int updateBoolean(Context context, Collection<Conversation> conversations,
            String columnName, boolean value) {
        ContentValues cv = new ContentValues();
        cv.put(columnName, value);
        return updateValues(context, conversations, cv);
    }

    /**
     * Update a string column for a single conversation (see updateString below)
     */
    public int updateString(Context context, String columnName, String value) {
        ArrayList<Conversation> conversations = new ArrayList<Conversation>();
        conversations.add(this);
        return updateString(context, conversations, columnName, value);
    }

    /**
     * Update a string column for a group of conversations (see updateValues below)
     */
    public static int updateString(Context context, Collection<Conversation> conversations,
            String columnName, String value) {
        ContentValues cv = new ContentValues();
        cv.put(columnName, value);
        return updateValues(context, conversations, cv);
    }

    /**
     * Update a boolean column for a group of conversations, immediately in the UI and in a single
     * transaction in the underlying provider
     * @param conversations a collection of conversations
     * @param context the caller's context
     * @param columnName the column to update
     * @param value the new value
     * @return the sequence number of the operation (for undo)
     */
    private static int updateValues(Context context, Collection<Conversation> conversations,
            ContentValues values) {
        ArrayList<ConversationOperation> ops = new ArrayList<ConversationOperation>();
        for (Conversation conv: conversations) {
            ConversationOperation op =
                    new ConversationOperation(ConversationOperation.UPDATE, conv, values);
            ops.add(op);
        }
        return apply(context, ops);
    }

    /**
     * Delete a single conversation
     * @param context the caller's context
     * @return the sequence number of the operation (for undo)
     */
    public int delete(Context context) {
        ArrayList<Conversation> conversations = new ArrayList<Conversation>();
        conversations.add(this);
        return delete(context, conversations);
    }

    /**
     * Delete a group of conversations immediately in the UI and in a single transaction in the
     * underlying provider
     * @param context the caller's context
     * @param conversations a collection of conversations
     * @return the sequence number of the operation (for undo)
     */
    public static int delete(Context context, Collection<Conversation> conversations) {
        ArrayList<ConversationOperation> ops = new ArrayList<ConversationOperation>();
        for (Conversation conv: conversations) {
            ConversationOperation op =
                    new ConversationOperation(ConversationOperation.DELETE, conv);
            ops.add(op);
        }
        return apply(context, ops);
    }

    // Convenience methods
    private static int apply(Context context, ArrayList<ConversationOperation> operations) {
        ContentProviderClient client =
                context.getContentResolver().acquireContentProviderClient(
                        ConversationProvider.AUTHORITY);
        try {
            ConversationProvider cp = (ConversationProvider)client.getLocalContentProvider();
            return cp.apply(operations);
        } finally {
            client.release();
        }
    }

    public static void undo(final Context context, final String undoUri) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                Cursor c = context.getContentResolver().query(Uri.parse(undoUri),
                        UIProvider.UNDO_PROJECTION, null, null, null);
                if (c != null) {
                    c.close();
                }
            }
        });
    }
}