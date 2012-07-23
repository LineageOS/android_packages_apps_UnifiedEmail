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

import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

public class ConversationInfo {
    private static final String CONV_MESSAGE_COUNT = "conv_msg_count";
    private static final String CONV_DRAFT_COUNT = "conv_draft_count";
    private static final String CONV_MESSAGES = "conv_msgs";
    private static final String CONV_FIRST_UNREAD_SNIPPET = "conv_first_unread";
    private static final String CONV_LAST_SNIPPET = "conv_last";
    private static final String CONV_FIRST_SNIPPET = "conv_first";
    private static final String CONV_SENDERS_DEPRECATED = "conv_senders";

    final public ArrayList<MessageInfo> messageInfos;
    final public int messageCount;
    final public int draftCount;
    public String firstSnippet;
    public String firstUnreadSnippet;
    public String lastSnippet;
    @Deprecated
    public String sendersInfo;

    public ConversationInfo(int count, int draft, String first, String firstUnread, String last,
            String senders) {
        messageCount = count;
        draftCount = draft;
        messageInfos = new ArrayList<MessageInfo>();
        firstSnippet = first;
        firstUnreadSnippet = firstUnread;
        lastSnippet = last;
        sendersInfo = senders;
    }

    public void addMessage(MessageInfo info) {
        messageInfos.add(info);
    }

    public static JSONObject toJSON(ConversationInfo info) throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put(CONV_MESSAGE_COUNT, info.messageCount);
        obj.put(CONV_DRAFT_COUNT, info.draftCount);
        obj.put(CONV_SENDERS_DEPRECATED, info.sendersInfo);
        obj.put(CONV_FIRST_SNIPPET, info.firstSnippet);
        obj.put(CONV_FIRST_UNREAD_SNIPPET, info.firstUnreadSnippet);
        obj.put(CONV_LAST_SNIPPET, info.lastSnippet);
        JSONArray array = new JSONArray();
        for (MessageInfo msgInfo : info.messageInfos) {
            array.put(MessageInfo.toJSON(msgInfo));
        }
        obj.put(CONV_MESSAGES, array.toString());
        return obj;
    }

    public static String toString(ConversationInfo info) throws JSONException {
        return toJSON(info).toString();
    }

    public static ConversationInfo fromJSON(JSONObject obj) throws JSONException {
        ConversationInfo info = new ConversationInfo(obj.getInt(CONV_MESSAGE_COUNT),
                obj.getInt(CONV_DRAFT_COUNT), obj.optString(CONV_FIRST_SNIPPET),
                obj.optString(CONV_FIRST_UNREAD_SNIPPET), obj.optString(CONV_LAST_SNIPPET),
                obj.optString(CONV_SENDERS_DEPRECATED));
        JSONArray array = new JSONArray(obj.getString(CONV_MESSAGES));
        for (int i = 0; i < array.length(); i++) {
            info.addMessage(MessageInfo.fromJSON(array.getJSONObject(i)));
        }
        return info;
    }

    public static ConversationInfo fromString(String inString) throws JSONException {
        if (TextUtils.isEmpty(inString)) {
            return null;
        }
        return fromJSON(new JSONObject(inString));
    }

    public void markRead(boolean read) {
        for (MessageInfo msg : messageInfos) {
            msg.markRead(read);
        }
        if (read) {
            firstSnippet = lastSnippet;
        } else {
            firstSnippet = firstUnreadSnippet;
        }
    }
}
