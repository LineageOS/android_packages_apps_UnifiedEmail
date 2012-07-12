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

import com.android.mail.providers.Conversation;
import com.android.mail.providers.Folder;
import com.android.mail.providers.UIProvider;

import java.util.Collection;

/**
 * Classes that can update conversations implement this interface.
 */
public interface ConversationUpdater {
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
     * Requests the removal of the current conversation with the specified destructive action.
     * @param target the conversations to act upon.
     * @param action to perform after the UI has been updated to remove the conversations
     */
    void delete(final Collection<Conversation> target, final DestructiveAction action);

    /**
     * Get a destructive action for selected conversations. The action corresponds to Menu item
     * identifiers, for example R.id.unread, or R.id.delete.
     * @param action
     * @return
     */
    public DestructiveAction getBatchAction(int action);

    /**
     * Assign the target conversations to the given folders, and remove them from all other
     * folders that they might be assigned to.
     * @param folders the folders to assign the conversations to.
     * @param target the conversations to act upon.
     * @param batch whether this is a batch operation
     * @param showUndo whether to show the undo bar
     */
    public void assignFolder(Collection<Folder> folders, Collection<Conversation> target,
            boolean batch, boolean showUndo);

    /**
     * Refreshes the conversation list, if one exists.
     */
    void refreshConversationList();
}
