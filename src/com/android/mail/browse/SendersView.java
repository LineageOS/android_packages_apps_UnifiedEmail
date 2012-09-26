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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.CharacterStyle;
import android.text.style.TextAppearanceSpan;
import android.text.util.Rfc822Token;
import android.text.util.Rfc822Tokenizer;

import com.android.mail.R;
import com.android.mail.providers.Address;
import com.android.mail.providers.Conversation;
import com.android.mail.providers.ConversationInfo;
import com.android.mail.providers.MessageInfo;
import com.android.mail.providers.UIProvider;
import com.android.mail.utils.Utils;
import com.google.android.common.html.parser.HtmlParser;
import com.google.android.common.html.parser.HtmlTreeBuilder;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Maps;

import java.util.ArrayList;
import java.util.Map;

import java.util.regex.Pattern;

public class SendersView {
    public static final int DEFAULT_FORMATTING = 0;
    public static final int MERGED_FORMATTING = 1;
    private static final Integer DOES_NOT_EXIST = -5;
    private static String sSendersSplitToken;
    public static String SENDERS_VERSION_SEPARATOR = "^**^";
    public static Pattern SENDERS_VERSION_SEPARATOR_PATTERN = Pattern.compile("\\^\\*\\*\\^");
    private static CharSequence sDraftSingularString;
    private static CharSequence sDraftPluralString;
    private static CharSequence sSendingString;
    private static String sDraftCountFormatString;
    private static CharacterStyle sMessageInfoStyleSpan;
    private static CharacterStyle sDraftsStyleSpan;
    private static CharacterStyle sSendingStyleSpan;
    private static CharacterStyle sUnreadStyleSpan;
    private static CharacterStyle sReadStyleSpan;
    private static String sMeString;
    private static String sMessageCountSpacerString;
    public static CharSequence sElidedString;
    private static Map<Integer, Integer> sPriorityToLength;
    private static BroadcastReceiver sConfigurationChangedReceiver;
    private static HtmlParser sHtmlParser;
    private static HtmlTreeBuilder sHtmlBuilder;

    public static Typeface getTypeface(boolean isUnread) {
        return isUnread ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT;
    }

    private static void getSenderResources(Context context) {
        if (sConfigurationChangedReceiver == null) {
            sConfigurationChangedReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    sDraftSingularString = null;
                    getSenderResources(context);
                }
            };
            context.registerReceiver(sConfigurationChangedReceiver, new IntentFilter(
                    Intent.ACTION_CONFIGURATION_CHANGED));
        }
        if (sDraftSingularString == null) {
            Resources res = context.getResources();
            sSendersSplitToken = res.getString(R.string.senders_split_token);
            sElidedString = res.getString(R.string.senders_elided);
            sDraftSingularString = res.getQuantityText(R.plurals.draft, 1);
            sDraftPluralString = res.getQuantityText(R.plurals.draft, 2);
            sDraftCountFormatString = res.getString(R.string.draft_count_format);
            sMessageInfoStyleSpan = new TextAppearanceSpan(context,
                    R.style.MessageInfoTextAppearance);
            sDraftsStyleSpan = new TextAppearanceSpan(context, R.style.DraftTextAppearance);
            sUnreadStyleSpan = new TextAppearanceSpan(context,
                    R.style.SendersUnreadTextAppearance);
            sSendingStyleSpan = new TextAppearanceSpan(context, R.style.SendingTextAppearance);
            sReadStyleSpan = new TextAppearanceSpan(context, R.style.SendersReadTextAppearance);
            sMessageCountSpacerString = res.getString(R.string.message_count_spacer);
            sSendingString = res.getString(R.string.sending);
        }
    }

    public static SpannableStringBuilder createMessageInfo(Context context, Conversation conv) {
        ConversationInfo conversationInfo = conv.conversationInfo;
        int sendingStatus = conv.sendingState;
        SpannableStringBuilder messageInfo = new SpannableStringBuilder();
        boolean hasSenders = false;
        // This covers the case where the sender is "me" and this is a draft
        // message, which means this will only run once most of the time.
        for (MessageInfo m : conversationInfo.messageInfos) {
            if (!TextUtils.isEmpty(m.sender)) {
                hasSenders = true;
                break;
            }
        }
        getSenderResources(context);
        if (conversationInfo != null) {
            int count = conversationInfo.messageCount;
            int draftCount = conversationInfo.draftCount;
            boolean showSending = sendingStatus == UIProvider.ConversationSendingState.SENDING;
            if (count > 1) {
                messageInfo.append(count + "");
            }
            messageInfo.setSpan(CharacterStyle.wrap(sMessageInfoStyleSpan), 0,
                    messageInfo.length(), 0);
            if (draftCount > 0) {
                // If we are showing a message count or any draft text and there
                // is at least 1 sender, prepend the sending state text with a
                // comma.
                if (hasSenders || count > 1) {
                    messageInfo.append(sSendersSplitToken);
                }
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
            if (showSending) {
                // If we are showing a message count or any draft text, prepend
                // the sending state text with a comma.
                if (count > 1 || draftCount > 0) {
                    messageInfo.append(sSendersSplitToken);
                }
                SpannableStringBuilder sending = new SpannableStringBuilder();
                sending.append(sSendingString);
                sending.setSpan(sSendingStyleSpan, 0, sending.length(), 0);
                messageInfo.append(sending);
            }
            // Prepend a space if we are showing other message info text.
            if (count > 1 || (draftCount > 0 && hasSenders) || showSending) {
                messageInfo = new SpannableStringBuilder(sMessageCountSpacerString)
                        .append(messageInfo);
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
            numCharsToRemovePerWord = numCharsUsed - maxChars;
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
        CharacterStyle style;
        boolean appendedElided = false;
        Map<String, Integer> displayHash = Maps.newHashMap();
        for (int i = 0; i < conversationInfo.messageInfos.size(); i++) {
            currentMessage = conversationInfo.messageInfos.get(i);
            nameString = !TextUtils.isEmpty(currentMessage.sender) ? currentMessage.sender : "";
            if (nameString.length() == 0) {
                nameString = getMe(context);
            } else {
                nameString = Utils.convertHtmlToPlainText(nameString, getParser(), getBuilder());
            }
            if (numCharsToRemovePerWord != 0) {
                nameString = nameString.substring(0,
                        Math.max(nameString.length() - numCharsToRemovePerWord, 0));
            }
            final int priority = currentMessage.priority;
            style = !currentMessage.read ? getUnreadStyleSpan() : getReadStyleSpan();
            if (priority <= maxPriorityToInclude) {
                spannableDisplay = new SpannableString(nameString);
                // Don't duplicate senders; leave the first instance, unless the
                // current instance is also unread.
                int oldPos = displayHash.containsKey(currentMessage.sender) ? displayHash
                        .get(currentMessage.sender) : DOES_NOT_EXIST;
                // If this sender doesn't exist OR the current message is
                // unread, add the sender.
                if (oldPos == DOES_NOT_EXIST || !currentMessage.read) {
                    // If the sender entry already existed, and is right next to the
                    // current sender, remove the old entry.
                    if (oldPos != DOES_NOT_EXIST && i > 0 && oldPos == i - 1
                            && oldPos < senders.size()) {
                        // Remove the old one!
                        senders.set(oldPos, null);
                    }
                    displayHash.put(currentMessage.sender, i);
                    spannableDisplay.setSpan(style, 0, spannableDisplay.length(), 0);
                    senders.add(spannableDisplay);
                }
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

    private static HtmlTreeBuilder getBuilder() {
        if (sHtmlBuilder == null) {
            sHtmlBuilder = new HtmlTreeBuilder();
        }
        return sHtmlBuilder;
    }

    private static HtmlParser getParser() {
        if (sHtmlParser == null) {
            sHtmlParser = new HtmlParser();
        }
        return sHtmlParser;
    }

    private static CharacterStyle getUnreadStyleSpan() {
        return CharacterStyle.wrap(sUnreadStyleSpan);
    }

    private static CharacterStyle getReadStyleSpan() {
        return CharacterStyle.wrap(sReadStyleSpan);
    }

    static String getMe(Context context) {
        if (sMeString == null) {
            sMeString = context.getResources().getString(R.string.me);
        }
        return sMeString;
    }

    private static void formatDefault(ConversationItemViewModel header, String sendersString,
            Context context) {
        getSenderResources(context);
        // Clear any existing sender fragments; we must re-make all of them.
        header.senderFragments.clear();
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

    private static void generateSenderFragments(ConversationItemViewModel header, String[] names) {
        header.sendersText = TextUtils.join(Address.ADDRESS_DELIMETER + " ", names);
        header.addSenderFragment(0, header.sendersText.length(), getReadStyleSpan(), true);
    }

    public static void formatSenders(ConversationItemViewModel header, Context context) {
        formatDefault(header, header.conversation.senders, context);
    }
}
