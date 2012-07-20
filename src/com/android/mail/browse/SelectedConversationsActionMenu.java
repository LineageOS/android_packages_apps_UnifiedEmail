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

import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.net.Uri;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;

import com.android.mail.R;
import com.android.mail.providers.Account;
import com.android.mail.providers.Conversation;
import com.android.mail.providers.ConversationInfo;
import com.android.mail.providers.Folder;
import com.android.mail.providers.MailAppProvider;
import com.android.mail.providers.MessageInfo;
import com.android.mail.providers.Settings;
import com.android.mail.providers.UIProvider;
import com.android.mail.providers.UIProvider.ConversationColumns;
import com.android.mail.providers.UIProvider.FolderCapabilities;
import com.android.mail.ui.ControllableActivity;
import com.android.mail.ui.ConversationSelectionSet;
import com.android.mail.ui.ConversationSetObserver;
import com.android.mail.ui.ConversationUpdater;
import com.android.mail.ui.DestructiveAction;
import com.android.mail.ui.FoldersSelectionDialog;
import com.android.mail.ui.RestrictedActivity;
import com.android.mail.ui.SwipeableListView;
import com.android.mail.utils.LogTag;
import com.android.mail.utils.LogUtils;
import com.android.mail.utils.Utils;
import com.google.common.annotations.VisibleForTesting;

import org.json.JSONException;

import java.util.ArrayList;
import java.util.Collection;

/**
 * A component that displays a custom view for an {@code ActionBar}'s {@code
 * ContextMode} specific to operating on a set of conversations.
 */
public class SelectedConversationsActionMenu implements ActionMode.Callback,
        ConversationSetObserver {

    private static final String LOG_TAG = LogTag.getLogTag();

    /**
     * The set of conversations to display the menu for.
     */
    protected final ConversationSelectionSet mSelectionSet;

    private final RestrictedActivity mActivity;

    /**
     * Context of the activity. A dialog requires the context of an activity rather than the global
     * root context of the process. So mContext = mActivity.getApplicationContext() will fail.
     */
    private final Context mContext;

    @VisibleForTesting
    private ActionMode mActionMode;

    private boolean mActivated = false;

    private Menu mMenu;

    /** Object that can update conversation state on our behalf. */
    private final ConversationUpdater mUpdater;

    private final Account mAccount;

    private final Folder mFolder;

    private final SwipeableListView mListView;

    public SelectedConversationsActionMenu(RestrictedActivity activity,
            ConversationSelectionSet selectionSet, Account account,
            Folder folder, SwipeableListView list) {
        mActivity = activity;
        mSelectionSet = selectionSet;
        mAccount = account;
        mFolder = folder;
        mListView = list;

        mContext = mActivity.getActivityContext();
        mUpdater = ((ControllableActivity) mActivity).getConversationUpdater();
    }

    @Override
    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
        boolean handled = true;
        switch (item.getItemId()) {
            case R.id.delete:
                performDestructiveAction(R.id.delete);
                break;
            case R.id.archive:
                performDestructiveAction(R.id.archive);
                break;
            case R.id.mute:
                mUpdater.delete(mSelectionSet.values(), mUpdater.getBatchAction(R.id.mute));
                break;
            case R.id.report_spam:
                mUpdater.delete(mSelectionSet.values(), mUpdater.getBatchAction(R.id.report_spam));
                break;
            case R.id.mark_not_spam:
                // Currently, since spam messages are only shown in list with other spam messages,
                // marking a message not as spam is a destructive action
                mUpdater.delete(mSelectionSet.values(),
                        mUpdater.getBatchAction(R.id.mark_not_spam));
                break;
            case R.id.report_phishing:
                mUpdater.delete(mSelectionSet.values(),
                        mUpdater.getBatchAction(R.id.report_phishing));
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
                if (mFolder.type == UIProvider.FolderType.STARRED) {
                    LogUtils.d(LOG_TAG, "We are in a starred folder, removing the star");
                    performDestructiveAction(R.id.remove_star);
                } else {
                    LogUtils.d(LOG_TAG, "Not in a starred folder.");
                    starConversations(false);
                }
                break;
            case R.id.change_folder:
                boolean cantMove = false;
                Account acct = mAccount;
                // Special handling for virtual folders
                if (mFolder.supportsCapability(FolderCapabilities.IS_VIRTUAL)) {
                    Uri accountUri = null;
                    for (Conversation conv: mSelectionSet.values()) {
                        if (accountUri == null) {
                            accountUri = conv.accountUri;
                        } else if (!accountUri.equals(conv.accountUri)) {
                            // Tell the user why we can't do this
                            Toast.makeText(mContext, R.string.cant_move_or_change_labels,
                                    Toast.LENGTH_LONG).show();
                            cantMove = true;
                            break;
                        }
                    }
                    if (!cantMove) {
                        // Get the actual account here, so that we display its folders in the dialog
                        acct = MailAppProvider.getAccountFromAccountUri(accountUri);
                    }
                }
                if (!cantMove) {
                    new FoldersSelectionDialog(mContext, acct, mUpdater,
                            mSelectionSet.values(), true).show();
                }
                break;
            case R.id.mark_important:
                markConversationsImportant(true);
                break;
            case R.id.mark_not_important:
                if (mFolder.supportsCapability(UIProvider.FolderCapabilities.ONLY_IMPORTANT)) {
                    performDestructiveAction(R.id.mark_not_important);
                } else {
                    markConversationsImportant(false);
                }
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
    }

    /**
     * Update the underlying list adapter and redraw the menus if necessary.
     */
    private void updateSelection() {
        mUpdater.refreshConversationList();
        if (mActionMode != null) {
            // Calling mActivity.invalidateOptionsMenu doesn't have the correct behavior, since
            // the action mode is not refreshed when activity's options menu is invalidated.
            // Since we need to refresh our own menu, it is easy to call onPrepareActionMode
            // directly.
            onPrepareActionMode(mActionMode, mActionMode.getMenu());
        }
    }

    private void performDestructiveAction(final int id) {
        final DestructiveAction action = mUpdater.getBatchAction(id);
        final Settings settings = mActivity.getSettings();
        final Collection<Conversation> conversations = mSelectionSet.values();
        final boolean showDialog = (settings != null
                && (id == R.id.delete) ? settings.confirmDelete : settings.confirmArchive);
        if (showDialog) {
            int resId = id == R.id.delete ? R.plurals.confirm_delete_conversation
                    : R.plurals.confirm_archive_conversation;
            CharSequence message = Utils.formatPlural(mContext, resId, conversations.size());
            new AlertDialog.Builder(mContext).setMessage(message)
                    .setPositiveButton(R.string.ok, new AlertDialog.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            destroy(id, conversations, action);
                        }
                    }).setNegativeButton(R.string.cancel, null).create().show();
        } else {
            destroy(id, conversations, action);
        }
    }

    private void destroy(int id, final Collection<Conversation> conversations,
            final DestructiveAction listener) {
        if (id == R.id.archive) {
            ArrayList<ConversationItemView> views = new ArrayList<ConversationItemView>();
            for (ConversationItemView view : mSelectionSet.views()) {
                views.add(view);
            }
            mListView.archiveItems(views, listener);
        } else {
            mUpdater.delete(conversations, listener);
        }
    }

    /**
     * Marks the read state of currently selected conversations (<b>and</b> the backing storage)
     * to the value provided here.
     * @param read is true if the conversations are to be marked as read, false if they are to be
     * marked unread.
     */
    private void markConversationsRead(boolean read) {
        final Collection<Conversation> targets = mSelectionSet.values();
        ContentValues values;
        ConversationInfo info;
        for (Conversation target : targets) {
            values = new ContentValues();
            values.put(ConversationColumns.READ, read);
            info = target.conversationInfo;
            if (info != null) {
                try {
                    info.markRead(read);
                    values.put(ConversationColumns.CONVERSATION_INFO,
                            ConversationInfo.toString(info));
                } catch (JSONException e) {
                    LogUtils.e(LOG_TAG, e, "Error updating conversation info");
                }
            }
            mUpdater.updateConversation(Conversation.listOf(target), values);
        }
        // Update the conversations in the selection too.
        for (final Conversation c : targets) {
            c.read = read;
        }
        updateSelection();
    }

    /**
     * Marks the important state of currently selected conversations (<b>and</b> the backing
     * storage) to the value provided here.
     * @param important is true if the conversations are to be marked as important, false if they
     * are to be marked not important.
     */
    private void markConversationsImportant(boolean important) {
        final Collection<Conversation> target = mSelectionSet.values();
        final int priority = important ? UIProvider.ConversationPriority.HIGH
                : UIProvider.ConversationPriority.LOW;
        mUpdater.updateConversation(target, ConversationColumns.PRIORITY, priority);
        // Update the conversations in the selection too.
        for (final Conversation c : target) {
            c.priority = priority;
        }
        updateSelection();
    }

    /**
     * Marks the selected conversations with the star setting provided here.
     * @param star true if you want all the conversations to have stars, false if you want to remove
     * stars from all conversations
     */
    private void starConversations(boolean star) {
        final Collection<Conversation> target = mSelectionSet.values();
        mUpdater.updateConversation(target, ConversationColumns.STARRED, star);
        // Update the conversations in the selection too.
        for (final Conversation c : target) {
            c.starred = star;
        }
        updateSelection();
    }

    @Override
    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
        mSelectionSet.addObserver(this);
        final MenuInflater inflater = mActivity.getMenuInflater();
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
        final Collection<Conversation> conversations = mSelectionSet.values();
        boolean showStar = false;
        boolean showMarkUnread = false;
        boolean showMarkImportant = false;
        boolean showMarkNotSpam = false;
        boolean showMarkAsPhishing = false;

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
            if (conversation.spam) {
                showMarkNotSpam = true;
            }
            if (!conversation.phishing) {
                showMarkAsPhishing = true;
            }
            if (showStar && showMarkUnread && showMarkImportant && showMarkNotSpam &&
                    showMarkAsPhishing) {
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
                mFolder.supportsCapability(FolderCapabilities.REPORT_SPAM));
        final MenuItem notSpam = menu.findItem(R.id.mark_not_spam);
        notSpam.setVisible(showMarkNotSpam &&
                mAccount.supportsCapability(UIProvider.AccountCapabilities.REPORT_SPAM) &&
                mFolder.supportsCapability(FolderCapabilities.MARK_NOT_SPAM));
        final MenuItem phishing = menu.findItem(R.id.report_phishing);
        phishing.setVisible(showMarkAsPhishing &&
                mAccount.supportsCapability(UIProvider.AccountCapabilities.REPORT_PHISHING) &&
                mFolder.supportsCapability(FolderCapabilities.REPORT_PHISHING));

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
     * Activates and shows this menu (essentially starting an {@link ActionMode}) if the selected
     * set is non-empty.
     */
    public void activate() {
        if (mSelectionSet.isEmpty()) {
            // We have nothing to do since there is no conversation selected.
            return;
        }
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
        mUpdater.refreshConversationList();
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
}
