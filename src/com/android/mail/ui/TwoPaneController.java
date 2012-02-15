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
        // TODO(viki): Auto-generated constructor stub
    }

    /**
     * Display the conversation list fragment.
     * @param show
     */
    private void initializeConversationListFragment(boolean show) {
        if (show) {
            mViewMode.enterConversationListMode();
        }
        FragmentTransaction fragmentTransaction = mActivity.getFragmentManager().beginTransaction();
        // Use cross fading animation.
        fragmentTransaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
        Fragment conversationListFragment = ConversationListFragment
                .newInstance(mConvListContext);
        fragmentTransaction.replace(R.id.conversation_list_pane, conversationListFragment);
        fragmentTransaction.commitAllowingStateLoss();
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
        // TODO: auto-generated method stub.
    }

    @Override
    public boolean onCreate(Bundle savedState) {
        mActivity.setContentView(R.layout.two_pane_activity);

        mLayout = (TwoPaneLayout) mActivity.findViewById(R.id.two_pane_activity);
        mLayout.initializeLayout(mActivity.getApplicationContext());

        // The tablet layout needs to refer to mode changes.
        mViewMode.addListener(mLayout);
        // The activity controller needs to listen to layout changes.
        mLayout.setListener(this);

        final boolean isParentInitialized = super.onCreate(savedState);
        if (isParentInitialized && savedState == null) {
            // Show a Label list, in the real application
            // renderLabelList();
            // In our case, we show a conversation list for everything.
        }
        return isParentInitialized;
    }

    @Override
    public void onViewModeChanged(int newMode) {
        super.onViewModeChanged(newMode);
        if (newMode != ViewMode.CONVERSATION) {
            // Clear this flag if the user jumps out of conversation mode
            // before a load completes.
            mJumpToFirstConversation = false;
        }
    }

    @Override
    public void resetActionBarIcon() {
        if (mViewMode.getMode() == ViewMode.CONVERSATION_LIST) {
            mActionBarView.removeBackButton();
        } else {
            mActionBarView.setBackButton();
        }
    }

}
