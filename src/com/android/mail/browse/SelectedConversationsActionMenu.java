/*
 * Copyright (C) 2010 Google Inc.
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

import com.android.mail.R;
import com.android.mail.providers.Account;
import com.android.mail.providers.Conversation;
import com.android.mail.providers.UIProvider;
import com.android.mail.providers.UIProvider.ConversationColumns;
import com.android.mail.ui.AnimatedAdapter;
import com.android.mail.ui.ActionCompleteListener;
import com.android.mail.ui.ConversationSelectionSet;
import com.android.mail.ui.ConversationSetObserver;
import com.android.mail.utils.LogUtils;
import com.google.common.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.Set;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.provider.BaseColumns;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;

/**
 * A component that displays a custom view for an {@code ActionBar}'s {@code
 * ContextMode} specific to operating on a set of conversations.
 */
public class SelectedConversationsActionMenu implements ActionMode.Callback,
        ConversationSetObserver, ActionCompleteListener {

    private static final String LOG_TAG = new LogUtils().getLogTag();

    /**
     * The set of conversations to display the menu for.
     */
    protected final ConversationSelectionSet mSelectionSet;
    /**
     * The set of conversations to marked for deletion
     */
    protected Collection<Conversation> mDeletionSet;
    /**
     * The new folder list (after selection)
     */
    protected String mFolderChangeList;

    private final Activity mActivity;

    private final Context mContext;

    @VisibleForTesting
    ActionMode mActionMode;

    private boolean mActivated = false;

    private Menu mMenu;

    private AnimatedAdapter mListAdapter;

    private ActionCompleteListener mActionCompleteListener;

    private Account mAccount;

    protected int mCheckedItem = 0;

    public SelectedConversationsActionMenu(Activity activity,
            ConversationSelectionSet selectionSet, AnimatedAdapter adapter,
            ActionCompleteListener listener, Account account) {
        mSelectionSet = selectionSet;
        mActivity = activity;
        mContext = mActivity;
        mListAdapter = adapter;
        mActionCompleteListener = listener;
        mAccount = account;
    }

    @Override
    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
        boolean handled = true;
        Collection<Conversation> conversations = mSelectionSet.values();
        switch (item.getItemId()) {
            case R.id.delete:
                mListAdapter.delete(conversations, this);
                break;
            case R.id.read_unread:
                // TODO: Interpret properly (rather than as "mark read")
                Conversation.updateBoolean(mActivity, conversations, ConversationColumns.READ,
                        true);
                mSelectionSet.clear();
                // Redraw with changes
                mListAdapter.notifyDataSetChanged();
                break;
            case R.id.star:
                if (conversations.size() > 0) {
                     Conversation.updateBoolean(mActivity, conversations,
                             ConversationColumns.STARRED, true);
                }
                mSelectionSet.clear();
                // Redraw with changes
                mListAdapter.notifyDataSetChanged();
                break;
            case R.id.change_folder:
                showChangeFoldersDialog();
                break;
            default:
                handled = false;
                break;
        }
        return handled;
    }

    private static final String CHECKED_COLUMN_NAME = "checked";
    // We only need _id because MatrixCursor insists
    private static final String[] FOLDER_DIALOG_PROJECTION = new String[] {
            BaseColumns._ID, UIProvider.FolderColumns.URI, UIProvider.FolderColumns.NAME,
            CHECKED_COLUMN_NAME
    };
    private static final int FOLDERS_CURSOR_ID = 0;
    private static final int FOLDERS_CURSOR_URI = 1;
    private static final int FOLDERS_CURSOR_NAME = 2;
    private static final int FOLDERS_CURSOR_CHECKED = 3;

    private void showChangeFoldersDialog() {
        // Mapping of a folder's uri to its checked state
        final HashMap<String, Boolean> checkedState = new HashMap<String, Boolean>();
        AlertDialog.Builder builder = new AlertDialog.Builder(mActivity);
        builder.setTitle("Change folders");
        DialogInterface.OnClickListener buttonListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                ArrayList<String> checkedItems = new ArrayList<String>();
                Set<Entry<String, Boolean>> states = checkedState.entrySet();
                for (Entry<String, Boolean> entry : states) {
                    if (entry.getValue()) {
                        checkedItems.add(entry.getKey());
                    }
                }
                StringBuilder folderUris = new StringBuilder();
                boolean first = true;
                for (String folderUri : checkedItems) {
                    if (first) {
                        first = false;
                    } else {
                        folderUris.append(',');
                    }
                    folderUris.append(folderUri);
                }
                mFolderChangeList = folderUris.toString();
                // TODO: Make user-friendly toast
                Toast.makeText(mActivity, mFolderChangeList, Toast.LENGTH_LONG).show();
                // Do the change here...
                final Collection<Conversation> conversations = mSelectionSet.values();
                // Indicate delete on update (i.e. no longer in this folder)
                mDeletionSet = new ArrayList<Conversation>();
                for (Conversation conv: conversations) {
                    conv.localDeleteOnUpdate = true;
                    // For Gmail, add...  if (noLongerInList(conv))...
                    mDeletionSet.add(conv);
                }
                // Delete the local delete items (all for now) and when done, update...
                mListAdapter.delete(mDeletionSet, mFolderChangeListener);
            }
        };
        builder.setPositiveButton(R.string.ok, buttonListener);
        builder.setNegativeButton(R.string.cancel, buttonListener);

        // Get all of our folders
        // TODO: Should only be folders that allow messages to be moved there!!
        Cursor foldersCursor = mActivity.getContentResolver().query(
                Uri.parse(mAccount.folderListUri), UIProvider.FOLDERS_PROJECTION, null, null, null);
        // Get the id, name, and a placeholder for check information
        Object[] columnValues = new Object[FOLDER_DIALOG_PROJECTION.length];
        final MatrixCursor folderDialogCursor = new MatrixCursor(FOLDER_DIALOG_PROJECTION);
        int i = 0;
        while (foldersCursor.moveToNext()) {
            int flags = foldersCursor.getInt(UIProvider.FOLDER_CAPABILITIES_COLUMN);
            if ((flags & UIProvider.FolderCapabilities.CAN_ACCEPT_MOVED_MESSAGES) == 0) {
                continue;
            }
            String uri = foldersCursor.getString(UIProvider.FOLDER_URI_COLUMN);
            columnValues[FOLDERS_CURSOR_ID] = i++;
            columnValues[FOLDERS_CURSOR_URI] = uri;
            columnValues[FOLDERS_CURSOR_NAME] =
                    foldersCursor.getString(UIProvider.FOLDER_NAME_COLUMN);
            columnValues[FOLDERS_CURSOR_CHECKED] = 0;  // 0 = unchecked
            folderDialogCursor.addRow(columnValues);
            checkedState.put(uri, false);
        }
        foldersCursor.close();

        if (!mAccount.supportsCapability(
                UIProvider.AccountCapabilities.MULTIPLE_FOLDERS_PER_CONV)) {
            DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    checkedState.clear();
                    folderDialogCursor.moveToPosition(which);
                    checkedState.put(folderDialogCursor.getString(FOLDERS_CURSOR_URI), true);
                }
            };
            builder.setSingleChoiceItems(folderDialogCursor, mCheckedItem,
                    UIProvider.FolderColumns.NAME, listener);
        } else {
            builder.setMultiChoiceItems(folderDialogCursor, CHECKED_COLUMN_NAME,
                    UIProvider.FolderColumns.NAME,
                    new DialogInterface.OnMultiChoiceClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                            folderDialogCursor.moveToPosition(which);
                            checkedState.put(
                                    folderDialogCursor.getString(FOLDERS_CURSOR_URI), isChecked);
                        }
                    });
        }
        AlertDialog alert = builder.create();
        alert.show();
    }

    private ActionCompleteListener mFolderChangeListener = new ActionCompleteListener() {
        @Override
        public void onActionComplete() {
            mActionCompleteListener.onActionComplete();
            Conversation.updateString(mActivity, mSelectionSet.values(),
                    ConversationColumns.FOLDER_LIST, mFolderChangeList);
            mSelectionSet.clear();
            mListAdapter.notifyDataSetChanged();
        }};

    @Override
    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
        mSelectionSet.addObserver(this);
        MenuInflater inflater = mActivity.getMenuInflater();
        inflater.inflate(R.menu.conversation_list_selection_actions_menu, menu);
        mActionMode = mode;
        mMenu = menu;
        updateCount();
        return true;
    }

    @Override
    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
        return true;
    }

    public void onPrepareActionMode() {
        if (mActionMode != null) {
            onPrepareActionMode(mActionMode, mActionMode.getMenu());
        }
    }

    @Override
    public void onDestroyActionMode(ActionMode mode) {
        mActionMode = null;
        // The action mode may have been destroyed due to this menu being deactivated, in which
        // case resources need not be cleaned up. However, if it was destroyed while this menu is
        // active, that implies the user hit "Done" in the top right, and resources need cleaning.
        if (mActivated) {
            destroy();

            if (mSelectionSet.size() > 0) {
                // If we are destroying the menu, when there is a selection, clear the
                // set of conversations
                LogUtils.e(LOG_TAG,
                        "Destroying action menu, with non-empty conversation set. Count: %d",
                        mSelectionSet.size());
                mSelectionSet.clear();
            }
        }
        mMenu = null;
    }

    @Override
    public void onSetPopulated(ConversationSelectionSet set) {
        // Noop. This object can only exist while the set is non-empty.
    }

    @Override
    public void onSetEmpty() {
        destroy();
    }

    @Override
    public void onSetChanged(ConversationSelectionSet set) {
        // If the set is empty, the menu buttons are invalid and most like the menu will be cleaned
        // up. Avoid making any changes to stop flickering ("Add Star" -> "Remove Star") just
        // before hiding the menu.
        if (set.isEmpty()) {
            return;
        }

        updateCount();
    }

    /**
     * Updates the visible count of how many conversations are selected.
     */
    private void updateCount() {
        if (mActionMode != null) {
            mActionMode.setTitle(mContext.getString(R.string.num_selected, mSelectionSet.size()));
        }
    }

    /**
     * Activates and shows this menu (essentially starting an {@link ActionMode}).
     */
    public void activate() {
        mActivated = true;
        if (mActionMode == null) {
            mActivity.startActionMode(this);
        }
    }

    /**
     * De-activates and hides the menu (essentially disabling the {@link ActionMode}), but maintains
     * the selection conversation set, and internally updates state as necessary.
     */
    public void deactivate() {
        if (mActionMode != null) {
            mActivated = false;
            mActionMode.finish();
        }
    }

    @VisibleForTesting
    public boolean isActivated() {
        return mActivated;
    }

    /**
     * Destroys and cleans up the resources associated with this menu.
     */
    public void destroy() {
        deactivate();
        mSelectionSet.removeObserver(this);
        mSelectionSet.clear();
    }

    /**
     * Disable the selected conversations menu item associated with a command
     * id.
     */
    public void disableCommand(int id) {
        enableMenuItem(id, false);
    }

    /**
     * Enable the selected conversations menu item associated with a command
     * id.
     */
    public void enableCommand(int id) {
        enableMenuItem(id, true);
    }

    private void enableMenuItem(int id, boolean enable) {
        if (mActivated) {
            MenuItem item = mMenu.findItem(id);
            if (item != null) {
                item.setEnabled(enable);
            }
        }
    }

    @Override
    public void onActionComplete() {
        // This is where we actually delete.
        mActionCompleteListener.onActionComplete();
        Conversation.delete(mActivity, mSelectionSet.values());
        mListAdapter.notifyDataSetChanged();
        mSelectionSet.clear();
    }
}
