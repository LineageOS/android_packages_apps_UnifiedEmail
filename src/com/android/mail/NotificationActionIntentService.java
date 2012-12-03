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
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import com.android.mail.providers.Message;
import com.android.mail.providers.UIProvider;
import com.android.mail.utils.NotificationActionUtils;
import com.android.mail.utils.NotificationActionUtils.NotificationAction;

/**
 * Processes notification action {@link Intent}s that need to run off the main thread.
 */
public class NotificationActionIntentService extends IntentService {
    // Toggle actions
    public static final String ACTION_MARK_READ = "com.android.mail.action.NOTIF_MARK_READ";
    // TODO: We may remove the ability to mark unread
    public static final String ACTION_MARK_UNREAD = "com.android.mail.action.NOTIF_MARK_UNREAD";
    public static final String ACTION_STAR = "com.android.mail.action.NOTIF_STAR";
    public static final String ACTION_UNSTAR = "com.android.mail.action.NOTIF_UNSTAR";
    public static final String ACTION_MARK_IMPORTANT =
            "com.android.mail.action.NOTIF_MARK_IMPORTANT";
    public static final String ACTION_MARK_NOT_IMPORTANT =
            "com.android.mail.action.NOTIF_MARK_NOT_IMPORTANT";

    // Destructive actions - These just display the undo bar
    public static final String ACTION_ARCHIVE_REMOVE_LABEL =
            "com.android.mail.action.NOTIF_ARCHIVE";
    public static final String ACTION_DELETE = "com.android.mail.action.NOTIF_DELETE";
    public static final String ACTION_MUTE = "com.android.mail.action.NOTIF_MUTE";

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
        } else if (ACTION_ARCHIVE_REMOVE_LABEL.equals(action) || ACTION_DELETE.equals(action)
                || ACTION_MUTE.equals(action)) {
            // All we need to do is switch to an Undo notification
            NotificationActionUtils.createUndoNotification(context, notificationAction);

            NotificationActionUtils.registerUndoTimeout(context, notificationAction);
        } else {
            if (ACTION_UNDO_TIMEOUT.equals(action) || ACTION_DESTRUCT.equals(action)) {
                // Process the action
                NotificationActionUtils.cancelUndoTimeout(this, notificationAction);
                NotificationActionUtils.processUndoNotification(this, notificationAction);
            } else if (ACTION_MARK_READ.equals(action)) {
                final Uri uri = message.uri;

                final ContentValues values = new ContentValues(1);
                values.put(UIProvider.MessageColumns.READ, 1);

                contentResolver.update(uri, values, null, null);
            } else if (ACTION_MARK_UNREAD.equals(action)) {
                // TODO: We may remove the ability to mark unread
                final Uri uri = message.uri;

                final ContentValues values = new ContentValues(1);
                values.put(UIProvider.MessageColumns.READ, 0);

                contentResolver.update(uri, values, null, null);
            } else if (ACTION_STAR.equals(action)) {
                final Uri uri = message.uri;

                final ContentValues values = new ContentValues(1);
                values.put(UIProvider.MessageColumns.STARRED, 1);

                contentResolver.update(uri, values, null, null);
            } else if (ACTION_UNSTAR.equals(action)) {
                final Uri uri = message.uri;

                final ContentValues values = new ContentValues(1);
                values.put(UIProvider.MessageColumns.STARRED, 0);

                contentResolver.update(uri, values, null, null);
            } else if (ACTION_MARK_IMPORTANT.equals(action)) {
                final Uri uri = message.uri;

                final ContentValues values = new ContentValues(1);
                values.put(UIProvider.ConversationColumns.PRIORITY,
                        UIProvider.ConversationPriority.IMPORTANT);

                contentResolver.update(uri, values, null, null);
            } else if (ACTION_MARK_NOT_IMPORTANT.equals(action)) {
                final Uri uri = message.uri;

                final ContentValues values = new ContentValues(1);
                values.put(UIProvider.ConversationColumns.PRIORITY,
                        UIProvider.ConversationPriority.DEFAULT);

                contentResolver.update(uri, values, null, null);
            }

            NotificationActionUtils.resendNotifications(context);
        }
    }
}
