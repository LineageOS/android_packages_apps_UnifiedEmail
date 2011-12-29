/**
 * Copyright (c) 2011, Google Inc.
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
package com.android.email;

import com.android.email.providers.UIProvider;
import com.google.common.base.Objects;

import android.content.Context;
import android.content.Intent;
import android.content.UriMatcher;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.List;

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
            long maxMessageId) {
        mConversationId = conversationId;
        mLocalMessageId = localMessageId;
        mServerMessageId = serverMessageId;
        mMaxMessageId = maxMessageId;
    }

    public ConversationInfo(long conversationId, long maxMessageId) {
        this(conversationId, 0, 0, maxMessageId);
    }

    private ConversationInfo(long conversationId) {
        mConversationId = conversationId;
        mServerMessageId = 0;
        mLocalMessageId = 0;
        mMaxMessageId = 0;
    }

    /**
     * Builds a {@code ConversationInfo} from an {@link Intent} to view a
     * conversation by the specified data URI in the {@link Intent}.
     */
    public static ConversationInfo forIntent(Context context, Intent intent) {
        String action = intent.getAction();
        if (Intent.ACTION_VIEW.equals(action)) {
            Uri data = intent.getData();

            if (data == null) {
                return null;
            }
            // Expect: "content://mail-ls/account/EMAIL/label/LABEL" +
            // "/conversationId/123/maxServerMessageId/456/labels/LABELS"
            // Or: "content://mail-ls/account/EMAIL" +
            // "/conversationId/123/maxServerMessageId/456/labels/LABELS"
            int match = sUrlMatcher.match(data);
            if (match == UriMatcher.NO_MATCH) {
                return null;
            }

            List<String> parts = intent.getData().getPathSegments();
            return new ConversationInfo(Long.parseLong(parts.get(5)), Long.parseLong(parts.get(7)));
        } else {
            return null;
        }
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
    }

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
                    maxMessageId);
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

            ConversationInfo other = (ConversationInfo) o;
            return mConversationId == other.mConversationId
                    && mLocalMessageId == other.mLocalMessageId
                    && mServerMessageId == other.mServerMessageId
                    && mMaxMessageId == other.mMaxMessageId;
        }
    }

    @Override
    public int hashCode() {
        synchronized(this) {
            return Objects.hashCode(mConversationId, mLocalMessageId, mServerMessageId,
                    mMaxMessageId);
        }
    }

    /**
     * Updates the max server message ID of the conversation, when new messages have arrived.
     */
    public void updateMaxMessageId(long maxMessageId) {
        mMaxMessageId = maxMessageId;
    }
}
