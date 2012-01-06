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

package com.android.email;

import com.android.email.browse.ConversationListActivity;
import com.android.email.browse.ActionbarActivity;
import com.android.email.browse.FolderItem;
import com.android.email.compose.ComposeActivity;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

public class UnifiedEmail extends Activity {
    void startActivityWithClass(Class <?> cls){
        Intent intent = new Intent();
        intent.setComponent(new ComponentName(this, cls));
        startActivity(intent);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout_tests);
    }

    public void labelSpinnerTest(View v){
        startActivityWithClass(FolderItem.class);
    }

    public void accountSpinnerTest(View v){
        startActivityWithClass(ComposeActivity.class);
    }

    public void uiProviderTest(View v){
        startActivityWithClass(ConversationListActivity.class);
    }

    public void actionbarTest(View v){
        startActivityWithClass(ActionbarActivity.class);
    }
}
