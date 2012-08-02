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
import android.provider.BaseColumns;
import android.text.TextUtils;

import com.android.mail.providers.UIProvider.ConversationColumns;
import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

public class Conversation implements Parcelable {
    public static final int NO_POSITION = -1;

    /**
     * @see BaseColumns#_ID
     */
    public long id;
    /**
     * @see UIProvider.ConversationColumns#URI
     */
    public Uri uri;
    /**
     * @see UIProvider.ConversationColumns#SUBJECT
     */
    public String subject;
    /**
     * @see UIProvider.ConversationColumns#DATE_RECEIVED_MS
     */
    public long dateMs;
    /**
     * @see UIProvider.ConversationColumns#SNIPPET
     */
    @Deprecated
    public String snippet;
    /**
     * @see UIProvider.ConversationColumns#HAS_ATTACHMENTS
     */
    public boolean hasAttachments;
    /**
     * @see UIProvider.ConversationColumns#MESSAGE_LIST_URI
     */
    public Uri messageListUri;
    /**
     * @see UIProvider.ConversationColumns#SENDER_INFO
     */
    @Deprecated
    public String senders;
    /**
     * @see UIProvider.ConversationColumns#NUM_MESSAGES
     */
    private int numMessages;
    /**
     * @see UIProvider.ConversationColumns#NUM_DRAFTS
     */
    private int numDrafts;
    /**
     * @see UIProvider.ConversationColumns#SENDING_STATE
     */
    public int sendingState;
    /**
     * @see UIProvider.ConversationColumns#PRIORITY
     */
    public int priority;
    /**
     * @see UIProvider.ConversationColumns#READ
     */
    public boolean read;
    /**
     * @see UIProvider.ConversationColumns#STARRED
     */
    public boolean starred;
    /**
     * @see UIProvider.ConversationColumns#RAW_FOLDERS
     */
    private String rawFolders;
    /**
     * @see UIProvider.ConversationColumns#FLAGS
     */
    public int convFlags;
    /**
     * @see UIProvider.ConversationColumns#PERSONAL_LEVEL
     */
    public int personalLevel;
    /**
     * @see UIProvider.ConversationColumns#SPAM
     */
    public boolean spam;
    /**
     * @see UIProvider.ConversationColumns#MUTED
     */
    public boolean muted;
    /**
     * @see UIProvider.ConversationColumns#PHISHING
     */
    public boolean phishing;
    /**
     * @see UIProvider.ConversationColumns#COLOR
     */
    public int color;
    /**
     * @see UIProvider.ConversationColumns#ACCOUNT_URI
     */
    public Uri accountUri;
    /**
     * @see UIProvider.ConversationColumns#CONVERSATION_INFO
     */
    public ConversationInfo conversationInfo;

    // Used within the UI to indicate the adapter position of this conversation
    public transient int position;
    // Used within the UI to indicate that a Conversation should be removed from
    // the ConversationCursor when executing an update, e.g. the the
    // Conversation is no longer in the ConversationList for the current folder,
    // that is it's now in some other folder(s)
    public transient boolean localDeleteOnUpdate;

    private ArrayList<Folder> cachedRawFolders;
    private ArrayList<Folder> cachedDisplayableFolders;

    // Constituents of convFlags below
    // Flag indicating that the item has been deleted, but will continue being
    // shown in the list Delete/Archive of a mostly-dead item will NOT propagate
    // the delete/archive, but WILL remove the item from the cursor
    public static final int FLAG_MOSTLY_DEAD = 1 << 0;

    /** An immutable, empty conversation list */
    public static final Collection<Conversation> EMPTY = Collections.emptyList();

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
        dest.writeString(rawFolders);
        dest.writeInt(convFlags);
        dest.writeInt(personalLevel);
        dest.writeInt(spam ? 1 : 0);
        dest.writeInt(phishing ? 1 : 0);
        dest.writeInt(muted ? 1 : 0);
        dest.writeInt(color);
        dest.writeParcelable(accountUri, 0);
        dest.writeString(ConversationInfo.toString(conversationInfo));
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
        rawFolders = in.readString();
        convFlags = in.readInt();
        personalLevel = in.readInt();
        spam = in.readInt() != 0;
        phishing = in.readInt() != 0;
        muted = in.readInt() != 0;
        color = in.readInt();
        accountUri = in.readParcelable(null);
        position = NO_POSITION;
        localDeleteOnUpdate = false;
        conversationInfo = ConversationInfo.fromString(in.readString());
    }

    @Override
    public String toString() {
        return "[conversation id=" + id + ", subject =" + subject + "]";
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

    /**
     * The column that needs to be updated to change the read state of a
     * conversation.
     */
    public static final String UPDATE_FOLDER_COLUMN = ConversationColumns.RAW_FOLDERS;

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
            hasAttachments = cursor.getInt(UIProvider.CONVERSATION_HAS_ATTACHMENTS_COLUMN) != 0;
            String messageList = cursor.getString(UIProvider.CONVERSATION_MESSAGE_LIST_URI_COLUMN);
            messageListUri = !TextUtils.isEmpty(messageList) ? Uri.parse(messageList) : null;
            sendingState = cursor.getInt(UIProvider.CONVERSATION_SENDING_STATE_COLUMN);
            priority = cursor.getInt(UIProvider.CONVERSATION_PRIORITY_COLUMN);
            read = cursor.getInt(UIProvider.CONVERSATION_READ_COLUMN) != 0;
            starred = cursor.getInt(UIProvider.CONVERSATION_STARRED_COLUMN) != 0;
            rawFolders = cursor.getString(UIProvider.CONVERSATION_RAW_FOLDERS_COLUMN);
            convFlags = cursor.getInt(UIProvider.CONVERSATION_FLAGS_COLUMN);
            personalLevel = cursor.getInt(UIProvider.CONVERSATION_PERSONAL_LEVEL_COLUMN);
            spam = cursor.getInt(UIProvider.CONVERSATION_IS_SPAM_COLUMN) != 0;
            phishing = cursor.getInt(UIProvider.CONVERSATION_IS_PHISHING_COLUMN) != 0;
            muted = cursor.getInt(UIProvider.CONVERSATION_MUTED_COLUMN) != 0;
            color = cursor.getInt(UIProvider.CONVERSATION_COLOR_COLUMN);
            String account = cursor.getString(UIProvider.CONVERSATION_ACCOUNT_URI_COLUMN);
            accountUri = !TextUtils.isEmpty(account) ? Uri.parse(account) : null;
            position = NO_POSITION;
            localDeleteOnUpdate = false;
            conversationInfo = ConversationInfo.fromString(cursor
                    .getString(UIProvider.CONVERSATION_INFO_COLUMN));
            if (conversationInfo == null) {
                snippet = cursor.getString(UIProvider.CONVERSATION_SNIPPET_COLUMN);
                senders = cursor.getString(UIProvider.CONVERSATION_SENDER_INFO_COLUMN);
                numMessages = cursor.getInt(UIProvider.CONVERSATION_NUM_MESSAGES_COLUMN);
                numDrafts = cursor.getInt(UIProvider.CONVERSATION_NUM_DRAFTS_COLUMN);
            }
        }
    }

    public Conversation() {
    }

    public static Conversation create(long id, Uri uri, String subject, long dateMs,
            String snippet, boolean hasAttachment, Uri messageListUri, String senders,
            int numMessages, int numDrafts, int sendingState, int priority, boolean read,
            boolean starred, String rawFolders, int convFlags, int personalLevel, boolean spam,
            boolean phishing, boolean muted, Uri accountUri, ConversationInfo conversationInfo) {

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
        conversation.rawFolders = rawFolders;
        conversation.convFlags = convFlags;
        conversation.personalLevel = personalLevel;
        conversation.spam = spam;
        conversation.phishing = phishing;
        conversation.muted = muted;
        conversation.color = 0;
        conversation.accountUri = accountUri;
        conversation.conversationInfo = conversationInfo;
        return conversation;
    }

    public ArrayList<Folder> getRawFolders() {
        if (cachedRawFolders == null) {
            // Create cached folders.
            if (!TextUtils.isEmpty(rawFolders)) {
                cachedRawFolders = Folder.getFoldersArray(rawFolders);
            } else {
                return new ArrayList<Folder>();
            }
        }
        return cachedRawFolders;
    }

    public void setRawFolders(String raw) {
        clearCachedFolders();
        rawFolders = raw;
    }

    public String getRawFoldersString() {
        return rawFolders;
    }

    private void clearCachedFolders() {
        cachedRawFolders = null;
        cachedDisplayableFolders = null;
    }

    public ArrayList<Folder> getRawFoldersForDisplay(Folder ignoreFolder) {
        ArrayList<Folder> folders = getRawFolders();
        if (cachedDisplayableFolders == null) {
            cachedDisplayableFolders = new ArrayList<Folder>();
            for (Folder folder : folders) {
                // skip the ignoreFolder
                if (ignoreFolder != null && ignoreFolder.equals(folder)) {
                    continue;
                }
                cachedDisplayableFolders.add(folder);
            }
        }
        return cachedDisplayableFolders;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof Conversation) {
            Conversation conv = (Conversation) o;
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

    /**
     * Returns true if the URI of the conversation specified as the needle was
     * found in the collection of conversations specified as the haystack. False
     * otherwise. This method is safe to call with null arguments.
     * 
     * @param haystack
     * @param needle
     * @return true if the needle was found in the haystack, false otherwise.
     */
    public final static boolean contains(Collection<Conversation> haystack, Conversation needle) {
        // If the haystack is empty, it cannot contain anything.
        if (haystack == null || haystack.size() <= 0) {
            return false;
        }
        // The null folder exists everywhere.
        if (needle == null) {
            return true;
        }
        final long toFind = needle.id;
        for (final Conversation c : haystack) {
            if (toFind == c.id) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns a collection of a single conversation. This method always returns
     * a valid collection even if the input conversation is null.
     * 
     * @param in a conversation, possibly null.
     * @return a collection of the conversation.
     */
    public static Collection<Conversation> listOf(Conversation in) {
        final Collection<Conversation> target = (in == null) ? EMPTY : ImmutableList.of(in);
        return target;
    }

    /**
     * Get the snippet for this conversation. Masks that it may come from
     * conversation info or the original deprecated snippet string.
     */
    public String getSnippet() {
        return conversationInfo != null && !TextUtils.isEmpty(conversationInfo.firstSnippet) ?
                conversationInfo.firstSnippet : snippet;
    }

    /**
     * Get the number of messages for this conversation.
     */
    public int getNumMessages() {
        return conversationInfo != null ? conversationInfo.messageCount : numMessages;
    }

    /**
     * Get the number of drafts for this conversation.
     */
    public int numDrafts() {
        return conversationInfo != null ? conversationInfo.draftCount : numDrafts;
    }

    /**
     * Create a human-readable string of all the conversations
     * @param collection Any collection of conversations
     * @return string with a human readable representation of the conversations.
     */
    public static String toString(Collection<Conversation> collection) {
        final StringBuilder out = new StringBuilder(collection.size() + " conversations:");
        int count = 0;
        for (final Conversation c : collection) {
            count++;
            // Indent the conversations to make them easy to read in debug
            // output.
            out.append("      " + count + ": " + c.toString() + "\n");
        }
        return out.toString();
    }
}
