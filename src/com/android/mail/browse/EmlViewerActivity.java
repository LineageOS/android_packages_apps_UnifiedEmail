/*
 * Copyright (C) 2013 Google Inc.
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

package com.android.mail.browse;

import android.app.ActionBar;
import android.app.Activity;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;

import com.android.mail.R;
import com.android.mail.utils.LogTag;
import com.android.mail.utils.LogUtils;
import com.android.mail.utils.MimeType;

public class EmlViewerActivity extends Activity {
    private static final String LOG_TAG = LogTag.getLogTag();

    private static final String FRAGMENT_TAG = "eml_message_fragment";

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.eml_viewer_activity);

        final ActionBar actionBar = getActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);

        final Intent intent = getIntent();
        final String action = intent.getAction();
        final String type = intent.getType();

        if (Intent.ACTION_VIEW.equals(action) &&
                MimeType.isEmlMimeType(type)) {
            final FragmentManager manager = getFragmentManager();

            if (manager.findFragmentByTag(FRAGMENT_TAG) == null) {
                final FragmentTransaction transaction = manager.beginTransaction();
                transaction.add(R.id.eml_root,
                        EmlMessageViewFragment.newInstance(intent.getData()), FRAGMENT_TAG);
                transaction.commit();
            }
        } else {
            LogUtils.wtf(LOG_TAG,
                    "Entered EmlViewerActivity with wrong intent action or type: %s, %s",
                    action, type);
            finish(); // we should not be here. bail out. bail out.
        }
    }



    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
