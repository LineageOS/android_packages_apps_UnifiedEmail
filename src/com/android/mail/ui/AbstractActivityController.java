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


import android.app.ActionBar;
import android.app.ActionBar.LayoutParams;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.ActionMode;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.widget.LinearLayout;

import com.android.mail.R;
import com.android.mail.ConversationListContext;
import com.android.mail.providers.Account;
import com.android.mail.providers.Folder;

/**
 * This is an abstract implementation of the Activity Controller. This class knows how to
 * respond to menu items, state changes, layout changes, etc.  It weaves together the views and
 * listeners, dispatching actions to the respective underlying classes.
 *
 * <p>Even though this class is abstract, it should provide default implementations for most, if
 * not all the methods in the ActivityController interface. This makes the task of the subclasses
 * easier: OnePaneActivityController and TwoPaneActivityController can be concise when the common
 * functionality is in AbstractActivityController.
 *</p>
 * <p>
 * In the Gmail codebase, this was called BaseActivityController</p>
 */
public abstract class AbstractActivityController implements ActivityController {
    private static final String SAVED_CONVERSATION = "saved-conversation";
    private static final String SAVED_CONVERSATION_POSITION = "saved-conv-pos";
    private static final String SAVED_CONVERSATIONS = "saved-conversations";
    // Keys for serialization of various information in Bundles.
    private static final String SAVED_LIST_CONTEXT = "saved-list-context";
    private Account mAccount;
    private ActionBarView mActionBarView;

    protected final RestrictedActivity mActivity;
    private ConversationSelectionSet mBatchConversations = new ConversationSelectionSet();
    protected final Context mContext;
    protected ConversationListFragment mConversationListFragment;
    protected final ViewMode mViewMode;

    public AbstractActivityController(MailActivity activity, ViewMode viewMode) {
        mActivity = activity;
        mViewMode = viewMode;
        mContext = activity.getApplicationContext();
    }

    @Override
    public void attachConversationList(ConversationListFragment conversationList) {
        mConversationListFragment = conversationList;
    }

    @Override
    public void clearSubject() {
        // TODO(viki): Auto-generated method stub

    }

    @Override
    public void dispatchTouchEvent(MotionEvent ev) {
        // TODO(viki): Auto-generated method stub

    }

    @Override
    public void doneChangingFolders(FolderOperations folderOperations) {
        // TODO(viki): Auto-generated method stub

    }

    @Override
    public void enterSearchMode() {
        // TODO(viki): Auto-generated method stub

    }

    @Override
    public void exitSearchMode() {
        // TODO(viki): Auto-generated method stub

    }

    @Override
    public ConversationSelectionSet getBatchConversations() {
        // TODO(viki): Auto-generated method stub
        return null;
    }

    @Override
    public String getCurrentAccount() {
        // TODO(viki): Auto-generated method stub
        return null;
    }

    @Override
    public ConversationListContext getCurrentListContext() {
        // TODO(viki): Auto-generated method stub
        return null;
    }

    @Override
    public String getHelpContext() {
        return "Mail";
    }

    @Override
    public String getUnshownSubject(String subject) {
        // Calculate how much of the subject is shown, and return the remaining.
        return null;
    }

    @Override
    public void handleConversationLoadError() {
        // TODO(viki): Auto-generated method stub

    }

    @Override
    public void handleSearchRequested() {
        // TODO(viki): Auto-generated method stub

    }

    /**
     * Initialize the action bar. This is not visible to OnePaneController and TwoPaneController so
     * they cannot override this behavior.
     */
    private void initCustomActionBarView() {
        ActionBar actionBar = mActivity.getActionBar();
        mActionBarView = (MailActionBar) LayoutInflater.from(mContext).inflate(
                R.layout.actionbar_view, null);

        if (actionBar != null && mActionBarView != null) {
            // Why have a different variable for the same thing? We should apply the same actions
            // on mActionBarView instead.
            // mSubjectDisplayer = (ConversationSubjectDisplayer) mActionBarView;
            mActionBarView.initialize(mActivity, this, mViewMode, actionBar);
            actionBar.setCustomView((LinearLayout) mActionBarView, new ActionBar.LayoutParams(
                    LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
            actionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM,
                    ActionBar.DISPLAY_SHOW_CUSTOM
                            | ActionBar.DISPLAY_SHOW_TITLE);
        }
    }

    /**
     * Returns whether the conversation list fragment is visible or not. Different layouts will have
     * their own notion on the visibility of fragments, so this method needs to be overriden.
     * @return
     */
    protected abstract boolean isConversationListVisible();

    @Override
    public boolean navigateToAccount(String account) {
        // TODO(viki): Auto-generated method stub
        return false;
    }

    @Override
    public void navigateToFolder(String folderCanonicalName) {
        // TODO(viki): Auto-generated method stub

    }

    @Override
    public void onActionModeFinished(ActionMode mode) {
        // TODO(viki): Auto-generated method stub

    }

    @Override
    public void onActionModeStarted(ActionMode mode) {
        // TODO(viki): Auto-generated method stub

    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        // TODO(viki): Auto-generated method stub

    }

    @Override
    public boolean onBackPressed() {
        // TODO(viki): Auto-generated method stub
        return false;
    }

    @Override
    public void onConversationListVisibilityChanged(boolean visible) {
        // TODO(viki): Auto-generated method stub

    }

    /**
     * By default, doing nothing is right. A two-pane controller will need to
     * override this.
     */
    @Override
    public void onConversationVisibilityChanged(boolean visible) {
        // Do nothing.
        return;
    }

    @Override
    public void onCreate(Bundle savedState) {
        // Initialize the action bar view.
        initCustomActionBarView();

        final Intent intent = mActivity.getIntent();
        // TODO(viki) Choose an account here.

        // Allow shortcut keys to function for the ActionBar and menus.
        mActivity.setDefaultKeyMode(Activity.DEFAULT_KEYS_SHORTCUT);
        final Context context = mActivity.getApplicationContext();

        mViewMode.addListener(this);
        restoreState(savedState);
    }

    @Override
    public Dialog onCreateDialog(int id, Bundle bundle) {
        // TODO(viki): Auto-generated method stub
        return null;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // TODO(viki): Auto-generated method stub
        return false;
    }

    @Override
    public void onEndBulkOperation() {
        // TODO(viki): Auto-generated method stub

    }

    @Override
    public void onFolderChanged(Folder folder, long conversationId, boolean added) {
        // TODO(viki): Auto-generated method stub

    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // TODO(viki): Auto-generated method stub
        return false;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // TODO(viki): Auto-generated method stub
        return false;
    }

    @Override
    public void onPause() {
        // TODO(viki): Auto-generated method stub

    }

    @Override
    public void onPrepareDialog(int id, Dialog dialog, Bundle bundle) {
        // TODO(viki): Auto-generated method stub

    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        // TODO(viki): Auto-generated method stub
        return false;
    }

    @Override
    public void onResume() {
        // TODO(viki): Auto-generated method stub
        mBatchConversations.addObserver(this);
        if (mActionBarView != null) {
            mActionBarView.onResume();
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        // TODO(viki): Auto-generated method stub

    }

    @Override
    public void onSearchRequested() {
        // TODO(viki): Auto-generated method stub

    }

    @Override
    public void onSetChanged(ConversationSelectionSet set) {
        // We don't care about changes to the set. Ignore.
    }

    @Override
    public void onSetEmpty() {
    }

    @Override
    public void onSetPopulated(ConversationSelectionSet set) {
        // TODO(viki): Auto-generated method stub

    }

    @Override
    public void onStartBulkOperation() {
        // TODO(viki): Auto-generated method stub

    }

    @Override
    public void onStartDragMode() {
        // TODO(viki): Auto-generated method stub

    }

    @Override
    public void onStop() {
        // TODO(viki): Auto-generated method stub

    }

    @Override
    public void onStopDragMode() {
        // TODO(viki): Auto-generated method stub

    }

    @Override
    public void onViewModeChanged(ViewMode mode) {
        // TODO(viki): Auto-generated method stub

    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        // TODO(viki): Auto-generated method stub

    }

    @Override
    public void reloadSearch(String string) {
        // TODO(viki): Auto-generated method stub

    }

    /**
     * @param savedState
     */
    protected void restoreListContext(Bundle savedState) {
        // TODO(viki): Auto-generated method stub

    }

    /**
     * Restore the state of selected conversations. This needs to be done after the correct mode
     * is set and the action bar is fully initialized. If not, several key pieces of state
     * information will be missing, and the split views may not be initialized correctly.
     * @param savedState
     */
    private void restoreSelectedConversations(Bundle savedState) {
        if (savedState != null){
            // Restore the view mode? This looks wrong.
            mBatchConversations = savedState.getParcelable(SAVED_CONVERSATIONS);
            if (mBatchConversations.isEmpty()) {
                onSetPopulated(mBatchConversations);
                onConversationVisibilityChanged(isConversationListVisible());
            } else {
                onSetEmpty();
            }
        } else {
            onSetEmpty();
        }
    }

    /**
     * Restore the state from the previous bundle. Subclasses should call this method from the
     * parent class, since it performs important UI initialization.
     * @param savedState
     */
    protected void restoreState(Bundle savedState) {
        if (savedState != null) {
            restoreListContext(savedState);
            // Attach the menu handler here.
        } else {
            final Intent intent = mActivity.getIntent();
            //  TODO(viki): Show the list context from Intent
            // showConversationList(ConversationListContext.forIntent(mContext, mAccount, intent));
        }

        // Set the correct mode based on the current context

        // And restore the state of selected conversations
        restoreSelectedConversations(savedState);
    }

    @Override
    public void setSubject(String subject) {
        // Do something useful with the subject. This requires changing the
        // conversation view's subject text.
    }

    @Override
    public void showFolderList() {
        // TODO(viki): Auto-generated method stub

    }

    @Override
    public void startActionBarStatusCursorLoader(String account) {
        // TODO(viki): Auto-generated method stub

    }

    @Override
    public void stopActionBarStatusCursorLoader(String account) {
        // TODO(viki): Auto-generated method stub

    }

}
