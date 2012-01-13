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

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.ActionMode;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;

import com.android.mail.ViewMode;
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
    protected final MailActivity mActivity;
    protected final ViewMode mViewMode;
    protected final Context mContext;

    public AbstractActivityController(MailActivity activity, ViewMode viewMode) {
        mActivity = activity;
        mViewMode = viewMode;
        mContext = activity.getApplicationContext();
    }

    @Override
    public String getHelpContext() {
        return "Mail";
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
    public void onLabelChanged(Folder label, long conversationId, boolean added) {
        // TODO(viki): Auto-generated method stub

    }

    @Override
    public void doneChangingLabels(FolderOperations labelOperations) {
        // TODO(viki): Auto-generated method stub

    }

    @Override
    public void handleSearchRequested() {
        // TODO(viki): Auto-generated method stub

    }

    @Override
    public void onStartBulkOperation() {
        // TODO(viki): Auto-generated method stub

    }

    @Override
    public void onEndBulkOperation() {
        // TODO(viki): Auto-generated method stub

    }

    @Override
    public void onStartDragMode() {
        // TODO(viki): Auto-generated method stub

    }

    @Override
    public void onStopDragMode() {
        // TODO(viki): Auto-generated method stub

    }

    @Override
    public void setSubject(String subject) {
        // Do something useful with the subject. This requires changing the
        // conversation view's
        // subject text.
    }

    @Override
    public String getUnshownSubject(String subject) {
        // Calculate how much of the subject is shown, and return the remaining.
        return null;
    }

    @Override
    public void onConversationListVisibilityChanged(boolean visible) {
        // TODO(viki): Auto-generated method stub

    }

    @Override
    public void onCreate(Bundle savedState) {
        // Initialize the action bar view.

        final Intent intent = mActivity.getIntent();
        // Allow shortcut keys to function for the ActionBar and menus.
        mActivity.setDefaultKeyMode(Activity.DEFAULT_KEYS_SHORTCUT);
        final Context context = mActivity.getApplicationContext();

        mViewMode.addListener(this);
        restoreState(savedState);
    }

    /**
     * Restore the state from the previous bundle.
     * @param savedState
     */
    protected void restoreState(Bundle savedState) {
        // Do nothing here.
    }

    @Override
    public void onSetEmpty(ConversationSelectionSet set) {
        // TODO(viki): Auto-generated method stub

    }

    @Override
    public void onSetPopulated(ConversationSelectionSet set) {
        // TODO(viki): Auto-generated method stub

    }

    @Override
    public void onSetChanged(ConversationSelectionSet set) {
        // TODO(viki): Auto-generated method stub

    }

    @Override
    public void onResume() {
        // TODO(viki): Auto-generated method stub

    }

    @Override
    public void onPause() {
        // TODO(viki): Auto-generated method stub

    }

    @Override
    public void onStop() {
        // TODO(viki): Auto-generated method stub

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // TODO(viki): Auto-generated method stub
        return false;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        // TODO(viki): Auto-generated method stub
        return false;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // TODO(viki): Auto-generated method stub
        return false;
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        // TODO(viki): Auto-generated method stub

    }

    @Override
    public boolean onBackPressed() {
        // TODO(viki): Auto-generated method stub
        return false;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        // TODO(viki): Auto-generated method stub

    }

    @Override
    public void onPrepareDialog(int id, Dialog dialog, Bundle bundle) {
        // TODO(viki): Auto-generated method stub

    }

    @Override
    public void onActionModeStarted(ActionMode mode) {
        // TODO(viki): Auto-generated method stub

    }

    @Override
    public void onActionModeFinished(ActionMode mode) {
        // TODO(viki): Auto-generated method stub

    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        // TODO(viki): Auto-generated method stub

    }

    @Override
    public ConversationSelectionSet getBatchConversations() {
        // TODO(viki): Auto-generated method stub
        return null;
    }

    @Override
    public void handleConversationLoadError() {
        // TODO(viki): Auto-generated method stub

    }

    @Override
    public void dispatchTouchEvent(MotionEvent ev) {
        // TODO(viki): Auto-generated method stub

    }

    @Override
    public Dialog onCreateDialog(int id, Bundle bundle) {
        // TODO(viki): Auto-generated method stub
        return null;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // TODO(viki): Auto-generated method stub
        return false;
    }

    @Override
    public void onSearchRequested() {
        // TODO(viki): Auto-generated method stub

    }

    @Override
    public void onViewModeChanged(ViewMode mode) {
        // TODO(viki): Auto-generated method stub

    }

    @Override
    public void clearSubject() {
        // TODO(viki): Auto-generated method stub

    }

}
