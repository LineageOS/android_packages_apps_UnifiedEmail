/*
 * Copyright (C) 2013 Google Inc.
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

import com.android.mail.R;

import java.util.ArrayList;

public class PreferredActionItemDialogFragment extends LimitedMultiSelectDialogFragment {
    public static final String FRAGMENT_TAG = "PreferredActionItemDialogFragment";
    private static final int MAX_SELECTED_VALUES = 3;

    public static PreferredActionItemDialogFragment newInstance(final ArrayList<String> entries,
            final ArrayList<String> entryValues, final ArrayList<String> selectedValues) {
        final PreferredActionItemDialogFragment fragment = new PreferredActionItemDialogFragment();

        populateArguments(fragment, entries, entryValues, selectedValues);

        return fragment;
    }

    @Override
    protected String getFragmentTag() {
        return FRAGMENT_TAG;
    }

    @Override
    protected int getMaxSelectedValues() {
        return MAX_SELECTED_VALUES;
    }

    @Override
    protected String getDialogTitle() {
        return getString(R.string.preference_preferred_actionbar_items_dialog_title,
                getMaxSelectedValues());
    }
}
