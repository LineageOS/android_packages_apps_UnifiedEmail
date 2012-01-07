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

import com.android.mail.utils.Utils;
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
    // Key used to save this {@link ViewMode}.
    private static final String VIEW_MODE_KEY = "view-mode";

    // Do not change the order of the values in the enum. The ordinal position is used in a
    // saved instance bundle. When adding values, add them to the end of the enum.
    enum Mode {
        MODE_UNKNOWN,
        MODE_LABEL_LIST,
        MODE_CONVERSATION_LIST,
        MODE_CONVERSATION,
        MODE_SEARCH_RESULTS,
    }

    // Handy names for external users of this class.
    public static Mode UNKNOWN = Mode.MODE_UNKNOWN;
    public static Mode LABEL_LIST = Mode.MODE_LABEL_LIST;
    public static Mode CONVERSATION_LIST = Mode.MODE_CONVERSATION_LIST;
    public static Mode CONVERSATION = Mode.MODE_CONVERSATION;
    public static Mode SEARCH_RESULTS = Mode.MODE_SEARCH_RESULTS;

    private Mode mMode = UNKNOWN;
    private final boolean mTwoPane;
    private final ArrayList<ModeChangeListener> mListeners = Lists.newArrayList();

    public ViewMode(Context context) {
        mTwoPane = Utils.useTabletUI(context);
    }

    /**
     * Requests a transition of the mode to show a conversation as the prominent view.
     * @return Whether or not a change occured.
     */
    public boolean transitionToConversationMode() {
        return setModeInternal(CONVERSATION);
    }

    /**
     * Requests a transition of the mode to show the conversation list as the prominent view.
     * @return Whether or not a change occured.
     */
    public boolean transitionToConversationListMode() {
        return setModeInternal(CONVERSATION_LIST);
    }

    /**
     * Requests a transition of the mode to show the label list as the prominent view.
     * @return Whether or not a change occured.
     */
    public boolean transitionToLabelListMode() {
        return setModeInternal(LABEL_LIST);
    }

    /**
     * Sets the internal mode.
     * @return Whether or not a change occured.
     */
    private boolean setModeInternal(Mode mode) {
        if (mMode == mode) {
            return false;
        }

        mMode = mode;
        dispatchModeChange();
        return true;
    }

    /**
     * @return The current mode.
     */
    public Mode getMode() {
        return mMode;
    }

    /**
     * @return Whether or not to display 2 pane.
     */
    public boolean isTwoPane() {
        return mTwoPane;
    }

    public boolean isConversationMode() {
        return mMode == CONVERSATION;
    }

    public boolean isConversationListMode() {
        return mMode == CONVERSATION_LIST;
    }

    public boolean isLabelListMode() {
        return mMode == LABEL_LIST;
    }

    public void handleSaveInstanceState(Bundle outState) {
        outState.putInt(VIEW_MODE_KEY, mMode.ordinal());
    }

    public void handleRestore(Bundle inState) {
        Mode mode = Mode.values()[inState.getInt(VIEW_MODE_KEY, UNKNOWN.ordinal())];
        setModeInternal(mode);
    }

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
     * Adds a listener from this view mode.
     * Must happen in the UI thread.
     */
    public void addListener(ModeChangeListener listener) {
        mListeners.add(listener);
    }

    /**
     * Removes a listener from this view mode.
     * Must happen in the UI thread.
     */
    public void removeListener(ModeChangeListener listener) {
        mListeners.remove(listener);
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
}
