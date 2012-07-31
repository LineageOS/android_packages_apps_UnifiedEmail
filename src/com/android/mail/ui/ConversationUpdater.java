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

package com.android.mail.ui;

import android.content.ContentValues;
import android.net.Uri;

import com.android.mail.browse.ConversationCursor;
import com.android.mail.browse.MessageCursor.ConversationMessage;
import com.android.mail.providers.Conversation;
import com.android.mail.providers.ConversationInfo;
import com.android.mail.providers.UIProvider;

import java.util.Collection;
import java.util.Set;

/**
 * Classes that can update conversations implement this interface.
 */
public interface ConversationUpdater extends ConversationListCallbacks {
    /**
     * Modify the given conversation by changing the column provided here to contain the value
     * provided. Column names are listed in {@link UIProvider.ConversationColumns}, for example
     * {@link UIProvider.ConversationColumns#FOLDER_LIST}
     * @param target
     * @param columnName
     * @param value
     */
    void updateConversation(Collection <Conversation> target, String columnName, String value);

    /**
     * Modify the given conversation by changing the column provided here to contain the value
     * provided. Column names are listed in {@link UIProvider.ConversationColumns}, for example
     * {@link UIProvider.ConversationColumns#READ}
     * @param target
     * @param columnName
     * @param value
     */
    void updateConversation(Collection <Conversation> target, String columnName, int value);

    /**
     * Modify the given conversation by changing the column provided here to contain the value
     * provided. Column names are listed in {@link UIProvider.ConversationColumns}, for example
     * {@link UIProvider.ConversationColumns#HAS_ATTACHMENTS}
     * @param target
     * @param columnName
     * @param value
     */
    void updateConversation(Collection <Conversation> target, String columnName, boolean value);

    /**
     * Modify the given conversation by changing the columns provided here to
     * contain the values provided. Column names are listed in
     * {@link UIProvider.ConversationColumns}, for example
     * {@link UIProvider.ConversationColumns#HAS_ATTACHMENTS}
     * @param target
     * @param values
     */
    void updateConversation(Collection <Conversation> target, ContentValues values);

    /**
     * Requests the removal of the current conversation with the specified destructive action.
     * @param target the conversations to act upon.
     * @param action to perform after the UI has been updated to remove the conversations
     */
    void delete(final Collection<Conversation> target, final DestructiveAction action);

    /**
     * Mark a number of conversations as read or unread.
     *
     */
    void markConversationsRead(Collection<Conversation> targets, boolean read);

    /**
     * Mark a single conversation unread, either entirely or for just a subset of the messages in a
     * conversation and the view <b>returns to Conversation List</b> mode.
     *
     * @param conv conversation to mark unread
     * @param unreadMessageUris URIs for the subset of the conversation's messages to mark unread,
     * or null/empty set to mark the entire conversation unread.
     * @param originalConversationInfo the original unread state of the {@link ConversationInfo}
     * that {@link ConversationCursor} will temporarily use until the commit is complete.
     */
    void markConversationMessagesUnread(Conversation conv, Set<Uri> unreadMessageUris,
            String originalConversationInfo);

    /**
     * Star a single message within a conversation. This method requires a
     * {@link ConversationMessage} to propagate the change to the owning {@link Conversation}.
     *
     */
    void starMessage(ConversationMessage msg, boolean starred);

    /**
     * Get a destructive action for selected conversations. The action corresponds to Menu item
     * identifiers, for example R.id.unread, or R.id.delete.
     * @param action
     * @return
     */
    public DestructiveAction getBatchAction(int action);

    /**
     * Get a destructive action for selected conversations. The action
     * corresponds to Menu item identifiers, for example R.id.unread, or
     * R.id.delete. but is not automatically added to the pending actions list.
     * The caller must explicitly call performAction.
     * @param action
     * @return
     */
    public DestructiveAction getDeferredBatchAction(int action);

    /**
     * Assign the target conversations to the given folders, and remove them from all other
     * folders that they might be assigned to.
     * @param folders the folders to assign the conversations to.
     * @param target the conversations to act upon.
     * @param batch whether this is a batch operation
     * @param showUndo whether to show the undo bar
     */
    public void assignFolder(Collection<FolderOperation> folders, Collection<Conversation> target,
            boolean batch, boolean showUndo);

    /**
     * Refreshes the conversation list, if one exists.
     */
    void refreshConversationList();
}
