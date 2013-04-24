/*
 * Copyright (C) 2012 Google Inc.
 * Licensed to The Android Open Source Project.
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

package com.android.mail.preferences;

import android.content.Context;
import android.content.SharedPreferences;

import com.android.mail.MailIntentService;
import com.android.mail.providers.Account;
import com.android.mail.providers.UIProvider;
import com.android.mail.widget.BaseWidgetProvider;
import com.google.common.collect.ImmutableSet;

import java.util.Set;

/**
 * A high-level API to store and retrieve unified mail preferences.
 * <p>
 * This will serve as an eventual replacement for Gmail's Persistence class.
 */
public final class MailPrefs extends VersionedPrefs {

    public static final boolean SHOW_EXPERIMENTAL_PREFS = false;

    private static final String PREFS_NAME = "UnifiedEmail";

    private static MailPrefs sInstance;

    public static final class PreferenceKeys {
        private static final String MIGRATED_VERSION = "migrated-version";

        public static final String WIDGET_ACCOUNT_PREFIX = "widget-account-";

        /** Hidden preference to indicate what version a "What's New" dialog was last shown for. */
        public static final String WHATS_NEW_LAST_SHOWN_VERSION = "whats-new-last-shown-version";

        /**
         * A boolean that, if <code>true</code>, means we should default all replies to "reply all"
         */
        public static final String DEFAULT_REPLY_ALL = "default-reply-all";
        /**
         * A preference for storing most recently used accounts (ordered list or URIs)
         */
        public static final String RECENT_ACCOUNTS = "recent-account-uris";
        /**
         * A boolean that, if <code>true</code>, means we should allow conversation list swiping
         */
        public static final String CONVERSATION_LIST_SWIPE = "conversation-list-swipe";

        /**
         * A boolean indicating whether the user prefers delete or archive.
         */
        public static final String PREFER_DELETE = "prefer-delete";

        /** Hidden preference used to cache the active notification set */
        private static final String CACHED_ACTIVE_NOTIFICATION_SET =
                "cache-active-notification-set";

        public static final ImmutableSet<String> BACKUP_KEYS =
                new ImmutableSet.Builder<String>()
                .add(DEFAULT_REPLY_ALL)
                .add(CONVERSATION_LIST_SWIPE)
                .add(PREFER_DELETE)
                .build();

    }

    public static final class ConversationListSwipeActions {
        public static final String ARCHIVE = "archive";
        public static final String DELETE = "delete";
        public static final String DISABLED = "disabled";
    }

    public static MailPrefs get(Context c) {
        if (sInstance == null) {
            sInstance = new MailPrefs(c);
        }
        return sInstance;
    }

    private MailPrefs(Context c) {
        super(c, PREFS_NAME);
    }

    @Override
    protected void performUpgrade(final int oldVersion, final int newVersion) {
        if (oldVersion > newVersion) {
            throw new IllegalStateException(
                    "You appear to have downgraded your app. Please clear app data.");
        } else if (oldVersion == newVersion) {
            return;
        }
    }

    @Override
    protected boolean canBackup(final String key) {
        return PreferenceKeys.BACKUP_KEYS.contains(key);
    }

    @Override
    protected boolean hasMigrationCompleted() {
        return getSharedPreferences().getInt(PreferenceKeys.MIGRATED_VERSION, 0)
                >= CURRENT_VERSION_NUMBER;
    }

    @Override
    protected void setMigrationComplete() {
        getEditor().putInt(PreferenceKeys.MIGRATED_VERSION, CURRENT_VERSION_NUMBER).apply();
    }

    public boolean isWidgetConfigured(int appWidgetId) {
        return getSharedPreferences().contains(PreferenceKeys.WIDGET_ACCOUNT_PREFIX + appWidgetId);
    }

    public void configureWidget(int appWidgetId, Account account, final String folderUri) {
        getEditor().putString(PreferenceKeys.WIDGET_ACCOUNT_PREFIX + appWidgetId,
                createWidgetPreferenceValue(account, folderUri)).apply();
    }

    public String getWidgetConfiguration(int appWidgetId) {
        return getSharedPreferences().getString(PreferenceKeys.WIDGET_ACCOUNT_PREFIX + appWidgetId,
                null);
    }

    private static String createWidgetPreferenceValue(Account account, String folderUri) {
        return account.uri.toString() + BaseWidgetProvider.ACCOUNT_FOLDER_PREFERENCE_SEPARATOR
                + folderUri;

    }

    public void clearWidgets(int[] appWidgetIds) {
        for (int id : appWidgetIds) {
            getEditor().remove(PreferenceKeys.WIDGET_ACCOUNT_PREFIX + id);
        }
        getEditor().apply();
    }

    /** If <code>true</code>, we should default all replies to "reply all" rather than "reply" */
    public boolean getDefaultReplyAll() {
        return getSharedPreferences().getBoolean(PreferenceKeys.DEFAULT_REPLY_ALL, false);
    }

    public void setDefaultReplyAll(final boolean replyAll) {
        getEditor().putBoolean(PreferenceKeys.DEFAULT_REPLY_ALL, replyAll).apply();
        MailIntentService.broadcastBackupDataChanged(getContext());
    }

    /**
     * Return a list of URIs corresponding to the most recently used accounts
     *
     * @return uris of accounts from least recently used to most recently used
     */
    public String getRecentAccountUris() {
        return getSharedPreferences().getString(PreferenceKeys.RECENT_ACCOUNTS,
                "");
    }

    /**
     * Take in an ArrayList of account URIs that are in order from least recently
     * used to most recently used, and save it as prefs.
     */
    public void setRecentAccountUris(String accountUris) {
        getEditor().putString(PreferenceKeys.RECENT_ACCOUNTS, accountUris).apply();
    }

    /**
     * Gets a boolean indicating whether delete is preferred over archive.
     */
    public boolean getPreferDelete() {
        final SharedPreferences sharedPreferences = getSharedPreferences();
        return sharedPreferences.getBoolean(PreferenceKeys.PREFER_DELETE, false);
    }

    public void setPreferDelete(final boolean preferDelete) {
        getEditor().putBoolean(PreferenceKeys.PREFER_DELETE, preferDelete).apply();
        MailIntentService.broadcastBackupDataChanged(getContext());
    }

    /**
     * Gets a boolean indicating whether conversation list swiping is enabled.
     */
    public boolean getIsConversationListSwipeEnabled() {
        final SharedPreferences sharedPreferences = getSharedPreferences();
        return sharedPreferences.getBoolean(PreferenceKeys.CONVERSATION_LIST_SWIPE, true);
    }

    public void setConversationListSwipeEnabled(final boolean enabled) {
        getEditor().putBoolean(PreferenceKeys.CONVERSATION_LIST_SWIPE, enabled).apply();
        MailIntentService.broadcastBackupDataChanged(getContext());
    }

    /**
     * Gets the action to take (one of the values from {@link UIProvider.Swipe}) when an item in the
     * conversation list is swiped.
     *
     * @param allowArchive <code>true</code> if Archive is an acceptable action (this will affect
     *        the default return value)
     */
    public int getConversationListSwipeActionInteger(final boolean allowArchive) {
        final boolean swipeEnabled = getIsConversationListSwipeEnabled();
        final boolean archive = !getPreferDelete() && allowArchive;

        if (swipeEnabled) {
            return archive ? UIProvider.Swipe.ARCHIVE : UIProvider.Swipe.DELETE;
        }

        return UIProvider.Swipe.DISABLED;
    }

    /**
     * Returns the previously cached notification set
     */
    public Set<String> getActiveNotificationSet() {
        return getSharedPreferences()
                .getStringSet(PreferenceKeys.CACHED_ACTIVE_NOTIFICATION_SET, null);
    }

    /**
     * Caches the current notification set.
     */
    public void cacheActiveNotificationSet(final Set<String> notificationSet) {
        getEditor().putStringSet(PreferenceKeys.CACHED_ACTIVE_NOTIFICATION_SET, notificationSet)
                .apply();
    }
}
