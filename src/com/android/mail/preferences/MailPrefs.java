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

import com.android.mail.R;
import com.android.mail.providers.Account;
import com.android.mail.providers.Folder;

/**
 * A high-level API to store and retrieve unified mail preferences.
 * <p>
 * This will serve as an eventual replacement for Gmail's Persistence class.
 */
public final class MailPrefs {

    // TODO: support account-specific prefs. probably just use a different prefs name instead of
    // prepending every key.

    private static final String PREFS_NAME = "UnifiedEmail";

    private static MailPrefs sInstance;
    private final SharedPreferences mPrefs;

    private static final String WIDGET_ACCOUNT_PREFIX = "widget-account-";
    private static final String ACCOUNT_FOLDER_PREFERENCE_SEPARATOR = " ";

    // Hidden preference to indicate what version a "What's New" dialog was last shown for.
    private static final String WHATS_NEW_LAST_SHOWN_VERSION = "whats-new-last-shown-version";
    public static final String ENABLE_CONVLIST_PHOTOS = "enable-convlist-photos";

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

    /**
     * Get whether to show the experimental inline contact photos in the
     * conversation list.
     */
    public boolean areConvListPhotosEnabled() {
        return mPrefs.getBoolean(ENABLE_CONVLIST_PHOTOS, false);
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

    /**
     * Returns a boolean indicating whether the What's New dialog should be shown
     * @param context Context
     * @return Boolean indicating whether the What's New dialogs should be shown
     */
    public boolean getShouldShowWhatsNew(final Context context) {
        // Get the last versionCode from the last time that the whats new dialogs has been shown
        final int lastShownVersion = mPrefs.getInt(WHATS_NEW_LAST_SHOWN_VERSION, 0);

        // Get the last version the What's New dialog was updated
        final int lastUpdatedVersion =
                context.getResources().getInteger(R.integer.whats_new_last_updated);

        return lastUpdatedVersion > lastShownVersion;
    }

    public void setHasShownWhatsNew(final int version) {
        mPrefs.edit()
            .putInt(WHATS_NEW_LAST_SHOWN_VERSION, version)
            .apply();
    }

}
