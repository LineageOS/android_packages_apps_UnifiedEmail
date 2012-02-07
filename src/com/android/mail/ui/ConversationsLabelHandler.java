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

import java.util.ArrayList;

/**
 * Utility class that handles all changes to labels associated with a set of
 * conversations.
 *
 * @author mindyp@google.com
 */
public class ConversationsLabelHandler {

    private final FolderSelectorAdapter mAdapter;

    private final ArrayList<String> mChangeList;

    public ConversationsLabelHandler(FolderSelectorAdapter adapter) {
        mAdapter = adapter;
        mChangeList = new ArrayList<String>();
    }

    /**
     * Call this to update the state of labels as a result of them being
     * selected / de-selected.
     *
     * @param row The item being updated.
     */
    public void update(FolderSelectorAdapter.FolderRow row) {
        // Update the UI
        final boolean add = !row.isPresent();
        final Folder folder = row.getFolder();

        row.setIsPresent(add);
        mAdapter.notifyDataSetChanged();

        // Update the label

        // Always add the change to our change list since this dialog could
        // be used to apply labels to several selected conversations and the
        // user might have to click on the same label (first + and then -) to
        // remove a label on the set. The previous implementation turned this
        // operation into a no-op but we can no longer do this now. The downside
        // is that we might emit label changes to the provider that cancel each
        // other out but the provider might be already smart enough not to emit
        // a no-op label change anyway.
        if (add) {
            mChangeList.add(folder.uri);
        } else {
            int pos = mChangeList.indexOf(folder.uri);
            if (pos >= 0) {
                mChangeList.remove(pos);
            }
        }
    }

    /**
     * Clear the state of the handler.
     */
    public void reset() {
        mChangeList.clear();
    }

    public String getUris() {
        StringBuilder folderUris = new StringBuilder();
        boolean first = true;
        for (String folderUri : mChangeList) {
            if (first) {
                first = false;
            } else {
                folderUris.append(',');
            }
            folderUris.append(folderUri);
        }
        return folderUris.toString();
    }
}
