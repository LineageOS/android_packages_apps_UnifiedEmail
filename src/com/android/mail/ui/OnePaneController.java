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

import android.os.Bundle;
import android.view.Window;

import com.android.mail.R;

/**
 * Controller for one-pane Mail activity. One Pane is used for phones, where screen real estate is
 * limited.
 */

// Called OnePaneActivityController in Gmail.
public final class OnePaneController extends AbstractActivityController {
    /**
     * @param activity
     * @param viewMode
     */
    public OnePaneController(MailActivity activity, ViewMode viewMode) {
        super(activity, viewMode);
        // TODO(viki): Auto-generated constructor stub
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        // Request opaque actionbar
        mActivity.getWindow().requestFeature(Window.FEATURE_ACTION_BAR);
        // Set 1-pane content view.
        mActivity.setContentView(R.layout.one_pane_activity);

        super.onCreate(savedInstanceState);
    }

    @Override
    protected boolean isConversationListVisible() {
        // TODO(viki): Auto-generated method stub
        return false;
    }
}
