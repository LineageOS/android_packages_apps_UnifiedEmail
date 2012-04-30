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
import android.view.Gravity;
import android.view.MenuItem;
import android.widget.FrameLayout;

import com.android.mail.ConversationListContext;
import com.android.mail.R;
import com.android.mail.providers.Account;
import com.android.mail.providers.Conversation;
import com.android.mail.providers.Folder;
import com.android.mail.providers.UIProvider;
import com.android.mail.providers.UIProvider.ConversationColumns;
import com.android.mail.utils.LogUtils;
import com.android.mail.utils.Utils;

import java.util.ArrayList;
import java.util.Collections;

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
        FolderListFragment folderListFragment = FolderListFragment.newInstance(parent, uri);
        FragmentTransaction fragmentTransaction = mActivity.getFragmentManager().beginTransaction();
        fragmentTransaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
        fragmentTransaction.replace(R.id.content_pane, folderListFragment, TAG_FOLDER_LIST);
        fragmentTransaction.commitAllowingStateLoss();
        // Since we are showing the folder list, we are at the start of the view
        // stack.
        resetActionBarIcon();
        if (getCurrentListContext() != null) {
            folderListFragment.selectFolder(getCurrentListContext().folder);
        }
    }

    @Override
    protected boolean isConversationListVisible() {
        return mLayout.isConversationListVisible();
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
    public void onWindowFocusChanged(boolean hasFocus) {
        if (hasFocus && isConversationListVisible()) {
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
        final FolderListFragment folderList = getFolderListFragment();
        if (folderList != null) {
            folderList.selectFolder(folder);
        }
        super.onFolderChanged(folder);
    }

    @Override
    public void onViewModeChanged(int newMode) {
        super.onViewModeChanged(newMode);
        if (newMode != ViewMode.WAITING_FOR_ACCOUNT_INITIALIZATION) {
            // Clear the wait fragment
            hideWaitForInitialization();
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

        mPagerController.show(mAccount, mFolder, conversation);
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
                commitLeaveBehindItems();
                unhideConversationList();
            } else {
                mActivity.onBackPressed();
            }
        } else if (mode == ViewMode.SEARCH_RESULTS_CONVERSATION) {
            if (!mLayout.isConversationListVisible()) {
                commitLeaveBehindItems();
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
        // Clear any visible undo bars.
        mUndoBarView.hide(false);
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
                final boolean showDialog =
                        (mCachedSettings != null && mCachedSettings.confirmArchive);
                confirmAndDelete(showDialog, R.plurals.confirm_archive_conversation,
                        getAction(R.id.archive));
                break;
            }
            case R.id.delete: {
                final boolean showDialog =
                        (mCachedSettings != null && mCachedSettings.confirmDelete);
                confirmAndDelete(showDialog, R.plurals.confirm_delete_conversation,
                        getAction(R.id.delete));
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
                ConversationListFragment convList = getConversationListFragment();
                if (convList != null) {
                    convList.requestDelete(getAction(R.id.mute));
                }
                break;
            case R.id.report_spam:
                convList = getConversationListFragment();
                if (convList != null) {
                    convList.requestDelete(getAction(R.id.report_spam));
                }
                break;
            default:
                handled = false;
                break;
        }
        return handled || super.onOptionsItemSelected(item);
    }

    /**
     * An object that performs an action on the conversation database. This is a
     * {@link DestructiveAction}: this is called <b>after</a> the conversation list has animated
     * the conversation away. Once the animation is completed, the {@link #performAction()}
     * method is called which performs the correct data operation.
     */
    private class TwoPaneDestructiveAction extends AbstractDestructiveAction {
        /** Whether this destructive action has already been performed */
        public boolean mCompleted;

        public TwoPaneDestructiveAction(int action) {
            super(action);
        }

        @Override
        public void performAction() {
            if (mCompleted) {
                return;
            }
            mCompleted = true;
            final ArrayList<Conversation> single = new ArrayList<Conversation>();
            single.add(mCurrentConversation);
            final Conversation nextConversation = mTracker.getNextConversation(mCachedSettings);
            TwoPaneController.this.performAction();
            final ConversationListFragment convList = getConversationListFragment();
            if (nextConversation != null) {
                // We have a conversation to auto advance to.
                if (convList != null) {
                    convList.viewConversation(nextConversation.position);
                }
                onUndoAvailable(new UndoOperation(1, mAction));
            } else {
                // We don't have a conversation to show: show conversation list instead.
                onBackPressed();
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        onUndoAvailable(new UndoOperation(1, mAction));
                    }
                });
            }
            baseAction(single);
            refreshConversationList();
        }
    }

    /**
     * Get a destructive action specific to the {@link TwoPaneController}.
     * This is a temporary method, to control the profusion of {@link DestructiveAction} classes
     * that are created. Please do not copy this paradigm.
     * TODO(viki): Resolve the various actions and clean up their calling sequence.
     * @param action
     * @return
     */
    private final DestructiveAction getAction(int action) {
        DestructiveAction da = new TwoPaneDestructiveAction(action);
        registerDestructiveAction(da);
        return da;
    }

    @Override
    public DestructiveAction getFolderDestructiveAction() {
        return getAction(R.id.change_folder);
    }

    @Override
    public void onUndoAvailable(UndoOperation op) {
        int mode = mViewMode.getMode();
        FrameLayout.LayoutParams params;
        final ConversationListFragment convList = getConversationListFragment();
        switch (mode) {
            case ViewMode.CONVERSATION_LIST:
                params = (FrameLayout.LayoutParams) mUndoBarView.getLayoutParams();
                params.width = mLayout.computeConversationListWidth();
                params.gravity = Gravity.BOTTOM | Gravity.RIGHT;
                mUndoBarView.setLayoutParams(params);
                if (convList != null) {
                    mUndoBarView.show(true, mActivity.getActivityContext(), op, mAccount,
                        convList.getAnimatedAdapter(), mConversationListCursor);
                }
                break;
            case ViewMode.CONVERSATION:
                if (op.mBatch) {
                    // Show undo bar in the conversation list.
                    params = (FrameLayout.LayoutParams) mUndoBarView.getLayoutParams();
                    params.gravity = Gravity.BOTTOM | Gravity.LEFT;
                    params.width = mLayout.computeConversationListWidth();
                } else {
                    // Show undo bar in the conversation.
                    params = (FrameLayout.LayoutParams) mUndoBarView.getLayoutParams();
                    params.gravity = Gravity.BOTTOM | Gravity.RIGHT;
                    params.width = mLayout.getConversationView().getWidth();
                }
                mUndoBarView.setLayoutParams(params);
                mUndoBarView.show(true, mActivity.getActivityContext(), op, mAccount,
                        convList.getAnimatedAdapter(), null);
                break;
        }
    }
}
