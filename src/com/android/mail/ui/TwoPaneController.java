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

import com.android.mail.ViewMode;

/**
 * Controller for one-pane Mail activity. One Pane is used for phones, where screen real estate is
 * limited.
 */

// Called OnePaneActivityController in Gmail.
public class TwoPaneController extends AbstractActivityController {

    /**
     * @param activity
     * @param viewMode
     */
    public TwoPaneController(MailActivity activity, ViewMode viewMode) {
        super(activity, viewMode);
        // TODO(viki): Auto-generated constructor stub
    }

}
