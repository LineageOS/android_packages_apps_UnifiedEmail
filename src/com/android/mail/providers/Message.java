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
import android.os.Parcel;
import android.os.Parcelable;


public class Message implements Parcelable {
    public long id;
    public long serverId;
    public String uri;
    public long conversationId;
    public String subject;
    public String snippet;
    public String from;
    public String to;
    public String cc;
    public String bcc;
    public String replyTo;
    public long dateReceivedMs;
    public String bodyHtml;
    public String bodyText;
    public boolean embedsExternalResources;
    public String refMessageId;
    public int draftType;
    public boolean appendRefMessageContent;
    public boolean hasAttachments;
    public String attachmentListUri;
    public long messageFlags;
    public String joinedAttachmentInfos;

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(id);
        dest.writeLong(serverId);
        dest.writeString(uri);
        dest.writeLong(conversationId);
        dest.writeString(subject);
        dest.writeString(snippet);
        dest.writeString(from);
        dest.writeString(to);
        dest.writeString(cc);
        dest.writeString(bcc);
        dest.writeString(replyTo);
        dest.writeLong(dateReceivedMs);
        dest.writeString(bodyHtml);
        dest.writeString(bodyText);
        dest.writeInt(embedsExternalResources ? 1 : 0);
        dest.writeString(refMessageId);
        dest.writeInt(draftType);
        dest.writeInt(appendRefMessageContent ? 1 : 0);
        dest.writeInt(hasAttachments ? 1 : 0);
        dest.writeString(attachmentListUri);
        dest.writeLong(messageFlags);
        dest.writeString(joinedAttachmentInfos);
    }

    public Message() {
    }

    private Message(Parcel in) {
        id = in.readLong();
        serverId = in.readLong();
        uri = in.readString();
        conversationId = in.readLong();
        subject = in.readString();
        snippet = in.readString();
        from = in.readString();
        to = in.readString();
        cc = in.readString();
        bcc = in.readString();
        replyTo = in.readString();
        dateReceivedMs = in.readLong();
        bodyHtml = in.readString();
        bodyText = in.readString();
        embedsExternalResources = in.readInt() != 0;
        refMessageId = in.readString();
        draftType = in.readInt();
        appendRefMessageContent = in.readInt() != 0;
        hasAttachments = in.readInt() != 0;
        attachmentListUri = in.readString();
        messageFlags = in.readLong();
        joinedAttachmentInfos = in.readString();
    }

    @Override
    public String toString() {
        return "[message id=" + id + "]";
    }

    public static final Creator<Message> CREATOR = new Creator<Message>() {

        @Override
        public Message createFromParcel(Parcel source) {
            return new Message(source);
        }

        @Override
        public Message[] newArray(int size) {
            return new Message[size];
        }

    };

    public static Message from(Cursor cursor) {
        return new Message(cursor);
    }

    private Message(Cursor cursor) {
        if (cursor != null) {
            id = cursor.getLong(UIProvider.MESSAGE_ID_COLUMN);
            serverId = cursor.getLong(UIProvider.MESSAGE_SERVER_ID_COLUMN);
            uri = cursor.getString(UIProvider.MESSAGE_URI_COLUMN);
            conversationId = cursor.getLong(UIProvider.MESSAGE_CONVERSATION_ID_COLUMN);
            subject = cursor.getString(UIProvider.MESSAGE_SUBJECT_COLUMN);
            snippet = cursor.getString(UIProvider.MESSAGE_SNIPPET_COLUMN);
            from = cursor.getString(UIProvider.MESSAGE_FROM_COLUMN);
            to = cursor.getString(UIProvider.MESSAGE_TO_COLUMN);
            cc = cursor.getString(UIProvider.MESSAGE_CC_COLUMN);
            bcc = cursor.getString(UIProvider.MESSAGE_BCC_COLUMN);
            replyTo = cursor.getString(UIProvider.MESSAGE_REPLY_TO_COLUMN);
            dateReceivedMs = cursor.getLong(UIProvider.MESSAGE_DATE_RECEIVED_MS_COLUMN);
            bodyHtml = cursor.getString(UIProvider.MESSAGE_BODY_HTML_COLUMN);
            bodyText = cursor.getString(UIProvider.MESSAGE_BODY_TEXT_COLUMN);
            embedsExternalResources = cursor
                    .getInt(UIProvider.MESSAGE_EMBEDS_EXTERNAL_RESOURCES_COLUMN) != 0;
            refMessageId = cursor.getString(UIProvider.MESSAGE_REF_MESSAGE_ID_COLUMN);
            draftType = cursor.getInt(UIProvider.MESSAGE_DRAFT_TYPE_COLUMN);
            appendRefMessageContent = cursor
                    .getInt(UIProvider.MESSAGE_APPEND_REF_MESSAGE_CONTENT_COLUMN) != 0;
            hasAttachments = cursor.getInt(UIProvider.MESSAGE_HAS_ATTACHMENTS_COLUMN) != 0;
            attachmentListUri = cursor.getString(UIProvider.MESSAGE_ATTACHMENT_LIST_URI_COLUMN);
            messageFlags = cursor.getLong(UIProvider.MESSAGE_FLAGS_COLUMN);
            joinedAttachmentInfos = cursor
                    .getString(UIProvider.MESSAGE_JOINED_ATTACHMENT_INFOS_COLUMN);
        }
    }
}
