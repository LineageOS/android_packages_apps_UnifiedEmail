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

package com.android.email.compose;

import android.accounts.Account;
import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.os.Bundle;
import android.text.util.Rfc822Tokenizer;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

import com.android.common.Rfc822Validator;
import com.android.email.providers.protos.Attachment;
import com.android.email.providers.protos.mock.MockAttachment;
import com.android.email.R;
import com.android.email.utils.MimeType;
import com.android.ex.chips.RecipientEditTextView;

public class ComposeActivity extends Activity implements OnClickListener {

    private RecipientEditTextView mTo;
    private RecipientEditTextView mCc;
    private RecipientEditTextView mBcc;
    private Button mCcBccButton;
    private CcBccView mCcBccView;
    private AttachmentsView mAttachmentsView;
    private String mAccount;
    private Rfc822Validator mRecipientValidator;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.compose);
        mCcBccButton = (Button) findViewById(R.id.add_cc);
        if (mCcBccButton != null) {
            mCcBccButton.setOnClickListener(this);
        }
        mAccount = "test@test.com";
        mCcBccView = (CcBccView) findViewById(R.id.cc_bcc_wrapper);
        mAttachmentsView = (AttachmentsView)findViewById(R.id.attachments);
        mTo = setupRecipients(R.id.to);
        mCc = setupRecipients(R.id.cc);
        mBcc = setupRecipients(R.id.bcc);
    }

    private RecipientEditTextView setupRecipients(int id) {
        RecipientEditTextView view = (RecipientEditTextView) findViewById(id);
        view.setAdapter(new RecipientAdapter(this, mAccount));
        view.setTokenizer(new Rfc822Tokenizer());
        if (mRecipientValidator == null) {
            int offset = mAccount.indexOf("@") + 1;
            String account = mAccount;
            if (offset > -1) {
                account = account.substring(mAccount.indexOf("@") + 1);
            }
            mRecipientValidator = new Rfc822Validator(account);
        }
        view.setValidator(mRecipientValidator);
        return view;
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        switch (id) {
            case R.id.add_cc:
            case R.id.add_bcc:
                // Verify that cc/ bcc aren't showing.
                // Animate in cc/bcc.
                mCcBccView.show();
                break;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.compose_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        boolean handled = false;
        switch (id) {
            case R.id.add_attachment:
                MockAttachment attachment = new MockAttachment();
                attachment.partId = "0";
                attachment.name = "testattachment.png";
                attachment.contentType = MimeType.inferMimeType(attachment.name, null);
                attachment.originExtras = "";
                mAttachmentsView.addAttachment(attachment);
                break;
            case R.id.add_cc:
            case R.id.add_bcc:
                mCcBccView.show();
                handled = true;
                break;
        }
        return !handled ? super.onOptionsItemSelected(item) : handled;
    }
}