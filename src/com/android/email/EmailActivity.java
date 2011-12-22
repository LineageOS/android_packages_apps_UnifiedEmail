/**
 * Copyright (c) 2011, Google Inc.
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
package com.android.email;

import android.app.Activity;
import android.content.ContentProvider;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;

import com.android.email.EmailActivity.BrowseItemAdapter;
import com.android.email.browse.BrowseItemView;
import com.android.email.browse.BrowseItemViewModel;
import com.android.email.providers.UIProvider;
import com.android.email.providers.protos.mock.MockUiProvider;

public class EmailActivity extends Activity {

    private ListView mListView;
    private BrowseItemAdapter mAdapter;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.email);
        mListView = (ListView) findViewById(R.id.browse_list);
        MockUiProvider provider = new MockUiProvider();
        Cursor cursor = provider.query(MockUiProvider.getAccountsUri(),
                UIProvider.ACCOUNTS_PROJECTION, null, null, null);
        Uri foldersUri = null;
        if (cursor != null) {
            int uriCol = cursor.getColumnIndex(UIProvider.AccountColumns.FOLDER_LIST_URI);
            cursor.moveToFirst();
            foldersUri = Uri.parse(cursor.getString(uriCol));
            cursor.close();
        }
        Uri conversationListUri = null;
        if (foldersUri != null) {
            cursor = provider.query(foldersUri, UIProvider.FOLDERS_PROJECTION, null, null, null);
            if (cursor != null) {
                int uriCol = cursor.getColumnIndex(UIProvider.FolderColumns.CONVERSATION_LIST_URI);
                cursor.moveToFirst();
                conversationListUri = Uri.parse(cursor.getString(uriCol));
                cursor.close();
            }
        }
        if (conversationListUri != null) {
            cursor = provider.query(conversationListUri, UIProvider.CONVERSATION_PROJECTION, null,
                    null, null);
        }
        mAdapter = new BrowseItemAdapter(this, R.layout.browse_item_view_normal, cursor);
        mListView.setAdapter(mAdapter);
    }

    class BrowseItemAdapter extends SimpleCursorAdapter {

        public BrowseItemAdapter(Context context, int textViewResourceId, Cursor cursor) {
            super(context, textViewResourceId, cursor, UIProvider.CONVERSATION_PROJECTION, null, 0);
        }

        public View newView(Context context, Cursor cursor, ViewGroup parent) {
            BrowseItemView view = new BrowseItemView(context, "test@testaccount.com");
            return view;
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            ((BrowseItemView) view).bind(cursor, null, "test@testaccount.com", null, new ViewMode(
                    EmailActivity.this));
        }
    }
}
