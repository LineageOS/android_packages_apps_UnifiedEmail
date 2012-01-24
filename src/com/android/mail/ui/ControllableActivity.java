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

import com.android.mail.ui.ViewMode.Mode;

/**
 * A controllable activity is an Activity that has a Controller attached. This activity must be
 * able to attach the various view fragments and delegate the method calls between them.
 */
public interface ControllableActivity extends HelpCallback, RestrictedActivity {
    /**
     * Returns the conversations selected by the user for performing a batch action like archive,
     * delete, etc.
     * @return conversations selected
     */
    ConversationSelectionSet getBatchConversations();

    /**
     * Returns the mode that the activity is currently in.
     * @see com.android.mail.ui.ViewMode.Mode
     * @return the mode the activity is currently in.
     */
    Mode getViewMode();

}
