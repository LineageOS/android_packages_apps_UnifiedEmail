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

import com.android.mail.providers.Folder;


/**
 * This interface is used to send notifications back to the calling
 * activity. MenuHandler takes care of updating the provider, so this
 * interface should be used for notification purposes only (such as updating
 * the UI).
 */
// Called MenuHandler.ActivityCallback in the previous code.
public interface MenuCallback extends HelpCallback {
// A Label is now a Folder, which is a list of messages.

    /**
     * Invoked whenever a label is added or removed.
     */
    void onLabelChanged(Folder label, long conversationId, boolean added);

    /**
     * Invoked once all the modifications on the labels have been performed.
     */
    void doneChangingLabels(FolderOperations labelOperations);

    /**
     * Invoked when the user requests search mode
     */
    void handleSearchRequested();

    /**
     * Invoked when a bulk Operation is starting.
     */
    void onStartBulkOperation();

    /**
     * Invoked when a bulk Operation has finished.
     */
    void onEndBulkOperation();

    /**
     * Invoked when user starts drag and drop mode.
     */
    void onStartDragMode();

    /**
     * Invoked when user stops drag and drop mode.
     */
    void onStopDragMode();
}

