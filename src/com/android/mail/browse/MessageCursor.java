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

import com.google.common.collect.Maps;

import android.database.Cursor;
import android.database.CursorWrapper;

import com.android.mail.providers.Message;
import com.android.mail.providers.UIProvider;

import java.util.Map;

/**
 * MessageCursor contains the messages within a conversation; the public methods within should
 * only be called by the UI thread, as cursor position isn't guaranteed to be maintained
 */
public class MessageCursor extends CursorWrapper {

    private final Map<Long, Message> mCache = Maps.newHashMap();

    public MessageCursor(Cursor inner) {
        super(inner);
    }

    public Message getMessage() {
        final long id = getWrappedCursor().getLong(UIProvider.MESSAGE_ID_COLUMN);
        Message m = mCache.get(id);
        if (m == null) {
            m = new Message(this);
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
}