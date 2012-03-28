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

import android.content.AsyncQueryHandler;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

import com.android.mail.browse.ConversationCursor;
import com.android.mail.providers.UIProvider.ConversationColumns;
import com.android.mail.providers.UIProvider.MessageColumns;
import com.android.mail.ui.ConversationViewFragment.MessageCursor;
import com.android.mail.utils.Utils;

import java.util.Collections;
import java.util.List;


public class Message implements Parcelable {
    public long id;
    public long serverId;
    public Uri uri;
    public String conversationUri;
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
    public Uri attachmentListUri;
    public long messageFlags;
    public String joinedAttachmentInfos;
    public String saveUri;
    public String sendUri;
    public boolean alwaysShowImages;
    public boolean read;
    public boolean starred;
    public int quotedTextOffset;
    public String attachmentsJson;

    private transient String[] mToAddresses = null;
    private transient String[] mCcAddresses = null;
    private transient String[] mBccAddresses = null;
    private transient String[] mReplyToAddresses = null;

    private transient List<Attachment> mAttachments = null;

    // While viewing a list of messages, points to the MessageCursor that contains it
    private transient MessageCursor mMessageCursor = null;

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || !(o instanceof Message)) {
            return false;
        }
        final Uri otherUri = ((Message) o).uri;
        if (uri == null) {
            return (otherUri == null);
        }
        return uri.equals(otherUri);
    }

    @Override
    public int hashCode() {
        return uri == null ? 0 : uri.hashCode();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(id);
        dest.writeLong(serverId);
        dest.writeParcelable(uri, 0);
        dest.writeString(conversationUri);
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
        dest.writeParcelable(attachmentListUri, 0);
        dest.writeLong(messageFlags);
        dest.writeString(joinedAttachmentInfos);
        dest.writeString(saveUri);
        dest.writeString(sendUri);
        dest.writeInt(alwaysShowImages ? 1 : 0);
        dest.writeInt(quotedTextOffset);
        dest.writeString(attachmentsJson);
    }

    private Message(Parcel in) {
        id = in.readLong();
        serverId = in.readLong();
        uri = in.readParcelable(null);
        conversationUri = in.readString();
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
        attachmentListUri = in.readParcelable(null);
        messageFlags = in.readLong();
        joinedAttachmentInfos = in.readString();
        saveUri = in.readString();
        sendUri = in.readString();
        alwaysShowImages = in.readInt() != 0;
        quotedTextOffset = in.readInt();
        attachmentsJson = in.readString();
    }

    public Message() {

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

    public Message(MessageCursor cursor) {
        this((Cursor)cursor);
        // Only set message cursor if appropriate
        mMessageCursor = cursor;
    }

    public Message(Cursor cursor) {
        if (cursor != null) {
            id = cursor.getLong(UIProvider.MESSAGE_ID_COLUMN);
            serverId = cursor.getLong(UIProvider.MESSAGE_SERVER_ID_COLUMN);
            String message = cursor.getString(UIProvider.MESSAGE_URI_COLUMN);
            uri = !TextUtils.isEmpty(message) ? Uri.parse(message) : null;
            conversationUri = cursor.getString(UIProvider.MESSAGE_CONVERSATION_URI_COLUMN);
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
            final String attachmentsUri = cursor
                    .getString(UIProvider.MESSAGE_ATTACHMENT_LIST_URI_COLUMN);
            attachmentListUri = hasAttachments && !TextUtils.isEmpty(attachmentsUri) ? Uri
                    .parse(attachmentsUri) : null;
            messageFlags = cursor.getLong(UIProvider.MESSAGE_FLAGS_COLUMN);
            joinedAttachmentInfos = cursor
                    .getString(UIProvider.MESSAGE_JOINED_ATTACHMENT_INFOS_COLUMN);
            saveUri = cursor
                    .getString(UIProvider.MESSAGE_SAVE_URI_COLUMN);
            sendUri = cursor
                    .getString(UIProvider.MESSAGE_SEND_URI_COLUMN);
            alwaysShowImages = cursor.getInt(UIProvider.MESSAGE_ALWAYS_SHOW_IMAGES_COLUMN) != 0;
            read = cursor.getInt(UIProvider.MESSAGE_READ_COLUMN) != 0;
            starred = cursor.getInt(UIProvider.MESSAGE_STARRED_COLUMN) != 0;
            quotedTextOffset = cursor.getInt(UIProvider.QUOTED_TEXT_OFFSET_COLUMN);
            attachmentsJson = cursor.getString(UIProvider.MESSAGE_ATTACHMENTS_COLUMN);
        }
    }

    public synchronized String[] getToAddresses() {
        if (mToAddresses == null) {
            mToAddresses = Utils.splitCommaSeparatedString(to);
        }
        return mToAddresses;
    }

    public synchronized String[] getCcAddresses() {
        if (mCcAddresses == null) {
            mCcAddresses = Utils.splitCommaSeparatedString(cc);
        }
        return mCcAddresses;
    }

    public synchronized String[] getBccAddresses() {
        if (mBccAddresses == null) {
            mBccAddresses = Utils.splitCommaSeparatedString(bcc);
        }
        return mBccAddresses;
    }

    public synchronized String[] getReplyToAddresses() {
        if (mReplyToAddresses == null) {
            mReplyToAddresses = Utils.splitCommaSeparatedString(replyTo);
        }
        return mReplyToAddresses;
    }

    public synchronized List<Attachment> getAttachments() {
        if (mAttachments == null) {
            if (attachmentsJson != null) {
                mAttachments = Attachment.fromJSONArray(attachmentsJson);
            } else {
                mAttachments = Collections.emptyList();
            }
        }
        return mAttachments;
    }

    /**
     * Returns whether a "Show Pictures" button should initially appear for this message. If the
     * button is shown, the message must also block all non-local images in the body. Inversely, if
     * the button is not shown, the message must show all images within (or else the user would be
     * stuck with no images and no way to reveal them).
     *
     * @return true if a "Show Pictures" button should appear.
     */
    public boolean shouldShowImagePrompt() {
        return embedsExternalResources && !alwaysShowImages;
    }

    /**
     * Helper method to command a provider to mark all messages from this sender with the
     * {@link MessageColumns#ALWAYS_SHOW_IMAGES} flag set.
     *
     * @param handler a caller-provided handler to run the query on
     * @param token (optional) token to identify the command to the handler
     * @param cookie (optional) cookie to pass to the handler
     */
    public void markAlwaysShowImages(AsyncQueryHandler handler, int token, Object cookie) {
        final ContentValues values = new ContentValues(1);
        values.put(UIProvider.MessageColumns.ALWAYS_SHOW_IMAGES, 1);

        handler.startUpdate(token, cookie, uri, values, null, null);
    }

    /**
     * Helper method to command a provider to star/unstar this message.
     *
     * @param starred whether to star or unstar the message
     * @param handler a caller-provided handler to run the query on
     * @param token (optional) token to identify the command to the handler
     * @param cookie (optional) cookie to pass to the handler
     */
    public void star(boolean starred, AsyncQueryHandler handler, int token, Object cookie) {
        this.starred = starred;
        boolean conversationStarred = starred;
        // If we're unstarring, we need to find out if the conversation is still starred
        if (mMessageCursor != null && !starred) {
            conversationStarred = mMessageCursor.isConversationStarred();
        }
        // Update the conversation cursor so that changes are reflected simultaneously
        ConversationCursor.setConversationColumn(conversationUri, ConversationColumns.STARRED,
                conversationStarred);
        final ContentValues values = new ContentValues(1);
        values.put(UIProvider.MessageColumns.STARRED, starred ? 1 : 0);

        handler.startUpdate(token, cookie, uri, values, null, null);
    }

}
