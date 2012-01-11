/**
 * Copyright (c) 2012, Google Inc.
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

package com.android.mail.compose;

import android.database.Cursor;
import android.net.Uri;
import android.test.ActivityInstrumentationTestCase2;
import android.text.TextUtils;
import android.text.util.Rfc822Tokenizer;

import com.android.mail.providers.Account;
import com.android.mail.providers.UIProvider;
import com.android.mail.utils.AccountUtils;

public class ComposeActivityTest extends ActivityInstrumentationTestCase2<ComposeActivity> {

    private ComposeActivity mActivity;
    private Account mAccount;

    public ComposeActivityTest() {
        super(ComposeActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        mActivity = getActivity();
        // Get a mock account.
        Account[] results = AccountUtils.getSyncingAccounts(mActivity, null, null, null);
        for (Account account : results) {
            if (account.name.equals("account1@mockuiprovider.com")) {
                mAccount = account;
                mActivity.setAccount(mAccount);
                break;
            }
        }
        super.setUp();
    }

    private Cursor getRefMessage() {
        Cursor foldersCursor = mActivity.getContentResolver().query(
                Uri.parse(mAccount.folderListUri), UIProvider.FOLDERS_PROJECTION, null, null, null);
        Uri convUri = null;
        if (foldersCursor != null) {
            foldersCursor.moveToFirst();
            convUri = Uri.parse(foldersCursor
                    .getString(UIProvider.FOLDER_CONVERSATION_LIST_URI_COLUMN));
        }
        foldersCursor.close();
        Cursor convCursor = mActivity.getContentResolver().query(convUri,
                UIProvider.CONVERSATION_PROJECTION, null, null, null);
        Uri messagesUri = null;
        if (convCursor != null) {
            convCursor.moveToFirst();
            messagesUri = Uri.parse(convCursor
                    .getString(UIProvider.CONVERSATION_MESSAGE_LIST_URI_COLUMN));
        }
        convCursor.close();
        Cursor msgCursor = mActivity.getContentResolver().query(messagesUri,
                UIProvider.MESSAGE_PROJECTION, null, null, null);
        if (msgCursor != null) {
            msgCursor.moveToFirst();
        }
        return msgCursor;
    }

    public void testReply() {
        final Cursor refMessage = getRefMessage();
        final ComposeActivity activity = mActivity;
        final Account account = mAccount;
        final String refMessageFromAccount = refMessage.getString(UIProvider.MESSAGE_FROM_COLUMN);

        mActivity.runOnUiThread(new Runnable() {
            public void run() {
                activity.initReplyRecipients(account.name, refMessage, ComposeActivity.REPLY);
                String[] to = activity.getToAddresses();
                String[] cc = activity.getCcAddresses();
                String[] bcc = activity.getBccAddresses();
                assertTrue(to.length == 1);
                assertEquals(refMessageFromAccount,
                        Rfc822Tokenizer.tokenize(to[0])[0].getAddress());
                assertTrue(cc.length == 0);
                assertTrue(bcc.length == 0);
            }
        });
    }

    public void testReplyAll() {
        final Cursor refMessage = getRefMessage();
        final ComposeActivity activity = mActivity;
        final Account account = mAccount;
        final String[] refMessageTo = TextUtils.split(
                refMessage.getString(UIProvider.MESSAGE_TO_COLUMN), ",");
        final String refMessageFromAccount = refMessage.getString(UIProvider.MESSAGE_FROM_COLUMN);

        mActivity.runOnUiThread(new Runnable() {
            public void run() {
                activity.initReplyRecipients(account.name, refMessage, ComposeActivity.REPLY_ALL);
                String[] to = activity.getToAddresses();
                String[] cc = activity.getCcAddresses();
                String[] bcc = activity.getBccAddresses();
                assertTrue(to.length == 1);
                assertEquals(refMessageFromAccount,
                        Rfc822Tokenizer.tokenize(to[0])[0].getAddress());
                assertEquals(cc.length, refMessageTo.length);
                assertTrue(bcc.length == 0);
            }
        });
    }

    public void testForward() {
        final Cursor refMessage = getRefMessage();
        final ComposeActivity activity = mActivity;
        final Account account = mAccount;

        mActivity.runOnUiThread(new Runnable() {
            public void run() {
                activity.initReplyRecipients(account.name, refMessage, ComposeActivity.FORWARD);
                String[] to = activity.getToAddresses();
                String[] cc = activity.getCcAddresses();
                String[] bcc = activity.getBccAddresses();
                assertEquals(to.length, 0);
                assertEquals(cc.length, 0);
                assertEquals(bcc.length, 0);
            }
        });
    }

    public void testCompose() {
        final Cursor refMessage = getRefMessage();
        final ComposeActivity activity = mActivity;
        final Account account = mAccount;

        mActivity.runOnUiThread(new Runnable() {
            public void run() {
                activity.initReplyRecipients(account.name, refMessage, ComposeActivity.COMPOSE);
                String[] to = activity.getToAddresses();
                String[] cc = activity.getCcAddresses();
                String[] bcc = activity.getBccAddresses();
                assertEquals(to.length, 0);
                assertEquals(cc.length, 0);
                assertEquals(bcc.length, 0);
            }
        });
    }
}
