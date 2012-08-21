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
import com.google.common.base.Objects;
import com.google.common.collect.Maps;

import java.util.Map;

/**
 * MessageCursor contains the messages within a conversation; the public methods within should
 * only be called by the UI thread, as cursor position isn't guaranteed to be maintained
 */
public class MessageCursor extends CursorWrapper {

    private final Map<Long, ConversationMessage> mCache = Maps.newHashMap();
    private final Conversation mConversation;
    private final ConversationController mController;

    private Integer mStatus;

    public interface ConversationController {
        ConversationUpdater getListController();
        MessageCursor getMessageCursor();
    }

    /**
     * A message created as part of a conversation view. Sometimes, like during star/unstar, it's
     * handy to have the owning {@link Conversation} for context.
     *
     * <p>This class must remain separate from the {@link MessageCursor} from whence it came,
     * because cursors can be closed by their Loaders at any time. The
     * {@link ConversationController} intermediate is used to obtain the currently opened cursor.
     *
     * <p>(N.B. This is a {@link Parcelable}, so try not to add non-transient fields here.
     * Parcelable state belongs either in {@link Message} or {@link MessageViewState}. The
     * assumption is that this class never needs the state of its extra context saved.)
     */
    public static final class ConversationMessage extends Message {

        public final transient Conversation conversation;

        private final transient ConversationController mController;

        public ConversationMessage(MessageCursor cursor, Conversation conv,
                ConversationController controller) {
            super(cursor);
            conversation = conv;
            mController = controller;
        }

        /**
         * Returns a hash code based on this message's identity, contents and current state.
         * This is a separate method from hashCode() to allow for an instance of this class to be
         * a functional key in a hash-based data structure.
         *
         */
        public int getStateHashCode() {
            return Objects.hashCode(uri, read, from, bodyHtml, bodyText, starred, isSending,
                    attachmentsJson);
        }

        public boolean isConversationStarred() {
            final MessageCursor c = mController.getMessageCursor();
            return c != null && c.isConversationStarred();
        }

        public void star(boolean newStarred) {
            final ConversationUpdater listController = mController.getListController();
            if (listController != null) {
                listController.starMessage(this, newStarred);
            }
        }

    }

    public MessageCursor(Cursor inner, Conversation conv, ConversationController controller) {
        super(inner);
        mConversation = conv;
        mController = controller;
    }

    public ConversationMessage getMessage() {
        final long id = getWrappedCursor().getLong(UIProvider.MESSAGE_ID_COLUMN);
        ConversationMessage m = mCache.get(id);
        if (m == null) {
            m = new ConversationMessage(this, mConversation, mController);
            mCache.put(id, m);
        }
        return m;
    }

    // Is the conversation starred?
    public boolean isConversationStarred() {
        int pos = -1;
        while (moveToPosition(++pos)) {
            if (getMessage().starred) {
                return true;
            }
        }
        return false;
    }


    public boolean isConversationRead() {
        int pos = -1;
        while (moveToPosition(++pos)) {
            if (!getMessage().read) {
                return false;
            }
        }
        return true;
    }
    public void markMessagesRead() {
        int pos = -1;
        while (moveToPosition(++pos)) {
            getMessage().read = true;
        }
    }

    @Override
    public int hashCode() {
        // overriding hashCode() and not equals() is okay, since we don't expect to use
        // MessageCursors as keys in any hash-based data structures
        int hashCode = 17;
        int pos = -1;
        while (moveToPosition(++pos)) {
            hashCode = 31 * hashCode + getMessage().getStateHashCode();
        }
        return hashCode;
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
                    "[Message #%d uri=%s id=%d serverId=%s, from='%s' draftType=%d isSending=%s]\n",
                    pos, m.uri, m.id, m.serverId, m.from, m.draftType, m.isSending));
        }
        return sb.toString();
    }

}