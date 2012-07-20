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
import android.content.res.Resources;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.CharacterStyle;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.util.Rfc822Token;
import android.text.util.Rfc822Tokenizer;
import android.util.AttributeSet;
import android.widget.TextView;

import com.android.mail.R;
import com.android.mail.providers.Address;
import com.android.mail.providers.Conversation;
import com.android.mail.providers.ConversationInfo;
import com.android.mail.providers.MessageInfo;
import com.android.mail.utils.Utils;

import java.util.regex.Pattern;

public class SendersView extends TextView {
    public static final int DEFAULT_FORMATTING = 0;
    public static final int MERGED_FORMATTING = 1;
    public static String SENDERS_VERSION_SEPARATOR = "^**^";
    CharacterStyle sNormalTextStyle = new StyleSpan(Typeface.NORMAL);
    public static Pattern SENDERS_VERSION_SEPARATOR_PATTERN = Pattern.compile("\\^\\*\\*\\^");
    private int mFormatVersion = -1;
    private ForegroundColorSpan sLightTextStyle;
    private int DRAFT_TEXT_COLOR;
    private int LIGHT_TEXT_COLOR;

    public SendersView(Context context) {
        this(context, null);
    }

    public SendersView(Context context, AttributeSet attrs) {
        this(context, attrs, -1);
    }

    public SendersView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        Resources res = context.getResources();
        LIGHT_TEXT_COLOR = res.getColor(R.color.light_text_color);
        DRAFT_TEXT_COLOR = res.getColor(R.color.drafts);
        sLightTextStyle = new ForegroundColorSpan(LIGHT_TEXT_COLOR);
    }

    public Typeface getTypeface(boolean isUnread) {
        return mFormatVersion == DEFAULT_FORMATTING ? isUnread ? Typeface.DEFAULT_BOLD
                : Typeface.DEFAULT : Typeface.DEFAULT;
    }

    public void formatSenders(ConversationItemViewModel header, boolean isUnread, int mode) {
        if (TextUtils.isEmpty(header.conversation.senders)
                && header.conversation.conversationInfo == null) {
            return;
        }
        Conversation conversation = header.conversation;
        String sendersInfo = conversation.conversationInfo != null ?
                conversation.conversationInfo.sendersInfo : header.conversation.senders;
        if (!TextUtils.isEmpty(sendersInfo)) {
            SendersInfo info = new SendersInfo(sendersInfo);
            mFormatVersion = info.version;
            switch (mFormatVersion) {
                case MERGED_FORMATTING:
                    formatMerged(header, info.text, isUnread, mode);
                    break;
                case DEFAULT_FORMATTING:
                default:
                    formatDefault(header, info.text);
                    break;
            }
        } else {
            // We have the properly formatted conversationinfo. Parse and display!
            format(header, conversation.conversationInfo);
        }
    }

    private void format(ConversationItemViewModel header, ConversationInfo conversationInfo) {
        String[] senders = new String[conversationInfo.messageCount];
        for (int i = 0; i < senders.length; i++) {
            senders[i] = parseSender(conversationInfo.messageInfos.get(i).sender);
        }
        generateSenderFragments(header, senders);
    }

    private String parseSender(String sender) {
        Rfc822Token[] senderTokens = Rfc822Tokenizer.tokenize(sender);
        String name;
        if (senderTokens != null && senderTokens.length > 0) {
            name = senderTokens[0].getName();
            if (TextUtils.isEmpty(name)) {
                name = senderTokens[0].getAddress();
            }
            return name;
        }
        return "";
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
        header.addSenderFragment(0, header.sendersText.length(), sNormalTextStyle, true);
    }

    private void formatMerged(ConversationItemViewModel header, String sendersString,
            boolean isUnread, int mode) {
        SpannableStringBuilder sendersBuilder = new SpannableStringBuilder();
        SpannableStringBuilder statusBuilder = new SpannableStringBuilder();
        Utils.getStyledSenderSnippet(getContext(), sendersString, sendersBuilder,
                statusBuilder, ConversationItemViewCoordinates.getSubjectLength(getContext(), mode,
                        header.folderDisplayer.hasVisibleFolders(),
                        header.conversation.hasAttachments), false, false, header.hasDraftMessage);
        header.sendersText = sendersBuilder.toString();

        CharacterStyle[] spans = sendersBuilder.getSpans(0, sendersBuilder.length(),
                CharacterStyle.class);
        header.clearSenderFragments();
        int lastPosition = 0;
        CharacterStyle style = sNormalTextStyle;
        if (spans != null) {
            for (CharacterStyle span : spans) {
                style = span;
                int start = sendersBuilder.getSpanStart(style);
                int end = sendersBuilder.getSpanEnd(style);
                if (start > lastPosition) {
                    header.addSenderFragment(lastPosition, start, sNormalTextStyle, false);
                }
                // From instructions won't be updated until the next sync. So we
                // have to override the text style here to be consistent with
                // the background color.
                if (isUnread) {
                    header.addSenderFragment(start, end, style, false);
                } else {
                    header.addSenderFragment(start, end, sNormalTextStyle, false);
                }
                lastPosition = end;
            }
        }
        if (lastPosition < sendersBuilder.length()) {
            style = sLightTextStyle;
            header.addSenderFragment(lastPosition, sendersBuilder.length(), style, true);
        }
        if (statusBuilder.length() > 0) {
            if (header.sendersText.length() > 0) {
                header.sendersText = header.sendersText.concat(", ");

                // Extend the last fragment to include the comma.
                int lastIndex = header.senderFragments.size() - 1;
                int start = header.senderFragments.get(lastIndex).start;
                int end = header.senderFragments.get(lastIndex).end + 2;
                style = header.senderFragments.get(lastIndex).style;

                // The new fragment is only fixed if the previous fragment
                // is fixed.
                boolean isFixed = header.senderFragments.get(lastIndex).isFixed;

                // Remove the old fragment.
                header.senderFragments.remove(lastIndex);

                // Add new fragment.
                header.addSenderFragment(start, end, style, isFixed);
            }
            int pos = header.sendersText.length();
            header.sendersText = header.sendersText.concat(statusBuilder.toString());
            header.addSenderFragment(pos, header.sendersText.length(), new ForegroundColorSpan(
                    DRAFT_TEXT_COLOR), true);
        }
    }

    public static class SendersInfo {
        public int version;
        public String text;

        public SendersInfo(String toParse) {
            if (TextUtils.isEmpty(toParse)) {
                version = 0;
                text = "";
            } else {
                String[] splits = TextUtils.split(toParse, SENDERS_VERSION_SEPARATOR_PATTERN);
                if (splits == null || splits.length < 2) {
                    version = SendersView.DEFAULT_FORMATTING;
                    text = toParse;
                } else {
                    version = Integer.parseInt(splits[0]);
                    text = splits[1];
                }
            }
        }
    }
}
