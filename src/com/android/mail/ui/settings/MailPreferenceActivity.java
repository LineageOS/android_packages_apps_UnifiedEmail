/*******************************************************************************
 *      Copyright (C) 2014 Google Inc.
 *      Licensed to The Android Open Source Project.
 *
 *      Licensed under the Apache License, Version 2.0 (the "License");
 *      you may not use this file except in compliance with the License.
 *      You may obtain a copy of the License at
 *
 *           http://www.apache.org/licenses/LICENSE-2.0
 *
 *      Unless required by applicable law or agreed to in writing, software
 *      distributed under the License is distributed on an "AS IS" BASIS,
 *      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *      See the License for the specific language governing permissions and
 *      limitations under the License.
 *******************************************************************************/

package com.android.mail.ui.settings;

import android.app.Fragment;
import android.os.Bundle;
import android.preference.PreferenceActivity;

import com.android.mail.R;
import com.google.common.annotations.VisibleForTesting;

import java.lang.ref.WeakReference;
import java.util.List;

public class MailPreferenceActivity extends PreferenceActivity {

    public static final String PREFERENCE_FRAGMENT_ID = "preference_fragment_id";

    private WeakReference<GeneralPrefsFragment> mGeneralPrefsFragmentRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @VisibleForTesting
    GeneralPrefsFragment getGeneralPrefsFragment() {
        return mGeneralPrefsFragmentRef != null ? mGeneralPrefsFragmentRef.get() : null;
    }

    @Override
    public void onAttachFragment(Fragment fragment) {
        super.onAttachFragment(fragment);
        if (fragment instanceof GeneralPrefsFragment) {
            mGeneralPrefsFragmentRef =
                    new WeakReference<GeneralPrefsFragment>((GeneralPrefsFragment) fragment);
        }
    }

    @Override
    protected boolean isValidFragment(String fragmentName) {
        return GeneralPrefsFragment.class.getCanonicalName().equals(fragmentName);
    }

    @Override
    public void onBuildHeaders(List<Header> target) {
        loadHeadersFromResource(R.xml.preference_headers, target);
    }
}
