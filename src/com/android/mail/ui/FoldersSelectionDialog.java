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

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface.OnMultiChoiceClickListener;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.provider.BaseColumns;

import com.android.mail.R;
import com.android.mail.providers.Account;
import com.android.mail.providers.UIProvider;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;
import java.util.Map.Entry;

public class FoldersSelectionDialog implements OnClickListener, OnMultiChoiceClickListener {
    private static final String CHECKED_COLUMN_NAME = "checked";
    // We only need _id because MatrixCursor insists
    private static final String[] FOLDER_DIALOG_PROJECTION = new String[] {
            BaseColumns._ID, UIProvider.FolderColumns.URI, UIProvider.FolderColumns.NAME,
            CHECKED_COLUMN_NAME
    };
    private static final int FOLDERS_CURSOR_ID = 0;
    private static final int FOLDERS_CURSOR_URI = 1;
    private static final int FOLDERS_CURSOR_NAME = 2;
    private static final int FOLDERS_CURSOR_CHECKED = 3;

    private int mCheckedItem;
    private AlertDialog mDialog;
    private CommitListener mCommitListener;
    private HashMap<String, Boolean> mCheckedState;
    private MatrixCursor mFolderDialogCursor;
    private boolean mSingle = false;

    public interface CommitListener {
        public void onCommit(String uris);
    }

    public FoldersSelectionDialog(final Context context, Account account,
            final CommitListener commitListener) {
        mCommitListener = commitListener;
        // Mapping of a folder's uri to its checked state
        mCheckedState = new HashMap<String, Boolean>();
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Change folders");
        builder.setPositiveButton(R.string.ok, this);
        builder.setNegativeButton(R.string.cancel, this);

        // Get all of our folders
        // TODO: Should only be folders that allow messages to be moved there!!
        Cursor foldersCursor = context.getContentResolver().query(Uri.parse(account.folderListUri),
                UIProvider.FOLDERS_PROJECTION, null, null, null);
        // Get the id, name, and a placeholder for check information
        Object[] columnValues = new Object[FOLDER_DIALOG_PROJECTION.length];
        mFolderDialogCursor = new MatrixCursor(FOLDER_DIALOG_PROJECTION);
        int i = 0;
        while (foldersCursor.moveToNext()) {
            int flags = foldersCursor.getInt(UIProvider.FOLDER_CAPABILITIES_COLUMN);
            if ((flags & UIProvider.FolderCapabilities.CAN_ACCEPT_MOVED_MESSAGES) == 0) {
                continue;
            }
            String uri = foldersCursor.getString(UIProvider.FOLDER_URI_COLUMN);
            columnValues[FOLDERS_CURSOR_ID] = i++;
            columnValues[FOLDERS_CURSOR_URI] = uri;
            columnValues[FOLDERS_CURSOR_NAME] = foldersCursor
                    .getString(UIProvider.FOLDER_NAME_COLUMN);
            columnValues[FOLDERS_CURSOR_CHECKED] = 0; // 0 = unchecked
            mFolderDialogCursor.addRow(columnValues);
            mCheckedState.put(uri, false);
        }
        foldersCursor.close();

        if (!account.supportsCapability(UIProvider.AccountCapabilities.MULTIPLE_FOLDERS_PER_CONV)) {
            mSingle = true;
            builder.setSingleChoiceItems(mFolderDialogCursor, mCheckedItem,
                    UIProvider.FolderColumns.NAME, this);
        } else {
            builder.setMultiChoiceItems(mFolderDialogCursor, CHECKED_COLUMN_NAME,
                    UIProvider.FolderColumns.NAME, this);
        }
        mDialog = builder.create();
    }

    public void show() {
        mDialog.show();
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        switch (which) {
            case DialogInterface.BUTTON_POSITIVE:
                ArrayList<String> checkedItems = new ArrayList<String>();
                Set<Entry<String, Boolean>> states = mCheckedState.entrySet();
                for (Entry<String, Boolean> entry : states) {
                    if (entry.getValue()) {
                        checkedItems.add(entry.getKey());
                    }
                }
                StringBuilder folderUris = new StringBuilder();
                boolean first = true;
                for (String folderUri : checkedItems) {
                    if (first) {
                        first = false;
                    } else {
                        folderUris.append(',');
                    }
                    folderUris.append(folderUri);
                }
                if (mCommitListener != null) {
                    mCommitListener.onCommit(folderUris.toString());
                }
                break;
            case DialogInterface.BUTTON_NEGATIVE:
                break;
            default:
                onClick(dialog, which, true);
                break;
        }
    }

    @Override
    public void onClick(DialogInterface dialog, int which, boolean isChecked) {
        mFolderDialogCursor.moveToPosition(which);
        if (mSingle) {
            mCheckedState.clear();
            mCheckedState.put(mFolderDialogCursor.getString(FOLDERS_CURSOR_URI), true);
        } else {
            mCheckedState.put(mFolderDialogCursor.getString(FOLDERS_CURSOR_URI), isChecked);
        }
    }
}
