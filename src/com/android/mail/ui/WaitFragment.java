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

package com.android.mail.ui;

import android.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;

import com.android.mail.R;
import com.android.mail.providers.Account;
import com.android.mail.providers.UIProvider.SyncStatus;

public class WaitFragment extends Fragment implements View.OnClickListener {
    // Keys used to pass data to {@link WaitFragment}.
    private static final String ACCOUNT_KEY = "account";

    private Account mAccount;

    public static WaitFragment newInstance(Account account) {
        WaitFragment fragment = new WaitFragment();

        final Bundle args = new Bundle();
        args.putParcelable(ACCOUNT_KEY, account);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle args = getArguments();
        mAccount = (Account)args.getParcelable(ACCOUNT_KEY);
    }

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final View root;

        if ((mAccount.syncStatus & SyncStatus.MANUAL_SYNC_REQUIRED) ==
                SyncStatus.MANUAL_SYNC_REQUIRED) {
            // A manual sync is required
            root = inflater.inflate(R.layout.wait_for_manual_sync, container, false);

            root.findViewById(R.id.manual_sync).setOnClickListener(this);
            root.findViewById(R.id.change_sync_settings).setOnClickListener(this);

        } else {
            root = inflater.inflate(R.layout.wait_for_sync, container, false);
        }

        return root;
    }

    void updateAccount(Account account) {
        mAccount = account;

        // TODO: If this was a manual sync, and the account state has transitioned from
        // "not initialized" & "manual sync required" to "sync in progress" & "manual sync required"
        // the view should be changed to reflect this
    }

    Account getAccount() {
        return mAccount;
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.manual_sync:
               // mCallbacks.onStartSync(sCurrentAccount.name);
                break;
            case R.id.change_sync_settings:
               // mCallbacks.onStartSync(sCurrentAccount.name);
                break;
        }
    }
}