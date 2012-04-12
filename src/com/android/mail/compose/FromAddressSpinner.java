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
import com.android.mail.utils.LogUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class FromAddressSpinner extends Spinner implements OnItemSelectedListener {
    private List<Account> mAccounts;
    private ReplyFromAccount mAccount;
    private final List<ReplyFromAccount> mReplyFromAccounts = Lists.newArrayList();
    private OnAccountChangedListener mAccountChangedListener;
    private static final String LOG_TAG = new LogUtils().getLogTag();

    public FromAddressSpinner(Context context) {
        this(context, null);
    }

    public FromAddressSpinner(Context context, AttributeSet set) {
        super(context, set);
    }

    public void setCurrentAccount(ReplyFromAccount account) {
        mAccount = account;
        int currentIndex = 0;
        for (ReplyFromAccount acct : mReplyFromAccounts) {
            if (mAccount.name.equals(acct.account.name)) {
                setSelection(currentIndex);
                break;
            }
            currentIndex++;
        }
    }

    public ReplyFromAccount getCurrentAccount() {
        return mAccount;
    }

    /**
     * @param action Action being performed; if this is COMPOSE, show all
     *            accounts. Otherwise, show just the account this was launched
     *            with.
     * @param currentAccount Account used to launch activity.
     */
    public void asyncInitFromSpinner(int action, Account currentAccount) {
        if (action == ComposeActivity.COMPOSE) {
            Account[] result = AccountUtils.getSyncingAccounts(getContext());
            mAccounts = AccountUtils
                    .mergeAccountLists(mAccounts, result, true /* prioritizeAccountList */);
        } else {
            mAccounts = ImmutableList.of(currentAccount);
        }
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

        mReplyFromAccounts.clear();
        for (Account account : mAccounts) {
            try {
                mReplyFromAccounts.addAll(getAccountSpecificFroms(account));
            } catch (JSONException e) {
                LogUtils.e(LOG_TAG, e, "Failed parsing from addresses associated with account %s",
                        account.name);
            }
        }
        adapter.addAccounts(mReplyFromAccounts);

        setAdapter(adapter);
        setOnItemSelectedListener(this);
    }

    public static List<ReplyFromAccount> getAccountSpecificFroms(Account account)
            throws JSONException {
        List<ReplyFromAccount> froms = new ArrayList<ReplyFromAccount>();
        ReplyFromAccount replyFrom = new ReplyFromAccount(account, account.uri, account.name,
                account.name, false, false);
        if (replyFrom != null) {
            froms.add(replyFrom);
        }
        if (!TextUtils.isEmpty(account.accountFromAddresses)) {
            try {
                JSONArray accounts = new JSONArray(account.accountFromAddresses);
                JSONObject accountString;
                for (int i = 0; i < accounts.length(); i++) {
                    accountString = (JSONObject) accounts.get(i);
                    ReplyFromAccount a = ReplyFromAccount.deserialize(account, accountString);
                    if (a != null) {
                        froms.add(a);
                    }
                }
            } catch (JSONException e) {
                LogUtils.e(LOG_TAG, e, "Failed to parse accountFromAddresses for account %s",
                        account.name);
            }
        }
        return froms;
    }

    public List<ReplyFromAccount> getReplyFromAccounts() {
        return mReplyFromAccounts;
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
