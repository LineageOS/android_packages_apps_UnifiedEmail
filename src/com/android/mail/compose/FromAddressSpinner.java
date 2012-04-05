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

import android.content.Context;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.Spinner;

import com.android.mail.providers.Account;
import com.android.mail.providers.ReplyFromAccount;
import com.android.mail.utils.AccountUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class FromAddressSpinner extends Spinner implements OnItemSelectedListener {
    private List<Account> mAccounts;
    private ReplyFromAccount mAccount;
    private List<ReplyFromAccount> mReplyFromAccounts;
    private OnAccountChangedListener mAccountChangedListener;

    public FromAddressSpinner(Context context) {
        this(context, null);
    }

    public FromAddressSpinner(Context context, AttributeSet set) {
        super(context, set);
    }

    public void setCurrentAccount(ReplyFromAccount account) {
        mAccount = account;
    }

    public ReplyFromAccount getCurrentAccount() {
        return mAccount;
    }

    public void asyncInitFromSpinner() {
        Account[] result = AccountUtils.getSyncingAccounts(getContext());
        mAccounts = AccountUtils.mergeAccountLists(mAccounts, result,
                true /* prioritizeAccountList */);
        initFromSpinner();
    }

    private void initFromSpinner() {
        // If there are not yet any accounts in the cached synced accounts
        // because this is the first time mail was opened, and it was opened
        // directly to the compose activity,don't bother populating the reply
        // from spinner yet.
        if (mAccounts == null || mAccounts.size() == 0) {
            return;
        }
        FromAddressSpinnerAdapter adapter = new FromAddressSpinnerAdapter(getContext());
        int currentAccountIndex = 0;

        mReplyFromAccounts = new ArrayList<ReplyFromAccount>();
        for (Account account : mAccounts) {
            ReplyFromAccount replyFrom = new ReplyFromAccount(account, account.uri, account.name,
                    account.name, false, false);
            if (replyFrom != null) {
                mReplyFromAccounts.add(replyFrom);
            }
            if (!TextUtils.isEmpty(account.accountFromAddresses)) {
                // Parse and create an entry for each.
                try {
                    JSONArray accounts = new JSONArray(account.accountFromAddresses);
                    JSONObject accountString;
                    for (int i = 0; i < accounts.length(); i++) {
                        accountString = (JSONObject) accounts.get(i);
                        ReplyFromAccount a = ReplyFromAccount.deserialize(account, accountString);
                        if (a != null) {
                            mReplyFromAccounts.add(a);
                        }
                    }
                } catch (JSONException e) {

                }
            }
        }
        currentAccountIndex = adapter.addAccounts(mAccount, mReplyFromAccounts);

        setAdapter(adapter);
        setSelection(currentAccountIndex, false);
        setOnItemSelectedListener(this);
        if (currentAccountIndex >= mReplyFromAccounts.size()) {
            currentAccountIndex = 0;
        }
        mAccount = mReplyFromAccounts.get(currentAccountIndex);
    }

    public void setOnAccountChangedListener(OnAccountChangedListener listener) {
        mAccountChangedListener = listener;
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        ReplyFromAccount selection = (ReplyFromAccount) getItemAtPosition(position);
        if (!selection.name.equals(mAccount.name)) {
            mAccount = selection;
            mAccountChangedListener.onAccountChanged();
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
        // Do nothing.
    }

    /**
     * Classes that want to know when a different account in the
     * FromAddressSpinner has been selected should implement this interface.
     * Note: if the user chooses the same account as the one that has already
     * been selected, this method will not be called.
     */
    public static interface OnAccountChangedListener {
        public void onAccountChanged();
    }
}
