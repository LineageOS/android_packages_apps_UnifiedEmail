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
import com.android.mail.providers.Folder;
import com.android.mail.providers.Settings;
import com.android.mail.providers.UIProvider;
import com.android.mail.providers.UIProvider.FolderCapabilities;
import com.android.mail.providers.UIProvider.ConversationColumns;
import com.android.mail.ui.AnimatedAdapter;
import com.android.mail.ui.ActionCompleteListener;
import com.android.mail.ui.ConversationSelectionSet;
import com.android.mail.ui.ConversationSetObserver;
import com.android.mail.ui.FoldersSelectionDialog;
import com.android.mail.ui.RestrictedActivity;
import com.android.mail.ui.FoldersSelectionDialog.FolderChangeCommitListener;
import com.android.mail.ui.UndoBarView.UndoListener;
import com.android.mail.ui.UndoOperation;
import com.android.mail.utils.LogUtils;
import com.android.mail.utils.Utils;
import com.google.common.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

/**
 * A component that displays a custom view for an {@code ActionBar}'s {@code
 * ContextMode} specific to operating on a set of conversations.
 */
public class SelectedConversationsActionMenu implements ActionMode.Callback,
        ConversationSetObserver, FolderChangeCommitListener {

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
    protected ArrayList<Folder> mFolderChangeList;

    private final RestrictedActivity mActivity;

    /**
     * Context of the activity. A dialog requires the context of an activity rather than the global
     * root context of the process. So mContext = mActivity.getApplicationContext() will fail.
     */
    private final Context mContext;

    @VisibleForTesting
    ActionMode mActionMode;

    private boolean mActivated = false;

    private Menu mMenu;

    private AnimatedAdapter mListAdapter;

    private ActionCompleteListener mActionCompleteListener;

    private UndoListener mUndoListener;

    private Account mAccount;

    protected int mCheckedItem = 0;

    private Folder mFolder;

    private ActionCompleteListener mDeleteListener = new DestructiveActionListener(R.id.delete);
    private ActionCompleteListener mArchiveListener = new DestructiveActionListener(R.id.archive);
    private ActionCompleteListener mMuteListener = new DestructiveActionListener(R.id.mute);
    private ActionCompleteListener mSpamListener = new DestructiveActionListener(R.id.report_spam);

    public SelectedConversationsActionMenu(RestrictedActivity activity,
            ConversationSelectionSet selectionSet, AnimatedAdapter adapter,
            ActionCompleteListener listener, UndoListener undoListener, Account account,
            Folder folder) {
        mSelectionSet = selectionSet;
        mActivity = activity;
        mContext = mActivity.getActivityContext();
        mListAdapter = adapter;
        mActionCompleteListener = listener;
        mUndoListener = undoListener;
        mAccount = account;
        mFolder = folder;
    }

    @Override
    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
        boolean handled = true;
        Collection<Conversation> conversations = mSelectionSet.values();
        switch (item.getItemId()) {
            case R.id.delete:
                performDestructiveAction(R.id.delete, mDeleteListener);
                break;
            case R.id.archive:
                performDestructiveAction(R.id.archive, mArchiveListener);
                break;
            case R.id.mute:
                mListAdapter.delete(conversations, mMuteListener);
                break;
            case R.id.report_spam:
                mListAdapter.delete(conversations, mSpamListener);
                break;
            case R.id.read:
                markConversationsRead(true);
                break;
            case R.id.unread:
                markConversationsRead(false);
                break;
            case R.id.star:
                starConversations(true);
                break;
            case R.id.remove_star:
                starConversations(false);
                break;
            case R.id.change_folder:
                showChangeFoldersDialog();
                break;
            case R.id.mark_important:
                markConversationsImportant(true);
                break;
            case R.id.mark_not_important:
                markConversationsImportant(false);
                break;
            default:
                handled = false;
                break;
        }
        return handled;
    }

    /**
     * Clear the selection and perform related UI changes to keep the state consistent.
     */
    private void clearSelection() {
        mSelectionSet.clear();
        // Redraw with changes
        mListAdapter.notifyDataSetChanged();
    }

    private void performDestructiveAction(int id, final ActionCompleteListener listener) {
        Settings settings = mActivity.getSettings();
        final Collection<Conversation> conversations = mSelectionSet.values();
        boolean showDialog = false;
        if (settings != null) {
            showDialog = (id == R.id.delete) ? settings.confirmDelete : settings.confirmArchive;
        }
        if (showDialog) {
            int resId = id == R.id.delete ? R.plurals.confirm_delete_conversation
                    : R.plurals.confirm_archive_conversation;
            CharSequence message = Utils.formatPlural(mContext, resId, conversations.size());
            new AlertDialog.Builder(mContext).setMessage(message)
                    .setPositiveButton(R.string.ok, new AlertDialog.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            mListAdapter.delete(conversations, listener);
                        }

                    }).setNegativeButton(R.string.cancel, null).create().show();
        } else {
            mListAdapter.delete(conversations, listener);
        }
    }

    private void markConversationsRead(boolean read) {
        Collection<Conversation> conversations = mSelectionSet.values();
        Conversation.updateBoolean(mContext, conversations, ConversationColumns.READ, read);
        clearSelection();
    }

    private void markConversationsImportant(boolean important) {
        Collection<Conversation> conversations = mSelectionSet.values();
        int priority = important ? UIProvider.ConversationPriority.HIGH
                : UIProvider.ConversationPriority.LOW;
        Conversation.updateInt(mContext, conversations, ConversationColumns.PRIORITY, priority);
        clearSelection();
    }

    private void starConversations(boolean star) {
        Collection<Conversation> conversations = mSelectionSet.values();
        if (conversations.size() > 0) {
            Conversation.updateBoolean(mContext, conversations,
                    ConversationColumns.STARRED, star);
        }
        clearSelection();
    }

    private void showChangeFoldersDialog() {
        new FoldersSelectionDialog(mContext, mAccount, this, mSelectionSet.values()).show();
    }

    @Override
    public void onFolderChangesCommit(ArrayList<Folder> folderChangeList) {
        mFolderChangeList = folderChangeList;
        // Do the change here...
        // Get currently active folder info and compare it to the list
        // these conversations have been given; if they no longer contain
        // the selected folder, delete them from the list.
        HashSet<String> folderUris = new HashSet<String>();
        if (folderChangeList != null && !folderChangeList.isEmpty()) {
            for (Folder f : folderChangeList) {
                folderUris.add(f.uri.toString());
            }
        }
        if (!folderUris.contains(mFolder.uri.toString())) {
            final Collection<Conversation> conversations = mSelectionSet.values();
            // Indicate delete on update (i.e. no longer in this folder)
            mDeletionSet = new ArrayList<Conversation>();
            for (Conversation conv : conversations) {
                conv.localDeleteOnUpdate = true;
                // For Gmail, add... if (noLongerInList(conv))...
                mDeletionSet.add(conv);
            }
            // Delete the local delete items (all for now) and when done,
            // update...
            mListAdapter.delete(mDeletionSet, mFolderChangeListener);
        } else {
            mFolderChangeListener.onActionComplete();
        }
    }

    private ActionCompleteListener mFolderChangeListener = new ActionCompleteListener() {
        @Override
        public void onActionComplete() {
            mActionCompleteListener.onActionComplete();
            Collection<Conversation> deletionSet = mDeletionSet;
            if (deletionSet != null && deletionSet.size() > 0) {
                // Only show undo if this was a destructive folder change.
                UndoOperation undoOp = new UndoOperation(deletionSet.size(), R.id.change_folder);
                mUndoListener.onUndoAvailable(undoOp);
                mDeletionSet = null;
            }
            StringBuilder foldersUrisString = new StringBuilder();
            boolean first = true;
            for (Folder f : mFolderChangeList) {
                if (first) {
                    first = false;
                } else {
                    foldersUrisString.append(',');
                }
                foldersUrisString.append(f.uri.toString());
            }
            Conversation.updateString(mContext, mSelectionSet.values(),
                    ConversationColumns.FOLDER_LIST, foldersUrisString.toString());
            Conversation.updateString(mContext, mSelectionSet.values(),
                    ConversationColumns.RAW_FOLDERS,
                    Folder.getSerializedFolderString(mFolder, mFolderChangeList));
            clearSelection();
        }
    };

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
        // Determine read/ unread
        // Star/ unstar
        Collection<Conversation> conversations = mSelectionSet.values();
        boolean showStar = false;
        boolean showMarkUnread = false;
        boolean showMarkImportant = false;

        for (Conversation conversation : conversations) {
            if (!conversation.starred) {
                showStar = true;
            }
            if (conversation.read) {
                showMarkUnread = true;
            }
            if (!conversation.isImportant()) {
                showMarkImportant = true;
            }
            if (showStar && showMarkUnread && showMarkImportant) {
                break;
            }
        }
        final MenuItem star = menu.findItem(R.id.star);
        star.setVisible(showStar);
        final MenuItem unstar = menu.findItem(R.id.remove_star);
        unstar.setVisible(!showStar);
        final MenuItem read = menu.findItem(R.id.read);
        read.setVisible(!showMarkUnread);
        final MenuItem unread = menu.findItem(R.id.unread);
        unread.setVisible(showMarkUnread);
        final MenuItem archive = menu.findItem(R.id.archive);
        archive.setVisible(mAccount.supportsCapability(UIProvider.AccountCapabilities.ARCHIVE) &&
                mFolder.supportsCapability(FolderCapabilities.ARCHIVE));
        final MenuItem spam = menu.findItem(R.id.report_spam);
        spam.setVisible(mAccount.supportsCapability(UIProvider.AccountCapabilities.REPORT_SPAM) &&
                mFolder.supportsCapability(FolderCapabilities.ARCHIVE));
        final MenuItem mute = menu.findItem(R.id.mute);
        mute.setVisible(mAccount.supportsCapability(UIProvider.AccountCapabilities.MUTE));
        final MenuItem markImportant = menu.findItem(R.id.mark_important);
        markImportant.setVisible(showMarkImportant
                && mAccount.supportsCapability(UIProvider.AccountCapabilities.MARK_IMPORTANT));
        final MenuItem markNotImportant = menu.findItem(R.id.mark_not_important);
        markNotImportant.setVisible(!showMarkImportant
                && mAccount.supportsCapability(UIProvider.AccountCapabilities.MARK_IMPORTANT));

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
        }
        mMenu = null;
    }

    @Override
    public void onSetPopulated(ConversationSelectionSet set) {
        // Noop. This object can only exist while the set is non-empty.
    }

    @Override
    public void onSetEmpty() {
        LogUtils.d(LOG_TAG, "onSetEmpty called.");
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
    private void destroy() {
        deactivate();
        mSelectionSet.removeObserver(this);
        clearSelection();
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

    private class DestructiveActionListener implements ActionCompleteListener {
        private final int mAction;
        public DestructiveActionListener(int action) {
            mAction = action;
        }

        @Override
        public void onActionComplete() {
            // This is where we actually delete.
            Collection<Conversation> conversations = mSelectionSet.values();
            mActionCompleteListener.onActionComplete();
            mUndoListener.onUndoAvailable(new UndoOperation(conversations.size(), mAction));
            switch (mAction) {
                case R.id.archive:
                    Conversation.archive(mContext, conversations);
                    break;
                case R.id.delete:
                    Conversation.delete(mContext, conversations);
                    break;
                case R.id.mute:
                    if (mFolder.supportsCapability(FolderCapabilities.DESTRUCTIVE_MUTE)) {
                        // Make sure to set the localDeleteOnUpdate flag for these conversatons.
                        for (Conversation conversation: conversations) {
                            conversation.localDeleteOnUpdate = true;
                        }
                    }
                    Conversation.mute(mContext, conversations);
                    break;
                case R.id.report_spam:
                    Conversation.reportSpam(mContext, conversations);
                    break;
            }
            clearSelection();
        }
    }
}
