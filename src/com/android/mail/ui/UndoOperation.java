/*******************************************************************************
 *      Copyright (C) 2011 Google Inc.
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

import com.android.mail.R;
import android.content.Context;

/**
 * A simple holder class that stores the information to undo the application of a folder.
 */
public class UndoOperation {
    public int mAction;
    public int mCount;
    public boolean mBatch;


    /**
     * Create an UndoOperation
     *
     * @param count Number of conversations this undo would be applied to.
     * @param menuId res id identifying the menu item tapped; used to determine
     *            what action was performed
     */
    public UndoOperation(int count, int menuId) {
        this(count, menuId, false);
    }

    public UndoOperation(int count, int action, boolean batch) {
        mCount = count;
        mAction = action;
        mBatch = batch;
    }

    /**
     * Get a string description of the undo operation that will be performed
     * when the user taps the undo bar.
     */
    public String getDescription(Context context) {
        String desc = "";
        int resId = -1;
        switch (mAction) {
            case R.id.delete:
                resId = R.plurals.conversation_deleted;
                break;
            case R.id.change_folder:
                resId = R.plurals.conversation_folder_changed;
                break;
            case R.id.archive:
                resId = R.plurals.conversation_archived;
                break;
            case R.id.report_spam:
                resId = R.plurals.conversation_spammed;
                break;
            case R.id.mute:
                resId = R.plurals.conversation_muted;
                break;
            default:
                resId = -1;
                break;
        }
        if (resId != -1) {
            desc = String.format(
                    context.getResources().getQuantityString(resId, mCount), mCount);
        }
        return desc;
    }
}
