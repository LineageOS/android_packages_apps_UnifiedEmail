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
import android.text.TextUtils;
import android.view.View;
import android.widget.AdapterView;

import com.android.mail.R;
import com.android.mail.providers.Account;
import com.android.mail.providers.Conversation;
import com.android.mail.providers.Folder;
import com.android.mail.providers.UIProvider;
import com.android.mail.ui.FolderSelectorAdapter.FolderRow;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.Map.Entry;

public class FoldersSelectionDialog implements OnClickListener, OnMultiChoiceClickListener {
    private AlertDialog mDialog;
    private FolderChangeCommitListener mCommitListener;
    private HashMap<Folder, Boolean> mCheckedState;
    private boolean mSingle = false;
    private FolderSelectorAdapter mAdapter;

    public interface FolderChangeCommitListener {
        /**
         * Commit the folder selection change to the underlying provider.
         * @param uris
         */
        public void onFolderChangesCommit(ArrayList<Folder> folders);
    }

    public FoldersSelectionDialog(final Context context, Account account,
            final FolderChangeCommitListener commitListener,
            Collection<Conversation> selectedConversations) {
        mCommitListener = commitListener;
        // Mapping of a folder's uri to its checked state
        mCheckedState = new HashMap<Folder, Boolean>();
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(R.string.folder_selection_dialog_title);
        builder.setPositiveButton(R.string.ok, this);
        builder.setNegativeButton(R.string.cancel, this);
        mSingle = !account
                .supportsCapability(UIProvider.AccountCapabilities.MULTIPLE_FOLDERS_PER_CONV);
        // TODO: (mindyp) make async
        Cursor foldersCursor = context.getContentResolver().query(account.folderListUri,
                UIProvider.FOLDERS_PROJECTION, null, null, null);
        try {
            HashSet<String> conversationFolders = new HashSet<String>();
            for (Conversation conversation: selectedConversations) {
                if (conversation != null && !TextUtils.isEmpty(conversation.folderList)) {
                    conversationFolders.addAll(Arrays.asList(conversation.folderList.split(",")));
                }
            }
            mAdapter = new FolderSelectorAdapter(context, foldersCursor,
                    conversationFolders, mSingle);
            builder.setAdapter(mAdapter, this);
            String folderUri;
            // Pre-load existing conversation folders.
            foldersCursor.moveToFirst();
            do {
                folderUri = foldersCursor.getString(UIProvider.FOLDER_URI_COLUMN);
                if (conversationFolders.contains(folderUri)) {
                    mCheckedState.put(new Folder(foldersCursor), true);
                }
            } while (foldersCursor.moveToNext());
        } finally {
            foldersCursor.close();
        }
        mDialog = builder.create();
    }

    public void show() {
        mDialog.show();
        mDialog.getListView().setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
               update(mAdapter.getItem(position));
            }
        });
    }

    /**
     * Call this to update the state of folders as a result of them being
     * selected / de-selected.
     *
     * @param row The item being updated.
     */
    public void update(FolderSelectorAdapter.FolderRow row) {
        // Update the UI
        boolean add = !row.isPresent();
        if (mSingle) {
            if (!add) {
                // This would remove the check on a single radio button, so just
                // return.
                return;
            }
            // Clear any other checked items.
            mAdapter.getCount();
            for (int i = 0; i < mAdapter.getCount(); i++) {
                mAdapter.getItem(i).setIsPresent(false);
            }
            mCheckedState.clear();
        }
        row.setIsPresent(add);
        mAdapter.notifyDataSetChanged();
        mCheckedState.put(row.getFolder(), add);
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        switch (which) {
            case DialogInterface.BUTTON_POSITIVE:
                ArrayList<Folder> checkedItems = new ArrayList<Folder>();
                Set<Entry<Folder, Boolean>> states = mCheckedState.entrySet();
                for (Entry<Folder, Boolean> entry : states) {
                    if (entry.getValue()) {
                        checkedItems.add(entry.getKey());
                    }
                }
                if (mCommitListener != null) {
                    mCommitListener.onFolderChangesCommit(checkedItems);
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
        FolderRow row = mAdapter.getItem(which);
        if (mSingle) {
            // Clear any other checked items.
            mCheckedState.clear();
            isChecked = true;
        }
        mCheckedState.put(row.getFolder(), isChecked);
        mDialog.getListView().setItemChecked(which, false);
    }
}
