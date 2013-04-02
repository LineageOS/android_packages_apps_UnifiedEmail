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
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.FrameLayout;

import com.android.mail.ConversationListContext;
import com.android.mail.R;
import com.android.mail.providers.Account;
import com.android.mail.providers.Conversation;
import com.android.mail.providers.Folder;
import com.android.mail.utils.LogUtils;
import com.android.mail.utils.Utils;

/**
 * Controller for two-pane Mail activity. Two Pane is used for tablets, where screen real estate
 * abounds.
 */
public final class TwoPaneController extends AbstractActivityController {
    private TwoPaneLayout mLayout;
    private Conversation mConversationToShow;

    public TwoPaneController(MailActivity activity, ViewMode viewMode) {
        super(activity, viewMode);
    }

    /**
     * Display the conversation list fragment.
     */
    private void initializeConversationListFragment() {
        if (Intent.ACTION_SEARCH.equals(mActivity.getIntent().getAction())) {
            if (shouldEnterSearchConvMode()) {
                mViewMode.enterSearchResultsConversationMode();
            } else {
                mViewMode.enterSearchResultsListMode();
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
        Fragment conversationListFragment = ConversationListFragment.newInstance(mConvListContext);
        fragmentTransaction.replace(R.id.conversation_list_pane, conversationListFragment,
                TAG_CONVERSATION_LIST);
        fragmentTransaction.commitAllowingStateLoss();
    }

    @Override
    public boolean doesActionChangeConversationListVisibility(int action) {
        switch (action) {
            case R.id.settings:
            case R.id.compose:
            case R.id.help_info_menu_item:
            case R.id.manage_folders_item:
            case R.id.folder_options:
            case R.id.feedback_menu_item:
                return true;
            default:
                return false;
        }
    }

    /**
     * Render the folder list in the correct pane.
     */
    private void renderFolderList() {
        if (mActivity == null) {
            return;
        }
        createFolderListFragment(FolderListFragment.ofDrawer());
    }

    /**
     * Create a {@link FolderListFragment} for trees with the specified parent
     * @param parent the parent folder whose children need to be displayed in this list
     */
    private void createFolderTree(Folder parent) {
        setHierarchyFolder(parent);
        createFolderListFragment(FolderListFragment.ofTree(parent));
    }

    private void createFolderListFragment(Fragment folderList) {
        // Create a sectioned FolderListFragment.
        FragmentTransaction fragmentTransaction = mActivity.getFragmentManager().beginTransaction();
        if (Utils.useFolderListFragmentTransition(mActivity.getActivityContext())) {
            fragmentTransaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
        }
        fragmentTransaction.replace(R.id.content_pane, folderList, TAG_FOLDER_LIST);
        fragmentTransaction.commitAllowingStateLoss();
        // We only set the action bar if the viewmode has been set previously. Otherwise, we leave
        // the action bar in the state it is currently in.
        if (mViewMode.getMode() != ViewMode.UNKNOWN) {
            resetActionBarIcon();
        }
    }

    @Override
    protected boolean isConversationListVisible() {
        return !mLayout.isConversationListCollapsed();
    }

    @Override
    public void showConversationList(ConversationListContext listContext) {
        super.showConversationList(listContext);
        initializeConversationListFragment();
    }

    @Override
    public boolean onCreate(Bundle savedState) {
        mActivity.setContentView(R.layout.two_pane_activity);
        mLayout = (TwoPaneLayout) mActivity.findViewById(R.id.two_pane_activity);
        if (mLayout == null) {
            // We need the layout for everything. Crash early if it is null.
            LogUtils.wtf(LOG_TAG, "mLayout is null!");
        }
        mLayout.setController(this, Intent.ACTION_SEARCH.equals(mActivity.getIntent().getAction()));

        // 2-pane layout is the main listener of view mode changes, and issues secondary
        // notifications upon animation completion:
        // (onConversationVisibilityChanged, onConversationListVisibilityChanged)
        mViewMode.addListener(mLayout);
        return super.onCreate(savedState);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        if (hasFocus && !mLayout.isConversationListCollapsed()) {
            // The conversation list is visible.
            informCursorVisiblity(true);
        }
    }

    @Override
    public void onFolderChanged(Folder folder) {
        super.onFolderChanged(folder);
        exitCabMode();
    }

    @Override
    public void onFolderSelected(Folder folder) {
        if (folder.hasChildren && !folder.equals(getHierarchyFolder())) {
            // Replace this fragment with a new FolderListFragment
            // showing this folder's children if we are not already looking
            // at the child view for this folder.
            createFolderTree(folder);
            // Show the up affordance when digging into child folders.
            mActionBarView.setBackButton();
        } else {
            setHierarchyFolder(folder);
        }
        super.onFolderSelected(folder);
    }

    private void goUpFolderHierarchy(Folder current) {
        Folder parent = current.parent;
        if (parent.parent != null) {
            createFolderTree(parent.parent);
            // Show the up affordance when digging into child folders.
            mActionBarView.setBackButton();
        } else {
            onFolderSelected(parent);
        }
    }

    @Override
    public void onViewModeChanged(int newMode) {
        super.onViewModeChanged(newMode);
        if (newMode != ViewMode.WAITING_FOR_ACCOUNT_INITIALIZATION) {
            // Clear the wait fragment
            hideWaitForInitialization();
        }
        // In conversation mode, if the conversation list is not visible, then the user cannot
        // see the selected conversations. Disable the CAB mode while leaving the selected set
        // untouched.
        // When the conversation list is made visible again, try to enable the CAB
        // mode if any conversations are selected.
        if (newMode == ViewMode.CONVERSATION || newMode == ViewMode.CONVERSATION_LIST){
            enableOrDisableCab();
        }
    }

    @Override
    public void onConversationVisibilityChanged(boolean visible) {
        super.onConversationVisibilityChanged(visible);
        if (!visible) {
            mPagerController.hide(false /* changeVisibility */);
        } else if (mConversationToShow != null) {
            mPagerController.show(mAccount, mFolder, mConversationToShow,
                    false /* changeVisibility */);
            mConversationToShow = null;
        }
    }

    @Override
    public void onConversationListVisibilityChanged(boolean visible) {
        super.onConversationListVisibilityChanged(visible);
        enableOrDisableCab();
    }

    @Override
    public void resetActionBarIcon() {
        // On two-pane, the back button is only removed in the conversation list mode, and shown
        // for every other condition.
        if (mViewMode.isListMode() || mViewMode.isWaitingForSync()) {
            mActionBarView.removeBackButton();
        } else {
            mActionBarView.setBackButton();
        }
    }

    /**
     * Enable or disable the CAB mode based on the visibility of the conversation list fragment.
     */
    private void enableOrDisableCab() {
        if (mLayout.isConversationListCollapsed()) {
            disableCabMode();
        } else {
            enableCabMode();
        }
    }

    @Override
    protected void showConversation(Conversation conversation, boolean inLoaderCallbacks) {
        super.showConversation(conversation, inLoaderCallbacks);

        // 2-pane can ignore inLoaderCallbacks because it doesn't use
        // FragmentManager.popBackStack().

        if (mActivity == null) {
            return;
        }
        if (conversation == null) {
            handleBackPress();
            return;
        }
        // If conversation list is not visible, then the user cannot see the CAB mode, so exit it.
        // This is needed here (in addition to during viewmode changes) because orientation changes
        // while viewing a conversation don't change the viewmode: the mode stays
        // ViewMode.CONVERSATION and yet the conversation list goes in and out of visibility.
        enableOrDisableCab();

        // When a mode change is required, wait for onConversationVisibilityChanged(), the signal
        // that the mode change animation has finished, before rendering the conversation.
        mConversationToShow = conversation;

        final int mode = mViewMode.getMode();
        LogUtils.i(LOG_TAG, "IN TPC.showConv, oldMode=%s conv=%s", mode, mConversationToShow);
        if (mode == ViewMode.SEARCH_RESULTS_LIST || mode == ViewMode.SEARCH_RESULTS_CONVERSATION) {
            mViewMode.enterSearchResultsConversationMode();
        } else {
            mViewMode.enterConversationMode();
        }
        // load the conversation immediately if we're already in conversation mode
        if (!mLayout.isModeChangePending()) {
            onConversationVisibilityChanged(true);
        } else {
            LogUtils.i(LOG_TAG, "TPC.showConversation will wait for TPL.animationEnd to show!");
        }
    }

    @Override
    public void setCurrentConversation(Conversation conversation) {
        // Order is important! We want to calculate different *before* the superclass changes
        // mCurrentConversation, so before super.setCurrentConversation().
        final long oldId = mCurrentConversation != null ? mCurrentConversation.id : -1;
        final long newId = conversation != null ? conversation.id : -1;
        final boolean different = oldId != newId;

        // This call might change mCurrentConversation.
        super.setCurrentConversation(conversation);

        final ConversationListFragment convList = getConversationListFragment();
        if (convList != null && conversation != null) {
            convList.setSelected(conversation.position, different);
        }
    }

    @Override
    public void showWaitForInitialization() {
        super.showWaitForInitialization();

        FragmentTransaction fragmentTransaction = mActivity.getFragmentManager().beginTransaction();
        fragmentTransaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
        fragmentTransaction.replace(R.id.wait, getWaitFragment(), TAG_WAIT);
        fragmentTransaction.commitAllowingStateLoss();
    }

    @Override
    protected void hideWaitForInitialization() {
        final WaitFragment waitFragment = getWaitFragment();
        if (waitFragment == null) {
            // We aren't showing a wait fragment: nothing to do
            return;
        }
        // Remove the existing wait fragment from the back stack.
        final FragmentTransaction fragmentTransaction =
                mActivity.getFragmentManager().beginTransaction();
        fragmentTransaction.remove(waitFragment);
        fragmentTransaction.commitAllowingStateLoss();
        super.hideWaitForInitialization();
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
    public boolean handleUpPress() {
        int mode = mViewMode.getMode();
        if (mode == ViewMode.CONVERSATION) {
            handleBackPress();
        } else if (mode == ViewMode.SEARCH_RESULTS_CONVERSATION) {
            if (mLayout.isConversationListCollapsed()
                    || (ConversationListContext.isSearchResult(mConvListContext) && !Utils.
                            showTwoPaneSearchResults(mActivity.getApplicationContext()))) {
                handleBackPress();
            } else {
                mActivity.finish();
            }
        } else if (mode == ViewMode.SEARCH_RESULTS_LIST) {
            mActivity.finish();
        } else if (mode == ViewMode.CONVERSATION_LIST) {
            popView(true);
        }
        return true;
    }

    @Override
    public boolean handleBackPress() {
        // Clear any visible undo bars.
        mToastBar.hide(false);
        popView(false);
        return true;
    }

    /**
     * Pops the "view stack" to the last screen the user was viewing.
     *
     * @param preventClose Whether to prevent closing the app if the stack is empty.
     */
    protected void popView(boolean preventClose) {
        // If the user is in search query entry mode, or the user is viewing
        // search results, exit
        // the mode.
        int mode = mViewMode.getMode();
        if (mode == ViewMode.SEARCH_RESULTS_LIST) {
            mActivity.finish();
        } else if (mode == ViewMode.CONVERSATION) {
            // Go to conversation list.
            mViewMode.enterConversationListMode();
        } else if (mode == ViewMode.SEARCH_RESULTS_CONVERSATION) {
            mViewMode.enterSearchResultsListMode();
        } else {
            // The Folder List fragment can be null for monkeys where we get a back before the
            // folder list has had a chance to initialize.
            final FolderListFragment folderList = getFolderListFragment();
            if (mode == ViewMode.CONVERSATION_LIST && folderList != null
                    && folderList.showingHierarchy()) {
                // If the user navigated via the left folders list into a child folder,
                // back should take the user up to the parent folder's conversation list.
                final Folder hierarchyFolder = getHierarchyFolder();
                if (hierarchyFolder.parent != null) {
                    goUpFolderHierarchy(hierarchyFolder);
                } else  {
                    // Show inbox; we are at the top of the hierarchy we were
                    // showing, and it doesn't have a parent, so we must want to
                    // the basic account folder list.
                    createFolderListFragment(FolderListFragment.ofDrawer());
                    loadAccountInbox();
                }
            } else if (!preventClose) {
                // There is nothing else to pop off the stack.
                mActivity.finish();
            }
        }
    }

    @Override
    public void exitSearchMode() {
        final int mode = mViewMode.getMode();
        if (mode == ViewMode.SEARCH_RESULTS_LIST
                || (mode == ViewMode.SEARCH_RESULTS_CONVERSATION
                        && Utils.showTwoPaneSearchResults(mActivity.getApplicationContext()))) {
            mActivity.finish();
        }
    }

    @Override
    public boolean shouldShowFirstConversation() {
        return Intent.ACTION_SEARCH.equals(mActivity.getIntent().getAction())
                && shouldEnterSearchConvMode();
    }

    private int getUndoBarWidth(int mode, ToastBarOperation op) {
        int resId = -1;
        switch (mode) {
            case ViewMode.SEARCH_RESULTS_LIST:
                resId = R.dimen.undo_bar_width_search_list;
                break;
            case ViewMode.CONVERSATION_LIST:
                resId = R.dimen.undo_bar_width_list;
                break;
            case ViewMode.SEARCH_RESULTS_CONVERSATION:
            case ViewMode.CONVERSATION:
                if (op.isBatchUndo() && !mLayout.isConversationListCollapsed()) {
                    resId = R.dimen.undo_bar_width_conv_list;
                } else {
                    resId = R.dimen.undo_bar_width_conv;
                }
        }
        return resId != -1 ? (int) mContext.getResources().getDimension(resId) : -1;
    }

    @Override
    public void onUndoAvailable(ToastBarOperation op) {
        final int mode = mViewMode.getMode();
        final ConversationListFragment convList = getConversationListFragment();

        repositionToastBar(op);

        switch (mode) {
            case ViewMode.SEARCH_RESULTS_LIST:
            case ViewMode.CONVERSATION_LIST:
                if (convList != null) {
                    mToastBar.show(
                            getUndoClickedListener(convList.getAnimatedAdapter()),
                            0,
                            Utils.convertHtmlToPlainText
                                (op.getDescription(mActivity.getActivityContext())),
                            true, /* showActionIcon */
                            R.string.undo,
                            true,  /* replaceVisibleToast */
                            op);
                }
                break;
            case ViewMode.SEARCH_RESULTS_CONVERSATION:
            case ViewMode.CONVERSATION:
                if (convList != null) {
                    mToastBar.show(getUndoClickedListener(convList.getAnimatedAdapter()), 0,
                            Utils.convertHtmlToPlainText
                                (op.getDescription(mActivity.getActivityContext())),
                            true, /* showActionIcon */
                            R.string.undo, true, /* replaceVisibleToast */
                            op);
                }
                break;
        }
    }

    public void repositionToastBar(ToastBarOperation op) {
        final int mode = mViewMode.getMode();
        final FrameLayout.LayoutParams params =
                (FrameLayout.LayoutParams) mToastBar.getLayoutParams();
        int undoBarWidth = getUndoBarWidth(mode, op);
        switch (mode) {
            case ViewMode.SEARCH_RESULTS_LIST:
            case ViewMode.CONVERSATION_LIST:
                params.width = undoBarWidth - params.leftMargin - params.rightMargin;
                params.gravity = Gravity.BOTTOM | Gravity.RIGHT;
                mToastBar.setLayoutParams(params);
                mToastBar.setConversationMode(false);
                break;
            case ViewMode.SEARCH_RESULTS_CONVERSATION:
            case ViewMode.CONVERSATION:
                if (op.isBatchUndo() && !mLayout.isConversationListCollapsed()) {
                    // Show undo bar in the conversation list.
                    params.gravity = Gravity.BOTTOM | Gravity.LEFT;
                    params.width = undoBarWidth - params.leftMargin - params.rightMargin;
                    mToastBar.setLayoutParams(params);
                    mToastBar.setConversationMode(false);
                } else {
                    // Show undo bar in the conversation.
                    params.gravity = Gravity.BOTTOM | Gravity.RIGHT;
                    params.width = undoBarWidth - params.leftMargin - params.rightMargin;
                    mToastBar.setLayoutParams(params);
                    mToastBar.setConversationMode(true);
                }
                break;
        }
    }

    @Override
    protected void hideOrRepositionToastBar(final boolean animated) {
        final int oldViewMode = mViewMode.getMode();
        mLayout.postDelayed(new Runnable() {
                @Override
            public void run() {
                if (/* the touch did not open a conversation */oldViewMode == mViewMode.getMode() ||
                /* animation has ended */!mToastBar.isAnimating()) {
                    mToastBar.hide(animated);
                } else {
                    // the touch opened a conversation, reposition undo bar
                    repositionToastBar(mToastBar.getOperation());
                }
            }
        },
        /* Give time for ViewMode to change from the touch */
        mContext.getResources().getInteger(R.integer.dismiss_undo_bar_delay_ms));
    }

    @Override
    public void onError(final Folder folder, boolean replaceVisibleToast) {
        final int mode = mViewMode.getMode();
        final FrameLayout.LayoutParams params =
                (FrameLayout.LayoutParams) mToastBar.getLayoutParams();
        switch (mode) {
            case ViewMode.SEARCH_RESULTS_LIST:
            case ViewMode.CONVERSATION_LIST:
                params.width = mLayout.computeConversationListWidth()
                        - params.leftMargin - params.rightMargin;
                params.gravity = Gravity.BOTTOM | Gravity.RIGHT;
                mToastBar.setLayoutParams(params);
                break;
            case ViewMode.SEARCH_RESULTS_CONVERSATION:
            case ViewMode.CONVERSATION:
                // Show error bar in the conversation list.
                params.gravity = Gravity.BOTTOM | Gravity.LEFT;
                params.width = mLayout.computeConversationListWidth()
                        - params.leftMargin - params.rightMargin;
                mToastBar.setLayoutParams(params);
                break;
        }

        showErrorToast(folder, replaceVisibleToast);
    }
}
