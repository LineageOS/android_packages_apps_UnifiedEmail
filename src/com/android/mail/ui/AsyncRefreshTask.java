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
import android.database.Cursor;
import android.database.DataSetObserver;
import android.net.Uri;
import android.os.AsyncTask;
import android.text.TextUtils;

import com.android.mail.providers.Folder;
import com.android.mail.providers.UIProvider;

public class AsyncRefreshTask extends AsyncTask<Void, Void, Void> {
    private Context mContext;
    private Folder mFolder;
    private Cursor mFolderCursor;
    private DataSetObserver mFolderObserver;

    public AsyncRefreshTask(Context context, Folder folder) {
        mContext = context;
        mFolder = folder;
        mFolderObserver = new FolderObserver();
    }

    @Override
    protected Void doInBackground(Void... voids) {
        String refreshUri = mFolder.refreshUri;
        if (!TextUtils.isEmpty(refreshUri)) {
            // Watch for changes on the folder.
            mFolderCursor = mContext.getContentResolver().query(Uri.parse(mFolder.uri),
                    UIProvider.FOLDERS_PROJECTION, null, null, null);
            mFolderCursor.registerDataSetObserver(mFolderObserver);
            // TODO: (mindyp) Start the spinner here.
            mContext.getContentResolver().query(Uri.parse(refreshUri), null, null, null, null);
        }
        return null;
    }

    private class FolderObserver extends DataSetObserver {
        /**
         * This method is called when the entire data set has changed,
         * most likely through a call to {@link Cursor#requery()} on a {@link Cursor}.
         */
        public void onChanged() {
            // TODO: (mindyp) Check the new folder status. If syncing is
            // complete, stop the spinner here.
            System.out.println("FOLDER STATUS = "); // + new Folder(mFolderCursor).status);
        }
    }
}
