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

package com.android.mail.browse;

import android.app.Activity;
import android.app.LoaderManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Loader;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.SimpleCursorAdapter;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.TextView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemSelectedListener;

import com.android.mail.R;
import com.android.mail.browse.ConversationCursor.ConversationListener;
import com.android.mail.compose.ComposeActivity;
import com.android.mail.providers.Account;
import com.android.mail.providers.AccountCacheProvider;
import com.android.mail.providers.Conversation;
import com.android.mail.providers.UIProvider;
import com.android.mail.providers.UIProvider.ConversationColumns;
import com.android.mail.ui.ActionCompleteListener;
import com.android.mail.ui.AnimatedAdapter;
import com.android.mail.ui.AnimatedListView;
import com.android.mail.ui.ConversationSelectionSet;
import com.android.mail.ui.ConversationSetObserver;
import com.android.mail.ui.RestrictedActivity;
import com.android.mail.ui.UndoBarView;

import java.util.ArrayList;

public class ConversationListActivity extends Activity implements OnItemSelectedListener,
        OnItemClickListener, ConversationSetObserver, ConversationListener, ActionCompleteListener,
        LoaderManager.LoaderCallbacks<Cursor>, RestrictedActivity {

    private AnimatedListView mListView;
    private AnimatedAdapter mListAdapter;
    private ConversationCursor mConversationListCursor;
    private Spinner mAccountsSpinner;
    private AccountsSpinnerAdapter mAccountsAdapter;
    private ContentResolver mResolver;
    private Account mSelectedAccount;
    /** The selected conversations. */
    protected ConversationSelectionSet mBatchConversations = new ConversationSelectionSet();
    private SelectedConversationsActionMenu mSelectedConversationsActionMenu;
    private UndoBarView mUndoView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.conversation_list_activity);
        mListView = (AnimatedListView) findViewById(R.id.browse_list);
        mListView.setOnItemClickListener(this);
        mAccountsSpinner = (Spinner) findViewById(R.id.accounts_spinner);
        mResolver = getContentResolver();
        // TODO: determine if we need to create a fake cursor that contains the account list from
        // last time that the application was run.  If getting the list of accounts take a long
        // time, this would prevent an empty spinner from being shown.
        mAccountsAdapter = new AccountsSpinnerAdapter(this, null);
        mAccountsSpinner.setAdapter(mAccountsAdapter);
        mAccountsSpinner.setOnItemSelectedListener(this);
        mBatchConversations.addObserver(this);
    }
    
    @Override
    public void onResume() {
        super.onResume();
        // Prepare the loader.  Either re-connect with an existing one,
        // or start a new one.
        getLoaderManager().initLoader(0, null, this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.conversation_list_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        boolean handled = true;
        int id = item.getItemId();
        switch (id) {
            case R.id.compose:
                ComposeActivity.compose(this, mSelectedAccount);
                break;
            default:
                handled = false;
                break;
        }
        return handled;
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        // Currently this activity only creates a single loader (Account List)
        return new CursorLoader(this, AccountCacheProvider.getAccountsUri(),
                UIProvider.ACCOUNTS_PROJECTION, null, null, null);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        // Set the new data in the adapter.
        mAccountsAdapter.changeCursor(data);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        // Clear the data in the adapter.
        mAccountsAdapter.changeCursor(null);
    }

    class AccountsSpinnerAdapter extends SimpleCursorAdapter implements SpinnerAdapter {

        private LayoutInflater mLayoutInflater;

        public AccountsSpinnerAdapter(Context context, Cursor cursor) {
            super(context, android.R.layout.simple_dropdown_item_1line, cursor,
                    UIProvider.ACCOUNTS_PROJECTION, null, 0);
            mLayoutInflater = LayoutInflater.from(context);
        }

        @Override
        public View getDropDownView(int position, View convertView, ViewGroup parent) {
            return getView(position, convertView, parent);
        }

        @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent) {
            return mLayoutInflater.inflate(android.R.layout.simple_dropdown_item_1line, null);
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            int accountNameCol = cursor.getColumnIndex(UIProvider.AccountColumns.NAME);
            ((TextView) view.findViewById(android.R.id.text1)).setText(cursor
                    .getString(accountNameCol));
        }
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        // Get an account and a folder list
        Uri foldersUri = null;
        Cursor cursor = mAccountsAdapter.getCursor();
        if (cursor != null && cursor.moveToPosition(position)) {
            int uriCol = cursor.getColumnIndex(UIProvider.AccountColumns.FOLDER_LIST_URI);
            foldersUri = Uri.parse(cursor.getString(uriCol));
            mSelectedAccount =  new Account(cursor);
            cursor.close();
        }
        Uri conversationListUri = null;
        if (foldersUri != null) {
            cursor = mResolver.query(foldersUri, UIProvider.FOLDERS_PROJECTION, null, null, null);
            if (cursor != null) {
                int uriCol = cursor.getColumnIndex(UIProvider.FolderColumns.CONVERSATION_LIST_URI);
                cursor.moveToFirst();
                conversationListUri = Uri.parse(cursor.getString(uriCol));
                cursor.close();
            }
        }
        // We need to have a conversation list here...
        if (conversationListUri == null) {
            throw new IllegalStateException("No conversation list for this account");
        }
        // Create the cursor for the list using the update cache
        // Make this asynchronous
        if (mConversationListCursor != null) {
            mConversationListCursor.close();
        }
        mConversationListCursor =
                ConversationCursor.create(this, UIProvider.ConversationColumns.URI,
                        conversationListUri, UIProvider.CONVERSATION_PROJECTION, null, null, null);
        mListAdapter = new AnimatedAdapter(this, position, mConversationListCursor,
                mBatchConversations, mSelectedAccount);
        mListView.setAdapter(mListAdapter);
        mConversationListCursor.setListener(this);
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        Conversation conv = ((ConversationItemView) view).getConversation();
        ConversationViewActivity.viewConversation(this, conv, mSelectedAccount);
        // Quick and dirty flag change
        if (!conv.read) {
            conv.read = true;
            conv.updateBoolean(this, ConversationColumns.READ, true);
            // For now, update the display
            mListAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onSetEmpty() {
        mSelectedConversationsActionMenu = null;
    }

    @Override
    public void onSetPopulated(ConversationSelectionSet set) {
        mSelectedConversationsActionMenu = new SelectedConversationsActionMenu(this,
                mBatchConversations, mListAdapter, this, mSelectedAccount);
        mSelectedConversationsActionMenu.activate();
    }

    @Override
    public void onSetChanged(ConversationSelectionSet set) {
        // Do nothing, we don't care about changes to the selection set.
    }

    // ConversationListener implementation
    // Underlying provider updates, etc.

    /**
     * Called when there is new data at the underlying provider
     * refresh() here causes the new data to be retrieved asynchronously
     * NOTE: The UI needn't take any action immediately (i.e. it might wait until a more
     * convenient time to get the update from the provider)
     */
    @Override
    public void onRefreshRequired() {
        // Refresh the query in the background
        mConversationListCursor.refresh();
    }

    /**
     * Complete the cursor refresh process by syncing to the underlying cursor and redrawing
     */
    private void finishRefresh() {
        // Swap cursors
        mConversationListCursor.sync();
        // Redraw with new data
        mListAdapter.notifyDataSetChanged();
    }

    /**
     * Called when new data from the underlying provider is ready for use
     * swapCursors() causes the cursor to reflect the refreshed data
     * notifyDataSetChanged() causes the list to redraw
     * NOTE: The UI needn't take any action immediately if it's otherwise engaged (animating, for
     * example)
     */
    @Override
    public void onRefreshReady() {
        ArrayList<Integer> deletedRows = mConversationListCursor.getRefreshDeletions();
        // If we have any deletions from the server, animate them away
        if (!deletedRows.isEmpty()) {
            mListAdapter.delete(deletedRows);
        } else {
            finishRefresh();
        }
    }

    @Override
    public void onActionComplete() {
        showUndo();
    }

    private void showUndo() {
        mUndoView = (UndoBarView)findViewById(R.id.undo_view);
        mUndoView.show(true, this, "undo", mSelectedAccount, mListAdapter);
    }

    @Override
    public Context getActivityContext() {
        return this;
    }
}