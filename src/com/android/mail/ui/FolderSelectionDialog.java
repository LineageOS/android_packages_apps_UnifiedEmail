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
import android.content.DialogInterface.OnDismissListener;
import android.os.AsyncTask;
import android.view.View;
import android.widget.AdapterView;

import com.android.mail.R;
import com.android.mail.providers.Account;
import com.android.mail.providers.Conversation;
import com.android.mail.providers.Folder;
import com.android.mail.providers.UIProvider;
import com.android.mail.utils.LogTag;
import com.android.mail.utils.LogUtils;

import java.util.Collection;

public abstract class FolderSelectionDialog implements OnClickListener, OnDismissListener {
    protected static final String LOG_TAG = LogTag.getLogTag();
    private static boolean sDialogShown;

    protected AlertDialog mDialog;
    protected ConversationUpdater mUpdater;
    protected SeparatedFolderListAdapter mAdapter;
    protected Collection<Conversation> mTarget;
    protected boolean mBatch;
    protected QueryRunner mRunner;
    protected Account mAccount;
    protected AlertDialog.Builder mBuilder;
    protected Folder mCurrentFolder;

    public static FolderSelectionDialog getInstance(final Context context, Account account,
            final ConversationUpdater updater, Collection<Conversation> target, boolean isBatch,
            Folder currentFolder, boolean isMoveTo) {
        if (sDialogShown) {
            return null;
        }

        if (isMoveTo || !account.supportsCapability(
                UIProvider.AccountCapabilities.MULTIPLE_FOLDERS_PER_CONV)) {
            return new SingleFolderSelectionDialog(
                    context, account, updater, target, isBatch, currentFolder);
        } else {
            return new MultiFoldersSelectionDialog(
                    context, account, updater, target, isBatch, currentFolder);
        }
    }

    public static void setDialogDismissed() {
        LogUtils.d(LOG_TAG, "Folder Selection dialog dismissed");
        sDialogShown = false;
    }

    protected abstract void updateAdapterInBackground(Context context);

    protected abstract void onListItemClick(int position);

    protected FolderSelectionDialog(final Context context, Account account,
            final ConversationUpdater updater, Collection<Conversation> target, boolean isBatch,
            Folder currentFolder) {
        mUpdater = updater;
        mTarget = target;
        mBatch = isBatch;

        final AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(R.string.folder_selection_dialog_title);
        builder.setPositiveButton(R.string.ok, this);
        builder.setNegativeButton(R.string.cancel, this);
        mAccount = account;
        mBuilder = builder;
        mCurrentFolder = currentFolder;
        mAdapter = new SeparatedFolderListAdapter(context);
        mRunner = new QueryRunner(context);
    }

    public void show() {
        sDialogShown = true;
        mRunner.execute();
    }

    protected void showInternal() {
        mDialog.show();
        mDialog.setOnDismissListener(this);
        mDialog.getListView().setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                onListItemClick(position);
            }
        });
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        FolderSelectionDialog.setDialogDismissed();
    }

    /**
     * Class to query the Folder list database in the background and update the
     * adapter with an open cursor.
     */
    private class QueryRunner extends AsyncTask<Void, Void, Void> {
        private final Context mContext;

        private QueryRunner(final Context context) {
            mContext = context;
        }

        @Override
        protected Void doInBackground(Void... v) {
            updateAdapterInBackground(mContext);
            return null;
        }

        @Override
        protected void onPostExecute(Void v) {
            mDialog = mBuilder.create();
            showInternal();
        }
    }
}
