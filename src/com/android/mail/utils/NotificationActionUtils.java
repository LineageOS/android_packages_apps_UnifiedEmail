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
package com.android.mail.utils;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.SystemClock;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.support.v4.util.SparseArrayCompat;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.widget.RemoteViews;

import com.android.mail.MailIntentService;
import com.android.mail.NotificationActionIntentService;
import com.android.mail.R;
import com.android.mail.compose.ComposeActivity;
import com.android.mail.providers.Account;
import com.android.mail.providers.Conversation;
import com.android.mail.providers.Folder;
import com.android.mail.providers.FolderList;
import com.android.mail.providers.Message;
import com.android.mail.providers.UIProvider;
import com.android.mail.providers.UIProvider.FolderType;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class NotificationActionUtils {
    private static long sUndoTimeoutMillis = -1;

    /**
     * If an {@link NotificationAction} exists here for a given notification key, then we should
     * display this undo notification rather than an email notification.
     */
    public static final SparseArrayCompat<NotificationAction> sUndoNotifications =
            new SparseArrayCompat<NotificationAction>();

    /**
     * If an undo notification is displayed, its timestamp
     * ({@link android.app.Notification.Builder#setWhen(long)}) is stored here so we can use it for
     * the original notification if the action is undone.
     */
    public static final SparseLongArray sNotificationTimestamps = new SparseLongArray();

    public enum NotificationActionType {
        ARCHIVE_REMOVE_LABEL("archive", R.drawable.ic_menu_archive_holo_dark,
                R.drawable.ic_menu_remove_label_holo_dark, R.string.notification_action_archive,
                R.string.notification_action_remove_label, new ActionToggler() {
            @Override
            public boolean shouldDisplayPrimary(final Folder folder,
                    final Conversation conversation, final Message message) {
                return folder == null || folder.type == FolderType.INBOX;
            }
        }),
        DELETE("delete", R.drawable.ic_menu_delete_holo_dark, R.string.notification_action_delete),
        MARK_READ("mark_read", R.drawable.ic_menu_mark_read_holo_dark,
                R.string.notification_action_mark_read),
        REPLY("reply", R.drawable.ic_reply_holo_dark, R.string.notification_action_reply),
        REPLY_ALL("reply_all", R.drawable.ic_reply_all_holo_dark,
                R.string.notification_action_reply_all),
        FORWARD("forward", R.drawable.ic_forward_holo_dark, R.string.notification_action_forward);

        private final String mPersistedValue;

        private final int mActionIcon;
        private final int mActionIcon2;

        private final int mDisplayString;
        private final int mDisplayString2;

        private final ActionToggler mActionToggler;

        private static final Map<String, NotificationActionType> sPersistedMapping;

        private interface ActionToggler {
            /**
             * Determines if we should display the primary or secondary text/icon.
             *
             * @return <code>true</code> to display primary, <code>false</code> to display secondary
             */
            boolean shouldDisplayPrimary(Folder folder, Conversation conversation, Message message);
        }

        static {
            final NotificationActionType[] values = values();
            final ImmutableMap.Builder<String, NotificationActionType> mapBuilder =
                    new ImmutableMap.Builder<String, NotificationActionType>();

            for (int i = 0; i < values.length; i++) {
                mapBuilder.put(values[i].getPersistedValue(), values[i]);
            }

            sPersistedMapping = mapBuilder.build();
        }

        private NotificationActionType(final String persistedValue, final int actionIcon,
                final int displayString) {
            mPersistedValue = persistedValue;
            mActionIcon = actionIcon;
            mActionIcon2 = -1;
            mDisplayString = displayString;
            mDisplayString2 = -1;
            mActionToggler = null;
        }

        private NotificationActionType(final String persistedValue, final int actionIcon,
                final int actionIcon2, final int displayString, final int displayString2,
                final ActionToggler actionToggler) {
            mPersistedValue = persistedValue;
            mActionIcon = actionIcon;
            mActionIcon2 = actionIcon2;
            mDisplayString = displayString;
            mDisplayString2 = displayString2;
            mActionToggler = actionToggler;
        }

        public static NotificationActionType getActionType(final String persistedValue) {
            return sPersistedMapping.get(persistedValue);
        }

        public String getPersistedValue() {
            return mPersistedValue;
        }

        public int getActionIconResId(final Folder folder, final Conversation conversation,
                final Message message) {
            if (mActionToggler == null || mActionToggler.shouldDisplayPrimary(folder, conversation,
                    message)) {
                return mActionIcon;
            }

            return mActionIcon2;
        }

        public int getDisplayStringResId(final Folder folder, final Conversation conversation,
                final Message message) {
            if (mActionToggler == null || mActionToggler.shouldDisplayPrimary(folder, conversation,
                    message)) {
                return mDisplayString;
            }

            return mDisplayString2;
        }
    }

    /**
     * Adds the appropriate notification actions to the specified
     * {@link android.app.Notification.Builder}
     *
     * @param notificationIntent The {@link Intent} used when the notification is clicked
     * @param when The value passed into {@link android.app.Notification.Builder#setWhen(long)}.
     *        This is used for maintaining notification ordering with the undo bar
     * @param notificationActionsString A comma-delimited {@link String} of the actions to display
     */
    public static void addNotificationActions(final Context context,
            final Intent notificationIntent, final Notification.Builder notification,
            final Account account, final Conversation conversation, final Message message,
            final Folder folder, final int notificationId, final long when,
            final String notificationActionsString) {
        final String[] notificationActionsValueArray =
                TextUtils.isEmpty(notificationActionsString) ? new String[0]
                        : notificationActionsString.split(",");
        final List<NotificationActionType> notificationActions =
                new ArrayList<NotificationActionType>(notificationActionsValueArray.length);
        for (int i = 0; i < notificationActionsValueArray.length; i++) {
            notificationActions.add(
                    NotificationActionType.getActionType(notificationActionsValueArray[i]));
        }

        sortNotificationActions(folder, notificationActions);

        for (final NotificationActionType notificationAction : notificationActions) {
            notification.addAction(notificationAction.getActionIconResId(
                    folder, conversation, message), context.getString(notificationAction
                    .getDisplayStringResId(folder, conversation, message)),
                    getNotificationActionPendingIntent(context, account, conversation, message,
                            folder, notificationIntent, notificationAction, notificationId, when));
        }
    }

    /**
     * Sorts the notification actions into the appropriate order, based on current label
     *
     * @param folder The {@link Folder} being notified
     * @param notificationActions The actions to sort
     */
    private static void sortNotificationActions(
            final Folder folder, final List<NotificationActionType> notificationActions) {
        final Set<NotificationActionType> tempActions =
                new HashSet<NotificationActionType>(notificationActions);
        notificationActions.clear();

        if (folder.type == FolderType.INBOX) {
            // Inbox
            /*
             * Action 1: Archive, Delete, Mute, Mark read, Add star, Mark important, Reply, Reply
             * all, Forward
             */
            /*
             * Action 2: Reply, Reply all, Forward, Mark important, Add star, Mark read, Mute,
             * Delete, Archive
             */
            if (tempActions.contains(NotificationActionType.ARCHIVE_REMOVE_LABEL)) {
                notificationActions.add(NotificationActionType.ARCHIVE_REMOVE_LABEL);
            }
            if (tempActions.contains(NotificationActionType.DELETE)) {
                notificationActions.add(NotificationActionType.DELETE);
            }
            if (tempActions.contains(NotificationActionType.MARK_READ)) {
                notificationActions.add(NotificationActionType.MARK_READ);
            }
            if (tempActions.contains(NotificationActionType.REPLY)) {
                notificationActions.add(NotificationActionType.REPLY);
            }
            if (tempActions.contains(NotificationActionType.REPLY_ALL)) {
                notificationActions.add(NotificationActionType.REPLY_ALL);
            }
            if (tempActions.contains(NotificationActionType.FORWARD)) {
                notificationActions.add(NotificationActionType.FORWARD);
            }
        } else if (folder.isProviderFolder()) {
            // Gmail system labels
            /*
             * Action 1: Delete, Mute, Mark read, Add star, Mark important, Reply, Reply all,
             * Forward
             */
            /*
             * Action 2: Reply, Reply all, Forward, Mark important, Add star, Mark read, Mute,
             * Delete
             */
            if (tempActions.contains(NotificationActionType.DELETE)) {
                notificationActions.add(NotificationActionType.DELETE);
            }
            if (tempActions.contains(NotificationActionType.MARK_READ)) {
                notificationActions.add(NotificationActionType.MARK_READ);
            }
            if (tempActions.contains(NotificationActionType.REPLY)) {
                notificationActions.add(NotificationActionType.REPLY);
            }
            if (tempActions.contains(NotificationActionType.REPLY_ALL)) {
                notificationActions.add(NotificationActionType.REPLY_ALL);
            }
            if (tempActions.contains(NotificationActionType.FORWARD)) {
                notificationActions.add(NotificationActionType.FORWARD);
            }
        } else {
            // Gmail user created labels
            /*
             * Action 1: Remove label, Delete, Mark read, Add star, Mark important, Reply, Reply
             * all, Forward
             */
            /*
             * Action 2: Reply, Reply all, Forward, Mark important, Add star, Mark read, Delete
             */
            if (tempActions.contains(NotificationActionType.ARCHIVE_REMOVE_LABEL)) {
                notificationActions.add(NotificationActionType.ARCHIVE_REMOVE_LABEL);
            }
            if (tempActions.contains(NotificationActionType.DELETE)) {
                notificationActions.add(NotificationActionType.DELETE);
            }
            if (tempActions.contains(NotificationActionType.MARK_READ)) {
                notificationActions.add(NotificationActionType.MARK_READ);
            }
            if (tempActions.contains(NotificationActionType.REPLY)) {
                notificationActions.add(NotificationActionType.REPLY);
            }
            if (tempActions.contains(NotificationActionType.REPLY_ALL)) {
                notificationActions.add(NotificationActionType.REPLY_ALL);
            }
            if (tempActions.contains(NotificationActionType.FORWARD)) {
                notificationActions.add(NotificationActionType.FORWARD);
            }
        }
    }

    /**
     * Creates a {@link PendingIntent} for the specified notification action.
     */
    private static PendingIntent getNotificationActionPendingIntent(final Context context,
            final Account account, final Conversation conversation, final Message message,
            final Folder folder, final Intent notificationIntent,
            final NotificationActionType action, final int notificationId, final long when) {
        final Uri messageUri = message.uri;

        final NotificationAction notificationAction = new NotificationAction(action, account,
                conversation, message, folder, conversation.id, message.serverId, message.id, when);

        switch (action) {
            case REPLY: {
                // Build a task stack that forces the conversation view on the stack before the
                // reply activity.
                final TaskStackBuilder taskStackBuilder = TaskStackBuilder.create(context);

                final Intent replyIntent = createReplyIntent(context, account, messageUri, false);
                // To make sure that the reply intents one notification don't clobber over
                // intents for other notification, force a data uri on the intent
                final Uri notificationUri =
                        Uri.parse("gmailfrom://gmail-ls/account/" + "reply/" + notificationId);
                replyIntent.setData(notificationUri);

                taskStackBuilder.addNextIntent(notificationIntent).addNextIntent(replyIntent);

                final PendingIntent pendingIntent = taskStackBuilder.getPendingIntent(
                        notificationId, PendingIntent.FLAG_UPDATE_CURRENT);

                final String intentAction = NotificationActionIntentService.ACTION_REPLY;

                final Intent intent = new Intent(intentAction);
                intent.putExtra(NotificationActionIntentService.EXTRA_NOTIFICATION_ACTION,
                        notificationAction);
                intent.putExtra(NotificationActionIntentService.EXTRA_NOTIFICATION_PENDING_INTENT,
                        pendingIntent);

                return PendingIntent.getService(
                        context, notificationId, intent, PendingIntent.FLAG_UPDATE_CURRENT);
            } case REPLY_ALL: {
                // Build a task stack that forces the conversation view on the stack before the
                // reply activity.
                final TaskStackBuilder taskStackBuilder = TaskStackBuilder.create(context);

                final Intent replyIntent = createReplyIntent(context, account, messageUri, true);
                // To make sure that the reply intents one notification don't clobber over
                // intents for other notification, force a data uri on the intent
                final Uri notificationUri =
                        Uri.parse("gmailfrom://gmail-ls/account/" + "replyall/" + notificationId);
                replyIntent.setData(notificationUri);

                taskStackBuilder.addNextIntent(notificationIntent).addNextIntent(replyIntent);

                final PendingIntent pendingIntent = taskStackBuilder.getPendingIntent(
                        notificationId, PendingIntent.FLAG_UPDATE_CURRENT);

                final String intentAction = NotificationActionIntentService.ACTION_REPLY_ALL;

                final Intent intent = new Intent(intentAction);
                intent.putExtra(NotificationActionIntentService.EXTRA_NOTIFICATION_ACTION,
                        notificationAction);
                intent.putExtra(NotificationActionIntentService.EXTRA_NOTIFICATION_PENDING_INTENT,
                        pendingIntent);

                return PendingIntent.getService(
                        context, notificationId, intent, PendingIntent.FLAG_UPDATE_CURRENT);
            } case FORWARD: {
                // Build a task stack that forces the conversation view on the stack before the
                // reply activity.
                final TaskStackBuilder taskStackBuilder = TaskStackBuilder.create(context);

                final Intent replyIntent = createForwardIntent(context, account, messageUri);
                // To make sure that the reply intents one notification don't clobber over
                // intents for other notification, force a data uri on the intent
                final Uri notificationUri =
                        Uri.parse("gmailfrom://gmail-ls/account/" + "forward/" + notificationId);
                replyIntent.setData(notificationUri);

                taskStackBuilder.addNextIntent(notificationIntent).addNextIntent(replyIntent);

                final PendingIntent pendingIntent = taskStackBuilder.getPendingIntent(
                        notificationId, PendingIntent.FLAG_UPDATE_CURRENT);

                final String intentAction = NotificationActionIntentService.ACTION_FORWARD;

                final Intent intent = new Intent(intentAction);
                intent.putExtra(NotificationActionIntentService.EXTRA_NOTIFICATION_ACTION,
                        notificationAction);
                intent.putExtra(NotificationActionIntentService.EXTRA_NOTIFICATION_PENDING_INTENT,
                        pendingIntent);

                return PendingIntent.getService(
                        context, notificationId, intent, PendingIntent.FLAG_UPDATE_CURRENT);
            } case ARCHIVE_REMOVE_LABEL: {
                final String intentAction =
                        NotificationActionIntentService.ACTION_ARCHIVE_REMOVE_LABEL;

                final Intent intent = new Intent(intentAction);
                intent.putExtra(NotificationActionIntentService.EXTRA_NOTIFICATION_ACTION,
                        notificationAction);

                return PendingIntent.getService(
                        context, notificationId, intent, PendingIntent.FLAG_UPDATE_CURRENT);
            } case DELETE: {
                final String intentAction = NotificationActionIntentService.ACTION_DELETE;

                final Intent intent = new Intent(intentAction);
                intent.putExtra(NotificationActionIntentService.EXTRA_NOTIFICATION_ACTION,
                        notificationAction);

                return PendingIntent.getService(
                        context, notificationId, intent, PendingIntent.FLAG_UPDATE_CURRENT);
            } case MARK_READ: {
                final String intentAction = NotificationActionIntentService.ACTION_MARK_READ;

                final Intent intent = new Intent(intentAction);
                intent.putExtra(NotificationActionIntentService.EXTRA_NOTIFICATION_ACTION,
                        notificationAction);

                return PendingIntent.getService(
                        context, notificationId, intent, PendingIntent.FLAG_UPDATE_CURRENT);
            }
        }

        throw new IllegalArgumentException("Invalid NotificationActionType");
    }

    /**
     * @return an intent which, if launched, will reply to the conversation
     */
    public static Intent createReplyIntent(final Context context, final Account account,
            final Uri messageUri, final boolean isReplyAll) {
        final Intent intent = ComposeActivity.createReplyIntent(context, account, messageUri,
                isReplyAll);
        return intent;
    }

    /**
     * @return an intent which, if launched, will forward the conversation
     */
    public static Intent createForwardIntent(
            final Context context, final Account account, final Uri messageUri) {
        final Intent intent = ComposeActivity.createForwardIntent(context, account, messageUri);
        return intent;
    }

    public static class NotificationAction implements Parcelable {
        private final NotificationActionType mNotificationActionType;
        private final Account mAccount;
        private final Conversation mConversation;
        private final Message mMessage;
        private final Folder mFolder;
        private final long mConversationId;
        private final String mMessageId;
        private final long mLocalMessageId;
        private final long mWhen;

        public NotificationAction(final NotificationActionType notificationActionType,
                final Account account, final Conversation conversation, final Message message,
                final Folder folder, final long conversationId, final String messageId,
                final long localMessageId, final long when) {
            mNotificationActionType = notificationActionType;
            mAccount = account;
            mConversation = conversation;
            mMessage = message;
            mFolder = folder;
            mConversationId = conversationId;
            mMessageId = messageId;
            mLocalMessageId = localMessageId;
            mWhen = when;
        }

        public NotificationActionType getNotificationActionType() {
            return mNotificationActionType;
        }

        public Account getAccount() {
            return mAccount;
        }

        public Conversation getConversation() {
            return mConversation;
        }

        public Message getMessage() {
            return mMessage;
        }

        public Folder getFolder() {
            return mFolder;
        }

        public long getConversationId() {
            return mConversationId;
        }

        public String getMessageId() {
            return mMessageId;
        }

        public long getLocalMessageId() {
            return mLocalMessageId;
        }

        public long getWhen() {
            return mWhen;
        }

        public int getActionTextResId() {
            switch (mNotificationActionType) {
                case ARCHIVE_REMOVE_LABEL:
                    if (mFolder.type == FolderType.INBOX) {
                        return R.string.notification_action_undo_archive;
                    } else {
                        return R.string.notification_action_undo_remove_label;
                    }
                case DELETE:
                    return R.string.notification_action_undo_delete;
                default:
                    throw new IllegalStateException(
                            "There is no action text for this NotificationActionType.");
            }
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(final Parcel out, final int flags) {
            out.writeInt(mNotificationActionType.ordinal());
            out.writeParcelable(mAccount, 0);
            out.writeParcelable(mConversation, 0);
            out.writeParcelable(mMessage, 0);
            out.writeParcelable(mFolder, 0);
            out.writeLong(mConversationId);
            out.writeString(mMessageId);
            out.writeLong(mLocalMessageId);
            out.writeLong(mWhen);
        }

        public static final Parcelable.ClassLoaderCreator<NotificationAction> CREATOR =
                new Parcelable.ClassLoaderCreator<NotificationAction>() {
                    @Override
                    public NotificationAction createFromParcel(final Parcel in) {
                        return new NotificationAction(in, null);
                    }

                    @Override
                    public NotificationAction[] newArray(final int size) {
                        return new NotificationAction[size];
                    }

                    @Override
                    public NotificationAction createFromParcel(
                            final Parcel in, final ClassLoader loader) {
                        return new NotificationAction(in, loader);
                    }
                };

        private NotificationAction(final Parcel in, final ClassLoader loader) {
            mNotificationActionType = NotificationActionType.values()[in.readInt()];
            mAccount = in.readParcelable(loader);
            mConversation = in.readParcelable(loader);
            mMessage = in.readParcelable(loader);
            mFolder = in.readParcelable(loader);
            mConversationId = in.readLong();
            mMessageId = in.readString();
            mLocalMessageId = in.readLong();
            mWhen = in.readLong();
        }
    }

    public static Notification createUndoNotification(final Context context,
            final NotificationAction notificationAction, final int notificationId) {
        final NotificationCompat.Builder builder = new NotificationCompat.Builder(context);

        builder.setSmallIcon(R.drawable.stat_notify_email);
        builder.setWhen(notificationAction.getWhen());

        final RemoteViews undoView =
                new RemoteViews(context.getPackageName(), R.layout.undo_notification);
        undoView.setTextViewText(
                R.id.description_text, context.getString(notificationAction.getActionTextResId()));

        final Intent clickIntent = new Intent(NotificationActionIntentService.ACTION_UNDO);
        clickIntent.putExtra(NotificationActionIntentService.EXTRA_NOTIFICATION_ACTION,
                notificationAction);
        final PendingIntent clickPendingIntent = PendingIntent.getService(context, notificationId,
                clickIntent, PendingIntent.FLAG_CANCEL_CURRENT);

        undoView.setOnClickPendingIntent(R.id.status_bar_latest_event_content, clickPendingIntent);

        builder.setContent(undoView);

        // When the notification is cleared, we perform the destructive action
        final Intent deleteIntent = new Intent(NotificationActionIntentService.ACTION_DESTRUCT);
        deleteIntent.putExtra(NotificationActionIntentService.EXTRA_NOTIFICATION_ACTION,
                notificationAction);
        final PendingIntent deletePendingIntent = PendingIntent.getService(context,
                notificationId, deleteIntent, PendingIntent.FLAG_CANCEL_CURRENT);
        builder.setDeleteIntent(deletePendingIntent);

        final Notification notification = builder.build();

        return notification;
    }

    /**
     * Registers a timeout for the undo notification such that when it expires, the undo bar will
     * disappear, and the action will be performed.
     */
    public static void registerUndoTimeout(
            final Context context, final NotificationAction notificationAction) {
        if (sUndoTimeoutMillis == -1) {
            sUndoTimeoutMillis =
                    context.getResources().getInteger(R.integer.undo_notification_timeout);
        }

        final AlarmManager alarmManager =
                (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        final long triggerAtMills = SystemClock.elapsedRealtime() + sUndoTimeoutMillis;

        final PendingIntent pendingIntent =
                createUndoTimeoutPendingIntent(context, notificationAction);

        alarmManager.set(AlarmManager.ELAPSED_REALTIME, triggerAtMills, pendingIntent);
    }

    /**
     * Cancels the undo timeout for a notification action. This should be called if the undo
     * notification is clicked (to prevent the action from being performed anyway) or cleared (since
     * we have already performed the action).
     */
    public static void cancelUndoTimeout(
            final Context context, final NotificationAction notificationAction) {
        final AlarmManager alarmManager =
                (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        final PendingIntent pendingIntent =
                createUndoTimeoutPendingIntent(context, notificationAction);

        alarmManager.cancel(pendingIntent);
    }

    /**
     * Creates a {@link PendingIntent} to be used for creating and canceling the undo timeout
     * alarm.
     */
    private static PendingIntent createUndoTimeoutPendingIntent(
            final Context context, final NotificationAction notificationAction) {
        final Intent intent = new Intent(NotificationActionIntentService.ACTION_UNDO_TIMEOUT);
        intent.putExtra(
                NotificationActionIntentService.EXTRA_NOTIFICATION_ACTION, notificationAction);

        final int requestCode = notificationAction.getAccount().hashCode()
                ^ notificationAction.getFolder().hashCode();
        final PendingIntent pendingIntent =
                PendingIntent.getService(context, requestCode, intent, 0);

        return pendingIntent;
    }

    /**
     * Processes the specified destructive action (archive, delete, mute) on the message.
     */
    public static void processDestructiveAction(
            final Context context, final NotificationAction notificationAction) {
        final NotificationActionType destructAction =
                notificationAction.getNotificationActionType();
        final Conversation conversation = notificationAction.getConversation();
        final Folder folder = notificationAction.getFolder();

        final ContentResolver contentResolver = context.getContentResolver();
        final Uri uri = conversation.uri;

        switch (destructAction) {
            case ARCHIVE_REMOVE_LABEL: {
                if (folder.type == FolderType.INBOX) {
                    // Inbox, so archive
                    final ContentValues values = new ContentValues(1);
                    values.put(UIProvider.ConversationOperations.OPERATION_KEY,
                            UIProvider.ConversationOperations.ARCHIVE);

                    contentResolver.update(uri, values, null, null);
                } else {
                    // Not inbox, so remove label
                    final List<Folder> folders = Lists.newArrayList(conversation.getRawFolders());
                    folders.remove(folder);
                    final ContentValues values = new ContentValues(1);
                    values.put(Conversation.UPDATE_FOLDER_COLUMN, FolderList.listToBlob(folders));

                    contentResolver.update(uri, values, null, null);
                }
                break;
            }
            case DELETE: {
                contentResolver.delete(uri, null, null);
                break;
            }
            default:
                throw new IllegalArgumentException(
                        "The specified NotificationActionType is not a destructive action.");
        }
    }

    /**
     * Creates and displays an Undo notification for the specified {@link NotificationAction}.
     */
    public static void createUndoNotification(final Context context,
            final NotificationAction notificationAction) {
        final int notificationId = getNotificationId(
                notificationAction.getAccount().name, notificationAction.getFolder());

        final Notification notification =
                createUndoNotification(context, notificationAction, notificationId);

        final NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(notificationId, notification);

        sUndoNotifications.put(notificationId, notificationAction);
        sNotificationTimestamps.put(notificationId, notificationAction.getWhen());
    }

    public static void cancelUndoNotification(final Context context,
            final NotificationAction notificationAction) {
        sUndoNotifications.delete(getNotificationId(
                notificationAction.getAccount().name, notificationAction.getFolder()));
        resendNotifications(context);
    }

    /**
     * If an undo notification is left alone for a long enough time, it will disappear, this method
     * will be called, and the action will be finalized.
     */
    public static void processUndoNotification(final Context context,
            final NotificationAction notificationAction) {
        final int notificationId = getNotificationId(
                notificationAction.getAccount().name, notificationAction.getFolder());
        sUndoNotifications.delete(notificationId);
        sNotificationTimestamps.delete(notificationId);
        processDestructiveAction(context, notificationAction);
        resendNotifications(context);
    }

    public static int getNotificationId(final String account, final Folder folder) {
        // TODO(skennedy): When notifications are fully in UnifiedEmail, remove this method and use
        // the one in Utils
        // 1 == Gmail.NOTIFICATION_ID
        return 1 ^ account.hashCode() ^ folder.hashCode();
    }

    /**
     * Broadcasts an {@link Intent} to inform the app to resend its notifications.
     */
    public static void resendNotifications(final Context context) {
        final Intent intent = new Intent(MailIntentService.ACTION_RESEND_NOTIFICATIONS);
        intent.setPackage(context.getPackageName()); // Make sure we only deliver this to ourself
        context.startService(intent);
    }
}
