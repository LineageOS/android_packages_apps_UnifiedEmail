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

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.core.app.JobIntentService;

/**
 * {@link IntentService} that cleans up temporary files in the cache for the eml viewer.
 */
public class EmlTempFileDeletionService extends JobIntentService {

    public static final int JOB_ID = 101;

    public EmlTempFileDeletionService() {
        super();
    }

    public EmlTempFileDeletionService(String name) {
        super();
    }

    public static void enqueueWork(Context context, Intent work) {
        enqueueWork(context, EmlTempFileDeletionService.class, JOB_ID, work);
    }

    @Override
    protected void onHandleWork(Intent intent) {
        final String action = intent.getAction();
        if (Intent.ACTION_DELETE.equals(action)) {
            final Uri uri = intent.getData();
            getContentResolver().delete(uri, null, null);
        }
    }
}
