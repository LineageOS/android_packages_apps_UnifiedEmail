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

import com.google.common.collect.ImmutableList;

import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;

import com.android.mail.ConversationListContext;
import com.android.mail.R;
import com.android.mail.providers.Account;
import com.android.mail.providers.Conversation;
import com.android.mail.providers.Folder;
import com.android.mail.providers.Settings;
import com.android.mail.providers.UIProvider;
import com.android.mail.providers.UIProvider.AutoAdvance;
import com.android.mail.providers.UIProvider.ConversationColumns;
import com.android.mail.utils.LogUtils;

import java.util.Collection;
import java.util.Collections;

/**
 * Controller for one-pane Mail activity. One Pane is used for phones, where screen real estate is
 * limited. This controller also does the layout, since the layout is simpler in the one pane case.
 */

// Called OnePaneActivityController in Gmail.
public final class OnePaneController extends AbstractActivityController {
    // Used for saving transaction IDs in bundles
    private static final String FOLDER_LIST_TRANSACTION_KEY = "folder-list-transaction";
    private static final String INBOX_CONVERSATION_LIST_TRANSACTION_KEY =
            "inbox_conversation-list-transaction";
    private static final String CONVERSATION_LIST_TRANSACTION_KEY = "conversation-list-transaction";
    private static final String CONVERSATION_TRANSACTION_KEY = "conversation-transaction";

    private static final int INVALID_ID = -1;
    private boolean mConversationListVisible = false;
    private int mLastInboxConversationListTransactionId = INVALID_ID;
    private int mLastConversationListTransactionId = INVALID_ID;
    private int mLastConversationTransactionId = INVALID_ID;
    private int mLastFolderListTransactionId = INVALID_ID;
    private Folder mInbox;
    /** Whether a conversation list for this account has ever been shown.*/
    private boolean mConversationListNeverShown = true;

    /**
     * @param activity
     * @param viewMode
     */
    public OnePaneController(MailActivity activity, ViewMode viewMode) {
        super(activity, viewMode);
    }

    @Override
    public void onRestoreInstanceState(Bundle inState) {
        super.onRestoreInstanceState(inState);
        // TODO(mindyp) handle saved state.
        if (inState != null) {
            mLastFolderListTransactionId = inState.getInt(FOLDER_LIST_TRANSACTION_KEY, INVALID_ID);
            mLastInboxConversationListTransactionId =
                    inState.getInt(INBOX_CONVERSATION_LIST_TRANSACTION_KEY, INVALID_ID);
            mLastConversationListTransactionId = inState.getInt(CONVERSATION_LIST_TRANSACTION_KEY,
                    INVALID_ID);
            mLastConversationTransactionId = inState.getInt(CONVERSATION_TRANSACTION_KEY,
                    INVALID_ID);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        // TODO(mindyp) handle saved state.
        outState.putInt(FOLDER_LIST_TRANSACTION_KEY, mLastFolderListTransactionId);
        outState.putInt(INBOX_CONVERSATION_LIST_TRANSACTION_KEY,
                mLastInboxConversationListTransactionId);
        outState.putInt(CONVERSATION_LIST_TRANSACTION_KEY, mLastConversationListTransactionId);
        outState.putInt(CONVERSATION_TRANSACTION_KEY, mLastConversationTransactionId);
    }

    @Override
    public void resetActionBarIcon() {
        final int mode = mViewMode.getMode();
        // If the settings aren't loaded yet, we may not know what the default
        // inbox is, so err toward this being the account inbox.
        if ((mAccount.settings != null && mConvListContext != null && !inInbox())
                || mode == ViewMode.SEARCH_RESULTS_LIST
                || mode == ViewMode.SEARCH_RESULTS_CONVERSATION
                || mode == ViewMode.CONVERSATION
                || mode == ViewMode.FOLDER_LIST) {
            mActionBarView.setBackButton();
        } else {
            mActionBarView.removeBackButton();
        }
    }

    private boolean inInbox() {
        final Uri inboxUri = Settings.getDefaultInboxUri(mAccount.settings);
        return mConvListContext != null && mConvListContext.folder != null ? (!mConvListContext
                .isSearchResult() && mConvListContext.folder.uri.equals(inboxUri)) : false;
    }

    @Override
    public void onAccountChanged(Account account) {
        super.onAccountChanged(account);
        mConversationListNeverShown = true;
    }

    @Override
    public boolean onCreate(Bundle savedInstanceState) {
        // Set 1-pane content view.
        mActivity.setContentView(R.layout.one_pane_activity);
        // The parent class sets the correct viewmode and starts the application off.
        return super.onCreate(savedInstanceState);
    }

    @Override
    protected boolean isConversationListVisible() {
        return mConversationListVisible;
    }

    @Override
    public void onViewModeChanged(int newMode) {
        super.onViewModeChanged(newMode);

        // When entering conversation list mode, hide and clean up any currently visible
        // conversation.
        // TODO: improve this transition
        if (newMode == ViewMode.CONVERSATION_LIST) {
            mPagerController.hide();
        }
    }

    @Override
    public void showConversationList(ConversationListContext listContext) {
        super.showConversationList(listContext);
        enableCabMode();
        // TODO(viki): Check if the account has been changed since the previous
        // time.
        if (listContext != null && listContext.isSearchResult()) {
            mViewMode.enterSearchResultsListMode();
        } else {
            mViewMode.enterConversationListMode();
        }
        // TODO(viki): This account transition looks strange in two pane mode.
        // Revisit as the app is coming together and improve the look and feel.
        final int transition = mConversationListNeverShown
                ? FragmentTransaction.TRANSIT_FRAGMENT_FADE
                : FragmentTransaction.TRANSIT_FRAGMENT_OPEN;
        Fragment conversationListFragment = ConversationListFragment.newInstance(listContext);

        if (!inInbox()) {
            // Maintain fragment transaction history so we can get back to the
            // fragment used to launch this list.
            mLastConversationListTransactionId = replaceFragment(conversationListFragment,
                    transition, TAG_CONVERSATION_LIST);
        } else {
            // If going to the inbox, clear the folder list transaction history.
            mInbox = listContext.folder;
            mLastInboxConversationListTransactionId = replaceFragment(conversationListFragment,
                    transition, TAG_CONVERSATION_LIST);
            mLastFolderListTransactionId = INVALID_ID;

            // If we ever to to the inbox, we want to unset the transation id for any other
            // non-inbox folder.
            mLastConversationListTransactionId = INVALID_ID;
        }
        mConversationListVisible = true;
        onConversationListVisibilityChanged(true);
        mConversationListNeverShown = false;
    }

    @Override
    public void showConversation(Conversation conversation) {
        super.showConversation(conversation);
        disableCabMode();
        if (mConvListContext != null && mConvListContext.isSearchResult()) {
            mViewMode.enterSearchResultsConversationMode();
        } else {
            mViewMode.enterConversationMode();
        }

        // Switching to conversation view is an incongruous transition: we are not replacing a
        // fragment with another fragment as usual. Instead, reveal the heretofore inert
        // conversation ViewPager and just remove the previously visible fragment
        // (e.g. conversation list, or possibly label list?).
        FragmentManager fm = mActivity.getFragmentManager();
        Fragment f = fm.findFragmentById(R.id.content_pane);
        if (f != null) {
            FragmentTransaction ft = fm.beginTransaction();
            ft.addToBackStack(null);
            ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
            ft.remove(f);
            ft.commitAllowingStateLoss();
        }

        // TODO: improve this transition
        mPagerController.show(mAccount, mFolder, conversation);

        resetActionBarIcon();

        mConversationListVisible = false;
        onConversationListVisibilityChanged(false);
    }

    @Override
    public void showWaitForInitialization() {
        super.showWaitForInitialization();

        replaceFragment(WaitFragment.newInstance(mAccount),
                FragmentTransaction.TRANSIT_FRAGMENT_OPEN, TAG_WAIT);
    }

    @Override
    public void hideWaitForInitialization() {
        transitionToInbox();
    }

    @Override
    public void showFolderList() {
        if (mAccount == null) {
            LogUtils.e(LOG_TAG, "Null account in showFolderList");
            return;
        }
        mViewMode.enterFolderListMode();
        enableCabMode();
        mLastFolderListTransactionId = replaceFragment(
                FolderListFragment.newInstance(null, mAccount.folderListUri),
                FragmentTransaction.TRANSIT_FRAGMENT_OPEN, TAG_FOLDER_LIST);
        mConversationListVisible = false;
        onConversationListVisibilityChanged(false);
    }

    /**
     * Replace the content_pane with the fragment specified here. The tag is specified so that
     * the {@link ActivityController} can look up the fragments through the
     * {@link android.app.FragmentManager}.
     * @param fragment
     * @param transition
     * @param tag
     * @return transaction ID returned when the transition is committed.
     */
    private int replaceFragment(Fragment fragment, int transition, String tag) {
        FragmentTransaction fragmentTransaction = mActivity.getFragmentManager().beginTransaction();
        fragmentTransaction.addToBackStack(null);
        fragmentTransaction.setTransition(transition);
        fragmentTransaction.replace(R.id.content_pane, fragment, tag);
        final int transactionId = fragmentTransaction.commitAllowingStateLoss();
        resetActionBarIcon();
        return transactionId;
    }

    /**
     * Back works as follows:
     * 1) If the user is in the folder list view, go back
     * to the account default inbox.
     * 2) If the user is in a conversation list
     * that is not the inbox AND:
     *  a) they got there by going through the folder
     *  list view, go back to the folder list view.
     *  b) they got there by using some other means (account dropdown), go back to the inbox.
     * 3) If the user is in a conversation, go back to the conversation list they were last in.
     * 4) If the user is in the conversation list for the default account inbox,
     * back exits the app.
     */
    @Override
    public boolean onBackPressed() {
        int mode = mViewMode.getMode();
        if (mode == ViewMode.FOLDER_LIST) {
            mLastFolderListTransactionId = INVALID_ID;
            transitionToInbox();
        } else if (mode == ViewMode.SEARCH_RESULTS_LIST) {
            mActivity.finish();
        } else if (mode == ViewMode.CONVERSATION_LIST && !inInbox()) {
            if (isTransactionIdValid(mLastFolderListTransactionId)) {
                // Go back to previous folder list.
                mViewMode.enterFolderListMode();
                mActivity.getFragmentManager().popBackStack(mLastFolderListTransactionId, 0);
            } else {
                // Go back to Inbox.
                transitionToInbox();
            }
        } else if (mode == ViewMode.CONVERSATION || mode == ViewMode.SEARCH_RESULTS_CONVERSATION) {
            transitionBackToConversationListMode();
        } else {
            mActivity.finish();
        }
        mUndoBarView.hide(false);
        return true;
    }

    private void transitionToInbox() {
        mViewMode.enterConversationListMode();
        if (mInbox == null) {
            loadAccountInbox();
        } else {
            ConversationListContext listContext = ConversationListContext.forFolder(mContext,
                    mAccount, mInbox);
            // Set the correct context for what the conversation view will be
            // now.
            onFolderChanged(mInbox);
            showConversationList(listContext);
        }
    }

    @Override
    public void onFolderSelected(Folder folder, boolean childView) {
        if (!childView && folder.hasChildren) {
            // Replace this fragment with a new FolderListFragment
            // showing this folder's children if we are not already looking
            // at the child view for this folder.
            mLastFolderListTransactionId = replaceFragment(
                    FolderListFragment.newInstance(folder, folder.childFoldersListUri),
                    FragmentTransaction.TRANSIT_FRAGMENT_OPEN, TAG_FOLDER_LIST);
            return;
        }
        if (mViewMode.getMode() == ViewMode.FOLDER_LIST && folder != null
                && folder.equals(mFolder)) {
            // if we are in folder list when we select a new folder,
            // and it is the same as the existing folder, clear the previous
            // folder setting so that the folder will be re-loaded/ shown.
            mFolder = null;
        }
        super.onFolderChanged(folder);
    }

    private boolean isTransactionIdValid(int id) {
        return id >= 0;
    }

    /**
     * Up works as follows:
     * 1) If the user is in a conversation list that is not the default account inbox,
     * a conversation, or the folder list, up follows the rules of back.
     * 2) If the user is in search results, up exits search
     * mode and returns the user to whatever view they were in when they began search.
     * 3) If the user is in the inbox, there is no up.
     */
    @Override
    public boolean onUpPressed() {
        int mode = mViewMode.getMode();
        if (mode == ViewMode.SEARCH_RESULTS_LIST) {
            mActivity.finish();
        } else if ((!inInbox() && mode == ViewMode.CONVERSATION_LIST)
                || mode == ViewMode.CONVERSATION
                || mode == ViewMode.FOLDER_LIST
                || mode == ViewMode.SEARCH_RESULTS_CONVERSATION) {
            // Same as go back.
            mActivity.onBackPressed();
        }
        return true;
    }

    private void transitionBackToConversationListMode() {
        int mode = mViewMode.getMode();
        enableCabMode();
        if (mode == ViewMode.SEARCH_RESULTS_CONVERSATION) {
            mViewMode.enterSearchResultsListMode();
        } else {
            mViewMode.enterConversationListMode();
        }
        if (isTransactionIdValid(mLastConversationListTransactionId)) {
            mActivity.getFragmentManager().popBackStack(mLastConversationListTransactionId, 0);
            resetActionBarIcon();
        } else if (isTransactionIdValid(mLastInboxConversationListTransactionId)) {
            mActivity.getFragmentManager().popBackStack(mLastInboxConversationListTransactionId, 0);
            resetActionBarIcon();
            onFolderChanged(mInbox);
        } else {
            // TODO: revist if this block is necessary
            ConversationListContext listContext = ConversationListContext.forFolder(mContext,
                    mAccount, mInbox);
            // Set the correct context for what the conversation view will be now.
            onFolderChanged(mInbox);
            showConversationList(listContext);
        }
        resetActionBarIcon();

        mConversationListVisible = true;
        onConversationListVisibilityChanged(true);
    }

    @Override
    public boolean shouldShowFirstConversation() {
        return false;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        boolean handled = true;
        final Collection<Conversation> target = ImmutableList.of(mCurrentConversation);
        final Settings settings = mAccount.settings;
        switch (item.getItemId()) {
            case R.id.y_button: {
                final boolean showDialog = (settings != null && settings.confirmArchive);
                confirmAndDelete(target, showDialog, R.plurals.confirm_archive_conversation,
                        getAction(R.id.archive));
                break;
            }
            case R.id.delete: {
                final boolean showDialog = (settings != null && settings.confirmDelete);
                confirmAndDelete(target, showDialog,
                        R.plurals.confirm_delete_conversation, getAction(R.id.delete));
                break;
            }
            case R.id.change_folders:
                new FoldersSelectionDialog(mActivity.getActivityContext(), mAccount, this,
                        target).show();
                break;
            case R.id.inside_conversation_unread:
                // Mark as unread and advance.
                performInsideConversationUnread();
                break;
            case R.id.mark_important:
                updateCurrentConversation(ConversationColumns.PRIORITY,
                        UIProvider.ConversationPriority.HIGH);
                break;
            case R.id.mark_not_important:
                updateCurrentConversation(ConversationColumns.PRIORITY,
                        UIProvider.ConversationPriority.LOW);
                break;
            case R.id.mute:
                requestDelete(target, getAction(R.id.mute));
                break;
            case R.id.report_spam:
                requestDelete(target, getAction(R.id.report_spam));
                break;
            default:
                handled = false;
                break;
        }
        return handled || super.onOptionsItemSelected(item);
    }

    // TODO: If when the conversation was opened, some of the messages were unread,
    // this is supposed to restore that state. Otherwise, this should mark all
    // messages as unread
    private void performInsideConversationUnread() {
        updateCurrentConversation(ConversationColumns.READ, false);
        if (returnToList()) {
            onBackPressed();
        } else {
            final DestructiveAction action = getAction(R.id.inside_conversation_unread);
            action.performAction();
        }
    }

    /**
     * Destroy conversations and update the UI state for a one pane activity.
     */
    private class OnePaneDestructiveAction implements DestructiveAction {
        /** Whether this destructive action has already been performed */
        private boolean mCompleted;
        /** Menu Id that created this action */
        private final int mId;
        /** Action that updates the underlying database to modify the conversation. */
        private final DestructiveAction mAction;

        public OnePaneDestructiveAction(int action) {
            final Collection<Conversation> single = ImmutableList.of(mCurrentConversation);
            mAction = new ConversationAction(action, single);
            mId = action;
        }

        @Override
        public void performAction() {
            if (isPerformed()) {
                return;
            }
            mAction.performAction();
            switch (mViewMode.getMode()) {
                case ViewMode.CONVERSATION:
                    final Conversation next = mTracker.getNextConversation(
                            Settings.getAutoAdvanceSetting(mAccount.settings));
                    if (next != null) {
                        showConversation(next);
                        onUndoAvailable(new UndoOperation(1, mId));
                    } else {
                        // No next conversation, we should got back to conversation list.
                        transitionBackToConversationListMode();
                    }
                    break;
                case ViewMode.CONVERSATION_LIST:
                    if (mId != R.id.inside_conversation_unread) {
                        onUndoAvailable(new UndoOperation(1, mId));
                    }
                    refreshConversationList();
                    break;
            }
        }
        /**
         * Returns true if this action has been performed, false otherwise.
         * @return
         */
        private synchronized boolean isPerformed() {
            if (mCompleted) {
                return true;
            }
            mCompleted = true;
            return false;
        }
    }

    /**
     * Get a destructive action specific to the {@link OnePaneController}.
     * This is a temporary method, to control the profusion of {@link DestructiveAction} classes
     * that are created. Please do not copy this paradigm.
     * TODO(viki): Resolve the various actions and clean up their calling sequence.
     * @param action
     * @return
     */
    private final DestructiveAction getAction(int action) {
        final DestructiveAction da = new OnePaneDestructiveAction(action);
        registerDestructiveAction(da);
        return da;
    }

    /**
     * Returns true if we need to return back to conversation list based on the current
     * AutoAdvance setting and the number of messages in the list.
     * @return true if we need to return back to conversation list, false otherwise.
     */
    private boolean returnToList() {
        final int pref = Settings.getAutoAdvanceSetting(mAccount.settings);
        final int position = mCurrentConversation.position;
        final boolean moveToNewer = (pref == AutoAdvance.NEWER && (position - 1 >= 0));
        final boolean moveToOlder = (pref == AutoAdvance.OLDER && mConversationListCursor != null
                && (position + 1 < mConversationListCursor.getCount()));
        final boolean canMove = moveToNewer || moveToOlder;
        // Return true if we cannot move forward or back, or if the user wants to go back to list.
        return pref == AutoAdvance.LIST || !canMove;
    }

    @Override
    public DestructiveAction getFolderDestructiveAction() {
        return getAction(R.id.change_folder);
    }

    @Override
    public void onUndoAvailable(UndoOperation op) {
        if (op != null && mAccount.supportsCapability(UIProvider.AccountCapabilities.UNDO)) {
            final int mode = mViewMode.getMode();
            switch (mode) {
                case ViewMode.CONVERSATION:
                    mUndoBarView.show(true, mActivity.getActivityContext(), op, mAccount,
                            null, null);
                    break;
                case ViewMode.CONVERSATION_LIST:
                    final ConversationListFragment convList = getConversationListFragment();
                    if (convList != null) {
                        mUndoBarView.show(true, mActivity.getActivityContext(), op, mAccount,
                            convList.getAnimatedAdapter(), mConversationListCursor);
                    }
                    break;
            }
        }
    }
}
