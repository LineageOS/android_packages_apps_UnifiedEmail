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

import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.ActionMode;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;

import com.android.mail.browse.ConversationItemView.StarHandler;
import com.android.mail.ui.ViewMode.ModeChangeListener;
import com.android.mail.utils.Utils;


/**
 * This is the root activity container that holds the left navigation fragment
 * (usually a list of labels), and the main content fragment (either a
 * conversation list or a conversation view).
 */
public class MailActivity extends AbstractMailActivity implements ControllableActivity {
    // TODO(viki) This class lacks: Conversation Position tracking
    // TODO(viki) This class lacks: What's New dialog
    // TODO(viki) This class lacks: Sync Window Upgrade dialog

    /**
     * The activity controller to which we delegate most Activity lifecycle events.
     */
    private ActivityController mController;
    /**
     * A clean launch is when the activity is not resumed. We want to show a "What's New" dialog
     * on a clean launch: when the user started the Activity by tapping on the icon: not when he
     * selected "Up" from compose, not when he resumed the activity, etc.
     */
    private boolean mLaunchedCleanly = false;

    private ViewMode mViewMode;

    @Override
    public void attachConversationList(ConversationListFragment fragment) {
        mController.attachConversationList(fragment);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        // TODO(viki): Why is there a check for null only in in this method?
        // onTouchEvent should only be sent when the activity is in focus...
        if (mController != null) {
            mController.dispatchTouchEvent(ev);
        }
        return super.dispatchTouchEvent(ev);
    }

    /**
     * Default implementation returns a null view mode.
     */
    @Override
    public int getViewMode() {
        return mViewMode.getMode();
    }

    @Override
    public void onActionModeFinished(ActionMode mode) {
        super.onActionModeFinished(mode);
        mController.onActionModeFinished(mode);
    }

    @Override
    public void onActionModeStarted(ActionMode mode) {
        super.onActionModeStarted(mode);
        mController.onActionModeStarted(mode);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        mController.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onBackPressed() {
        if (!mController.onBackPressed()) {
            super.onBackPressed();
        }
    }

    @Override
    public void onCreate(Bundle savedState) {
        super.onCreate(savedState);

        mViewMode = new ViewMode(this);
        final boolean tabletUi = Utils.useTabletUI(this);
        mController = ControllerFactory.forActivity(this, mViewMode, tabletUi);
        mController.onCreate(savedState);

        Intent intent = getIntent();
        // Only display "What's New" and similar dialogs on a clean launch.
        // A clean launch is one where the activity is not resumed.
        // We also want to avoid showing any dialogs when the user goes "up"
        // from a compose
        // activity launched directly from a send-to intent. (in that case the
        // action is null.)
        if (savedState == null && intent.getAction() != null) {
            mLaunchedCleanly = true;
        }
    }

    @Override
    public Dialog onCreateDialog(int id, Bundle bundle) {
        Dialog dialog = mController.onCreateDialog(id, bundle);
        // TODO(viki): Handle what's new and the sync window upgrade dialog here.
        return dialog == null ? super.onCreateDialog(id, bundle) : dialog;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        return mController.onCreateOptionsMenu(menu) || super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        return mController.onKeyDown(keyCode, event) || super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return mController.onOptionsItemSelected(item) || super.onOptionsItemSelected(item);
    }

    @Override
    public void onPause() {
        super.onPause();
        mController.onPause();
        //        mSyncWindowUpgradeReceiver.disable();
    }

    @Override
    public void onPrepareDialog(int id, Dialog dialog, Bundle bundle) {
        super.onPrepareDialog(id, dialog, bundle);
        mController.onPrepareDialog(id, dialog, bundle);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        return mController.onPrepareOptionsMenu(menu) || super.onPrepareOptionsMenu(menu);
    }

    @Override
    public void onResume() {
        super.onResume();
        mController.onResume();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mController.onSaveInstanceState(outState);
    }

    @Override
    public boolean onSearchRequested() {
        mController.onSearchRequested();
        return true;
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (mLaunchedCleanly) {
            // TODO(viki): Show a "what's new screen"
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        mController.onStop();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        mController.onWindowFocusChanged(hasFocus);
    }

    @Override
    public void setViewModeListener(ModeChangeListener listener) {
        mViewMode.addListener(listener);
    }

    @Override
    public void unsetViewModeListener(ModeChangeListener listener) {
        mViewMode.removeListener(listener);
    }

    @Override
    public ConversationListCallbacks getListHandler() {
        return mController;
    }

    @Override
    public StarHandler getStarHandler() {
        return mController;
    }

    @Override
    public void attachFolderList(FolderListFragment folderListFragment) {
        // TODO Auto-generated method stub
    }
}
