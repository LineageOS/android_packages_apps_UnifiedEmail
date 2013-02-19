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
import android.content.SharedPreferences.Editor;

import com.android.mail.providers.Account;
import com.android.mail.providers.Folder;

/**
 * A high-level API to store and retrieve unified mail preferences.
 * <p>
 * This will serve as an eventual replacement for Gmail's Persistence class.
 */
public final class MailPrefs {

    public static final boolean SHOW_EXPERIMENTAL_PREFS = false;

    // TODO: support account-specific prefs. probably just use a different prefs name instead of
    // prepending every key.

    private static final String PREFS_NAME = "UnifiedEmail";

    private static MailPrefs sInstance;
    private final SharedPreferences mPrefs;

    private static final String WIDGET_ACCOUNT_PREFIX = "widget-account-";
    private static final String ACCOUNT_FOLDER_PREFERENCE_SEPARATOR = " ";

    private static final String ENABLE_FTS = "enable-fts";
    private static final String ENABLE_CHIP_DRAG_AND_DROP = "enable-chip-drag-and-drop";
    public static final String ENABLE_CONVLIST_PHOTOS = "enable-convlist-photos";
    public static final String ENABLE_WHOOSH_ZOOM = "enable-whoosh-zoom";

    public static MailPrefs get(Context c) {
        if (sInstance == null) {
            sInstance = new MailPrefs(c);
        }
        return sInstance;
    }

    private MailPrefs(Context c) {
        mPrefs = c.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public String getSharedPreferencesName() {
        return PREFS_NAME;
    }

    /**
     * Set the value of a shared preference of type boolean.
     */
    public void setSharedBooleanPreference(String pref, boolean value) {
        mPrefs.edit().putBoolean(pref, value).apply();
    }

    public boolean isWidgetConfigured(int appWidgetId) {
        return mPrefs.contains(WIDGET_ACCOUNT_PREFIX + appWidgetId);
    }

    public void configureWidget(int appWidgetId, Account account, Folder folder) {
        mPrefs.edit()
            .putString(WIDGET_ACCOUNT_PREFIX + appWidgetId,
                    createWidgetPreferenceValue(account, folder))
            .apply();
    }

    public String getWidgetConfiguration(int appWidgetId) {
        return mPrefs.getString(WIDGET_ACCOUNT_PREFIX + appWidgetId, null);
    }

    public boolean fullTextSearchEnabled() {
        // If experimental preferences are not enabled, return true.
        return !SHOW_EXPERIMENTAL_PREFS || mPrefs.getBoolean(ENABLE_FTS, true);
    }

    public boolean chipDragAndDropEnabled() {
        // If experimental preferences are not enabled, return false.
        return SHOW_EXPERIMENTAL_PREFS && mPrefs.getBoolean(ENABLE_CHIP_DRAG_AND_DROP, false);
    }

    /**
     * Get whether to show the experimental inline contact photos in the
     * conversation list.
     */
    public boolean areConvListPhotosEnabled() {
        // If experimental preferences are not enabled, return false.
        return SHOW_EXPERIMENTAL_PREFS && mPrefs.getBoolean(ENABLE_CONVLIST_PHOTOS, false);
    }

    public boolean isWhooshZoomEnabled() {
        // If experimental preferences are not enabled, return false.
        return SHOW_EXPERIMENTAL_PREFS && mPrefs.getBoolean(ENABLE_WHOOSH_ZOOM, false);
    }

    private static String createWidgetPreferenceValue(Account account, Folder folder) {
        return account.uri.toString() +
                ACCOUNT_FOLDER_PREFERENCE_SEPARATOR + folder.uri.toString();

    }

    public void clearWidgets(int[] appWidgetIds) {
        final Editor e = mPrefs.edit();
        for (int id : appWidgetIds) {
            e.remove(WIDGET_ACCOUNT_PREFIX + id);
        }
        e.apply();
    }
}
