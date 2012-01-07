/**
 * Copyright (c) 2011, Google Inc.
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
package com.android.mail;

import android.os.Bundle;

import java.util.Collection;

/**
 * A simple holder class that stores the information to undo the application of a label.
 */
public class UndoOperation {
    private static final String ACCOUNT = "undo-account";
    private static final String DESCRIPTION = "undo-description";
    private static final String CONVERSATIONS = "undo-conversations";

    public Collection<ConversationInfo> mConversations;
    public String mDescription;
    public String mAccount;

    public UndoOperation(String account, Collection<ConversationInfo> conversations,
            String description) {
        this(account, conversations, description, true /* undoAction */);
    }

    /**
     * Create an UndoOperation
     * @param account Account that this operation is associated with
     * @param conversations Collection of ConversationInfo objects that this operation
     *        should be applied to
     * @param description Desctiption text that should be shown to the user
     * @param undoAction  Boolean indicating whether the operations should be reversed
     *        in order to perform the action.  This is only false when un-marshaling a
     *        previously existing UndoOperation
     */
    private UndoOperation(String account, Collection<ConversationInfo> conversations,
            String description, boolean undoAction) {
        mAccount = account;
        mConversations = conversations;
        mDescription = description;
    }

    /**
     * Save this object into an extra object.
     */
    public void saveToExtras(Bundle extras) {
        extras.putString(ACCOUNT, mAccount);
        extras.putString(DESCRIPTION, mDescription);
        extras.putParcelableArray(CONVERSATIONS,
                mConversations.toArray(new ConversationInfo[mConversations.size()]));
    }
}
