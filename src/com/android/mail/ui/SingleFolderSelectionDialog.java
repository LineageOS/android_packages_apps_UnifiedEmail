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
import android.app.AlertDialog.Builder;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.database.Cursor;
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
import java.util.Collection;

/**
 * Displays a folder selection dialog for the conversation provided. It allows
 * the user to switch a conversation from one folder to another.
 */
public class SingleFolderSelectionDialog implements OnClickListener {
    private AlertDialog mDialog;
    private ConversationUpdater mUpdater;
    private SeparatedFolderListAdapter mAdapter;
    private final Collection<Conversation> mTarget;
    private boolean mBatch;
    final QueryRunner mRunner;
    private Folder mExcludeFolder;
    private Builder mBuilder;
    private Account mAccount;

    /**
     * Create a new {@link SingleFolderSelectionDialog}. It is displayed when
     * the {@link #show()} method is called.
     * @param context
     * @param account the current account
     * @param updater
     * @param target conversations that are impacted
     * @param isBatch whether the dialog is shown during Contextual Action Bar
     *            (CAB) mode
     * @param currentFolder the current folder that the
     *            {@link FolderListFragment} is showing
     */
    public SingleFolderSelectionDialog(final Context context, Account account,
            final ConversationUpdater updater, Collection<Conversation> target, boolean isBatch,
            Folder currentFolder) {
        mUpdater = updater;
        mTarget = target;
        mBatch = isBatch;
        mExcludeFolder = currentFolder;
        mBuilder = new AlertDialog.Builder(context);
        mBuilder.setTitle(R.string.folder_selection_dialog_title);
        mBuilder.setNegativeButton(R.string.cancel, this);
        mAccount = account;
        mAdapter = new SeparatedFolderListAdapter(context);
        mRunner = new QueryRunner(context);
    }

    /**
     * Class to query the Folder list database in the background and update the adapter with an
     * open cursor.
     */
    private final class QueryRunner extends AsyncTask<Void, Void, Void> {
        private final Context mContext;

        private QueryRunner(final Context context){
            mContext = context;
        }

        @Override
        protected Void doInBackground(Void... v) {
            Cursor foldersCursor = null;
            try {
                foldersCursor = mContext.getContentResolver().query(
                        !Utils.isEmpty(mAccount.fullFolderListUri) ? mAccount.fullFolderListUri
                                : mAccount.folderListUri, UIProvider.FOLDERS_PROJECTION, null,
                        null, null);
                // TODO(mindyp) : bring this back in UR8 when Email providers
                // will have divided folder sections.
                final String[] headers = mContext.getResources().getStringArray(
                        R.array.moveto_folder_sections);
                // Currently, the number of adapters are assumed to match the
                // number of headers in the string array.
                mAdapter.addSection(new SystemFolderSelectorAdapter(mContext, foldersCursor,
                        R.layout.single_folders_view, null, mExcludeFolder));

                // TODO(mindyp): we currently do not support frequently moved to
                // folders, at headers[1]; need to define what that means.*/
                mAdapter.addSection(new HierarchicalFolderSelectorAdapter(mContext,
                        AddableFolderSelectorAdapter.filterFolders(foldersCursor),
                        R.layout.single_folders_view, headers[2], mExcludeFolder));
                mBuilder.setAdapter(mAdapter, SingleFolderSelectionDialog.this);
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
     * Shows the {@link SingleFolderSelectionDialog} dialog.
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
                    final Folder folder = ((FolderRow) item).getFolder();
                    ArrayList<FolderOperation> ops = new ArrayList<FolderOperation>();
                    // Remove the current folder and add the new folder.
                    ops.add(new FolderOperation(mExcludeFolder, false));
                    ops.add(new FolderOperation(folder, true));
                    mUpdater.assignFolder(ops, mTarget, mBatch, true);
                    mDialog.dismiss();
                }
            }
        });
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        // Do nothing.
    }
}
