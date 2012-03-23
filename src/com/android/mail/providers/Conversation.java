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

import android.content.ContentProviderClient;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

import com.android.mail.browse.ConversationCursor.ConversationOperation;
import com.android.mail.browse.ConversationCursor.ConversationProvider;
import com.android.mail.providers.UIProvider.ConversationColumns;
import com.google.common.collect.Lists;

import java.util.ArrayList;
import java.util.Arrays;
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
    public String rawFolders;
    public int convFlags;
    public int personalLevel;
    public boolean spam;
    public boolean muted;

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
            position = NO_POSITION;
            localDeleteOnUpdate = false;
        }
    }

    private Conversation() {
    }

    // TODO: (mindyp) remove once gmail is updated and checked in.
    @Deprecated
    public static Conversation create(long id, Uri uri, String subject, long dateMs,
            String snippet, boolean hasAttachment, Uri messageListUri, String senders,
            int numMessages, int numDrafts, int sendingState, int priority, boolean read,
            boolean starred, String folderList, String rawFolders, int convFlags,
            int personalLevel) {
        return Conversation.create(id, uri, subject, dateMs, snippet, hasAttachment,
                messageListUri, senders, numMessages, numDrafts, sendingState, priority, read,
                starred, folderList, rawFolders, convFlags, personalLevel, false, false);
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
        return conversation;
    }

    /**
     * Get if this conversation is marked as high priority.
     */
    public boolean isImportant() {
        return priority == UIProvider.ConversationPriority.IMPORTANT;
    }

    // Below are methods that update Conversation data (update/delete)

    /**
     * Update an integer column for a single conversation (see updateBoolean below)
     */
    public int updateInt(Context context, String columnName, int value) {
        return updateInt(context, Arrays.asList(this), columnName, value);
    }

    /**
     * Update an integer column for a group of conversations (see updateValues below)
     */
    public static int updateInt(Context context, Collection<Conversation> conversations,
            String columnName, int value) {
        ContentValues cv = new ContentValues();
        cv.put(columnName, value);
        return updateValues(context, conversations, cv);
    }

    /**
     * Update a boolean column for a single conversation (see updateBoolean below)
     */
    public int updateBoolean(Context context, String columnName, boolean value) {
        return updateBoolean(context, Arrays.asList(this), columnName, value);
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
        return updateString(context, Arrays.asList(this), columnName, value);
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
        return apply(context,
                getOperationsForConversations(conversations, ConversationOperation.UPDATE, values));
    }

    private static ArrayList<ConversationOperation> getOperationsForConversations(
            Collection<Conversation> conversations, int op, ContentValues values) {
        return getOperationsForConversations(conversations, op, values, false /* autoNotify */);
    }

    private static ArrayList<ConversationOperation> getOperationsForConversations(
            Collection<Conversation> conversations, int type, ContentValues values,
            boolean autoNotify) {
        final ArrayList<ConversationOperation> ops = Lists.newArrayList();
        for (Conversation conv: conversations) {
            ConversationOperation op = new ConversationOperation(type, conv, values, autoNotify);
            ops.add(op);
        }
        return ops;
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
     * Mark a single conversation read/unread.
     * @param context the caller's context
     * @param read true for read, false for unread
     * @return the sequence number of the operation (for undo)
     */
    public int markRead(Context context, boolean read) {
        ContentValues values = new ContentValues();
        values.put(ConversationColumns.READ, read);

        return apply(
                context,
                getOperationsForConversations(Arrays.asList(this), ConversationOperation.UPDATE,
                        values, true /* autoNotify */));
    }

    /**
     * Delete a group of conversations immediately in the UI and in a single transaction in the
     * underlying provider
     * @param context the caller's context
     * @param conversations a collection of conversations
     * @return the sequence number of the operation (for undo)
     */
    public static int delete(Context context, Collection<Conversation> conversations) {
        ArrayList<ConversationOperation> ops = Lists.newArrayList();
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

    private static void undoLocal(Context context) {
        ContentProviderClient client =
                context.getContentResolver().acquireContentProviderClient(
                        ConversationProvider.AUTHORITY);
        try {
            ConversationProvider cp = (ConversationProvider)client.getLocalContentProvider();
            cp.undo();
        } finally {
            client.release();
        }
    }

    public static void undo(final Context context, final Uri undoUri) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                Cursor c = context.getContentResolver().query(undoUri, UIProvider.UNDO_PROJECTION,
                        null, null, null);
                if (c != null) {
                    c.close();
                }
            }
        }).start();
        undoLocal(context);
    }

    public static int archive(Context context, Collection<Conversation> conversations) {
        ArrayList<ConversationOperation> ops = Lists.newArrayList();
        for (Conversation conv: conversations) {
            ConversationOperation op =
                    new ConversationOperation(ConversationOperation.ARCHIVE, conv);
            ops.add(op);
        }
        return apply(context, ops);
    }

    public static int mute(Context context, Collection<Conversation> conversations) {
        ArrayList<ConversationOperation> ops = Lists.newArrayList();
        for (Conversation conv: conversations) {
            ConversationOperation op =
                    new ConversationOperation(ConversationOperation.MUTE, conv);
            ops.add(op);
        }
        return apply(context, ops);
    }

    public static int reportSpam(Context context, Collection<Conversation> conversations) {
        ArrayList<ConversationOperation> ops = Lists.newArrayList();
        for (Conversation conv: conversations) {
            ConversationOperation op =
                    new ConversationOperation(ConversationOperation.REPORT_SPAM, conv);
            ops.add(op);
        }
        return apply(context, ops);
    }
}