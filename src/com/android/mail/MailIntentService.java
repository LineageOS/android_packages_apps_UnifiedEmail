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
import android.content.Context;
import android.content.Intent;

import com.android.mail.providers.Account;
import com.android.mail.providers.Folder;
import com.android.mail.utils.LogTag;
import com.android.mail.utils.LogUtils;
import com.android.mail.utils.NotificationUtils;

/**
 * A service to handle various intents asynchronously.
 */
public class MailIntentService extends IntentService {
    private static final String LOG_TAG = LogTag.getLogTag();

    public static final String ACTION_RESEND_NOTIFICATIONS =
            "com.android.mail.action.RESEND_NOTIFICATIONS";
    public static final String ACTION_CLEAR_NEW_MAIL_NOTIFICATIONS =
            "com.android.mail.action.CLEAR_NEW_MAIL_NOTIFICATIONS";
    public static final String ACTION_MARK_SEEN = "com.android.mail.action.MARK_SEEN";
    public static final String ACTION_BACKUP_DATA_CHANGED =
            "com.android.mail.action.BACKUP_DATA_CHANGED";

    public static final String ACCOUNT_EXTRA = "account";
    public static final String FOLDER_EXTRA = "folder";
    public static final String CONVERSATION_EXTRA = "conversation";

    public MailIntentService() {
        super("MailIntentService");
    }

    protected MailIntentService(final String name) {
        super(name);
    }

    @Override
    protected void onHandleIntent(final Intent intent) {
        // UnifiedEmail does not handle all Intents

        LogUtils.v(LOG_TAG, "Handling intent %s", intent);

        final String action = intent.getAction();

        if (Intent.ACTION_LOCALE_CHANGED.equals(action)) {
            handleLocaleChanged();
        } else if (ACTION_CLEAR_NEW_MAIL_NOTIFICATIONS.equals(action)) {
            final Account account = intent.getParcelableExtra(ACCOUNT_EXTRA);
            final Folder folder = intent.getParcelableExtra(FOLDER_EXTRA);

            NotificationUtils.clearFolderNotification(this, account, folder);
        } else if (ACTION_RESEND_NOTIFICATIONS.equals(action)) {
            NotificationUtils.resendNotifications(this, false);
        } else if (ACTION_MARK_SEEN.equals(action)) {
            final Folder folder = intent.getParcelableExtra(FOLDER_EXTRA);

            NotificationUtils.markSeen(this, folder);
        }
    }

    public static void broadcastBackupDataChanged(final Context context) {
        final Intent intent = new Intent(ACTION_BACKUP_DATA_CHANGED);
        context.startService(intent);
    }

    private void handleLocaleChanged() {
        // Cancel all notifications. The correct ones will be recreated when the app starts back up
        NotificationUtils.cancelAndResendNotifications(this);
    }
}
