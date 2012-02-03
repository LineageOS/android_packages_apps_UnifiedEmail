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
import android.text.TextUtils;
import android.view.Window;

import com.android.mail.ConversationListContext;
import com.android.mail.R;

/**
 * Controller for one-pane Mail activity. One Pane is used for phones, where screen real estate is
 * limited. This controller also does the layout, since the layout is simpler in the one pane case.
 */

// Called OnePaneActivityController in Gmail.
public final class OnePaneController extends AbstractActivityController {

    /**
     * @param activity
     * @param viewMode
     */
    public OnePaneController(MailActivity activity, ViewMode viewMode) {
        super(activity, viewMode);
        // TODO(viki): Auto-generated constructor stub
    }

    @Override
    public void resetActionBarIcon() {
        final int mode = mViewMode.getMode();
        if ((mode == ViewMode.CONVERSATION_LIST && mConversationListContext.isSearchResult())
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
        // TODO(viki): Auto-generated method stub
        return false;
    }

    @Override
    public void onViewModeChanged(int newMode) {
        super.onViewModeChanged(newMode);
        if (newMode == ViewMode.CONVERSATION_LIST) {
            // TODO(viki): Get name of inbox from the Account
            final String inboxFolder = "inbox";
            ConversationListContext listContext = ConversationListContext.forFolder(mContext,
                    mAccount, inboxFolder);
            showConversationList(listContext);
        }

        // We don't want to invalidate the options menu when switching to conversation
        // mode, as it will happen when the conversation finishes loading.
        if (newMode != ViewMode.CONVERSATION) {
            mActivity.invalidateOptionsMenu();
        }
    }

    @Override
    public void showConversationList(ConversationListContext listContext) {
        FragmentTransaction fragmentTransaction = mActivity.getFragmentManager().beginTransaction();
        fragmentTransaction.addToBackStack(null);
        final boolean accountChanged = false;
        // TODO(viki): This account transition looks strange in two pane mode. Revisit as the app
        // is coming together and improve the look and feel.
        final int transition = accountChanged ? FragmentTransaction.TRANSIT_FRAGMENT_FADE :
                FragmentTransaction.TRANSIT_FRAGMENT_OPEN;
        fragmentTransaction.setTransition(transition);

        Fragment conversationListFragment = ConversationListFragment
                .newInstance(mConversationListContext);
        fragmentTransaction.replace(R.id.content_pane, conversationListFragment);

        fragmentTransaction.commitAllowingStateLoss();
        resetActionBarIcon();
    }

}
