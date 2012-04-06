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
import com.android.mail.persistence.Persistence;
import com.android.mail.providers.Account;
import com.android.mail.providers.Folder;
import com.android.mail.providers.UIProvider;
import com.android.mail.ui.MailboxSelectionActivity;
import com.android.mail.utils.AccountUtils;
import com.android.mail.utils.Utils;
import com.google.common.collect.Sets;
import com.google.common.primitives.Ints;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences.Editor;
import android.database.Cursor;
import android.net.Uri;
import android.text.TextUtils;
import android.view.View;
import android.widget.RemoteViews;

import java.util.Set;

public class BaseWidgetProvider extends AppWidgetProvider {
    public static final String EXTRA_ACCOUNT = "account";
    public static final String EXTRA_FOLDER = "folder";
    public static final String EXTRA_UNREAD = "unread";
    public static final String WIDGET_ACCOUNT_PREFIX = "widget-account-";

    private static final String ACCOUNT_FOLDER_PREFERENCE_SEPARATOR = " ";

    private static String createWidgetPreferenceValue(Account account, Folder folder) {
        return account.uri.toString() + ACCOUNT_FOLDER_PREFERENCE_SEPARATOR + folder.uri.toString();

    }

    /**
     * Persists the information about the specified widget.
     */
    static void saveWidgetInformation(Context context, int appWidgetId, Account account,
            Folder folder) {
        Editor editor = Persistence.getPreferences(context).edit();
        editor.putString(WidgetProvider.WIDGET_ACCOUNT_PREFIX + appWidgetId,
                BaseWidgetProvider.createWidgetPreferenceValue(account, folder));
        editor.apply();
    }

    /**
     * Remove preferences when deleting widget
     */
    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        super.onDeleted(context, appWidgetIds);
        // TODO: (mindyp) save widget information.
        Editor editor = Persistence.getPreferences(context).edit();
        for (int i = 0; i < appWidgetIds.length; ++i) {
            // Remove the account in the preference
            editor.remove(WIDGET_ACCOUNT_PREFIX + appWidgetIds[i]);
        }
        editor.apply();

    }

    /**
     * @return the list ids for the currently configured widgets.
     */
    private static int[] getCurrentWidgetIds(Context context) {
        final AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        final ComponentName mailComponent = new ComponentName(context, WidgetProvider.PROVIDER_NAME);
        return appWidgetManager.getAppWidgetIds(mailComponent);
    }

    /**
     * Catches ACTION_NOTIFY_DATASET_CHANGED intent and update the corresponding
     * widgets.
     */
    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        // Receive notification for a certain account.
        if (Utils.ACTION_NOTIFY_DATASET_CHANGED.equals(intent.getAction())) {
            String account = intent.getExtras().getString(Utils.EXTRA_ACCOUNT);
            if (account == null) {
                return;
            }
            final Account accountToBeUpdated = Account.newinstance(account);
            final Set<Integer> widgetsToUpdate = Sets.newHashSet();

            for (int id : getCurrentWidgetIds(context)) {
                // Retrieve the persisted information for this widget from
                // preferences.
                final String accountFolder = Persistence.getPreferences(context).getString(
                        WIDGET_ACCOUNT_PREFIX + id, null);
                // If the account matched, update the widget.
                if (accountFolder != null) {
                    final String[] parsedInfo = TextUtils.split(accountFolder,
                            ACCOUNT_FOLDER_PREFERENCE_SEPARATOR);
                    if (TextUtils.equals(accountToBeUpdated.uri.toString(), parsedInfo[0])) {
                        widgetsToUpdate.add(id);
                    }
                }

            }
            if (widgetsToUpdate.size() > 0) {
                final int[] widgets = Ints.toArray(widgetsToUpdate);
                AppWidgetManager.getInstance(context).notifyAppWidgetViewDataChanged(widgets,
                        R.id.conversation_list);
            }
        }
    }

    /**
     * Update all widgets in the list
     */
    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        super.onUpdate(context, appWidgetManager, appWidgetIds);
        // Update each of the widgets with a remote adapter
        ContentResolver resolver = context.getContentResolver();
        for (int i = 0; i < appWidgetIds.length; ++i) {
            // Get the account for this widget from preference
            String accountFolder = Persistence.getPreferences(context).getString(
                    WIDGET_ACCOUNT_PREFIX + appWidgetIds[i], null);
            String accountUri = null;
            String folderUri = null;
            if (!TextUtils.isEmpty(accountFolder)) {
                final String[] parsedInfo = TextUtils.split(accountFolder,
                        ACCOUNT_FOLDER_PREFERENCE_SEPARATOR);
                if (parsedInfo.length == 2) {
                    accountUri = parsedInfo[0];
                    folderUri = parsedInfo[1];
                } else {
                    // TODO: (mindyp) how can we lookup the associated account?
                    // AccountCacheProvider?
                    accountUri = accountFolder;
                    folderUri = null; // account.getAccountInbox(context,
                                      // account);
                }
            }
            // account will be null the first time a widget is created. This is
            // OK, as isAccountValid will return false, allowing the widget to
            // be configured.

            // Lookup the account by URI.
            Account account = null;
            if (!TextUtils.isEmpty(accountUri)) {
                Cursor accountCursor = null;
                try {
                    accountCursor = resolver.query(Uri.parse(accountUri),
                            UIProvider.ACCOUNTS_PROJECTION, null, null, null);
                    if (accountCursor != null) {
                        accountCursor.moveToFirst();
                        account = new Account(accountCursor);
                    }
                } finally {
                    if (accountCursor != null) {
                        accountCursor.close();
                    }
                }
            }
            Folder folder = null;
            if (!TextUtils.isEmpty(folderUri)) {
                Cursor folderCursor = null;
                try {
                    folderCursor = resolver.query(Uri.parse(folderUri),
                            UIProvider.FOLDERS_PROJECTION, null, null, null);
                    if (folderCursor != null) {
                        folderCursor.moveToFirst();
                        folder = new Folder(folderCursor);
                    }
                } finally {
                    if (folderCursor != null) {
                        folderCursor.close();
                    }
                }
            }
            updateWidget(context, appWidgetIds[i], account, folder);
        }
    }

    protected static boolean isAccountValid(Context context, Account account) {
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
     * Returns true if this widget id has been configured and saved.
     */
    public static boolean isWidgetConfigured(Context context, int appWidgetId, Account account,
            Folder folder) {
        if (isAccountValid(context, account)) {
            return Persistence.getPreferences(context).getString(
                    WIDGET_ACCOUNT_PREFIX + appWidgetId, null) != null;
        }
        return false;
    }

    /**
     * Update the widget appWidgetId with the given account and folder
     */
    public static void updateWidget(Context context, int appWidgetId, Account account, Folder folder) {
        RemoteViews remoteViews = new RemoteViews(context.getPackageName(), R.layout.widget);
        final boolean isAccountValid = isAccountValid(context, account);

        if (!isAccountValid) {
            // Widget has not been configured yet
            remoteViews.setViewVisibility(R.id.widget_folder, View.GONE);
            remoteViews.setViewVisibility(R.id.widget_account, View.GONE);
            remoteViews.setViewVisibility(R.id.widget_unread_count, View.GONE);
            remoteViews.setViewVisibility(R.id.widget_compose, View.GONE);
            remoteViews.setViewVisibility(R.id.conversation_list, View.GONE);
            remoteViews.setViewVisibility(R.id.widget_folder_not_synced, View.GONE);
            remoteViews.setViewVisibility(R.id.widget_configuration, View.VISIBLE);

            final Intent configureIntent = new Intent(context, MailboxSelectionActivity.class);
            configureIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
            configureIntent.setData(Uri.parse(configureIntent.toUri(Intent.URI_INTENT_SCHEME)));
            configureIntent.setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
            PendingIntent clickIntent = PendingIntent.getActivity(context, 0, configureIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT);
            remoteViews.setOnClickPendingIntent(R.id.widget_configuration, clickIntent);
        } else {
            // Set folder to a space here to avoid flicker.
            configureValidAccountWidget(context, remoteViews, appWidgetId, account, folder, " ");

        }
        AppWidgetManager.getInstance(context).updateAppWidget(appWidgetId, remoteViews);
    }

    /**
     * Modifies the remoteView for the given account and folder.
     */
    static void configureValidAccountWidget(Context context, RemoteViews remoteViews,
            int appWidgetId, Account account, Folder folder, String folderDisplayName) {
        BaseWidgetProvider.configureValidAccountWidget(context, remoteViews, appWidgetId, account,
                folder, folderDisplayName, WidgetService.class);
    }

    /**
     * Modifies the remoteView for the given account and folder.
     */
    static void configureValidAccountWidget(Context context, RemoteViews remoteViews,
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

        BaseWidgetProvider.configureValidWidgetIntents(context, remoteViews, appWidgetId, account,
                folder, folderDisplayName, widgetService);
    }

    public static void configureValidWidgetIntents(Context context, RemoteViews remoteViews,
            int appWidgetId, Account account, Folder folder, String folderDisplayName,
            Class<?> serviceClass) {
        remoteViews.setViewVisibility(R.id.widget_configuration, View.GONE);


        // Launch an intent to avoid ANRs
        final Intent intent = new Intent(context, serviceClass);
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        intent.putExtra(EXTRA_ACCOUNT, account.serialize());
        intent.putExtra(EXTRA_FOLDER, folder.serialize());
        intent.setData(Uri.parse(intent.toUri(Intent.URI_INTENT_SCHEME)));
        remoteViews.setRemoteAdapter(R.id.conversation_list, intent);
        // Open mail app when click on header
        final Intent mailIntent = Utils.createViewFolderIntent(folder, account, false);
        PendingIntent clickIntent = PendingIntent.getActivity(context, 0, mailIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);
        remoteViews.setOnClickPendingIntent(R.id.widget_header, clickIntent);

        // On click intent for Compose
        final Intent composeIntent = new Intent();
        composeIntent.setAction(Intent.ACTION_SEND);
        composeIntent.putExtra(Utils.EXTRA_ACCOUNT, account);
        composeIntent.setData(account.composeIntentUri);
        if (account.composeIntentUri != null) {
            composeIntent.putExtra(Utils.EXTRA_COMPOSE_URI, account.composeIntentUri);
        }
        clickIntent = PendingIntent.getActivity(context, 0, composeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);
        remoteViews.setOnClickPendingIntent(R.id.widget_compose, clickIntent);
        // On click intent for Conversation
        final Intent conversationIntent = new Intent();
        conversationIntent.setAction(Intent.ACTION_VIEW);
        clickIntent = PendingIntent.getActivity(context, 0, conversationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);
        remoteViews.setPendingIntentTemplate(R.id.conversation_list, clickIntent);
    }

    /**
     * Updates all of the configured widgets.
     */
    public static void updateAllWidgets(Context context) {
        AppWidgetManager.getInstance(context).notifyAppWidgetViewDataChanged(
                getCurrentWidgetIds(context), R.id.conversation_list);
    }
}
