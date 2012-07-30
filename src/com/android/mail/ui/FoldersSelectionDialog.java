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
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.view.View;
import android.widget.AdapterView;

import com.android.mail.R;
import com.android.mail.providers.Account;
import com.android.mail.providers.Conversation;
import com.android.mail.providers.Folder;
import com.android.mail.providers.UIProvider;
import com.android.mail.ui.FolderSelectorAdapter.FolderRow;
import com.android.mail.utils.Utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;

/**
 * Displays a folder selection dialog for the conversation provided. It allows the user to mark
 * folders to assign that conversation to.
 */
public class FoldersSelectionDialog implements OnClickListener {
    private AlertDialog mDialog;
    private ConversationUpdater mUpdater;
    private boolean mSingle = false;
    private SeparatedFolderListAdapter mAdapter;
    private final Collection<Conversation> mTarget;
    private boolean mBatch;
    private HashMap<Uri, FolderOperation> mOperations;
    final QueryRunner mRunner;

    /**
     * Create a new {@link FoldersSelectionDialog}. It is displayed when the {@link #show()} method
     * is called.
     * @param context
     * @param account the current account
     * @param updater
     * @param target conversations that are impacted
     * @param isBatch whether the dialog is shown during Contextual Action Bar (CAB) mode
     * @param currentFolder the current folder that the {@link FolderListFragment} is showing
     */
    public FoldersSelectionDialog(final Context context, Account account,
            final ConversationUpdater updater, Collection<Conversation> target, boolean isBatch,
            Folder currentFolder) {
        mUpdater = updater;
        mTarget = target;
        mBatch = isBatch;

        mOperations = new HashMap<Uri, FolderOperation>();
        final AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(R.string.folder_selection_dialog_title);
        builder.setPositiveButton(R.string.ok, this);
        builder.setNegativeButton(R.string.cancel, this);
        mSingle = !account
                .supportsCapability(UIProvider.AccountCapabilities.MULTIPLE_FOLDERS_PER_CONV);
        mAdapter = new SeparatedFolderListAdapter(context);
        mRunner = new QueryRunner(context, account, builder, currentFolder);
    }

    /**
     * Class to query the Folder list database in the background and update the adapter with an
     * open cursor.
     */
    private final class QueryRunner extends AsyncTask<Void, Void, Void> {
        private final Context mContext;
        private final Account mAccount;
        final AlertDialog.Builder mBuilder;
        private final Folder mCurrentFolder;

        private QueryRunner(final Context context, final Account account,
                final AlertDialog.Builder builder, final Folder currentFolder){
            mContext = context;
            mAccount = account;
            mBuilder = builder;
            mCurrentFolder = currentFolder;
        }

        @Override
        protected Void doInBackground(Void... v) {
            Cursor foldersCursor = null;
            try {
                foldersCursor = mContext.getContentResolver().query(
                        !Utils.isEmpty(mAccount.fullFolderListUri) ? mAccount.fullFolderListUri
                                : mAccount.folderListUri, UIProvider.FOLDERS_PROJECTION, null, null,
                                null);
                /** All the folders that this conversations is assigned to. */
                final HashSet<String> checked = new HashSet<String>();
                for (final Conversation conversation : mTarget) {
                    final ArrayList<Folder> rawFolders = conversation.getRawFolders();
                    if (conversation != null && rawFolders != null && rawFolders.size() > 0) {
                        // Parse the raw folders and get all the uris.
                        checked.addAll(Arrays.asList(Folder.getUriArray(rawFolders)));
                    } else {
                        // There are no folders for this conversation, so it must
                        // belong to the folder we are currently looking at.
                        checked.add(mCurrentFolder.uri.toString());
                    }
                }
                final String[] headers = mContext.getResources()
                        .getStringArray(R.array.moveto_folder_sections);
                // Currently, the number of adapters are assumed to match the number of headers
                // in the string array.
                mAdapter.addSection(new SystemFolderSelectorAdapter(mContext, foldersCursor,
                        checked, mSingle, null));

                // TODO(mindyp): we currently do not support frequently moved to
                // folders, at headers[1]; need to define what that means.
                mAdapter.addSection(new HierarchicalFolderSelectorAdapter(mContext, foldersCursor,
                        checked, mSingle, headers[2]));
                mBuilder.setAdapter(mAdapter, FoldersSelectionDialog.this);
            } finally {
                if (foldersCursor != null) {
                    foldersCursor.close();
                }
            }
            return null;
        }
        @Override
        protected void onPostExecute(Void v) {
            mDialog = mBuilder.create();
            showInternal();
        }
    }

    /**
     * Shows the {@link FoldersSelectionDialog} dialog.
     */
    public void show() {
        mRunner.execute();
    }

    /**
     * Shows the dialog after a database query has occurred.
     */
    private final void showInternal() {
        mDialog.show();
        mDialog.getListView().setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                final Object item = mAdapter.getItem(position);
                if (item instanceof FolderRow) {
                    update((FolderRow) item);
                }
            }
        });
    }

    /**
     * Call this to update the state of folders as a result of them being
     * selected / de-selected.
     *
     * @param row The item being updated.
     */
    private final void update(FolderSelectorAdapter.FolderRow row) {
        final boolean add = !row.isPresent();
        if (mSingle) {
            if (!add) {
                // This would remove the check on a single radio button, so just
                // return.
                return;
            }
            // Clear any other checked items.
            for (int i = 0, size = mAdapter.getCount(); i < size; i++) {
                final Object item = mAdapter.getItem(i);
                if (item instanceof FolderRow) {
                   ((FolderRow)item).setIsPresent(false);
                   final Folder folder = ((FolderRow)item).getFolder();
                   mOperations.put(folder.uri, new FolderOperation(folder, false));
                }
            }
        }
        row.setIsPresent(add);
        mAdapter.notifyDataSetChanged();
        final Folder folder = row.getFolder();
        mOperations.put(folder.uri, new FolderOperation(folder, add));
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        switch (which) {
            case DialogInterface.BUTTON_POSITIVE:
                if (mUpdater != null) {
                    mUpdater.assignFolder(mOperations.values(), mTarget, mBatch, true);
                }
                break;
            case DialogInterface.BUTTON_NEGATIVE:
                break;
            default:
                break;
        }
    }
}
