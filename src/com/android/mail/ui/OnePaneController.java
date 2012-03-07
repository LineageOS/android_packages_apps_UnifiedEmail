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

import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.net.Uri;
import android.os.Bundle;

import com.android.mail.ConversationListContext;
import com.android.mail.R;
import com.android.mail.providers.Conversation;
import com.android.mail.providers.Folder;

/**
 * Controller for one-pane Mail activity. One Pane is used for phones, where screen real estate is
 * limited. This controller also does the layout, since the layout is simpler in the one pane case.
 */

// Called OnePaneActivityController in Gmail.
public final class OnePaneController extends AbstractActivityController {
    private static final String FOLDER_LIST_TRANSACTION_KEY = "folder-list-transaction";
    private static final String CONVERSATION_LIST_TRANSACTION_KEY = "conversation-list-transaction";
    private static final String CONVERSATION_TRANSACTION_KEY = "conversation-transaction";
    private static final int INVALID_ID = -1;
    private boolean mConversationListVisible = false;
    private int mLastConversationListTransactionId = INVALID_ID;
    private int mLastConversationTransactionId = INVALID_ID;
    private int mLastFolderListTransactionId = INVALID_ID;
    private Folder mInbox;
    /**
     * @param activity
     * @param viewMode
     */
    public OnePaneController(MailActivity activity, ViewMode viewMode) {
        super(activity, viewMode);
    }

    @Override
    protected void restoreState(Bundle inState) {
        super.restoreState(inState);
        // TODO(mindyp) handle saved state.
        if (inState != null) {
            mLastFolderListTransactionId = inState.getInt(FOLDER_LIST_TRANSACTION_KEY, INVALID_ID);
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
        outState.putInt(CONVERSATION_LIST_TRANSACTION_KEY, mLastConversationListTransactionId);
        outState.putInt(CONVERSATION_TRANSACTION_KEY, mLastConversationTransactionId);
    }

    @Override
    public void resetActionBarIcon() {
        final int mode = mViewMode.getMode();
        // If the settings aren't loaded yet, we may not know what the default
        // inbox is, so err toward this being the account inbox.
        if ((mCachedSettings != null && mConvListContext != null && !inInbox())
                || (mode == ViewMode.CONVERSATION_LIST && mConvListContext.isSearchResult())
                || mode == ViewMode.CONVERSATION || mode == ViewMode.FOLDER_LIST) {
            mActionBarView.setBackButton();
        } else {
            mActionBarView.removeBackButton();
        }
    }

    private boolean inInbox() {
        Uri inboxUri = mCachedSettings != null ? mCachedSettings.defaultInbox : null;
        return mConvListContext != null && mConvListContext.folder != null ? (!mConvListContext
                .isSearchResult() && mConvListContext.folder.uri.equals(inboxUri)) : false;
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
    }

    @Override
    public void showConversationList(ConversationListContext listContext) {
        // TODO(viki): Check if the account has been changed since the previous
        // time.
        mViewMode.enterConversationListMode();
        final boolean accountChanged = false;
        // TODO(viki): This account transition looks strange in two pane mode.
        // Revisit as the app is coming together and improve the look and feel.
        final int transition = accountChanged ? FragmentTransaction.TRANSIT_FRAGMENT_FADE
                : FragmentTransaction.TRANSIT_FRAGMENT_OPEN;
        if (listContext == null) {
            listContext = getCurrentListContext();
        }
        Fragment conversationListFragment = ConversationListFragment.newInstance(listContext);
        if (!inInbox()) {
            // Maintain fragment transaction history so we can get back to the
            // fragment used to launch this list.
            replaceFragment(conversationListFragment, transition);
        } else {
            // If going to the inbox, clear the folder list transaction history.
            mInbox = listContext.folder;
            mLastConversationListTransactionId = replaceFragment(conversationListFragment,
                    transition);
            mLastFolderListTransactionId = INVALID_ID;
        }
        mConversationListVisible = true;
    }

    @Override
    public void showConversation(Conversation conversation) {
        mViewMode.enterConversationMode();
        mLastConversationTransactionId = replaceFragment(
                ConversationViewFragment.newInstance(mAccount, conversation),
                FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
        mConversationListVisible = false;
    }

    @Override
    public void showFolderList() {
        mViewMode.enterFolderListMode();
        mLastFolderListTransactionId = replaceFragment(
                FolderListFragment.newInstance(this, mAccount.folderListUri),
                FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
        mConversationListVisible = false;
    }

    private int replaceFragment(Fragment fragment, int transition) {
        FragmentTransaction fragmentTransaction = mActivity.getFragmentManager().beginTransaction();
        fragmentTransaction.addToBackStack(null);
        fragmentTransaction.setTransition(transition);
        fragmentTransaction.replace(R.id.content_pane, fragment);
        int transactionId = fragmentTransaction.commitAllowingStateLoss();
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
        } else if (mode == ViewMode.CONVERSATION_LIST && !inInbox()) {
            if (isTransactionIdValid(mLastFolderListTransactionId)) {
                // Go back to previous label list.
                mViewMode.enterFolderListMode();
                mActivity.getFragmentManager().popBackStack(mLastFolderListTransactionId, 0);
            } else {
                // Go back to Inbox.
                transitionToInbox();
            }
        } else if (mode == ViewMode.CONVERSATION) {
            transitionBackToConversationListMode();
        } else {
            mActivity.finish();
        }
        return true;
    }

    private void transitionToInbox() {
        mViewMode.enterConversationListMode();
        ConversationListContext listContext = ConversationListContext.forFolder(mContext,
                mAccount, mInbox);
        // Set the correct context for what the conversation view will be now.
        onFolderChanged(mInbox);
        if (isTransactionIdValid(mLastConversationListTransactionId)) {
           // showConversationList(listContext);
            mActivity.getFragmentManager().popBackStack(mLastConversationListTransactionId, 0);
            resetActionBarIcon();
        } else {
            showConversationList(listContext);
        }
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
        if ((!inInbox() && mode == ViewMode.CONVERSATION_LIST) || mode == ViewMode.CONVERSATION
                || mode == ViewMode.FOLDER_LIST) {
            // Same as go back.
            mActivity.onBackPressed();
        } else if (mode == ViewMode.SEARCH_RESULTS) {
            mActivity.finish();
        }
        return true;
    }

    private void transitionBackToConversationListMode() {
        mViewMode.enterConversationListMode();
        mActivity.getFragmentManager().popBackStack(mLastConversationTransactionId,
                FragmentManager.POP_BACK_STACK_INCLUSIVE);
        resetActionBarIcon();
    }
}
