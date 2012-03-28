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

import com.android.mail.ConversationListContext;
import com.android.mail.R;
import com.android.mail.providers.Account;
import com.android.mail.providers.Conversation;
import com.android.mail.providers.Folder;
import com.android.mail.providers.Settings;
import com.android.mail.providers.UIProvider;
import com.android.mail.providers.UIProvider.AutoAdvance;
import com.android.mail.providers.UIProvider.ConversationColumns;
import com.android.mail.ui.FolderListFragment.FolderListSelectionListener;
import com.android.mail.utils.LogUtils;

import java.util.ArrayList;
import java.util.Collections;

import android.app.Fragment;
import android.app.FragmentTransaction;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;

/**
 * Controller for one-pane Mail activity. One Pane is used for phones, where screen real estate is
 * limited.
 */

// Called OnePaneActivityController in Gmail.
public final class TwoPaneController extends AbstractActivityController implements
        FolderListSelectionListener {
    private boolean mJumpToFirstConversation;
    private TwoPaneLayout mLayout;
    private final ActionCompleteListener mDeleteListener = new TwoPaneDestructiveActionListener(
            R.id.delete);
    private final ActionCompleteListener mArchiveListener = new TwoPaneDestructiveActionListener(
            R.id.archive);
    private final ActionCompleteListener mMuteListener = new TwoPaneDestructiveActionListener(
            R.id.mute);
    private final ActionCompleteListener mSpamListener = new TwoPaneDestructiveActionListener(
            R.id.report_spam);
    private final TwoPaneDestructiveActionListener mFolderChangeListener =
            new TwoPaneDestructiveActionListener(R.id.change_folder);

    /**
     * @param activity
     * @param viewMode
     */
    public TwoPaneController(MailActivity activity, ViewMode viewMode) {
        super(activity, viewMode);
    }

    /**
     * Display the conversation list fragment.
     * @param show
     */
    private void initializeConversationListFragment(boolean show) {
        if (show) {
            if (mConvListContext != null && mConvListContext.isSearchResult()) {
                mViewMode.enterSearchResultsListMode();
            } else {
                mViewMode.enterConversationListMode();
            }
        }
        renderConversationList();
    }

    /**
     * Render the conversation list in the correct pane.
     */
    private void renderConversationList() {
        if (mActivity == null) {
            return;
        }
        FragmentTransaction fragmentTransaction = mActivity.getFragmentManager().beginTransaction();
        // Use cross fading animation.
        fragmentTransaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
        Fragment conversationListFragment = ConversationListFragment
                .newInstance(mConvListContext);
        fragmentTransaction.replace(R.id.conversation_list_pane, conversationListFragment);
        fragmentTransaction.commitAllowingStateLoss();
    }

    /**
     * Render the folder list in the correct pane.
     */
    private void renderFolderList() {
        if (mActivity == null) {
            return;
        }
        createFolderListFragment(null, mAccount.folderListUri);
    }

    private void createFolderListFragment(Folder parent, Uri uri) {
        FolderListFragment folderListFragment = FolderListFragment.newInstance(this, parent,
                uri);
        FragmentTransaction fragmentTransaction = mActivity.getFragmentManager().beginTransaction();
        fragmentTransaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
        fragmentTransaction.replace(R.id.content_pane, folderListFragment);
        fragmentTransaction.commitAllowingStateLoss();
        // Since we are showing the folder list, we are at the start of the view
        // stack.
        resetActionBarIcon();
        attachFolderList(folderListFragment);
        if (getCurrentListContext() != null) {
            folderListFragment.selectFolder(getCurrentListContext().folder);
        }
    }

    @Override
    protected boolean isConversationListVisible() {
        // TODO(viki): Auto-generated method stub
        return false;
    }

    @Override
    public void showConversationList(ConversationListContext context) {
        initializeConversationListFragment(true);
    }

    @Override
    public void showFolderList() {
        // On two-pane layouts, showing the folder list takes you to the top level of the
        // application, which is the same as pressing the Up button
        onUpPressed();
    }

    @Override
    public boolean onCreate(Bundle savedState) {
        mActivity.setContentView(R.layout.two_pane_activity);
        mLayout = (TwoPaneLayout) mActivity.findViewById(R.id.two_pane_activity);
        if (mLayout == null) {
            LogUtils.d(LOG_TAG, "mLayout is null!");
        }
        mLayout.initializeLayout(mActivity.getApplicationContext());

        // The tablet layout needs to refer to mode changes.
        mViewMode.addListener(mLayout);
        // The activity controller needs to listen to layout changes.
        mLayout.setListener(this);
        final boolean isParentInitialized = super.onCreate(savedState);
        return isParentInitialized;
    }

    @Override
    public void onAccountChanged(Account account) {
        super.onAccountChanged(account);
        renderFolderList();
    }

    @Override
    public void onFolderSelected(Folder folder, boolean childView) {
        if (!childView && folder.hasChildren) {
            // Replace this fragment with a new FolderListFragment
            // showing this folder's children if we are not already looking
            // at the child view for this folder.
            createFolderListFragment(folder, folder.childFoldersListUri);
            // Show the up affordance when digging into child folders.
            mActionBarView.setBackButton();
            return;
        }
        if (mFolderListFragment != null) {
            mFolderListFragment.selectFolder(folder);
        }
        super.onFolderChanged(folder);
    }

    @Override
    public void onViewModeChanged(int newMode) {
        super.onViewModeChanged(newMode);
        if (newMode != ViewMode.CONVERSATION) {
            // Clear this flag if the user jumps out of conversation mode
            // before a load completes.
            mJumpToFirstConversation = false;
        }
        resetActionBarIcon();
    }

    @Override
    public void resetActionBarIcon() {
        if (mViewMode.getMode() == ViewMode.CONVERSATION_LIST) {
            mActionBarView.removeBackButton();
        } else {
            mActionBarView.setBackButton();
        }
    }

    @Override
    public void showConversation(Conversation conversation) {
        if (mActivity == null) {
            return;
        }
        super.showConversation(conversation);
        int mode = mViewMode.getMode();
        if (mode == ViewMode.SEARCH_RESULTS_LIST || mode == ViewMode.SEARCH_RESULTS_CONVERSATION) {
            mViewMode.enterSearchResultsConversationMode();
            unhideConversationList();
        } else {
            mViewMode.enterConversationMode();
        }
        Fragment convFragment = ConversationViewFragment.newInstance(mAccount, conversation,
                mFolder);
        FragmentTransaction fragmentTransaction = mActivity.getFragmentManager().beginTransaction();
        fragmentTransaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
        fragmentTransaction.replace(R.id.conversation_pane, convFragment);
        fragmentTransaction.commitAllowingStateLoss();
    }

    /**
     * Show the conversation list if it can be shown in the current orientation.
     * @return true if the conversation list was shown
     */
    private boolean unhideConversationList() {
        // Find if the conversation list can be shown
        int mode = mViewMode.getMode();
        final boolean isConversationListShowable = (mode == ViewMode.CONVERSATION
                && mLayout.isConversationListCollapsible()
                || (mode == ViewMode.SEARCH_RESULTS_CONVERSATION));
        if (isConversationListShowable) {
            return mLayout.uncollapseList();
        }
        return false;
    }

    /**
     * Up works as follows:
     * 1) If the user is in a conversation and:
     *  a) the conversation list is hidden (portrait mode), shows the conv list and
     *  stays in conversation view mode.
     *  b) the conversation list is shown, goes back to conversation list mode.
     * 2) If the user is in search results, up exits search.
     * mode and returns the user to whatever view they were in when they began search.
     * 3) If the user is in conversation list mode, there is no up.
     */
    @Override
    public boolean onUpPressed() {
        int mode = mViewMode.getMode();
        if (mode == ViewMode.CONVERSATION) {
            if (!mLayout.isConversationListVisible()) {
                unhideConversationList();
            } else {
                mActivity.onBackPressed();
            }
        } else if (mode == ViewMode.SEARCH_RESULTS_CONVERSATION) {
            if (!mLayout.isConversationListVisible()) {
                unhideConversationList();
            } else {
                mActivity.finish();
            }
        } else if (mode == ViewMode.SEARCH_RESULTS_LIST) {
            mActivity.finish();
        } else if (mode == ViewMode.CONVERSATION_LIST) {
            // This case can only happen if the user is looking at child folders.
            createFolderListFragment(null, mAccount.folderListUri);
            loadAccountInbox();
        }
        return true;
    }

    @Override
    public boolean onBackPressed() {
        popView(false);
        return true;
    }

    /**
     * Pops the "view stack" to the last screen the user was viewing.
     *
     * @param preventClose Whether to prevent closing the app if the stack is empty.
     */
    protected void popView(boolean preventClose) {
        // If the user is in search query entry mode, or the user is viewing search results, exit
        // the mode.
        int mode = mViewMode.getMode();
        if (mode == ViewMode.SEARCH_RESULTS_LIST) {
            mActivity.finish();
        } else if (mViewMode.getMode() == ViewMode.CONVERSATION) {
            // Go to conversation list.
            mViewMode.enterConversationListMode();
        } else if (mode == ViewMode.SEARCH_RESULTS_CONVERSATION) {
            mViewMode.enterSearchResultsListMode();
        } else {
            // There is nothing else to pop off the stack.
            if (!preventClose) {
                mActivity.finish();
            }
        }
    }

    @Override
    public boolean shouldShowFirstConversation() {
        return mConvListContext != null && mConvListContext.isSearchResult();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        boolean handled = true;
        final int id = item.getItemId();
        switch (id) {
            case R.id.y_button: {
                final Settings settings = mActivity.getSettings();
                final boolean showDialog = (settings != null && settings.confirmArchive);
                confirmAndDelete(showDialog, R.plurals.confirm_archive_conversation,
                        mArchiveListener);
                break;
            }
            case R.id.delete: {
                final Settings settings = mActivity.getSettings();
                final boolean showDialog = (settings != null && settings.confirmDelete);
                confirmAndDelete(showDialog, R.plurals.confirm_delete_conversation,
                        mDeleteListener);
                break;
            }
            case R.id.change_folders:
                new FoldersSelectionDialog(mActivity.getActivityContext(), mAccount, this,
                        Collections.singletonList(mCurrentConversation)).show();
                break;
            case R.id.inside_conversation_unread:
                updateCurrentConversation(ConversationColumns.READ, false);
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
                mConversationListFragment.requestDelete(mMuteListener);
                break;
            case R.id.report_spam:
                mConversationListFragment.requestDelete(mSpamListener);
                break;
            default:
                handled = false;
                break;
        }
        return handled || super.onOptionsItemSelected(item);
    }

    /**
     * An object that performs an action on the conversation database. This is an
     * ActionCompleteListener since this is called <b>after</a> the conversation list has animated
     * the conversation away. Once the animation is completed, the {@link #onActionComplete()}
     * method is called which performs the correct data operation.
     */
    private class TwoPaneDestructiveActionListener extends DestructiveActionListener {
        public TwoPaneDestructiveActionListener(int action) {
            super(action);
        }

        @Override
        public void onActionComplete() {
            final ArrayList<Conversation> single = new ArrayList<Conversation>();
            single.add(mCurrentConversation);
            int next = -1;
            int pref = getAutoAdvanceSetting(mActivity);
            Cursor c = mConversationListCursor;
            int updatedPosition = -1;
            int position = mCurrentConversation.position;
            if (c != null) {
                switch (pref) {
                    case AutoAdvance.NEWER:
                        if (position - 1 >= 0) {
                            // This conversation was deleted, so to get to the previous
                            // conversation, show what is now in its position - 1.
                            next = position - 1;
                            // The position is correct, since no items before this have
                            // been deleted.
                            updatedPosition = position - 1;
                        }
                        break;
                    case AutoAdvance.OLDER:
                        if (position + 1 < c.getCount()) {
                            // This conversation was deleted, so to get to the next
                            // conversation, show what is now in position + 1.
                            next = position + 1;
                            // Since this conversation was deleted, update the conversation
                            // we are showing to have the position this conversation was in.
                            updatedPosition = position;
                        }
                        break;
                }
            }
            TwoPaneController.this.onActionComplete();
            mConversationListFragment.onUndoAvailable(new UndoOperation(1, mAction));
            if (next != -1) {
                mConversationListFragment.viewConversation(next);
                mCurrentConversation.position = updatedPosition;
            } else {
                onBackPressed();
            }
            performConversationAction(single);
            mConversationListFragment.requestListRefresh();
        }
    }

    protected void requestDelete(final ActionCompleteListener listener) {
        mConversationListFragment.requestDelete(listener);
    }

    @Override
    protected DestructiveActionListener getFolderDestructiveActionListener() {
        return mFolderChangeListener;
    }
}
