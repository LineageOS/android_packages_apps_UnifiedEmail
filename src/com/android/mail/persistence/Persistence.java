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

import com.android.mail.utils.LogTag;
import com.android.mail.utils.LogUtils;
import com.google.common.annotations.VisibleForTesting;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;

import java.util.Set;

/**
 * This class is used to read and write Mail data to the persistent store. Note that each
 * key is prefixed with the account name, which is why I need to do some manual work in
 * order to get/set these values.
 */
public class Persistence {
    public static final String TAG = LogTag.getLogTag();

    private static Persistence mInstance = null;

    private static SharedPreferences sSharedPrefs;

    private Persistence() {
        //  Singleton only, use getInstance()
    }

    // The name of our shared preferences store
    public static final String SHARED_PREFERENCES_NAME = "UnifiedEmail";

    public static Persistence getInstance() {
        if (mInstance == null) {
            mInstance = new Persistence();
        }

        return mInstance;
    }

    public static SharedPreferences getPreferences(Context context) {
        if (sSharedPrefs == null) {
            sSharedPrefs = context.getSharedPreferences(
                    SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
        }
        return sSharedPrefs;
    }

    private String makeKey(String account, String key) {
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

    public void setBoolean(Context context, String account, String key, Boolean value) {
        Editor editor = getPreferences(context).edit();
        editor.putBoolean(makeKey(account, key), value);
        editor.apply();
    }

    @VisibleForTesting
    public void remove(Context context, String account, String key) {
        remove(context, makeKey(account, key));
    }

    private void remove(Context context, String key) {
        Editor editor = getPreferences(context).edit();
        editor.remove(key);
        editor.apply();
    }
}
