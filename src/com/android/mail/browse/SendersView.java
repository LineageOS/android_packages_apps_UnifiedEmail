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
import android.text.TextUtils;
import android.text.style.CharacterStyle;
import android.text.style.StyleSpan;
import android.text.util.Rfc822Token;
import android.text.util.Rfc822Tokenizer;
import android.util.AttributeSet;
import android.widget.TextView;

import com.android.mail.providers.Address;
import com.android.mail.providers.Conversation;

public class SendersView extends TextView {
    CharacterStyle sNormalTextStyle = new StyleSpan(Typeface.NORMAL);

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

    /**
     * Parses senders text into small fragments.
     */
    public void parseSendersFragments(ConversationItemViewModel header, boolean isUnread,
            int mode) {
        if (TextUtils.isEmpty(header.conversation.senders)) {
            return;
        }
        header.sendersText = formatSenders(header.conversation);
        header.addSenderFragment(0, header.sendersText.length(), sNormalTextStyle, true);
    }

    public String formatSenders(Conversation conversation) {
        String sendersString = conversation.senders;
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
        return TextUtils.join(Address.ADDRESS_DELIMETER + " ", namesOnly);
    }
}
