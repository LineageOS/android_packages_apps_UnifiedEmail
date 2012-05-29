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
import android.app.LoaderManager;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.view.DragEvent;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;

import com.android.mail.ConversationListContext;
import com.android.mail.providers.Account;
import com.android.mail.providers.Conversation;
import com.android.mail.providers.Folder;
import com.android.mail.providers.Settings;
import com.android.mail.ui.FoldersSelectionDialog.FolderChangeCommitListener;
import com.android.mail.ui.ViewMode.ModeChangeListener;

import java.util.Collection;

/**
 * An Activity controller knows how to combine views and listeners into a functioning activity.
 * ActivityControllers are delegates that implement methods by calling underlying views to modify,
 * or respond to user action.
 */
public interface ActivityController extends DragListener, LayoutListener, SubjectDisplayChanger,
        ModeChangeListener, ConversationListCallbacks, FolderChangeCommitListener,
        FolderChangeListener, AccountChangeListener, LoaderManager.LoaderCallbacks<Cursor>,
        ConversationSetObserver,
        FolderListFragment.FolderListSelectionListener, HelpCallback, UndoBarView.UndoListener {

    // As far as possible, the methods here that correspond to Activity lifecycle have the same name
    // as their counterpart in the Activity lifecycle.

    /**
     * Returns the current account.
     */
    Account getCurrentAccount();

    /**
     * Returns the current conversation list context.
     */
    ConversationListContext getCurrentListContext();

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
     * Called by the Mail activity when the up button is pressed.
     * @return
     */
    boolean onUpPressed();

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
     * @see android.app.Activity#onDestroy
     */
    void onDestroy();

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
     * @see android.app.Activity#onRestoreInstanceState
     */
    void onRestoreInstanceState(Bundle savedInstanceState);

    /**
     * @see android.app.Activity#onSaveInstanceState
     * @param outState
     */
    void onSaveInstanceState(Bundle outState);

    /**
     * @see android.app.Activity#onSearchRequested()
     */
    void onSearchRequested(String query);

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
     * Show the conversation provided here. If the conversation is null, this is a request to pop
     * <em>out</em> of conversation view mode and head back to conversation list mode, or whatever
     * should best show in its place.
     * @param conversation conversation to display, possibly null.
     */
    void showConversation(Conversation conversation);

    /**
     * Show the wait for account initialization mode.
     */
    public void showWaitForInitialization();

    /**
     * Dismiss the wait for account initialization mode.
     */
    public void hideWaitForInitialization();

    /**
     * Update the wait for account initialization mode.
     */
    public void updateWaitMode();

    public boolean inWaitMode();

    /**
     * Show the folder list associated with the currently selected account.
     */
    void showFolderList();

    /**
     * Handle a touch event.
     */
    void onTouchEvent(MotionEvent event);

    /**
     * Return the settings currently being used by this activity.
     * @return
     */
    Settings getSettings();

    /**
     * Returns whether the first conversation in the conversation list should be
     * automatically selected and shown.
     */
    boolean shouldShowFirstConversation();

    public ConversationSelectionSet getSelectedSet();

    /**
     * Start search mode if the account being view supports the search capability.
     */
    void startSearch();

    /**
     * Exit the search mode, popping off one activity so that the back stack is fine.
     */
    void exitSearchMode();

    /**
     * Supports dragging conversations to a folder.
     */
    boolean supportsDrag(DragEvent event, Folder folder);

    /**
     * Handles dropping conversations to a folder.
     */
    void handleDrop(DragEvent event, Folder folder);

    void onUndoCancel();

    /**
     * Coordinates actions that might occur in response to a conversation that has finished loading
     * and is now user-visible.
     */
    void onConversationSeen(Conversation conv);

    /**
     * Load the default inbox associated with the current account.
     */
    public abstract void loadAccountInbox();

    /**
     * Return the folder currently being viewed by the activity.
     */
    public abstract Folder getFolder();
}
