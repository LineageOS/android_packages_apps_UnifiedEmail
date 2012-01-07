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
package com.android.mail;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;

import com.android.mail.browse.MultiAdapterSpinner;

/**
 * A spinner among accounts that also displays shortcuts to recently used labels for the current
 * account.
 */
// TODO: this is increasingly no longer a spinner in that the design now calls for no selection
// state on phones and the anchor is inconsistent with the popup choices. consider modifying this
// to be less spinner-like.
public class AccountRecentLabelSpinner extends MultiAdapterSpinner {
    private String mCurrentAccount;
    private String[] mAllAccounts;
    private TextView mAccountNameView;

    public AccountRecentLabelSpinner(Context context) {
        this(context, null);
    }

    public AccountRecentLabelSpinner(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mAccountNameView = (TextView) findViewById(R.id.name);
    }

    @Override
    public void onClick(View v) {
        super.onClick(v);
    }

    public void setAccounts(String[] accounts, int currentAccountIndex) {
        mAllAccounts = accounts;
        if (accounts[currentAccountIndex].equals(mCurrentAccount)) {
            return;
        }
        mCurrentAccount = accounts[currentAccountIndex];
        mAccountNameView.setText(mCurrentAccount);
    }

}
