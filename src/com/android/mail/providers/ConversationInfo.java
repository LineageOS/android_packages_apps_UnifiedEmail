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
import com.google.common.base.Objects;

import java.util.ArrayList;
import java.util.regex.Pattern;

public class ConversationInfo {
    public static final String SPLITTER = "^*^";
    private static final Pattern SPLITTER_REGEX = Pattern.compile("\\^\\*\\^");
    private static final String MESSAGE_CONV_SPLIT = "^**^";
    private static final Pattern MESSAGE_CONV_SPLITTER_REGEX = Pattern.compile("\\^\\*\\*\\^");
    public static final String MESSAGE_SPLIT = "^***^";
    private static final Pattern MESSAGE_SPLITTER_REGEX = Pattern.compile("\\^\\*\\*\\*\\^");
    public static final String ESCAPE = "^";
    public static final String ESCAPE_REPLACE = "\\^\\";

    final public ArrayList<MessageInfo> messageInfos;
    public int messageCount;
    public int draftCount;
    public String firstSnippet;
    public String firstUnreadSnippet;
    public String lastSnippet;

    public ConversationInfo() {
        messageInfos = new ArrayList<MessageInfo>();
    }

    public ConversationInfo(int count, int draft, String first, String firstUnread, String last) {
        messageInfos = new ArrayList<MessageInfo>();
        set(count, draft, first, firstUnread, last);
    }

    public void set(int count, int draft, String first, String firstUnread, String last) {
        messageInfos.clear();
        messageCount = count;
        draftCount = draft;
        firstSnippet = first;
        firstUnreadSnippet = firstUnread;
        lastSnippet = last;
    }

    public void addMessage(MessageInfo info) {
        messageInfos.add(info);
    }

    public static String toString(ConversationInfo info) {
        if (info == null) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        createAsString(builder, info.messageCount, info.draftCount, info.firstSnippet,
                info.firstUnreadSnippet, info.lastSnippet);
        getMessageInfoString(info, builder);
        return builder.toString();
    }

    private static void getMessageInfoString(ConversationInfo info, StringBuilder builder) {
        // Create a string of all the messge infos
        int i = 0;
        for (MessageInfo msg : info.messageInfos) {
            builder.append(MessageInfo.toString(msg));
            if (i < info.messageInfos.size() - 1) {
                builder.append(MESSAGE_SPLIT);
            }
            i++;
        }
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
        String first = unescapeValue(split[2]);
        String firstUnread = unescapeValue(split[3]);
        String lastUnread = unescapeValue(split[4]);
        return new ConversationInfo(messageCount, draftCount, first, firstUnread, lastUnread);
    }

    public boolean markRead(boolean read) {
        boolean changed = false;
        for (MessageInfo msg : messageInfos) {
            changed |= msg.markRead(read);
        }
        if (read) {
            firstSnippet = lastSnippet;
        } else {
            firstSnippet = firstUnreadSnippet;
        }
        return changed;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(messageCount, draftCount, messageInfos, firstSnippet,
                lastSnippet, firstUnreadSnippet);
    }

    public static void createAsString(StringBuilder builder, int numMessages, int draftCount,
            String first, String firstUnread, String last) {
        builder.append(numMessages);
        builder.append(SPLITTER);
        builder.append(draftCount);
        builder.append(SPLITTER);
        builder.append(escapeValue(first));
        builder.append(SPLITTER);
        builder.append(escapeValue(firstUnread));
        builder.append(SPLITTER);
        builder.append(escapeValue(last));
        builder.append(MESSAGE_CONV_SPLIT);
    }

    static String escapeValue(String value) {
        if (value == null) {
            return "";
        }
        return value.replace(ESCAPE, ESCAPE_REPLACE);
    }

    static String unescapeValue(String escaped) {
        if (escaped == null) {
            return "";
        }
        return escaped.replace(ESCAPE_REPLACE, ESCAPE);
    }

}
