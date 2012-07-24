/*
 * Copyright (C) 2012 Google Inc.
 * Licensed to The Android Open Source Project.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.mail.ui;

import android.net.Uri;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

import com.android.mail.providers.Conversation;
import com.android.mail.providers.ConversationInfo;
import com.android.mail.providers.Message;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import org.json.JSONException;

import java.util.Map;
import java.util.Set;

/**
 * A small class to keep state for conversation view when restoring.
 *
 */
class ConversationViewState implements Parcelable {

    // N.B. don't serialize entire Messages because they contain body HTML/text

    private final Map<Uri, MessageViewState> mMessageViewStates = Maps.newHashMap();

    private String mConversationInfo;

    public ConversationViewState() {}

    public boolean isUnread(Message m) {
        final MessageViewState mvs = mMessageViewStates.get(m.uri);
        return (mvs != null && !mvs.read);
    }

    public void setReadState(Message m, boolean read) {
        MessageViewState mvs = mMessageViewStates.get(m.uri);
        if (mvs == null) {
            mvs = new MessageViewState();
        }
        mvs.read = read;
        mMessageViewStates.put(m.uri, mvs);
    }

    /**
     * Returns the expansion state of a message in a conversation view. Expansion state only refers
     * to the user action of expanding or collapsing a message view, and not any messages that
     * are expanded by default (e.g. last message, starred messages).
     *
     * @param m a Message in the conversation
     * @return true if the user expanded it, false if the user collapsed it, or null otherwise.
     */
    public Boolean getExpandedState(Message m) {
        final MessageViewState mvs = mMessageViewStates.get(m.uri);
        return (mvs == null ? null : mvs.expanded);
    }

    public void setExpandedState(Message m, boolean expanded) {
        MessageViewState mvs = mMessageViewStates.get(m.uri);
        if (mvs == null) {
            mvs = new MessageViewState();
        }
        mvs.expanded = expanded;
        mMessageViewStates.put(m.uri, mvs);
    }

    public String getConversationInfo() {
        return mConversationInfo;
    }

    public void setInfoForConversation(Conversation conv) throws JSONException {
        mConversationInfo = ConversationInfo.toString(conv.conversationInfo);
    }

    /**
     * Returns URIs of all unread messages in the conversation per
     * {@link #setReadState(Message, boolean)}. Returns an empty set for read conversations.
     *
     */
    public Set<Uri> getUnreadMessageUris() {
        final Set<Uri> result = Sets.newHashSet();
        for (Uri uri : mMessageViewStates.keySet()) {
            final MessageViewState mvs = mMessageViewStates.get(uri);
            if (mvs != null && !mvs.read) {
                result.add(uri);
            }
        }
        return result;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        final Bundle states = new Bundle();
        for (Uri uri : mMessageViewStates.keySet()) {
            final MessageViewState mvs = mMessageViewStates.get(uri);
            states.putParcelable(uri.toString(), mvs);
        }
        dest.writeBundle(states);
        dest.writeString(mConversationInfo);
    }

    private ConversationViewState(Parcel source) {
        final Bundle states = source.readBundle(MessageViewState.class.getClassLoader());
        for (String key : states.keySet()) {
            final MessageViewState state = states.getParcelable(key);
            mMessageViewStates.put(Uri.parse(key), state);
        }
        mConversationInfo = source.readString();
    }

    public static Parcelable.Creator<ConversationViewState> CREATOR =
            new Parcelable.Creator<ConversationViewState>() {

        @Override
        public ConversationViewState createFromParcel(Parcel source) {
            return new ConversationViewState(source);
        }

        @Override
        public ConversationViewState[] newArray(int size) {
            return new ConversationViewState[size];
        }

    };

    // Keep per-message state in an inner Parcelable.
    // This is a semi-private implementation detail.
    static class MessageViewState implements Parcelable {

        public boolean read;
        /**
         * null = default, false = collapsed, true = expanded
         */
        public Boolean expanded;

        public MessageViewState() {}

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeInt(read ? 1 : 0);
            dest.writeInt(expanded == null ? -1 : (expanded ? 1 : 0));
        }

        private MessageViewState(Parcel source) {
            read = (source.readInt() != 0);
            final int expandedVal = source.readInt();
            expanded = (expandedVal == -1) ? null : (expandedVal != 0);
        }

        @SuppressWarnings("hiding")
        public static Parcelable.Creator<MessageViewState> CREATOR =
                new Parcelable.Creator<MessageViewState>() {

            @Override
            public MessageViewState createFromParcel(Parcel source) {
                return new MessageViewState(source);
            }

            @Override
            public MessageViewState[] newArray(int size) {
                return new MessageViewState[size];
            }

        };

    }

}
