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
package com.android.email.browse;

import com.android.email.R;
import com.android.email.providers.UIProvider;
import com.android.email.providers.protos.mock.MockUiProvider;

import android.accounts.Account;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;

public class ConversationViewActivity extends Activity {

    private static final String EXTRA_CONVERSATION_LOOKUP = "conversationUri";
    private static final String EXTRA_ACCOUNT = "account";

    private Uri mLookupUri;
    private ContentResolver mResolver;
    private Cursor mConversationCursor;
    private TextView mSubject;

    public static void viewConversation(Context context, String uri, String account) {
        Intent intent = new Intent(context, ConversationViewActivity.class);

        intent.putExtra(EXTRA_CONVERSATION_LOOKUP, uri);
        intent.putExtra(EXTRA_ACCOUNT, account);
        context.startActivity(intent);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        mLookupUri = Uri.parse(intent.getStringExtra(EXTRA_CONVERSATION_LOOKUP));
        mResolver = getContentResolver();
        setContentView(R.layout.conversation_view);
    }

    @Override
    public void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        mSubject = (TextView) findViewById(R.id.subject);
        mConversationCursor = mResolver.query(mLookupUri, UIProvider.CONVERSATION_PROJECTION, null,
                null, null);
        if (mConversationCursor != null) {
            mConversationCursor.moveToFirst();
            mSubject.setText(mConversationCursor.getString(UIProvider.CONVERSATION_SUBJECT_COLUMN));
        }
    }
}
