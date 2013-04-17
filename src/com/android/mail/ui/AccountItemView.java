/**
 * Copyright (c) 2013, Google Inc.
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
package com.android.mail.ui;

import com.android.mail.R;

import android.widget.TextView;

import com.android.mail.providers.Account;
import com.android.mail.utils.Utils;

import android.content.Context;

import android.util.AttributeSet;
import android.view.View;
import android.widget.RelativeLayout;

/**
 * The view for each account in the folder list/drawer.
 */
public class AccountItemView extends RelativeLayout {
    private TextView mAccountTextView;
    private TextView mUnreadCountTextView;

    public AccountItemView(Context context) {
        super(context);
    }

    public AccountItemView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public AccountItemView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mAccountTextView = (TextView)findViewById(R.id.name);
        mUnreadCountTextView = (TextView)findViewById(R.id.unread);
    }

    /**
     * Sets the account name and draws the unread count. If the account is the current account,
     * the unread count is hidden and the account item is given the focused background to
     * partially highlight it.
     *
     * @param account account whose name will be displayed
     * @param isCurrentAccount true if the account is the one in use, false otherwise
     * @param count unread count
     */
    public void bind(final Account account, final boolean isCurrentAccount, final int count) {
        mAccountTextView.setText(account.name);
        setUnreadCount(count);

        if(isCurrentAccount) {
            mUnreadCountTextView.setVisibility(View.GONE);
            setBackgroundResource(R.drawable.list_focused_holo);
        } else {
            setBackgroundResource(0);
        }
    }

    /**
     * Sets the unread count, taking care to hide/show the textview if the count
     * is zero/non-zero.
     */
    private void setUnreadCount(final int count) {
        mUnreadCountTextView.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
        if (count > 0) {
            mUnreadCountTextView.setText(Utils.getUnreadCountString(getContext(), count));
        }
    }
}
