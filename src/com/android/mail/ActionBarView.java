/*
 * Copyright (C) 2012 Google Inc.
 * Licensed to The Android Open Source Project.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.mail;

import android.app.ActionBar;
import android.os.Bundle;
import android.view.Menu;

/**
 * ActionBarView simplifies supporting both MailActionBar and MailActionBarDeprecated (used for
 * pre-v14 devices).
 */

public interface ActionBarView {
    /**
     * The action bar can be in a variety of modes, which determine the actions available.
     * This is a list of the modes.
     *
     */
    enum Mode {
        /**
         * Default mode,
         */
        NORMAL,
        /**
         * Viewing a conversation
         */
        CONVERSATION,
        /**
         * Viewing a list of conversation
         */
        CONVERSATION_LIST,
        /**
         * Viewing a single conversation?
         */
        CONVERSATION_SUBJECT,
        /**
         * Viewing results from user search
         */
        SEARCH_RESULTS,
        /**
         * Viewing a list of labels
         */
        LABEL,
        /**
         * Viewing a conversation from search results
         */
        SEARCH_RESULTS_CONVERSATION,
        /**
         * Abnormal mode
         */
        INVALID
    }

    /**
     * Initialize the ActionBarView
     * @param activity
     * @param callback
     * @param viewMode
     * @param actionBar
     */
    void initialize(RestrictedActivity activity, MailActionBar.Callback callback,
            ViewMode viewMode, ActionBar actionBar);

    /**
     * Return the mode that the action bar is in.
     * @return The mode the action bar is in.
     */
    Mode getMode();

    /**
     * Handle handleRestore from the Android framework.
     * @param savedInstanceState
     */
    void handleRestore(Bundle savedInstanceState);

    /**
     * Change the mode of the actionbar.
     * @param mode
     * @return true if the change in mode was successful
     */
    boolean setMode(Mode mode);

    /**
     * Handle onResume from the Android framework.
     */
    void onResume();

    /**
     * Handle onPause from the Android framework.
     */
    void onPause();

    /**
     * @param outState
     */
    void handleSaveInstanceState(Bundle outState);

    /**
     * @param mAccountNames
     * @param currentAccount
     */
    void updateActionBar(String[] mAccountNames, String currentAccount);

    /**
     * Update the label that the user is currently viewing??
     * @param label
     */
    void setLabel(String label);

    /**
     * Returns the menu ID for the menu in this actionbar.
     * @return the Menu ID for the menu.
     */
    int getOptionsMenuId();

    /**
     * Shows a back button in the top left. We have progressed one level into the application.
     */
    void setBackButton();

    /**
     * Removes any back button from the top left. We have returned to the top of the application.
     */
    void removeBackButton();

    /**
     * Prepares all the icons that go inside the options menu. This depends on the context of
     * the action bar.
     * @param menu
     */
    boolean prepareOptionsMenu(Menu menu);

    /**
     * Creates the first time options menu.
     * @param menu
     */
    boolean createOptionsMenu(Menu menu);

    /**
     * Update sthe action bar based on a new status received from the server.
     * @param account
     * @param status
     */
    void onStatusResult(String account, int status);
}
