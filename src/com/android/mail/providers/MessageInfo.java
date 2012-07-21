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

import org.json.JSONException;
import org.json.JSONObject;

public class MessageInfo {
    public static String SENDER_LIST_TOKEN_ELIDED = " .. ";
    private static final String MESSAGE_READ = "msg_read";
    private static final String MESSAGE_STARRED = "msg_starred";
    private static final String MESSAGE_SENDER = "msg_sender";
    public boolean read;
    public boolean starred;
    public final String sender;

    public MessageInfo(boolean isRead, boolean isStarred, String senderString) {
        read = isRead;
        starred = isStarred;
        sender = senderString;
    }

    public void markRead(boolean isRead) {
        read = isRead;
    }

    public static JSONObject toJSON(MessageInfo info) throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put(MESSAGE_READ, info.read);
        obj.put(MESSAGE_STARRED, info.starred);
        obj.put(MESSAGE_SENDER, info.sender);
        return obj;
    }

    public static String toString(MessageInfo info) throws JSONException {
        return toJSON(info).toString();
    }

    public static MessageInfo fromJSON(JSONObject obj) throws JSONException {
        return new MessageInfo(obj.getBoolean(MESSAGE_READ), obj.getBoolean(MESSAGE_STARRED),
                obj.getString(MESSAGE_SENDER));
    }

    public static MessageInfo fromString(String inString) throws JSONException {
        return fromJSON(new JSONObject(inString));
    }
}
