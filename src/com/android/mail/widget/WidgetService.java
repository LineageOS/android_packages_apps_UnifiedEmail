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
package com.android.mail.widget;

import com.android.mail.R;
import com.android.mail.browse.SendersView;
import com.android.mail.compose.ComposeActivity;
import com.android.mail.persistence.Persistence;
import com.android.mail.providers.Account;
import com.android.mail.providers.Conversation;
import com.android.mail.providers.Folder;
import com.android.mail.providers.UIProvider;
import com.android.mail.providers.UIProvider.ConversationListQueryParameters;
import com.android.mail.utils.AccountUtils;
import com.android.mail.utils.DelayedTaskHandler;
import com.android.mail.utils.LogTag;
import com.android.mail.utils.LogUtils;
import com.android.mail.utils.Utils;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.Loader;
import android.content.Loader.OnLoadCompleteListener;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Looper;
import android.support.v4.app.TaskStackBuilder;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import org.json.JSONException;

public class WidgetService extends RemoteViewsService {
    /**
     * Lock to avoid race condition between widgets.
     */
    private static Object sWidgetLock = new Object();

    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new MailFactory(getApplicationContext(), intent, this);
    }


    protected void configureValidAccountWidget(Context context, RemoteViews remoteViews,
            int appWidgetId, Account account, Folder folder, String folderName) {
        configureValidAccountWidget(context, remoteViews, appWidgetId, account, folder, folderName,
                WidgetService.class);
    }

    /**
     * Modifies the remoteView for the given account and folder.
     */
    public static void configureValidAccountWidget(Context context, RemoteViews remoteViews,
            int appWidgetId, Account account, Folder folder, String folderDisplayName,
            Class<?> widgetService) {
        remoteViews.setViewVisibility(R.id.widget_folder, View.VISIBLE);
        remoteViews.setTextViewText(R.id.widget_folder, folderDisplayName);
        remoteViews.setViewVisibility(R.id.widget_account, View.VISIBLE);
        remoteViews.setTextViewText(R.id.widget_account, account.name);
        remoteViews.setViewVisibility(R.id.widget_unread_count, View.VISIBLE);
        remoteViews.setViewVisibility(R.id.widget_compose, View.VISIBLE);
        remoteViews.setViewVisibility(R.id.conversation_list, View.VISIBLE);
        remoteViews.setViewVisibility(R.id.widget_folder_not_synced, View.GONE);

        WidgetService.configureValidWidgetIntents(context, remoteViews, appWidgetId, account,
                folder, folderDisplayName, widgetService);
    }

    public static void configureValidWidgetIntents(Context context, RemoteViews remoteViews,
            int appWidgetId, Account account, Folder folder, String folderDisplayName,
            Class<?> serviceClass) {
        remoteViews.setViewVisibility(R.id.widget_configuration, View.GONE);


        // Launch an intent to avoid ANRs
        final Intent intent = new Intent(context, serviceClass);
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        intent.putExtra(BaseWidgetProvider.EXTRA_ACCOUNT, account.serialize());
        intent.putExtra(BaseWidgetProvider.EXTRA_FOLDER, folder.serialize());
        intent.setData(Uri.parse(intent.toUri(Intent.URI_INTENT_SCHEME)));
        remoteViews.setRemoteAdapter(R.id.conversation_list, intent);
        // Open mail app when click on header
        final Intent mailIntent = Utils.createViewFolderIntent(folder, account);
        PendingIntent clickIntent = PendingIntent.getActivity(context, 0, mailIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);
        remoteViews.setOnClickPendingIntent(R.id.widget_header, clickIntent);

        // On click intent for Compose
        final Intent composeIntent = new Intent();
        composeIntent.setAction(Intent.ACTION_SEND);
        composeIntent.putExtra(Utils.EXTRA_ACCOUNT, account);
        composeIntent.setData(account.composeIntentUri);
        composeIntent.putExtra(ComposeActivity.EXTRA_FROM_EMAIL_TASK, true);
        if (account.composeIntentUri != null) {
            composeIntent.putExtra(Utils.EXTRA_COMPOSE_URI, account.composeIntentUri);
        }

        // Build a task stack that forces the conversation list on the stack before the compose
        // activity.
        final TaskStackBuilder taskStackBuilder = TaskStackBuilder.create(context);
        clickIntent = taskStackBuilder.addNextIntent(mailIntent)
                .addNextIntent(composeIntent)
                .getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
        remoteViews.setOnClickPendingIntent(R.id.widget_compose, clickIntent);

        // On click intent for Conversation
        final Intent conversationIntent = new Intent();
        conversationIntent.setAction(Intent.ACTION_VIEW);
        clickIntent = PendingIntent.getActivity(context, 0, conversationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);
        remoteViews.setPendingIntentTemplate(R.id.conversation_list, clickIntent);
    }

    /**
     * Persists the information about the specified widget.
     */
    public static void saveWidgetInformation(Context context, int appWidgetId, Account account,
                Folder folder) {
        final SharedPreferences.Editor editor = Persistence.getPreferences(context).edit();
        editor.putString(WidgetProvider.WIDGET_ACCOUNT_PREFIX + appWidgetId,
                createWidgetPreferenceValue(account, folder));
        editor.apply();
    }

    private static String createWidgetPreferenceValue(Account account, Folder folder) {
        return account.uri.toString() +
                BaseWidgetProvider.ACCOUNT_FOLDER_PREFERENCE_SEPARATOR + folder.uri.toString();

    }

    /**
     * Returns true if this widget id has been configured and saved.
     */
    public boolean isWidgetConfigured(Context context, int appWidgetId, Account account,
            Folder folder) {
        if (isAccountValid(context, account)) {
            return Persistence.getPreferences(context).getString(
                    BaseWidgetProvider.WIDGET_ACCOUNT_PREFIX + appWidgetId, null) != null;
        }
        return false;
    }

    protected boolean isAccountValid(Context context, Account account) {
        if (account != null) {
            Account[] accounts = AccountUtils.getSyncingAccounts(context);
            for (Account existing : accounts) {
                if (account != null && existing != null && account.uri.equals(existing.uri)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Remote Views Factory for Mail Widget.
     */
    protected static class MailFactory
            implements RemoteViewsService.RemoteViewsFactory, OnLoadCompleteListener<Cursor> {
        private static final int MAX_CONVERSATIONS_COUNT = 25;
        private static final int MAX_SENDERS_LENGTH = 25;
        private static final String LOG_TAG = LogTag.getLogTag();

        private final Context mContext;
        private final int mAppWidgetId;
        private final Account mAccount;
        private Folder mFolder;
        private final WidgetConversationViewBuilder mWidgetConversationViewBuilder;
        private Cursor mConversationCursor;
        private CursorLoader mFolderLoader;
        private FolderUpdateHandler mFolderUpdateHandler;
        private int mFolderCount;
        private boolean mShouldShowViewMore;
        private boolean mFolderInformationShown = false;
        private ContentResolver mResolver;
        private WidgetService mService;
        private int mSenderFormatVersion;

        public MailFactory(Context context, Intent intent, WidgetService service) {
            mContext = context;
            mAppWidgetId = intent.getIntExtra(
                    AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
            mAccount = Account.newinstance(intent.getStringExtra(WidgetProvider.EXTRA_ACCOUNT));
            try {
                mFolder = Folder.fromJSONString(intent.getStringExtra(WidgetProvider.EXTRA_FOLDER));
            } catch (JSONException e) {
                mFolder = null;
                LogUtils.wtf(LOG_TAG, e, "unable to parse folder for widget");
            }
            mWidgetConversationViewBuilder = new WidgetConversationViewBuilder(context,
                    mAccount);
            mResolver = context.getContentResolver();
            mService = service;
        }

        @Override
        public void onCreate() {

            // Save the map between widgetId and account to preference
            saveWidgetInformation(mContext, mAppWidgetId, mAccount, mFolder);

            // If the account of this widget has been removed, we want to update the widget to
            // "Tap to configure" mode.
            if (!mService.isWidgetConfigured(mContext, mAppWidgetId, mAccount, mFolder)) {
                BaseWidgetProvider.updateWidget(mContext, mAppWidgetId, mAccount, mFolder);
            }

            // We want to limit the query result to 25 and don't want these queries to cause network
            // traffic
            final Uri.Builder builder = mFolder.conversationListUri.buildUpon();
            final String maxConversations = Integer.toString(MAX_CONVERSATIONS_COUNT);
            final Uri widgetConversationQueryUri = builder
                    .appendQueryParameter(ConversationListQueryParameters.LIMIT, maxConversations)
                    .appendQueryParameter(ConversationListQueryParameters.USE_NETWORK,
                            Boolean.FALSE.toString()).build();

            mConversationCursor = mResolver.query(widgetConversationQueryUri,
                    UIProvider.CONVERSATION_PROJECTION, null, null, null);

            mFolderLoader = new CursorLoader(mContext, mFolder.uri, UIProvider.FOLDERS_PROJECTION,
                    null, null, null);
            mFolderLoader.registerListener(0, this);
            mFolderUpdateHandler = new FolderUpdateHandler(mContext.getResources().getInteger(
                    R.integer.widget_folder_refresh_delay_ms));
            mFolderUpdateHandler.scheduleTask();

        }

        @Override
        public void onDestroy() {
            synchronized (sWidgetLock) {
                if (mConversationCursor != null && !mConversationCursor.isClosed()) {
                    mConversationCursor.close();
                    mConversationCursor = null;
                }
            }

            if (mFolderLoader != null) {
                mFolderLoader.reset();
                mFolderLoader = null;
            }
        }

        @Override
        public void onDataSetChanged() {
            synchronized (sWidgetLock) {
                // TODO: use loader manager.
                mConversationCursor.requery();
            }
            mFolderUpdateHandler.scheduleTask();
        }

        /**
         * Returns the number of items should be shown in the widget list.  This method also updates
         * the boolean that indicates whether the "show more" item should be shown.
         * @return the number of items to be displayed in the list.
         */
        @Override
        public int getCount() {
            synchronized (sWidgetLock) {
                final int count = getConversationCount();
                mShouldShowViewMore = count < mConversationCursor.getCount()
                        || count < mFolderCount;
                return count + (mShouldShowViewMore ? 1 : 0);
            }
        }

        /**
         * Returns the number of conversations that should be shown in the widget.  This method
         * doesn't update the boolean that indicates that the "show more" item should be included
         * in the list.
         * @return
         */
        private int getConversationCount() {
            synchronized (sWidgetLock) {
                return Math.min(mConversationCursor.getCount(), MAX_CONVERSATIONS_COUNT);
            }
        }

        /**
         * @return the {@link RemoteViews} for a specific position in the list.
         */
        @Override
        public RemoteViews getViewAt(int position) {
            synchronized (sWidgetLock) {
                // "View more conversations" view.
                if (mConversationCursor == null
                        || (mShouldShowViewMore && position >= getConversationCount())) {
                    return getViewMoreConversationsView();
                }

                if (!mConversationCursor.moveToPosition(position)) {
                    // If we ever fail to move to a position, return the "View More conversations"
                    // view.
                    LogUtils.e(LOG_TAG,
                            "Failed to move to position %d in the cursor.", position);
                    return getViewMoreConversationsView();
                }

                Conversation conversation = new Conversation(mConversationCursor);
                String senders = conversation.conversationInfo != null ?
                        conversation.conversationInfo.sendersInfo : conversation.senders;
                SendersView.SendersInfo sendersInfo = new SendersView.SendersInfo(senders);
                mSenderFormatVersion = sendersInfo.version;
                String sendersString = sendersInfo.text;
                // Split the senders and status from the instructions.
                SpannableStringBuilder senderBuilder = new SpannableStringBuilder();
                SpannableStringBuilder statusBuilder = new SpannableStringBuilder();

                if (mSenderFormatVersion == SendersView.MERGED_FORMATTING) {
                    Utils.getStyledSenderSnippet(mContext, sendersString, senderBuilder,
                            statusBuilder, MAX_SENDERS_LENGTH, false, false, false);
                } else {
                    senderBuilder.append(sendersString);
                }
                // Get styled date.
                CharSequence date = DateUtils.getRelativeTimeSpanString(
                        mContext, conversation.dateMs);

                // Load up our remote view.
                RemoteViews remoteViews = mWidgetConversationViewBuilder.getStyledView(
                        senderBuilder, statusBuilder, date, filterTag(conversation.subject),
                        conversation.snippet, conversation.rawFolders, conversation.hasAttachments,
                        conversation.read, mFolder);

                // On click intent.
                remoteViews.setOnClickFillInIntent(R.id.widget_conversation,
                        Utils.createViewConversationIntent(conversation, mFolder, mAccount));

                return remoteViews;
            }
        }

        /**
         * @return the "View more conversations" view.
         */
        private RemoteViews getViewMoreConversationsView() {
            RemoteViews view = new RemoteViews(mContext.getPackageName(), R.layout.widget_loading);
            view.setTextViewText(
                    R.id.loading_text, mContext.getText(R.string.view_more_conversations));
            view.setOnClickFillInIntent(R.id.widget_loading,
                    Utils.createViewFolderIntent(mFolder, mAccount));
            return view;
        }

        @Override
        public RemoteViews getLoadingView() {
            RemoteViews view = new RemoteViews(mContext.getPackageName(), R.layout.widget_loading);
            view.setTextViewText(
                    R.id.loading_text, mContext.getText(R.string.loading_conversation));
            return view;
        }

        @Override
        public int getViewTypeCount() {
            return 2;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public boolean hasStableIds() {
            return false;
        }

        @Override
        public void onLoadComplete(Loader<Cursor> loader, Cursor data) {
            if (!data.moveToFirst()) {
                return;
            }
            final int unreadCount = data.getInt(UIProvider.FOLDER_UNREAD_COUNT_COLUMN);
            final String folderName = data.getString(UIProvider.FOLDER_NAME_COLUMN);
            mFolderCount = data.getInt(UIProvider.FOLDER_TOTAL_COUNT_COLUMN);

            RemoteViews remoteViews = new RemoteViews(mContext.getPackageName(), R.layout.widget);
            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(mContext);

            if (!mFolderInformationShown && !TextUtils.isEmpty(folderName)) {
                // We want to do a full update to the widget at least once, as the widget
                // manager doesn't cache the state of the remote views when doing a partial
                // widget update. This causes the folder name to be shown as blank if the state
                // of the widget is restored.
                mService.configureValidAccountWidget(
                        mContext, remoteViews, mAppWidgetId, mAccount, mFolder, folderName);
                appWidgetManager.updateAppWidget(mAppWidgetId, remoteViews);
                mFolderInformationShown = true;
            }

            remoteViews.setViewVisibility(R.id.widget_folder, View.VISIBLE);
            remoteViews.setTextViewText(R.id.widget_folder, folderName);
            remoteViews.setViewVisibility(R.id.widget_unread_count, View.VISIBLE);
            remoteViews.setTextViewText(
                    R.id.widget_unread_count, Utils.getUnreadCountString(mContext, unreadCount));

            appWidgetManager.partiallyUpdateAppWidget(mAppWidgetId, remoteViews);
        }

        /**
         * If the subject contains the tag of a mailing-list (text surrounded with []), return the
         * subject with that tag ellipsized, e.g. "[android-gmail-team] Hello" -> "[andr...] Hello"
         */
        private static String filterTag(String subject) {
            String result = subject;
            if (subject.length() > 0 && subject.charAt(0) == '[') {
                int end = subject.indexOf(']');
                if (end > 0) {
                    String tag = subject.substring(1, end);
                    result = "[" + Utils.ellipsize(tag, 7) + "]" + subject.substring(end + 1);
                }
            }

            return result;
        }

        /**
         * A {@link DelayedTaskHandler} to throttle folder update to a reasonable rate.
         */
        private class FolderUpdateHandler extends DelayedTaskHandler {
            public FolderUpdateHandler(int refreshDelay) {
                super(Looper.myLooper(), refreshDelay);
            }

            @Override
            protected void performTask() {
                // Start the loader. The cached data will be returned if present.
                if (mFolderLoader != null) {
                    mFolderLoader.startLoading();
                }
            }
        }
    }
}
