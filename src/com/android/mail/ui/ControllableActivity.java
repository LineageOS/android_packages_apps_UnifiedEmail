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

import com.android.mail.ui.ViewMode.ModeChangeListener;
import com.android.mail.ui.FolderListFragment;

/**
 * A controllable activity is an Activity that has a Controller attached. This activity must be
 * able to attach the various view fragments and delegate the method calls between them.
 */
public interface ControllableActivity extends HelpCallback, RestrictedActivity,
        FolderItemView.DropHandler {
    /**
     * Attaches the conversation list fragment to the activity controller. This callback is
     * currently required because the Activity Controller directly calls methods on the conversation
     * list fragment. This behavior should be modified to allow the controller to call a layout
     * controller which knows about the fragments.
     * @param conversationList
     */
    void attachConversationList(ConversationListFragment conversationList);

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
     * Attaches a folder list fragment to the activity controller.
     * @param folderListFragment cannot be null.
     */
    void attachFolderList(FolderListFragment folderListFragment);

    /**
     * Attaches a conversation view fragment to the activity controller.
     * @param conversationViewFragment cannot be null.
     */
    void attachConversationView(ConversationViewFragment conversationViewFragment);

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

    ConversationSelectionSet getSelectedSet();

    /**
     * Returns the listener for folder list selection changes in the folder list
     * fragment so that activity controllers can track the last folder list
     * pushed for hierarchical folders.
     */
    FolderListFragment.FolderListSelectionListener getFolderListSelectionListener();

    DragListener getDragListener();
}
