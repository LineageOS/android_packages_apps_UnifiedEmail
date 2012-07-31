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

import java.util.regex.Pattern;

public class MessageInfo {
    public static String SENDER_LIST_TOKEN_ELIDED = " .. ";
    public boolean read;
    public boolean starred;
    public final String sender;
    public static String MSG_DIVIDER = "^****^";
    private static Pattern MSG_DIVIDER_REGEX = Pattern.compile("\\^\\*\\*\\*\\*\\^");
    public int priority;

    public MessageInfo(boolean isRead, boolean isStarred, String senderString, int p) {
        read = isRead;
        starred = isStarred;
        sender = senderString;
        priority = p;
    }

    public void markRead(boolean isRead) {
        read = isRead;
    }

    public static String toString(MessageInfo info) {
        StringBuilder builder = new StringBuilder();
        builder.append(info.read ? 1 : 0);
        builder.append(MSG_DIVIDER);
        builder.append(info.starred ? 1 : 0);
        builder.append(MSG_DIVIDER);
        builder.append(info.sender);
        builder.append(MSG_DIVIDER);
        builder.append(info.priority);
        return builder.toString();
    }

    public static MessageInfo fromString(String inString) {
        String[] split = TextUtils.split(inString, MSG_DIVIDER_REGEX);
        int read = Integer.parseInt(split[0]);
        int starred = Integer.parseInt(split[1]);
        String senders = split[2];
        int priority = Integer.parseInt(split[3]);
        return new MessageInfo(read != 0, starred != 0, senders, priority);
    }

    @Override
    public int hashCode() {
        return (read ? 1 : 0) ^ (starred ? 1 : 0) ^ sender.hashCode();
    }
}
