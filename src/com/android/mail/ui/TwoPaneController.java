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
import com.android.mail.utils.LogUtils;

import android.app.Fragment;
import android.app.FragmentTransaction;
import android.os.Bundle;
import android.view.Window;

/**
 * Controller for one-pane Mail activity. One Pane is used for phones, where screen real estate is
 * limited.
 */

// Called OnePaneActivityController in Gmail.
public final class TwoPaneController extends AbstractActivityController {
    private boolean mJumpToFirstConversation;
    private TwoPaneLayout mLayout;

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
            mViewMode.enterConversationListMode();
        }
        renderConversationList();
    }

    /**
     * Render the conversation list in the correct pane.
     */
    private void renderConversationList() {
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
        FolderListFragment folderListFragment = FolderListFragment.newInstance(this,
                mAccount.folderListUri);
        FragmentTransaction fragmentTransaction = mActivity.getFragmentManager().beginTransaction();
        fragmentTransaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
        fragmentTransaction.replace(R.id.folders_pane, folderListFragment);
        fragmentTransaction.commitAllowingStateLoss();
        // Since we are showing the folder list, we are at the start of the view
        // stack.
        resetActionBarIcon();
        attachFolderList(folderListFragment);
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
        mViewMode.enterConversationMode();
        Fragment convFragment = ConversationViewFragment.newInstance(mAccount, conversation);
        FragmentTransaction fragmentTransaction = mActivity.getFragmentManager().beginTransaction();
        fragmentTransaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
        fragmentTransaction.replace(R.id.conversation_pane, convFragment);
        fragmentTransaction.commitAllowingStateLoss();
    }

    /**
     * Show the conversation list if it can be shown in the current orientation.
     * @return true if the conversation list was shown
     */
    private boolean unhideConversationList(){
        // Find if the conversation list can be shown
        final boolean isConversationListShowable =
                (mViewMode.getMode() == ViewMode.CONVERSATION &&
                mLayout.isConversationListCollapsible());
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
        } else if (mode == ViewMode.SEARCH_RESULTS) {
            mActivity.finish();
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
        if (mConvListContext != null && mConvListContext.isSearchResult()) {
            mActivity.finish();
        } else if (mViewMode.getMode() == ViewMode.CONVERSATION) {
            // Go to conversation list.
            mViewMode.enterConversationListMode();
        } else {
            // There is nothing else to pop off the stack.
            if (!preventClose) {
                mActivity.finish();
            }
        }
    }
}
