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
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;

import com.android.mail.providers.Account;
import com.android.mail.providers.Attachment;
import com.android.mail.providers.Conversation;
import com.android.mail.providers.Message;
import com.android.mail.providers.UIProvider;
import com.android.mail.providers.UIProvider.CursorExtraKeys;
import com.android.mail.providers.UIProvider.CursorStatus;
import com.android.mail.ui.ConversationUpdater;

import com.google.common.base.Objects;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import java.util.List;
import java.util.Map;

/**
 * MessageCursor contains the messages within a conversation; the public methods within should
 * only be called by the UI thread, as cursor position isn't guaranteed to be maintained
 */
public class MessageCursor extends CursorWrapper {

    private final Map<Long, ConversationMessage> mCache = Maps.newHashMap();
    /**
     * The current controller that this cursor can use to reference the owning {@link Conversation},
     * and a current {@link ConversationUpdater}. Since this cursor will survive a rotation, but
     * the controller does not, whatever the new controller is MUST update this reference before
     * using this cursor.
     */
    private ConversationController mController;

    private Integer mStatus;

    public interface ConversationController {
        Conversation getConversation();
        ConversationUpdater getListController();
        MessageCursor getMessageCursor();
        Account getAccount();
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

        private transient ConversationController mController;

        private ConversationMessage(MessageCursor cursor) {
            super(cursor);
        }

        public void setController(ConversationController controller) {
            mController = controller;
        }

        public Conversation getConversation() {
            return mController.getConversation();
        }

        public Account getAccount() {
            return mController.getAccount();
        }

        /**
         * Returns a hash code based on this message's identity, contents and current state.
         * This is a separate method from hashCode() to allow for an instance of this class to be
         * a functional key in a hash-based data structure.
         *
         */
        public int getStateHashCode() {
            return Objects.hashCode(uri, read, starred, getAttachmentsStateHashCode());
        }

        private int getAttachmentsStateHashCode() {
            int hash = 0;
            for (Attachment a : getAttachments()) {
                final Uri uri = a.getIdentifierUri();
                hash += (uri != null ? uri.hashCode() : 0);
            }
            return hash;
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

    public MessageCursor(Cursor inner) {
        super(inner);
    }

    public void setController(ConversationController controller) {
        mController = controller;
    }

    public ConversationMessage getMessage() {
        final long id = getWrappedCursor().getLong(UIProvider.MESSAGE_ID_COLUMN);
        ConversationMessage m = mCache.get(id);
        if (m == null) {
            m = new ConversationMessage(this);
            mCache.put(id, m);
        }
        // ALWAYS set up each ConversationMessage with the latest controller.
        // Rotation invalidates everything except this Cursor, its Loader and the cached Messages,
        // so if we want to continue using them after rotate, we have to ensure their controller
        // references always point to the current controller.
        m.setController(mController);
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

    public int getStateHashCode() {
        return getStateHashCode(0);
    }

    /**
     * Calculate a hash code that compactly summarizes the state of the messages in this cursor,
     * with respect to the way the messages are displayed in conversation view. This is not a
     * general-purpose hash code. When the state hash codes of a new cursor differs from the
     * existing cursor's hash code, the conversation view will re-render from scratch.
     *
     * @param exceptLast optional number of messages to exclude iterating through at the end of the
     * cursor. pass zero to iterate through all messages (or use {@link #getStateHashCode()}).
     * @return state hash code of the selected messages in this cursor
     */
    public int getStateHashCode(int exceptLast) {
        int hashCode = 17;
        int pos = -1;
        final int stopAt = getCount() - exceptLast;
        while (moveToPosition(++pos) && pos < stopAt) {
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

    /**
     * Returns true if the cursor is fully loaded. Returns false if the cursor is expected to get
     * new messages.
     * @return
     */
    public boolean isLoaded() {
        return !CursorStatus.isWaitingForResults(getStatus());
    }

    public String getDebugDump() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("conv='%s' status=%d messages:\n",
                mController.getConversation(), getStatus()));
        int pos = -1;
        while (moveToPosition(++pos)) {
            final ConversationMessage m = getMessage();
            final List<Uri> attUris = Lists.newArrayList();
            for (Attachment a : m.getAttachments()) {
                attUris.add(a.uri);
            }
            sb.append(String.format(
                    "[Message #%d hash=%s uri=%s id=%s serverId=%s from='%s' draftType=%d" +
                    " isSending=%s read=%s starred=%s attUris=%s]\n",
                    pos, m.getStateHashCode(), m.uri, m.id, m.serverId, m.getFrom(), m.draftType,
                    m.isSending, m.read, m.starred, attUris));
        }
        return sb.toString();
    }

}