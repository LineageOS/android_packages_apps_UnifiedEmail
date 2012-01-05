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
import android.app.ActionBar;
import android.app.ActionBar.OnNavigationListener;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.text.util.Rfc822Tokenizer;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.TextView;

import com.android.common.Rfc822Validator;
import com.android.email.providers.UIProvider;
import com.android.email.providers.Attachment;
import com.android.email.providers.protos.mock.MockAttachment;
import com.android.email.R;
import com.android.email.utils.MimeType;
import com.android.email.utils.Utils;
import com.android.ex.chips.RecipientEditTextView;

public class ComposeActivity extends Activity implements OnClickListener, OnNavigationListener {
    // Identifiers for which type of composition this is
    static final int COMPOSE = -1;  // also used for editing a draft
    static final int REPLY = 0;
    static final int REPLY_ALL = 1;
    static final int FORWARD = 2;

    // Integer extra holding one of the above compose action
    private static final String EXTRA_ACTION = "action";

    /**
     * Notifies the {@code Activity} that the caller is an Email
     * {@code Activity}, so that the back behavior may be modified accordingly.
     *
     * @see #onAppUpPressed
     */
    private static final String EXTRA_FROM_EMAIL_TASK = "fromemail";

    //  If this is a reply/forward then this extra will hold the original message uri
    private static final String EXTRA_IN_REFERENCE_TO_MESSAGE_URI = "in-reference-to-uri";

    private RecipientEditTextView mTo;
    private RecipientEditTextView mCc;
    private RecipientEditTextView mBcc;
    private Button mCcBccButton;
    private CcBccView mCcBccView;
    private AttachmentsView mAttachmentsView;
    private String mAccount;
    private Rfc822Validator mRecipientValidator;
    private Uri mRefMessageUri;
    private TextView mSubject;

    private ActionBar mActionBar;
    private ComposeModeAdapter mComposeModeAdapter;
    private int mComposeMode = -1;

    /**
     * Can be called from a non-UI thread.
     */
    public static void editDraft(Context context, String account, long mLocalMessageId) {
    }

    /**
     * Can be called from a non-UI thread.
     */
    public static void compose(Context launcher, String account) {
        launch(launcher, account, null, COMPOSE);
    }

    /**
     * Can be called from a non-UI thread.
     */
    public static void reply(Context launcher, String account, String uri) {
        launch(launcher, account, uri, REPLY);
    }

    /**
     * Can be called from a non-UI thread.
     */
    public static void replyAll(Context launcher, String account, String uri) {
        launch(launcher, account, uri, REPLY_ALL);
    }

    /**
     * Can be called from a non-UI thread.
     */
    public static void forward(Context launcher, String account, String uri) {
        launch(launcher, account, uri, FORWARD);
    }

    private static void launch(Context launcher, String account, String uri, int action) {
        Intent intent = new Intent(launcher, ComposeActivity.class);
        intent.putExtra(EXTRA_FROM_EMAIL_TASK, true);
        intent.putExtra(EXTRA_ACTION, action);
        intent.putExtra(Utils.EXTRA_ACCOUNT, account);
        intent.putExtra(EXTRA_IN_REFERENCE_TO_MESSAGE_URI, uri);
        launcher.startActivity(intent);
    }

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
        mSubject = (TextView) findViewById(R.id.subject);
        Intent intent = getIntent();
        int action = intent.getIntExtra(EXTRA_ACTION, COMPOSE);
        initActionBar(action);
        if (action == REPLY || action == REPLY_ALL || action == FORWARD) {
            mRefMessageUri = Uri.parse(intent.getStringExtra(EXTRA_IN_REFERENCE_TO_MESSAGE_URI));
            initFromRefMessage(action);
        }
    }

    private void initActionBar(int action) {
        mComposeMode = action;
        mActionBar = getActionBar();
        if (action == ComposeActivity.COMPOSE) {
            mActionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
            mActionBar.setTitle(R.string.compose);
        } else {
            mActionBar.setTitle(null);
            if (mComposeModeAdapter == null) {
                mComposeModeAdapter = new ComposeModeAdapter(this);
            }
            mActionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);
            mActionBar.setListNavigationCallbacks(mComposeModeAdapter, this);
            switch (action) {
                case ComposeActivity.REPLY:
                    mActionBar.setSelectedNavigationItem(0);
                    break;
                case ComposeActivity.REPLY_ALL:
                    mActionBar.setSelectedNavigationItem(1);
                    break;
                case ComposeActivity.FORWARD:
                    mActionBar.setSelectedNavigationItem(2);
                    break;
            }
        }
    }

    private void initFromRefMessage(int action) {
        ContentResolver resolver = getContentResolver();
        Cursor refMessage = resolver.query(mRefMessageUri, UIProvider.MESSAGE_PROJECTION, null,
                null, null);
        if (refMessage != null) {
            refMessage.moveToFirst();
            mSubject.setText(refMessage.getString(UIProvider.MESSAGE_SUBJECT_COLUMN));
        }
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

    @Override
    public boolean onNavigationItemSelected(int position, long itemId) {
        if (position == ComposeActivity.REPLY) {
            mComposeMode = ComposeActivity.REPLY;
        } else if (position == ComposeActivity.REPLY_ALL) {
            mComposeMode = ComposeActivity.REPLY_ALL;
        } else if (position == ComposeActivity.FORWARD) {
            mComposeMode = ComposeActivity.FORWARD;
        }
        return true;
    }

    private class ComposeModeAdapter extends ArrayAdapter<String> {

        private LayoutInflater mInflater;

        public ComposeModeAdapter(Context context) {
            super(context, R.layout.compose_mode_item, R.id.mode, getResources()
                    .getStringArray(R.array.compose_modes));
        }

        private LayoutInflater getInflater() {
            if (mInflater == null) {
                mInflater = LayoutInflater.from(getContext());
            }
            return mInflater;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = getInflater().inflate(R.layout.compose_mode_display_item, null);
            }
            ((TextView) convertView.findViewById(R.id.mode)).setText(getItem(position));
            return super.getView(position, convertView, parent);
        }
    }
}