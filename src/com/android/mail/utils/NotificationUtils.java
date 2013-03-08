/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.mail.utils;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.Contacts.Photo;
import android.support.v4.app.NotificationCompat;
import android.text.Html;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextUtils.SimpleStringSplitter;
import android.text.style.CharacterStyle;
import android.text.style.TextAppearanceSpan;
import android.util.Pair;
import android.util.SparseArray;

import com.android.mail.EmailAddress;
import com.android.mail.MailIntentService;
import com.android.mail.R;
import com.android.mail.browse.MessageCursor;
import com.android.mail.browse.SendersView;
import com.android.mail.preferences.AccountPreferences;
import com.android.mail.preferences.FolderPreferences;
import com.android.mail.preferences.MailPrefs;
import com.android.mail.providers.Account;
import com.android.mail.providers.Conversation;
import com.android.mail.providers.Folder;
import com.android.mail.providers.Message;
import com.android.mail.providers.UIProvider;
import com.android.mail.utils.NotificationActionUtils.NotificationAction;
import com.google.android.common.html.parser.HTML;
import com.google.android.common.html.parser.HTML4;
import com.google.android.common.html.parser.HtmlDocument;
import com.google.android.common.html.parser.HtmlTree;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class NotificationUtils {
    public static final String LOG_TAG = LogTag.getLogTag();

    /** Contains a list of <(account, label), unread conversations> */
    private static NotificationMap sActiveNotificationMap = null;

    private static final SparseArray<Bitmap> sNotificationIcons = new SparseArray<Bitmap>();

    private static TextAppearanceSpan sNotificationUnreadStyleSpan;
    private static CharacterStyle sNotificationReadStyleSpan;

    private static final Map<Integer, Integer> sPriorityToLength = Maps.newHashMap();
    private static final SimpleStringSplitter SENDER_LIST_SPLITTER =
            new SimpleStringSplitter(Utils.SENDER_LIST_SEPARATOR);
    private static String[] sSenderFragments = new String[8];

    /** A factory that produces a plain text converter that removes elided text. */
    private static final HtmlTree.PlainTextConverterFactory MESSAGE_CONVERTER_FACTORY =
            new HtmlTree.PlainTextConverterFactory() {
                @Override
                public HtmlTree.PlainTextConverter createInstance() {
                    return new MailMessagePlainTextConverter();
                }
            };

    /**
     * Clears all notifications in response to the user tapping "Clear" in the status bar.
     */
    public static void clearAllNotfications(Context context) {
        LogUtils.v(LOG_TAG, "Clearing all notifications.");
        final NotificationMap notificationMap = getNotificationMap(context);
        notificationMap.clear();
        notificationMap.saveNotificationMap(context);
    }

    /**
     * Returns the notification map, creating it if necessary.
     */
    private static synchronized NotificationMap getNotificationMap(Context context) {
        if (sActiveNotificationMap == null) {
            sActiveNotificationMap = new NotificationMap();

            // populate the map from the cached data
            sActiveNotificationMap.loadNotificationMap(context);
        }
        return sActiveNotificationMap;
    }

    /**
     * Class representing the existing notifications, and the number of unread and
     * unseen conversations that triggered each.
     */
    private static class NotificationMap
            extends ConcurrentHashMap<NotificationKey, Pair<Integer, Integer>> {

        private static final String NOTIFICATION_PART_SEPARATOR = " ";
        private static final int NUM_NOTIFICATION_PARTS= 4;

        /**
         * Retuns the unread count for the given NotificationKey.
         */
        public Integer getUnread(NotificationKey key) {
            final Pair<Integer, Integer> value = get(key);
            return value != null ? value.first : null;
        }

        /**
         * Retuns the unread unseen count for the given NotificationKey.
         */
        public Integer getUnseen(NotificationKey key) {
            final Pair<Integer, Integer> value = get(key);
            return value != null ? value.second : null;
        }

        /**
         * Store the unread and unseen value for the given NotificationKey
         */
        public void put(NotificationKey key, int unread, int unseen) {
            final Pair<Integer, Integer> value =
                    new Pair<Integer, Integer>(Integer.valueOf(unread), Integer.valueOf(unseen));
            put(key, value);
        }

        /**
         * Populates the notification map with previously cached data.
         */
        public synchronized void loadNotificationMap(final Context context) {
            final MailPrefs mailPrefs = MailPrefs.get(context);
            final Set<String> notificationSet = mailPrefs.getActiveNotificationSet();
            if (notificationSet != null) {
                for (String notificationEntry : notificationSet) {
                    // Get the parts of the string that make the notification entry
                    final String[] notificationParts =
                            TextUtils.split(notificationEntry, NOTIFICATION_PART_SEPARATOR);
                    if (notificationParts.length == NUM_NOTIFICATION_PARTS) {
                        final Uri accountUri = Uri.parse(notificationParts[0]);
                        final Cursor accountCursor = context.getContentResolver().query(
                                accountUri, UIProvider.ACCOUNTS_PROJECTION, null, null, null);
                        final Account account;
                        try {
                            if (accountCursor.moveToFirst()) {
                                account = new Account(accountCursor);
                            } else {
                                continue;
                            }
                        } finally {
                            accountCursor.close();
                        }

                        final Uri folderUri = Uri.parse(notificationParts[1]);
                        final Cursor folderCursor = context.getContentResolver().query(
                                folderUri, UIProvider.FOLDERS_PROJECTION, null, null, null);
                        final Folder folder;
                        try {
                            if (folderCursor.moveToFirst()) {
                                folder = new Folder(folderCursor);
                            } else {
                                continue;
                            }
                        } finally {
                            folderCursor.close();
                        }

                        final NotificationKey key = new NotificationKey(account, folder);
                        final Integer unreadValue = Integer.valueOf(notificationParts[2]);
                        final Integer unseenValue = Integer.valueOf(notificationParts[3]);
                        final Pair<Integer, Integer> unreadUnseenValue =
                                new Pair<Integer, Integer>(unreadValue, unseenValue);
                        put(key, unreadUnseenValue);
                    }
                }
            }
        }

        /**
         * Cache the notification map.
         */
        public synchronized void saveNotificationMap(Context context) {
            final Set<String> notificationSet = Sets.newHashSet();
            final Set<NotificationKey> keys = keySet();
            for (NotificationKey key : keys) {
                final Pair<Integer, Integer> value = get(key);
                final Integer unreadCount = value.first;
                final Integer unseenCount = value.second;
                if (value != null && unreadCount != null && unseenCount != null) {
                    final String[] partValues = new String[] {
                            key.account.uri.toString(), key.folder.uri.toString(),
                            unreadCount.toString(), unseenCount.toString()};
                    notificationSet.add(TextUtils.join(NOTIFICATION_PART_SEPARATOR, partValues));
                }
            }
            final MailPrefs mailPrefs = MailPrefs.get(context);
            mailPrefs.cacheActiveNotificationSet(notificationSet);
        }
    }

    /**
     * @return the title of this notification with each account and the number of unread and unseen
     * conversations for it. Also remove any account in the map that has 0 unread.
     */
    private static String createNotificationString(NotificationMap notifications) {
        StringBuilder result = new StringBuilder();
        int i = 0;
        Set<NotificationKey> keysToRemove = Sets.newHashSet();
        for (NotificationKey key : notifications.keySet()) {
            Integer unread = notifications.getUnread(key);
            Integer unseen = notifications.getUnseen(key);
            if (unread == null || unread.intValue() == 0) {
                keysToRemove.add(key);
            } else {
                if (i > 0) result.append(", ");
                result.append(key.toString() + " (" + unread + ", " + unseen + ")");
                i++;
            }
        }

        for (NotificationKey key : keysToRemove) {
            notifications.remove(key);
        }

        return result.toString();
    }

    /**
     * Get all notifications for all accounts and cancel them.
     **/
    public static void cancelAllNotifications(Context context) {
        NotificationManager nm = (NotificationManager) context.getSystemService(
                Context.NOTIFICATION_SERVICE);
        nm.cancelAll();
        clearAllNotfications(context);
    }

    /**
     * Get all notifications for all accounts, cancel them, and repost.
     * This happens when locale changes.
     **/
    public static void cancelAndResendNotifications(Context context) {
        resendNotifications(context, true);
    }

    /**
     * Get all notifications for all accounts, optionally cancel them, and repost.
     * This happens when locale changes.
     **/
    public static void resendNotifications(Context context, final boolean cancelExisting) {
        if (cancelExisting) {
            NotificationManager nm =
                    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            nm.cancelAll();
        }
        // Re-validate the notifications.
        final NotificationMap notificationMap = getNotificationMap(context);
        final Set<NotificationKey> keys = notificationMap.keySet();
        for (NotificationKey notification : keys) {
            final Folder folder = notification.folder;
            final int notificationId = getNotificationId(notification.account.name, folder);

            final NotificationAction undoableAction =
                    NotificationActionUtils.sUndoNotifications.get(notificationId);
            if (undoableAction == null) {
                validateNotifications(context, folder, notification.account, true, false,
                        notification);
            } else {
                // Create an undo notification
                NotificationActionUtils.createUndoNotification(context, undoableAction);
            }
        }
    }

    /**
     * Validate the notifications for the specified account.
     */
    public static void validateAccountNotifications(Context context, String account) {
        List<NotificationKey> notificationsToCancel = Lists.newArrayList();
        // Iterate through the notification map to see if there are any entries that correspond to
        // labels that are not in the sync set.
        final NotificationMap notificationMap = getNotificationMap(context);
        Set<NotificationKey> keys = notificationMap.keySet();
        final AccountPreferences accountPreferences = new AccountPreferences(context, account);
        final boolean enabled = accountPreferences.areNotificationsEnabled();
        if (!enabled) {
            // Cancel all notifications for this account
            for (NotificationKey notification : keys) {
                if (notification.account.name.equals(account)) {
                    notificationsToCancel.add(notification);
                }
            }
        } else {
            // Iterate through the notification map to see if there are any entries that
            // correspond to labels that are not in the notification set.
            for (NotificationKey notification : keys) {
                if (notification.account.name.equals(account)) {
                    // If notification is not enabled for this label, remember this NotificationKey
                    // to later cancel the notification, and remove the entry from the map
                    final Folder folder = notification.folder;
                    final boolean isInbox =
                            notification.account.settings.defaultInbox.equals(folder.uri);
                    final FolderPreferences folderPreferences = new FolderPreferences(
                            context, notification.account.name, folder, isInbox);

                    if (!folderPreferences.areNotificationsEnabled()) {
                        notificationsToCancel.add(notification);
                    }
                }
            }
        }

        // Cancel & remove the invalid notifications.
        if (notificationsToCancel.size() > 0) {
            NotificationManager nm = (NotificationManager) context.getSystemService(
                    Context.NOTIFICATION_SERVICE);
            for (NotificationKey notification : notificationsToCancel) {
                final Folder folder = notification.folder;
                final int notificationId = getNotificationId(notification.account.name, folder);
                nm.cancel(notificationId);
                notificationMap.remove(notification);
                NotificationActionUtils.sUndoNotifications.remove(notificationId);
                NotificationActionUtils.sNotificationTimestamps.delete(notificationId);
            }
            notificationMap.saveNotificationMap(context);
        }
    }

    /**
     * Display only one notification.
     */
    public static void setNewEmailIndicator(Context context, final int unreadCount,
            final int unseenCount, final Account account, final Folder folder,
            final boolean getAttention) {
        boolean ignoreUnobtrusiveSetting = false;

        final int notificationId = getNotificationId(account.name, folder);

        // Update the notification map
        final NotificationMap notificationMap = getNotificationMap(context);
        final NotificationKey key = new NotificationKey(account, folder);
        if (unreadCount == 0) {
            notificationMap.remove(key);
            ((NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE))
                    .cancel(notificationId);
        } else {
            if (!notificationMap.containsKey(key)) {
                // This account previously didn't have any unread mail; ignore the "unobtrusive
                // notifications" setting and play sound and/or vibrate the device even if a
                // notification already exists (bug 2412348).
                ignoreUnobtrusiveSetting = true;
            }
            notificationMap.put(key, unreadCount, unseenCount);
        }
        notificationMap.saveNotificationMap(context);

        if (LogUtils.isLoggable(LOG_TAG, LogUtils.VERBOSE)) {
            LogUtils.v(LOG_TAG, "New email: %s mapSize: %d getAttention: %b",
                    createNotificationString(notificationMap), notificationMap.size(),
                    getAttention);
        }

        if (NotificationActionUtils.sUndoNotifications.get(notificationId) == null) {
            validateNotifications(context, folder, account, getAttention, ignoreUnobtrusiveSetting,
                    key);
        }
    }

    /**
     * Validate the notifications notification.
     */
    private static void validateNotifications(Context context, final Folder folder,
            final Account account, boolean getAttention, boolean ignoreUnobtrusiveSetting,
            NotificationKey key) {

        NotificationManager nm = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);

        final NotificationMap notificationMap = getNotificationMap(context);
        if (LogUtils.isLoggable(LOG_TAG, LogUtils.VERBOSE)) {
            LogUtils.v(LOG_TAG, "Validating Notification: %s mapSize: %d folder: %s "
                    + "getAttention: %b", createNotificationString(notificationMap),
                    notificationMap.size(), folder.name, getAttention);
        }
        // The number of unread messages for this account and label.
        final Integer unread = notificationMap.getUnread(key);
        final int unreadCount = unread != null ? unread.intValue() : 0;
        final Integer unseen = notificationMap.getUnseen(key);
        int unseenCount = unseen != null ? unseen.intValue() : 0;

        Cursor cursor = null;

        try {
            final Uri.Builder uriBuilder = folder.conversationListUri.buildUpon();
            uriBuilder.appendQueryParameter(
                    UIProvider.SEEN_QUERY_PARAMETER, Boolean.FALSE.toString());
            cursor = context.getContentResolver().query(uriBuilder.build(),
                    UIProvider.CONVERSATION_PROJECTION, null, null, null);
            final int cursorUnseenCount = cursor.getCount();

            // Make sure the unseen count matches the number of items in the cursor.  But, we don't
            // want to overwrite a 0 unseen count that was specified in the intent
            if (unseenCount != 0 && unseenCount != cursorUnseenCount) {
                LogUtils.d(LOG_TAG,
                        "Unseen count doesn't match cursor count.  unseen: %d cursor count: %d",
                        unseenCount, cursorUnseenCount);
                unseenCount = cursorUnseenCount;
            }

            // For the purpose of the notifications, the unseen count should be capped at the num of
            // unread conversations.
            if (unseenCount > unreadCount) {
                unseenCount = unreadCount;
            }

            final int notificationId = getNotificationId(account.name, folder);

            if (unseenCount == 0) {
                nm.cancel(notificationId);
                return;
            }

            // We now have all we need to create the notification and the pending intent
            PendingIntent clickIntent;

            NotificationCompat.Builder notification = new NotificationCompat.Builder(context);
            notification.setSmallIcon(R.drawable.stat_notify_email);
            notification.setTicker(account.name);

            final long when;

            final long oldWhen =
                    NotificationActionUtils.sNotificationTimestamps.get(notificationId);
            if (oldWhen != 0) {
                when = oldWhen;
            } else {
                when = System.currentTimeMillis();
            }

            notification.setWhen(when);

            // The timestamp is now stored in the notification, so we can remove it from here
            NotificationActionUtils.sNotificationTimestamps.delete(notificationId);

            // Dispatch a CLEAR_NEW_MAIL_NOTIFICATIONS intent if the user taps the "X" next to a
            // notification.  Also this intent gets fired when the user taps on a notification as
            // the AutoCancel flag has been set
            final Intent cancelNotificationIntent =
                    new Intent(MailIntentService.ACTION_CLEAR_NEW_MAIL_NOTIFICATIONS);
            cancelNotificationIntent.setPackage(context.getPackageName());
            cancelNotificationIntent.setData(Utils.appendVersionQueryParameter(context,
                    folder.uri));
            cancelNotificationIntent.putExtra(MailIntentService.ACCOUNT_EXTRA, account);
            cancelNotificationIntent.putExtra(MailIntentService.FOLDER_EXTRA, folder);

            notification.setDeleteIntent(PendingIntent.getService(
                    context, notificationId, cancelNotificationIntent, 0));

            // Ensure that the notification is cleared when the user selects it
            notification.setAutoCancel(true);

            boolean eventInfoConfigured = false;

            final boolean isInbox = account.settings.defaultInbox.equals(folder.uri);
            final FolderPreferences folderPreferences =
                    new FolderPreferences(context, account.name, folder, isInbox);

            if (isInbox) {
                final AccountPreferences accountPreferences =
                        new AccountPreferences(context, account.name);
                moveNotificationSetting(accountPreferences, folderPreferences);
            }

            if (!folderPreferences.areNotificationsEnabled()) {
                // Don't notify
                return;
            }

            if (unreadCount > 0) {
                // How can I order this properly?
                if (cursor.moveToNext()) {
                    Intent notificationIntent = createViewConversationIntent(context, account,
                            folder, null);

                    // Launch directly to the conversation, if the
                    // number of unseen conversations == 1
                    if (unseenCount == 1) {
                        notificationIntent = createViewConversationIntent(context, account, folder,
                                cursor);
                    }

                    if (notificationIntent == null) {
                        LogUtils.e(LOG_TAG, "Null intent when building notification");
                        return;
                    }

                    clickIntent = PendingIntent.getActivity(context, -1, notificationIntent, 0);
                    configureLatestEventInfoFromConversation(context, account, folderPreferences,
                            notification, cursor, clickIntent, notificationIntent,
                            account.name, unreadCount, unseenCount, folder, when);
                    eventInfoConfigured = true;
                }
            }

            final boolean vibrate = folderPreferences.isNotificationVibrateEnabled();
            final String ringtoneUri = folderPreferences.getNotificationRingtoneUri();
            final boolean notifyOnce = !folderPreferences.isEveryMessageNotificationEnabled();

            if (!ignoreUnobtrusiveSetting && account != null && notifyOnce) {
                // If the user has "unobtrusive notifications" enabled, only alert the first time
                // new mail is received in this account.  This is the default behavior.  See
                // bugs 2412348 and 2413490.
                notification.setOnlyAlertOnce(true);
            }

            if (account != null) {
                LogUtils.d(LOG_TAG, "Account: %s vibrate: %s", account.name,
                        Boolean.toString(folderPreferences.isNotificationVibrateEnabled()));
            }

            int defaults = 0;

            /*
             * We do not want to notify if this is coming back from an Undo notification, hence the
             * oldWhen check.
             */
            if (getAttention && account != null && oldWhen == 0) {
                final AccountPreferences accountPreferences =
                        new AccountPreferences(context, account.name);
                if (accountPreferences.areNotificationsEnabled()) {
                    if (vibrate) {
                        defaults |= Notification.DEFAULT_VIBRATE;
                    }

                    notification.setSound(TextUtils.isEmpty(ringtoneUri) ? null
                            : Uri.parse(ringtoneUri));
                    LogUtils.d(LOG_TAG, "New email in %s vibrateWhen: %s, playing notification: %s",
                            account.name, vibrate, ringtoneUri);
                }
            }

            if (eventInfoConfigured) {
                defaults |= Notification.DEFAULT_LIGHTS;
                notification.setDefaults(defaults);

                if (oldWhen != 0) {
                    // We do not want to display the ticker again if we are re-displaying this
                    // notification (like from an Undo notification)
                    notification.setTicker(null);
                }

                nm.notify(notificationId, notification.build());
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    /**
     * @return an {@link Intent} which, if launched, will display the corresponding conversation
     */
    private static Intent createViewConversationIntent(final Context context, final Account account,
            final Folder folder, final Cursor cursor) {
        if (folder == null || account == null) {
            LogUtils.e(LOG_TAG, "Null account or folder.  account: %s folder: %s",
                    account, folder);
            return null;
        }

        final Intent intent;

        if (cursor == null) {
            intent = Utils.createViewFolderIntent(context, folder, account);
        } else {
            // A conversation cursor has been specified, so this intent is intended to be go
            // directly to the one new conversation

            // Get the Conversation object
            final Conversation conversation = new Conversation(cursor);
            intent = Utils.createViewConversationIntent(context, conversation, folder, account);
        }

        return intent;
    }

    private static Bitmap getDefaultNotificationIcon(
            final Context context, final Folder folder, final boolean multipleNew) {
        final Bitmap icon;
        if (folder.notificationIconResId != 0) {
            icon = getIcon(context, folder.notificationIconResId);
        } else if (multipleNew) {
            icon = getIcon(context, R.drawable.ic_notification_multiple_mail_holo_dark);
        } else {
            icon = getIcon(context, R.drawable.ic_contact_picture);
        }
        return icon;
    }

    private static Bitmap getIcon(final Context context, final int resId) {
        final Bitmap cachedIcon = sNotificationIcons.get(resId);
        if (cachedIcon != null) {
            return cachedIcon;
        }

        final Bitmap icon = BitmapFactory.decodeResource(context.getResources(), resId);
        sNotificationIcons.put(resId, icon);

        return icon;
    }

    private static void configureLatestEventInfoFromConversation(final Context context,
            final Account account, final FolderPreferences folderPreferences,
            final NotificationCompat.Builder notification, final Cursor conversationCursor,
            final PendingIntent clickIntent, final Intent notificationIntent,
            final String notificationAccount, final int unreadCount, final int unseenCount,
            final Folder folder, final long when) {
        final Resources res = context.getResources();

        LogUtils.w(LOG_TAG, "Showing notification with unreadCount of %d and "
                + "unseenCount of %d", unreadCount, unseenCount);

        String notificationTicker = null;

        // Boolean indicating that this notification is for a non-inbox label.
        final boolean isInbox = account.settings.defaultInbox.equals(folder.uri);

        // Notification label name for user label notifications.
        final String notificationLabelName = isInbox ? null : folder.name;

        if (unseenCount > 1) {
            // Build the string that describes the number of new messages
            final String newMessagesString = res.getString(R.string.new_messages, unseenCount);

            // Use the default notification icon
            notification.setLargeIcon(
                    getDefaultNotificationIcon(context, folder, true /* multiple new messages */));

            // The ticker initially start as the new messages string.
            notificationTicker = newMessagesString;

            // The title of the notification is the new messages string
            notification.setContentTitle(newMessagesString);

            if (com.android.mail.utils.Utils.isRunningJellybeanOrLater()) {
                // For a new-style notification
                final int maxNumDigestItems = context.getResources().getInteger(
                        R.integer.max_num_notification_digest_items);

                // The body of the notification is the account name, or the label name.
                notification.setSubText(isInbox ? notificationAccount : notificationLabelName);

                final NotificationCompat.InboxStyle digest =
                        new NotificationCompat.InboxStyle(notification);

                digest.setBigContentTitle(newMessagesString);

                int numDigestItems = 0;
                do {
                    final Conversation conversation = new Conversation(conversationCursor);

                    if (!conversation.read) {
                        boolean multipleUnreadThread = false;
                        // TODO(cwren) extract this pattern into a helper

                        Cursor cursor = null;
                        MessageCursor messageCursor = null;
                        try {
                            final Uri.Builder uriBuilder = conversation.messageListUri.buildUpon();
                            uriBuilder.appendQueryParameter(
                                    UIProvider.LABEL_QUERY_PARAMETER, notificationLabelName);
                            cursor = context.getContentResolver().query(uriBuilder.build(),
                                    UIProvider.MESSAGE_PROJECTION, null, null, null);
                            messageCursor = new MessageCursor(cursor);

                            String from = null;
                            String fromAddress = "";
                            if (messageCursor.moveToPosition(messageCursor.getCount() - 1)) {
                                final Message message = messageCursor.getMessage();
                                fromAddress = message.getFrom();
                                from = getDisplayableSender(fromAddress);
                            }
                            while (messageCursor.moveToPosition(messageCursor.getPosition() - 1)) {
                                final Message message = messageCursor.getMessage();
                                if (!message.read &&
                                        !fromAddress.contentEquals(message.getFrom())) {
                                    multipleUnreadThread = true;
                                    break;
                                }
                            }
                            final SpannableStringBuilder sendersBuilder;
                            if (multipleUnreadThread) {
                                final int sendersLength =
                                        res.getInteger(R.integer.swipe_senders_length);

                                sendersBuilder = getStyledSenders(context, conversationCursor,
                                        sendersLength, notificationAccount);
                            } else {
                                sendersBuilder = new SpannableStringBuilder(from);
                            }
                            final CharSequence digestLine = getSingleMessageInboxLine(context,
                                    sendersBuilder.toString(),
                                    conversation.subject,
                                    conversation.snippet);
                            digest.addLine(digestLine);
                            numDigestItems++;
                        } finally {
                            if (messageCursor != null) {
                                messageCursor.close();
                            }
                            if (cursor != null) {
                                cursor.close();
                            }
                        }
                    }
                } while (numDigestItems <= maxNumDigestItems && conversationCursor.moveToNext());
            } else {
                // The body of the notification is the account name, or the label name.
                notification.setContentText(
                        isInbox ? notificationAccount : notificationLabelName);
            }
        } else {
            // For notifications for a single new conversation, we want to get the information from
            // the conversation

            // Move the cursor to the most recent unread conversation
            seekToLatestUnreadConversation(conversationCursor);

            final Conversation conversation = new Conversation(conversationCursor);

            Cursor cursor = null;
            MessageCursor messageCursor = null;
            boolean multipleUnseenThread = false;
            String from = null;
            try {
                cursor = context.getContentResolver().query(conversation.messageListUri,
                        UIProvider.MESSAGE_PROJECTION, null, null, null);
                messageCursor = new MessageCursor(cursor);
                // Use the information from the last sender in the conversation that triggered
                // this notification.

                String fromAddress = "";
                if (messageCursor.moveToPosition(messageCursor.getCount() - 1)) {
                    final Message message = messageCursor.getMessage();
                    fromAddress = message.getFrom();
                    from = getDisplayableSender(fromAddress);
                    notification.setLargeIcon(
                            getContactIcon(context, getSenderAddress(fromAddress), folder));
                }

                // Assume that the last message in this conversation is unread
                int firstUnseenMessagePos = messageCursor.getPosition();
                while (messageCursor.moveToPosition(messageCursor.getPosition() - 1)) {
                    final Message message = messageCursor.getMessage();
                    final boolean unseen = !message.seen;
                    if (unseen) {
                        firstUnseenMessagePos = messageCursor.getPosition();
                        if (!multipleUnseenThread
                                && !fromAddress.contentEquals(message.getFrom())) {
                            multipleUnseenThread = true;
                        }
                    }
                }

                if (Utils.isRunningJellybeanOrLater()) {
                    // For a new-style notification

                    if (multipleUnseenThread) {
                        // The title of a single conversation is the list of senders.
                        int sendersLength = res.getInteger(R.integer.swipe_senders_length);

                        final SpannableStringBuilder sendersBuilder = getStyledSenders(
                                context, conversationCursor, sendersLength, notificationAccount);

                        notification.setContentTitle(sendersBuilder);
                        // For a single new conversation, the ticker is based on the sender's name.
                        notificationTicker = sendersBuilder.toString();
                    } else {
                        // The title of a single message the sender.
                        notification.setContentTitle(from);
                        // For a single new conversation, the ticker is based on the sender's name.
                        notificationTicker = from;
                    }

                    // The notification content will be the subject of the conversation.
                    notification.setContentText(
                            getSingleMessageLittleText(context, conversation.subject));

                    // The notification subtext will be the subject of the conversation for inbox
                    // notifications, or will based on the the label name for user label
                    // notifications.
                    notification.setSubText(isInbox ? notificationAccount : notificationLabelName);

                    if (multipleUnseenThread) {
                        notification.setLargeIcon(
                                getDefaultNotificationIcon(context, folder, true));
                    }
                    final NotificationCompat.BigTextStyle bigText =
                            new NotificationCompat.BigTextStyle(notification);

                    // Seek the message cursor to the first unread message
                    messageCursor.moveToPosition(firstUnseenMessagePos);
                    final Message message = messageCursor.getMessage();
                    bigText.bigText(getSingleMessageBigText(context,
                            conversation.subject, message));

                    final Set<String> notificationActions =
                            folderPreferences.getNotificationActions();

                    final int notificationId = getNotificationId(notificationAccount, folder);

                    NotificationActionUtils.addNotificationActions(context, notificationIntent,
                            notification, account, conversation, message, folder,
                            notificationId, when, notificationActions);
                } else {
                    // For an old-style notification

                    // The title of a single conversation notification is built from both the sender
                    // and subject of the new message.
                    notification.setContentTitle(getSingleMessageNotificationTitle(context,
                            from, conversation.subject));

                    // The notification content will be the subject of the conversation for inbox
                    // notifications, or will based on the the label name for user label
                    // notifications.
                    notification.setContentText(
                            isInbox ? notificationAccount : notificationLabelName);

                    // For a single new conversation, the ticker is based on the sender's name.
                    notificationTicker = from;
                }
            } finally {
                if (messageCursor != null) {
                    messageCursor.close();
                }
                if (cursor != null) {
                    cursor.close();
                }
            }
        }

        // Build the notification ticker
        if (notificationLabelName != null && notificationTicker != null) {
            // This is a per label notification, format the ticker with that information
            notificationTicker = res.getString(R.string.label_notification_ticker,
                    notificationLabelName, notificationTicker);
        }

        if (notificationTicker != null) {
            // If we didn't generate a notification ticker, it will default to account name
            notification.setTicker(notificationTicker);
        }

        // Set the number in the notification
        if (unreadCount > 1) {
            notification.setNumber(unreadCount);
        }

        notification.setContentIntent(clickIntent);
    }

    private static SpannableStringBuilder getStyledSenders(final Context context,
            final Cursor conversationCursor, final int maxLength, final String account) {
        final Conversation conversation = new Conversation(conversationCursor);
        final com.android.mail.providers.ConversationInfo conversationInfo =
                conversation.conversationInfo;
        final ArrayList<SpannableString> senders = new ArrayList<SpannableString>();
        if (sNotificationUnreadStyleSpan == null) {
            sNotificationUnreadStyleSpan = new TextAppearanceSpan(
                    context, R.style.NotificationSendersUnreadTextAppearance);
            sNotificationReadStyleSpan =
                    new TextAppearanceSpan(context, R.style.NotificationSendersReadTextAppearance);
        }
        SendersView.format(context, conversationInfo, "", maxLength, senders, null, null, account,
                sNotificationUnreadStyleSpan, sNotificationReadStyleSpan, false);

        return ellipsizeStyledSenders(context, senders);
    }

    private static String sSendersSplitToken = null;
    private static String sElidedPaddingToken = null;

    private static SpannableStringBuilder ellipsizeStyledSenders(final Context context,
            ArrayList<SpannableString> styledSenders) {
        if (sSendersSplitToken == null) {
            sSendersSplitToken = context.getString(R.string.senders_split_token);
            sElidedPaddingToken = context.getString(R.string.elided_padding_token);
        }

        SpannableStringBuilder builder = new SpannableStringBuilder();
        SpannableString prevSender = null;
        for (SpannableString sender : styledSenders) {
            if (sender == null) {
                LogUtils.e(LOG_TAG, "null sender while iterating over styledSenders");
                continue;
            }
            CharacterStyle[] spans = sender.getSpans(0, sender.length(), CharacterStyle.class);
            if (SendersView.sElidedString.equals(sender.toString())) {
                prevSender = sender;
                sender = copyStyles(spans, sElidedPaddingToken + sender + sElidedPaddingToken);
            } else if (builder.length() > 0
                    && (prevSender == null || !SendersView.sElidedString.equals(prevSender
                            .toString()))) {
                prevSender = sender;
                sender = copyStyles(spans, sSendersSplitToken + sender);
            } else {
                prevSender = sender;
            }
            builder.append(sender);
        }
        return builder;
    }

    private static SpannableString copyStyles(CharacterStyle[] spans, CharSequence newText) {
        SpannableString s = new SpannableString(newText);
        if (spans != null && spans.length > 0) {
            s.setSpan(spans[0], 0, s.length(), 0);
        }
        return s;
    }

    /**
     * Seeks the cursor to the position of the most recent unread conversation. If no unread
     * conversation is found, the position of the cursor will be restored, and false will be
     * returned.
     */
    private static boolean seekToLatestUnreadConversation(final Cursor cursor) {
        final int initialPosition = cursor.getPosition();
        do {
            final Conversation conversation = new Conversation(cursor);
            if (!conversation.read) {
                return true;
            }
        } while (cursor.moveToNext());

        // Didn't find an unread conversation, reset the position.
        cursor.moveToPosition(initialPosition);
        return false;
    }

    /**
     * Sets the bigtext for a notification for a single new conversation
     *
     * @param context
     * @param senders Sender of the new message that triggered the notification.
     * @param subject Subject of the new message that triggered the notification
     * @param snippet Snippet of the new message that triggered the notification
     * @return a {@link CharSequence} suitable for use in
     *         {@link android.support.v4.app.NotificationCompat.BigTextStyle}
     */
    private static CharSequence getSingleMessageInboxLine(Context context,
            String senders, String subject, String snippet) {
        // TODO(cwren) finish this step toward commmon code with getSingleMessageBigText

        final String subjectSnippet = !TextUtils.isEmpty(subject) ? subject : snippet;

        final TextAppearanceSpan notificationPrimarySpan =
                new TextAppearanceSpan(context, R.style.NotificationPrimaryText);

        if (TextUtils.isEmpty(senders)) {
            // If the senders are empty, just use the subject/snippet.
            return subjectSnippet;
        } else if (TextUtils.isEmpty(subjectSnippet)) {
            // If the subject/snippet is empty, just use the senders.
            final SpannableString spannableString = new SpannableString(senders);
            spannableString.setSpan(notificationPrimarySpan, 0, senders.length(), 0);

            return spannableString;
        } else {
            final String formatString = context.getResources().getString(
                    R.string.multiple_new_message_notification_item);
            final TextAppearanceSpan notificationSecondarySpan =
                    new TextAppearanceSpan(context, R.style.NotificationSecondaryText);

            final String instantiatedString = String.format(formatString, senders, subjectSnippet);

            final SpannableString spannableString = new SpannableString(instantiatedString);

            final boolean isOrderReversed = formatString.indexOf("%2$s") <
                    formatString.indexOf("%1$s");
            final int primaryOffset =
                    (isOrderReversed ? instantiatedString.lastIndexOf(senders) :
                     instantiatedString.indexOf(senders));
            final int secondaryOffset =
                    (isOrderReversed ? instantiatedString.lastIndexOf(subjectSnippet) :
                     instantiatedString.indexOf(subjectSnippet));
            spannableString.setSpan(notificationPrimarySpan,
                    primaryOffset, primaryOffset + senders.length(), 0);
            spannableString.setSpan(notificationSecondarySpan,
                    secondaryOffset, secondaryOffset + subjectSnippet.length(), 0);
            return spannableString;
        }
    }

    /**
     * Sets the bigtext for a notification for a single new conversation
     * @param context
     * @param subject Subject of the new message that triggered the notification
     * @return a {@link CharSequence} suitable for use in {@link Notification.ContentText}
     */
    private static CharSequence getSingleMessageLittleText(Context context, String subject) {
        final TextAppearanceSpan notificationSubjectSpan = new TextAppearanceSpan(
                context, R.style.NotificationPrimaryText);

        final SpannableString spannableString = new SpannableString(subject);
        spannableString.setSpan(notificationSubjectSpan, 0, subject.length(), 0);

        return spannableString;
    }

    /**
     * Sets the bigtext for a notification for a single new conversation
     *
     * @param context
     * @param subject Subject of the new message that triggered the notification
     * @param message the {@link Message} to be displayed.
     * @return a {@link CharSequence} suitable for use in
     *         {@link android.support.v4.app.NotificationCompat.BigTextStyle}
     */
    private static CharSequence getSingleMessageBigText(Context context, String subject,
            final Message message) {

        final TextAppearanceSpan notificationSubjectSpan = new TextAppearanceSpan(
                context, R.style.NotificationPrimaryText);

        final String snippet = getMessageBodyWithoutElidedText(message);

        // Change multiple newlines (with potential white space between), into a single new line
        final String collapsedSnippet =
                !TextUtils.isEmpty(snippet) ? snippet.replaceAll("\\n\\s+", "\n") : "";

        if (TextUtils.isEmpty(subject)) {
            // If the subject is empty, just use the snippet.
            return snippet;
        } else if (TextUtils.isEmpty(collapsedSnippet)) {
            // If the snippet is empty, just use the subject.
            final SpannableString spannableString = new SpannableString(subject);
            spannableString.setSpan(notificationSubjectSpan, 0, subject.length(), 0);

            return spannableString;
        } else {
            final String notificationBigTextFormat = context.getResources().getString(
                    R.string.single_new_message_notification_big_text);

            // Localizers may change the order of the parameters, look at how the format
            // string is structured.
            final boolean isSubjectFirst = notificationBigTextFormat.indexOf("%2$s") >
                    notificationBigTextFormat.indexOf("%1$s");
            final String bigText =
                    String.format(notificationBigTextFormat, subject, collapsedSnippet);
            final SpannableString spannableString = new SpannableString(bigText);

            final int subjectOffset =
                    (isSubjectFirst ? bigText.indexOf(subject) : bigText.lastIndexOf(subject));
            spannableString.setSpan(notificationSubjectSpan,
                    subjectOffset, subjectOffset + subject.length(), 0);

            return spannableString;
        }
    }

    /**
     * Gets the title for a notification for a single new conversation
     * @param context
     * @param sender Sender of the new message that triggered the notification.
     * @param subject Subject of the new message that triggered the notification
     * @return a {@link CharSequence} suitable for use as a {@link Notification} title.
     */
    private static CharSequence getSingleMessageNotificationTitle(Context context,
            String sender, String subject) {

        if (TextUtils.isEmpty(subject)) {
            // If the subject is empty, just set the title to the sender's information.
            return sender;
        } else {
            final String notificationTitleFormat = context.getResources().getString(
                    R.string.single_new_message_notification_title);

            // Localizers may change the order of the parameters, look at how the format
            // string is structured.
            final boolean isSubjectLast = notificationTitleFormat.indexOf("%2$s") >
                    notificationTitleFormat.indexOf("%1$s");
            final String titleString = String.format(notificationTitleFormat, sender, subject);

            // Format the string so the subject is using the secondaryText style
            final SpannableString titleSpannable = new SpannableString(titleString);

            // Find the offset of the subject.
            final int subjectOffset =
                    isSubjectLast ? titleString.lastIndexOf(subject) : titleString.indexOf(subject);
            final TextAppearanceSpan notificationSubjectSpan =
                    new TextAppearanceSpan(context, R.style.NotificationSecondaryText);
            titleSpannable.setSpan(notificationSubjectSpan,
                    subjectOffset, subjectOffset + subject.length(), 0);
            return titleSpannable;
        }
    }

    /**
     * Adds a fragment with given style to a string builder.
     *
     * @param builder the current string builder
     * @param fragment the fragment to be added
     * @param style the style of the fragment
     * @param withSpaces whether to add the whole fragment or to divide it into
     *            smaller ones
     */
    private static void addStyledFragment(SpannableStringBuilder builder, String fragment,
            CharacterStyle style, boolean withSpaces) {
        if (withSpaces) {
            int pos = builder.length();
            builder.append(fragment);
            builder.setSpan(CharacterStyle.wrap(style), pos, builder.length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        } else {
            int start = 0;
            while (true) {
                int pos = fragment.substring(start).indexOf(' ');
                if (pos == -1) {
                    addStyledFragment(builder, fragment.substring(start), style, true);
                    break;
                } else {
                    pos += start;
                    if (start < pos) {
                        addStyledFragment(builder, fragment.substring(start, pos), style, true);
                        builder.append(' ');
                    }
                    start = pos + 1;
                    if (start >= fragment.length()) {
                        break;
                    }
                }
            }
        }
    }

    /**
     * Uses sender instructions to build a formatted string.
     *
     * <p>Sender list instructions contain compact information about the sender list. Most work that
     * can be done without knowing how much room will be availble for the sender list is done when
     * creating the instructions.
     *
     * <p>The instructions string consists of tokens separated by SENDER_LIST_SEPARATOR. Here are
     * the tokens, one per line:<ul>
     * <li><tt>n</tt></li>
     * <li><em>int</em>, the number of non-draft messages in the conversation</li>
     * <li><tt>d</tt</li>
     * <li><em>int</em>, the number of drafts in the conversation</li>
     * <li><tt>l</tt></li>
     * <li><em>literal html to be included in the output</em></li>
     * <li><tt>s</tt> indicates that the message is sending (in the outbox without errors)</li>
     * <li><tt>f</tt> indicates that the message failed to send (in the outbox with errors)</li>
     * <li><em>for each message</em><ul>
     *   <li><em>int</em>, 0 for read, 1 for unread</li>
     *   <li><em>int</em>, the priority of the message. Zero is the most important</li>
     *   <li><em>text</em>, the sender text or blank for messages from 'me'</li>
     * </ul></li>
     * <li><tt>e</tt> to indicate that one or more messages have been elided</li>
     *
     * <p>The instructions indicate how many messages and drafts are in the conversation and then
     * describe the most important messages in order, indicating the priority of each message and
     * whether the message is unread.
     *
     * @param instructions instructions as described above
     * @param senderBuilder the SpannableStringBuilder to append to for sender information
     * @param statusBuilder the SpannableStringBuilder to append to for status
     * @param maxChars the number of characters available to display the text
     * @param unreadStyle the CharacterStyle for unread messages, or null
     * @param draftsStyle the CharacterStyle for draft messages, or null
     * @param sendingString the string to use when there are messages scheduled to be sent
     * @param sendFailedString the string to use when there are messages that mailed to send
     * @param meString the string to use for messages sent by this user
     * @param draftString the string to use for "Draft"
     * @param draftPluralString the string to use for "Drafts"
     * @param showNumMessages false means do not show the message count
     * @param onlyShowUnread true means the only information from unread messages should be included
     */
    public static synchronized void getSenderSnippet(
            String instructions, SpannableStringBuilder senderBuilder,
            SpannableStringBuilder statusBuilder, int maxChars,
            CharacterStyle unreadStyle,
            CharacterStyle readStyle,
            CharacterStyle draftsStyle,
            CharSequence meString, CharSequence draftString, CharSequence draftPluralString,
            CharSequence sendingString, CharSequence sendFailedString,
            boolean forceAllUnread, boolean forceAllRead, boolean allowDraft,
            boolean showNumMessages, boolean onlyShowUnread) {
        assert !(forceAllUnread && forceAllRead);
        boolean unreadStatusIsForced = forceAllUnread || forceAllRead;
        boolean forcedUnreadStatus = forceAllUnread;

        // Measure each fragment. It's ok to iterate over the entire set of fragments because it is
        // never a long list, even if there are many senders.
        final Map<Integer, Integer> priorityToLength = sPriorityToLength;
        priorityToLength.clear();

        int maxFoundPriority = Integer.MIN_VALUE;
        int numMessages = 0;
        int numDrafts = 0;
        CharSequence draftsFragment = "";
        CharSequence sendingFragment = "";
        CharSequence sendFailedFragment = "";

        SENDER_LIST_SPLITTER.setString(instructions);
        int numFragments = 0;
        String[] fragments = sSenderFragments;
        int currentSize = fragments.length;
        while (SENDER_LIST_SPLITTER.hasNext()) {
            fragments[numFragments++] = SENDER_LIST_SPLITTER.next();
            if (numFragments == currentSize) {
                sSenderFragments = new String[2 * currentSize];
                System.arraycopy(fragments, 0, sSenderFragments, 0, currentSize);
                currentSize *= 2;
                fragments = sSenderFragments;
            }
        }

        for (int i = 0; i < numFragments;) {
            String fragment0 = fragments[i++];
            if ("".equals(fragment0)) {
                // This should be the final fragment.
            } else if (Utils.SENDER_LIST_TOKEN_ELIDED.equals(fragment0)) {
                // ignore
            } else if (Utils.SENDER_LIST_TOKEN_NUM_MESSAGES.equals(fragment0)) {
                numMessages = Integer.valueOf(fragments[i++]);
            } else if (Utils.SENDER_LIST_TOKEN_NUM_DRAFTS.equals(fragment0)) {
                String numDraftsString = fragments[i++];
                numDrafts = Integer.parseInt(numDraftsString);
                draftsFragment = numDrafts == 1 ? draftString :
                        draftPluralString + " (" + numDraftsString + ")";
            } else if (Utils.SENDER_LIST_TOKEN_LITERAL.equals(fragment0)) {
                senderBuilder.append(Html.fromHtml(fragments[i++]));
                return;
            } else if (Utils.SENDER_LIST_TOKEN_SENDING.equals(fragment0)) {
                sendingFragment = sendingString;
            } else if (Utils.SENDER_LIST_TOKEN_SEND_FAILED.equals(fragment0)) {
                sendFailedFragment = sendFailedString;
            } else {
                final String unreadString = fragment0;
                final boolean unread = unreadStatusIsForced
                        ? forcedUnreadStatus : Integer.parseInt(unreadString) != 0;
                String priorityString = fragments[i++];
                CharSequence nameString = fragments[i++];
                if (nameString.length() == 0) nameString = meString;
                int priority = Integer.parseInt(priorityString);

                // We want to include this entry if
                //   1) The onlyShowUnread flags is not set
                //   2) The above flag is set, and the message is unread
                if (!onlyShowUnread || unread) {
                    priorityToLength.put(priority, nameString.length());
                    maxFoundPriority = Math.max(maxFoundPriority, priority);
                }
            }
        }
        final String numMessagesFragment =
                (numMessages != 0 && showNumMessages) ?
                " \u00A0" + Integer.toString(numMessages + numDrafts) : "";

        // Don't allocate fixedFragment unless we need it
        SpannableStringBuilder fixedFragment = null;
        int fixedFragmentLength = 0;
        if (draftsFragment.length() != 0 && allowDraft) {
            if (fixedFragment == null) {
                fixedFragment = new SpannableStringBuilder();
            }
            fixedFragment.append(draftsFragment);
            if (draftsStyle != null) {
                fixedFragment.setSpan(
                        CharacterStyle.wrap(draftsStyle),
                        0, fixedFragment.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        if (sendingFragment.length() != 0) {
            if (fixedFragment == null) {
                fixedFragment = new SpannableStringBuilder();
            }
            if (fixedFragment.length() != 0) fixedFragment.append(", ");
            fixedFragment.append(sendingFragment);
        }
        if (sendFailedFragment.length() != 0) {
            if (fixedFragment == null) {
                fixedFragment = new SpannableStringBuilder();
            }
            if (fixedFragment.length() != 0) fixedFragment.append(", ");
            fixedFragment.append(sendFailedFragment);
        }

        if (fixedFragment != null) {
            fixedFragmentLength = fixedFragment.length();
        }
        maxChars -= fixedFragmentLength;

        int maxPriorityToInclude = -1; // inclusive
        int numCharsUsed = numMessagesFragment.length();
        int numSendersUsed = 0;
        while (maxPriorityToInclude < maxFoundPriority) {
            if (priorityToLength.containsKey(maxPriorityToInclude + 1)) {
                int length = numCharsUsed + priorityToLength.get(maxPriorityToInclude + 1);
                if (numCharsUsed > 0) length += 2;
                // We must show at least two senders if they exist. If we don't have space for both
                // then we will truncate names.
                if (length > maxChars && numSendersUsed >= 2) {
                    break;
                }
                numCharsUsed = length;
                numSendersUsed++;
            }
            maxPriorityToInclude++;
        }

        int numCharsToRemovePerWord = 0;
        if (numCharsUsed > maxChars) {
            numCharsToRemovePerWord = (numCharsUsed - maxChars) / numSendersUsed;
        }

        String lastFragment = null;
        CharacterStyle lastStyle = null;
        for (int i = 0; i < numFragments;) {
            String fragment0 = fragments[i++];
            if ("".equals(fragment0)) {
                // This should be the final fragment.
            } else if (Utils.SENDER_LIST_TOKEN_ELIDED.equals(fragment0)) {
                if (lastFragment != null) {
                    addStyledFragment(senderBuilder, lastFragment, lastStyle, false);
                    senderBuilder.append(" ");
                    addStyledFragment(senderBuilder, "..", lastStyle, true);
                    senderBuilder.append(" ");
                }
                lastFragment = null;
            } else if (Utils.SENDER_LIST_TOKEN_NUM_MESSAGES.equals(fragment0)) {
                i++;
            } else if (Utils.SENDER_LIST_TOKEN_NUM_DRAFTS.equals(fragment0)) {
                i++;
            } else if (Utils.SENDER_LIST_TOKEN_SENDING.equals(fragment0)) {
            } else if (Utils.SENDER_LIST_TOKEN_SEND_FAILED.equals(fragment0)) {
            } else {
                final String unreadString = fragment0;
                final String priorityString = fragments[i++];
                String nameString = fragments[i++];
                final boolean unread = unreadStatusIsForced
                        ? forcedUnreadStatus : Integer.parseInt(unreadString) != 0;

                // We want to include this entry if
                //   1) The onlyShowUnread flags is not set
                //   2) The above flag is set, and the message is unread
                if (!onlyShowUnread || unread) {
                    if (nameString.length() == 0) {
                        nameString = meString.toString();
                    } else {
                        nameString = Html.fromHtml(nameString).toString();
                    }
                    if (numCharsToRemovePerWord != 0) {
                        nameString = nameString.substring(
                                0, Math.max(nameString.length() - numCharsToRemovePerWord, 0));
                    }
                    final int priority = Integer.parseInt(priorityString);
                    if (priority <= maxPriorityToInclude) {
                        if (lastFragment != null && !lastFragment.equals(nameString)) {
                            addStyledFragment(
                                    senderBuilder, lastFragment.concat(","), lastStyle, false);
                            senderBuilder.append(" ");
                        }
                        lastFragment = nameString;
                        lastStyle = unread ? unreadStyle : readStyle;
                    } else {
                        if (lastFragment != null) {
                            addStyledFragment(senderBuilder, lastFragment, lastStyle, false);
                            // Adjacent spans can cause the TextView in Gmail widget
                            // confused and leads to weird behavior on scrolling.
                            // Our workaround here is to separate the spans by
                            // spaces.
                            senderBuilder.append(" ");
                            addStyledFragment(senderBuilder, "..", lastStyle, true);
                            senderBuilder.append(" ");
                        }
                        lastFragment = null;
                    }
                }
            }
        }
        if (lastFragment != null) {
            addStyledFragment(senderBuilder, lastFragment, lastStyle, false);
        }
        senderBuilder.append(numMessagesFragment);
        if (fixedFragmentLength != 0) {
            statusBuilder.append(fixedFragment);
        }
    }

    /**
     * Clears the notifications for the specified account/folder/conversation.
     */
    public static void clearFolderNotification(Context context, Account account, Folder folder) {
        LogUtils.v(LOG_TAG, "Clearing all notifications for %s/%s", account.name, folder.name);
        final NotificationMap notificationMap = getNotificationMap(context);
        final NotificationKey key = new NotificationKey(account, folder);
        notificationMap.remove(key);
        notificationMap.saveNotificationMap(context);

        markSeen(context, folder);
    }

    private static ArrayList<Long> findContacts(Context context, Collection<String> addresses) {
        ArrayList<String> whereArgs = new ArrayList<String>();
        StringBuilder whereBuilder = new StringBuilder();
        String[] questionMarks = new String[addresses.size()];

        whereArgs.addAll(addresses);
        Arrays.fill(questionMarks, "?");
        whereBuilder.append(Email.DATA1 + " IN (").
                append(TextUtils.join(",", questionMarks)).
                append(")");

        ContentResolver resolver = context.getContentResolver();
        Cursor c = resolver.query(Email.CONTENT_URI,
                new String[]{Email.CONTACT_ID}, whereBuilder.toString(),
                whereArgs.toArray(new String[0]), null);

        ArrayList<Long> contactIds = new ArrayList<Long>();
        if (c == null) {
            return contactIds;
        }
        try {
            while (c.moveToNext()) {
                contactIds.add(c.getLong(0));
            }
        } finally {
            c.close();
        }
        return contactIds;
    }

    private static Bitmap getContactIcon(
            Context context, String senderAddress, final Folder folder) {
        if (senderAddress == null) {
            return null;
        }
        Bitmap icon = null;
        ArrayList<Long> contactIds = findContacts(
                context, Arrays.asList(new String[] { senderAddress }));

        if (contactIds != null) {
            // Get the ideal size for this icon.
            final Resources res = context.getResources();
            final int idealIconHeight =
                    res.getDimensionPixelSize(android.R.dimen.notification_large_icon_height);
            final int idealIconWidth =
                    res.getDimensionPixelSize(android.R.dimen.notification_large_icon_width);
            for (long id : contactIds) {
                final Uri contactUri =
                        ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, id);
                final Uri photoUri = Uri.withAppendedPath(contactUri, Photo.CONTENT_DIRECTORY);
                final Cursor cursor = context.getContentResolver().query(
                        photoUri, new String[] { Photo.PHOTO }, null, null, null);

                if (cursor != null) {
                    try {
                        if (cursor.moveToFirst()) {
                            byte[] data = cursor.getBlob(0);
                            if (data != null) {
                                icon = BitmapFactory.decodeStream(new ByteArrayInputStream(data));
                                if (icon != null && icon.getHeight() < idealIconHeight) {
                                    // We should scale this image to fit the intended size
                                    icon = Bitmap.createScaledBitmap(
                                            icon, idealIconWidth, idealIconHeight, true);
                                }
                                if (icon != null) {
                                    break;
                                }
                            }
                        }
                    } finally {
                        cursor.close();
                    }
                }
            }
        }
        if (icon == null) {
            // icon should be the default gmail icon.
            icon = getDefaultNotificationIcon(context, folder, false /* single new message */);
        }
        return icon;
    }

    private static String getMessageBodyWithoutElidedText(final Message message) {
        return getMessageBodyWithoutElidedText(message.getBodyAsHtml());
    }

    public static String getMessageBodyWithoutElidedText(String html) {
        if (TextUtils.isEmpty(html)) {
            return "";
        }
        // Get the html "tree" for this message body
        final HtmlTree htmlTree = com.android.mail.utils.Utils.getHtmlTree(html);
        htmlTree.setPlainTextConverterFactory(MESSAGE_CONVERTER_FACTORY);

        return htmlTree.getPlainText();
    }

    public static void markSeen(final Context context, final Folder folder) {
        final Uri uri = folder.uri;

        final ContentValues values = new ContentValues(1);
        values.put(UIProvider.ConversationColumns.SEEN, 1);

        context.getContentResolver().update(uri, values, null, null);
    }

    /**
     * Returns a displayable string representing
     * the message sender. It has a preference toward showing the name,
     * but will fall back to the address if that is all that is available.
     */
    private static String getDisplayableSender(String sender) {
        final EmailAddress address = EmailAddress.getEmailAddress(sender);

        String displayableSender = address.getName();
        // If that fails, default to the sender address.
        if (TextUtils.isEmpty(displayableSender)) {
            displayableSender = address.getAddress();
        }
        // If we were unable to tokenize a name or address,
        // just use whatever was in the sender.
        if (TextUtils.isEmpty(displayableSender)) {
            displayableSender = sender;
        }
        return displayableSender;
    }

    /**
     * Returns only the address portion of a message sender.
     */
    private static String getSenderAddress(String sender) {
        final EmailAddress address = EmailAddress.getEmailAddress(sender);

        String tokenizedAddress = address.getAddress();

        // If we were unable to tokenize a name or address,
        // just use whatever was in the sender.
        if (TextUtils.isEmpty(tokenizedAddress)) {
            tokenizedAddress = sender;
        }
        return tokenizedAddress;
    }

    public static int getNotificationId(final String account, final Folder folder) {
        return 1 ^ account.hashCode() ^ folder.hashCode();
    }

    private static class NotificationKey {
        public final Account account;
        public final Folder folder;

        public NotificationKey(Account account, Folder folder) {
            this.account = account;
            this.folder = folder;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof NotificationKey)) {
                return false;
            }
            NotificationKey key = (NotificationKey) other;
            return account.equals(key.account) && folder.equals(key.folder);
        }

        @Override
        public String toString() {
            return account.toString() + " " + folder.name;
        }

        @Override
        public int hashCode() {
            final int accountHashCode = account.hashCode();
            final int folderHashCode = folder.hashCode();
            return accountHashCode ^ folderHashCode;
        }
    }

    /**
     * Contains the logic for converting the contents of one HtmlTree into
     * plaintext.
     */
    public static class MailMessagePlainTextConverter extends HtmlTree.DefaultPlainTextConverter {
        // Strings for parsing html message bodies
        private static final String ELIDED_TEXT_ELEMENT_NAME = "div";
        private static final String ELIDED_TEXT_ELEMENT_ATTRIBUTE_NAME = "class";
        private static final String ELIDED_TEXT_ELEMENT_ATTRIBUTE_CLASS_VALUE = "elided-text";

        private static final HTML.Attribute ELIDED_TEXT_ATTRIBUTE =
                new HTML.Attribute(ELIDED_TEXT_ELEMENT_ATTRIBUTE_NAME, HTML.Attribute.NO_TYPE);

        private static final HtmlDocument.Node ELIDED_TEXT_REPLACEMENT_NODE =
                HtmlDocument.createSelfTerminatingTag(HTML4.BR_ELEMENT, null, null, null);

        private int mEndNodeElidedTextBlock = -1;

        @Override
        public void addNode(HtmlDocument.Node n, int nodeNum, int endNum) {
            // If we are in the middle of an elided text block, don't add this node
            if (nodeNum < mEndNodeElidedTextBlock) {
                return;
            } else if (nodeNum == mEndNodeElidedTextBlock) {
                super.addNode(ELIDED_TEXT_REPLACEMENT_NODE, nodeNum, endNum);
                return;
            }

            // If this tag starts another elided text block, we want to remember the end
            if (n instanceof HtmlDocument.Tag) {
                boolean foundElidedTextTag = false;
                final HtmlDocument.Tag htmlTag = (HtmlDocument.Tag)n;
                final HTML.Element htmlElement = htmlTag.getElement();
                if (ELIDED_TEXT_ELEMENT_NAME.equals(htmlElement.getName())) {
                    // Make sure that the class is what is expected
                    final List<HtmlDocument.TagAttribute> attributes =
                            htmlTag.getAttributes(ELIDED_TEXT_ATTRIBUTE);
                    for (HtmlDocument.TagAttribute attribute : attributes) {
                        if (ELIDED_TEXT_ELEMENT_ATTRIBUTE_CLASS_VALUE.equals(
                                attribute.getValue())) {
                            // Found an "elided-text" div.  Remember information about this tag
                            mEndNodeElidedTextBlock = endNum;
                            foundElidedTextTag = true;
                            break;
                        }
                    }
                }

                if (foundElidedTextTag) {
                    return;
                }
            }

            super.addNode(n, nodeNum, endNum);
        }
    }

    /**
     * During account setup in Email, we may not have an inbox yet, so the notification setting had
     * to be stored in {@link AccountPreferences}. If it is still there, we need to move it to the
     * {@link FolderPreferences} now.
     */
    public static void moveNotificationSetting(final AccountPreferences accountPreferences,
            final FolderPreferences folderPreferences) {
        if (accountPreferences.isDefaultInboxNotificationsEnabledSet()) {
            // If this setting has been changed some other way, don't overwrite it
            if (!folderPreferences.isNotificationsEnabledSet()) {
                final boolean notificationsEnabled =
                        accountPreferences.getDefaultInboxNotificationsEnabled();

                folderPreferences.setNotificationsEnabled(notificationsEnabled);
            }

            accountPreferences.clearDefaultInboxNotificationsEnabled();
        }
    }
}
