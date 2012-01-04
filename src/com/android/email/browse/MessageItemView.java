/**
 * Copyright (c) 2011, Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.email.browse;

import android.content.Context;
import android.database.Cursor;
import android.util.AttributeSet;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.email.R;
import com.android.email.compose.ComposeActivity;
import com.android.email.providers.UIProvider;

// TODO: this will probably becomes the message header view?
public class MessageItemView extends LinearLayout implements OnClickListener {
    private long mId;
    private String mUri;
    private Button mReply;
    private Button mReplyAll;
    private Button mForward;
    private TextView mSubject;
    private TextView mSnippet;

    public MessageItemView(Context context) {
        this(context, null);
    }

    public MessageItemView(Context context, AttributeSet attrs) {
        this(context, attrs, -1);
    }

    public MessageItemView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public void bind(Cursor cursor) {
        mReply = (Button) findViewById(R.id.reply);
        mReplyAll = (Button) findViewById(R.id.replyAll);
        mForward = (Button) findViewById(R.id.forward);
        mReply.setOnClickListener(this);
        mReplyAll.setOnClickListener(this);
        mForward.setOnClickListener(this);
        mSubject = (TextView) findViewById(R.id.subject);
        mSubject.setText(cursor.getString(UIProvider.MESSAGE_SUBJECT_COLUMN));
        mSnippet = (TextView) findViewById(R.id.snippet);
        mSnippet.setText(cursor.getString(UIProvider.MESSAGE_SNIPPET_COLUMN));
        mId = cursor.getLong(UIProvider.MESSAGE_ID_COLUMN);
        mUri = cursor.getString(UIProvider.MESSAGE_URI_COLUMN);
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        switch (id) {
            case R.id.reply:
                ComposeActivity.reply(getContext(), null, mUri);
                break;
            case R.id.replyAll:
                ComposeActivity.replyAll(getContext(), null, mUri);
                break;
            case R.id.forward:
                ComposeActivity.forward(getContext(), null, mUri);
                break;
        }
    }
}
