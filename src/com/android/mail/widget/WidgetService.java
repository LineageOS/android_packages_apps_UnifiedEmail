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
import com.android.mail.providers.Account;
import com.android.mail.providers.Conversation;
import com.android.mail.providers.Folder;
import com.android.mail.providers.UIProvider;
import com.android.mail.providers.UIProvider.ConversationListQueryParameters;
import com.android.mail.utils.DelayedTaskHandler;
import com.android.mail.utils.LogUtils;
import com.android.mail.utils.Utils;

import android.appwidget.AppWidgetManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.Loader;
import android.content.Loader.OnLoadCompleteListener;
import android.database.Cursor;
import android.net.Uri;
import android.os.Looper;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

public class WidgetService extends RemoteViewsService {
    /**
     * Lock to avoid race condition between widgets.
     */
    private static Object sWidgetLock = new Object();

    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        Context context = getApplicationContext();
        Account account = Account.newinstance(intent.getStringExtra(WidgetProvider.EXTRA_ACCOUNT));
        return new MailFactory(context, intent, this, new WidgetConversationViewBuilder(context,
                account), account);
    }


    protected void configureValidAccountWidget(Context context, RemoteViews remoteViews,
            int appWidgetId, Account account, Folder folder, String folderName) {
        BaseWidgetProvider.configureValidAccountWidget(context, remoteViews, appWidgetId, account,
                folder, folderName);
    }

    /**
     * Remote Views Factory for Mail Widget.
     */
    protected static class MailFactory
            implements RemoteViewsService.RemoteViewsFactory, OnLoadCompleteListener<Cursor> {
        private static final int MAX_CONVERSATIONS_COUNT = 25;
        private static final int MAX_SENDERS_LENGTH = 25;
        private static final String LOG_TAG = new LogUtils().getLogTag();

        private final Context mContext;
        private final int mAppWidgetId;
        private final Account mAccount;
        private final Folder mFolder;
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

        public MailFactory(Context context, Intent intent, WidgetService service,
                WidgetConversationViewBuilder builder, Account account) {
            mContext = context;
            mAppWidgetId = intent.getIntExtra(
                    AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
            mAccount = account;
            mFolder = new Folder(intent.getStringExtra(WidgetProvider.EXTRA_FOLDER));
            mWidgetConversationViewBuilder = builder;
            mResolver = context.getContentResolver();
            mService = service;
        }

        @Override
        public void onCreate() {
            // Save the map between widgetId and account to preference
            BaseWidgetProvider.saveWidgetInformation(mContext, mAppWidgetId, mAccount, mFolder);

            // If the account of this widget has been removed, we want to update the widget to
            // "Tap to configure" mode.
            if (!BaseWidgetProvider.isWidgetConfigured(mContext, mAppWidgetId, mAccount, mFolder)) {
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
                SendersView.SendersInfo sendersInfo = new SendersView.SendersInfo(
                        conversation.senders);
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
                    Utils.createViewFolderIntent(mFolder, mAccount, false));
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
