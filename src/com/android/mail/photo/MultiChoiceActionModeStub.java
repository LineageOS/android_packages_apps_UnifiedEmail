/*
 * Copyright (C) 2011 Google Inc.
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

package com.android.mail.photo;

import android.graphics.Color;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ListView;

/**
 * A wrapper for the Honeycomb/ICS ActionMode class
 */
public class MultiChoiceActionModeStub {
    // Instance variables
    private final MultiChoiceCallbackStub mCallbackStub;
    private final ListView.MultiChoiceModeListener mActionCallback;
    private ActionMode mActionMode;

    /**
     * The multi choice callback
     */
    private class MultiChoiceCallback implements ListView.MultiChoiceModeListener {
        /**
         * {@inheritDoc}
         */
        @Override
        public boolean onCreateActionMode(ActionMode actionMode, Menu menu) {
            mActionMode = actionMode;
            return mCallbackStub.onCreateActionMode(MultiChoiceActionModeStub.this, menu);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean onPrepareActionMode(ActionMode actionMode, Menu menu) {
            return mCallbackStub.onPrepareActionMode(MultiChoiceActionModeStub.this, menu);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean onActionItemClicked(ActionMode actionMode, MenuItem menuItem) {
            return mCallbackStub.onActionItemClicked(MultiChoiceActionModeStub.this, menuItem);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void onDestroyActionMode(ActionMode actionMode) {
            mCallbackStub.onDestroyActionMode(MultiChoiceActionModeStub.this);
            mActionMode = null;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void onItemCheckedStateChanged(ActionMode mode, int position, long id,
                boolean checked) {
            mCallbackStub.onItemCheckedStateChanged(MultiChoiceActionModeStub.this, position, id,
                    checked);
        }
    }

    /**
     * The action mode callback
     */
    public interface MultiChoiceCallbackStub {
        /**
         * The action mode is created
         *
         * @param actionModeStub The actionMode stub
         * @param menu The menu
         *
         * @return true if the action mode should be created
         */
        public boolean onCreateActionMode(MultiChoiceActionModeStub actionModeStub, Menu menu);

        /**
         * The action mode is prepared
         *
         * @param actionModeStub The actionMode stub
         * @param menu The menu
         *
         * @return true if the action mode has changed
         */
        public boolean onPrepareActionMode(MultiChoiceActionModeStub actionModeStub, Menu menu);

        /**
         * An action button is clicked
         *
         * @param actionModeStub The actionMode stub
         * @param menuItem The menu item
         *
         * @return true if the action was handled
         */
        public boolean onActionItemClicked(MultiChoiceActionModeStub actionModeStub,
                MenuItem menuItem);

        /**
         * The action mode is destroyed
         *
         * @param actionModeStub The actionMode stub
         */
        public void onDestroyActionMode(MultiChoiceActionModeStub actionModeStub);

        /**
         * Check item state
         *
         * @param mode The action mode stub
         * @param position The item position
         * @param id The item id
         * @param checked The checked state
         */
        public void onItemCheckedStateChanged(MultiChoiceActionModeStub mode, int position,
                long id, boolean checked);
    }

    /**
     * Constructor
     *
     * @param callbackStub The callback stub
     */
    public MultiChoiceActionModeStub(MultiChoiceCallbackStub callbackStub) {
        mCallbackStub = callbackStub;
        mActionCallback = new MultiChoiceCallback();
    }

    /**
     * @return The action mode callback
     */
    public ListView.MultiChoiceModeListener getCallback() {
        return mActionCallback;
    }

    /**
     * Set the title of the action bar
     *
     * @param title The title
     */
    public void setTitle(CharSequence title) {
        if (mActionMode != null) {
            if (title != null) {
                // Forcing the title to be white in a spannable because there is a
                // bug in Honeycomb code that doesn't expose actionModeStyle, so
                // we can't style this text correctly for action modes with our
                // overall light theme and inverted action bar.
                SpannableString s = new SpannableString(title);
                s.setSpan(new ForegroundColorSpan(Color.WHITE), 0, s.length(),
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                mActionMode.setTitle(s);
            } else {
                mActionMode.setTitle(null);
            }
        }
    }

    /**
     * Invalidate the action mode
     */
    public void invalidate() {
        if (mActionMode != null) {
            mActionMode.invalidate();
        }
    }

    /**
     * Finish the action mode
     */
    public void finish() {
        if (mActionMode != null) {
            mActionMode.finish();
        }
    }
}
