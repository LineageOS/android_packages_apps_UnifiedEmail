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
package com.android.mail.browse;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.view.View;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;

import com.android.mail.FormattedDateBuilder;
import com.android.mail.R;
import com.android.mail.providers.Account;
import com.android.mail.providers.Conversation;
import com.android.mail.providers.UIProvider;
import com.android.mail.utils.Utils;

public class ConversationViewActivity extends Activity {

    private static final String EXTRA_CONVERSATION = "conversation";

    private Conversation mConversation;
    private ContentResolver mResolver;
    private Cursor mMessageCursor;
    private TextView mSubject;
    private ListView mMessageList;
    private FormattedDateBuilder mDateBuilder;
    private Account mAccount;

    public static void viewConversation(Context context, Conversation conv, Account account) {
        Intent intent = new Intent(context, ConversationViewActivity.class);
        intent.putExtra(EXTRA_CONVERSATION, conv);
        intent.putExtra(Utils.EXTRA_ACCOUNT, account);
        context.startActivity(intent);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        mConversation = intent.getParcelableExtra(EXTRA_CONVERSATION);
        mResolver = getContentResolver();
        mAccount = (Account)intent.getParcelableExtra(Utils.EXTRA_ACCOUNT);
        setContentView(R.layout.conversation_view);
    }

    @Override
    public void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        mSubject = (TextView) findViewById(R.id.subject);
        mMessageList = (ListView) findViewById(R.id.message_list);
        mDateBuilder = new FormattedDateBuilder(this);
        mSubject.setText(mConversation.subject);
        mMessageCursor = mResolver.query(mConversation.messageListUri,
                UIProvider.MESSAGE_PROJECTION, null, null, null);
        mMessageList.setAdapter(new MessageListAdapter(this, mMessageCursor));
    }

    class MessageListAdapter extends SimpleCursorAdapter {
        public MessageListAdapter(Context context, Cursor cursor) {
            super(context, R.layout.message, cursor,
                    UIProvider.MESSAGE_PROJECTION, new int[0], 0);
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            super.bindView(view, context, cursor);
            MessageHeaderView header = (MessageHeaderView) view.findViewById(R.id.message_header);
            header.initialize(mDateBuilder, mAccount, true, true, false);
            header.bind(cursor);
            MessageWebView webView = (MessageWebView) view.findViewById(R.id.body);
            webView.loadData(cursor.getString(UIProvider.MESSAGE_BODY_HTML_COLUMN), "text/html",
                    null);
        }
    }
}
