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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Objects;
import com.google.common.collect.Lists;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.StaticLayout;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.text.style.CharacterStyle;
import android.util.LruCache;
import android.util.Pair;
import android.widget.TextView;

import com.android.mail.R;
import com.android.mail.providers.Conversation;
import com.android.mail.providers.Folder;
import com.android.mail.providers.MessageInfo;
import com.android.mail.providers.UIProvider;

import java.util.ArrayList;

/**
 * This is the view model for the conversation header. It includes all the
 * information needed to layout a conversation header view. Each view model is
 * associated with a conversation and is cached to improve the relayout time.
 */
public class ConversationItemViewModel {
    private static final int MAX_CACHE_SIZE = 100;

    boolean faded = false;
    int fontColor;
    @VisibleForTesting
    static LruCache<Pair<String, Long>, ConversationItemViewModel> sConversationHeaderMap
        = new LruCache<Pair<String, Long>, ConversationItemViewModel>(MAX_CACHE_SIZE);

    /**
     * The Folder associated with the cache of models.
     */
    private static Folder sCachedModelsFolder;

    // The hashcode used to detect if the conversation has changed.
    private int mDataHashCode;
    private int mLayoutHashCode;

    // Unread
    boolean unread;

    // Date
    String dateText;
    Bitmap dateBackground;

    // Personal level
    Bitmap personalLevelBitmap;

    // Paperclip
    Bitmap paperclip;

    // Senders
    String sendersText;

    // A list of all the fragments that cover sendersText
    final ArrayList<SenderFragment> senderFragments;

    SpannableStringBuilder sendersDisplayText;
    StaticLayout sendersDisplayLayout;

    boolean hasDraftMessage;

    // Subject
    SpannableStringBuilder subjectText;

    StaticLayout subjectLayout;

    // View Width
    public int viewWidth;

    // Standard scaled dimen used to detect if the scale of text has changed.
    public int standardScaledDimen;

    public String fromSnippetInstructions;

    public long maxMessageId;

    public boolean checkboxVisible;

    public Conversation conversation;

    public ConversationItemView.ConversationItemFolderDisplayer folderDisplayer;

    public boolean hasBeenForwarded;

    public boolean hasBeenRepliedTo;

    public boolean isInvite;

    public StaticLayout subjectLayoutActivated;

    public SpannableStringBuilder subjectTextActivated;

    public SpannableString[] styledSenders;

    public SpannableStringBuilder styledSendersString;

    public SpannableStringBuilder messageInfoString;

    public int styledMessageInfoStringOffset;

    private String mContentDescription;

    public TextView sendersTextView;

    /**
     * Returns the view model for a conversation. If the model doesn't exist for this conversation
     * null is returned. Note: this should only be called from the UI thread.
     *
     * @param account the account contains this conversation
     * @param conversationId the Id of this conversation
     * @return the view model for this conversation, or null
     */
    @VisibleForTesting
    static ConversationItemViewModel forConversationIdOrNull(
            String account, long conversationId) {
        final Pair<String, Long> key = new Pair<String, Long>(account, conversationId);
        synchronized(sConversationHeaderMap) {
            return sConversationHeaderMap.get(key);
        }
    }

    static ConversationItemViewModel forCursor(String account, Cursor cursor) {
        return forConversation(account, new Conversation(cursor));
    }

    static ConversationItemViewModel forConversation(String account, Conversation conv) {
        ConversationItemViewModel header = ConversationItemViewModel.forConversationId(account,
                conv.id);
        if (conv != null) {
            header.faded = false;
            header.checkboxVisible = true;
            header.conversation = conv;
            header.unread = !conv.read;
            header.hasBeenForwarded =
                    (conv.convFlags & UIProvider.ConversationFlags.FORWARDED)
                    == UIProvider.ConversationFlags.FORWARDED;
            header.hasBeenRepliedTo =
                    (conv.convFlags & UIProvider.ConversationFlags.REPLIED)
                    == UIProvider.ConversationFlags.REPLIED;
            header.isInvite =
                    (conv.convFlags & UIProvider.ConversationFlags.CALENDAR_INVITE)
                    == UIProvider.ConversationFlags.CALENDAR_INVITE;
        }
        return header;
    }

    /**
     * Returns the view model for a conversation. If this is the first time
     * call, a new view model will be returned. Note: this should only be called
     * from the UI thread.
     *
     * @param account the account contains this conversation
     * @param conversationId the Id of this conversation
     * @param cursor the cursor to use in populating/ updating the model.
     * @return the view model for this conversation
     */
    static ConversationItemViewModel forConversationId(String account, long conversationId) {
        synchronized(sConversationHeaderMap) {
            ConversationItemViewModel header =
                    forConversationIdOrNull(account, conversationId);
            if (header == null) {
                final Pair<String, Long> key = new Pair<String, Long>(account, conversationId);
                header = new ConversationItemViewModel();
                sConversationHeaderMap.put(key, header);
            }
            return header;
        }
    }

    public ConversationItemViewModel() {
        senderFragments = Lists.newArrayList();
    }

    /**
     * Adds a sender fragment.
     *
     * @param start the start position of this fragment
     * @param end the start position of this fragment
     * @param style the style of this fragment
     * @param isFixed whether this fragment is fixed or not
     */
    void addSenderFragment(int start, int end, CharacterStyle style, boolean isFixed) {
        SenderFragment senderFragment = new SenderFragment(start, end, sendersText, style, isFixed);
        senderFragments.add(senderFragment);
    }

    /**
     * Clears all the current sender fragments.
     */
    void clearSenderFragments() {
        senderFragments.clear();
    }

    /**
     * Returns the hashcode to compare if the data in the header is valid.
     */
    private static int getHashCode(Context context, String dateText, Object convInfo,
            String rawFolders, boolean starred, boolean read, int priority) {
        if (dateText == null) {
            return -1;
        }
        if (TextUtils.isEmpty(rawFolders)) {
            rawFolders = "";
        }
        return Objects.hashCode(convInfo, dateText, rawFolders, starred, read, priority);
    }

    /**
     * Returns the layout hashcode to compare to see if the layout state has changed.
     */
    private int getLayoutHashCode() {
        return Objects.hashCode(mDataHashCode, viewWidth, standardScaledDimen, checkboxVisible);
    }

    private Object getConvInfo() {
        return conversation.conversationInfo != null ?
                conversation.conversationInfo :
                    TextUtils.isEmpty(fromSnippetInstructions) ? "" : fromSnippetInstructions;
    }

    /**
     * Marks this header as having valid data and layout.
     */
    void validate(Context context) {
        mDataHashCode = getHashCode(context, dateText, getConvInfo(),
                conversation.getRawFoldersString(), conversation.starred, conversation.read,
                conversation.priority);
        mLayoutHashCode = getLayoutHashCode();
    }

    /**
     * Returns if the data in this model is valid.
     */
    boolean isDataValid(Context context) {
        return mDataHashCode == getHashCode(context, dateText, getConvInfo(),
                conversation.getRawFoldersString(), conversation.starred, conversation.read,
                conversation.priority);
    }

    /**
     * Returns if the layout in this model is valid.
     */
    boolean isLayoutValid(Context context) {
        return isDataValid(context) && mLayoutHashCode == getLayoutHashCode();
    }

    /**
     * Describes the style of a Senders fragment.
     */
    static class SenderFragment {
        // Indices that determine which substring of mSendersText we are
        // displaying.
        int start;
        int end;

        // The style to apply to the TextPaint object.
        CharacterStyle style;

        // Width of the fragment.
        int width;

        // Ellipsized text.
        String ellipsizedText;

        // Whether the fragment is fixed or not.
        boolean isFixed;

        // Should the fragment be displayed or not.
        boolean shouldDisplay;

        SenderFragment(int start, int end, CharSequence sendersText, CharacterStyle style,
                boolean isFixed) {
            this.start = start;
            this.end = end;
            this.style = style;
            this.isFixed = isFixed;
        }
    }


    /**
     * Reset the content description; enough content has changed that we need to
     * regenerate it.
     */
    public void resetContentDescription() {
        mContentDescription = null;
    }

    /**
     * Get conversation information to use for accessibility.
     */
    public CharSequence getContentDescription(Context context) {
        if (mContentDescription == null) {
            // If any are unread, get the first unread sender.
            // If all are unread, get the first sender.
            // If all are read, get the last sender.
            String sender = "";
            if (conversation.conversationInfo != null) {
                String lastSender = "";
                int last = conversation.conversationInfo.messageInfos != null ?
                        conversation.conversationInfo.messageInfos.size() - 1 : -1;
                if (last != -1) {
                    lastSender = conversation.conversationInfo.messageInfos.get(last).sender;
                }
                if (conversation.read) {
                    sender = TextUtils.isEmpty(lastSender) ?
                            SendersView.getMe(context) : lastSender;
                } else {
                    MessageInfo firstUnread = null;
                    for (MessageInfo m : conversation.conversationInfo.messageInfos) {
                        if (!m.read) {
                            firstUnread = m;
                            break;
                        }
                    }
                    if (firstUnread != null) {
                        sender = TextUtils.isEmpty(firstUnread.sender) ?
                                SendersView.getMe(context) : firstUnread.sender;
                    }
                }
                if (TextUtils.isEmpty(sender)) {
                    // Just take the last sender
                    sender = lastSender;
                }
            }
            boolean isToday = DateUtils.isToday(conversation.dateMs);
            String date = DateUtils.getRelativeTimeSpanString(context, conversation.dateMs)
                    .toString();
            int res = isToday ? R.string.content_description_today : R.string.content_description;
            mContentDescription = context.getString(res, sender,
                    conversation.subject, conversation.getSnippet(), date);
        }
        return mContentDescription;
    }

    /**
     * Clear cached header model objects when the folder changes.
     */
    public static void onFolderUpdated(Folder folder) {
        Uri old = sCachedModelsFolder != null ? sCachedModelsFolder.uri : Uri.EMPTY;
        Uri newUri = folder != null ? folder.uri : Uri.EMPTY;
        if (!old.equals(newUri)) {
            sCachedModelsFolder = folder;
            sConversationHeaderMap.evictAll();
        }
    }
}