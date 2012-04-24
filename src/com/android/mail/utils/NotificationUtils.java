/**
 * Copyright (c) 2011, Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.mail.utils;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Build;
import android.text.TextUtils;

import com.android.mail.R;
import com.android.mail.compose.ComposeActivity;
import com.android.mail.providers.Account;
import com.android.mail.providers.Message;

import java.util.ArrayList;

public class NotificationUtils {

    // TODO: add degraded experience for ICS.
    // The plan is to do a 2 line where the first line is sender and subject
    // (stylized) and the second line is account and unread count.
    private static boolean newNotificationsAvailable() {
        return TextUtils.equals("JellyBean", Build.VERSION.RELEASE);
    }

    public static Notification.Builder buildNotification(Context context, Notification.Builder nf,
            String subject, String sender, String snippet, int smallIconResId, String account,
            Bitmap senderBitmap, int count) {
     /*   if (newNotificationsAvailable()) {
            nf.setContentTitle(sender).setContentText(subject).setLargeIcon(senderBitmap)
                    .setSubText(account).setNumber(count).setSmallIcon(smallIconResId)
                    .setTicker(sender);
        } else {
            nf.setContentTitle(sender).setContentText(subject).setLargeIcon(senderBitmap)
                    .setPriority(Notification.PRIORITY_HIGH).setSubText(account).setNumber(count)
                    .setSmallIcon(smallIconResId).setTicker(sender);
        }*/
        return nf;
    }

    // TODO: add degraded experience for ICS.
    // The plan is to do a 2 line where the first line is sender and subject
    // (stylized) and the second line is account and unread count.
    public static Notification.Builder buildExpandedNotification(Context context,
            Notification.Builder nf, String subject, String sender, String snippet,
            int smallIconResId, ArrayList<NotificationAction> actions, String account,
            Bitmap senderBitmap, int count) {
     /*   if (newNotificationsAvailable()) {
            nf.setContentTitle(sender).setContentText(subject).setLargeIcon(senderBitmap)
                    .setPriority(Notification.PRIORITY_HIGH).setSubText(account).setNumber(count)
                    .setSmallIcon(smallIconResId).setTicker(sender);
            for (NotificationAction action : actions) {
                nf.addAction(action.iconResId, context.getText(action.titleResId),
                        action.clickIntent);
            }
        } else {
            nf.setContentTitle(sender).setContentText(subject).setLargeIcon(senderBitmap)
                    .setPriority(Notification.PRIORITY_HIGH).setSubText(account).setNumber(count)
                    .setSmallIcon(smallIconResId).setTicker(sender);
        }*/
        return nf;
    }

    public static Object applyBigTextStyle(Notification.Builder nf, String snippet) {
        if (newNotificationsAvailable()) {
            return new Notification.BigTextStyle(nf).bigText(snippet);
        } else {
            return nf;
        }
    }

    public static NotificationAction getReplyAction(PendingIntent clickIntent) {
        return new NotificationAction(R.drawable.ic_reply_holo_dark, R.string.reply, clickIntent);
    }

    public static NotificationAction getReplyAllAction(PendingIntent clickIntent) {
        return new NotificationAction(R.drawable.ic_reply_all_holo_dark, R.string.reply_all,
                clickIntent);
    }

    /**
     * @return an intent which, if launched, will display the corresponding
     *         conversation
     */
    public static PendingIntent createReplyIntent(Context context, Account uiAccount,
            Message message, boolean replyAll) {

        final Intent intent = new Intent();
        intent.setAction(Intent.ACTION_SENDTO);
        intent.setData(uiAccount.composeIntentUri);
        if (uiAccount.composeIntentUri != null) {
            intent.putExtra(Utils.EXTRA_COMPOSE_URI, uiAccount.composeIntentUri);
        }
        intent.putExtra(ComposeActivity.EXTRA_ACTION, replyAll ? ComposeActivity.REPLY_ALL
                : ComposeActivity.REPLY);
        intent.putExtra(Utils.EXTRA_ACCOUNT, uiAccount);
        intent.putExtra(ComposeActivity.EXTRA_IN_REFERENCE_TO_MESSAGE, message);
        PendingIntent pi = PendingIntent.getActivity(context, 0, intent,
                PendingIntent.FLAG_CANCEL_CURRENT);
        return pi;
    }
}
