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

import java.util.ArrayList;
import java.util.regex.Pattern;

public class ConversationInfo {
<<<<<<< HEAD
    private static final String CONV_MESSAGE_COUNT = "conv_msg_count";
    private static final String CONV_DRAFT_COUNT = "conv_draft_count";
    private static final String CONV_MESSAGES = "conv_msgs";
    private static final String CONV_FIRST_UNREAD_SNIPPET = "conv_first_unread";
    private static final String CONV_LAST_SNIPPET = "conv_last";
    private static final String CONV_FIRST_SNIPPET = "conv_first";
    private static final String CONV_SENDERS_DEPRECATED = "conv_senders";
||||||| merged common ancestors
    private static final String CONV_MESSAGE_COUNT = "conv_msg_count";
    private static final String CONV_DRAFT_COUNT = "conv_draft_count";
    private static final String CONV_MESSAGES = "conv_msgs";
    private static final String CONV_FIRST_UNREAD_SNIPPET = "conv_first_unread";
    private static final String CONV_LAST_SNIPPET = "conv_last";
    private static final String CONV_FIRST_SNIPPET = "conv_first";
=======
    public static String SPLITTER = "^*^";
    private static Pattern SPLITTER_REGEX = Pattern.compile("\\^\\*\\^");
    private static final String MESSAGE_CONV_SPLIT = "^**^";
    private static Pattern MESSAGE_CONV_SPLITTER_REGEX = Pattern.compile("\\^\\*\\*\\^");
    private static final String MESSAGE_SPLIT = "^***^";
    private static Pattern MESSAGE_SPLITTER_REGEX = Pattern.compile("\\^\\*\\*\\*\\^");
>>>>>>> abb78177

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

<<<<<<< HEAD
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
||||||| merged common ancestors
    public static JSONObject toJSON(ConversationInfo info) throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put(CONV_MESSAGE_COUNT, info.messageCount);
        obj.put(CONV_DRAFT_COUNT, info.draftCount);
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
=======
    public static String toString(ConversationInfo info) {
>>>>>>> abb78177
        if (info == null) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        builder.append(info.messageCount);
        builder.append(SPLITTER);
        builder.append(info.draftCount);
        builder.append(SPLITTER);
        builder.append(info.firstSnippet);
        builder.append(SPLITTER);
        builder.append(info.firstUnreadSnippet);
        builder.append(SPLITTER);
        builder.append(info.lastSnippet);
        builder.append(MESSAGE_CONV_SPLIT);
        builder.append(getMessageInfoString(info));
        return builder.toString();
    }

<<<<<<< HEAD
    public static ConversationInfo fromJSON(JSONObject obj) throws JSONException {
        ConversationInfo info = new ConversationInfo(obj.getInt(CONV_MESSAGE_COUNT),
                obj.getInt(CONV_DRAFT_COUNT), obj.optString(CONV_FIRST_SNIPPET),
                obj.optString(CONV_FIRST_UNREAD_SNIPPET), obj.optString(CONV_LAST_SNIPPET),
                obj.optString(CONV_SENDERS_DEPRECATED));
        JSONArray array = new JSONArray(obj.getString(CONV_MESSAGES));
        for (int i = 0; i < array.length(); i++) {
            info.addMessage(MessageInfo.fromJSON(array.getJSONObject(i)));
||||||| merged common ancestors
    public static ConversationInfo fromJSON(JSONObject obj) throws JSONException {
        ConversationInfo info = new ConversationInfo(obj.getInt(CONV_MESSAGE_COUNT),
                obj.getInt(CONV_DRAFT_COUNT), obj.optString(CONV_FIRST_SNIPPET),
                obj.optString(CONV_FIRST_UNREAD_SNIPPET), obj.optString(CONV_LAST_SNIPPET));
        JSONArray array = new JSONArray(obj.getString(CONV_MESSAGES));
        for (int i = 0; i < array.length(); i++) {
            info.addMessage(MessageInfo.fromJSON(array.getJSONObject(i)));
=======
    private static String getMessageInfoString(ConversationInfo info) {
        // Create a string of all the messge infos
        StringBuilder builder = new StringBuilder();
        int i = 0;
        for (MessageInfo msg : info.messageInfos) {
            builder.append(MessageInfo.toString(msg));
            if (i < info.messageInfos.size() - 1) {
                builder.append(MESSAGE_SPLIT);
            }
            i++;
>>>>>>> abb78177
        }
        return builder.toString();
    }

    public static ConversationInfo fromString(String inString) {
        if (TextUtils.isEmpty(inString)) {
            return null;
        }
        String[] convMessageSplit = TextUtils.split(inString, MESSAGE_CONV_SPLITTER_REGEX);
        if (convMessageSplit.length < 2) {
            return null;
        }
        // Parse conversation
        ConversationInfo info = parseConversation(convMessageSplit[0]);
        //Messages
        parseMessages(info, convMessageSplit[1]);
        return info;
    }

    private static void parseMessages(ConversationInfo info, String messagesString) {
        String[] messages = TextUtils.split(messagesString, MESSAGE_SPLITTER_REGEX);
        for (String m : messages) {
            info.addMessage(MessageInfo.fromString(m));
        }
    }

    private static ConversationInfo parseConversation(String conv) {
        String[] split = TextUtils.split(conv, SPLITTER_REGEX);
        int messageCount = Integer.parseInt(split[0]);
        int draftCount = Integer.parseInt(split[1]);
        String first = split[2];
        String firstUnread = split[3];
        String lastUnread = split[4];
        return new ConversationInfo(messageCount, draftCount, first, firstUnread, lastUnread);
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
