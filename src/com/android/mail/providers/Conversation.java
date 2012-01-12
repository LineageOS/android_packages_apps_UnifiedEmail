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

public class Conversation implements Parcelable {

    public long id;
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

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(id);
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
    }

    private Conversation(Parcel in) {
        id = in.readLong();
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
            dateMs = cursor.getLong(UIProvider.CONVERSATION_DATE_RECEIVED_MS_COLUMN);
            subject = cursor.getString(UIProvider.CONVERSATION_SUBJECT_COLUMN);
            snippet = cursor.getString(UIProvider.CONVERSATION_SNIPPET_COLUMN);
            hasAttachments = cursor
                    .getInt(UIProvider.CONVERSATION_HAS_ATTACHMENTS_COLUMN) == 1 ? true : false;
            messageListUri = Uri.parse(cursor
                    .getString(UIProvider.CONVERSATION_MESSAGE_LIST_URI_COLUMN));
            senders = cursor.getString(UIProvider.CONVERSATION_SENDER_INFO_COLUMN);
            numMessages = cursor.getInt(UIProvider.CONVERSATION_NUM_MESSAGES_COLUMN);
            numDrafts = cursor.getInt(UIProvider.CONVERSATION_NUM_DRAFTS_COLUMN);
            sendingState = cursor.getInt(UIProvider.CONVERSATION_SENDING_STATE_COLUMN);
            priority = cursor.getInt(UIProvider.CONVERSATION_PRIORITY_COLUMN);
        }
    }

}
