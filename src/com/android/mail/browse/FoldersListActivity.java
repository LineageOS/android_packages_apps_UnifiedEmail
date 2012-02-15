/**
 * Copyright (c) 2012, Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.mail.browse;

import com.android.mail.R;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;

import com.android.mail.providers.Account;
import com.android.mail.providers.Folder;
import com.android.mail.providers.Message;
import com.android.mail.providers.UIProvider;
import com.android.mail.ui.FolderItemView;
import com.android.mail.ui.FolderItemView.DropHandler;
import com.android.mail.utils.Utils;

public class FoldersListActivity extends Activity {
    private ListView mListView;
    private Account mAccount;
    private Cursor mFoldersCursor;

    public static void launch(Context launcher, Account account) {
        Intent intent = new Intent(launcher, FoldersListActivity.class);
        intent.putExtra(Utils.EXTRA_ACCOUNT, account);
        launcher.startActivity(intent);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.folders_activity);
        mListView = (ListView) findViewById(R.id.folders_list);
        mAccount = (Account) getIntent().getParcelableExtra(Utils.EXTRA_ACCOUNT);
        mFoldersCursor = getContentResolver().query(Uri.parse(mAccount.folderListUri),
                UIProvider.FOLDERS_PROJECTION, null, null, null);
        mListView.setAdapter(new FolderListAdapter(this, R.layout.folder_item, mFoldersCursor,
                null, null));
    }

    private class FolderListAdapter extends SimpleCursorAdapter {

        public FolderListAdapter(Context context, int layout, Cursor c, String[] from, int[] to) {
            super(context, layout, c, new String[0], new int[0], 0);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            FolderItemView folderItemView;
            if (convertView != null) {
                folderItemView = (FolderItemView) convertView;
            } else {
                folderItemView = (FolderItemView) LayoutInflater.from(FoldersListActivity.this)
                        .inflate(R.layout.folder_item, null);
            }
            mFoldersCursor.moveToPosition(position);
            folderItemView.bind(new Folder(mFoldersCursor), null);
            return folderItemView;
        }
    }
}
