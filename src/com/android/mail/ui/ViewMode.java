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
import java.util.ArrayList;

/**
 * Represents the view mode for the tablet Gmail activity.
 * Transitions between modes should be done through this central object, and UI components that are
 * dependent on the mode should listen to changes on this object.
 */
public class ViewMode {
    /**
     * A listener for changes on a ViewMode. To listen to mode changes, implement this
     * interface and register your object with the single ViewMode held by the ActivityController
     * instance. On mode changes, the onViewModeChanged method will be called with the new mode.
     */
    public interface ModeChangeListener {
        /**
         * Called when the mode has changed.
         */
        void onViewModeChanged(int newMode);
    }

    /**
     * Mode when showing a single conversation.
     */
    public static final int CONVERSATION = 1;
    /**
     * Mode when showing a list of conversations
     */
    public static final int CONVERSATION_LIST = 2;
    /**
     * Mode when showing a list of folders.
     */
    public static final int FOLDER_LIST = 3;
    /**
     * Mode when showing results from user search.
     */
    public static final int SEARCH_RESULTS_LIST = 4;
    /**
     * Mode when showing results from user search.
     */
    public static final int SEARCH_RESULTS_CONVERSATION = 5;
    /**
     * Mode when showing the "waiting for sync" message.
     */
    public static final int WAITING_FOR_ACCOUNT_INITIALIZATION = 6;
    /**
     * Uncertain mode. The mode has not been initialized.
     */
    public static final int UNKNOWN = 0;

    // Key used to save this {@link ViewMode}.
    private static final String VIEW_MODE_KEY = "view-mode";
    private final ArrayList<ModeChangeListener> mListeners = Lists.newArrayList();
    /**
     * The actual mode the activity is in. We start out with an UNKNOWN mode, and require entering
     * a valid mode after the object has been created.
     */
    private int mMode = UNKNOWN;

    public ViewMode(Context context) {
        // Do nothing
    }

    /**
     * Adds a listener from this view mode.
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
            assert (listener != null);
            listener.onViewModeChanged(mMode);
        }
    }

    /**
     * Requests a transition of the mode to show the conversation list as the prominent view.
     * @return Whether or not a change occurred.
     */
    public boolean enterConversationListMode() {
        return setModeInternal(CONVERSATION_LIST);
    }

    /**
     * Requests a transition of the mode to show a conversation as the prominent view.
     * @return Whether or not a change occurred.
     */
    public boolean enterConversationMode() {
        return setModeInternal(CONVERSATION);
    }

    /**
     * Requests a transition of the mode to show the folder list as the prominent view.
     * @return Whether or not a change occurred.
     */
    public boolean enterFolderListMode() {
        return setModeInternal(FOLDER_LIST);
    }

    /**
     * Requests a transition of the mode to show a list of search results as the
     * prominent view.
     *
     * @return Whether or not a change occurred.
     */
    public boolean enterSearchResultsListMode() {
        return setModeInternal(SEARCH_RESULTS_LIST);
    }

    /**
     * Requests a transition of the mode to show a conversation that was part of
     * search results.
     *
     * @return Whether or not a change occurred.
     */
    public boolean enterSearchResultsConversationMode() {
        return setModeInternal(SEARCH_RESULTS_CONVERSATION);
    }

    /**
     * Requests a transition of the mode to show the "waiting for sync" messages
     *
     * @return Whether or not a change occurred.
     */
    public boolean enterWaitingForInitializationMode() {
        return setModeInternal(WAITING_FOR_ACCOUNT_INITIALIZATION);
    }

    /**
     * @return The current mode.
     */
    public int getMode() {
        return mMode;
    }

    /**
     * Return whether the current mode is considered a list mode.
     */
    public boolean isListMode() {
        return mMode == ViewMode.CONVERSATION_LIST || mMode == ViewMode.SEARCH_RESULTS_LIST;
    }

    /**
     * Restoring from a saved state restores only the mode. It does not restore the listeners of
     * this object.
     * @param inState
     */
    public void handleRestore(Bundle inState) {
        if (inState == null) {
            return;
        }
        // Restore the previous mode, and UNKNOWN if nothing exists.
        final int newMode = inState.getInt(VIEW_MODE_KEY, UNKNOWN);
        if (newMode != UNKNOWN) {
            setModeInternal(newMode);
        }
    }

    /**
     * Save the existing mode only. Does not save the existing listeners.
     * @param outState
     */
    public void handleSaveInstanceState(Bundle outState) {
        if (outState == null) {
            return;
        }
        outState.putInt(VIEW_MODE_KEY, mMode);
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
     * @return Whether or not a change occurred.
     */
    private boolean setModeInternal(int mode) {
        if (mMode == mode) {
            return false;
        }
        mMode = mode;
        dispatchModeChange();
        return true;
    }
}
