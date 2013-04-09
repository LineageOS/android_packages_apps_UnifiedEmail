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
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.View;
import android.widget.RelativeLayout;

/**
 * The view for each account in the folder list/drawer.
 */
public class AccountItemView extends RelativeLayout {
    private TextView mAccountTextView;
    private TextView mUnreadCountTextView;
    private TextView mUnseenCountTextView;

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
        mUnseenCountTextView = (TextView)findViewById(R.id.unseen);
    }

    /**
     * Sets the account name and draws the unread count
     *
     * @param account account whose name will be displayed
     * @param count unread count
     */
    public void bind(Account account, int count) {
        mAccountTextView.setText(account.name);
        setUnseenCount(Color.BLACK, 0);
        setUnreadCount(count);
    }

    /**
     * Takes in true if current item view should be modified to look like
     * the current account header. This should not get called with inactive or
     * non-displayed accounts.
     *
     * @param isCurrentAccount true if account is active, false otherwise
     */
    public void setCurrentAccount(boolean isCurrentAccount) {
        if(isCurrentAccount) {
            mUnreadCountTextView.setVisibility(View.GONE);
            mAccountTextView.setAllCaps(true);
            mAccountTextView.setTextColor(R.color.account_item_heading_text_color);
            mAccountTextView.setTextAppearance(getContext(), android.R.style.TextAppearance_Small);
        } else {
            mAccountTextView.setAllCaps(false);
        }
    }

    /**
     * Sets the unread count, taking care to hide/show the textview if the count
     * is zero/non-zero.
     */
    private void setUnreadCount(int count) {
        mUnreadCountTextView.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
        if (count > 0) {
            mUnreadCountTextView.setText(Utils.getUnreadCountString(getContext(), count));
        }
    }

    /**
     * Sets the unseen count, taking care to hide/show the textview if the count
     * is zero/non-zero.
     */
    private void setUnseenCount(final int color, final int count) {
        mUnseenCountTextView.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
        if (count > 0) {
            mUnseenCountTextView.setBackgroundColor(color);
            mUnseenCountTextView.setText(
                    getContext().getString(R.string.inbox_unseen_banner, count));
        }
    }
}
