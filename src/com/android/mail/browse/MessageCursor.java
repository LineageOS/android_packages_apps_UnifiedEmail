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

package com.android.mail.browse;

import android.database.Cursor;
import android.database.CursorWrapper;
import android.os.Bundle;
import android.os.Parcelable;

import com.android.mail.providers.Conversation;
import com.android.mail.providers.Message;
import com.android.mail.providers.UIProvider;
import com.android.mail.providers.UIProvider.CursorExtraKeys;
import com.android.mail.providers.UIProvider.CursorStatus;
import com.android.mail.ui.ConversationUpdater;
import com.google.common.collect.Maps;

import java.util.Map;

/**
 * MessageCursor contains the messages within a conversation; the public methods within should
 * only be called by the UI thread, as cursor position isn't guaranteed to be maintained
 */
public class MessageCursor extends CursorWrapper {

    private final Map<Long, ConversationMessage> mCache = Maps.newHashMap();
    private final Conversation mConversation;
    private final ConversationUpdater mListController;

    private Integer mStatus;

    /**
     * A message created as part of a conversation view. Sometimes, like during star/unstar, it's
     * handy to have the owning {@link MessageCursor} and {@link Conversation} for context.
     *
     * <p>(N.B. This is a {@link Parcelable}, so try not to add non-transient fields here.
     * Parcelable state belongs either in {@link Message} or {@link MessageViewState}. The
     * assumption is that this class never needs the state of its extra context saved.)
     */
    public static class ConversationMessage extends Message {

        public final transient Conversation conversation;

        private final transient MessageCursor mOwningCursor;
        private final transient ConversationUpdater mListController;

        public ConversationMessage(MessageCursor cursor, Conversation conv,
                ConversationUpdater listController) {
            super(cursor);
            conversation = conv;
            mOwningCursor = cursor;
            mListController = listController;
        }

        public boolean isConversationStarred() {
            return mOwningCursor.isConversationStarred();
        }

        public void star(boolean newStarred) {
            mListController.starMessage(this, newStarred);
        }

    }

    public MessageCursor(Cursor inner, Conversation conv, ConversationUpdater listController) {
        super(inner);
        mConversation = conv;
        mListController = listController;
    }

    public ConversationMessage getMessage() {
        final long id = getWrappedCursor().getLong(UIProvider.MESSAGE_ID_COLUMN);
        ConversationMessage m = mCache.get(id);
        if (m == null) {
            m = new ConversationMessage(this, mConversation, mListController);
            mCache.put(id, m);
        }
        return m;
    }

    // Is the conversation starred?
    public boolean isConversationStarred() {
        int pos = -1;
        while (moveToPosition(++pos)) {
            Message m = getMessage();
            if (m.starred) {
                return true;
            }
        }
        return false;
    }

    public int getStatus() {
        if (mStatus != null) {
            return mStatus;
        }

        mStatus = CursorStatus.LOADED;
        final Bundle extras = getExtras();
        if (extras != null && extras.containsKey(CursorExtraKeys.EXTRA_STATUS)) {
            mStatus = extras.getInt(CursorExtraKeys.EXTRA_STATUS);
        }
        return mStatus;
    }

    public boolean isLoaded() {
        return getStatus() >= CursorStatus.LOADED || getCount() > 0; // FIXME: remove count hack
    }

    public String getDebugDump() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("conv subj='%s' status=%d messages:\n",
                mConversation.subject, getStatus()));
        int pos = -1;
        while (moveToPosition(++pos)) {
            final Message m = getMessage();
            sb.append(String.format(
                    "[Message #%d uri=%s id=%d serverId=%d, from='%s' draftType=%d isSending=%s]\n",
                    pos, m.uri, m.id, m.serverId, m.from, m.draftType, m.isSending));
        }
        return sb.toString();
    }

}