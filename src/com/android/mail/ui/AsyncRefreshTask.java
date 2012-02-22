/*******************************************************************************mFolder
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

import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.text.TextUtils;

import com.android.mail.providers.Folder;
import com.android.mail.providers.UIProvider;
import com.android.mail.providers.UIProvider.SyncStatus;
import com.android.mail.utils.LogUtils;

public class AsyncRefreshTask extends AsyncTask<Void, Void, Void> {
    private static final String LOG_TAG = new LogUtils().getLogTag();
    private Context mContext;
    private Folder mFolder;
    private Cursor mFolderCursor;
    private ContentObserver mFolderObserver;
    private final RefreshListener mRefreshListener;


    private AsyncTask<Void, Void, Void> mRefreshAsyncTask;

    public AsyncRefreshTask(Context context, Folder folder, RefreshListener listener) {
        mContext = context;
        mFolder = folder;
        mFolderObserver = new FolderObserver();
        mRefreshListener = listener;
    }

    @Override
    protected Void doInBackground(Void... voids) {
        String refreshUri = mFolder.refreshUri;
        if (!TextUtils.isEmpty(refreshUri)) {
            // Let listeners know we are kicking off a refresh.
            mRefreshListener.onRefreshStarted();
            mFolderCursor = mContext.getContentResolver().query(Uri.parse(mFolder.uri),
                    UIProvider.FOLDERS_PROJECTION, null, null, null);
            // Watch for changes on the folder.
            mFolderCursor.registerContentObserver(mFolderObserver);
            mContext.getContentResolver().query(Uri.parse(refreshUri), null, null, null, null);
        }
        return null;
    }

    private class FolderObserver extends ContentObserver {
        public FolderObserver() {
            super(null);
        }

        @Override
        public void onChange(boolean selfChange) {
            // Unregister the current listener.
            if (mFolderObserver != null) {
                mFolderCursor.unregisterContentObserver(mFolderObserver);
                mFolderObserver = null;
            }
            if (mRefreshAsyncTask != null) {
                mRefreshAsyncTask.cancel(true);
            }
            // Close the existing cursor.
            mFolderCursor.close();
            // Update the cursor.
            mRefreshAsyncTask = new AsyncTask<Void, Void, Void>() {
                protected Void doInBackground(Void... voids) {
                    mFolderCursor = mContext.getContentResolver().query(Uri.parse(mFolder.uri),
                            UIProvider.FOLDERS_PROJECTION, null, null, null);
                    mFolderCursor.moveToFirst();
                    Folder folder = new Folder(mFolderCursor);
                    switch (folder.syncStatus) {
                        case SyncStatus.NO_SYNC:
                            // Stop the spinner here. Don't add a new listener;
                            // the sync is done.
                            mRefreshListener.onRefreshStopped(folder.lastSyncResult);
                            break;
                        default:
                            // re-add the listener
                            if (mFolderObserver == null) {
                                mFolderObserver = new FolderObserver();
                            }
                            mFolderCursor.registerContentObserver(mFolderObserver);
                            break;
                    }
                    LogUtils.v(LOG_TAG, "FOLDER STATUS = " + folder.syncStatus);
                    return null;
                }
            };
            mRefreshAsyncTask.execute();
        }
    }

    /**
     * Classes which want to know about the status of a user requested
     * refresh of the current folder should implement this interface.
     */
    public interface RefreshListener {
        public void onRefreshStarted();
        public void onRefreshStopped(int status);
    }
}
