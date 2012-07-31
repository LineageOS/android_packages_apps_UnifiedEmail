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
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Html;
import android.text.TextUtils;
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

// Called TwoPaneActivityController in Gmail.
public final class TwoPaneController extends AbstractActivityController {
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
            if (Intent.ACTION_SEARCH.equals(mActivity.getIntent().getAction())) {
                if (Utils.showTwoPaneSearchResults(mActivity.getActivityContext())) {
                    mViewMode.enterSearchResultsConversationMode();
                } else {
                    mViewMode.enterSearchResultsListMode();
                }
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
        Fragment conversationListFragment = ConversationListFragment.newInstance(mConvListContext);
        fragmentTransaction.replace(R.id.conversation_list_pane, conversationListFragment,
                TAG_CONVERSATION_LIST);
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
        setHierarchyFolder(parent);
        FolderListFragment folderListFragment = FolderListFragment.newInstance(parent, uri);
        FragmentTransaction fragmentTransaction = mActivity.getFragmentManager().beginTransaction();
        fragmentTransaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
        fragmentTransaction.replace(R.id.content_pane, folderListFragment, TAG_FOLDER_LIST);
        fragmentTransaction.commitAllowingStateLoss();
        // Since we are showing the folder list, we are at the start of the view
        // stack.
        resetActionBarIcon();
    }

    @Override
    protected boolean isConversationListVisible() {
        return !mLayout.isConversationListCollapsed();
    }

    @Override
    public void showConversationList(ConversationListContext listContext) {
        super.showConversationList(listContext);
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
            // We need the layout for everything. Crash early if it is null.
            LogUtils.wtf(LOG_TAG, "mLayout is null!");
        }
        mLayout.initializeLayout(mActivity.getApplicationContext(),
                Intent.ACTION_SEARCH.equals(mActivity.getIntent().getAction()));

        // The tablet layout needs to refer to mode changes.
        mViewMode.addListener(mLayout);
        // The activity controller needs to listen to layout changes.
        mLayout.setListener(this);
        final boolean isParentInitialized = super.onCreate(savedState);
        return isParentInitialized;
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        if (hasFocus && !mLayout.isConversationListCollapsed()) {
            // The conversation list is visible.
            Utils.setConversationCursorVisibility(mConversationListCursor, true);
        }
    }

    @Override
    public void onAccountChanged(Account account) {
        super.onAccountChanged(account);
        renderFolderList();
    }

    @Override
    public void onFolderChanged(Folder folder) {
        super.onFolderChanged(folder);
        exitCabMode();
        final FolderListFragment folderList = getFolderListFragment();
        if (folderList != null) {
            folderList.selectInitialFolder(folder);
        }
    }

    @Override
    public void onFolderSelected(Folder folder) {
        if (folder.hasChildren && !folder.equals(getHierarchyFolder())) {
            // Replace this fragment with a new FolderListFragment
            // showing this folder's children if we are not already looking
            // at the child view for this folder.
            createFolderListFragment(folder, folder.childFoldersListUri);
            // Show the up affordance when digging into child folders.
            mActionBarView.setBackButton();
            super.onFolderSelected(folder);
        } else {
            setHierarchyFolder(folder);
            super.onFolderSelected(folder);
        }
    }

    private void goUpFolderHierarchy(Folder current) {
        Folder parent = current.parent;
        if (parent.parent != null) {
            createFolderListFragment(parent.parent, parent.parent.childFoldersListUri);
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
        // Otherwise, the conversation list is guaranteed to be visible. Try to enable the CAB
        // mode if any conversations are selected.
        if (newMode == ViewMode.CONVERSATION){
            enableOrDisableCab();
        }
        resetActionBarIcon();
    }

    @Override
    public void onConversationVisibilityChanged(boolean visible) {
        super.onConversationVisibilityChanged(visible);
        if (!visible) {
            mPagerController.hide();
        }
    }

    @Override
    public void onConversationListVisibilityChanged(boolean visible) {
        super.onConversationListVisibilityChanged(visible);
    }

    @Override
    public void resetActionBarIcon() {
        if (mViewMode.isListMode()) {
            mActionBarView.removeBackButton();
        } else {
            mActionBarView.setBackButton();
        }
    }

    /**
     * Enable or disable the CAB mode based on the visibility of the conversation list fragment.
     */
    private final void enableOrDisableCab() {
        if (mLayout.isConversationListCollapsed()) {
            disableCabMode();
        } else {
            enableCabMode();
        }
    }

    @Override
    public void showConversation(Conversation conversation) {
        super.showConversation(conversation);
        if (mActivity == null) {
            return;
        }
        if (conversation == null) {
            // This is a request to remove the conversation view and show the conversation list
            // fragment instead.
            mPagerController.stopListening();
            onBackPressed();
            return;
        }
        // If conversation list is not visible, then the user cannot see the CAB mode, so exit it.
        // This is needed here (in addition to during viewmode changes) because orientation changes
        // while viewing a conversation don't change the viewmode: the mode stays
        // ViewMode.CONVERSATION and yet the conversation list goes in and out of visibility.
        enableOrDisableCab();

        final int mode = mViewMode.getMode();
        if (mode == ViewMode.SEARCH_RESULTS_LIST || mode == ViewMode.SEARCH_RESULTS_CONVERSATION) {
            mViewMode.enterSearchResultsConversationMode();
        } else {
            mViewMode.enterConversationMode();
        }
        mPagerController.show(mAccount, mFolder, conversation);
    }

    @Override
    public void setCurrentConversation(Conversation conversation) {
        super.setCurrentConversation(conversation);

        final ConversationListFragment convList = getConversationListFragment();
        if (convList != null && conversation != null) {
            LogUtils.d(LOG_TAG, "showConversation: Selecting position %d.", conversation.position);
            convList.setSelected(conversation.position);
        }
    }

    @Override
    public void showWaitForInitialization() {
        super.showWaitForInitialization();

        Fragment waitFragment = WaitFragment.newInstance(mAccount);
        FragmentTransaction fragmentTransaction = mActivity.getFragmentManager().beginTransaction();
        fragmentTransaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
        fragmentTransaction.replace(R.id.two_pane_activity, waitFragment, TAG_WAIT);
        fragmentTransaction.commitAllowingStateLoss();
    }

    @Override
    public void hideWaitForInitialization() {
        final FragmentManager manager = mActivity.getFragmentManager();
        final WaitFragment waitFragment = (WaitFragment)manager.findFragmentByTag(TAG_WAIT);
        if (waitFragment != null) {
            FragmentTransaction fragmentTransaction =
                    mActivity.getFragmentManager().beginTransaction();
            fragmentTransaction.remove(waitFragment);
            fragmentTransaction.commitAllowingStateLoss();
        }
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
            mActivity.onBackPressed();
        } else if (mode == ViewMode.SEARCH_RESULTS_CONVERSATION) {
            if (mLayout.isConversationListCollapsed()
                    || (mConvListContext.isSearchResult() && !Utils.
                            showTwoPaneSearchResults(mActivity.getApplicationContext()))) {
                onBackPressed();
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
    public boolean onBackPressed() {
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
            if (mode == ViewMode.CONVERSATION_LIST && getFolderListFragment().showingHierarchy()) {
                // If the user navigated via the left folders list into a child folder,
                // back should take the user up to the parent folder's conversation list.
                Folder hierarchyFolder = getHierarchyFolder();
                if (hierarchyFolder.parent != null) {
                    goUpFolderHierarchy(hierarchyFolder);
                } else  {
                    // Show inbox; we are at the top of the hierarchy we were
                    // showing, and it doesn't have a parent, so we must want to
                    // the basic account folder list.
                    createFolderListFragment(null, mAccount.folderListUri);
                    loadAccountInbox();
                }
            } else if (!preventClose) {
                // There is nothing else to pop off the stack.
                mActivity.finish();
            }
        }
    }

    @Override
    public void onRestoreInstanceState(Bundle inState) {
        super.onRestoreInstanceState(inState);
        if (inState.containsKey(SAVED_HIERARCHICAL_FOLDER)) {
            String folderString = inState.getString(SAVED_HIERARCHICAL_FOLDER);
            if (!TextUtils.isEmpty(folderString)) {
                Folder folder = Folder.fromString(inState.getString(SAVED_HIERARCHICAL_FOLDER));
                mViewMode.enterConversationListMode();
                if (folder.hasChildren) {
                    onFolderSelected(folder);
                } else if (folder.parent != null) {
                    onFolderSelected(folder.parent);
                    setHierarchyFolder(folder);
                }
            }
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        Folder hierarchyFolder = getHierarchyFolder();
        outState.putString(SAVED_HIERARCHICAL_FOLDER,
                hierarchyFolder != null ? Folder.toString(hierarchyFolder) : null);
    }

    @Override
    public void exitSearchMode() {
        int mode = mViewMode.getMode();
        if (mode == ViewMode.SEARCH_RESULTS_LIST
                || (mode == ViewMode.SEARCH_RESULTS_CONVERSATION
                        && Utils.showTwoPaneSearchResults(mActivity.getApplicationContext()))) {
            mActivity.finish();
        }
    }

    @Override
    public boolean shouldShowFirstConversation() {
        return Intent.ACTION_SEARCH.equals(mActivity.getIntent().getAction())
                && Utils.showTwoPaneSearchResults(mActivity.getApplicationContext());
    }

    @Override
    public void onUndoAvailable(ToastBarOperation op) {
        final int mode = mViewMode.getMode();
        final FrameLayout.LayoutParams params =
                (FrameLayout.LayoutParams) mToastBar.getLayoutParams();
        final ConversationListFragment convList = getConversationListFragment();
        switch (mode) {
            case ViewMode.SEARCH_RESULTS_LIST:
            case ViewMode.CONVERSATION_LIST:
                params.width = mLayout.computeConversationListWidth()
                        - params.leftMargin - params.rightMargin;
                params.gravity = Gravity.BOTTOM | Gravity.RIGHT;
                mToastBar.setLayoutParams(params);
                mToastBar.setConversationMode(false);
                if (convList != null) {
                    mToastBar.show(
                            getUndoClickedListener(convList.getAnimatedAdapter()),
                            0,
                            Html.fromHtml(op.getDescription(mActivity.getActivityContext())),
                            true, /* showActionIcon */
                            R.string.undo,
                            true,  /* replaceVisibleToast */
                            op);
                }
                break;
            case ViewMode.SEARCH_RESULTS_CONVERSATION:
            case ViewMode.CONVERSATION:
                if (op.isBatchUndo()) {
                    // Show undo bar in the conversation list.
                    params.gravity = Gravity.BOTTOM | Gravity.LEFT;
                    params.width = mLayout.computeConversationListWidth()
                            - params.leftMargin - params.rightMargin;
                    mToastBar.setLayoutParams(params);
                    mToastBar.setConversationMode(false);
                } else {
                    // Show undo bar in the conversation.
                    params.gravity = Gravity.BOTTOM | Gravity.RIGHT;
                    params.width = mLayout.getConversationView().getWidth()
                            - params.leftMargin - params.rightMargin;
                    mToastBar.setLayoutParams(params);
                    mToastBar.setConversationMode(true);
                }
                mToastBar.show(
                        getUndoClickedListener(convList.getAnimatedAdapter()),
                        0,
                        Html.fromHtml(op.getDescription(mActivity.getActivityContext())),
                        true, /* showActionIcon */
                        R.string.undo,
                        true,  /* replaceVisibleToast */
                        op);
                break;
        }
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
