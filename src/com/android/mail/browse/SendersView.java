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

import android.content.Context;
import android.graphics.Typeface;
import android.text.Html;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.CharacterStyle;
import android.text.style.StyleSpan;
import android.text.util.Rfc822Token;
import android.text.util.Rfc822Tokenizer;
import android.util.AttributeSet;
import android.widget.TextView;

import com.android.mail.R;
import com.android.mail.providers.Address;
import com.android.mail.providers.ConversationInfo;
import com.android.mail.providers.MessageInfo;
import com.google.common.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.HashMap;

public class SendersView extends TextView {
    private static StyleSpan sUnreadStyleSpan;
    private static StyleSpan sReadStyleSpan;
    private static String sMeString;

    public SendersView(Context context) {
        this(context, null);
    }

    public SendersView(Context context, AttributeSet attrs) {
        this(context, attrs, -1);
    }

    public SendersView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public Typeface getTypeface(boolean isUnread) {
        return isUnread ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT;
    }

    public void formatSenders(ConversationItemViewModel header, boolean isUnread, int mode) {
        String senders = header.conversation.senders;
        if (TextUtils.isEmpty(senders)) {
            return;
        }
        formatDefault(header, senders);
    }

    @VisibleForTesting
    public static SpannableString[] format(Context context, ConversationInfo conversationInfo) {
        HashMap<String, Integer> displayHash = new HashMap<String, Integer>();
        ArrayList<SpannableString> displays = new ArrayList<SpannableString>();
        String display;
        SpannableString spannableDisplay;
        String sender;
        CharacterStyle style;
        MessageInfo currentMessage;
        for (int i = 0; i < conversationInfo.messageInfos.size(); i++) {
            currentMessage = conversationInfo.messageInfos.get(i);
            sender = currentMessage.sender;
            if (TextUtils.isEmpty(sender)) {
                sender = getMe(context);
            } else {
                sender = Html.fromHtml(sender).toString();
            }
            display = parseSender(sender);
            spannableDisplay = new SpannableString(display);
            style = !currentMessage.read ? getUnreadStyleSpan() : getReadStyleSpan();
            spannableDisplay.setSpan(style, 0, spannableDisplay.length(), 0);
            if (displayHash.containsKey(display)) {
                displays.remove(displayHash.get(display).intValue());
            }
            displayHash.put(display, i);
            displays.add(spannableDisplay);
        }
        return displays.toArray(new SpannableString[displays.size()]);
    }

    private static CharacterStyle getUnreadStyleSpan() {
        if (sUnreadStyleSpan == null) {
            sUnreadStyleSpan = new StyleSpan(Typeface.BOLD);
        }
        return CharacterStyle.wrap(sUnreadStyleSpan);
    }

    private static CharacterStyle getReadStyleSpan() {
        if (sReadStyleSpan == null) {
            sReadStyleSpan = new StyleSpan(Typeface.NORMAL);
        }
        return CharacterStyle.wrap(sReadStyleSpan);
    }

    private static String getMe(Context context) {
        if (sMeString == null) {
            sMeString = context.getResources().getString(R.string.me);
        }
        return sMeString;
    }

    private static String parseSender(String sender) {
        Rfc822Token[] senderTokens = Rfc822Tokenizer.tokenize(sender);
        String name;
        if (senderTokens != null && senderTokens.length > 0) {
            name = senderTokens[0].getName();
            if (TextUtils.isEmpty(name)) {
                name = senderTokens[0].getAddress();
            }
            return name;
        }
        return sender;
    }

    private void formatDefault(ConversationItemViewModel header, String sendersString) {
        String[] senders = TextUtils.split(sendersString, Address.ADDRESS_DELIMETER);
        String[] namesOnly = new String[senders.length];
        Rfc822Token[] senderTokens;
        String display;
        for (int i = 0; i < senders.length; i++) {
            senderTokens = Rfc822Tokenizer.tokenize(senders[i]);
            if (senderTokens != null && senderTokens.length > 0) {
                display = senderTokens[0].getName();
                if (TextUtils.isEmpty(display)) {
                    display = senderTokens[0].getAddress();
                }
                namesOnly[i] = display;
            }
        }
        generateSenderFragments(header, namesOnly);
    }

    private void generateSenderFragments(ConversationItemViewModel header, String[] names) {
        header.sendersText = TextUtils.join(Address.ADDRESS_DELIMETER + " ", names);
        header.addSenderFragment(0, header.sendersText.length(), getReadStyleSpan(), true);
    }
}
