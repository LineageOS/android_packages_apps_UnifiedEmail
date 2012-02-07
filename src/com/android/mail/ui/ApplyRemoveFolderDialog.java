/*******************************************************************************
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

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.database.Cursor;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;

import com.android.mail.R;
import com.android.mail.providers.Account;
import com.android.mail.providers.Conversation;
import com.android.mail.providers.UIProvider;
import com.android.mail.ui.FoldersSelectionDialog.CommitListener;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Apply labels to a conversation.
 *
 * Invoked by ConversationActivity and ConversationListActivity to display a
 * list of available labels and allow the user to add or remove them from the
 * current conversation. This class doesn't do any cursor manipulation.
 */
public class ApplyRemoveFolderDialog extends AlertDialog
        implements OnCancelListener, DialogInterface.OnClickListener {

    // All the labels available on this account
    public static final String EXTRA_ALL_LABELS = "all-labels";

    // All the labels applied to the current conversation
    public static final String EXTRA_CURRENT_LABELS = "current-labels";

    // The following two extras are set on the result and they contain
    // all the labels that were added and removed by the user on this screen.
    public static final String EXTRA_ADDED_LABELS = "added-labels";
    public static final String EXTRA_REMOVED_LABELS = "removed-labels";

    private Context mContext;
    private ListView mListView;

    private FolderSelectorAdapter mAdapter;

    private CommitListener mCommitListener;

    private ConversationsLabelHandler mLabelHandler;

    public ApplyRemoveFolderDialog(Context context, CommitListener commitListener, Account account) {
        super(context);
        mContext = context;
        setTitle("change label");
        setOnCancelListener(this);
        setButton(DialogInterface.BUTTON_POSITIVE,
                mContext.getString(android.R.string.ok), this);
        setButton(DialogInterface.BUTTON_NEGATIVE,
            mContext.getString(android.R.string.cancel), this);
        setInverseBackgroundForced(true);

        LayoutInflater inflater = LayoutInflater.from(mContext);
        mListView = (ListView) inflater.inflate(R.layout.apply_remove_folder_dialog, null);
        setView(mListView, 0, 0, 0, 0);
        mCommitListener = commitListener;
        onPrepare(account);
    }

    /**
     * Invoked before showing the dialog.  This method resets the change list and initializes
     * the list adapter to reflect the labels found on the current conversations.
     * @param account the account
     * @param currentLabels the labels on this/these conversations.
     */
    public void onPrepare(Account account) {
        Cursor folders = getContext().getContentResolver().query(Uri.parse(account.folderListUri),
                UIProvider.FOLDERS_PROJECTION, null, null, null);
        mAdapter = new FolderSelectorAdapter(getContext(), folders, new HashSet<String>());

        // Handle toggling of labels on the list item itself.
        // The clicks on the checkboxes are suppressed so that there is a consistent experience
        // regardless on whether or not the touch area was the checkbox, or elsewhere in the item.
        // This also allows the list items to be navigable and toggled using the trackball.
        mListView.setItemsCanFocus(true);
        mListView.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                onSelectLabel(position);
            }
        });

        mListView.setAdapter(mAdapter);
        mLabelHandler = new ConversationsLabelHandler(mAdapter);
    }

    @SuppressWarnings("unchecked")
    private void onSelectLabel(int position) {
        // Update the UI
        mLabelHandler.update(mAdapter.getItem(position));
    }

    /////
    // implements DialogInterface.OnClickListener
    //

    @Override
    public void onCancel(DialogInterface dialog) {
        // Nothing to do
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        // If the user clicked the OK button, apply the change list
        if (which == DialogInterface.BUTTON_POSITIVE) {
            mCommitListener.onCommit(mLabelHandler.getUris());
        }
    }
}
