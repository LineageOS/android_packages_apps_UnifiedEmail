/*******************************************************************************
 *      Copyright (C) 2012 Google Inc.
 *      Licensed to The Android Open Source Project.
 *
 *      Licensed under the Apache License, Version 2.0 (the "License");
 *      you may not use this file except in compliance with the License.
 *      You may obtain a copy of the License at
 *
 *           http://www.apache.org/licenses/LICENSE-2.0
 *
 *      Unless required by applicable law or agreed to in writing, software
 *      distributed under the License is distributed on an "AS IS" BASIS,
 *      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *      See the License for the specific language governing permissions and
 *      limitations under the License.
 *******************************************************************************/

package com.android.mail.ui;

import com.android.mail.providers.Conversation;
import com.android.mail.providers.Folder;
import com.android.mail.ui.ViewMode.ModeChangeListener;

/**
 * A controllable activity is an Activity that has a Controller attached. This activity must be
 * able to attach the various view fragments and delegate the method calls between them.
 */
public interface ControllableActivity extends HelpCallback, RestrictedActivity,
        FolderItemView.DropHandler, UndoBarView.OnUndoCancelListener,
        UndoBarView.UndoListener {
    /**
     * Returns the ViewMode the activity is updating.
     * @see com.android.mail.ui.ViewMode
     * @return ViewMode.
     */
    ViewMode getViewMode();

    /**
     * Sets the listener for receiving ViewMode changes.
     * @param listener
     */
    void setViewModeListener(ModeChangeListener listener);

    /**
     * Removes the given listener from receiving ViewMode changes.
     * @param listener
     */
    void unsetViewModeListener(ModeChangeListener listener);

    /**
     * Returns the object that handles {@link ConversationListCallbacks} that is associated with
     * this activity.
     * @return
     */
    ConversationListCallbacks getListHandler();

    /**
     * Return the folder change listener for this activity
     * @return
     */
    FolderChangeListener getFolderChangeListener();

    /**
     * Returns whether the first conversation in the conversation list should be
     * automatically selected and shown.
     */
    boolean shouldShowFirstConversation();

    /**
     * Get the set of currently selected conversations. This method returns a non-null value.
     * In case no conversation is currently selected, it returns an empty selection set.
     * @return
     */
    ConversationSelectionSet getSelectedSet();

    /**
     * Returns the listener for folder list selection changes in the folder list
     * fragment so that activity controllers can track the last folder list
     * pushed for hierarchical folders.
     */
    FolderListFragment.FolderListSelectionListener getFolderListSelectionListener();

    void onConversationSeen(Conversation conv);

    /**
     * Get the folder currently being accessed by the activity.
     */
    Folder getCurrentFolder();

    /**
     * Returns an object that can update conversation state. Holding a reference to the
     * ConversationUpdater is safe since the ConversationUpdater is guaranteed to persist across
     * changes to the conversation cursor.
     * @return
     */
    ConversationUpdater getConversationUpdater();

    SubjectDisplayChanger getSubjectDisplayChanger();
}
