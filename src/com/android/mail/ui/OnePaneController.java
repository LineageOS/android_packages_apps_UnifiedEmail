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
import android.app.FragmentTransaction;
import android.os.Bundle;

import com.android.mail.ConversationListContext;
import com.android.mail.R;
import com.android.mail.providers.Conversation;

/**
 * Controller for one-pane Mail activity. One Pane is used for phones, where screen real estate is
 * limited. This controller also does the layout, since the layout is simpler in the one pane case.
 */

// Called OnePaneActivityController in Gmail.
public final class OnePaneController extends AbstractActivityController {
    private boolean mConversationListVisible = false;
    /**
     * @param activity
     * @param viewMode
     */
    public OnePaneController(MailActivity activity, ViewMode viewMode) {
        super(activity, viewMode);
    }

    @Override
    public void resetActionBarIcon() {
        final int mode = mViewMode.getMode();
        if ((mode == ViewMode.CONVERSATION_LIST && mConvListContext.isSearchResult())
                || mode == ViewMode.CONVERSATION || mode == ViewMode.FOLDER_LIST) {
            mActionBarView.setBackButton();
        } else {
            mActionBarView.removeBackButton();
        }
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
        mViewMode.enterConversationListMode();

        // TODO(viki): Check if the account has been changed since the previous time.
        final boolean accountChanged = false;
        // TODO(viki): This account transition looks strange in two pane mode.
        // Revisit as the app is coming together and improve the look and feel.
        final int transition = accountChanged ? FragmentTransaction.TRANSIT_FRAGMENT_FADE
                : FragmentTransaction.TRANSIT_FRAGMENT_OPEN;
        if (listContext == null) {
            listContext = getCurrentListContext();
        }

        Fragment conversationListFragment = ConversationListFragment.newInstance(listContext);
        replaceFragment(conversationListFragment, transition);
        mConversationListVisible = true;
    }

    @Override
    public void showConversation(Conversation conversation) {
        mViewMode.enterConversationMode();
        replaceFragment(ConversationViewFragment.newInstance(mAccount, conversation),
                FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
        mConversationListVisible = false;
    }

    @Override
    public void showFolderList() {
        mViewMode.enterFolderListMode();
        replaceFragment(FolderListFragment.newInstance(this, mAccount.folderListUri),
                FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
        mConversationListVisible = false;
    }

    private void replaceFragment(Fragment fragment, int transition) {
        FragmentTransaction fragmentTransaction = mActivity.getFragmentManager().beginTransaction();
        fragmentTransaction.addToBackStack(null);
        fragmentTransaction.setTransition(transition);
        fragmentTransaction.replace(R.id.content_pane, fragment);
        fragmentTransaction.commitAllowingStateLoss();
        resetActionBarIcon();
    }

    @Override
    public boolean onBackPressed() {
        int mode = mViewMode.getMode();
        if (mode == ViewMode.CONVERSATION_LIST) {
            mActivity.finish();
            return true;
        } else if (mode == ViewMode.CONVERSATION || mode == ViewMode.FOLDER_LIST) {
            transitionBackToConversationListMode();
        }
        return false;
    }

    @Override
    public boolean onUpPressed() {
        int mode = mViewMode.getMode();
        if (mode == ViewMode.CONVERSATION_LIST || mode == ViewMode.CONVERSATION
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
        resetActionBarIcon();
    }
}
