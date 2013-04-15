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

import com.android.mail.utils.StorageLowState;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;

import com.android.mail.providers.Account;
import com.android.mail.providers.Folder;
import com.android.mail.utils.LogTag;
import com.android.mail.utils.LogUtils;
import com.android.mail.utils.NotificationUtils;
import com.android.mail.utils.Utils;

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
            final Account account = intent.getParcelableExtra(Utils.EXTRA_ACCOUNT);
            final Folder folder = intent.getParcelableExtra(Utils.EXTRA_FOLDER);

            NotificationUtils.clearFolderNotification(this, account, folder);
        } else if (ACTION_RESEND_NOTIFICATIONS.equals(action)) {
            NotificationUtils.resendNotifications(this, false);
        } else if (ACTION_MARK_SEEN.equals(action)) {
            final Folder folder = intent.getParcelableExtra(Utils.EXTRA_FOLDER);

            NotificationUtils.markSeen(this, folder);
        } else if (Intent.ACTION_DEVICE_STORAGE_LOW.equals(action)) {
            // The storage_low state is recorded centrally even though
            // no handler might be present to change application state
            // based on state changes.
            StorageLowState.setIsStorageLow(true);
        } else if (Intent.ACTION_DEVICE_STORAGE_OK.equals(action)) {
            StorageLowState.setIsStorageLow(false);
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
