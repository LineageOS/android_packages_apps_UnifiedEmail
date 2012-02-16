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

import com.android.mail.ConversationListContext;
import com.android.mail.browse.ConversationItemView.StarHandler;
import com.android.mail.providers.Conversation;
import com.android.mail.ui.ViewMode.ModeChangeListener;

/**
 * An Activity controller knows how to combine views and listeners into a functioning activity.
 * ActivityControllers are delegates that implement methods by calling underlying views to modify,
 * or respond to user action.
 */
public interface ActivityController extends MenuCallback, LayoutListener, SubjectDisplayChanger,
        ModeChangeListener, MailActionBar.Callback, StarHandler, ConversationListCallbacks,
        FolderListCallback {

    // As far as possible, the methods here that correspond to Activity lifecycle have the same name
    // as their counterpart in the Activity lifecycle.

    /**
     * Attach the conversation list fragment to the appropriate view.
     * @param conversationListFragment
     */
    // TODO(viki): Why does the activity controller have such a deep knowledge of the conversation
    // list fragment? Calls to the fragment show up in handleLoadFinished, isConversationListMode,
    // onDestructiveCommand, restoreState, showConversationAtCursor, handleKeyDown, etc.
    // Instead, it might be beneficial to have a layout controller a la TriStateSplitLayout which
    // exists both for one pane and two pane modes. The layout controller should know about the
    // fragment, and send appropriate calls to it. Such a scheme will allow some separation of
    // control and view logic, which is spread between the activity controller and the fragment
    // currently.
    void attachConversationList(ConversationListFragment conversationListFragment);

    /**
     * Attach the folder list fragment to the appropriate view.
     * @param folderListFragment
     */
    void attachFolderList(FolderListFragment folderListFragment);

    /**
     * Attach the conversation view fragment to the appropriate view.
     * @param conversationViewFragment
     */
    void attachConversationView(ConversationViewFragment conversationViewFragment);

    /**
     * @see android.app.Activity#dispatchTouchEvent(MotionEvent)
     * @param event
     */
    void dispatchTouchEvent(MotionEvent event);

    /**
     * Return the current mode the activity is in. Values need to be matched against constants in
     * {@link ViewMode}.
     * @return
     */
    int getMode();

    /**
     *
     */
    void handleConversationLoadError();

    /**
     * @see android.app.Activity#onActionModeFinished(ActionMode)
     * @param mode
     */
    void onActionModeFinished(ActionMode mode);

    /**
     * @see android.app.Activity#onActionModeStarted(ActionMode)
     * @param mode
     */
    void onActionModeStarted(ActionMode mode);

    /**
     * @see android.app.Activity#onActivityResult
     * @param requestCode
     * @param resultCode
     * @param data
     */
    void onActivityResult(int requestCode, int resultCode, Intent data);

    /**
     * Called by the Mail activity when the back button is pressed. Returning true consumes the
     * event and disallows the calling method from trying to handle the back button any other way.
     *
     * @see android.app.Activity#onBackPressed()
     * @return true if the back press was handled and the event was consumed. Return false if the
     * event was not consumed.
     */
    boolean onBackPressed();

    /**
     * Called when the root activity calls onCreate. Any initialization needs to
     * be done here. Subclasses need to call their parents' onCreate method, since it performs
     * valuable initialization common to all subclasses.
     *
     * This was called initialize in Gmail.
     *
     * @see android.app.Activity#onCreate
     * @param savedState
     * @return true if the controller was able to initialize successfully, false otherwise.
     */
    boolean onCreate(Bundle savedState);

    /**
     * @see android.app.Activity#onCreateDialog(int, Bundle)
     * @param id
     * @param bundle
     * @return
     */
    Dialog onCreateDialog(int id, Bundle bundle);

    /**
     * @see android.app.Activity#onCreateOptionsMenu(Menu)
     * @param menu
     * @return
     */
    boolean onCreateOptionsMenu(Menu menu);

    /**
     * @see android.app.Activity#onKeyDown(int, KeyEvent)
     * @param keyCode
     * @param event
     * @return
     */
    boolean onKeyDown(int keyCode, KeyEvent event);

    /**
     * Called by Mail activity when menu items are selected
     * @see android.app.Activity#onOptionsItemSelected(MenuItem)
     * @param item
     * @return
     */
    boolean onOptionsItemSelected(MenuItem item);

    /**
     * Called by the Mail activity on Activity pause.
     * @see android.app.Activity#onPause
     */
    void onPause();

    /**
     * @see android.app.Activity#onPrepareDialog
     * @param id
     * @param dialog
     * @param bundle
     */
    void onPrepareDialog(int id, Dialog dialog, Bundle bundle);

    /**
     * Called by the Mail activity when menu items need to be prepared.
     * @see android.app.Activity#onPrepareOptionsMenu(Menu)
     * @param menu
     * @return
     */
    boolean onPrepareOptionsMenu(Menu menu);

    /**
     * Called by the Mail activity on Activity resume.
     * @see android.app.Activity#onResume
     */
    void onResume();

    /**
     * @see android.app.Activity#onSaveInstanceState
     * @param outState
     */
    void onSaveInstanceState(Bundle outState);

    /**
     * @see android.app.Activity#onSearchRequested()
     */
    void onSearchRequested();

    /**
     * Called by the Mail activity on Activity stop.
     * @see android.app.Activity#onStop
     */
    void onStop();

    /**
     * Called by the Mail activity when window focus changes.
     * @see android.app.Activity#onWindowFocusChanged(boolean)
     * @param hasFocus
     */
    void onWindowFocusChanged(boolean hasFocus);

    /**
     * Set the Action Bar icon according to the mode. The Action Bar icon can contain a back button
     * or not. The individual controller is responsible for changing the icon based on the mode.
     */
    void resetActionBarIcon();

    /**
     * Show the conversation List with the list context provided here. On certain layouts, this
     * might show more than just the conversation list. For instance, on tablets this might show
     * the conversations along with the conversation list.
     * @param listContext context providing information on what conversation list to display.
     */
    void showConversationList(ConversationListContext listContext);

    /**
     * Show the conversation provided here.
     * @param Converseation conversation to display.
     */
    void showConversation(Conversation conversation);

    /**
     * Show the folder list associated with the currently selected account.
     */
    void showFolderList();
}
