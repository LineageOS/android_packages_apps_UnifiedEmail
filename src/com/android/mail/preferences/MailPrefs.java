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

import com.google.common.collect.ImmutableSet;

import android.content.Context;

import com.android.mail.MailIntentService;
import com.android.mail.providers.Account;
import com.android.mail.providers.UIProvider;
import com.android.mail.widget.BaseWidgetProvider;

import java.util.Set;

/**
 * A high-level API to store and retrieve unified mail preferences.
 * <p>
 * This will serve as an eventual replacement for Gmail's Persistence class.
 */
public final class MailPrefs extends VersionedPrefs {

    public static final boolean SHOW_EXPERIMENTAL_PREFS = true;

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

        public static final String CONVERSATION_LIST_SWIPE_ACTION =
                "conversation-list-swipe-action";

        /** Hidden preference used to cache the active notification set */
        private static final String CACHED_ACTIVE_NOTIFICATION_SET =
                "cache-active-notification-set";

        public static final ImmutableSet<String> BACKUP_KEYS =
                new ImmutableSet.Builder<String>()
                .add(DEFAULT_REPLY_ALL)
                .add(CONVERSATION_LIST_SWIPE_ACTION)
                .build();

        public static final String ENABLE_CONVLIST_PHOTOS = "enable-convlist-photos";
        public static final String ENABLE_WHOOSH_ZOOM = "enable-whoosh-zoom";
        public static final String ENABLE_MUNGE_TABLES = "enable-munge-tables";
        public static final String ENABLE_MUNGE_IMAGES = "enable-munge-images";
        public static final String ENABLE_SECTIONED_INBOX_EXPERIMENT = "enable-sectioned-inbox";

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

    /**
     * Get whether to show the experimental inline contact photos in the
     * conversation list.
     */
    @SuppressWarnings("unused")
    public boolean areConvListPhotosEnabled() {
        // If experimental preferences are not enabled, return false.
        return SHOW_EXPERIMENTAL_PREFS && getSharedPreferences().getBoolean(
                PreferenceKeys.ENABLE_CONVLIST_PHOTOS, false);
    }

    public void setConvListPhotosEnabled(final boolean enabled) {
        getEditor().putBoolean(PreferenceKeys.ENABLE_CONVLIST_PHOTOS, enabled).apply();
    }

    @SuppressWarnings("unused")
    public boolean isWhooshZoomEnabled() {
        // If experimental preferences are not enabled, return false.
        return SHOW_EXPERIMENTAL_PREFS && getSharedPreferences().getBoolean(
                PreferenceKeys.ENABLE_WHOOSH_ZOOM, false);
    }

    @SuppressWarnings("unused")
    public boolean shouldMungeTables() {
        // If experimental preferences are not enabled, return false.
        return SHOW_EXPERIMENTAL_PREFS && getSharedPreferences().getBoolean(
                PreferenceKeys.ENABLE_MUNGE_TABLES, true);
    }

    @SuppressWarnings("unused")
    public boolean shouldMungeImages() {
        // If experimental preferences are not enabled, return false.
        return SHOW_EXPERIMENTAL_PREFS && getSharedPreferences().getBoolean(
                PreferenceKeys.ENABLE_MUNGE_IMAGES, true);
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
     * Gets the action to take (one of the values from {@link ConversationListSwipeActions}) when an
     * item in the conversation list is swiped.
     *
     * @param allowArchive <code>true</code> if Archive is an acceptable action (this will affect
     *        the default return value)
     */
    public String getConversationListSwipeAction(final boolean allowArchive) {
        return getSharedPreferences().getString(
                PreferenceKeys.CONVERSATION_LIST_SWIPE_ACTION,
                allowArchive ? ConversationListSwipeActions.ARCHIVE
                        : ConversationListSwipeActions.DELETE);
    }

    /**
     * Gets the action to take (one of the values from {@link UIProvider.Swipe}) when an item in the
     * conversation list is swiped.
     *
     * @param allowArchive <code>true</code> if Archive is an acceptable action (this will affect
     *        the default return value)
     */
    public int getConversationListSwipeActionInteger(final boolean allowArchive) {
        final String swipeAction = getConversationListSwipeAction(allowArchive);
        if (ConversationListSwipeActions.ARCHIVE.equals(swipeAction)) {
            return UIProvider.Swipe.ARCHIVE;
        } else if (ConversationListSwipeActions.DELETE.equals(swipeAction)) {
            return UIProvider.Swipe.DELETE;
        } else if (ConversationListSwipeActions.DISABLED.equals(swipeAction)) {
            return UIProvider.Swipe.DISABLED;
        } else {
            return UIProvider.Swipe.DEFAULT;
        }
    }

    public void setConversationListSwipeAction(final String swipeAction) {
        getEditor().putString(PreferenceKeys.CONVERSATION_LIST_SWIPE_ACTION, swipeAction).apply();
        MailIntentService.broadcastBackupDataChanged(getContext());
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

    public boolean isSectionedInboxExperimentEnabled() {
        // If experimental preferences are not enabled, return false.
        return SHOW_EXPERIMENTAL_PREFS && getSharedPreferences().getBoolean(
                PreferenceKeys.ENABLE_SECTIONED_INBOX_EXPERIMENT, false);
    }
}
