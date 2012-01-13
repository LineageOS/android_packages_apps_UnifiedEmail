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
 * @author viki@google.com (Vikram Aggarwal)
 *
 */
public abstract class AbstractActivityController implements MenuCallback, LayoutListener {

    @Override
    public String getHelpContext() {
        // TODO(viki): Auto-generated method stub
        return null;
    }

    @Override
    public void onConversationListVisibilityChanged(boolean visible) {
        // Activate the selected conversation action menu
        return;
    }

    /**
     * By default, doing nothing is right. A two-pane controller will need to override this.
     */
    @Override
    public void onConversationVisibilityChanged(boolean visible) {
        // Do nothing.
        return;
    }

    @Override
    public void onLabelChanged(Folder label, long conversationId, boolean added) {
        // TODO(viki): Auto-generated method stub

    }

    @Override
    public void doneChangingLabels(FolderOperations labelOperations) {
        // TODO(viki): Auto-generated method stub

    }

    @Override
    public void enterSearchMode() {
        // TODO(viki): Auto-generated method stub

    }

    @Override
    public void onStartBulkOperation() {
        // TODO(viki): Auto-generated method stub

    }

    @Override
    public void onEndBulkOperation() {
        // TODO(viki): Auto-generated method stub

    }

    @Override
    public void onStartDragMode() {
        // TODO(viki): Auto-generated method stub

    }

    @Override
    public void onStopDragMode() {
        // TODO(viki): Auto-generated method stub

    }

}
