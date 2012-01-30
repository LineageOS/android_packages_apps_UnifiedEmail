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

package com.android.mail.ui;

import com.google.common.collect.Lists;

import android.content.Context;
import android.os.Bundle;

import com.android.mail.utils.Utils;

import java.util.ArrayList;

/**
 * Represents the view mode for the tablet Gmail activity.
 * Transitions between modes should be done through this central object, and UI components that are
 * dependent on the mode should listen to changes on this object.
 */
public class ViewMode {
    /**
     * A listener for changes on a ViewMode.
     */
    public interface ModeChangeListener {
        /**
         * Handles a mode change.
         */
        void onViewModeChanged(ViewMode mode);
    }

    /**
     * Mode when showing a single conversation.
     */
    public static int CONVERSATION = 1;
    /**
     * Mode when showing a list of conversations
     */
    public static int CONVERSATION_LIST = 2;
    /**
     * Mode when showing a list of folders.
     */
    public static int FOLDER_LIST = 3;
    /**
     * Mode when showing results from user search.
     */
    public static int SEARCH_RESULTS = 4;
    /**
     * Uncertain mode. The mode has not been initialized.
     */
    public static int UNKNOWN = 0;

    // Key used to save this {@link ViewMode}.
    private static final String VIEW_MODE_KEY = "view-mode";
    private final ArrayList<ModeChangeListener> mListeners = Lists.newArrayList();
    private int mMode = UNKNOWN;
    private boolean mTwoPane;

    public ViewMode(Context context) {
        mTwoPane = Utils.useTabletUI(context);
    }

    /**
     *  Adds a listener from this view mode.
     * Must happen in the UI thread.
     */
    public void addListener(ModeChangeListener listener) {
        mListeners.add(listener);
    }

    /**
     * Dispatches a change event for the mode.
     * Always happens in the UI thread.
     */
    private void dispatchModeChange() {
        ArrayList<ModeChangeListener> list = new ArrayList<ModeChangeListener>(mListeners);
        for (ModeChangeListener listener : list) {
            listener.onViewModeChanged(this);
        }
    }

    /**
     * @return The current mode.
     */
    public int getMode() {
        return mMode;
    }

    public void handleRestore(Bundle inState) {
        mMode = inState.getInt(VIEW_MODE_KEY);
    }

    public void handleSaveInstanceState(Bundle outState) {
        outState.putInt(VIEW_MODE_KEY, mMode);
    }

    public boolean isConversationListMode() {
        return mMode == CONVERSATION_LIST;
    }

    public boolean isConversationMode() {
        return mMode == CONVERSATION;
    }

    public boolean isFolderListMode() {
        return mMode == FOLDER_LIST;
    }

    /**
     * @return Whether or not to display 2 pane.
     */
    public boolean isTwoPane() {
        return mTwoPane;
    }

    /**
     * Removes a listener from this view mode.
     * Must happen in the UI thread.
     */
    public void removeListener(ModeChangeListener listener) {
        mListeners.remove(listener);
    }

    /**
     * Sets the internal mode.
     * @return Whether or not a change occured.
     */
    private boolean setModeInternal(int mode) {
        if (mMode == mode) {
            return false;
        }
        mMode = mode;
        dispatchModeChange();
        return true;
    }

    /**
     * Requests a transition of the mode to show the conversation list as the prominent view.
     * @return Whether or not a change occured.
     */
    public boolean transitionToConversationListMode() {
        return setModeInternal(CONVERSATION_LIST);
    }

    /**
     * Requests a transition of the mode to show a conversation as the prominent view.
     * @return Whether or not a change occured.
     */
    public boolean transitionToConversationMode() {
        return setModeInternal(CONVERSATION);
    }

    /**
     * Requests a transition of the mode to show the folder list as the prominent view.
     * @return Whether or not a change occured.
     */
    public boolean transitionToFolderListMode() {
        return setModeInternal(FOLDER_LIST);
    }
}
