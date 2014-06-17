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

import android.app.Activity;
import android.content.Intent;
import android.preference.PreferenceActivity;
import android.test.ActivityInstrumentationTestCase2;
import android.test.UiThreadTest;
import android.test.suitebuilder.annotation.MediumTest;
import android.test.suitebuilder.annotation.Suppress;

// TODO: Insert the unified prefs class here once it's written
@Suppress
public class GeneralPrefsFragmentTest
        extends ActivityInstrumentationTestCase2<Activity> {

    public GeneralPrefsFragmentTest() {
        super(Activity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        final Intent i = new Intent();
        i.putExtra(PreferenceActivity.EXTRA_SHOW_FRAGMENT, "com.android.mail.ui.settings.");
        setActivityIntent(i);
        getActivity();
    }

    @UiThreadTest
    @MediumTest
    public void testChangeAutoAdvance() throws Throwable {
        // A weak proxy test that a click on auto-advance will actually persist the proper value.
        // A better test would simulate a dialog, dialog click, and check that the value is
        // both displayed and persisted.
        /*
        final Activity activity = getActivity();
        final MailPrefs mailPrefs = MailPrefs.get(activity);

        activity.getFragmentManager().executePendingTransactions();

        final GeneralPrefsFragment fragment = activity.getGeneralPrefsFragment();
        final ListPreference autoAdvancePref = (ListPreference) fragment
                .findPreference(GeneralPrefsFragment.AUTO_ADVANCE_WIDGET);

        fragment.onPreferenceChange(autoAdvancePref, UIProvider.AUTO_ADVANCE_MODE_OLDER);

        assertEquals(AutoAdvance.OLDER, mailPrefs.getAutoAdvanceMode());

        fragment.onPreferenceChange(autoAdvancePref, UIProvider.AUTO_ADVANCE_MODE_NEWER);

        assertEquals(AutoAdvance.NEWER, mailPrefs.getAutoAdvanceMode());
        */
    }

    @UiThreadTest
    @MediumTest
    public void testChangeSnapHeader() throws Throwable {
        /*
        final Activity activity = getActivity();
        final MailPrefs mailPrefs = MailPrefs.get(activity);

        activity.getFragmentManager().executePendingTransactions();

        final GeneralPrefsFragment fragment = activity.getGeneralPrefsFragment();
        final ListPreference snapPref = (ListPreference) fragment
                .findPreference(GeneralPrefsFragment.SNAP_HEADER_MODE_WIDGET);

        final int neverValue = GeneralPrefsFragment.prefValueToWidgetIndex(
                GeneralPrefsFragment.SNAP_HEADER_VALUES, SnapHeaderValue.NEVER, -1);
        snapPref.setValueIndex(neverValue);

        assertEquals(SnapHeaderValue.NEVER, mailPrefs.getSnapHeaderMode());

        final int alwaysValue = GeneralPrefsFragment.prefValueToWidgetIndex(
                GeneralPrefsFragment.SNAP_HEADER_VALUES, SnapHeaderValue.ALWAYS, -1);
        snapPref.setValueIndex(alwaysValue);

        assertEquals(SnapHeaderValue.ALWAYS, mailPrefs.getSnapHeaderMode());
        */
    }

}
