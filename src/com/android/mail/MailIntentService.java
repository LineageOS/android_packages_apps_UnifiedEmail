/*
 * Copyright (C) 2012 The Android Open Source Project
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

import android.app.IntentService;
import android.content.Intent;

/**
 * A service to handle various intents asynchronously.
 */
public class MailIntentService extends IntentService {
    public static final String ACTION_RESEND_NOTIFICATIONS =
            "com.android.mail.action.RESEND_NOTIFICATIONS";
    public static final String ACTION_CLEAR_NEW_MAIL_NOTIFICATIONS =
            "com.android.mail.action.CLEAR_NEW_MAIL_NOTIFICATIONS";
    public static final String ACTION_MARK_SEEN = "com.android.mail.action.MARK_SEEN";

    public static final String ACCOUNT_EXTRA = "account";
    public static final String FOLDER_EXTRA = "folder";

    public MailIntentService() {
        super("MailIntentService");
    }

    protected MailIntentService(final String name) {
        super(name);
    }

    @Override
    protected void onHandleIntent(final Intent intent) {
        // UnifiedEmail does not handle any of these at the moment
    }
}
