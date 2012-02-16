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

import com.android.mail.browse.ConversationItemView.StarHandler;
import com.android.mail.ui.ViewMode.ModeChangeListener;


/**
 * A controllable activity is an Activity that has a Controller attached. This activity must be
 * able to attach the various view fragments and delegate the method calls between them.
 */
public interface ControllableActivity extends HelpCallback, RestrictedActivity {
    /**
     * Attaches the conversation list fragment to the activity controller. This callback is
     * currently required because the Activity Controller directly calls methods on the conversation
     * list fragment. This behavior should be modified to allow the controller to call a layout
     * controller which knows about the fragments.
     * @param conversationList
     */
    void attachConversationList(ConversationListFragment conversationList);

    /**
     * Returns the mode that the activity is currently in.
     * @see com.android.mail.ui.ViewMode
     * @return the mode the activity is currently in.
     */
    int getViewMode();

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
     * Returns the object that handles Star events associated with this activity.
     * @return
     */
    StarHandler getStarHandler();

    void attachFolderList(FolderListFragment folderListFragment);

    void attachConversationView(ConversationViewFragment conversationViewFragment);
}
