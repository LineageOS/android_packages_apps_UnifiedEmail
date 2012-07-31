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
import android.text.Html;
import android.text.Spannable;
import android.text.SpannableString;
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
import com.android.mail.providers.ConversationInfo;
import com.android.mail.providers.MessageInfo;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Maps;

import java.util.ArrayList;
import java.util.Map;

import java.util.regex.Pattern;

public class SendersView extends TextView {
    public static final int DEFAULT_FORMATTING = 0;
    public static final int MERGED_FORMATTING = 1;
    private static String sSendersSplitToken;
    public static String SENDERS_VERSION_SEPARATOR = "^**^";
    CharacterStyle sNormalTextStyle = new StyleSpan(Typeface.NORMAL);
    public static Pattern SENDERS_VERSION_SEPARATOR_PATTERN = Pattern.compile("\\^\\*\\*\\^");
    private static CharSequence sDraftSingularString;
    private static CharSequence sDraftPluralString;
    private static String sDraftCountFormatString;
    private static ForegroundColorSpan sDraftsStyleSpan;
    private static StyleSpan sUnreadStyleSpan;
    private static StyleSpan sReadStyleSpan;
    private static String sMeString;
    private static CharacterStyle style;
    public static CharSequence sElidedString;
    private static Map<Integer, Integer> sPriorityToLength;

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

    private static void getSenderResources(Context context) {
        if (sDraftSingularString == null) {
            Resources res = context.getResources();
            sSendersSplitToken = res.getString(R.string.senders_split_token);
            sElidedString = res.getString(R.string.senders_elided);
            sDraftSingularString = res.getQuantityText(R.plurals.draft, 1);
            sDraftPluralString = res.getQuantityText(R.plurals.draft, 2);
            sDraftCountFormatString = res.getString(R.string.draft_count_format);
            sDraftsStyleSpan = new ForegroundColorSpan(res.getColor(R.color.drafts));
        }
    }

    public static SpannableStringBuilder createMessageInfo(Context context,
            ConversationInfo conversationInfo) {
        SpannableStringBuilder messageInfo = new SpannableStringBuilder();
        if (conversationInfo != null) {
            int count = conversationInfo.messageCount;
            int draftCount = conversationInfo.draftCount;
            if (count > 0 || draftCount <= 0) {
                messageInfo.append(" ");
            }
            if (count > 1) {
                messageInfo.append(count + "");
            }
            if (draftCount > 0) {
                messageInfo.append(sSendersSplitToken);
                SpannableStringBuilder draftString = new SpannableStringBuilder();
                if (draftCount == 1) {
                    draftString.append(sDraftSingularString);
                } else {
                    draftString.append(sDraftPluralString
                            + String.format(sDraftCountFormatString, draftCount));
                }
                draftString.setSpan(CharacterStyle.wrap(sDraftsStyleSpan), 0, draftString.length(),
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                messageInfo.append(draftString);
            }
        }
        return messageInfo;
    }

    @VisibleForTesting
    public static SpannableString[] format(Context context,
            ConversationInfo conversationInfo, String messageInfo, int maxChars) {
        getSenderResources(context);
        ArrayList<SpannableString> displays = handlePriority(context, maxChars,
                messageInfo.toString(), conversationInfo);
        return displays.toArray(new SpannableString[displays.size()]);
    }

    public static ArrayList<SpannableString> handlePriority(Context context, int maxChars,
            String messageInfoString, ConversationInfo conversationInfo) {
        int maxPriorityToInclude = -1; // inclusive
        int numCharsUsed = messageInfoString.length(); // draft, number drafts,
                                                       // count
        int numSendersUsed = 0;
        int numCharsToRemovePerWord = 0;
        int maxFoundPriority = 0;
        if (numCharsUsed > maxChars) {
            numCharsToRemovePerWord = (numCharsUsed - maxChars) / numSendersUsed;
        }
        if (sPriorityToLength == null) {
            sPriorityToLength = Maps.newHashMap();
        }
        final Map<Integer, Integer> priorityToLength = sPriorityToLength;
        priorityToLength.clear();
        int senderLength;
        for (MessageInfo info : conversationInfo.messageInfos) {
            senderLength = !TextUtils.isEmpty(info.sender) ? info.sender.length() : 0;
            priorityToLength.put(info.priority, senderLength);
            maxFoundPriority = Math.max(maxFoundPriority, info.priority);
        }
        while (maxPriorityToInclude < maxFoundPriority) {
            if (priorityToLength.containsKey(maxPriorityToInclude + 1)) {
                int length = numCharsUsed + priorityToLength.get(maxPriorityToInclude + 1);
                if (numCharsUsed > 0)
                    length += 2;
                // We must show at least two senders if they exist. If we don't
                // have space for both
                // then we will truncate names.
                if (length > maxChars && numSendersUsed >= 2) {
                    break;
                }
                numCharsUsed = length;
                numSendersUsed++;
            }
            maxPriorityToInclude++;
        }
        // We want to include this entry if
        // 1) The onlyShowUnread flags is not set
        // 2) The above flag is set, and the message is unread
        MessageInfo currentMessage;
        ArrayList<SpannableString> senders = new ArrayList<SpannableString>();
        SpannableString spannableDisplay;
        String nameString;
        boolean appendedElided = false;
        Map<String, Integer> displayHash = Maps.newHashMap();
        for (int i = 0; i < conversationInfo.messageInfos.size(); i++) {
            currentMessage = conversationInfo.messageInfos.get(i);
            nameString = !TextUtils.isEmpty(currentMessage.sender) ? currentMessage.sender : "";
            if (nameString.length() == 0) {
                nameString = getMe(context);
            } else {
                nameString = Html.fromHtml(nameString).toString();
            }
            if (numCharsToRemovePerWord != 0) {
                nameString = nameString.substring(0,
                        Math.max(nameString.length() - numCharsToRemovePerWord, 0));
            }
            final int priority = currentMessage.priority;
            if (priority <= maxPriorityToInclude) {
                spannableDisplay = new SpannableString(nameString);
                style = !currentMessage.read ? getUnreadStyleSpan() : getReadStyleSpan();
                spannableDisplay.setSpan(style, 0, spannableDisplay.length(), 0);
                if (displayHash.containsKey(currentMessage.sender)) {
                    senders.remove(displayHash.get(currentMessage.sender).intValue());
                }
                displayHash.put(currentMessage.sender, i);
                senders.add(spannableDisplay);
            } else {
                if (!appendedElided) {
                    spannableDisplay = new SpannableString(sElidedString);
                    spannableDisplay.setSpan(style, 0, spannableDisplay.length(), 0);
                    appendedElided = true;
                    senders.add(spannableDisplay);
                }
            }
        }
        return senders;
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
