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
import android.app.PendingIntent;
import android.app.PendingIntent.CanceledException;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import com.android.mail.providers.Account;
import com.android.mail.providers.Folder;
import com.android.mail.providers.Message;
import com.android.mail.providers.UIProvider;
import com.android.mail.utils.LogUtils;
import com.android.mail.utils.NotificationActionUtils;
import com.android.mail.utils.NotificationActionUtils.NotificationAction;

/**
 * Processes notification action {@link Intent}s that need to run off the main thread.
 */
public class NotificationActionIntentService extends IntentService {
    private static final String TAG = "NotificationActionIntentService";

    // Compose actions
    public static final String ACTION_REPLY = "com.android.mail.action.NOTIF_REPLY";
    public static final String ACTION_REPLY_ALL = "com.android.mail.action.NOTIF_REPLY_ALL";
    public static final String ACTION_FORWARD = "com.android.mail.action.NOTIF_FORWARD";
    // Toggle actions
    public static final String ACTION_MARK_READ = "com.android.mail.action.NOTIF_MARK_READ";

    // Destructive actions - These just display the undo bar
    public static final String ACTION_ARCHIVE_REMOVE_LABEL =
            "com.android.mail.action.NOTIF_ARCHIVE";
    public static final String ACTION_DELETE = "com.android.mail.action.NOTIF_DELETE";

    /**
     * This action cancels the undo notification, and does not commit any changes.
     */
    public static final String ACTION_UNDO = "com.android.mail.action.NOTIF_UNDO";

    /**
     * This action performs the actual destructive action.
     */
    public static final String ACTION_DESTRUCT = "com.android.mail.action.NOTIF_DESTRUCT";

    public static final String EXTRA_NOTIFICATION_ACTION =
            "com.android.mail.extra.EXTRA_NOTIFICATION_ACTION";
    public static final String EXTRA_NOTIFICATION_PENDING_INTENT =
            "com.android.mail.extra.EXTRA_NOTIFICATION_PENDING_INTENT";
    public static final String ACTION_UNDO_TIMEOUT = "com.android.mail.action.NOTIF_UNDO_TIMEOUT";

    public NotificationActionIntentService() {
        super("NotificationActionIntentService");
    }

    @Override
    protected void onHandleIntent(final Intent intent) {
        final Context context = this;
        final String action = intent.getAction();
        final Bundle extras = intent.getExtras();
        final NotificationAction notificationAction =
                extras.getParcelable(EXTRA_NOTIFICATION_ACTION);
        final Message message = notificationAction.getMessage();

        final ContentResolver contentResolver = getContentResolver();

        if (ACTION_UNDO.equals(action)) {
            NotificationActionUtils.cancelUndoTimeout(context, notificationAction);
            NotificationActionUtils.cancelUndoNotification(context, notificationAction);
        } else if (ACTION_ARCHIVE_REMOVE_LABEL.equals(action) || ACTION_DELETE.equals(action)) {
            // All we need to do is switch to an Undo notification
            NotificationActionUtils.createUndoNotification(context, notificationAction);

            NotificationActionUtils.registerUndoTimeout(context, notificationAction);
        } else {
            final Account account = notificationAction.getAccount();
            final String accountName = account.name;

            if (ACTION_UNDO_TIMEOUT.equals(action) || ACTION_DESTRUCT.equals(action)) {
                // Process the action
                NotificationActionUtils.cancelUndoTimeout(this, notificationAction);
                NotificationActionUtils.processUndoNotification(this, notificationAction);
            } else if (ACTION_REPLY.equals(action) || ACTION_REPLY_ALL.equals(action)
                    || ACTION_FORWARD.equals(action)) {
                cancelNotifications(accountName, notificationAction.getFolder());

                final PendingIntent pendingIntent =
                        intent.getParcelableExtra(EXTRA_NOTIFICATION_PENDING_INTENT);
                try {
                    pendingIntent.send();
                } catch (final CanceledException e) {
                    LogUtils.e(TAG, "Error replying from notification action", e);
                }
            } else if (ACTION_MARK_READ.equals(action)) {
                final Uri uri = message.uri;

                final ContentValues values = new ContentValues(1);
                values.put(UIProvider.MessageColumns.READ, 1);

                contentResolver.update(uri, values, null, null);
            }

            NotificationActionUtils.resendNotifications(context);
        }
    }

    private void cancelNotifications(final String account, final Folder folder) {
        final Intent intent = new Intent(MailIntentService.ACTION_CLEAR_NEW_MAIL_NOTIFICATIONS);
        intent.putExtra(MailIntentService.ACCOUNT_EXTRA, account);
        intent.putExtra(MailIntentService.FOLDER_EXTRA, folder);

        startService(intent);
    }
}
