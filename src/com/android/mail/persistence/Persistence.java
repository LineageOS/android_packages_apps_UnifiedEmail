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

package com.android.mail.persistence;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Maps;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;

import com.android.mail.utils.LogTag;

import java.util.Map;
import java.util.Set;

/**
 * This class is used to read and write Mail data to the persistent store. Note that each
 * key is prefixed with the account name, which is why I need to do some manual work in
 * order to get/set these values.
 */
public class Persistence {
    public static final String TAG = LogTag.getLogTag();

    private static Persistence mInstance = null;

    private static final Map<String, SharedPreferences> SHARED_PREFS_MAP = Maps.newHashMap();

    protected Persistence() {
        //  Singleton only, use getInstance()
    }

    // The name of our shared preferences store
    private static final String SHARED_PREFERENCES_NAME = "UnifiedEmail";

    public static Persistence getInstance() {
        if (mInstance == null) {
            mInstance = new Persistence();
        }

        return mInstance;
    }

    public String getSharedPreferencesName() {
        return SHARED_PREFERENCES_NAME;
    }

    public SharedPreferences getPreferences(Context context) {
        final String sharedPrefName = getSharedPreferencesName();
        synchronized (SHARED_PREFS_MAP) {
            SharedPreferences preferences = SHARED_PREFS_MAP.get(sharedPrefName);
            if (preferences == null) {
                preferences = context.getSharedPreferences(sharedPrefName, Context.MODE_PRIVATE);
                SHARED_PREFS_MAP.put(sharedPrefName, preferences);
            }
            return preferences;
        }
    }

    protected String makeKey(String account, String key) {
        return account != null ? account + "-" + key : key;
    }

    public void setString(Context context, String account, String key, String value) {
        Editor editor = getPreferences(context).edit();
        editor.putString(makeKey(account, key), value);
        editor.apply();
    }

    public void setStringSet(Context context, String account, String key, Set<String> values) {
        Editor editor = getPreferences(context).edit();
        editor.putStringSet(makeKey(account, key) , values);
        editor.apply();
    }

    public Set<String> getStringSet(Context context, String account, String key, Set<String> def) {
        return getPreferences(context).getStringSet(makeKey(account, key), def);
    }

    /**
     * Sets a boolean preference. For an account-specific setting, provide a string account
     * (user@example.com). Otherwise, providing null is safe.
     * @param context
     * @param account
     * @param key
     * @param value
     */
    public void setBoolean(Context context, String account, String key, Boolean value) {
        Editor editor = getPreferences(context).edit();
        editor.putBoolean(makeKey(account, key), value);
        editor.apply();
    }

    @VisibleForTesting
    public void remove(Context context, String account, String key) {
        remove(context, makeKey(account, key));
    }

    protected void remove(Context context, String key) {
        Editor editor = getPreferences(context).edit();
        editor.remove(key);
        editor.apply();
    }
}
